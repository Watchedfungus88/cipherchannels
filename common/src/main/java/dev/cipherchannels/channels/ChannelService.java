package dev.cipherchannels.channels;

import dev.cipherchannels.crypto.ChannelIdentity;
import dev.cipherchannels.crypto.ChannelKeys;
import dev.cipherchannels.crypto.InviteCode;
import dev.cipherchannels.crypto.KeyMaterial;
import dev.cipherchannels.protocol.FrameCodec;
import dev.cipherchannels.protocol.FrameFailure;
import dev.cipherchannels.protocol.FramePreview;
import dev.cipherchannels.protocol.ParsedFrame;
import dev.cipherchannels.protocol.TransportMode;
import dev.cipherchannels.storage.ConfigStore;
import dev.cipherchannels.storage.ConfigLoadState;
import dev.cipherchannels.storage.LoadedConfig;
import dev.cipherchannels.storage.OsSecretStore;
import dev.cipherchannels.storage.SecretStore;
import java.time.Clock;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class ChannelService implements AutoCloseable {
    private final ConfigStore configStore;
    private final SessionKeyStore keys = new SessionKeyStore();
    private final ReplayCache replayCache;
    private final SecretStore secretStore;
    private final ExecutorService storageWorker = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().daemon(true).name("CipherChannels credentials").factory());
    private final Set<UUID> storagePending = new HashSet<>();
    private final CompletableFuture<Void> rememberedKeysReady;
    private boolean storageExclusive;
    private boolean closed;
    private long keyRevision;
    private ChannelConfig config;
    private boolean writable;
    private ConfigLoadState loadState;
    private final List<String> startupNotices = new ArrayList<>();

    public ChannelService(ConfigStore configStore) {
        this(configStore, Clock.systemUTC());
    }

    public ChannelService(ConfigStore configStore, Clock clock) {
        this(configStore, clock, new OsSecretStore(configStore.configDirectory()));
    }

    public ChannelService(ConfigStore configStore, Clock clock, SecretStore secretStore) {
        this.configStore = Objects.requireNonNull(configStore, "configStore");
        this.secretStore = Objects.requireNonNull(secretStore, "secretStore");
        this.replayCache = new ReplayCache(clock);
        LoadedConfig loaded = configStore.load();
        this.config = loaded.config();
        this.writable = loaded.writable();
        this.loadState = loaded.state();
        if (!loaded.notice().isEmpty()) startupNotices.add(loaded.notice());
        this.rememberedKeysReady = !loadState.locked() && config.channels().stream().anyMatch(ChannelRecord::rememberInvite)
            ? storageTask(null, () -> { restoreRememberedKeys(); return null; })
            : CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> rememberedKeysReady() {
        return rememberedKeysReady;
    }

    public synchronized long keyRevision() {
        return keyRevision;
    }

    public synchronized boolean storageBusy(UUID id) {
        return storageExclusive || storagePending.contains(id);
    }

    public synchronized CompletableFuture<Void> setRememberInvite(UUID id, boolean remember) {
        requireWritable();
        ChannelRecord record = mutableRecord(id);
        if (record.rememberInvite() == remember) return CompletableFuture.completedFuture(null);
        KeyMaterial key = keys.get(id);
        if (remember && key == null) throw new IllegalStateException("Load the invite before remembering it");
        long remembered = config.channels().stream().filter(ChannelRecord::rememberInvite).count()
            + storagePending.stream().filter(pending -> config.channels().stream()
                .anyMatch(channel -> channel.id().equals(pending) && !channel.rememberInvite())).count();
        if (remember && remembered >= SessionKeyStore.MAX_KEYS) {
            throw new IllegalStateException("At most 16 invites can be remembered");
        }
        byte[] saved = remember ? key.copyBytes() : null;
        return storageTask(id, () -> {
            try {
                return changeStoredKey(id, saved, () -> {
                    synchronized (this) {
                        persist(config.upsert(requireRecord(id).withRememberInvite(remember), false));
                        return null;
                    }
                });
            } finally {
                if (saved != null) Arrays.fill(saved, (byte) 0);
            }
        });
    }

    public synchronized String takeStartupNotice() {
        return startupNotices.isEmpty() ? "" : startupNotices.removeFirst();
    }

    public synchronized ChannelConfig config() {
        return config;
    }

    public synchronized boolean writable() {
        return writable;
    }

    public synchronized ConfigLoadState loadState() {
        return loadState;
    }

    public Path configDirectory() {
        return configStore.configDirectory();
    }

    public synchronized boolean hasSessionKey(UUID id) {
        return id != null && keys.get(id) != null;
    }

    public synchronized boolean canLoadSessionKey(UUID id) {
        return keys.canStore(id);
    }

    public synchronized ChannelRecord create(String localName) {
        KeyMaterial key = ChannelKeys.generate();
        UUID id = UUID.randomUUID();
        try {
            requireWritable();
            requireStorageIdle(null);
            ensureKeyCapacity(id);
            ChannelRecord record = new ChannelRecord(id, localName, ChannelIdentity.fingerprint(key), null);
            persist(config.upsert(record, true).withEnabled(false));
            keys.put(id, key);
            key = null;
            return record;
        } finally {
            if (key != null) {
                key.close();
            }
        }
    }

    public ChannelRecord importInvite(String invite, String requestedName) {
        return await(importInviteAsync(invite, requestedName));
    }

    public synchronized CompletableFuture<ChannelRecord> importInviteAsync(String invite, String requestedName) {
        ChannelRecord saved = savedChannelForInvite(invite);
        return saved != null && saved.rememberInvite() ? loadKeyAsync(saved.id(), invite)
            : CompletableFuture.completedFuture(importInviteImmediately(invite, requestedName));
    }

    private synchronized ChannelRecord importInviteImmediately(String invite, String requestedName) {
        KeyMaterial key = InviteCode.parse(invite);
        try {
            requireWritable();
            requireStorageIdle(null);
            String fingerprint = ChannelIdentity.fingerprint(key);
            ChannelRecord existing = config.channels().stream()
                .filter(record -> record.fingerprint().equals(fingerprint))
                .findFirst().orElse(null);
            if (existing != null) {
                requireStorageIdle(existing.id());
                ensureKeyCapacity(existing.id());
                keys.put(existing.id(), key);
                key = null;
                return existing;
            }
            ChannelRecord record = new ChannelRecord(UUID.randomUUID(), requestedName, fingerprint, null);
            ensureKeyCapacity(record.id());
            persist(config.upsert(record, true).withEnabled(false));
            keys.put(record.id(), key);
            key = null;
            return record;
        } finally {
            if (key != null) {
                key.close();
            }
        }
    }

    public synchronized ChannelRecord savedChannelForInvite(String invite) {
        try (KeyMaterial key = InviteCode.parse(invite)) {
            String fingerprint = ChannelIdentity.fingerprint(key);
            return config.channels().stream()
                .filter(record -> record.fingerprint().equals(fingerprint))
                .findFirst().orElse(null);
        }
    }

    public ChannelRecord loadKey(UUID id, String invite) {
        return await(loadKeyAsync(id, invite));
    }

    public synchronized CompletableFuture<ChannelRecord> loadKeyAsync(UUID id, String invite) {
        ChannelRecord record = mutableRecord(id);
        KeyMaterial key = InviteCode.parse(invite);
        try {
            if (!record.fingerprint().equals(ChannelIdentity.fingerprint(key))) {
                throw new IllegalArgumentException("Invite does not belong to this channel");
            }
            ensureKeyCapacity(id);
            if (record.rememberInvite()) {
                KeyMaterial loaded = key;
                CompletableFuture<ChannelRecord> result = storageTask(id, () -> {
                    byte[] saved = loaded.copyBytes();
                    boolean stored = false;
                    try {
                        ChannelRecord updated = changeStoredKey(id, saved, () -> {
                            synchronized (this) {
                                requireWritable();
                                ensureKeyCapacity(id);
                                keys.put(id, loaded);
                                return requireRecord(id);
                            }
                        });
                        stored = true;
                        return updated;
                    } finally {
                        Arrays.fill(saved, (byte) 0);
                        if (!stored) loaded.close();
                    }
                });
                key = null;
                return result;
            }
            keys.put(id, key);
            key = null;
            return CompletableFuture.completedFuture(record);
        } finally {
            if (key != null) {
                key.close();
            }
        }
    }

    public synchronized String inviteFor(UUID id) {
        KeyMaterial key = keys.get(id);
        if (key == null) {
            throw new IllegalStateException("Re-import this channel invite before copying it");
        }
        return InviteCode.create(key);
    }

    public synchronized void setEnabled(boolean enabled, ServerBinding currentServer) {
        if (enabled) {
            requireReadyFor(config.activeChannelId(), currentServer);
        }
        persist(config.withEnabled(enabled));
    }

    public synchronized void select(UUID id, ServerBinding currentServer) {
        requireRecord(id);
        if (config.encryptionEnabled()) {
            requireReadyFor(id, currentServer);
        }
        persist(config.withActive(id));
    }

    public synchronized void select(UUID id) {
        select(id, null);
    }

    public synchronized boolean canKeepEncryptionOn(UUID id, ServerBinding currentServer) {
        try {
            requireReadyFor(id, currentServer);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public synchronized void rename(UUID id, String newName) {
        ChannelRecord record = mutableRecord(id);
        persist(config.upsert(record.renamed(newName), false));
    }

    public synchronized void bind(UUID id, ServerBinding binding) {
        mutableRecord(id);
        Objects.requireNonNull(binding, "binding");
        for (ChannelRecord candidate : config.channels()) {
            if (!candidate.id().equals(id) && binding.equals(candidate.binding())) {
                throw new IllegalStateException("Another channel is already bound to this server");
            }
        }
        persist(config.upsert(requireRecord(id).withBinding(binding), false));
    }

    public synchronized void unbind(UUID id) {
        persist(config.upsert(mutableRecord(id).withBinding(null), false));
    }

    public synchronized void setTransport(ServerBinding endpoint, TransportMode mode) {
        if (endpoint == null) {
            throw new IllegalStateException("Transport compatibility can only be set for multiplayer servers");
        }
        persist(config.withTransport(endpoint, mode));
    }

    public synchronized TransportMode transportFor(ServerBinding endpoint) {
        return config.transportFor(endpoint);
    }

    public synchronized ChannelRecord boundChannelFor(ServerBinding endpoint) {
        if (endpoint == null) {
            return null;
        }
        return config.channels().stream().filter(channel -> endpoint.equals(channel.binding())).findFirst().orElse(null);
    }

    public void forget(UUID id) {
        await(forgetAsync(id));
    }

    public synchronized CompletableFuture<Void> forgetAsync(UUID id) {
        requireWritable();
        ChannelRecord old = mutableRecord(id);
        return storageTask(id, () -> {
            Supplier<Void> commit = () -> {
                synchronized (this) {
                    persist(config.remove(id));
                    keys.remove(id);
                    replayCache.removeFingerprint(old.fingerprint());
                    return null;
                }
            };
            return old.rememberInvite() ? changeStoredKey(id, null, commit) : commit.get();
        });
    }

    public ChannelRecord rotateKey(UUID id) {
        return await(rotateKeyAsync(id));
    }

    public synchronized CompletableFuture<ChannelRecord> rotateKeyAsync(UUID id) {
        requireWritable();
        ChannelRecord old = mutableRecord(id);
        return storageTask(id, () -> {
            KeyMaterial key = ChannelKeys.generate();
            byte[] saved = null;
            try {
                ChannelRecord replacement = new ChannelRecord(UUID.randomUUID(), old.name(),
                    ChannelIdentity.fingerprint(key), old.binding(), old.rememberInvite());
                KeyMaterial replacementKey = key;
                Supplier<ChannelRecord> commit = () -> {
                    synchronized (this) {
                        if (keys.get(id) == null) ensureKeyCapacity(replacement.id());
                        persist(config.remove(id).upsert(replacement, true).withEnabled(false));
                        keys.remove(id);
                        keys.put(replacement.id(), replacementKey);
                        replayCache.removeFingerprint(old.fingerprint());
                        return replacement;
                    }
                };
                if (old.rememberInvite()) {
                    saved = key.copyBytes();
                    byte[] replacementBytes = saved;
                    changeStoredKey(id, null, () -> changeStoredKey(replacement.id(), replacementBytes, commit));
                } else {
                    commit.get();
                }
                key = null;
                return replacement;
            } finally {
                if (key != null) key.close();
                if (saved != null) Arrays.fill(saved, (byte) 0);
            }
        });
    }

    public void resetUnsafeConfiguration() {
        await(resetUnsafeConfigurationAsync());
    }

    public synchronized CompletableFuture<Void> resetUnsafeConfigurationAsync() {
        List<ChannelRecord> remembered = config.channels().stream().filter(ChannelRecord::rememberInvite).toList();
        return storageTask(null, () -> deleteRemembered(remembered, 0, () -> {
            synchronized (this) {
                if (closed) throw new IllegalStateException("CipherChannels is closed");
                LoadedConfig loaded = configStore.resetUnsafeConfiguration();
                config = loaded.config();
                writable = loaded.writable();
                loadState = loaded.state();
                startupNotices.clear();
                if (!loaded.notice().isEmpty()) startupNotices.add(loaded.notice());
                keys.close();
                if (!loaded.writable()) throw new IllegalStateException("Could not reset settings");
                return null;
            }
        }));
    }

    public synchronized ChannelStatus status(ServerBinding currentServer) {
        if (loadState.locked()) {
            return new ChannelStatus(TransportState.CONFIG_LOCKED, null);
        }
        if (!config.encryptionEnabled()) {
            return new ChannelStatus(TransportState.OFF, config.activeChannel());
        }
        ChannelRecord active = config.activeChannel();
        if (active == null || keys.get(active.id()) == null) {
            return new ChannelStatus(TransportState.NO_CHANNEL, active);
        }
        if (active.binding() != null && !active.binding().equals(currentServer)) {
            return new ChannelStatus(TransportState.SUSPENDED, active);
        }
        return new ChannelStatus(TransportState.READY, active);
    }

    public synchronized MessagePreflight preflightOutgoing(String normalizedMessage, ServerBinding currentServer) {
        ChannelStatus channelStatus = status(currentServer);
        if (channelStatus.state() == TransportState.OFF) {
            return MessagePreflight.passthrough(channelStatus);
        }
        TransportMode transport = config.transportFor(currentServer);
        if (channelStatus.state() == TransportState.CONFIG_LOCKED) {
            return MessagePreflight.blocked(channelStatus, transport, null, OutboundBlockReason.CONFIG_LOCKED);
        }
        if (channelStatus.state() == TransportState.NO_CHANNEL) {
            return MessagePreflight.blocked(channelStatus, transport, null, OutboundBlockReason.NO_CHANNEL);
        }
        if (channelStatus.state() == TransportState.SUSPENDED) {
            return MessagePreflight.blocked(channelStatus, transport, null, OutboundBlockReason.BINDING_MISMATCH);
        }
        FramePreview preview = FrameCodec.preview(normalizedMessage, transport);
        if (preview.ready()) {
            return MessagePreflight.ready(channelStatus, preview);
        }
        OutboundBlockReason reason = switch (preview.status()) {
            case EMPTY -> OutboundBlockReason.EMPTY;
            case MALFORMED_UNICODE -> OutboundBlockReason.MALFORMED_UNICODE;
            case SOURCE_TOO_LARGE -> OutboundBlockReason.SOURCE_TOO_LARGE;
            case DOES_NOT_FIT -> OutboundBlockReason.DOES_NOT_FIT;
            case READY_RAW, READY_COMPRESSED -> throw new IllegalStateException("Ready preview was not handled");
        };
        return MessagePreflight.blocked(channelStatus, transport, preview, reason);
    }

    public synchronized OutboundResult prepareOutgoing(String normalizedMessage, ServerBinding currentServer) {
        MessagePreflight preflight = preflightOutgoing(normalizedMessage, currentServer);
        if (preflight.kind() == MessagePreflight.Kind.PASSTHROUGH) {
            return OutboundResult.passthrough(preflight);
        }
        if (!preflight.ready()) {
            return OutboundResult.blocked(preflight);
        }
        try {
            KeyMaterial key = keys.get(preflight.channelStatus().activeChannel().id());
            return OutboundResult.encrypted(FrameCodec.encrypt(key, normalizedMessage, preflight.transport()), preflight);
        } catch (RuntimeException exception) {
            MessagePreflight failed = MessagePreflight.blocked(preflight.channelStatus(), preflight.transport(),
                preflight.frame(), OutboundBlockReason.ENCRYPTION_FAILED);
            return OutboundResult.blocked(failed);
        }
    }

    public synchronized IncomingResult decryptIncoming(String wire) {
        ParsedFrame frame;
        try {
            frame = FrameCodec.parse(wire);
        } catch (FrameCodec.FrameFormatException exception) {
            return IncomingResult.failed(exception.failure());
        }
        try {
            List<Map.Entry<UUID, KeyMaterial>> candidates = orderedKeys();
            if (candidates.isEmpty()) {
                return IncomingResult.failed(FrameFailure.UNKNOWN_CHANNEL);
            }
            boolean recognized = false;
            boolean authenticatedInvalid = false;
            for (Map.Entry<UUID, KeyMaterial> candidate : candidates) {
                if (!FrameCodec.matchesRecognitionHint(candidate.getValue(), frame)) {
                    continue;
                }
                recognized = true;
                try {
                    String plaintext = FrameCodec.decrypt(candidate.getValue(), frame);
                    ChannelRecord channel = requireRecord(candidate.getKey());
                    byte[] digest = FrameCodec.frameDigest(frame);
                    try {
                        if (replayCache.isReplay(channel.fingerprint(), digest)) {
                            return IncomingResult.failed(FrameFailure.REPLAYED);
                        }
                    } finally {
                        java.util.Arrays.fill(digest, (byte) 0);
                    }
                    return IncomingResult.authenticated(plaintext, channel);
                } catch (FrameCodec.FrameAuthenticationException ignored) {
                    continue;
                } catch (FrameCodec.FrameContentException ignored) {
                    authenticatedInvalid = true;
                }
            }
            if (authenticatedInvalid) {
                return IncomingResult.failed(FrameFailure.AUTHENTICATED_INVALID);
            }
            return IncomingResult.failed(recognized ? FrameFailure.AUTHENTICATION_FAILED : FrameFailure.UNKNOWN_CHANNEL);
        } finally {
            frame.close();
        }
    }

    private List<Map.Entry<UUID, KeyMaterial>> orderedKeys() {
        List<Map.Entry<UUID, KeyMaterial>> result = new ArrayList<>(keys.snapshot().entrySet());
        UUID active = config.activeChannelId();
        result.sort(Comparator.comparing(entry -> !entry.getKey().equals(active)));
        return result;
    }

    private void requireReadyFor(UUID id, ServerBinding currentServer) {
        ChannelRecord target = requireRecord(id);
        if (keys.get(target.id()) == null) {
            throw new IllegalStateException("Re-import this channel invite before enabling encrypted chat");
        }
        if (target.binding() != null && !target.binding().equals(currentServer)) {
            throw new IllegalStateException("This channel is bound to " + target.binding().displayName());
        }
    }

    private ChannelRecord requireRecord(UUID id) {
        return config.channels().stream().filter(channel -> channel.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown channel"));
    }

    private void ensureKeyCapacity(UUID id) {
        if (!keys.canStore(id)) {
            throw new IllegalStateException("Forget another session channel before importing a new one");
        }
    }

    private void persist(ChannelConfig next) {
        requireWritable();
        configStore.save(next);
        config = next;
    }

    private void requireWritable() {
        if (closed) throw new IllegalStateException("CipherChannels is closed");
        if (!writable) {
            throw new IllegalStateException("CipherChannels settings are read-only until the configuration is repaired");
        }
    }

    private ChannelRecord mutableRecord(UUID id) {
        if (closed) throw new IllegalStateException("CipherChannels is closed");
        requireStorageIdle(id);
        return requireRecord(id);
    }

    private void requireStorageIdle(UUID id) {
        if (storageExclusive || (id != null && storagePending.contains(id))) {
            throw new IllegalStateException("Credential storage is busy");
        }
    }

    private synchronized <T> CompletableFuture<T> storageTask(UUID id, Supplier<T> action) {
        if (closed) throw new IllegalStateException("CipherChannels is closed");
        requireStorageIdle(id);
        if (id == null) {
            if (!storagePending.isEmpty()) throw new IllegalStateException("Credential storage is busy");
            storageExclusive = true;
        } else {
            storagePending.add(id);
        }
        keyRevision++;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.get();
            } catch (LinkageError exception) {
                throw new IllegalStateException("OS credential storage is unavailable");
            } finally {
                synchronized (this) {
                    if (id == null) storageExclusive = false;
                    else storagePending.remove(id);
                    keyRevision++;
                }
            }
        }, storageWorker);
    }

    private <T> T changeStoredKey(UUID id, byte[] next, Supplier<T> commit) {
        byte[] previous;
        try {
            previous = secretStore.load(id);
        } catch (SecretStore.InvalidSecretException exception) {
            previous = null;
        }
        if (previous != null && previous.length != KeyMaterial.LENGTH) {
            Arrays.fill(previous, (byte) 0);
            previous = null;
        }
        try {
            try {
                if (next == null) secretStore.delete(id);
                else secretStore.save(id, next);
                return commit.get();
            } catch (RuntimeException | LinkageError failure) {
                try {
                    if (previous == null) secretStore.delete(id);
                    else secretStore.save(id, previous);
                } catch (RuntimeException | LinkageError rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } finally {
            if (previous != null) Arrays.fill(previous, (byte) 0);
        }
    }

    private <T> T deleteRemembered(List<ChannelRecord> records, int index, Supplier<T> commit) {
        return index == records.size() ? commit.get()
            : changeStoredKey(records.get(index).id(), null, () -> deleteRemembered(records, index + 1, commit));
    }

    private void restoreRememberedKeys() {
        List<ChannelRecord> remembered;
        synchronized (this) {
            remembered = new ArrayList<>(config.channels().stream().filter(ChannelRecord::rememberInvite).toList());
            remembered.sort(Comparator.comparing(record -> !record.id().equals(config.activeChannelId())));
        }
        boolean failed = false;
        for (ChannelRecord record : remembered) {
            synchronized (this) {
                if (closed) return;
            }
            byte[] raw = null;
            KeyMaterial key = null;
            try {
                raw = secretStore.load(record.id());
                if (raw == null) throw new IllegalStateException("Remembered invite is unavailable");
                key = KeyMaterial.fromBytes(raw);
                if (!record.fingerprint().equals(ChannelIdentity.fingerprint(key))) {
                    throw new IllegalStateException("Remembered invite does not match the channel");
                }
                synchronized (this) {
                    if (!closed && keys.canStore(record.id())) {
                        keys.put(record.id(), key);
                        key = null;
                        keyRevision++;
                    }
                }
            } catch (RuntimeException | LinkageError exception) {
                failed = true;
            } finally {
                if (raw != null) Arrays.fill(raw, (byte) 0);
                if (key != null) key.close();
            }
        }
        if (failed) {
            synchronized (this) {
                startupNotices.add("cipherchannels.notice.invite_unavailable");
            }
        }
    }

    private static <T> T await(CompletableFuture<T> result) {
        try {
            return result.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException("Credential storage failed");
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        keys.close();
        storageWorker.shutdown();
    }
}
