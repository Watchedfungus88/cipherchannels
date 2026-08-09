package dev.cipherchannels.protocol;

public enum FrameFailure {
    MALFORMED,
    UNKNOWN_CHANNEL,
    AUTHENTICATION_FAILED,
    AUTHENTICATED_INVALID,
    REPLAYED
}
