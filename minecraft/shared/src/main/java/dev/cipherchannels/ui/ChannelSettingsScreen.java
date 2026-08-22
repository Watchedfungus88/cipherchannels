package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.ServerBinding;
import dev.cipherchannels.protocol.TransportMode;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

final class ChannelSettingsScreen extends SettingsScreenBase {
    private static final int WIDTH = 320;
    private final ChannelManagerScreen parent;
    private final ContentScroller scroller = new ContentScroller();
    private UUID channelId;

    ChannelSettingsScreen(ChannelManagerScreen parent, UUID channelId) {
        super(Component.translatable("cipherchannels.settings.title"));
        this.parent = parent;
        this.channelId = channelId;
    }

    @Override
    protected void init() {
        ChannelRecord channel = record();
        if (channel == null) {
            ClientContext.setScreen(parent);
            return;
        }
        int panelWidth = Math.min(WIDTH, width - 20);
        int left = (width - panelWidth) / 2;
        Component screenTitle = Component.translatable("cipherchannels.settings.channel_title", channel.name());
        addRenderableOnly(new StringWidget((width - font.width(screenTitle)) / 2, 14,
            font.width(screenTitle), font.lineHeight, screenTitle, font));
        scroller.reset(34, height - 34);
        int y = 38;

        y = heading(left, y, panelWidth, "cipherchannels.settings.access");
        y = text(left, y, panelWidth, Component.translatable(CipherChannels.channels().hasSessionKey(channel.id())
            ? "cipherchannels.settings.key_loaded" : "cipherchannels.settings.key_missing"),
            CipherChannels.channels().hasSessionKey(channel.id()) ? ChatFormatting.WHITE : ChatFormatting.YELLOW);
        if (!CipherChannels.channels().hasSessionKey(channel.id())) {
            y = button(left, y + 4, panelWidth, Component.translatable("cipherchannels.settings.load"),
                ignored -> ClientContext.setScreen(ChannelFormScreen.loadKey(this, channel.id())));
        }

        y = heading(left, y + 8, panelWidth, "cipherchannels.settings.share");
        y = text(left, y, panelWidth, Component.translatable("cipherchannels.settings.fingerprint",
            channel.fingerprint()), ChatFormatting.WHITE);
        y = text(left, y, panelWidth, Component.translatable("cipherchannels.settings.fingerprint_help"),
            ChatFormatting.GRAY);
        int half = (panelWidth - 4) / 2;
        add(Button.builder(Component.translatable("cipherchannels.settings.copy_fingerprint"),
            ignored -> copyFingerprint(channel)).bounds(left, y + 4, half, 20).build());
        Button invite = add(Button.builder(Component.translatable("cipherchannels.settings.copy_invite"),
            ignored -> copyInvite(channel)).bounds(left + half + 4, y + 4, half, 20).build());
        invite.setTooltip(Tooltip.create(Component.translatable("cipherchannels.settings.invite_warning")));
        if (!CipherChannels.channels().hasSessionKey(channel.id())) {
            disable(invite, Component.translatable("cipherchannels.disabled.key_needed", channel.name()));
        }
        y += 30;
        y = text(left, y, panelWidth, Component.translatable("cipherchannels.settings.invite_warning"),
            ChatFormatting.YELLOW);

        y = heading(left, y + 8, panelWidth, "cipherchannels.settings.server");
        ServerBinding endpoint = ClientContext.currentServer();
        y = text(left, y, panelWidth, Component.translatable("cipherchannels.settings.endpoint",
            endpoint == null ? Component.translatable("cipherchannels.endpoint.singleplayer")
                : endpoint.displayName()), ChatFormatting.GRAY);
        Component bindingLabel = channel.binding() == null
            ? Component.translatable("cipherchannels.settings.bind")
            : Component.translatable("cipherchannels.settings.unbind", channel.binding().displayName());
        Button binding = add(Button.builder(bindingLabel, ignored -> toggleBinding())
            .bounds(left, y + 4, panelWidth, 20).build());
        if (channel.binding() == null && endpoint == null) {
            disable(binding, Component.translatable("cipherchannels.disabled.multiplayer_only"));
        }
        y += 30;
        TransportMode mode = CipherChannels.channels().transportFor(endpoint);
        Button transport = add(Button.builder(Component.translatable("cipherchannels.settings.transport",
                ClientContext.transportName(mode)), ignored -> toggleTransport())
            .bounds(left, y, panelWidth, 20).build());
        if (endpoint == null) disable(transport, Component.translatable("cipherchannels.disabled.multiplayer_only"));
        else transport.setTooltip(Tooltip.create(Component.translatable("cipherchannels.settings.transport_tooltip")));
        y += 28;

        y = heading(left, y + 4, panelWidth, "cipherchannels.settings.local");
        y = button(left, y, panelWidth, Component.translatable("cipherchannels.settings.rename"),
            ignored -> ClientContext.setScreen(ChannelFormScreen.rename(this, channel.id())));
        Button rotate = add(Button.builder(Component.translatable("cipherchannels.settings.rotate"),
            ignored -> confirmRotate(channel)).bounds(left, y + 4, half, 20).build());
        Button forget = add(Button.builder(Component.translatable("cipherchannels.settings.forget"),
            ignored -> confirmForget(channel)).bounds(left + half + 4, y + 4, half, 20).build());
        rotate.setTooltip(Tooltip.create(Component.translatable("cipherchannels.settings.rotate_tooltip")));
        forget.setTooltip(Tooltip.create(Component.translatable("cipherchannels.settings.forget_tooltip")));

        scroller.finish();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private int heading(int x, int y, int width, String key) {
        Component title = Component.translatable(key).withStyle(ChatFormatting.GRAY);
        add(new StringWidget(x + (width - font.width(title)) / 2, y,
            font.width(title), font.lineHeight, title, font));
        return y + 18;
    }

    private int text(int x, int y, int width, Component value, ChatFormatting color) {
        MultiLineTextWidget widget = add(new MultiLineTextWidget(x, y,
            value.copy().withStyle(color), font).setMaxWidth(width).setMaxRows(3).setCentered(true));
        return y + Math.max(12, widget.getHeight()) + 2;
    }

    private int button(int x, int y, int width, Component label, Button.OnPress action) {
        add(Button.builder(label, action).bounds(x, y, width, 20).build());
        return y + 24;
    }

    private <T extends AbstractWidget> T add(T widget) {
        addRenderableWidget(widget);
        scroller.track(widget);
        return widget;
    }

    private void copyFingerprint(ChannelRecord channel) {
        minecraft.keyboardHandler.setClipboard(channel.fingerprint());
        ClientContext.toast(Component.translatable("cipherchannels.toast.fingerprint_copied"));
    }

    private void copyInvite(ChannelRecord channel) {
        runAction(() -> {
            InviteClipboardGuard.copy(channel.id(), CipherChannels.channels().inviteFor(channel.id()));
            ClientContext.toast(Component.translatable("cipherchannels.toast.invite_copied"));
        }, "cipherchannels.toast.copy_failed");
    }

    private void toggleBinding() {
        ChannelRecord channel = record();
        if (channel == null) return;
        runAction(() -> {
            if (channel.binding() == null) {
                ServerBinding endpoint = ClientContext.currentServer();
                if (endpoint == null) throw new IllegalStateException("No multiplayer endpoint");
                CipherChannels.channels().bind(channel.id(), endpoint);
                ClientContext.toast(Component.translatable("cipherchannels.toast.bound", endpoint.displayName()));
            } else {
                CipherChannels.channels().unbind(channel.id());
                ClientContext.toast(Component.translatable("cipherchannels.toast.unbound"));
            }
            rebuildWidgets();
        }, "cipherchannels.toast.binding_failed");
    }

    private void toggleTransport() {
        ServerBinding endpoint = ClientContext.currentServer();
        if (endpoint == null) return;
        runAction(() -> {
            TransportMode next = CipherChannels.channels().transportFor(endpoint) == TransportMode.HIGH_CAPACITY
                ? TransportMode.ASCII_COMPATIBILITY : TransportMode.HIGH_CAPACITY;
            CipherChannels.channels().setTransport(endpoint, next);
            ClientContext.toast(Component.translatable("cipherchannels.toast.transport",
                ClientContext.transportName(next)));
            rebuildWidgets();
        }, "cipherchannels.toast.transport_failed");
    }

    private void confirmRotate(ChannelRecord channel) {
        ClientContext.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) runAction(() -> {
                ChannelRecord replacement = CipherChannels.channels().rotateKey(channel.id());
                InviteClipboardGuard.clearMatching(channel.id());
                channelId = replacement.id();
                parent.select(replacement.id());
                ClientContext.toast(Component.translatable("cipherchannels.toast.rotated", replacement.name()));
                rebuildWidgets();
            }, "cipherchannels.toast.rotate_failed");
            ClientContext.setScreen(this);
        }, Component.translatable("cipherchannels.confirm.rotate.title"),
            Component.translatable("cipherchannels.confirm.rotate.message", channel.name()),
            Component.translatable("cipherchannels.confirm.rotate.yes"), Component.translatable("gui.cancel")));
    }

    private void confirmForget(ChannelRecord channel) {
        ClientContext.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) runAction(() -> {
                CipherChannels.channels().forget(channel.id());
                InviteClipboardGuard.clearMatching(channel.id());
                UUID next = CipherChannels.channels().config().activeChannelId();
                if (next == null) next = CipherChannels.channels().config().channels().stream()
                    .findFirst().map(ChannelRecord::id).orElse(null);
                parent.select(next);
                ClientContext.toast(Component.translatable("cipherchannels.toast.forgotten", channel.name()));
                ClientContext.setScreen(parent);
            }, "cipherchannels.toast.forget_failed");
            if (!accepted || record() != null) ClientContext.setScreen(this);
        }, Component.translatable("cipherchannels.confirm.forget.title"),
            Component.translatable("cipherchannels.confirm.forget.message", channel.name()),
            Component.translatable("cipherchannels.confirm.forget.yes"), Component.translatable("gui.cancel")));
    }

    private ChannelRecord record() {
        return CipherChannels.channels().config().channels().stream()
            .filter(channel -> channel.id().equals(channelId)).findFirst().orElse(null);
    }

    private void runAction(Runnable action, String errorKey) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            ClientContext.toast(Component.translatable(errorKey));
        }
    }

    private static void disable(Button button, Component explanation) {
        button.active = false;
        button.setTooltip(Tooltip.create(explanation));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return scroller.contains(mouseY) && scroller.scroll(vertical)
            || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    protected boolean scrollContent(double amount) {
        return scroller.scroll(amount);
    }

    @Override
    public void onClose() {
        ClientContext.setScreen(parent);
    }
}
