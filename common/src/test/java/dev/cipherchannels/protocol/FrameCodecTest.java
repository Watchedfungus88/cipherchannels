package dev.cipherchannels.protocol;

import dev.cipherchannels.crypto.AesGcm;
import dev.cipherchannels.crypto.ChannelKeys;
import dev.cipherchannels.crypto.HkdfSha256;
import dev.cipherchannels.crypto.KeyMaterial;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameCodecTest {
    private static final byte[] RECOGNITION_DOMAIN =
        "CipherChannels recognition v2\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FRAME_DOMAIN =
        "CipherChannels frame v2\0".getBytes(StandardCharsets.UTF_8);

    @Test
    void roundTripsBothFixedTransportsWithUnicode() {
        try (KeyMaterial key = ChannelKeys.generate()) {
            String source = "Coffee ☕, שלום, こんにちは";
            for (TransportMode mode : TransportMode.values()) {
                String wire = FrameCodec.encrypt(key, source, mode);
                assertEquals(FrameCodec.WIRE_LENGTH, wire.length());
                assertEquals(mode, FrameCodec.detectTransport(wire));
                try (ParsedFrame frame = FrameCodec.parse(wire)) {
                    assertTrue(FrameCodec.matchesRecognitionHint(key, frame));
                    assertEquals(source, FrameCodec.decrypt(key, frame));
                }
            }
        }
    }

    @Test
    void enforcesRawBoundariesAndUsesCompressionOnlyAfterThem() {
        assertEquals(FramePreview.Status.READY_RAW,
            FrameCodec.preview("a".repeat(443), TransportMode.HIGH_CAPACITY).status());
        FramePreview highOver = FrameCodec.preview("a".repeat(444), TransportMode.HIGH_CAPACITY);
        assertEquals(FramePreview.Status.READY_COMPRESSED, highOver.status());
        assertEquals(444, highOver.sourceBytes());

        assertEquals(FramePreview.Status.READY_RAW,
            FrameCodec.preview("a".repeat(155), TransportMode.ASCII_COMPATIBILITY).status());
        FramePreview asciiOver = FrameCodec.preview("a".repeat(156), TransportMode.ASCII_COMPATIBILITY);
        assertEquals(FramePreview.Status.READY_COMPRESSED, asciiOver.status());
        assertEquals(156, asciiOver.sourceBytes());
    }

    @Test
    void handlesCompressionThrough4096AndRejectsEveryOversizeFailureWithoutTruncation() {
        try (KeyMaterial key = ChannelKeys.generate()) {
            String maximum = "abc123".repeat(682) + "abcd";
            assertEquals(4096, maximum.getBytes(StandardCharsets.UTF_8).length);
            FramePreview preview = FrameCodec.preview(maximum, TransportMode.HIGH_CAPACITY);
            assertEquals(FramePreview.Status.READY_COMPRESSED, preview.status());
            String wire = FrameCodec.encrypt(key, maximum, TransportMode.HIGH_CAPACITY);
            try (ParsedFrame parsed = FrameCodec.parse(wire)) {
                assertEquals(maximum, FrameCodec.decrypt(key, parsed));
            }

            assertEquals(FramePreview.Status.SOURCE_TOO_LARGE,
                FrameCodec.preview("a".repeat(4097), TransportMode.HIGH_CAPACITY).status());
            assertThrows(FrameCodec.FrameCapacityException.class,
                () -> FrameCodec.encrypt(key, "a".repeat(4097), TransportMode.HIGH_CAPACITY));

            String incompressible = randomTwoByteText(700, 0xC1F3L);
            FramePreview failed = FrameCodec.preview(incompressible, TransportMode.HIGH_CAPACITY);
            assertEquals(1400, failed.sourceBytes());
            assertEquals(FramePreview.Status.DOES_NOT_FIT, failed.status());
        }
    }

    @Test
    void rejectsMalformedSurrogatesWrongKeysAndAllAuthenticatedTampering() {
        try (KeyMaterial key = ChannelKeys.generate(); KeyMaterial other = ChannelKeys.generate()) {
            assertEquals(FramePreview.Status.MALFORMED_UNICODE,
                FrameCodec.preview("broken\uD800", TransportMode.HIGH_CAPACITY).status());
            assertThrows(FrameCodec.FrameCapacityException.class,
                () -> FrameCodec.encrypt(key, "broken\uD800", TransportMode.HIGH_CAPACITY));

            String wire = FrameCodec.encrypt(key, "private", TransportMode.HIGH_CAPACITY);
            try (ParsedFrame frame = FrameCodec.parse(wire)) {
                assertFalse(FrameCodec.matchesRecognitionHint(other, frame));
                assertThrows(FrameCodec.FrameAuthenticationException.class, () -> FrameCodec.decrypt(other, frame));
            }

            byte[] binary = decodedBinary(wire);
            for (int offset : new int[] {0, 12, 20, binary.length - 1}) {
                byte[] changed = binary.clone();
                changed[offset] ^= 1;
                try (ParsedFrame frame = FrameCodec.parse(encodedBinary(changed, TransportMode.HIGH_CAPACITY))) {
                    if (offset == 12 || offset == 0) {
                        assertFalse(FrameCodec.matchesRecognitionHint(key, frame));
                    }
                    assertThrows(FrameCodec.FrameAuthenticationException.class, () -> FrameCodec.decrypt(key, frame));
                }
            }
            Arrays.fill(binary, (byte) 0);
        }
    }

    @Test
    void rejectsAuthenticatedInvalidControlUtf8StreamsTrailingBytesAndExpansionBombs() {
        try (KeyMaterial key = ChannelKeys.generate()) {
            for (byte legacyControl : new byte[] {0x10, 0x11}) {
                byte[] invalidControl = new byte[TransportMode.HIGH_CAPACITY.encryptedPlaintextLength()];
                invalidControl[0] = 1;
                invalidControl[1] = legacyControl;
                assertAuthenticatedContentFailure(key,
                    authenticatedWire(key, TransportMode.HIGH_CAPACITY, invalidControl, legacyControl));
                Arrays.fill(invalidControl, (byte) 0);
            }

            byte[] invalidUtf8 = new byte[TransportMode.HIGH_CAPACITY.encryptedPlaintextLength()];
            invalidUtf8[0] = (byte) 0xC3;
            invalidUtf8[1] = 0x28;
            invalidUtf8[2] = FrameCodec.CONTROL_RAW_V2;
            assertAuthenticatedContentFailure(key, authenticatedWire(key, TransportMode.HIGH_CAPACITY, invalidUtf8, (byte) 2));

            byte[] compressed = deflate("hello".getBytes(StandardCharsets.UTF_8));
            byte[] trailing = new byte[TransportMode.HIGH_CAPACITY.encryptedPlaintextLength()];
            System.arraycopy(compressed, 0, trailing, 0, compressed.length);
            trailing[compressed.length] = 1;
            trailing[compressed.length + 1] = FrameCodec.CONTROL_DEFLATE_V2;
            assertAuthenticatedContentFailure(key, authenticatedWire(key, TransportMode.HIGH_CAPACITY, trailing, (byte) 3));

            byte[] bomb = deflate("z".repeat(4097).getBytes(StandardCharsets.UTF_8));
            byte[] bombRecord = new byte[TransportMode.HIGH_CAPACITY.encryptedPlaintextLength()];
            System.arraycopy(bomb, 0, bombRecord, 0, bomb.length);
            bombRecord[bomb.length] = FrameCodec.CONTROL_DEFLATE_V2;
            assertAuthenticatedContentFailure(key, authenticatedWire(key, TransportMode.HIGH_CAPACITY, bombRecord, (byte) 4));

            Arrays.fill(invalidUtf8, (byte) 0);
            Arrays.fill(compressed, (byte) 0);
            Arrays.fill(trailing, (byte) 0);
            Arrays.fill(bomb, (byte) 0);
            Arrays.fill(bombRecord, (byte) 0);
        }
    }

    @Test
    void versionOneRecognitionAndFrameDomainsAreNeverAccepted() {
        byte[] padded = new byte[TransportMode.HIGH_CAPACITY.encryptedPlaintextLength()];
        padded[0] = 'x';
        padded[1] = 0x10;
        byte[] recognitionV1 = "CipherChannels recognition v1\0".getBytes(StandardCharsets.UTF_8);
        byte[] frameV1 = "CipherChannels frame v1\0".getBytes(StandardCharsets.UTF_8);
        try (KeyMaterial key = ChannelKeys.generate();
             ParsedFrame frame = FrameCodec.parse(authenticatedWire(key, TransportMode.HIGH_CAPACITY,
                 padded, (byte) 9, recognitionV1, frameV1))) {
            assertFalse(FrameCodec.matchesRecognitionHint(key, frame));
            assertThrows(FrameCodec.FrameAuthenticationException.class, () -> FrameCodec.decrypt(key, frame));
        } finally {
            Arrays.fill(padded, (byte) 0);
            Arrays.fill(recognitionV1, (byte) 0);
            Arrays.fill(frameV1, (byte) 0);
        }
    }

    @Test
    void strictCandidateParsingIsFixedLengthAlphabetOnlyAndLegacyFramesStayOrdinaryText() {
        assertMalformed("A".repeat(255));
        assertMalformed("A".repeat(257));
        assertMalformed("A".repeat(255) + "=");
        assertMalformed("A".repeat(255) + " ");
        assertEquals(0, FrameScanner.scan("~CC1:abcdefghijklmnop~").size());
        assertEquals(1, FrameScanner.scan("A".repeat(256)).size());
        String formatted = "[01:12:19] [VIP] player: " + "A".repeat(256) + " [channel]";
        FrameCandidate candidate = FrameScanner.scan(formatted).getFirst();
        assertEquals(formatted.indexOf("A".repeat(256)), candidate.start());
        assertEquals("A".repeat(256), candidate.wire());
        assertEquals(TransportMode.ASCII_COMPATIBILITY, candidate.transport());
        assertEquals(0, FrameScanner.scan("x" + "A".repeat(256)).size());
        assertEquals(0, FrameScanner.scan("A".repeat(256) + " " + "B".repeat(256)).size());
        assertEquals(0, FrameScanner.scan("[" + "A".repeat(256) + "]" + "x".repeat(4096)).size());
        try (ParsedFrame parsed = FrameCodec.parse("A".repeat(256))) {
            assertEquals(TransportMode.ASCII_COMPATIBILITY, parsed.transport());
            assertArrayEquals(new byte[192], parsed.binary());
        }
    }

    @Test
    void freshNoncesRotateRecognitionHintsAndFrames() {
        try (KeyMaterial key = ChannelKeys.generate()) {
            String first = FrameCodec.encrypt(key, "same", TransportMode.HIGH_CAPACITY);
            String second = FrameCodec.encrypt(key, "same", TransportMode.HIGH_CAPACITY);
            assertNotEquals(first, second);
            try (ParsedFrame one = FrameCodec.parse(first); ParsedFrame two = FrameCodec.parse(second)) {
                assertFalse(MessageDigest.isEqual(one.nonce(), two.nonce()));
                assertFalse(MessageDigest.isEqual(one.recognitionHint(), two.recognitionHint()));
            }
        }
    }

    @Test
    void parserFuzzingHasBoundedFailureAndNeverProducesPlaintext() {
        Random random = new Random(0xCC1L);
        for (int iteration = 0; iteration < 50_000; iteration++) {
            int length = random.nextInt(600);
            StringBuilder input = new StringBuilder(length);
            for (int index = 0; index < length; index++) {
                input.append((char) random.nextInt(Character.MAX_VALUE + 1));
            }
            String value = input.toString();
            try {
                FrameCodec.parse(value).close();
            } catch (FrameCodec.FrameFormatException expected) {}
            assertTrue(FrameScanner.scan(value).size() <= FrameScanner.MAX_CANDIDATES);
        }
    }

    private static String randomTwoByteText(int characters, long seed) {
        Random random = new Random(seed);
        StringBuilder value = new StringBuilder(characters);
        for (int index = 0; index < characters; index++) {
            value.append((char) (0x80 + random.nextInt(0x780)));
        }
        return value.toString();
    }

    private static void assertAuthenticatedContentFailure(KeyMaterial key, String wire) {
        try (ParsedFrame parsed = FrameCodec.parse(wire)) {
            assertTrue(FrameCodec.matchesRecognitionHint(key, parsed));
            assertThrows(FrameCodec.FrameContentException.class, () -> FrameCodec.decrypt(key, parsed));
        }
    }

    private static byte[] decodedBinary(String wire) {
        TransportMode mode = FrameCodec.detectTransport(wire);
        return mode == TransportMode.HIGH_CAPACITY
            ? Base32768Codec.decode(wire) : Base64.getUrlDecoder().decode(wire);
    }

    private static String encodedBinary(byte[] binary, TransportMode mode) {
        return mode == TransportMode.HIGH_CAPACITY ? Base32768Codec.encode(binary)
            : Base64.getUrlEncoder().withoutPadding().encodeToString(binary);
    }

    private static String authenticatedWire(KeyMaterial key, TransportMode mode, byte[] padded, byte nonceByte) {
        return authenticatedWire(key, mode, padded, nonceByte, RECOGNITION_DOMAIN, FRAME_DOMAIN);
    }

    private static String authenticatedWire(KeyMaterial key, TransportMode mode, byte[] padded, byte nonceByte,
                                            byte[] recognitionDomain, byte[] frameDomain) {
        byte[] nonce = new byte[AesGcm.NONCE_LENGTH];
        Arrays.fill(nonce, nonceByte);
        try (HkdfSha256.DerivedKeys derived = HkdfSha256.derive(key)) {
            byte[] transportId = {mode.id()};
            byte[] fullHint = HkdfSha256.hmac(derived.recognition(), recognitionDomain, transportId, nonce);
            byte[] hint = Arrays.copyOf(fullHint, FrameCodec.RECOGNITION_HINT_LENGTH);
            byte[] aad = new byte[frameDomain.length + 1 + nonce.length + hint.length];
            int cursor = 0;
            System.arraycopy(frameDomain, 0, aad, cursor, frameDomain.length);
            cursor += frameDomain.length;
            aad[cursor++] = mode.id();
            System.arraycopy(nonce, 0, aad, cursor, nonce.length);
            cursor += nonce.length;
            System.arraycopy(hint, 0, aad, cursor, hint.length);
            byte[] encrypted = AesGcm.encrypt(derived.encryption(), nonce, padded, aad);
            byte[] binary = new byte[mode.binaryLength()];
            System.arraycopy(nonce, 0, binary, 0, nonce.length);
            System.arraycopy(hint, 0, binary, nonce.length, hint.length);
            System.arraycopy(encrypted, 0, binary, nonce.length + hint.length, encrypted.length);
            String result = encodedBinary(binary, mode);
            Arrays.fill(transportId, (byte) 0);
            Arrays.fill(fullHint, (byte) 0);
            Arrays.fill(hint, (byte) 0);
            Arrays.fill(aad, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
            Arrays.fill(binary, (byte) 0);
            return result;
        } finally {
            Arrays.fill(nonce, (byte) 0);
        }
    }

    private static byte[] deflate(byte[] source) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(source);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void assertMalformed(String wire) {
        FrameCodec.FrameFormatException exception = assertThrows(FrameCodec.FrameFormatException.class,
            () -> FrameCodec.parse(wire));
        assertEquals(FrameFailure.MALFORMED, exception.failure());
    }
}
