package dev.cipherchannels.crypto;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    @Test
    void versionOneKeyScheduleProducesDifferentKeys() {
        byte[] masterBytes = new byte[KeyMaterial.LENGTH];
        Arrays.fill(masterBytes, (byte) 7);
        byte[] oldEncryption = legacyKey(masterBytes, "CipherChannels encryption key v1\0");
        byte[] oldRecognition = legacyKey(masterBytes, "CipherChannels recognition key v1\0");
        try (KeyMaterial master = KeyMaterial.fromBytes(masterBytes);
             HkdfSha256.DerivedKeys current = HkdfSha256.derive(master)) {
            byte[] encryption = current.encryption().copyBytes();
            byte[] recognition = current.recognition().copyBytes();
            try {
                assertFalse(Arrays.equals(oldEncryption, encryption));
                assertFalse(Arrays.equals(oldRecognition, recognition));
            } finally {
                Arrays.fill(encryption, (byte) 0);
                Arrays.fill(recognition, (byte) 0);
            }
        } finally {
            Arrays.fill(masterBytes, (byte) 0);
            Arrays.fill(oldEncryption, (byte) 0);
            Arrays.fill(oldRecognition, (byte) 0);
        }
    }

    private static byte[] legacyKey(byte[] master, String info) {
        try {
            Mac extract = Mac.getInstance("HmacSHA256");
            extract.init(new SecretKeySpec("CipherChannels key schedule v1\0".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] pseudorandom = extract.doFinal(master);
            try {
                Mac expand = Mac.getInstance("HmacSHA256");
                expand.init(new SecretKeySpec(pseudorandom, "HmacSHA256"));
                expand.update(info.getBytes(StandardCharsets.UTF_8));
                expand.update((byte) 1);
                return expand.doFinal();
            } finally {
                Arrays.fill(pseudorandom, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
