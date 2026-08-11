package dev.cipherchannels.protocol;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base32768CodecTest {
    @Test
    void entireRepertoireIsUniqueAllowedBmpAndNormalizationStable() {
        char[] alphabet = Base32768Codec.alphabet();
        assertEquals(32_768, alphabet.length);
        HashSet<Character> unique = new HashSet<>();
        for (char character : alphabet) {
            assertTrue(unique.add(character));
            assertFalse(Character.isSurrogate(character));
            String one = Character.toString(character);
            assertEquals(one, Normalizer.normalize(one, Normalizer.Form.NFC));
        }
    }

    @Test
    void exactConversionAndEveryAlphabetChunkDecodeCanonically() {
        byte[] zero = new byte[Base32768Codec.BINARY_LENGTH];
        String encoded = Base32768Codec.encode(zero);
        assertEquals(Base32768Codec.TEXT_LENGTH, encoded.length());
        assertArrayEquals(zero, Base32768Codec.decode(encoded));

        char[] alphabet = Base32768Codec.alphabet();
        for (int start = 0; start < alphabet.length; start += Base32768Codec.TEXT_LENGTH) {
            String wire = new String(alphabet, start, Base32768Codec.TEXT_LENGTH);
            assertEquals(wire, Base32768Codec.encode(Base32768Codec.decode(wire)));
        }
        assertThrows(IllegalArgumentException.class, () -> Base32768Codec.decode(encoded.substring(1)));
        assertThrows(IllegalArgumentException.class, () -> Base32768Codec.encode(new byte[479]));
    }

    @Test
    void randomizedRoundTrips() {
        Random random = new Random(0xB32768L);
        for (int iteration = 0; iteration < 2_000; iteration++) {
            byte[] source = new byte[Base32768Codec.BINARY_LENGTH];
            random.nextBytes(source);
            String wire = Base32768Codec.encode(source);
            assertEquals(wire, Normalizer.normalize(wire, Normalizer.Form.NFC));
            assertArrayEquals(source, Base32768Codec.decode(wire));
        }
    }
}
