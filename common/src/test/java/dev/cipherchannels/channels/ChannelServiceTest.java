package dev.cipherchannels.channels;

import dev.cipherchannels.protocol.FrameFailure;
import dev.cipherchannels.protocol.TransportMode;
import dev.cipherchannels.storage.ConfigStore;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void isFailClosedWithoutSessionKeyAndOnWrongStrictBinding() {
        ConfigStore store = new ConfigStore(temporaryDirectory);
        ChannelRecord record;
        try (ChannelService service = new ChannelService(store)) {
            record = service.create("Friends");
            assertFalse(service.config().encryptionEnabled());
            service.bind(record.id(), ServerBinding.of("Example.COM.", 25565));
            service.setEnabled(true, ServerBinding.of("example.com", 25565));

            assertEquals(OutboundResult.Kind.ENCRYPTED,
                service.prepareOutgoing("hello", ServerBinding.of("example.com", 25565)).kind());
            OutboundResult suspended = service.prepareOutgoing("hello", ServerBinding.of("elsewhere.example", 25565));
            assertEquals(OutboundResult.Kind.BLOCKED, suspended.kind());
            assertEquals(OutboundBlockReason.BINDING_MISMATCH, suspended.preflight().blockReason());
            assertNull(suspended.frame());
        }
        try (ChannelService restarted = new ChannelService(store)) {
            assertEquals(TransportState.NO_CHANNEL, restarted.status(ServerBinding.of("example.com", 25565)).state());
            OutboundResult blocked = restarted.prepareOutgoing("must not leak", ServerBinding.of("example.com", 25565));
            assertEquals(OutboundResult.Kind.BLOCKED, blocked.kind());
            assertEquals(OutboundBlockReason.NO_CHANNEL, blocked.preflight().blockReason());
            assertNotNull(record);
        }
    }

    @Test
    void createImportAndSwitchingAreExplicitAndAlwaysStartDisabled() {
        try (ChannelService service = new ChannelService(new ConfigStore(temporaryDirectory))) {
            ChannelRecord first = service.create("One");
            String invite = service.inviteFor(first.id());
            service.setEnabled(true, null);
            ChannelRecord second = service.create("Two");
            assertEquals(second.id(), service.config().activeChannelId());
            assertFalse(service.config().encryptionEnabled());

            service.select(first.id(), false, null);
            service.setEnabled(true, null);
            assertTrue(service.canKeepEncryptionOn(second.id(), null));
            service.select(second.id(), true, null);
            assertTrue(service.config().encryptionEnabled());
            assertEquals(second.id(), service.config().activeChannelId());

            ChannelRecord imported = service.importInvite(invite, "Same key");
            assertEquals(first.id(), imported.id());
            assertEquals(first.id(), service.config().activeChannelId());
            assertFalse(service.config().encryptionEnabled());
        }
    }

    @Test
    void defaultsToUnicodeAndPersistsOnlyExplicitAsciiEndpointOverrides() {
        ServerBinding first = ServerBinding.of("play.example", 25565);
        ServerBinding second = ServerBinding.of("other.example", 25570);
        ConfigStore store = new ConfigStore(temporaryDirectory);
        try (ChannelService service = new ChannelService(store)) {
            assertEquals(TransportMode.HIGH_CAPACITY, service.transportFor(first));
            assertEquals(TransportMode.HIGH_CAPACITY, service.transportFor(null));
            service.setTransport(first, TransportMode.ASCII_COMPATIBILITY);
            assertEquals(TransportMode.ASCII_COMPATIBILITY, service.transportFor(first));
            assertEquals(TransportMode.HIGH_CAPACITY, service.transportFor(second));
            service.setTransport(first, TransportMode.HIGH_CAPACITY);
            assertTrue(service.config().transportOverrides().isEmpty());
            assertThrows(IllegalStateException.class,
                () -> service.setTransport(null, TransportMode.ASCII_COMPATIBILITY));
        }
    }

    @Test
    void incomingDiagnosticsDistinguishUnknownAlteredValidAndReplay() {
        try (ChannelService sender = new ChannelService(new ConfigStore(temporaryDirectory.resolve("sender")));
             ChannelService receiver = new ChannelService(new ConfigStore(temporaryDirectory.resolve("receiver")));
             ChannelService stranger = new ChannelService(new ConfigStore(temporaryDirectory.resolve("stranger")))) {
            ChannelRecord created = sender.create("Friends");
            String invite = sender.inviteFor(created.id());
            receiver.importInvite(invite, "Friends locally");
            sender.setTransport(ServerBinding.of("ascii.example", 25565), TransportMode.ASCII_COMPATIBILITY);
            sender.setEnabled(true, ServerBinding.of("ascii.example", 25565));
            String wire = sender.prepareOutgoing("hello", ServerBinding.of("ascii.example", 25565)).frame();

            assertEquals(FrameFailure.UNKNOWN_CHANNEL, stranger.decryptIncoming(wire).failure());
            IncomingResult valid = receiver.decryptIncoming(wire);
            assertTrue(valid.authenticated());
            assertEquals("hello", valid.plaintext());
            assertEquals(FrameFailure.REPLAYED, receiver.decryptIncoming(wire).failure());

            byte[] bytes = Base64.getUrlDecoder().decode(wire);
            bytes[30] ^= 1;
            String altered = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            assertEquals(FrameFailure.AUTHENTICATION_FAILED, receiver.decryptIncoming(altered).failure());
        }
    }

    @Test
    void importAndConcurrentAuthenticatedProcessingAreSafe() throws Exception {
        try (ChannelService sender = new ChannelService(new ConfigStore(temporaryDirectory.resolve("sender")))) {
            ChannelRecord created = sender.create("Test friends");
            String invite = sender.inviteFor(created.id());
            sender.setEnabled(true, null);
            try (ChannelService receiver = new ChannelService(new ConfigStore(temporaryDirectory.resolve("receiver")))) {
                receiver.importInvite(invite, "Other local name");
                ExecutorService executor = Executors.newFixedThreadPool(6);
                try {
                    List<Callable<Boolean>> work = new ArrayList<>();
                    for (int index = 0; index < 128; index++) {
                        int messageNumber = index;
                        work.add(() -> {
                            OutboundResult outgoing = sender.prepareOutgoing("message-" + messageNumber, null);
                            return outgoing.kind() == OutboundResult.Kind.ENCRYPTED
                                && receiver.decryptIncoming(outgoing.frame()).authenticated();
                        });
                    }
                    List<Future<Boolean>> results = executor.invokeAll(work);
                    for (Future<Boolean> result : results) {
                        assertTrue(result.get());
                    }
                } finally {
                    executor.shutdownNow();
                }
            }
        }
    }

    @Test
    void enforcesStructuredMessageLimitsAndLiveKeyLimit() {
        try (ChannelService service = new ChannelService(new ConfigStore(temporaryDirectory))) {
            ChannelRecord current = null;
            for (int index = 0; index < 16; index++) {
                current = service.create("Channel " + index);
            }
            assertThrows(IllegalStateException.class, () -> service.create("Seventeenth live key"));
            assertNotNull(current);
            service.setEnabled(true, null);
            assertEquals(OutboundBlockReason.SOURCE_TOO_LARGE,
                service.preflightOutgoing("x".repeat(4097), null).blockReason());
            assertNotEquals(OutboundResult.Kind.PASSTHROUGH,
                service.prepareOutgoing("x".repeat(4097), null).kind());
        }
    }

    @Test
    void importedChannelsRequireSessionApprovalUntilFingerprintIsVerified() {
        Path senderPath = temporaryDirectory.resolve("verification-sender");
        Path receiverPath = temporaryDirectory.resolve("verification-receiver");
        try (ChannelService sender = new ChannelService(new ConfigStore(senderPath));
             ChannelService receiver = new ChannelService(new ConfigStore(receiverPath))) {
            ChannelRecord source = sender.create("Friends");
            ChannelRecord imported = receiver.importInvite(sender.inviteFor(source.id()), "Friends");
            assertEquals(VerificationState.UNVERIFIED, imported.verification());
            assertThrows(IllegalStateException.class, () -> receiver.setEnabled(true, null));
            receiver.setEnabled(true, null, true);
            receiver.setEnabled(false, null);
            receiver.setEnabled(true, null);
            receiver.markVerified(imported.id());
            assertEquals(VerificationState.VERIFIED, receiver.config().activeChannel().verification());
            receiver.markUnverified(imported.id());
            assertThrows(IllegalStateException.class, () -> receiver.setEnabled(true, null));
        }
    }

    @Test
    void compromisedReplacementPreservesLocalContextAndRemovesOldSecret() {
        try (ChannelService service = new ChannelService(new ConfigStore(temporaryDirectory.resolve("replacement")))) {
            ChannelRecord old = service.create("Close friends");
            ServerBinding binding = ServerBinding.of("play.example", 25565);
            service.bind(old.id(), binding);
            String oldInvite = service.inviteFor(old.id());
            service.setEnabled(true, binding);
            ChannelRecord replacement = service.replaceCompromised(old.id());
            assertNotEquals(old.id(), replacement.id());
            assertNotEquals(old.fingerprint(), replacement.fingerprint());
            assertEquals(old.name(), replacement.name());
            assertEquals(binding, replacement.binding());
            assertEquals(VerificationState.LOCAL_CREATED, replacement.verification());
            assertFalse(service.config().encryptionEnabled());
            assertThrows(IllegalStateException.class, () -> service.inviteFor(old.id()));
            assertNotEquals(oldInvite, service.inviteFor(replacement.id()));
        }
    }

    @Test
    void unsafeConfigurationBlocksChatUntilExplicitReset() throws Exception {
        Path path = temporaryDirectory.resolve("safe-mode");
        Files.createDirectories(path);
        Files.writeString(path.resolve("cipherchannels.json"), "not json");
        try (ChannelService service = new ChannelService(new ConfigStore(path))) {
            assertEquals(TransportState.CONFIG_LOCKED, service.status(null).state());
            assertEquals(OutboundBlockReason.CONFIG_LOCKED, service.preflightOutgoing("must not leak", null).blockReason());
            service.resetUnsafeConfiguration();
            assertEquals(TransportState.OFF, service.status(null).state());
        }
    }

    @Test
    void verifiedReplayHistorySurvivesRestart() {
        Path senderPath = temporaryDirectory.resolve("replay-sender");
        Path receiverPath = temporaryDirectory.resolve("replay-receiver");
        String invite;
        String frame;
        try (ChannelService sender = new ChannelService(new ConfigStore(senderPath));
             ChannelService receiver = new ChannelService(new ConfigStore(receiverPath))) {
            ChannelRecord source = sender.create("Friends");
            invite = sender.inviteFor(source.id());
            receiver.importInvite(invite, "Friends");
            sender.setEnabled(true, null);
            frame = sender.prepareOutgoing("persisted replay", null).frame();
            assertTrue(receiver.decryptIncoming(frame).authenticated());
        }
        try (ChannelService receiver = new ChannelService(new ConfigStore(receiverPath))) {
            receiver.importInvite(invite, "Friends");
            assertEquals(FrameFailure.REPLAYED, receiver.decryptIncoming(frame).failure());
        }
    }

    @Test
    void forgettingRemovesPersistentReplayHistoryForThatFingerprint() {
        Path senderPath = temporaryDirectory.resolve("forget-sender");
        Path receiverPath = temporaryDirectory.resolve("forget-receiver");
        String invite;
        String frame;
        try (ChannelService sender = new ChannelService(new ConfigStore(senderPath));
             ChannelService receiver = new ChannelService(new ConfigStore(receiverPath))) {
            ChannelRecord source = sender.create("Friends");
            invite = sender.inviteFor(source.id());
            ChannelRecord imported = receiver.importInvite(invite, "Friends");
            sender.setEnabled(true, null);
            frame = sender.prepareOutgoing("forget replay history", null).frame();
            assertTrue(receiver.decryptIncoming(frame).authenticated());
            receiver.forget(imported.id());
        }
        try (ChannelService receiver = new ChannelService(new ConfigStore(receiverPath))) {
            receiver.importInvite(invite, "Friends");
            assertTrue(receiver.decryptIncoming(frame).authenticated());
        }
    }

    @Test
    void replacementRemovesReplayHistoryForTheCompromisedFingerprint() {
        Path senderPath = temporaryDirectory.resolve("replace-replay-sender");
        Path receiverPath = temporaryDirectory.resolve("replace-replay-receiver");
        try (ChannelService sender = new ChannelService(new ConfigStore(senderPath));
             ChannelService receiver = new ChannelService(new ConfigStore(receiverPath))) {
            ChannelRecord source = sender.create("Friends");
            String invite = sender.inviteFor(source.id());
            ChannelRecord imported = receiver.importInvite(invite, "Friends");
            sender.setEnabled(true, null);
            String frame = sender.prepareOutgoing("replace replay history", null).frame();
            assertTrue(receiver.decryptIncoming(frame).authenticated());
            receiver.replaceCompromised(imported.id());
            receiver.importInvite(invite, "Old compromised key");
            assertTrue(receiver.decryptIncoming(frame).authenticated());
        }
    }

    @Test
    void replacementRollsBackWhenConfigurationCannotBeSaved() throws Exception {
        Path path = temporaryDirectory.resolve("replacement-rollback");
        ConfigStore store = new ConfigStore(path);
        try (ChannelService service = new ChannelService(store)) {
            ChannelRecord original = service.create("Friends");
            String invite = service.inviteFor(original.id());
            Path moved = temporaryDirectory.resolve("replacement-rollback-preserved");
            Files.move(path, moved);
            Files.writeString(path, "not a directory");
            assertThrows(IllegalStateException.class, () -> service.replaceCompromised(original.id()));
            assertEquals(original, service.config().activeChannel());
            assertEquals(invite, service.inviteFor(original.id()));
        }
    }

    @Test
    void recoveredConfigurationCanBeExplicitlyAcknowledged() throws Exception {
        Path path = temporaryDirectory.resolve("acknowledge-recovery");
        ConfigStore store = new ConfigStore(path);
        ChannelConfig config = ChannelConfig.empty().upsert(
            new ChannelRecord(java.util.UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null), true);
        store.save(config);
        store.save(config.withEnabled(true));
        Files.writeString(store.configFile(), "broken");
        try (ChannelService service = new ChannelService(store)) {
            assertEquals(dev.cipherchannels.storage.ConfigLoadState.RECOVERED, service.loadState());
            service.acknowledgeRecovery();
            assertEquals(dev.cipherchannels.storage.ConfigLoadState.NORMAL, service.loadState());
        }
    }
}
