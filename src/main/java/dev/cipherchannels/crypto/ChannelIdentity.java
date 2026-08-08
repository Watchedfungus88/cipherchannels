package dev.cipherchannels.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class ChannelIdentity {
    private static final byte[] FINGERPRINT_DOMAIN = "CipherChannels fingerprint v1\0".getBytes(StandardCharsets.UTF_8);

    private ChannelIdentity() {}

    public static String fingerprint(KeyMaterial key) {
        byte[] digest = digest(FINGERPRINT_DOMAIN, key.copyBytes());
        byte[] firstTen = Arrays.copyOf(digest, 10);
        Arrays.fill(digest, (byte) 0);
        String compact = CrockfordBase32.encode(firstTen);
        Arrays.fill(firstTen, (byte) 0);
        return compact.substring(0, 4) + '-' + compact.substring(4, 8) + '-' + compact.substring(8, 12) + '-' + compact.substring(12, 16);
    }

    public static byte[] sha256(byte[]... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] value : values) {
                digest.update(value);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }

    private static byte[] digest(byte[] domain, byte[] key) {
        try {
            return sha256(domain, key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }
}
