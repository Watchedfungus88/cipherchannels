package dev.cipherchannels.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

public final class InviteCode {
    private static final String PREFIX = "CC2.";
    private static final byte[] DOMAIN = "CipherChannels invite checksum v2\0".getBytes(StandardCharsets.UTF_8);
    private static final int KEY_TEXT_LENGTH = 43;
    private static final int CHECKSUM_LENGTH = 8;

    private InviteCode() {}

    public static String create(KeyMaterial key) {
        byte[] raw = key.copyBytes();
        try {
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            return PREFIX + encoded + '.' + checksum(raw);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    public static KeyMaterial parse(String invite) {
        Objects.requireNonNull(invite, "invite");
        if (invite.startsWith("CC1.")) {
            throw new IllegalArgumentException("CipherChannels 1.x invites are incompatible; create a new 2.0 channel");
        }
        if (invite.length() != PREFIX.length() + KEY_TEXT_LENGTH + 1 + CHECKSUM_LENGTH
            || !invite.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invite has an invalid format");
        }
        int separator = PREFIX.length() + KEY_TEXT_LENGTH;
        if (invite.charAt(separator) != '.') {
            throw new IllegalArgumentException("Invite has an invalid format");
        }
        String encodedKey = invite.substring(PREFIX.length(), separator);
        String suppliedChecksum = invite.substring(separator + 1);
        if (!isStrictBase64Url(encodedKey) || !CrockfordBase32.isStrictToken(suppliedChecksum, CHECKSUM_LENGTH)) {
            throw new IllegalArgumentException("Invite has an invalid format");
        }

        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invite has an invalid encoding", exception);
        }
        try {
            if (raw.length != KeyMaterial.LENGTH) {
                throw new IllegalArgumentException("Invite has an invalid key length");
            }
            if (!isCanonicalBase64Url(encodedKey, raw)) {
                throw new IllegalArgumentException("Invite has a non-canonical key encoding");
            }
            byte[] expected = checksum(raw).getBytes(StandardCharsets.US_ASCII);
            byte[] supplied = suppliedChecksum.getBytes(StandardCharsets.US_ASCII);
            try {
                if (!MessageDigest.isEqual(expected, supplied)) {
                    throw new IllegalArgumentException("Invite checksum does not match");
                }
            } finally {
                Arrays.fill(expected, (byte) 0);
                Arrays.fill(supplied, (byte) 0);
            }
            return KeyMaterial.fromBytes(raw);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private static String checksum(byte[] key) {
        byte[] digest = ChannelIdentity.sha256(DOMAIN, key);
        byte[] firstFive = Arrays.copyOf(digest, 5);
        Arrays.fill(digest, (byte) 0);
        try {
            return CrockfordBase32.encode(firstFive);
        } finally {
            Arrays.fill(firstFive, (byte) 0);
        }
    }

    private static boolean isStrictBase64Url(String text) {
        if (text.length() != KEY_TEXT_LENGTH) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            boolean valid = value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-' || value == '_';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCanonicalBase64Url(String supplied, byte[] decoded) {
        byte[] actual = supplied.getBytes(StandardCharsets.US_ASCII);
        byte[] canonical = Base64.getUrlEncoder().withoutPadding().encode(decoded);
        try {
            return MessageDigest.isEqual(canonical, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
            Arrays.fill(canonical, (byte) 0);
        }
    }
}
