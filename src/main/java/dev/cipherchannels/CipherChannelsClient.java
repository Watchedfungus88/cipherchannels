package dev.cipherchannels;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cipherchannels.channels.ChannelService;
import dev.cipherchannels.storage.ConfigStore;
import dev.cipherchannels.ui.ChannelManagerScreen;
import dev.cipherchannels.ui.ClientContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class CipherChannelsClient implements ClientModInitializer {
    public static final String MOD_ID = "cipherchannels";
    private static ChannelService channels;
    private static KeyMapping openScreenKey;

    @Override
    public void onInitializeClient() {
        channels = new ChannelService(new ConfigStore(FabricLoader.getInstance().getConfigDir()));
        openScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.cipherchannels.open", InputConstants.Type.KEYSYM, InputConstants.KEY_O,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"))));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> channels().close());
    }

    public static ChannelService channels() {
        if (channels == null) {
            throw new IllegalStateException("CipherChannels has not initialized");
        }
        return channels;
    }

    public static boolean matchesOpenScreenKey(InputConstants.Key key) {
        return openScreenKey != null && openScreenKey.matches(key);
    }

    private void onClientTick(Minecraft minecraft) {
        boolean shortcutPressed = openScreenKey.consumeClick();
        if (shortcutPressed && minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(new ChannelManagerScreen(null));
        }
        String notice = channels().takeStartupNotice();
        if (!notice.isEmpty()) {
            ClientContext.notice(notice);
        }
    }
}
