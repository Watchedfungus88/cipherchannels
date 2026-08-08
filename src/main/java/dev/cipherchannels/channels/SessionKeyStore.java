package dev.cipherchannels.channels;

import dev.cipherchannels.crypto.KeyMaterial;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SessionKeyStore implements AutoCloseable {
    public static final int MAX_KEYS = 16;
    private final Map<UUID, KeyMaterial> keys = new LinkedHashMap<>();

    public synchronized void put(UUID id, KeyMaterial key) {
        KeyMaterial previous = keys.get(id);
        if (previous == null && keys.size() >= MAX_KEYS) {
            throw new IllegalStateException("CipherChannels supports at most 16 active session keys");
        }
        keys.put(id, key);
        if (previous != null && previous != key) {
            previous.close();
        }
    }

    public synchronized KeyMaterial get(UUID id) {
        return keys.get(id);
    }

    public synchronized Map<UUID, KeyMaterial> snapshot() {
        return Map.copyOf(keys);
    }

    public synchronized boolean canStore(UUID id) {
        return keys.containsKey(id) || keys.size() < MAX_KEYS;
    }

    public synchronized void remove(UUID id) {
        KeyMaterial key = keys.remove(id);
        if (key != null) {
            key.close();
        }
    }

    @Override
    public synchronized void close() {
        keys.values().forEach(KeyMaterial::close);
        keys.clear();
    }
}
