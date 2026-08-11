package dev.cipherchannels.mixin;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.chat.ChatLogProtection;
import dev.cipherchannels.channels.MessagePreflight;
import dev.cipherchannels.ui.ChannelManagerScreen;
import dev.cipherchannels.ui.ClientContext;
import dev.cipherchannels.ui.DraftStatus;
import dev.cipherchannels.ui.UiPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected EditBox input;
    @Shadow protected String initial;

    @Unique private DraftStatus cipherchannels$draftStatus;
    @Unique private Button cipherchannels$managerButton;
    @Unique private String cipherchannels$preparedRaw;
    @Unique private String cipherchannels$preparedNormalized;

    @ModifyArg(method = "init", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/EditBox;setMaxLength(I)V"), index = 0)
    private int cipherchannels$initialInputLimit(int vanillaLimit) {
        return dev.cipherchannels.ui.UiPolicy.editorLimit(
            CipherChannels.channels().config().encryptionEnabled(), initial.length());
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void cipherchannels$initialize(CallbackInfo callback) {
        Screen screen = (Screen) (Object) this;
        cipherchannels$syncInputLimit();
        cipherchannels$draftStatus = ClientContext.draftStatus(input.getValue());
        cipherchannels$managerButton = Button.builder(ClientContext.entryButtonLabel(),
            ignored -> Minecraft.getInstance().gui.setScreen(ChannelManagerScreen.create(screen)))
            .bounds(screen.width - 152, screen.height - 46, 148, 20)
            .build();
        ((ScreenInvoker) screen).cipherchannels$addRenderableWidget(cipherchannels$managerButton);
    }

    @Inject(method = "onEdited", at = @At("TAIL"))
    private void cipherchannels$refreshDraftPreview(String value, CallbackInfo callback) {
        cipherchannels$draftStatus = ClientContext.draftStatus(value);
    }

    @Inject(method = "keyPressed", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/ChatScreen;handleChatInput(Ljava/lang/String;Z)V"), cancellable = true)
    private void cipherchannels$blockEnter(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        Component reason = cipherchannels$blockedReason(input.getValue());
        if (reason != null) {
            cipherchannels$clearPreparedDraft();
            cipherchannels$draftStatus = new DraftStatus(reason, 0xFFFF5555, null);
            ClientContext.notice(reason);
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void cipherchannels$preflight(String rawMessage, boolean addToHistory, CallbackInfo callback) {
        Component reason = rawMessage.equals(cipherchannels$preparedRaw)
            ? null : cipherchannels$blockedReason(rawMessage);
        if (reason != null) {
            cipherchannels$clearPreparedDraft();
            cipherchannels$draftStatus = new DraftStatus(reason, 0xFFFF5555, null);
            ClientContext.notice(reason);
            callback.cancel();
        } else if (CipherChannels.channels().config().encryptionEnabled()) {
            ChatLogProtection.prepareOutgoing(ClientContext.normalizeDraft(rawMessage));
        }
    }

    @Inject(method = "handleChatInput", at = @At("RETURN"))
    private void cipherchannels$clearLoggingGuard(String rawMessage, boolean addToHistory, CallbackInfo callback) {
        ChatLogProtection.clearOutgoing();
    }

    @Inject(method = "normalizeChatMessage", at = @At("HEAD"), cancellable = true)
    private void cipherchannels$normalizeLongEncryptedDraft(String rawMessage,
                                                             CallbackInfoReturnable<String> callback) {
        String normalized = rawMessage.equals(cipherchannels$preparedRaw)
            ? cipherchannels$preparedNormalized : ClientContext.normalizeDraft(rawMessage);
        cipherchannels$clearPreparedDraft();
        if (CipherChannels.channels().config().encryptionEnabled() && !normalized.startsWith("/")) {
            callback.setReturnValue(normalized);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void cipherchannels$renderStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                              float tickProgress, CallbackInfo callback) {
        Screen screen = (Screen) (Object) this;
        DraftStatus status = cipherchannels$draftStatus == null
            ? ClientContext.draftStatus(input.getValue()) : cipherchannels$draftStatus;
        Font font = Minecraft.getInstance().font;
        int x = Math.max(4, screen.width - font.width(status.message()) - 4);
        int y = screen.height - 66;
        int background = UiPolicy.textBackgroundColor(
            Minecraft.getInstance().options.textBackgroundOpacity().get());
        graphics.fill(x - 2, y - 2, screen.width - 2, y + font.lineHeight + 2, background);
        graphics.text(font, status.message(), x, y, status.color(), true);
    }

    @Unique
    private void cipherchannels$syncInputLimit() {
        input.setMaxLength(dev.cipherchannels.ui.UiPolicy.editorLimit(
            CipherChannels.channels().config().encryptionEnabled(), input.getValue().length()));
    }

    @Unique
    private Component cipherchannels$blockedReason(String raw) {
        String normalized = ClientContext.normalizeDraft(raw);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("/")) {
            return normalized.length() > 256
                ? Component.translatable("cipherchannels.block.command_limit") : null;
        }
        if (!ChatLogProtection.allowsEncryption() && CipherChannels.channels().config().encryptionEnabled()) {
            return Component.translatable("cipherchannels.block.chat_logging");
        }
        MessagePreflight preflight = raw.equals(input.getValue()) && cipherchannels$draftStatus != null
            && cipherchannels$draftStatus.preflight() != null
            ? cipherchannels$draftStatus.preflight()
            : CipherChannels.channels().preflightOutgoing(normalized, ClientContext.currentServer());
        if (preflight.kind() == MessagePreflight.Kind.PASSTHROUGH) {
            return normalized.length() > 256
                ? Component.translatable("cipherchannels.block.vanilla_limit") : null;
        }
        if (preflight.ready()) {
            cipherchannels$preparedRaw = raw;
            cipherchannels$preparedNormalized = normalized;
            ChatLogProtection.prepareOutgoing(normalized);
            return null;
        }
        return ClientContext.blockExplanation(preflight);
    }

    @Unique
    private void cipherchannels$clearPreparedDraft() {
        cipherchannels$preparedRaw = null;
        cipherchannels$preparedNormalized = null;
    }
}
