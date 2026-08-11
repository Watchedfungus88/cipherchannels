package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.ChannelRecord;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ChannelSwitchScreen extends Screen {
    private final ChannelManagerScreen parent;
    private final ChannelRecord target;

    ChannelSwitchScreen(ChannelManagerScreen parent, ChannelRecord target) {
        super(Component.translatable("cipherchannels.switch.title"));
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(440, width - 24);
        int left = (width - panelWidth) / 2;
        addRenderableOnly(new MultiLineTextWidget(left, 50,
            Component.translatable("cipherchannels.switch.message", target.name()), font)
            .setMaxWidth(panelWidth).setMaxRows(4).setCentered(true));
        Button keepOn = addRenderableWidget(Button.builder(Component.translatable("cipherchannels.switch.keep_on"),
            ignored -> complete(true)).bounds(left, 116, panelWidth, 22).build());
        boolean unverified = CipherChannels.channels().requiresVerificationWarning(target.id());
        if (unverified || !CipherChannels.channels().canKeepEncryptionOn(target.id(), ClientContext.currentServer())) {
            keepOn.active = false;
            keepOn.setTooltip(Tooltip.create(Component.translatable(unverified
                ? "cipherchannels.disabled.unverified_switch" : "cipherchannels.disabled.not_ready")));
        }
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.switch.turn_off"),
            ignored -> complete(false)).bounds(left, 146, panelWidth, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left, 176, panelWidth, 22).build());
    }

    private void complete(boolean keepOn) {
        minecraft.gui.setScreen(parent);
        parent.performSwitch(target, keepOn);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
