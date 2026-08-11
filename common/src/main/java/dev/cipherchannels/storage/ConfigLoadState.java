package dev.cipherchannels.storage;

public enum ConfigLoadState {
    FIRST_RUN,
    NORMAL,
    RECOVERED,
    SAFE_MODE_CORRUPT,
    SAFE_MODE_NEWER;

    public boolean safeMode() {
        return this == SAFE_MODE_CORRUPT || this == SAFE_MODE_NEWER;
    }
}
