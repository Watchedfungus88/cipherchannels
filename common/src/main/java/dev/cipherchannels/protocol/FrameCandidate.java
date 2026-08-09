package dev.cipherchannels.protocol;

public record FrameCandidate(int start, int endExclusive, String wire, TransportMode transport) {
    public FrameCandidate {
        if (start < 0 || endExclusive < start) {
            throw new IllegalArgumentException("Invalid candidate range");
        }
    }
}
