package dev.cipherchannels.channels;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayCacheTest {
    @Test
    void expiresEntriesAndEvictsLeastRecentlyUsedEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        ReplayCache cache = new ReplayCache(clock);
        byte[] first = {1};
        assertFalse(cache.isReplay("AAAA-BBBB-CCCC-DDDD", first));
        assertTrue(cache.isReplay("AAAA-BBBB-CCCC-DDDD", first));
        clock.advance(ReplayCache.LIFETIME);
        assertFalse(cache.isReplay("AAAA-BBBB-CCCC-DDDD", first));

        for (int index = 0; index <= ReplayCache.MAX_ENTRIES; index++) {
            byte[] digest = new byte[32];
            digest[0] = (byte) (index >>> 8);
            digest[1] = (byte) index;
            assertFalse(cache.isReplay("EEEE-FFFF-GGGG-HHHH", digest));
            Arrays.fill(digest, (byte) 0);
        }
        assertEquals(ReplayCache.MAX_ENTRIES, cache.size());
        assertFalse(cache.isReplay("EEEE-FFFF-GGGG-HHHH", new byte[32]));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(java.time.Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
