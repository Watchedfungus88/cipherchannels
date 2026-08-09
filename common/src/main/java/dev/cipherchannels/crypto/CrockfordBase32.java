package dev.cipherchannels.crypto;

public final class CrockfordBase32 {
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private CrockfordBase32() {}

    public static String encode(byte[] input) {
        if (input.length == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : input) {
            buffer = (buffer << Byte.SIZE) | Byte.toUnsignedInt(value);
            bits += Byte.SIZE;
            while (bits >= 5) {
                bits -= 5;
                result.append(ALPHABET[(buffer >>> bits) & 0x1f]);
            }
        }
        if (bits > 0) {
            result.append(ALPHABET[(buffer << (5 - bits)) & 0x1f]);
        }
        return result.toString();
    }

    public static boolean isStrictToken(String token, int expectedLength) {
        if (token.length() != expectedLength) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            if (indexOf(token.charAt(index)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(char value) {
        for (int index = 0; index < ALPHABET.length; index++) {
            if (ALPHABET[index] == value) {
                return index;
            }
        }
        return -1;
    }
}
