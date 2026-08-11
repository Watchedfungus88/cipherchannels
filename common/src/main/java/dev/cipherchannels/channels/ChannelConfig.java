package dev.cipherchannels.channels;

import dev.cipherchannels.protocol.TransportMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ChannelConfig(int version, boolean encryptionEnabled, UUID activeChannelId,
                            List<ChannelRecord> channels, List<TransportOverride> transportOverrides) {
    public static final int CURRENT_VERSION = 3;
    public static final int MAX_CHANNELS = 64;
    public static final int MAX_TRANSPORT_OVERRIDES = 64;

    public ChannelConfig {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported local config version");
        }
        channels = List.copyOf(channels);
        transportOverrides = List.copyOf(transportOverrides);
        if (channels.size() > MAX_CHANNELS) {
            throw new IllegalArgumentException("Too many channel records");
        }
        if (transportOverrides.size() > MAX_TRANSPORT_OVERRIDES) {
            throw new IllegalArgumentException("Too many transport overrides");
        }
        long distinctIds = channels.stream().map(ChannelRecord::id).distinct().count();
        if (distinctIds != channels.size()) {
            throw new IllegalArgumentException("Duplicate channel identifier");
        }
        long boundCount = channels.stream().filter(channel -> channel.binding() != null).count();
        long distinctBindings = channels.stream().map(ChannelRecord::binding).filter(Objects::nonNull).distinct().count();
        if (boundCount != distinctBindings) {
            throw new IllegalArgumentException("Multiple channels cannot use the same server binding");
        }
        long distinctOverrides = transportOverrides.stream().map(TransportOverride::endpoint).distinct().count();
        if (distinctOverrides != transportOverrides.size()) {
            throw new IllegalArgumentException("Duplicate transport endpoint");
        }
        if (activeChannelId != null && channels.stream().noneMatch(channel -> channel.id().equals(activeChannelId))) {
            throw new IllegalArgumentException("Active channel is missing from configuration");
        }
    }

    public static ChannelConfig empty() {
        return new ChannelConfig(CURRENT_VERSION, false, null, List.of(), List.of());
    }

    public ChannelRecord activeChannel() {
        if (activeChannelId == null) {
            return null;
        }
        return channels.stream().filter(channel -> channel.id().equals(activeChannelId)).findFirst().orElse(null);
    }

    public TransportMode transportFor(ServerBinding endpoint) {
        if (endpoint == null) {
            return TransportMode.HIGH_CAPACITY;
        }
        return transportOverrides.stream()
            .filter(override -> override.endpoint().equals(endpoint))
            .map(TransportOverride::mode)
            .findFirst().orElse(TransportMode.HIGH_CAPACITY);
    }

    public ChannelConfig withEnabled(boolean enabled) {
        return new ChannelConfig(version, enabled, activeChannelId, channels, transportOverrides);
    }

    public ChannelConfig withActive(UUID id) {
        if (id != null && channels.stream().noneMatch(channel -> channel.id().equals(id))) {
            throw new IllegalArgumentException("Unknown active channel");
        }
        return new ChannelConfig(version, encryptionEnabled, id, channels, transportOverrides);
    }

    public ChannelConfig withTransport(ServerBinding endpoint, TransportMode mode) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(mode, "mode");
        List<TransportOverride> updated = new ArrayList<>(transportOverrides);
        updated.removeIf(override -> override.endpoint().equals(endpoint));
        if (mode == TransportMode.ASCII_COMPATIBILITY) {
            if (updated.size() == MAX_TRANSPORT_OVERRIDES) {
                throw new IllegalStateException("CipherChannels supports at most 64 server compatibility overrides");
            }
            updated.add(new TransportOverride(endpoint, mode));
        }
        return new ChannelConfig(version, encryptionEnabled, activeChannelId, channels, updated);
    }

    public ChannelConfig upsert(ChannelRecord record, boolean makeActive) {
        Objects.requireNonNull(record, "record");
        List<ChannelRecord> updated = new ArrayList<>(channels);
        int existing = -1;
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).id().equals(record.id())) {
                existing = index;
                break;
            }
        }
        if (existing >= 0) {
            updated.set(existing, record);
        } else {
            if (updated.size() == MAX_CHANNELS) {
                throw new IllegalStateException("CipherChannels supports at most 64 saved channels");
            }
            updated.add(record);
        }
        return new ChannelConfig(version, encryptionEnabled, makeActive ? record.id() : activeChannelId,
            updated, transportOverrides);
    }

    public ChannelConfig remove(UUID id) {
        List<ChannelRecord> updated = channels.stream().filter(channel -> !channel.id().equals(id)).toList();
        UUID newActive = id.equals(activeChannelId) ? null : activeChannelId;
        return new ChannelConfig(version, newActive == null ? false : encryptionEnabled,
            newActive, updated, transportOverrides);
    }
}
