package dev.cipherchannels.crypto;

import java.security.SecureRandom;

public final class ChannelKeys {
    private static final SecureRandom RANDOM = new SecureRandom();

    private ChannelKeys() {}

    public static KeyMaterial generate() {
        byte[] key = new byte[KeyMaterial.LENGTH];
        RANDOM.nextBytes(key);
        try {
            return KeyMaterial.fromBytes(key);
        } finally {
            java.util.Arrays.fill(key, (byte) 0);
        }
    }

    public static void nextBytes(byte[] destination) {
        RANDOM.nextBytes(destination);
    }
}
