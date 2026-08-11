package dev.cipherchannels.protocol;

public enum TransportMode {
    HIGH_CAPACITY((byte) 0x01, 480, 443),
    ASCII_COMPATIBILITY((byte) 0x02, 192, 155);

    private static final int PUBLIC_OVERHEAD = 12 + 8 + 16 + 1;

    private final byte id;
    private final int binaryLength;
    private final int rawCapacity;

    TransportMode(byte id, int binaryLength, int rawCapacity) {
        if (binaryLength - PUBLIC_OVERHEAD != rawCapacity) {
            throw new IllegalArgumentException("Transport capacity does not match frame layout");
        }
        this.id = id;
        this.binaryLength = binaryLength;
        this.rawCapacity = rawCapacity;
    }

    public byte id() {
        return id;
    }

    public int binaryLength() {
        return binaryLength;
    }

    public int encryptedPlaintextLength() {
        return binaryLength - 12 - 8 - 16;
    }

    public int rawCapacity() {
        return rawCapacity;
    }

    public boolean accepts(char value) {
        return switch (this) {
            case HIGH_CAPACITY -> Base32768Codec.isAlphabetCharacter(value);
            case ASCII_COMPATIBILITY -> value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-' || value == '_';
        };
    }
}
