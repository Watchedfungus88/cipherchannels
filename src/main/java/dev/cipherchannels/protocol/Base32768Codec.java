package dev.cipherchannels.protocol;

import java.util.Arrays;

public final class Base32768Codec {
    public static final int BINARY_LENGTH = 480;
    public static final int TEXT_LENGTH = 256;
    private static final int BITS_PER_CHARACTER = 15;
    private static final String RANGE_PAIRS =
        "ҠҿԀԟڀڿݠޟ߀ߟကဟႠႿᄀᅟᆀᆟᇠሿበቿዠዿጠጿᎠᏟᐠᙟᚠᛟកសᠠᡟᣀᣟᦀᦟ᧠᧿ᨠᨿᯀᯟᰀᰟᴀᴟ⇠⇿⋀⋟⍀⏟␀␟─❟➀➿⠀⥿⦠⦿⨠⩟⪀⪿⫠⭟ⰀⰟⲀⳟⴀⴟⵀⵟ⺠⻟㇀㇟㐀䶟䷀龿ꀀꑿ꒠꒿ꔀꗿꙀꙟꚠꛟ꜀ꝟꞀꞟꡀꡟ";
    private static final char[] ENCODE = buildEncodeTable();
    private static final int[] DECODE = buildDecodeTable();

    private Base32768Codec() {}

    public static String encode(byte[] binary) {
        if (binary.length != BINARY_LENGTH) {
            throw new IllegalArgumentException("High-capacity frames must contain exactly 480 bytes");
        }
        char[] output = new char[TEXT_LENGTH];
        int outputIndex = 0;
        int accumulator = 0;
        int accumulatedBits = 0;
        for (byte value : binary) {
            accumulator = (accumulator << Byte.SIZE) | (value & 0xFF);
            accumulatedBits += Byte.SIZE;
            while (accumulatedBits >= BITS_PER_CHARACTER) {
                accumulatedBits -= BITS_PER_CHARACTER;
                output[outputIndex++] = ENCODE[(accumulator >>> accumulatedBits) & 0x7FFF];
                accumulator &= (1 << accumulatedBits) - 1;
            }
        }
        if (accumulatedBits != 0 || outputIndex != output.length) {
            throw new IllegalStateException("Fixed Base32768 frame alignment failed");
        }
        return new String(output);
    }

    public static byte[] decode(String text) {
        if (text.length() != TEXT_LENGTH) {
            throw new IllegalArgumentException("High-capacity frames must contain exactly 256 characters");
        }
        byte[] output = new byte[BINARY_LENGTH];
        int outputIndex = 0;
        int accumulator = 0;
        int accumulatedBits = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            int value = DECODE[character];
            if (value < 0) {
                Arrays.fill(output, (byte) 0);
                throw new IllegalArgumentException("Unrecognized Base32768 character");
            }
            accumulator = (accumulator << BITS_PER_CHARACTER) | value;
            accumulatedBits += BITS_PER_CHARACTER;
            while (accumulatedBits >= Byte.SIZE) {
                accumulatedBits -= Byte.SIZE;
                output[outputIndex++] = (byte) (accumulator >>> accumulatedBits);
                accumulator &= (1 << accumulatedBits) - 1;
            }
        }
        if (accumulatedBits != 0 || outputIndex != output.length) {
            Arrays.fill(output, (byte) 0);
            throw new IllegalArgumentException("Non-canonical Base32768 frame");
        }
        return output;
    }

    public static boolean isAlphabetCharacter(char value) {
        return DECODE[value] >= 0;
    }

    public static char[] alphabet() {
        return ENCODE.clone();
    }

    private static char[] buildEncodeTable() {
        char[] result = new char[1 << BITS_PER_CHARACTER];
        int cursor = 0;
        if ((RANGE_PAIRS.length() & 1) != 0) {
            throw new IllegalStateException("Invalid Base32768 repertoire ranges");
        }
        for (int index = 0; index < RANGE_PAIRS.length(); index += 2) {
            int first = RANGE_PAIRS.charAt(index);
            int last = RANGE_PAIRS.charAt(index + 1);
            for (int codePoint = first; codePoint <= last; codePoint++) {
                if (cursor == result.length) {
                    throw new IllegalStateException("Base32768 repertoire is too large");
                }
                result[cursor++] = (char) codePoint;
            }
        }
        if (cursor != result.length) {
            throw new IllegalStateException("Base32768 repertoire must contain exactly 32768 characters");
        }
        return result;
    }

    private static int[] buildDecodeTable() {
        int[] result = new int[Character.MAX_VALUE + 1];
        Arrays.fill(result, -1);
        for (int index = 0; index < ENCODE.length; index++) {
            result[ENCODE[index]] = index;
        }
        return result;
    }
}
