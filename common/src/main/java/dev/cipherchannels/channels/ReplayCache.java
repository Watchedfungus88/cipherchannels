package dev.cipherchannels.channels;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ReplayCache {
    public static final int MAX_ENTRIES = 4_096;
    public static final Duration LIFETIME = Duration.ofHours(6);

    private final Clock clock;
    private final Map<String, Instant> seen = new LinkedHashMap<>(128, 0.75f, true);

    public ReplayCache(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized boolean isReplay(String fingerprint, byte[] digest) {
        Instant now = clock.instant();
        evictExpired(now);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        String token = fingerprint + ':' + encoded;
        if (seen.containsKey(token)) {
            return true;
        }
        seen.put(token, now);
        trim();
        return false;
    }

    public synchronized void removeFingerprint(String fingerprint) {
        seen.keySet().removeIf(key -> key.startsWith(fingerprint + ':'));
    }

    public synchronized int size() {
        evictExpired(clock.instant());
        return seen.size();
    }

    private void evictExpired(Instant now) {
        Instant threshold = now.minus(LIFETIME);
        seen.entrySet().removeIf(entry -> !entry.getValue().isAfter(threshold));
    }

    private void trim() {
        while (seen.size() > MAX_ENTRIES) {
            Iterator<String> iterator = seen.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

}
