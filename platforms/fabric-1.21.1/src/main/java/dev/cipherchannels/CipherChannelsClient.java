package dev.cipherchannels;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cipherchannels.ui.ChannelManagerScreen;
import dev.cipherchannels.ui.ClientContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class CipherChannelsClient implements ClientModInitializer {
    private static KeyMapping openScreenKey;

    @Override
    public void onInitializeClient() {
        CipherChannels.initialize(FabricLoader.getInstance().getConfigDir());
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.cipherchannels.open", InputConstants.Type.KEYSYM, InputConstants.KEY_O,
            "key.category.cipherchannels.general"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> CipherChannels.shutdown());
    }

    private void onClientTick(Minecraft minecraft) {
        boolean shortcutPressed = openScreenKey.consumeClick();
        if (shortcutPressed && minecraft.screen == null) {
            minecraft.setScreen(new ChannelManagerScreen(null));
        }
        String notice = CipherChannels.channels().takeStartupNotice();
        if (!notice.isEmpty()) {
            ClientContext.notice(notice);
        }
    }
}
