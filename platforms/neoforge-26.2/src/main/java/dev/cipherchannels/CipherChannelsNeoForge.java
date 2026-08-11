package dev.cipherchannels;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cipherchannels.ui.ChannelManagerScreen;
import dev.cipherchannels.ui.ClientContext;
import dev.cipherchannels.ui.InviteClipboardGuard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CipherChannels.MOD_ID, dist = Dist.CLIENT)
public final class CipherChannelsNeoForge {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath(CipherChannels.MOD_ID, "general"));
    private static final KeyMapping OPEN_SCREEN = new KeyMapping("key.cipherchannels.open",
        InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY);

    public CipherChannelsNeoForge(IEventBus modBus, ModContainer container) {
        CipherChannels.initialize(FMLPaths.CONFIGDIR.get());
        modBus.addListener(CipherChannelsNeoForge::registerKeys);
        NeoForge.EVENT_BUS.addListener(CipherChannelsNeoForge::tick);
        NeoForge.EVENT_BUS.addListener(CipherChannelsNeoForge::shutdown);
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (ignored, parent) -> ChannelManagerScreen.create(parent));
    }

    private static void shutdown(GameShuttingDownEvent event) {
        InviteClipboardGuard.close();
        CipherChannels.shutdown();
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_SCREEN);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (OPEN_SCREEN.consumeClick() && minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(ChannelManagerScreen.create(null));
        }
        String notice = CipherChannels.channels().takeStartupNotice();
        if (!notice.isEmpty()) {
            ClientContext.notice(Component.translatable(notice));
        }
        ClientContext.checkSecurityNotices();
    }
}
