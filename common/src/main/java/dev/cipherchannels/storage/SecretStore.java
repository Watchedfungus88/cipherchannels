package dev.cipherchannels.storage;

import java.util.UUID;

public interface SecretStore {
    byte[] load(UUID channelId);
    void save(UUID channelId, byte[] key);
    void delete(UUID channelId);

    final class InvalidSecretException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public InvalidSecretException() { super("Stored credential is invalid"); }
    }
}
