package dev.cipherchannels.channels;

import java.time.Instant;
import java.util.Objects;

public record ReplayRecord(String fingerprint, String digest, Instant seenAt) {
    public ReplayRecord {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(seenAt, "seenAt");
    }

    public String token() {
        return fingerprint + ':' + digest;
    }
}
