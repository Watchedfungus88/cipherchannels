package dev.cipherchannels;

import dev.cipherchannels.channels.ChannelService;
import dev.cipherchannels.storage.ConfigStore;
import dev.cipherchannels.telemetry.HostContext;
import dev.cipherchannels.telemetry.Telemetry;
import java.nio.file.Path;

public final class CipherChannels {
    public static final String MOD_ID = "cipherchannels";
    private static ChannelService channels;

    private CipherChannels() {}

    public static void initialize(Path configDirectory, HostContext host) {
        if (channels == null) {
            channels = new ChannelService(new ConfigStore(configDirectory));
        }
        Telemetry.start(configDirectory, host);
    }

    public static ChannelService channels() {
        if (channels == null) {
            throw new IllegalStateException("CipherChannels has not initialized");
        }
        return channels;
    }

    public static void shutdown() {
        Telemetry.stop();
        if (channels != null) {
            channels.close();
            channels = null;
        }
    }
}
