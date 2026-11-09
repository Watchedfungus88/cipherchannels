package dev.cipherchannels.storage;

import dev.cipherchannels.channels.ChannelService;
import dev.cipherchannels.channels.OutboundResult;
import dev.cipherchannels.crypto.ChannelKeys;
import dev.cipherchannels.crypto.KeyMaterial;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "cipherchannels.test.nativeKeyring", matches = "true")
@Timeout(60)
class NativeKeyringTest {
    @TempDir Path directory;

    @Test
    void nativeStoreRoundTripsUpdatesIsolatesProfilesAndDeletesSyntheticKeys() {
        SecretStore first = new OsSecretStore(directory.resolve("first"));
        SecretStore second = new OsSecretStore(directory.resolve("second"));
        UUID id = UUID.randomUUID();
        try (KeyMaterial original = ChannelKeys.generate(); KeyMaterial replacement = ChannelKeys.generate()) {
            byte[] one = original.copyBytes();
            byte[] two = replacement.copyBytes();
            try {
                assertNull(first.load(id));
                first.save(id, one);
                assertArrayEquals(one, first.load(id));
                assertNull(second.load(id));
                first.save(id, two);
                assertArrayEquals(two, first.load(id));
                first.delete(id);
                first.delete(id);
                assertNull(first.load(id));
            } finally {
                Arrays.fill(one, (byte) 0);
                Arrays.fill(two, (byte) 0);
                first.delete(id);
            }
        }
    }

    @Test
    void realServiceRestoresAndForgetsAnOsStoredInvite() {
        ConfigStore config = new ConfigStore(directory.resolve("restart"));
        UUID id = null;
        SecretStore secrets = new OsSecretStore(config.configDirectory());
        try {
            String invite;
            try (ChannelService service = new ChannelService(config, Clock.systemUTC(), secrets)) {
                id = service.create("Synthetic keyring test").id();
                invite = service.inviteFor(id);
                service.setEnabled(true, null);
                service.setRememberInvite(id, true).join();
            }
            try (ChannelService service = new ChannelService(config, Clock.systemUTC(),
                new OsSecretStore(config.configDirectory()))) {
                service.rememberedKeysReady().join();
                assertEquals(invite, service.inviteFor(id));
                assertEquals(OutboundResult.Kind.ENCRYPTED, service.prepareOutgoing("synthetic test", null).kind());
                service.forgetAsync(id).join();
                assertNull(secrets.load(id));
            }
        } finally {
            if (id != null) secrets.delete(id);
        }
    }
}
