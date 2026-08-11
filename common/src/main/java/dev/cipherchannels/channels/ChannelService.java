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
import dev.cipherchannels.storage.ReplayStore;
import java.time.Clock;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ChannelService implements AutoCloseable {
    private final ConfigStore configStore;
    private final SessionKeyStore keys = new SessionKeyStore();
    private final Set<UUID> unverifiedSessionAllowances = new HashSet<>();
    private final ReplayCache replayCache;
    private ChannelConfig config;
    private boolean writable;
    private ConfigLoadState loadState;
    private final List<String> startupNotices = new ArrayList<>();

    public ChannelService(ConfigStore configStore) {
        this(configStore, Clock.systemUTC());
    }

    public ChannelService(ConfigStore configStore, Clock clock) {
        this.configStore = Objects.requireNonNull(configStore, "configStore");
        this.replayCache = new ReplayCache(clock, new ReplayStore(configStore.configDirectory()));
        LoadedConfig loaded = configStore.load();
        this.config = loaded.config();
        this.writable = loaded.writable();
        this.loadState = loaded.state();
        if (!loaded.notice().isEmpty()) startupNotices.add(loaded.notice());
        String replayNotice = replayCache.takePersistenceNotice();
        if (!replayNotice.isEmpty()) startupNotices.add(replayNotice);
    }

    public synchronized String takeStartupNotice() {
        return startupNotices.isEmpty() ? replayCache.takePersistenceNotice() : startupNotices.removeFirst();
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

    public synchronized void acknowledgeRecovery() {
        if (loadState == ConfigLoadState.RECOVERED) loadState = ConfigLoadState.NORMAL;
    }

    public synchronized boolean replayPersistenceHealthy() {
        return replayCache.persistenceHealthy();
    }

    public Path configDirectory() {
        return configStore.configDirectory();
    }

    public synchronized boolean hasSessionKey(UUID id) {
        return id != null && keys.get(id) != null;
    }

    public synchronized ChannelRecord create(String localName) {
        KeyMaterial key = ChannelKeys.generate();
        UUID id = UUID.randomUUID();
        try {
            requireWritable();
            ensureKeyCapacity(id);
            ChannelRecord record = new ChannelRecord(id, localName, ChannelIdentity.fingerprint(key), null,
                VerificationState.LOCAL_CREATED);
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

    public synchronized ChannelRecord importInvite(String invite, String requestedName) {
        KeyMaterial key = InviteCode.parse(invite);
        try {
            requireWritable();
            String fingerprint = ChannelIdentity.fingerprint(key);
            ChannelRecord existing = config.channels().stream()
                .filter(record -> record.fingerprint().equals(fingerprint))
                .findFirst().orElse(null);
            ChannelRecord record = existing == null
                ? new ChannelRecord(UUID.randomUUID(), requestedName, fingerprint, null, VerificationState.UNVERIFIED)
                : existing;
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

    public synchronized String inviteFor(UUID id) {
        KeyMaterial key = keys.get(id);
        if (key == null) {
            throw new IllegalStateException("Re-import this channel invite before copying it");
        }
        return InviteCode.create(key);
    }

    public synchronized void setEnabled(boolean enabled, ServerBinding currentServer) {
        setEnabled(enabled, currentServer, false);
    }

    public synchronized void setEnabled(boolean enabled, ServerBinding currentServer, boolean allowUnverified) {
        if (enabled) {
            requireReadyFor(config.activeChannelId(), currentServer);
            ChannelRecord active = requireRecord(config.activeChannelId());
            boolean unverified = active.verification() == VerificationState.UNVERIFIED;
            if (unverified && !allowUnverified && !unverifiedSessionAllowances.contains(active.id())) {
                throw new IllegalStateException("Confirm this unverified channel before enabling it for this session");
            }
            if (unverified && allowUnverified) {
                unverifiedSessionAllowances.add(active.id());
            }
        }
        persist(config.withEnabled(enabled));
    }

    public synchronized boolean requiresVerificationWarning(UUID id) {
        return id != null && requireRecord(id).verification() == VerificationState.UNVERIFIED
            && !unverifiedSessionAllowances.contains(id);
    }

    public synchronized void select(UUID id, boolean keepEncryptionEnabled, ServerBinding currentServer) {
        requireRecord(id);
        boolean enabled = config.encryptionEnabled() && keepEncryptionEnabled;
        if (enabled) {
            requireReadyFor(id, currentServer);
            if (requiresVerificationWarning(id)) {
                throw new IllegalStateException("Confirm this unverified channel before keeping encryption on");
            }
        }
        persist(config.withActive(id).withEnabled(enabled));
    }

    public synchronized void select(UUID id) {
        select(id, false, null);
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
        ChannelRecord record = requireRecord(id);
        persist(config.upsert(record.renamed(newName), false));
    }

    public synchronized void bind(UUID id, ServerBinding binding) {
        Objects.requireNonNull(binding, "binding");
        for (ChannelRecord candidate : config.channels()) {
            if (!candidate.id().equals(id) && binding.equals(candidate.binding())) {
                throw new IllegalStateException("Another channel is already bound to this server");
            }
        }
        persist(config.upsert(requireRecord(id).withBinding(binding), false));
    }

    public synchronized void unbind(UUID id) {
        persist(config.upsert(requireRecord(id).withBinding(null), false));
    }

    public synchronized void markVerified(UUID id) {
        persist(config.upsert(requireRecord(id).withVerification(VerificationState.VERIFIED), false));
        unverifiedSessionAllowances.remove(id);
    }

    public synchronized void markUnverified(UUID id) {
        ChannelConfig updated = config.upsert(requireRecord(id).withVerification(VerificationState.UNVERIFIED), false);
        if (id.equals(config.activeChannelId())) {
            updated = updated.withEnabled(false);
        }
        persist(updated);
        unverifiedSessionAllowances.remove(id);
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

    public synchronized void forget(UUID id) {
        requireWritable();
        ChannelRecord old = requireRecord(id);
        ChannelConfig updated = config.remove(id);
        configStore.save(updated);
        config = updated;
        keys.remove(id);
        unverifiedSessionAllowances.remove(id);
        replayCache.removeFingerprint(old.fingerprint());
    }

    public synchronized ChannelRecord replaceCompromised(UUID id) {
        requireWritable();
        ChannelRecord old = requireRecord(id);
        KeyMaterial key = ChannelKeys.generate();
        ChannelRecord replacement = new ChannelRecord(UUID.randomUUID(), old.name(), ChannelIdentity.fingerprint(key),
            old.binding(), VerificationState.LOCAL_CREATED);
        try {
            ChannelConfig updated = config.remove(id).upsert(replacement, true).withEnabled(false);
            configStore.save(updated);
            config = updated;
            keys.remove(id);
            unverifiedSessionAllowances.remove(id);
            keys.put(replacement.id(), key);
            key = null;
            replayCache.removeFingerprint(old.fingerprint());
            return replacement;
        } finally {
            if (key != null) {
                key.close();
            }
        }
    }

    public synchronized void resetUnsafeConfiguration() {
        LoadedConfig loaded = configStore.resetUnsafeConfiguration();
        config = loaded.config();
        writable = loaded.writable();
        loadState = loaded.state();
        startupNotices.clear();
        if (!loaded.notice().isEmpty()) startupNotices.add(loaded.notice());
        keys.close();
        unverifiedSessionAllowances.clear();
    }

    public synchronized ChannelStatus status(ServerBinding currentServer) {
        if (loadState.safeMode()) {
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
        if (!writable) {
            throw new IllegalStateException("CipherChannels settings are read-only until the configuration is repaired");
        }
    }

    @Override
    public synchronized void close() {
        keys.close();
        unverifiedSessionAllowances.clear();
        replayCache.close();
    }
}
