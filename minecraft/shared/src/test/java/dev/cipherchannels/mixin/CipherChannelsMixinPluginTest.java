package dev.cipherchannels.mixin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CipherChannelsMixinPluginTest {
    @Test void optionalChatPatchesMixinIsSelectedWithoutLoadingItsTarget() {
        var plugin = new CipherChannelsMixinPlugin();
        assertTrue(plugin.shouldApplyMixin("obro1961.chatpatches.ChatLog",
            "dev.cipherchannels.mixin.ChatPatchesChatLogMixin"));
    }
}
