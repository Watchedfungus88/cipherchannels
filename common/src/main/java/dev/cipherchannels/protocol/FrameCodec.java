package dev.cipherchannels.protocol;

import dev.cipherchannels.crypto.AesGcm;
import dev.cipherchannels.crypto.ChannelIdentity;
import dev.cipherchannels.crypto.ChannelKeys;
import dev.cipherchannels.crypto.HkdfSha256;
import dev.cipherchannels.crypto.KeyMaterial;
import dev.cipherchannels.crypto.StrictUtf8;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class FrameCodec {
    public static final int WIRE_LENGTH = 256;
    public static final int MAX_SOURCE_BYTES = 4_096;
    public static final int RECOGNITION_HINT_LENGTH = 8;
    public static final byte CONTROL_RAW_V2 = 0x20;
    public static final byte CONTROL_DEFLATE_V2 = 0x21;

    private static final int NONCE_OFFSET = 0;
    private static final int HINT_OFFSET = NONCE_OFFSET + AesGcm.NONCE_LENGTH;
    private static final int CIPHERTEXT_OFFSET = HINT_OFFSET + RECOGNITION_HINT_LENGTH;
    private static final byte[] RECOGNITION_DOMAIN =
        "CipherChannels recognition v2\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FRAME_DOMAIN = "CipherChannels frame v2\0".getBytes(StandardCharsets.UTF_8);

    private FrameCodec() {}

    public static FramePreview preview(String message, TransportMode transport) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(transport, "transport");
        PreparedContent prepared = prepareContent(message, transport);
        try {
            return prepared.preview();
        } finally {
            prepared.close();
        }
    }

    public static String encrypt(KeyMaterial masterKey, String message, TransportMode transport) {
        Objects.requireNonNull(masterKey, "masterKey");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(transport, "transport");
        PreparedContent prepared = prepareContent(message, transport);
        byte[] nonce = new byte[AesGcm.NONCE_LENGTH];
        byte[] hint = null;
        byte[] padded = null;
        byte[] aad = null;
        byte[] encrypted = null;
        byte[] binary = null;
        try {
            if (!prepared.preview().ready()) {
                throw new FrameCapacityException(prepared.preview());
            }
            ChannelKeys.nextBytes(nonce);
            try (HkdfSha256.DerivedKeys derived = HkdfSha256.derive(masterKey)) {
                hint = recognitionHint(derived.recognition(), transport, nonce);
                padded = new byte[transport.encryptedPlaintextLength()];
                byte[] content = prepared.content();
                System.arraycopy(content, 0, padded, 0, content.length);
                padded[content.length] = prepared.preview().compressed() ? CONTROL_DEFLATE_V2 : CONTROL_RAW_V2;
                aad = aad(transport, nonce, hint);
                encrypted = AesGcm.encrypt(derived.encryption(), nonce, padded, aad);
            }
            binary = new byte[transport.binaryLength()];
            System.arraycopy(nonce, 0, binary, NONCE_OFFSET, nonce.length);
            System.arraycopy(hint, 0, binary, HINT_OFFSET, hint.length);
            System.arraycopy(encrypted, 0, binary, CIPHERTEXT_OFFSET, encrypted.length);
            return encode(binary, transport);
        } finally {
            prepared.close();
            wipe(nonce);
            wipe(hint);
            wipe(padded);
            wipe(aad);
            wipe(encrypted);
            wipe(binary);
        }
    }

    public static ParsedFrame parse(String wire) {
        Objects.requireNonNull(wire, "wire");
        TransportMode transport = detectTransport(wire);
        if (transport == null) {
            throw new FrameFormatException(FrameFailure.MALFORMED);
        }
        byte[] binary;
        try {
            binary = decode(wire, transport);
        } catch (IllegalArgumentException exception) {
            throw new FrameFormatException(FrameFailure.MALFORMED, exception);
        }
        if (binary.length != transport.binaryLength()) {
            wipe(binary);
            throw new FrameFormatException(FrameFailure.MALFORMED);
        }
        byte[] nonce = Arrays.copyOfRange(binary, NONCE_OFFSET, HINT_OFFSET);
        byte[] hint = Arrays.copyOfRange(binary, HINT_OFFSET, CIPHERTEXT_OFFSET);
        byte[] ciphertextAndTag = Arrays.copyOfRange(binary, CIPHERTEXT_OFFSET, binary.length);
        return new ParsedFrame(transport, nonce, hint, ciphertextAndTag, binary, wire);
    }

    public static TransportMode detectTransport(String wire) {
        if (wire == null || wire.length() != WIRE_LENGTH) {
            return null;
        }
        boolean highCapacity = true;
        boolean ascii = true;
        for (int index = 0; index < wire.length(); index++) {
            char value = wire.charAt(index);
            highCapacity &= TransportMode.HIGH_CAPACITY.accepts(value);
            ascii &= TransportMode.ASCII_COMPATIBILITY.accepts(value);
            if (!highCapacity && !ascii) {
                return null;
            }
        }
        if (highCapacity) {
            return TransportMode.HIGH_CAPACITY;
        }
        return ascii ? TransportMode.ASCII_COMPATIBILITY : null;
    }

    public static boolean matchesRecognitionHint(KeyMaterial masterKey, ParsedFrame frame) {
        byte[] nonce = frame.nonce();
        byte[] actual = frame.recognitionHint();
        byte[] expected = null;
        try (HkdfSha256.DerivedKeys derived = HkdfSha256.derive(masterKey)) {
            expected = recognitionHint(derived.recognition(), frame.transport(), nonce);
            return MessageDigest.isEqual(expected, actual);
        } finally {
            wipe(nonce);
            wipe(actual);
            wipe(expected);
        }
    }

    public static String decrypt(KeyMaterial masterKey, ParsedFrame frame) {
        byte[] nonce = frame.nonce();
        byte[] hint = frame.recognitionHint();
        byte[] encrypted = frame.ciphertextAndTag();
        byte[] aad = aad(frame.transport(), nonce, hint);
        byte[] padded;
        try (HkdfSha256.DerivedKeys derived = HkdfSha256.derive(masterKey)) {
            try {
                padded = AesGcm.decrypt(derived.encryption(), nonce, encrypted, aad);
            } catch (IllegalArgumentException exception) {
                throw new FrameAuthenticationException(exception);
            }
        } finally {
            wipe(nonce);
            wipe(hint);
            wipe(encrypted);
            wipe(aad);
        }
        try {
            return decodePaddedContent(padded);
        } catch (IllegalArgumentException exception) {
            throw new FrameContentException(exception);
        } finally {
            wipe(padded);
        }
    }

    public static byte[] frameDigest(ParsedFrame frame) {
        byte[] binary = frame.binary();
        byte[] transport = {frame.transport().id()};
        try {
            return ChannelIdentity.sha256(transport, binary);
        } finally {
            wipe(binary);
            wipe(transport);
        }
    }

    private static PreparedContent prepareContent(String message, TransportMode transport) {
        byte[] source;
        try {
            source = StrictUtf8.encode(message);
        } catch (IllegalArgumentException exception) {
            return PreparedContent.failed(new FramePreview(FramePreview.Status.MALFORMED_UNICODE, transport, -1, -1));
        }
        if (source.length == 0) {
            wipe(source);
            return PreparedContent.failed(new FramePreview(FramePreview.Status.EMPTY, transport, 0, 0));
        }
        if (source.length > MAX_SOURCE_BYTES) {
            int length = source.length;
            wipe(source);
            return PreparedContent.failed(new FramePreview(FramePreview.Status.SOURCE_TOO_LARGE, transport, length, -1));
        }
        if (source.length <= transport.rawCapacity()) {
            return new PreparedContent(source,
                new FramePreview(FramePreview.Status.READY_RAW, transport, source.length, source.length));
        }
        byte[] compressed = deflate(source);
        int sourceLength = source.length;
        wipe(source);
        if (compressed.length <= transport.rawCapacity()) {
            return new PreparedContent(compressed,
                new FramePreview(FramePreview.Status.READY_COMPRESSED, transport, sourceLength, compressed.length));
        }
        int compressedLength = compressed.length;
        wipe(compressed);
        return PreparedContent.failed(
            new FramePreview(FramePreview.Status.DOES_NOT_FIT, transport, sourceLength, compressedLength));
    }

    private static byte[] deflate(byte[] source) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(source);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(source.length, MAX_SOURCE_BYTES));
            byte[] buffer = new byte[512];
            try {
                while (!deflater.finished()) {
                    int count = deflater.deflate(buffer);
                    if (count == 0 && deflater.needsInput()) {
                        break;
                    }
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            } finally {
                wipe(buffer);
            }
        } finally {
            deflater.end();
        }
    }

    private static String decodePaddedContent(byte[] padded) {
        int controlIndex = padded.length - 1;
        while (controlIndex >= 0 && padded[controlIndex] == 0) {
            controlIndex--;
        }
        if (controlIndex < 0) {
            throw new IllegalArgumentException("Encrypted frame has no control byte");
        }
        byte control = padded[controlIndex];
        byte[] content = Arrays.copyOf(padded, controlIndex);
        try {
            if (control == CONTROL_RAW_V2) {
                if (content.length == 0 || content.length > MAX_SOURCE_BYTES) {
                    throw new IllegalArgumentException("Invalid raw message length");
                }
                return StrictUtf8.decode(content);
            }
            if (control == CONTROL_DEFLATE_V2) {
                byte[] inflated = inflate(content);
                try {
                    return StrictUtf8.decode(inflated);
                } finally {
                    wipe(inflated);
                }
            }
            throw new IllegalArgumentException("Unsupported encrypted control byte");
        } finally {
            wipe(content);
        }
    }

    private static byte[] inflate(byte[] compressed) {
        if (compressed.length == 0) {
            throw new IllegalArgumentException("Compressed message is empty");
        }
        Inflater inflater = new Inflater(true);
        inflater.setInput(compressed);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_SOURCE_BYTES, compressed.length * 2));
        byte[] buffer = new byte[512];
        try {
            int idleIterations = 0;
            while (!inflater.finished()) {
                int count;
                try {
                    count = inflater.inflate(buffer);
                } catch (DataFormatException exception) {
                    throw new IllegalArgumentException("Invalid compressed message", exception);
                }
                if (count == 0) {
                    idleIterations++;
                    if (inflater.needsInput() || inflater.needsDictionary() || idleIterations > 2) {
                        throw new IllegalArgumentException("Truncated compressed message");
                    }
                    continue;
                }
                idleIterations = 0;
                if (output.size() + count > MAX_SOURCE_BYTES) {
                    throw new IllegalArgumentException("Compressed message expands beyond the safety limit");
                }
                output.write(buffer, 0, count);
            }
            if (inflater.getRemaining() != 0 || output.size() == 0) {
                throw new IllegalArgumentException("Compressed message has trailing data or is empty");
            }
            return output.toByteArray();
        } finally {
            inflater.end();
            wipe(buffer);
        }
    }

    private static byte[] recognitionHint(KeyMaterial key, TransportMode transport, byte[] nonce) {
        byte[] transportId = {transport.id()};
        byte[] full = HkdfSha256.hmac(key, RECOGNITION_DOMAIN, transportId, nonce);
        try {
            return Arrays.copyOf(full, RECOGNITION_HINT_LENGTH);
        } finally {
            wipe(full);
            wipe(transportId);
        }
    }

    private static byte[] aad(TransportMode transport, byte[] nonce, byte[] hint) {
        byte[] result = Arrays.copyOf(FRAME_DOMAIN,
            FRAME_DOMAIN.length + 1 + nonce.length + hint.length);
        int cursor = FRAME_DOMAIN.length;
        result[cursor++] = transport.id();
        System.arraycopy(nonce, 0, result, cursor, nonce.length);
        cursor += nonce.length;
        System.arraycopy(hint, 0, result, cursor, hint.length);
        return result;
    }

    private static String encode(byte[] binary, TransportMode transport) {
        if (transport == TransportMode.HIGH_CAPACITY) {
            return Base32768Codec.encode(binary);
        }
        String wire = Base64.getUrlEncoder().withoutPadding().encodeToString(binary);
        if (wire.length() != WIRE_LENGTH) {
            throw new IllegalStateException("Compatibility frame did not encode to 256 characters");
        }
        return wire;
    }

    private static byte[] decode(String wire, TransportMode transport) {
        if (transport == TransportMode.HIGH_CAPACITY) {
            return Base32768Codec.decode(wire);
        }
        return Base64.getUrlDecoder().decode(wire);
    }

    private static void wipe(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static final class PreparedContent implements AutoCloseable {
        private final byte[] content;
        private final FramePreview preview;

        private PreparedContent(byte[] content, FramePreview preview) {
            this.content = content;
            this.preview = preview;
        }

        static PreparedContent failed(FramePreview preview) {
            return new PreparedContent(new byte[0], preview);
        }

        byte[] content() {
            return content;
        }

        FramePreview preview() {
            return preview;
        }

        @Override
        public void close() {
            wipe(content);
        }
    }

    public static final class FrameCapacityException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final transient FramePreview preview;

        FrameCapacityException(FramePreview preview) {
            super("Message does not fit the selected CipherChannels transport");
            this.preview = preview;
        }

        public FramePreview preview() {
            return preview;
        }
    }

    public static final class FrameFormatException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final FrameFailure failure;

        public FrameFormatException(FrameFailure failure) {
            this(failure, null);
        }

        public FrameFormatException(FrameFailure failure, Throwable cause) {
            super("Invalid CipherChannels frame: " + failure, cause);
            this.failure = failure;
        }

        public FrameFailure failure() {
            return failure;
        }
    }

    public static final class FrameAuthenticationException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        FrameAuthenticationException(Throwable cause) {
            super("CipherChannels authentication failed", cause);
        }
    }

    public static final class FrameContentException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        FrameContentException(Throwable cause) {
            super("Authenticated CipherChannels content is invalid", cause);
        }
    }
}
