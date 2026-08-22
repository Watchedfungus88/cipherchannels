package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.ChannelRecord;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ChannelFormScreen extends FormScreenBase {
    private enum Mode { CREATE, IMPORT, LOAD_KEY, RENAME }

    private static final int WIDTH = 320;
    private final Screen parent;
    private final Mode mode;
    private final UUID channelId;
    private String name = "";
    private String invite = "";
    private boolean reveal;
    private EditBox nameInput;
    private EditBox inviteInput;
    private Button submitButton;
    private MultiLineTextWidget validation;

    private ChannelFormScreen(Screen parent, Mode mode, UUID channelId) {
        super(Component.translatable(switch (mode) {
            case CREATE -> "cipherchannels.form.create.title";
            case IMPORT -> "cipherchannels.form.import.title";
            case LOAD_KEY -> "cipherchannels.form.load.title";
            case RENAME -> "cipherchannels.form.rename.title";
        }));
        this.parent = parent;
        this.mode = mode;
        this.channelId = channelId;
        if (mode == Mode.RENAME) {
            ChannelRecord record = record();
            name = record == null ? "" : record.name();
        }
    }

    static ChannelFormScreen create(ChannelManagerScreen parent) {
        return new ChannelFormScreen(parent, Mode.CREATE, null);
    }

    static ChannelFormScreen importInvite(ChannelManagerScreen parent) {
        return new ChannelFormScreen(parent, Mode.IMPORT, null);
    }

    static ChannelFormScreen loadKey(ChannelSettingsScreen parent, UUID channelId) {
        return new ChannelFormScreen(parent, Mode.LOAD_KEY, channelId);
    }

    static ChannelFormScreen rename(ChannelSettingsScreen parent, UUID channelId) {
        return new ChannelFormScreen(parent, Mode.RENAME, channelId);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(WIDTH, width - 20);
        int left = (width - panelWidth) / 2;
        addRenderableOnly(new StringWidget((width - font.width(title)) / 2, 18, title, font));
        int y = 48;
        if (mode == Mode.IMPORT || mode == Mode.LOAD_KEY) {
            addRenderableOnly(new StringWidget(left, y,
                Component.translatable("cipherchannels.form.invite.label"), font));
            y += 15;
            inviteInput = addRenderableWidget(new EditBox(font, left, y, panelWidth, 20,
                Component.translatable("cipherchannels.form.invite.narration")));
            inviteInput.setMaxLength(96);
            inviteInput.setHint(Component.translatable("cipherchannels.form.invite.hint"));
            inviteInput.setValue(invite);
            inviteInput.setResponder(value -> {
                invite = value;
                validateForm();
            });
            ClientContext.maskInvite(inviteInput, reveal);
            y += 24;
            int half = (panelWidth - 4) / 2;
            addRenderableWidget(Button.builder(Component.translatable(reveal
                    ? "cipherchannels.form.invite.hide" : "cipherchannels.form.invite.show"),
                ignored -> toggleReveal()).bounds(left, y, half, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("cipherchannels.form.invite.paste"), ignored -> {
                inviteInput.setValue(minecraft.keyboardHandler.getClipboard());
                inviteInput.setFocused(true);
            }).bounds(left + half + 4, y, half, 20).build());
            y += 30;
        }
        if (mode != Mode.LOAD_KEY) {
            addRenderableOnly(new StringWidget(left, y,
                Component.translatable(mode == Mode.IMPORT
                    ? "cipherchannels.form.name.new_label" : "cipherchannels.form.name.label"), font));
            y += 15;
            nameInput = addRenderableWidget(new EditBox(font, left, y, panelWidth, 20,
                Component.translatable("cipherchannels.form.name.narration")));
            nameInput.setMaxLength(48);
            nameInput.setHint(Component.translatable("cipherchannels.form.name.hint"));
            nameInput.setValue(name);
            nameInput.setResponder(value -> {
                name = value;
                validateForm();
            });
            y += 30;
        }
        validation = addRenderableOnly(new MultiLineTextWidget(left, y,
            Component.empty(), font).setMaxWidth(panelWidth).setMaxRows(3).setCentered(true));

        int buttonY = height - 30;
        int half = (panelWidth - 4) / 2;
        submitButton = addRenderableWidget(Button.builder(Component.translatable(submitKey()), ignored -> submitForm())
            .bounds(left, buttonY, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left + half + 4, buttonY, half, 20).build());
        validateForm();
        if (inviteInput != null) setInitialFocus(inviteInput);
        else if (nameInput != null) setInitialFocus(nameInput);
    }

    private String submitKey() {
        return switch (mode) {
            case CREATE -> "cipherchannels.form.create.submit";
            case IMPORT -> "cipherchannels.form.import.submit";
            case LOAD_KEY -> "cipherchannels.form.load.submit";
            case RENAME -> "cipherchannels.form.rename.submit";
        };
    }

    private void toggleReveal() {
        reveal = !reveal;
        rebuildWidgets();
        if (inviteInput != null) inviteInput.setFocused(true);
    }

    private void validateForm() {
        if (submitButton == null || validation == null) return;
        Component problem = validationProblem();
        submitButton.active = problem == null;
        submitButton.setTooltip(problem == null ? null : Tooltip.create(problem));
        validation.setMessage(problem == null ? Component.empty() : problem.copy().withStyle(ChatFormatting.RED));
    }

    private Component validationProblem() {
        if (mode == Mode.CREATE || mode == Mode.RENAME) {
            if (!UiPolicy.validChannelName(name)) {
                return Component.translatable("cipherchannels.form.validation.name");
            }
            return mode == Mode.CREATE && !CipherChannels.channels().canLoadSessionKey(null)
                ? Component.translatable("cipherchannels.form.validation.key_limit") : null;
        }
        if (invite.isEmpty()) return Component.translatable("cipherchannels.form.validation.invite_required");
        if (invite.startsWith("CC1.")) {
            return Component.translatable("cipherchannels.form.validation.legacy_invite");
        }
        ChannelRecord saved;
        try {
            saved = CipherChannels.channels().savedChannelForInvite(invite);
        } catch (RuntimeException exception) {
            return Component.translatable("cipherchannels.form.validation.invite");
        }
        if (mode == Mode.LOAD_KEY) {
            if (saved == null || !saved.id().equals(channelId)) {
                return Component.translatable("cipherchannels.form.validation.wrong_channel");
            }
            return CipherChannels.channels().canLoadSessionKey(channelId) ? null
                : Component.translatable("cipherchannels.form.validation.key_limit");
        }
        if (!CipherChannels.channels().canLoadSessionKey(saved == null ? null : saved.id())) {
            return Component.translatable("cipherchannels.form.validation.key_limit");
        }
        return saved != null || UiPolicy.validChannelName(name) ? null
            : Component.translatable("cipherchannels.form.validation.new_name");
    }

    @Override
    protected void submitForm() {
        if (validationProblem() != null) return;
        try {
            switch (mode) {
                case CREATE -> createChannel();
                case IMPORT -> importChannel();
                case LOAD_KEY -> loadKey();
                case RENAME -> renameChannel();
            }
        } catch (RuntimeException exception) {
            validation.setMessage(Component.translatable("cipherchannels.form.validation.failed")
                .withStyle(ChatFormatting.RED));
        }
    }

    private void createChannel() {
        ChannelRecord created = CipherChannels.channels().create(name);
        ChannelManagerScreen manager = (ChannelManagerScreen) parent;
        manager.select(created.id());
        ClientContext.toast(Component.translatable("cipherchannels.toast.created", created.name()));
        clearInvite();
        ClientContext.setScreen(new ChannelSettingsScreen(manager, created.id()));
    }

    private void importChannel() {
        ChannelRecord saved = CipherChannels.channels().savedChannelForInvite(invite);
        ChannelRecord imported = CipherChannels.channels().importInvite(invite, name);
        ChannelManagerScreen manager = (ChannelManagerScreen) parent;
        if (saved == null) manager.select(imported.id());
        ClientContext.toast(Component.translatable(saved == null
            ? "cipherchannels.toast.imported" : "cipherchannels.toast.key_loaded", imported.name()));
        clearInvite();
        ClientContext.setScreen(manager);
    }

    private void loadKey() {
        ChannelRecord loaded = CipherChannels.channels().loadKey(channelId, invite);
        ClientContext.toast(Component.translatable("cipherchannels.toast.key_loaded", loaded.name()));
        clearInvite();
        ClientContext.setScreen(parent);
    }

    private void renameChannel() {
        CipherChannels.channels().rename(channelId, name);
        ClientContext.toast(Component.translatable("cipherchannels.toast.renamed", name.trim()));
        ClientContext.setScreen(parent);
    }

    private ChannelRecord record() {
        return CipherChannels.channels().config().channels().stream()
            .filter(channel -> channel.id().equals(channelId)).findFirst().orElse(null);
    }

    private void clearInvite() {
        invite = "";
        if (inviteInput != null) inviteInput.setValue("");
    }

    @Override
    public void onClose() {
        clearInvite();
        ClientContext.setScreen(parent);
    }
}
