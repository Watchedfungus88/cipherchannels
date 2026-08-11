package dev.cipherchannels.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cipherchannels.crypto.AesGcm;
import dev.cipherchannels.crypto.ChannelIdentity;
import dev.cipherchannels.crypto.HkdfSha256;
import dev.cipherchannels.crypto.InviteCode;
import dev.cipherchannels.crypto.KeyMaterial;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolVectorTest {
    private static final byte[] RECOGNITION_DOMAIN = "CipherChannels recognition v2\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FRAME_DOMAIN = "CipherChannels frame v2\0".getBytes(StandardCharsets.UTF_8);

    @Test
    void matchesPublishedVersionTwoVectors() throws IOException {
        JsonObject vectors;
        try (var source = getClass().getResourceAsStream("/protocol-vectors-v2.json")) {
            assertTrue(source != null);
            vectors = JsonParser.parseReader(new java.io.InputStreamReader(source, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        assertEquals(2, vectors.get("version").getAsInt());
        byte[] masterBytes = HexFormat.of().parseHex(vectors.get("masterKeyHex").getAsString());
        try (KeyMaterial master = KeyMaterial.fromBytes(masterBytes); HkdfSha256.DerivedKeys keys = HkdfSha256.derive(master)) {
            assertEquals(vectors.get("encryptionKeyHex").getAsString(), hex(keys.encryption()));
            assertEquals(vectors.get("recognitionKeyHex").getAsString(), hex(keys.recognition()));
            assertEquals(vectors.get("invite").getAsString(), InviteCode.create(master));
            assertEquals(vectors.get("fingerprint").getAsString(), ChannelIdentity.fingerprint(master));
            for (JsonElement element : vectors.getAsJsonArray("frames")) assertFrame(master, element.getAsJsonObject());
        } finally {
            Arrays.fill(masterBytes, (byte) 0);
        }
    }

    private static void assertFrame(KeyMaterial master, JsonObject expected) {
        TransportMode mode = TransportMode.valueOf(expected.get("transport").getAsString());
        String plaintext = expected.has("plaintext") ? expected.get("plaintext").getAsString()
            : expected.get("plaintextPattern").getAsString().repeat(expected.get("plaintextRepeats").getAsInt());
        byte[] nonce = HexFormat.of().parseHex(expected.get("nonceHex").getAsString());
        GeneratedFrame frame = generate(master, mode, plaintext, expected.get("compressed").getAsBoolean(), nonce);
        try {
            assertEquals(expected.get("recognitionHintHex").getAsString(), HexFormat.of().formatHex(frame.hint));
            assertEquals(expected.get("binaryBase64url").getAsString(), Base64.getUrlEncoder().withoutPadding().encodeToString(frame.binary));
            assertEquals(expected.get("wireSha256").getAsString(), sha256Hex(frame.wire.getBytes(StandardCharsets.UTF_8)));
            assertEquals(expected.get("frameDigestHex").getAsString(), HexFormat.of().formatHex(frame.digest));
            try (ParsedFrame parsed = FrameCodec.parse(frame.wire)) {
                assertTrue(FrameCodec.matchesRecognitionHint(master, parsed));
                assertEquals(plaintext, FrameCodec.decrypt(master, parsed));
                assertArrayEquals(frame.digest, FrameCodec.frameDigest(parsed));
            }
        } finally {
            frame.close();
            Arrays.fill(nonce, (byte) 0);
        }
    }

    private static GeneratedFrame generate(KeyMaterial master, TransportMode mode, String plaintext,
                                            boolean compressed, byte[] nonce) {
        byte[] content = compressed ? deflate(plaintext.getBytes(StandardCharsets.UTF_8))
            : plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] record = new byte[mode.encryptedPlaintextLength()];
        System.arraycopy(content, 0, record, 0, content.length);
        record[content.length] = compressed ? FrameCodec.CONTROL_DEFLATE_V2 : FrameCodec.CONTROL_RAW_V2;
        byte[] hint;
        byte[] encrypted;
        try (HkdfSha256.DerivedKeys keys = HkdfSha256.derive(master)) {
            byte[] transport = {mode.id()};
            byte[] fullHint = HkdfSha256.hmac(keys.recognition(), RECOGNITION_DOMAIN, transport, nonce);
            hint = Arrays.copyOf(fullHint, FrameCodec.RECOGNITION_HINT_LENGTH);
            byte[] aad = join(FRAME_DOMAIN, transport, nonce, hint);
            encrypted = AesGcm.encrypt(keys.encryption(), nonce, record, aad);
            Arrays.fill(transport, (byte) 0);
            Arrays.fill(fullHint, (byte) 0);
            Arrays.fill(aad, (byte) 0);
        }
        byte[] binary = join(nonce, hint, encrypted);
        String wire = mode == TransportMode.HIGH_CAPACITY ? Base32768Codec.encode(binary)
            : Base64.getUrlEncoder().withoutPadding().encodeToString(binary);
        byte[] digest = ChannelIdentity.sha256(new byte[] {mode.id()}, binary);
        Arrays.fill(content, (byte) 0);
        Arrays.fill(record, (byte) 0);
        Arrays.fill(encrypted, (byte) 0);
        return new GeneratedFrame(hint, binary, wire, digest);
    }

    private static byte[] join(byte[]... values) {
        int size = Arrays.stream(values).mapToInt(value -> value.length).sum();
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private static byte[] deflate(byte[] source) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(source);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer));
            Arrays.fill(buffer, (byte) 0);
            Arrays.fill(source, (byte) 0);
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static String hex(KeyMaterial key) {
        byte[] bytes = key.copyBytes();
        try {
            return HexFormat.of().formatHex(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            Arrays.fill(value, (byte) 0);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record GeneratedFrame(byte[] hint, byte[] binary, String wire, byte[] digest) implements AutoCloseable {
        @Override
        public void close() {
            Arrays.fill(hint, (byte) 0);
            Arrays.fill(binary, (byte) 0);
            Arrays.fill(digest, (byte) 0);
        }
    }
}
