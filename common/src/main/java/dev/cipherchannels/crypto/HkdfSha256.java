package dev.cipherchannels.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HkdfSha256 {
    private static final byte[] SALT = "CipherChannels key schedule v1\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENCRYPTION_INFO = "CipherChannels AES-256-GCM key v1\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RECOGNITION_INFO = "CipherChannels recognition key v1\0".getBytes(StandardCharsets.UTF_8);

    private HkdfSha256() {}

    public static DerivedKeys derive(KeyMaterial masterKey) {
        byte[] inputKey = masterKey.copyBytes();
        byte[] pseudorandomKey = null;
        byte[] encryption = null;
        byte[] recognition = null;
        try {
            pseudorandomKey = hmac(SALT, inputKey);
            encryption = expandOneBlock(pseudorandomKey, ENCRYPTION_INFO);
            recognition = expandOneBlock(pseudorandomKey, RECOGNITION_INFO);
            return new DerivedKeys(KeyMaterial.fromBytes(encryption), KeyMaterial.fromBytes(recognition));
        } finally {
            Arrays.fill(inputKey, (byte) 0);
            wipe(pseudorandomKey);
            wipe(encryption);
            wipe(recognition);
        }
    }

    public static byte[] hmac(KeyMaterial key, byte[]... inputs) {
        byte[] keyBytes = key.copyBytes();
        try {
            Mac mac = mac(keyBytes);
            for (byte[] input : inputs) {
                mac.update(input);
            }
            return mac.doFinal();
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private static byte[] expandOneBlock(byte[] pseudorandomKey, byte[] info) {
        Mac mac = mac(pseudorandomKey);
        mac.update(info);
        mac.update((byte) 0x01);
        return mac.doFinal();
    }

    private static byte[] hmac(byte[] key, byte[] input) {
        Mac mac = mac(key);
        return mac.doFinal(input);
    }

    private static Mac mac(byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is required by Java", exception);
        }
    }

    private static void wipe(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    public record DerivedKeys(KeyMaterial encryption, KeyMaterial recognition) implements AutoCloseable {
        @Override
        public void close() {
            encryption.close();
            recognition.close();
        }
    }
}
