package dev.cipherchannels.channels;

public enum OutboundBlockReason {
    NONE,
    NO_CHANNEL,
    BINDING_MISMATCH,
    EMPTY,
    MALFORMED_UNICODE,
    SOURCE_TOO_LARGE,
    DOES_NOT_FIT,
    VANILLA_LIMIT,
    ENCRYPTION_FAILED
}
