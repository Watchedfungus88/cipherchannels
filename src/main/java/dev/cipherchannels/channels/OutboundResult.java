package dev.cipherchannels.channels;

import java.util.Objects;

public record OutboundResult(Kind kind, String frame, MessagePreflight preflight) {
    public enum Kind { PASSTHROUGH, ENCRYPTED, BLOCKED }

    public OutboundResult {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(preflight, "preflight");
        boolean encrypted = kind == Kind.ENCRYPTED;
        if (encrypted != (frame != null)) {
            throw new IllegalArgumentException("Invalid outbound result");
        }
    }

    public static OutboundResult passthrough(MessagePreflight preflight) {
        return new OutboundResult(Kind.PASSTHROUGH, null, preflight);
    }

    public static OutboundResult encrypted(String frame, MessagePreflight preflight) {
        return new OutboundResult(Kind.ENCRYPTED, frame, preflight);
    }

    public static OutboundResult blocked(MessagePreflight preflight) {
        return new OutboundResult(Kind.BLOCKED, null, preflight);
    }
}
