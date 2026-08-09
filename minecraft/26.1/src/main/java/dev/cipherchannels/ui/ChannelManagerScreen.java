package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.ChannelConfig;
import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.ChannelStatus;
import dev.cipherchannels.channels.ServerBinding;
import dev.cipherchannels.channels.TransportState;
import dev.cipherchannels.protocol.TransportMode;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ChannelManagerScreen extends Screen {
    enum Tab { OVERVIEW, CHANNELS, SHARE }
    private enum Form { NONE, CREATE, JOIN, RENAME }

    private final Screen parent;
    private Tab tab = Tab.OVERVIEW;
    private Form form = Form.NONE;
    private EditBox nameInput;
    private EditBox inviteInput;
    private MultiLineTextWidget feedbackWidget;
    private UiFeedback feedback = new UiFeedback(UiFeedback.Severity.WARNING,
        Component.translatable("cipherchannels.feedback.welcome"));

    public ChannelManagerScreen(Screen parent) {
        super(Component.translatable("cipherchannels.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ChannelConfig config = CipherChannels.channels().config();
        int panelWidth = Math.min(520, width - 24);
        int left = (width - panelWidth) / 2;

        addRenderableOnly(new StringWidget(left, 10, panelWidth, 16,
            Component.translatable("cipherchannels.screen.title").withStyle(ChatFormatting.AQUA), font));

        if (config.channels().isEmpty()) {
            initOnboarding(left, panelWidth);
        } else {
            initHeader(left, panelWidth, config);
            initTabs(left, panelWidth);
            initFeedback(left, panelWidth, 104);
            switch (tab) {
                case OVERVIEW -> initOverview(left, panelWidth, config);
                case CHANNELS -> initChannels(left, panelWidth, config);
                case SHARE -> initShare(left, panelWidth, config);
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void initOnboarding(int left, int panelWidth) {
        addText(left, 36, panelWidth, 2, Component.translatable("cipherchannels.onboarding.explanation"), ChatFormatting.WHITE);
        initFeedback(left, panelWidth, 66);
        if (form == Form.CREATE || form == Form.JOIN) {
            initChannelForm(left, panelWidth, 112, true);
            return;
        }
        int half = (panelWidth - 10) / 2;
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.onboarding.create"),
            ignored -> beginForm(Form.CREATE)).bounds(left, 122, half, 24).build());
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.onboarding.join"),
            ignored -> beginForm(Form.JOIN)).bounds(left + half + 10, 122, half, 24).build());
        addText(left, 158, panelWidth, 3, Component.translatable("cipherchannels.onboarding.keys_session_only"), ChatFormatting.GRAY);
    }

    private void initHeader(int left, int panelWidth, ChannelConfig config) {
        ChannelStatus status = CipherChannels.channels().status(ClientContext.currentServer());
        ChannelRecord active = config.activeChannel();
        String stateKey = switch (status.state()) {
            case OFF -> "cipherchannels.header.state.off";
            case READY -> "cipherchannels.header.state.on";
            case NO_CHANNEL, SUSPENDED -> "cipherchannels.header.state.blocked";
        };
        Component activeName = active == null ? Component.translatable("cipherchannels.channel.none") : Component.literal(active.name());
        Component endpoint = ClientContext.currentServer() == null
            ? Component.translatable("cipherchannels.endpoint.singleplayer")
            : Component.literal(ClientContext.currentServer().displayName());
        addRenderableOnly(new StringWidget(left, 28, panelWidth, 12,
            Component.translatable("cipherchannels.header.summary", Component.translatable(stateKey), activeName)
                .withStyle(status.state() == TransportState.READY ? ChatFormatting.GREEN : ChatFormatting.YELLOW), font));
        addRenderableOnly(new StringWidget(left, 42, panelWidth, 12,
            Component.translatable("cipherchannels.header.endpoint_transport", endpoint,
                ClientContext.transportName(CipherChannels.channels().transportFor(ClientContext.currentServer())))
                .withStyle(ChatFormatting.GRAY), font));
    }

    private void initTabs(int left, int panelWidth) {
        int gap = 4;
        int buttonWidth = (panelWidth - gap * 2) / 3;
        addTabButton(left, 68, buttonWidth, Tab.OVERVIEW, "cipherchannels.tab.overview");
        addTabButton(left + buttonWidth + gap, 68, buttonWidth, Tab.CHANNELS, "cipherchannels.tab.channels");
        addTabButton(left + (buttonWidth + gap) * 2, 68, buttonWidth, Tab.SHARE,
            "cipherchannels.tab.share_security");
    }

    private void addTabButton(int x, int y, int buttonWidth, Tab target, String key) {
        Button button = addRenderableWidget(Button.builder(Component.translatable(key), ignored -> {
            tab = target;
            form = Form.NONE;
            rebuildWidgets();
        }).bounds(x, y, buttonWidth, 22).build());
        button.active = tab != target;
    }

    private void initFeedback(int left, int panelWidth, int y) {
        feedbackWidget = addRenderableOnly(new MultiLineTextWidget(left, y,
            feedback.message().copy().withStyle(feedback.color()), font).setMaxWidth(panelWidth).setMaxRows(2));
    }

    private void initOverview(int left, int panelWidth, ChannelConfig config) {
        ChannelRecord active = config.activeChannel();
        ChannelStatus status = CipherChannels.channels().status(ClientContext.currentServer());
        int y = 134;
        addText(left, y, panelWidth, 1, Component.translatable("cipherchannels.overview.active",
            active == null ? Component.translatable("cipherchannels.channel.none") : active.name()), ChatFormatting.WHITE);
        y += 18;
        addText(left, y, panelWidth, 1, Component.translatable("cipherchannels.overview.key",
            active != null && CipherChannels.channels().hasSessionKey(active.id())
                ? Component.translatable("cipherchannels.value.ready") : Component.translatable("cipherchannels.value.key_needed")), ChatFormatting.WHITE);
        y += 18;
        addText(left, y, panelWidth, 2, bindingSummary(active), ChatFormatting.WHITE);
        y += 28;
        TransportMode mode = CipherChannels.channels().transportFor(ClientContext.currentServer());
        addText(left, y, panelWidth, 2, Component.translatable("cipherchannels.overview.transport",
            ClientContext.transportName(mode), mode.rawCapacity()), ChatFormatting.WHITE);
        y += 34;

        boolean enabled = config.encryptionEnabled();
        Button toggle = addRenderableWidget(Button.builder(Component.translatable(enabled
                ? "cipherchannels.overview.turn_off" : "cipherchannels.overview.turn_on"), ignored -> toggleEncryption())
            .bounds(left, y, panelWidth, 26).build());
        if (!enabled && (active == null || !CipherChannels.channels().canKeepEncryptionOn(active.id(), ClientContext.currentServer()))) {
            disable(toggle, Component.translatable(active == null
                ? "cipherchannels.disabled.no_channel" : "cipherchannels.disabled.not_ready"));
        }
        y += 34;

        ChannelRecord bound = CipherChannels.channels().boundChannelFor(ClientContext.currentServer());
        if (bound != null && (active == null || !bound.id().equals(active.id()))) {
            addRenderableWidget(Button.builder(Component.translatable("cipherchannels.overview.switch_bound", bound.name()),
                ignored -> requestSwitch(bound)).bounds(left, y, panelWidth, 22).build());
        } else {
            Component next = switch (status.state()) {
                case OFF -> Component.translatable("cipherchannels.overview.next.turn_on");
                case READY -> Component.translatable("cipherchannels.overview.next.ready");
                case NO_CHANNEL -> Component.translatable("cipherchannels.overview.next.import");
                case SUSPENDED -> Component.translatable("cipherchannels.overview.next.suspended");
            };
            addText(left, y, panelWidth, 3, next, status.state() == TransportState.READY ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        }
    }

    private void initChannels(int left, int panelWidth, ChannelConfig config) {
        if (form != Form.NONE) {
            initChannelForm(left, panelWidth, 132, false);
            return;
        }
        int listBottom = Math.max(210, height - 100);
        ChannelList list = addRenderableWidget(new ChannelList(minecraft, panelWidth,
            listBottom - 128, 128, 40, this::requestSwitch));
        list.setX(left);
        for (ChannelRecord record : config.channels()) {
            list.addRecord(record);
        }
        int y = listBottom + 6;
        int quarter = (panelWidth - 12) / 4;
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.channels.create"),
            ignored -> beginForm(Form.CREATE)).bounds(left, y, quarter, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.channels.join"),
            ignored -> beginForm(Form.JOIN)).bounds(left + quarter + 4, y, quarter, 20).build());
        Button rename = addRenderableWidget(Button.builder(Component.translatable("cipherchannels.channels.rename"),
            ignored -> beginForm(Form.RENAME)).bounds(left + (quarter + 4) * 2, y, quarter, 20).build());
        Button forget = addRenderableWidget(Button.builder(Component.translatable("cipherchannels.channels.forget"),
            ignored -> confirmForget()).bounds(left + (quarter + 4) * 3, y, quarter, 20).build());
        if (config.activeChannel() == null) {
            disable(rename, Component.translatable("cipherchannels.disabled.no_channel"));
            disable(forget, Component.translatable("cipherchannels.disabled.no_channel"));
        }
    }

    private void initChannelForm(int left, int panelWidth, int y, boolean onboarding) {
        Component heading = Component.translatable(switch (form) {
            case CREATE -> "cipherchannels.form.create.title";
            case JOIN -> "cipherchannels.form.join.title";
            case RENAME -> "cipherchannels.form.rename.title";
            case NONE -> throw new IllegalStateException("No form selected");
        });
        addText(left, y, panelWidth, 1, heading, ChatFormatting.AQUA);
        y += 24;
        addText(left, y, panelWidth, 1, Component.translatable("cipherchannels.form.name.label"), ChatFormatting.GRAY);
        y += 14;
        nameInput = addRenderableWidget(new EditBox(font, left, y, panelWidth, 20,
            Component.translatable("cipherchannels.form.name.narration")));
        nameInput.setMaxLength(48);
        nameInput.setHint(Component.translatable("cipherchannels.form.name.hint"));
        ChannelRecord active = CipherChannels.channels().config().activeChannel();
        nameInput.setValue(form == Form.RENAME && active != null ? active.name() : "Friends");
        y += 30;
        if (form == Form.JOIN) {
            addText(left, y, panelWidth, 1, Component.translatable("cipherchannels.form.invite.label"), ChatFormatting.GRAY);
            y += 14;
            inviteInput = addRenderableWidget(new EditBox(font, left, y, panelWidth, 20,
                Component.translatable("cipherchannels.form.invite.narration")));
            inviteInput.setMaxLength(96);
            inviteInput.setHint(Component.translatable("cipherchannels.form.invite.hint"));
            y += 30;
        }
        int half = (panelWidth - 8) / 2;
        Component submitLabel = Component.translatable(switch (form) {
            case CREATE -> "cipherchannels.form.create.submit";
            case JOIN -> "cipherchannels.form.join.submit";
            case RENAME -> "cipherchannels.form.rename.submit";
            case NONE -> throw new IllegalStateException("No form selected");
        });
        addRenderableWidget(Button.builder(submitLabel, ignored -> submitForm())
            .bounds(left, y, half, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> {
            form = Form.NONE;
            if (!onboarding) {
                tab = Tab.CHANNELS;
            }
            rebuildWidgets();
        }).bounds(left + half + 8, y, half, 22).build());
    }

    private void initShare(int left, int panelWidth, ChannelConfig config) {
        ChannelRecord active = config.activeChannel();
        if (active == null) {
            addText(left, 134, panelWidth, 3, Component.translatable("cipherchannels.share.no_channel"), ChatFormatting.YELLOW);
            return;
        }
        int y = 134;
        addText(left, y, panelWidth, 1, Component.translatable("cipherchannels.share.channel", active.name()), ChatFormatting.WHITE);
        y += 20;
        addText(left, y, panelWidth, 2, Component.translatable("cipherchannels.share.fingerprint", active.fingerprint()), ChatFormatting.AQUA);
        y += 30;
        addRenderableWidget(Button.builder(Component.translatable("cipherchannels.share.copy_invite"),
            ignored -> confirmCopyInvite(active)).tooltip(Tooltip.create(
                Component.translatable("cipherchannels.share.copy_warning"))).bounds(left, y, panelWidth, 22).build());
        y += 32;

        ServerBinding endpoint = ClientContext.currentServer();
        Button binding = addRenderableWidget(Button.builder(Component.translatable(active.binding() == null
                ? "cipherchannels.share.bind" : "cipherchannels.share.unbind"), ignored -> toggleBinding(active))
            .bounds(left, y, panelWidth, 22).build());
        if (active.binding() == null && endpoint == null) {
            disable(binding, Component.translatable("cipherchannels.disabled.multiplayer_only"));
        }
        y += 32;
        addText(left, y, panelWidth, 2, Component.translatable("cipherchannels.share.transport_help"), ChatFormatting.GRAY);
        y += 30;
        int half = (panelWidth - 8) / 2;
        TransportMode mode = CipherChannels.channels().transportFor(endpoint);
        Button high = addRenderableWidget(Button.builder(Component.translatable("cipherchannels.transport.high_capacity"),
            ignored -> setTransport(TransportMode.HIGH_CAPACITY)).bounds(left, y, half, 22).build());
        Button ascii = addRenderableWidget(Button.builder(Component.translatable("cipherchannels.transport.compatibility"),
            ignored -> setTransport(TransportMode.ASCII_COMPATIBILITY)).bounds(left + half + 8, y, half, 22).build());
        if (endpoint == null) {
            disable(high, Component.translatable("cipherchannels.disabled.multiplayer_only"));
            disable(ascii, Component.translatable("cipherchannels.disabled.multiplayer_only"));
        } else if (mode == TransportMode.HIGH_CAPACITY) {
            high.active = false;
        } else {
            ascii.active = false;
        }
    }

    private void beginForm(Form next) {
        form = next;
        rebuildWidgets();
    }

    private void submitForm() {
        runAction(() -> {
            switch (form) {
                case CREATE -> {
                    ChannelRecord created = CipherChannels.channels().create(nameInput.getValue());
                    feedback = new UiFeedback(UiFeedback.Severity.SUCCESS,
                        Component.translatable("cipherchannels.feedback.created", created.name(), created.fingerprint()));
                    tab = Tab.SHARE;
                }
                case JOIN -> {
                    ChannelRecord joined = CipherChannels.channels().importInvite(inviteInput.getValue(), nameInput.getValue());
                    inviteInput.setValue("");
                    feedback = new UiFeedback(UiFeedback.Severity.SUCCESS,
                        Component.translatable("cipherchannels.feedback.joined", joined.name(), joined.fingerprint()));
                    tab = Tab.OVERVIEW;
                }
                case RENAME -> {
                    ChannelRecord active = requireActive();
                    CipherChannels.channels().rename(active.id(), nameInput.getValue());
                    feedback = new UiFeedback(UiFeedback.Severity.SUCCESS,
                        Component.translatable("cipherchannels.feedback.renamed", nameInput.getValue().trim()));
                    tab = Tab.CHANNELS;
                }
                case NONE -> throw new IllegalStateException("No action selected");
            }
            form = Form.NONE;
            rebuildWidgets();
        });
    }

    private void toggleEncryption() {
        runAction(() -> {
            boolean enabled = CipherChannels.channels().config().encryptionEnabled();
            CipherChannels.channels().setEnabled(!enabled, ClientContext.currentServer());
            setFeedback(Component.translatable(enabled
                ? "cipherchannels.feedback.encryption_off" : "cipherchannels.feedback.encryption_on"), ChatFormatting.GREEN);
            rebuildWidgets();
        });
    }

    private void requestSwitch(ChannelRecord target) {
        ChannelConfig config = CipherChannels.channels().config();
        UiPolicy.SwitchFlow flow = UiPolicy.switchFlow(config, target.id());
        if (flow == UiPolicy.SwitchFlow.ALREADY_ACTIVE) {
            setFeedback(Component.translatable("cipherchannels.feedback.already_active", target.name()), ChatFormatting.AQUA);
            return;
        }
        if (flow == UiPolicy.SwitchFlow.CONFIRM) {
            minecraft.setScreen(new ChannelSwitchScreen(this, target));
            return;
        }
        performSwitch(target, false);
    }

    void performSwitch(ChannelRecord target, boolean keepEncryptionOn) {
        runAction(() -> {
            CipherChannels.channels().select(target.id(), keepEncryptionOn, ClientContext.currentServer());
            setFeedback(Component.translatable(keepEncryptionOn
                ? "cipherchannels.feedback.switched_on" : "cipherchannels.feedback.switched_off", target.name()), ChatFormatting.GREEN);
            tab = Tab.OVERVIEW;
            rebuildWidgets();
        });
    }

    private void confirmCopyInvite(ChannelRecord active) {
        BooleanConsumer callback = accepted -> {
            if (accepted) {
                runAction(() -> {
                    minecraft.keyboardHandler.setClipboard(CipherChannels.channels().inviteFor(active.id()));
                    setFeedback(Component.translatable("cipherchannels.feedback.invite_copied"), ChatFormatting.YELLOW);
                });
            } else {
                setFeedback(Component.translatable("cipherchannels.feedback.copy_cancelled"), ChatFormatting.GRAY);
            }
            minecraft.setScreen(this);
        };
        minecraft.setScreen(new ConfirmScreen(callback,
            Component.translatable("cipherchannels.confirm.copy.title"),
            Component.translatable("cipherchannels.confirm.copy.message"),
            Component.translatable("cipherchannels.confirm.copy.yes"), Component.translatable("gui.cancel")));
    }

    private void confirmForget() {
        ChannelRecord active;
        try {
            active = requireActive();
        } catch (RuntimeException exception) {
            setError(exception);
            return;
        }
        minecraft.setScreen(new ConfirmScreen(accepted -> {
            if (accepted) {
                runAction(() -> {
                    CipherChannels.channels().forget(active.id());
                    setFeedback(Component.translatable("cipherchannels.feedback.forgotten", active.name()), ChatFormatting.YELLOW);
                });
            } else {
                setFeedback(Component.translatable("cipherchannels.feedback.forget_cancelled"), ChatFormatting.GRAY);
            }
            minecraft.setScreen(this);
        }, Component.translatable("cipherchannels.confirm.forget.title"),
            Component.translatable("cipherchannels.confirm.forget.message", active.name()),
            Component.translatable("cipherchannels.confirm.forget.yes"), Component.translatable("gui.cancel")));
    }

    private void toggleBinding(ChannelRecord active) {
        runAction(() -> {
            if (active.binding() == null) {
                ServerBinding current = ClientContext.currentServer();
                if (current == null) {
                    throw new IllegalStateException("Join a saved multiplayer server before binding a channel");
                }
                CipherChannels.channels().bind(active.id(), current);
                setFeedback(Component.translatable("cipherchannels.feedback.bound", current.displayName()), ChatFormatting.GREEN);
            } else {
                CipherChannels.channels().unbind(active.id());
                setFeedback(Component.translatable("cipherchannels.feedback.unbound"), ChatFormatting.GREEN);
            }
            rebuildWidgets();
        });
    }

    private void setTransport(TransportMode mode) {
        runAction(() -> {
            CipherChannels.channels().setTransport(ClientContext.currentServer(), mode);
            setFeedback(Component.translatable("cipherchannels.feedback.transport", ClientContext.transportName(mode)), ChatFormatting.GREEN);
            rebuildWidgets();
        });
    }

    private ChannelRecord requireActive() {
        ChannelRecord active = CipherChannels.channels().config().activeChannel();
        if (active == null) {
            throw new IllegalStateException("Select a channel first");
        }
        return active;
    }

    private Component bindingSummary(ChannelRecord record) {
        if (record == null || record.binding() == null) {
            return Component.translatable("cipherchannels.overview.binding.unbound");
        }
        return Component.translatable("cipherchannels.overview.binding.bound", record.binding().displayName());
    }

    private void addText(int x, int y, int maxWidth, int maxRows, Component text, ChatFormatting color) {
        addRenderableOnly(new MultiLineTextWidget(x, y, text.copy().withStyle(color), font)
            .setMaxWidth(maxWidth).setMaxRows(maxRows));
    }

    private static void disable(Button button, Component explanation) {
        button.active = false;
        button.setTooltip(Tooltip.create(explanation));
    }

    private void setFeedback(Component message, ChatFormatting color) {
        feedback = UiFeedback.fromFormatting(message, color);
        if (feedbackWidget != null) {
            feedbackWidget.setMessage(feedback.message().copy().withStyle(feedback.color()));
        }
    }

    private void setError(RuntimeException exception) {
        String message = exception.getMessage();
        setFeedback(Component.translatable("cipherchannels.feedback.failed",
            message == null || message.isBlank()
                ? Component.translatable("cipherchannels.feedback.failed_generic") : message), ChatFormatting.RED);
    }

    private void runAction(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            setError(exception);
        }
    }
}
