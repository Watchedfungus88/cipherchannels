package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.storage.ConfigLoadState;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ConfigurationRecoveryScreen extends Screen {
    private final Screen parent;
    public ConfigurationRecoveryScreen(Screen parent) { super(Component.translatable("cipherchannels.recovery.title")); this.parent = parent; }
    @Override protected void init() {
        int panelWidth = Math.min(520, width - 24);
        int left = (width - panelWidth) / 2;
        addRenderableOnly(new StringWidget(left, 24, panelWidth, 18, title.copy().withStyle(ChatFormatting.RED), font));
        addRenderableOnly(new MultiLineTextWidget(left, 56, Component.translatable("cipherchannels.recovery." +
            CipherChannels.channels().loadState().name().toLowerCase()), font).setMaxWidth(panelWidth).setMaxRows(6));
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.recovery.open_folder"), ignored ->
            Util.getPlatform().openPath(CipherChannels.channels().configDirectory())).bounds(left, 132, panelWidth, 22).build());
        boolean recovered = CipherChannels.channels().loadState() == ConfigLoadState.RECOVERED;
        addRenderableWidget(Button.builder(Component.translatable(recovered
            ? "cipherchannels.recovery.continue" : "cipherchannels.recovery.reset"), ignored -> {
                if (recovered) { CipherChannels.channels().acknowledgeRecovery(); minecraft.setScreen(new ChannelManagerScreen(parent)); }
                else confirmReset();
            }).bounds(left, 164, panelWidth, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose()).bounds(width / 2 - 75, height - 28, 150, 20).build());
    }
    private void confirmReset() {
        BooleanConsumer callback = accepted -> {
            if (accepted) { CipherChannels.channels().resetUnsafeConfiguration(); minecraft.setScreen(new ChannelManagerScreen(parent)); }
            else minecraft.setScreen(this);
        };
        minecraft.setScreen(new ConfirmScreen(callback, Component.translatable("cipherchannels.recovery.confirm.title"),
            Component.translatable("cipherchannels.recovery.confirm.message"), Component.translatable("cipherchannels.recovery.confirm.yes"), Component.translatable("gui.cancel")));
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
