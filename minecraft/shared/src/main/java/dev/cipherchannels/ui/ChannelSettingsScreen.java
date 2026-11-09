package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.ServerBinding;
import dev.cipherchannels.protocol.TransportMode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

final class ChannelSettingsScreen extends SettingsScreenBase {
    private static final int WIDTH = 320;
    private final ChannelManagerScreen parent;
    private final ContentScroller scroller = new ContentScroller();
    private UUID channelId;
    private long keyRevision;
    private boolean actionPending;

    ChannelSettingsScreen(ChannelManagerScreen parent, UUID channelId) {
        super(Component.translatable("cipherchannels.settings.title"));
        this.parent = parent;
        this.channelId = channelId;
    }

    @Override
    protected void init() {
        keyRevision = CipherChannels.channels().keyRevision();
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
        int half = (panelWidth - 4) / 2;
        boolean keyLoaded = CipherChannels.channels().hasSessionKey(channel.id());
        button(left, y, half, Component.translatable("cipherchannels.settings.rename"),
            ignored -> ClientContext.setScreen(ChannelFormScreen.rename(this, channel.id())));
        Button remember = add(Button.builder(Component.translatable(channel.rememberInvite()
                ? "cipherchannels.settings.remember.on" : "cipherchannels.settings.remember.off"),
            ignored -> toggleRemember()).bounds(left + half + 4, y, half, 20).build());
        remember.setTooltip(Tooltip.create(Component.translatable("cipherchannels.settings.remember.tooltip")));
        remember.active = !busy();
        if (!channel.rememberInvite() && !keyLoaded) {
            disable(remember, Component.translatable("cipherchannels.disabled.key_needed", channel.name()));
        }
        y += 24;
        if (!keyLoaded) {
            y = button(left, y, panelWidth, Component.translatable("cipherchannels.settings.load"),
                ignored -> ClientContext.setScreen(ChannelFormScreen.loadKey(this, channel.id())));
        }
        Button invite = add(Button.builder(Component.translatable("cipherchannels.settings.copy_invite"),
            ignored -> copyInvite(channel)).bounds(left, y, half, 20).build());
        invite.setTooltip(Tooltip.create(Component.translatable("cipherchannels.settings.invite_warning")));
        if (!keyLoaded) {
            disable(invite, Component.translatable("cipherchannels.disabled.key_needed", channel.name()));
        }
        Button fingerprint = add(Button.builder(Component.translatable("cipherchannels.settings.copy_fingerprint"),
            ignored -> copyFingerprint(channel)).bounds(left + half + 4, y, half, 20).build());
        fingerprint.setTooltip(Tooltip.create(Component.literal(channel.fingerprint()).append("\n")
            .append(Component.translatable("cipherchannels.settings.fingerprint_help"))));
        y += 32;

        ServerBinding endpoint = ClientContext.currentServer();
        Component bindingLabel = channel.binding() == null
            ? Component.translatable("cipherchannels.settings.bind")
            : Component.translatable("cipherchannels.settings.unbind");
        Button binding = add(Button.builder(bindingLabel, ignored -> toggleBinding())
            .bounds(left, y, panelWidth, 20).build());
        binding.active = !busy();
        if (channel.binding() == null && endpoint == null) {
            disable(binding, Component.translatable("cipherchannels.disabled.multiplayer_only"));
        } else {
            binding.setTooltip(Tooltip.create(Component.translatable(channel.binding() == null
                ? "cipherchannels.settings.bind_tooltip" : "cipherchannels.settings.unbind_tooltip",
                channel.binding() == null ? endpoint.displayName() : channel.binding().displayName())));
        }
        y += 24;
        TransportMode mode = CipherChannels.channels().transportFor(endpoint);
        Button transport = add(Button.builder(Component.translatable("cipherchannels.settings.transport",
                ClientContext.transportName(mode)), ignored -> toggleTransport())
            .bounds(left, y, panelWidth, 20).build());
        if (endpoint == null) disable(transport, Component.translatable("cipherchannels.disabled.multiplayer_only"));
        else transport.setTooltip(Tooltip.create(Component.translatable("cipherchannels.settings.transport_tooltip")));
        y += 32;

        Button rotate = add(Button.builder(Component.translatable("cipherchannels.settings.rotate"),
            ignored -> confirmRotate(channel)).bounds(left, y, half, 20).build());
        Button forget = add(Button.builder(Component.translatable("cipherchannels.settings.forget"),
            ignored -> confirmForget(channel)).bounds(left + half + 4, y, half, 20).build());
        rotate.setTooltip(Tooltip.create(Component.translatable("cipherchannels.settings.rotate_tooltip")));
        forget.setTooltip(Tooltip.create(Component.translatable("cipherchannels.settings.forget_tooltip")));
        rotate.active = !busy();
        forget.active = !busy();

        scroller.finish();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    private int button(int x, int y, int width, Component label, Button.OnPress action) {
        Button button = add(Button.builder(label, action).bounds(x, y, width, 20).build());
        button.active = !busy();
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

    private boolean busy() {
        return actionPending || CipherChannels.channels().storageBusy(channelId);
    }

    private void toggleRemember() {
        ChannelRecord channel = record();
        if (channel == null || busy()) return;
        runStorageAction(() -> CipherChannels.channels().setRememberInvite(channel.id(), !channel.rememberInvite()),
            ignored -> {}, "cipherchannels.toast.remember_failed");
    }

    private <T> void runStorageAction(Supplier<CompletableFuture<T>> action, Consumer<T> success, String errorKey) {
        try {
            actionPending = true;
            action.get().whenComplete((result, failure) -> minecraft.execute(() -> {
                try {
                    if (failure == null) success.accept(result);
                    else ClientContext.toast(Component.translatable(errorKey));
                } finally {
                    actionPending = false;
                    keyRevision = -1;
                }
            }));
            rebuildWidgets();
        } catch (RuntimeException exception) {
            actionPending = false;
            ClientContext.toast(Component.translatable(errorKey));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!actionPending && keyRevision != CipherChannels.channels().keyRevision()) rebuildWidgets();
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
            ClientContext.setScreen(this);
            if (accepted) runStorageAction(() -> CipherChannels.channels().rotateKeyAsync(channel.id()), replacement -> {
                InviteClipboardGuard.clearMatching(channel.id());
                channelId = replacement.id();
                parent.select(replacement.id());
                ClientContext.toast(Component.translatable("cipherchannels.toast.rotated", replacement.name()));
            }, "cipherchannels.toast.rotate_failed");
        }, Component.translatable("cipherchannels.confirm.rotate.title"),
            Component.translatable("cipherchannels.confirm.rotate.message", channel.name()),
            Component.translatable("cipherchannels.confirm.rotate.yes"), Component.translatable("gui.cancel")));
    }

    private void confirmForget(ChannelRecord channel) {
        ClientContext.setScreen(new ConfirmScreen(accepted -> {
            ClientContext.setScreen(this);
            if (accepted) runStorageAction(() -> CipherChannels.channels().forgetAsync(channel.id()), ignored -> {
                InviteClipboardGuard.clearMatching(channel.id());
                UUID next = CipherChannels.channels().config().activeChannelId();
                if (next == null) next = CipherChannels.channels().config().channels().stream()
                    .findFirst().map(ChannelRecord::id).orElse(null);
                parent.select(next);
                ClientContext.toast(Component.translatable("cipherchannels.toast.forgotten", channel.name()));
            }, "cipherchannels.toast.forget_failed");
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
