package dev.cipherchannels.channels;

import dev.cipherchannels.storage.ReplayStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReplayCache {
    public static final int MAX_ENTRIES = 4_096;
    public static final Duration LIFETIME = Duration.ofHours(6);

    private final Clock clock;
    private final ReplayStore store;
    private final Map<String, ReplayRecord> seen = new LinkedHashMap<>(128, 0.75f, true);

    public ReplayCache(Clock clock) {
        this(clock, null);
    }

    public ReplayCache(Clock clock, ReplayStore store) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.store = store;
        if (store != null) {
            Instant now = clock.instant();
            Instant threshold = now.minus(LIFETIME);
            for (ReplayRecord record : store.load().records()) {
                Instant seenAt = record.seenAt().isAfter(now) ? now : record.seenAt();
                if (!seenAt.isBefore(threshold)) {
                    ReplayRecord adjusted = new ReplayRecord(record.fingerprint(), record.digest(), seenAt);
                    seen.put(adjusted.token(), adjusted);
                }
            }
            trim();
        }
    }

    public synchronized boolean isReplay(String fingerprint, byte[] digest) {
        Instant now = clock.instant();
        evictExpired(now);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        String token = fingerprint + ':' + encoded;
        if (seen.containsKey(token)) {
            return true;
        }
        ReplayRecord record = new ReplayRecord(fingerprint, encoded, now);
        seen.put(token, record);
        trim();
        persist();
        return false;
    }

    public synchronized void removeFingerprint(String fingerprint) {
        if (seen.keySet().removeIf(key -> key.startsWith(fingerprint + ':'))) {
            persist();
        }
    }

    public synchronized int size() {
        evictExpired(clock.instant());
        return seen.size();
    }

    private void evictExpired(Instant now) {
        Instant threshold = now.minus(LIFETIME);
        if (seen.entrySet().removeIf(entry -> entry.getValue().seenAt().isBefore(threshold))) {
            persist();
        }
    }

    public boolean persistenceHealthy() {
        return store == null || store.healthy();
    }

    public String takePersistenceNotice() {
        return store == null ? "" : store.takeNotice();
    }

    public void close() {
        if (store != null) {
            store.close();
        }
    }

    private void trim() {
        while (seen.size() > MAX_ENTRIES) {
            Iterator<String> iterator = seen.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private void persist() {
        if (store != null) {
            store.save(List.copyOf(seen.values()));
        }
    }
}
