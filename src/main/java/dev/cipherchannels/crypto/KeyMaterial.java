package dev.cipherchannels.crypto;

import java.util.Arrays;
import java.util.Objects;

public final class KeyMaterial implements AutoCloseable {
    public static final int LENGTH = 32;

    private byte[] bytes;

    private KeyMaterial(byte[] bytes) {
        this.bytes = bytes;
    }

    public static KeyMaterial fromBytes(byte[] source) {
        Objects.requireNonNull(source, "source");
        if (source.length != LENGTH) {
            throw new IllegalArgumentException("CipherChannels keys must be exactly 256 bits");
        }
        return new KeyMaterial(source.clone());
    }

    public synchronized byte[] copyBytes() {
        ensureOpen();
        return bytes.clone();
    }

    public synchronized boolean isClosed() {
        return bytes == null;
    }

    @Override
    public synchronized void close() {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
            bytes = null;
        }
    }

    private void ensureOpen() {
        if (bytes == null) {
            throw new IllegalStateException("Channel key is no longer available in this session");
        }
    }
}
