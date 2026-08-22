package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.ChannelConfig;
import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.ChannelStatus;
import dev.cipherchannels.channels.TransportState;
import dev.cipherchannels.chat.ChatLogProtection;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ChannelManagerScreen extends Screen {
    private static final int WIDTH = 320;
    private final Screen parent;
    private UUID selectedId;
    private Button useButton;
    private Button settingsButton;

    public ChannelManagerScreen(Screen parent) {
        this(parent, null);
    }

    ChannelManagerScreen(Screen parent, UUID selectedId) {
        super(Component.translatable("cipherchannels.screen.title"));
        this.parent = parent;
        this.selectedId = selectedId;
    }

    public static Screen create(Screen parent) {
        return new ChannelManagerScreen(parent);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(WIDTH, width - 20);
        int left = (width - panelWidth) / 2;
        addRenderableOnly(new StringWidget((width - font.width(title)) / 2, 14,
            font.width(title), font.lineHeight, title, font));
        if (CipherChannels.channels().loadState().locked()) {
            initLocked(left, panelWidth);
            return;
        }

        ChannelConfig config = CipherChannels.channels().config();
        if (selectedId == null || config.channels().stream().noneMatch(channel -> channel.id().equals(selectedId))) {
            selectedId = config.activeChannelId() != null ? config.activeChannelId()
                : config.channels().stream().findFirst().map(ChannelRecord::id).orElse(null);
        }

        Button encryption = addRenderableWidget(Button.builder(encryptionLabel(config), ignored -> toggleEncryption())
            .bounds(width / 2 - 100, 32, 200, 20).build());
        if (!config.encryptionEnabled()) {
            Component reason = enableBlockedReason(config.activeChannel());
            if (reason != null) disable(encryption, reason);
        }

        addRenderableOnly(new MultiLineTextWidget(left, 57, stateLine(config), font)
            .setMaxWidth(panelWidth).setMaxRows(2).setCentered(true));

        int listTop = 78;
        int footerTop = height - 76;
        int listHeight = Math.max(1, footerTop - listTop - 4);
        ChannelList list = addRenderableWidget(new ChannelList(minecraft, width, listHeight,
            listTop, 36, panelWidth, this::selectRow, this::useFromList));
        list.setX(0);
        for (ChannelRecord record : config.channels()) list.addRecord(record, record.id().equals(selectedId));
        if (config.channels().isEmpty()) {
            Component empty = Component.translatable("cipherchannels.manager.empty").withStyle(ChatFormatting.GRAY);
            addRenderableOnly(new StringWidget((width - font.width(empty)) / 2,
                listTop + (listHeight - font.lineHeight) / 2, font.width(empty), font.lineHeight, empty, font));
        }

        int half = (panelWidth - 4) / 2;
        useButton = addRenderableWidget(Button.builder(Component.translatable("cipherchannels.manager.use"),
            ignored -> useSelected()).bounds(left, footerTop, half, 20).build());
        settingsButton = addRenderableWidget(Button.builder(Component.translatable("cipherchannels.manager.settings"),
            ignored -> openSettings()).bounds(left + half + 4, footerTop, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.manager.create"),
            ignored -> ClientContext.setScreen(ChannelFormScreen.create(this)))
            .bounds(left, footerTop + 24, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.manager.import"),
            ignored -> ClientContext.setScreen(ChannelFormScreen.importInvite(this)))
            .bounds(left + half + 4, footerTop + 24, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(width / 2 - 100, footerTop + 48, 200, 20).build());
        updateSelectionButtons();
    }

    private void initLocked(int left, int panelWidth) {
        addRenderableOnly(new MultiLineTextWidget(left, 48,
            Component.translatable("cipherchannels.locked."
                + CipherChannels.channels().loadState().name().toLowerCase()).withStyle(ChatFormatting.RED), font)
            .setMaxWidth(panelWidth).setMaxRows(5).setCentered(true));
        int y = Math.max(112, height / 2 - 10);
        int half = (panelWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.locked.open_folder"),
            ignored -> ClientContext.openConfigFolder()).bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.locked.reset"),
            ignored -> confirmReset()).bounds(left + half + 4, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(width / 2 - 100, y + 28, 200, 20).build());
    }

    private Component encryptionLabel(ChannelConfig config) {
        return Component.translatable(config.encryptionEnabled()
            ? "cipherchannels.manager.encryption.on" : "cipherchannels.manager.encryption.off");
    }

    private Component stateLine(ChannelConfig config) {
        if (!config.encryptionEnabled()) return Component.translatable("cipherchannels.manager.state.off")
            .withStyle(ChatFormatting.GRAY);
        if (!ChatLogProtection.allowsEncryption()) return Component.translatable(
            "cipherchannels.manager.state.logging_blocked").withStyle(ChatFormatting.RED);
        ChannelStatus status = CipherChannels.channels().status(ClientContext.currentServer());
        ChannelRecord active = status.activeChannel();
        return switch (status.state()) {
            case READY -> Component.translatable("cipherchannels.manager.state.on", active.name())
                .withStyle(ChatFormatting.GREEN);
            case NO_CHANNEL -> (active == null
                ? Component.translatable("cipherchannels.manager.state.no_channel")
                : Component.translatable("cipherchannels.manager.state.key_needed", active.name()))
                .withStyle(ChatFormatting.RED);
            case SUSPENDED -> Component.translatable("cipherchannels.manager.state.binding",
                active.name(), active.binding().displayName()).withStyle(ChatFormatting.RED);
            case CONFIG_LOCKED -> Component.translatable("cipherchannels.manager.state.config_locked")
                .withStyle(ChatFormatting.RED);
            case OFF -> Component.translatable("cipherchannels.manager.state.off").withStyle(ChatFormatting.GRAY);
        };
    }

    private Component enableBlockedReason(ChannelRecord active) {
        if (!ChatLogProtection.allowsEncryption()) return Component.translatable("cipherchannels.disabled.chat_logging");
        if (active == null) return Component.translatable("cipherchannels.disabled.no_channel");
        if (!CipherChannels.channels().hasSessionKey(active.id())) {
            return Component.translatable("cipherchannels.disabled.key_needed", active.name());
        }
        if (active.binding() != null && !active.binding().equals(ClientContext.currentServer())) {
            return Component.translatable("cipherchannels.disabled.binding", active.binding().displayName());
        }
        return null;
    }

    private void toggleEncryption() {
        runAction(() -> {
            boolean enabled = CipherChannels.channels().config().encryptionEnabled();
            CipherChannels.channels().setEnabled(!enabled, ClientContext.currentServer());
            ClientContext.toast(Component.translatable(enabled
                ? "cipherchannels.toast.encryption_off" : "cipherchannels.toast.encryption_on"));
            rebuildWidgets();
        }, "cipherchannels.toast.action_failed");
    }

    private void selectRow(ChannelRecord record) {
        selectedId = record.id();
        updateSelectionButtons();
    }

    void select(UUID id) {
        selectedId = id;
    }

    private ChannelRecord selectedRecord() {
        return CipherChannels.channels().config().channels().stream()
            .filter(channel -> channel.id().equals(selectedId)).findFirst().orElse(null);
    }

    private void updateSelectionButtons() {
        if (useButton == null || settingsButton == null) return;
        ChannelRecord selected = selectedRecord();
        settingsButton.active = selected != null;
        settingsButton.setTooltip(selected == null
            ? Tooltip.create(Component.translatable("cipherchannels.disabled.select_channel")) : null);
        Component blocked = useBlockedReason(selected);
        useButton.active = blocked == null;
        useButton.setTooltip(blocked == null ? null : Tooltip.create(blocked));
    }

    private Component useBlockedReason(ChannelRecord selected) {
        if (selected == null) return Component.translatable("cipherchannels.disabled.select_channel");
        ChannelConfig config = CipherChannels.channels().config();
        if (selected.id().equals(config.activeChannelId())) {
            return Component.translatable("cipherchannels.disabled.already_active");
        }
        if (!config.encryptionEnabled()) return null;
        if (!CipherChannels.channels().hasSessionKey(selected.id())) {
            return Component.translatable("cipherchannels.disabled.key_needed", selected.name());
        }
        if (selected.binding() != null && !selected.binding().equals(ClientContext.currentServer())) {
            return Component.translatable("cipherchannels.disabled.binding", selected.binding().displayName());
        }
        return null;
    }

    private void useSelected() {
        ChannelRecord selected = selectedRecord();
        Component blocked = useBlockedReason(selected);
        if (blocked != null) {
            ClientContext.toast(blocked);
            return;
        }
        useRecord(selected);
    }

    private void useFromList(ChannelRecord record) {
        selectedId = record.id();
        useSelected();
    }

    private void useRecord(ChannelRecord record) {
        runAction(() -> {
            CipherChannels.channels().select(record.id(), ClientContext.currentServer());
            selectedId = record.id();
            ClientContext.toast(Component.translatable("cipherchannels.toast.using", record.name()));
            rebuildWidgets();
        }, "cipherchannels.toast.switch_failed");
    }

    private void openSettings() {
        ChannelRecord selected = selectedRecord();
        if (selected == null) {
            ClientContext.toast(Component.translatable("cipherchannels.disabled.select_channel"));
            return;
        }
        ClientContext.setScreen(new ChannelSettingsScreen(this, selected.id()));
    }

    private void confirmReset() {
        ClientContext.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) runAction(() -> {
                CipherChannels.channels().resetUnsafeConfiguration();
                ClientContext.toast(Component.translatable("cipherchannels.toast.config_reset"));
                rebuildWidgets();
            }, "cipherchannels.toast.reset_failed");
            ClientContext.setScreen(this);
        }, Component.translatable("cipherchannels.confirm.reset.title"),
            Component.translatable("cipherchannels.confirm.reset.message"),
            Component.translatable("cipherchannels.confirm.reset.yes"), Component.translatable("gui.cancel")));
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
    public void onClose() {
        ClientContext.setScreen(parent);
    }
}
