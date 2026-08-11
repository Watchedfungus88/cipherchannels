package dev.cipherchannels.storage;

import dev.cipherchannels.channels.ReplayRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayStoreTest {
    private static final String FINGERPRINT = "0123-4567-89AB-CDEF";
    private static final String DIGEST_ONE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String DIGEST_TWO = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";

    @TempDir Path temporaryDirectory;

    @Test
    void shutdownFlushesNewestImmutableSnapshotWithoutSensitiveContent() throws Exception {
        ReplayRecord first = new ReplayRecord(FINGERPRINT, DIGEST_ONE, Instant.parse("2026-08-10T00:00:00Z"));
        ReplayRecord second = new ReplayRecord(FINGERPRINT, DIGEST_TWO, Instant.parse("2026-08-10T00:01:00Z"));
        ReplayStore store = new ReplayStore(temporaryDirectory);
        store.save(List.of(first));
        store.save(List.of(first, second));
        store.close();

        String json = Files.readString(temporaryDirectory.resolve("cipherchannels-replay.json"));
        assertTrue(json.contains(DIGEST_TWO));
        assertFalse(json.contains("plaintext"));
        assertFalse(json.contains("ciphertext"));
        try (ReplayStore loaded = new ReplayStore(temporaryDirectory)) {
            assertEquals(List.of(first, second), loaded.load().records());
        }
    }

    @Test
    void corruptPrimaryRecoversBackupAndBothCorruptFilesResetWithWarning() throws Exception {
        ReplayRecord first = new ReplayRecord(FINGERPRINT, DIGEST_ONE, Instant.parse("2026-08-10T00:00:00Z"));
        ReplayRecord second = new ReplayRecord(FINGERPRINT, DIGEST_TWO, Instant.parse("2026-08-10T00:01:00Z"));
        try (ReplayStore store = new ReplayStore(temporaryDirectory)) {
            store.save(List.of(first));
        }
        try (ReplayStore store = new ReplayStore(temporaryDirectory)) {
            store.save(List.of(second));
        }
        Path primary = temporaryDirectory.resolve("cipherchannels-replay.json");
        Path backup = temporaryDirectory.resolve("cipherchannels-replay.json.bak");
        Files.writeString(primary, "broken");
        try (ReplayStore recovered = new ReplayStore(temporaryDirectory)) {
            ReplayStore.LoadedReplay loaded = recovered.load();
            assertTrue(loaded.healthy());
            assertEquals(List.of(first), loaded.records());
            assertEquals("cipherchannels.notice.replay.recovered_primary", loaded.notice());
        }

        Files.writeString(primary, "broken again");
        Files.writeString(backup, "also broken");
        try (ReplayStore reset = new ReplayStore(temporaryDirectory)) {
            ReplayStore.LoadedReplay loaded = reset.load();
            assertFalse(loaded.healthy());
            assertTrue(loaded.records().isEmpty());
            assertEquals("cipherchannels.notice.replay.reset", loaded.notice());
        }
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")));
        }
    }

    @Test
    void asynchronousWriteFailureLeavesSessionProtectionMarkedUnhealthy() throws Exception {
        Path notDirectory = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(notDirectory, "x");
        ReplayStore store = new ReplayStore(notDirectory);
        store.save(List.of(new ReplayRecord(FINGERPRINT, DIGEST_ONE, Instant.now())));
        store.close();
        assertFalse(store.healthy());
        assertEquals("cipherchannels.notice.replay.write_failed", store.takeNotice());
    }

    @Test
    void validBackupSurvivesFailedPrimaryRestore() throws Exception {
        ReplayRecord first = new ReplayRecord(FINGERPRINT, DIGEST_ONE, Instant.parse("2026-08-10T00:00:00Z"));
        ReplayRecord second = new ReplayRecord(FINGERPRINT, DIGEST_TWO, Instant.parse("2026-08-10T00:01:00Z"));
        try (ReplayStore store = new ReplayStore(temporaryDirectory)) {
            store.save(List.of(first));
        }
        try (ReplayStore store = new ReplayStore(temporaryDirectory)) {
            store.save(List.of(second));
        }
        Path primary = temporaryDirectory.resolve("cipherchannels-replay.json");
        Path backup = temporaryDirectory.resolve("cipherchannels-replay.json.bak");
        Files.delete(primary);
        Files.createDirectory(primary);

        try (ReplayStore store = new ReplayStore(temporaryDirectory)) {
            ReplayStore.LoadedReplay loaded = store.load();
            assertFalse(loaded.healthy());
            assertEquals(List.of(first), loaded.records());
            assertEquals("cipherchannels.notice.replay.write_failed", loaded.notice());
        }
        assertTrue(Files.isRegularFile(backup));
    }

    @Test
    void nonCanonicalDigestIsRejected() throws Exception {
        String json = "{\"version\":1,\"entries\":[{\"fingerprint\":\"" + FINGERPRINT
            + "\",\"digest\":\"" + DIGEST_ONE.substring(0, 42)
            + "B\",\"seenAt\":\"2026-08-10T00:00:00Z\"}]}";
        Files.writeString(temporaryDirectory.resolve("cipherchannels-replay.json"), json);
        try (ReplayStore store = new ReplayStore(temporaryDirectory)) {
            ReplayStore.LoadedReplay loaded = store.load();
            assertFalse(loaded.healthy());
            assertTrue(loaded.records().isEmpty());
        }
    }
}
