package dev.cipherchannels.storage;

import dev.cipherchannels.channels.ChannelConfig;

public record LoadedConfig(ChannelConfig config, boolean writable, String notice) {
    public LoadedConfig {
        if (notice == null) {
            notice = "";
        }
    }
}
