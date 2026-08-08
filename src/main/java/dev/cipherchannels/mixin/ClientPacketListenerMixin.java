package dev.cipherchannels.mixin;

import dev.cipherchannels.CipherChannelsClient;
import dev.cipherchannels.channels.OutboundResult;
import dev.cipherchannels.ui.ClientContext;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique private OutboundResult cipherchannels$pending;

    @Inject(method = "sendChat", at = @At("HEAD"))
    private void cipherchannels$clearStaleOutgoing(String message, CallbackInfo callback) {
        cipherchannels$pending = null;
    }

    @Inject(method = "sendChat", at = @At(value = "NEW", target = "net/minecraft/network/chat/SignedMessageBody"), cancellable = true)
    private void cipherchannels$prepareOutgoing(String message, CallbackInfo callback) {
        OutboundResult result = CipherChannelsClient.channels().prepareOutgoing(message, ClientContext.currentServer());
        if (result.kind() == OutboundResult.Kind.BLOCKED) {
            ClientContext.notice(ClientContext.blockExplanation(result.preflight()));
            cipherchannels$pending = null;
            callback.cancel();
            return;
        }
        cipherchannels$pending = result;
    }

    @ModifyArg(method = "sendChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/SignedMessageBody;<init>(Ljava/lang/String;Ljava/time/Instant;JLnet/minecraft/network/chat/LastSeenMessages;)V"), index = 0)
    private String cipherchannels$replaceBeforeSigning(String message) {
        OutboundResult result = cipherchannels$pending;
        return result != null && result.kind() == OutboundResult.Kind.ENCRYPTED ? result.frame() : message;
    }

    @ModifyArg(method = "sendChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundChatPacket;<init>(Ljava/lang/String;Ljava/time/Instant;JLnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/network/chat/LastSeenMessages$Update;)V"), index = 0)
    private String cipherchannels$replacePacketMessage(String message) {
        OutboundResult result = cipherchannels$pending;
        cipherchannels$pending = null;
        return result != null && result.kind() == OutboundResult.Kind.ENCRYPTED ? result.frame() : message;
    }

    @Inject(method = "sendChat", at = @At("RETURN"))
    private void cipherchannels$clearCompletedOutgoing(String message, CallbackInfo callback) {
        cipherchannels$pending = null;
    }

    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void cipherchannels$warnPlaintextCommand(String command, CallbackInfo callback) {
        if (CipherChannelsClient.channels().config().encryptionEnabled()) {
            ClientContext.notice(net.minecraft.network.chat.Component.translatable(
                "cipherchannels.notice.command_plaintext"));
        }
    }
}
