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
    void writesVersionTwoAtomicallyKeepsBackupAndNeverStoresSecrets() throws Exception {
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
        assertTrue(stored.contains("\"version\":2"));
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
        assertTrue(recovered.notice().contains("backup"));
        assertEquals(original, recovered.config());
        assertTrue(Files.isRegularFile(store.configFile()));
    }

    @Test
    void migratesDevelopmentVersionOneWithoutInventingTransportOverrides() throws Exception {
        ConfigStore store = new ConfigStore(temporaryDirectory);
        Files.createDirectories(store.configFile().getParent());
        UUID id = UUID.randomUUID();
        String versionOne = "{\"version\":1,\"encryptionEnabled\":true,\"activeChannelId\":\"" + id
            + "\",\"channels\":[{\"id\":\"" + id + "\",\"name\":\"Friends\","
            + "\"fingerprint\":\"0123-4567-89AB-CDEF\",\"binding\":null}]}";
        Files.writeString(store.configFile(), versionOne);

        LoadedConfig migrated = store.load();
        assertEquals(ChannelConfig.CURRENT_VERSION, migrated.config().version());
        assertTrue(migrated.config().encryptionEnabled());
        assertTrue(migrated.config().transportOverrides().isEmpty());
    }

    @Test
    void preservesCorruptConfigButLeavesNewerConfigUntouchedAndReadOnly() throws Exception {
        ConfigStore corruptStore = new ConfigStore(temporaryDirectory.resolve("corrupt"));
        Files.createDirectories(corruptStore.configFile().getParent());
        Files.writeString(corruptStore.configFile(), "not JSON");
        LoadedConfig recovered = corruptStore.load();
        assertTrue(recovered.writable());
        assertFalse(recovered.config().encryptionEnabled());
        assertTrue(recovered.config().channels().isEmpty());
        assertTrue(recovered.notice().contains("preserved"));
        try (var paths = Files.list(corruptStore.configFile().getParent())) {
            assertTrue(paths.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")));
        }

        ConfigStore newerStore = new ConfigStore(temporaryDirectory.resolve("newer"));
        Files.createDirectories(newerStore.configFile().getParent());
        String newer = "{\"version\":3,\"encryptionEnabled\":true,\"activeChannelId\":null,"
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
