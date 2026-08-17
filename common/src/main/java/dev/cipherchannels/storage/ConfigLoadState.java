package dev.cipherchannels.storage;

public enum ConfigLoadState {
    NORMAL,
    LOCKED_CORRUPT,
    LOCKED_NEWER;

    public boolean locked() {
        return this != NORMAL;
    }
}
