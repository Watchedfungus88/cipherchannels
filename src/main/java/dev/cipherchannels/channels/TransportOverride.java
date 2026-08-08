package dev.cipherchannels.channels;

import dev.cipherchannels.protocol.TransportMode;
import java.util.Objects;

public record TransportOverride(ServerBinding endpoint, TransportMode mode) {
    public TransportOverride {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(mode, "mode");
        if (mode != TransportMode.ASCII_COMPATIBILITY) {
            throw new IllegalArgumentException("Only non-default compatibility overrides are persisted");
        }
    }
}
