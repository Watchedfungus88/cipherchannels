package dev.cipherchannels.storage;

import dev.cipherchannels.channels.ChannelConfig;
import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.ServerBinding;
import dev.cipherchannels.protocol.TransportMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void writesVersionThreeAtomicallyKeepsBackupAndNeverStoresSecrets() throws Exception {
        ConfigStore store = new ConfigStore(temporaryDirectory);
        UUID id = UUID.randomUUID();
        ChannelRecord record = new ChannelRecord(id, "Friends", "0123-4567-89AB-CDEF", null);
        ServerBinding endpoint = ServerBinding.of("play.example", 25565);
        ChannelConfig first = ChannelConfig.empty().upsert(record, true)
            .withTransport(endpoint, TransportMode.ASCII_COMPATIBILITY);
        store.save(first);
        store.save(first.withEnabled(true));

        String stored = Files.readString(store.configFile());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("cipherchannels.json.bak")));
        assertTrue(stored.contains("\"version\":3"));
        assertTrue(stored.contains("ASCII_COMPATIBILITY"));
        assertFalse(stored.contains("CC1."));
        assertFalse(stored.contains("plaintext"));
        assertFalse(stored.contains("ciphertext"));
        LoadedConfig loaded = store.load();
        assertTrue(loaded.config().encryptionEnabled());
        assertEquals(TransportMode.ASCII_COMPATIBILITY, loaded.config().transportFor(endpoint));
    }

    @Test
    void recoversAValidBackupWhenThePrimaryFileIsMissing() throws Exception {
        ConfigStore store = new ConfigStore(temporaryDirectory);
        ChannelRecord record = new ChannelRecord(UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null);
        ChannelConfig original = ChannelConfig.empty().upsert(record, true);
        store.save(original);
        store.save(original.withEnabled(true));
        Files.delete(store.configFile());

        LoadedConfig recovered = store.load();
        assertTrue(recovered.writable());
        assertEquals("cipherchannels.notice.config.recovered_backup", recovered.notice());
        assertEquals(original, recovered.config());
        assertTrue(Files.isRegularFile(store.configFile()));
    }

    @Test
    void preservesEveryPreTwoDevelopmentConfigAndStartsFresh() throws Exception {
        for (int version : new int[] {1, 2}) {
            ConfigStore store = new ConfigStore(temporaryDirectory.resolve("v" + version));
            Files.createDirectories(store.configFile().getParent());
            UUID id = UUID.randomUUID();
            String obsolete = "{\"version\":" + version + ",\"encryptionEnabled\":true,\"activeChannelId\":\"" + id
                + "\",\"channels\":[{\"id\":\"" + id + "\",\"name\":\"Friends\","
                + "\"fingerprint\":\"0123-4567-89AB-CDEF\",\"binding\":null}]}";
            Files.writeString(store.configFile(), obsolete);
            LoadedConfig reset = store.load();
            assertEquals(ConfigLoadState.FIRST_RUN, reset.state());
            assertFalse(reset.config().encryptionEnabled());
            assertTrue(reset.config().channels().isEmpty());
            try (var paths = Files.list(store.configFile().getParent())) {
                assertTrue(paths.anyMatch(path -> path.getFileName().toString().contains(".pre-2.0-")));
            }
        }
    }

    @Test
    void corruptPrimaryRecoversValidVersionThreeBackup() throws Exception {
        ConfigStore store = new ConfigStore(temporaryDirectory.resolve("backup-recovery"));
        ChannelConfig expected = ChannelConfig.empty().upsert(
            new ChannelRecord(UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null), true);
        store.save(expected);
        store.save(expected.withEnabled(true));
        Files.writeString(store.configFile(), "broken");
        LoadedConfig recovered = store.load();
        assertEquals(ConfigLoadState.RECOVERED, recovered.state());
        assertEquals(expected, recovered.config());
        assertTrue(recovered.writable());
    }

    @Test
    void preservesCorruptConfigButLeavesNewerConfigUntouchedAndReadOnly() throws Exception {
        ConfigStore corruptStore = new ConfigStore(temporaryDirectory.resolve("corrupt"));
        Files.createDirectories(corruptStore.configFile().getParent());
        Files.writeString(corruptStore.configFile(), "not JSON");
        LoadedConfig recovered = corruptStore.load();
        assertFalse(recovered.writable());
        assertEquals(ConfigLoadState.SAFE_MODE_CORRUPT, recovered.state());
        assertFalse(recovered.config().encryptionEnabled());
        assertTrue(recovered.config().channels().isEmpty());
        assertEquals("cipherchannels.notice.config.corrupt", recovered.notice());
        try (var paths = Files.list(corruptStore.configFile().getParent())) {
            assertTrue(paths.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")));
        }

        ConfigStore newerStore = new ConfigStore(temporaryDirectory.resolve("newer"));
        Files.createDirectories(newerStore.configFile().getParent());
        String newer = "{\"version\":4,\"encryptionEnabled\":true,\"activeChannelId\":null,"
            + "\"channels\":[],\"transportOverrides\":[]}";
        Files.writeString(newerStore.configFile(), newer);
        LoadedConfig readOnly = newerStore.load();
        assertFalse(readOnly.writable());
        assertFalse(readOnly.config().encryptionEnabled());
        assertEquals(newer, Files.readString(newerStore.configFile()));
    }

    @Test
    void saveFailureDoesNotSilentlyContinue() throws Exception {
        Path fileInsteadOfDirectory = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(fileInsteadOfDirectory, "x");
        ConfigStore store = new ConfigStore(fileInsteadOfDirectory);
        assertThrows(IllegalStateException.class, () -> store.save(ChannelConfig.empty()));
    }
}
