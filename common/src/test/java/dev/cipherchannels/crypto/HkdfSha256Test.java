package dev.cipherchannels.crypto;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HkdfSha256Test {
    @Test
    void derivationIsStableAndDomainSeparated() {
        byte[] masterBytes = new byte[KeyMaterial.LENGTH];
        for (int index = 0; index < masterBytes.length; index++) {
            masterBytes[index] = (byte) index;
        }
        try (KeyMaterial master = KeyMaterial.fromBytes(masterBytes);
             HkdfSha256.DerivedKeys first = HkdfSha256.derive(master);
             HkdfSha256.DerivedKeys second = HkdfSha256.derive(master)) {
            byte[] firstEncryption = first.encryption().copyBytes();
            byte[] firstRecognition = first.recognition().copyBytes();
            byte[] secondEncryption = second.encryption().copyBytes();
            byte[] secondRecognition = second.recognition().copyBytes();
            try {
                assertArrayEquals(firstEncryption, secondEncryption);
                assertArrayEquals(firstRecognition, secondRecognition);
                assertFalse(Arrays.equals(firstEncryption, firstRecognition));
            } finally {
                Arrays.fill(firstEncryption, (byte) 0);
                Arrays.fill(firstRecognition, (byte) 0);
                Arrays.fill(secondEncryption, (byte) 0);
                Arrays.fill(secondRecognition, (byte) 0);
            }
        } finally {
            Arrays.fill(masterBytes, (byte) 0);
        }
    }
}
