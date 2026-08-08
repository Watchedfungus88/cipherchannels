package dev.cipherchannels.channels;

import dev.cipherchannels.protocol.FrameFailure;
import java.util.Objects;

public record IncomingResult(String plaintext, ChannelRecord channel, FrameFailure failure) {
    public IncomingResult {
        boolean authenticated = channel != null;
        if (authenticated && (plaintext == null || failure != null)
            || !authenticated && (plaintext != null || failure == null)) {
            throw new IllegalArgumentException("Invalid incoming result");
        }
    }

    public static IncomingResult authenticated(String plaintext, ChannelRecord channel) {
        return new IncomingResult(Objects.requireNonNull(plaintext), Objects.requireNonNull(channel), null);
    }

    public static IncomingResult failed(FrameFailure failure) {
        return new IncomingResult(null, null, failure);
    }

    public boolean authenticated() {
        return channel != null;
    }
}
