package dev.cipherchannels.crypto;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcm {
    public static final int NONCE_LENGTH = 12;
    public static final int TAG_LENGTH = 16;

    private AesGcm() {}

    public static byte[] encrypt(KeyMaterial key, byte[] nonce, byte[] plaintext, byte[] aad) {
        if (nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("AES-GCM requires a 96-bit nonce here");
        }
        return run(Cipher.ENCRYPT_MODE, key, nonce, plaintext, aad);
    }

    public static byte[] decrypt(KeyMaterial key, byte[] nonce, byte[] ciphertextAndTag, byte[] aad) {
        if (nonce.length != NONCE_LENGTH || ciphertextAndTag.length < TAG_LENGTH) {
            throw new IllegalArgumentException("Invalid AES-GCM payload length");
        }
        return run(Cipher.DECRYPT_MODE, key, nonce, ciphertextAndTag, aad);
    }

    private static byte[] run(int mode, KeyMaterial material, byte[] nonce, byte[] input, byte[] aad) {
        byte[] keyBytes = material.copyBytes();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(TAG_LENGTH * Byte.SIZE, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("CipherChannels authentication failed", exception);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }
}
