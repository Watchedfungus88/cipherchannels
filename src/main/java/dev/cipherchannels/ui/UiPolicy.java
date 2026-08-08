package dev.cipherchannels.ui;

import dev.cipherchannels.channels.ChannelConfig;
import java.util.Objects;
import java.util.UUID;

public final class UiPolicy {
    public enum SwitchFlow { ALREADY_ACTIVE, IMMEDIATE, CONFIRM }

    private UiPolicy() {}

    public static int editorLimit(boolean encryptionIntent, int currentDraftCharacters) {
        return encryptionIntent || currentDraftCharacters > 256 ? 4096 : 256;
    }

    public static int textBackgroundColor(double opacity) {
        double clamped = Math.max(0.0D, Math.min(1.0D, opacity));
        return ((int) Math.round(clamped * 255.0D)) << 24;
    }

    public static SwitchFlow switchFlow(ChannelConfig config, UUID target) {
        Objects.requireNonNull(target, "target");
        if (target.equals(config.activeChannelId())) {
            return SwitchFlow.ALREADY_ACTIVE;
        }
        return config.encryptionEnabled() ? SwitchFlow.CONFIRM : SwitchFlow.IMMEDIATE;
    }
}
