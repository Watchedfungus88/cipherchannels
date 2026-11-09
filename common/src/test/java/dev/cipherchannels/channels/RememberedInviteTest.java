package dev.cipherchannels.channels;

import dev.cipherchannels.crypto.ChannelKeys;
import dev.cipherchannels.crypto.KeyMaterial;
import dev.cipherchannels.storage.ConfigStore;
import dev.cipherchannels.storage.SecretStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class RememberedInviteTest {
    @TempDir Path directory;

    private ChannelService open(MemoryStore secrets) {
        return new ChannelService(new ConfigStore(directory), Clock.systemUTC(), secrets);
    }

    @Test
    void defaultDoesNotAccessCredentialStorageOrRememberInvites() {
        MemoryStore secrets = new MemoryStore();
        UUID id;
        try (ChannelService service = open(secrets)) {
            ChannelRecord channel = service.create("Friends");
            id = channel.id();
            assertFalse(channel.rememberInvite());
        }
        try (ChannelService service = open(secrets)) {
            service.rememberedKeysReady().join();
            assertFalse(service.hasSessionKey(id));
            assertEquals(0, secrets.calls);
        }
    }

    @Test
    void optInSurvivesRestartWithoutChangingSelectionBindingOrEnabledIntent() throws Exception {
        MemoryStore secrets = new MemoryStore();
        UUID remembered;
        UUID temporary;
        String invite;
        ServerBinding server = ServerBinding.of("play.example", 25565);
        try (ChannelService service = open(secrets)) {
            ChannelRecord first = service.create("Friends");
            remembered = first.id();
            invite = service.inviteFor(remembered);
            temporary = service.create("Temporary").id();
            service.bind(remembered, server);
            service.select(remembered, server);
            service.setEnabled(true, server);
            service.setRememberInvite(remembered, true).join();
            assertTrue(service.config().activeChannel().rememberInvite());
            assertEquals(remembered, service.config().activeChannelId());
            assertTrue(service.config().encryptionEnabled());
            for (Path path : new Path[] {directory.resolve("cipherchannels.json"), directory.resolve("cipherchannels.json.bak")}) {
                String config = Files.readString(path);
                assertFalse(config.contains(invite));
                assertFalse(config.contains(invite.substring(4, 47)));
            }
        }
        try (ChannelService service = open(secrets)) {
            service.rememberedKeysReady().join();
            assertTrue(service.hasSessionKey(remembered));
            assertFalse(service.hasSessionKey(temporary));
            assertEquals(invite, service.inviteFor(remembered));
            assertEquals(remembered, service.config().activeChannelId());
            assertEquals(TransportState.READY, service.status(server).state());
            assertEquals(OutboundResult.Kind.ENCRYPTED, service.prepareOutgoing("hello", server).kind());
            assertEquals(OutboundResult.Kind.BLOCKED, service.prepareOutgoing("hello", null).kind());
        }
    }

    @Test
    void disablingDeletesOnlyTheSavedCopyAndKeepsTheCurrentSessionWorking() {
        MemoryStore secrets = new MemoryStore();
        UUID id;
        try (ChannelService service = open(secrets)) {
            id = service.create("Friends").id();
            service.setEnabled(true, null);
            service.setRememberInvite(id, true).join();
            service.setRememberInvite(id, false).join();
            assertFalse(secrets.values.containsKey(id));
            assertTrue(service.hasSessionKey(id));
            assertFalse(service.config().activeChannel().rememberInvite());
            assertEquals(OutboundResult.Kind.ENCRYPTED, service.prepareOutgoing("hello", null).kind());
        }
        try (ChannelService service = open(secrets)) {
            service.rememberedKeysReady().join();
            assertFalse(service.hasSessionKey(id));
            assertEquals(OutboundResult.Kind.BLOCKED, service.prepareOutgoing("hello", null).kind());
        }
    }

    @Test
    void failedSaveOrDeleteDoesNotChangeTheSettingOrLoseTheLiveKey() {
        MemoryStore secrets = new MemoryStore();
        try (ChannelService service = open(secrets)) {
            UUID id = service.create("Friends").id();
            String invite = service.inviteFor(id);
            secrets.failSave = true;
            assertThrows(CompletionException.class, () -> service.setRememberInvite(id, true).join());
            assertFalse(service.config().activeChannel().rememberInvite());
            assertFalse(secrets.values.containsKey(id));
            assertEquals(invite, service.inviteFor(id));
            secrets.failSave = false;
            service.setRememberInvite(id, true).join();
            secrets.failDelete = true;
            assertThrows(CompletionException.class, () -> service.setRememberInvite(id, false).join());
            assertTrue(service.config().activeChannel().rememberInvite());
            assertTrue(secrets.values.containsKey(id));
            assertEquals(invite, service.inviteFor(id));
        }
    }

    @Test
    void missingWrongOrUnavailableSecretsLeaveEnabledChatBlocked() {
        for (int failure = 0; failure < 3; failure++) {
            directory = directory.resolve("case-" + failure);
            MemoryStore secrets = new MemoryStore();
            UUID id;
            try (ChannelService service = open(secrets)) {
                id = service.create("Friends").id();
                service.setEnabled(true, null);
                service.setRememberInvite(id, true).join();
            }
            if (failure == 0) secrets.values.clear();
            else if (failure == 1) {
                try (KeyMaterial wrong = ChannelKeys.generate()) { secrets.values.put(id, wrong.copyBytes()); }
            } else secrets.failLoad = true;
            try (ChannelService service = open(secrets)) {
                service.rememberedKeysReady().join();
                assertTrue(service.config().encryptionEnabled());
                assertFalse(service.hasSessionKey(id));
                assertEquals(OutboundBlockReason.NO_CHANNEL, service.prepareOutgoing("never public", null).preflight().blockReason());
                assertEquals("cipherchannels.notice.invite_unavailable", service.takeStartupNotice());
            }
        }
    }

    @Test
    void manualLoadingAndImportRepairAMissingSavedSecret() {
        MemoryStore secrets = new MemoryStore();
        UUID id;
        String invite;
        try (ChannelService service = open(secrets)) {
            id = service.create("Friends").id();
            invite = service.inviteFor(id);
            service.setRememberInvite(id, true).join();
        }
        for (boolean importing : new boolean[] {false, true}) {
            secrets.values.clear();
            try (ChannelService service = open(secrets)) {
                service.rememberedKeysReady().join();
                if (importing) service.importInviteAsync(invite, "Ignored").join();
                else service.loadKeyAsync(id, invite).join();
                assertTrue(secrets.values.containsKey(id));
                assertTrue(service.hasSessionKey(id));
                assertEquals("Friends", service.config().activeChannel().name());
            }
            try (ChannelService service = open(secrets)) {
                service.rememberedKeysReady().join();
                assertEquals(invite, service.inviteFor(id));
            }
        }
    }

    @Test
    void forgetAndRotationRemoveTheOldCredentialAndRotationKeepsOptIn() {
        MemoryStore secrets = new MemoryStore();
        UUID replacementId;
        String replacementInvite;
        try (ChannelService service = open(secrets)) {
            UUID original = service.create("Friends").id();
            service.setRememberInvite(original, true).join();
            String originalInvite = service.inviteFor(original);
            ChannelRecord replacement = service.rotateKeyAsync(original).join();
            replacementId = replacement.id();
            replacementInvite = service.inviteFor(replacementId);
            assertTrue(replacement.rememberInvite());
            assertFalse(service.config().encryptionEnabled());
            assertNotEquals(originalInvite, replacementInvite);
            assertFalse(secrets.values.containsKey(original));
            assertTrue(secrets.values.containsKey(replacementId));
        }
        try (ChannelService service = open(secrets)) {
            service.rememberedKeysReady().join();
            assertEquals(replacementInvite, service.inviteFor(replacementId));
            service.forgetAsync(replacementId).join();
            assertTrue(service.config().channels().isEmpty());
            assertTrue(secrets.values.isEmpty());
            assertFalse(service.hasSessionKey(replacementId));
        }
    }

    @Test
    void failedRotationOrForgetKeepsTheOriginalChannelAndCredential() {
        MemoryStore secrets = new MemoryStore();
        try (ChannelService service = open(secrets)) {
            UUID id = service.create("Friends").id();
            service.setRememberInvite(id, true).join();
            String invite = service.inviteFor(id);
            secrets.failDelete = true;
            assertThrows(CompletionException.class, () -> service.rotateKeyAsync(id).join());
            assertEquals(id, service.config().activeChannelId());
            assertEquals(invite, service.inviteFor(id));
            assertEquals(1, secrets.values.size());
            assertThrows(CompletionException.class, () -> service.forgetAsync(id).join());
            assertEquals(id, service.config().activeChannelId());
            assertTrue(service.hasSessionKey(id));
        }
    }

    @Test
    void configWriteFailuresRollBackCredentialChanges() throws Exception {
        MemoryStore secrets = new MemoryStore();
        Path original = directory;
        try (ChannelService service = open(secrets)) {
            UUID id = service.create("Friends").id();
            service.setRememberInvite(id, true).join();
            String invite = service.inviteFor(id);
            Path preserved = original.resolveSibling(original.getFileName() + "-preserved");
            Files.move(original, preserved);
            try {
                Files.writeString(original, "not a directory");
                assertThrows(CompletionException.class, () -> service.setRememberInvite(id, false).join());
                assertTrue(secrets.values.containsKey(id));
                assertThrows(CompletionException.class, () -> service.rotateKeyAsync(id).join());
                assertEquals(1, secrets.values.size());
                assertThrows(CompletionException.class, () -> service.forgetAsync(id).join());
                assertEquals(id, service.config().activeChannelId());
                assertEquals(invite, service.inviteFor(id));
            } finally {
                Files.delete(original);
                Files.move(preserved, original);
            }
        }
    }

    @Test
    void blockedOsOperationDoesNotBlockChatOrAllowOverlappingChanges() throws Exception {
        MemoryStore secrets = new MemoryStore();
        try (ChannelService service = open(secrets)) {
            UUID id = service.create("Friends").id();
            service.setEnabled(true, null);
            secrets.saveEntered = new CountDownLatch(1);
            secrets.releaseSave = new CountDownLatch(1);
            CompletableFuture<Void> pending = service.setRememberInvite(id, true);
            try {
                assertTrue(secrets.saveEntered.await(2, TimeUnit.SECONDS));
                assertTrue(service.storageBusy(id));
                assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> assertEquals(OutboundResult.Kind.ENCRYPTED, service.prepareOutgoing("hello", null).kind()));
                assertThrows(IllegalStateException.class, () -> service.setRememberInvite(id, false));
                assertThrows(IllegalStateException.class, () -> service.forgetAsync(id));
                assertThrows(IllegalStateException.class, () -> service.rotateKeyAsync(id));
            } finally {
                secrets.releaseSave.countDown();
            }
            pending.join();
            assertFalse(service.storageBusy(id));
            service.setRememberInvite(id, false).join();
            service.setRememberInvite(id, true).join();
            assertTrue(service.config().activeChannel().rememberInvite());
        }
    }

    @Test
    void resetRemovesRememberedCredentialsButUnsafeConfigNeverLoadsThem() throws Exception {
        MemoryStore secrets = new MemoryStore();
        try (ChannelService service = open(secrets)) {
            UUID id = service.create("Friends").id();
            service.setRememberInvite(id, true).join();
            service.resetUnsafeConfigurationAsync().join();
            assertTrue(secrets.values.isEmpty());
            assertTrue(service.config().channels().isEmpty());
        }
        Files.deleteIfExists(directory.resolve("cipherchannels.json.bak"));
        Files.writeString(directory.resolve("cipherchannels.json"), "{\"version\":999}");
        int calls = secrets.calls;
        try (ChannelService service = open(secrets)) {
            service.rememberedKeysReady().join();
            assertEquals(TransportState.CONFIG_LOCKED, service.status(null).state());
            assertEquals(calls, secrets.calls);
        }
    }

    @Test
    void shutdownDuringSaveDoesNotResurrectKeysOrLeaveANewCredential() throws Exception {
        MemoryStore secrets = new MemoryStore();
        ChannelService service = open(secrets);
        UUID id = service.create("Friends").id();
        secrets.saveEntered = new CountDownLatch(1);
        secrets.releaseSave = new CountDownLatch(1);
        CompletableFuture<Void> pending = service.setRememberInvite(id, true);
        try {
            assertTrue(secrets.saveEntered.await(2, TimeUnit.SECONDS));
            service.close();
        } finally {
            secrets.releaseSave.countDown();
            service.close();
        }
        assertThrows(CompletionException.class, pending::join);
        assertFalse(service.hasSessionKey(id));
        assertFalse(secrets.values.containsKey(id));
    }

    @Test
    void restoresAllSixteenRememberedInvitesWithTheActiveOneFirst() {
        MemoryStore secrets = new MemoryStore();
        List<UUID> ids = new ArrayList<>();
        try (ChannelService service = open(secrets)) {
            for (int index = 0; index < SessionKeyStore.MAX_KEYS; index++) {
                UUID id = service.create("Channel " + index).id();
                ids.add(id);
                service.setRememberInvite(id, true).join();
            }
            service.setEnabled(true, null);
        }
        secrets.loadOrder.clear();
        try (ChannelService service = open(secrets)) {
            service.rememberedKeysReady().join();
            assertEquals(SessionKeyStore.MAX_KEYS, secrets.loadOrder.size());
            assertEquals(ids.getLast(), secrets.loadOrder.getFirst());
            for (UUID id : ids) assertTrue(service.hasSessionKey(id));
            assertFalse(service.canLoadSessionKey(null));
            assertEquals(TransportState.READY, service.status(null).state());
        }
    }

    @Test
    void rejectsSettingsThatWouldRememberMoreThanTheLiveKeyLimit() {
        List<ChannelRecord> records = new ArrayList<>();
        for (int index = 0; index <= SessionKeyStore.MAX_KEYS; index++) {
            records.add(new ChannelRecord(UUID.randomUUID(), "Channel " + index, "0123-4567-89AB-CDEF", null, true));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new ChannelConfig(ChannelConfig.CURRENT_VERSION, false, null, records, List.of()));
    }

    @Test
    void startupDoesNotBlockTheClientWhileWaitingForTheKeyring() throws Exception {
        MemoryStore secrets = new MemoryStore();
        UUID id;
        try (ChannelService service = open(secrets)) {
            id = service.create("Friends").id();
            service.setRememberInvite(id, true).join();
            service.setEnabled(true, null);
        }
        secrets.loadEntered = new CountDownLatch(1);
        secrets.releaseLoad = new CountDownLatch(1);
        try (ChannelService service = open(secrets)) {
            try {
                assertTrue(secrets.loadEntered.await(2, TimeUnit.SECONDS));
                assertFalse(service.rememberedKeysReady().isDone());
                assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> assertEquals(OutboundResult.Kind.BLOCKED, service.prepareOutgoing("never public", null).kind()));
            } finally {
                secrets.releaseLoad.countDown();
            }
            service.rememberedKeysReady().join();
            assertTrue(service.hasSessionKey(id));
            assertEquals(TransportState.READY, service.status(null).state());
        }
    }

    @Test
    void aCorruptNativeCredentialCanBeReplacedOrRemoved() {
        MemoryStore secrets = new MemoryStore();
        UUID id;
        String invite;
        try (ChannelService service = open(secrets)) {
            id = service.create("Friends").id();
            invite = service.inviteFor(id);
            service.setRememberInvite(id, true).join();
        }
        for (boolean repair : new boolean[] {true, false}) {
            secrets.invalid = true;
            try (ChannelService service = open(secrets)) {
                service.rememberedKeysReady().join();
                assertFalse(service.hasSessionKey(id));
                if (repair) {
                    service.loadKeyAsync(id, invite).join();
                    secrets.invalid = false;
                    assertTrue(service.hasSessionKey(id));
                    assertNotNull(secrets.load(id));
                } else {
                    service.setRememberInvite(id, false).join();
                    assertFalse(service.config().activeChannel().rememberInvite());
                    assertTrue(secrets.values.isEmpty());
                }
            }
        }
    }

    static final class MemoryStore implements SecretStore {
        final Map<UUID, byte[]> values = new HashMap<>();
        int calls;
        boolean failLoad;
        boolean failSave;
        boolean failDelete;
        boolean invalid;
        final List<UUID> loadOrder = new ArrayList<>();
        CountDownLatch loadEntered;
        CountDownLatch releaseLoad;
        CountDownLatch saveEntered;
        CountDownLatch releaseSave;

        @Override public synchronized byte[] load(UUID id) {
            calls++;
            loadOrder.add(id);
            if (loadEntered != null) {
                loadEntered.countDown();
                try {
                    if (!releaseLoad.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted");
                }
            }
            if (failLoad) throw new IllegalStateException("Unavailable");
            if (invalid) throw new SecretStore.InvalidSecretException();
            byte[] value = values.get(id);
            return value == null ? null : value.clone();
        }

        @Override public void save(UUID id, byte[] key) {
            if (saveEntered != null) {
                saveEntered.countDown();
                try {
                    if (!releaseSave.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted");
                }
            }
            synchronized (this) {
                calls++;
                if (failSave) throw new IllegalStateException("Unavailable");
                byte[] previous = values.put(id, key.clone());
                if (previous != null) Arrays.fill(previous, (byte) 0);
            }
        }

        @Override public synchronized void delete(UUID id) {
            calls++;
            if (failDelete) throw new IllegalStateException("Unavailable");
            byte[] removed = values.remove(id);
            if (removed != null) Arrays.fill(removed, (byte) 0);
        }
    }
}
