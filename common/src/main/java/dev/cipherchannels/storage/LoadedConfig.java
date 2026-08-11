package dev.cipherchannels.storage;

import dev.cipherchannels.channels.ChannelConfig;

public record LoadedConfig(ChannelConfig config, boolean writable, ConfigLoadState state, String notice) {
    public LoadedConfig {
        if (state == null) {
            state = ConfigLoadState.NORMAL;
        }
        if (notice == null) {
            notice = "";
        }
    }
}
