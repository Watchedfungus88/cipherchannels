package dev.cipherchannels.mixin;

import dev.cipherchannels.chat.ComponentTransformer;
import dev.cipherchannels.chat.TransformedMessageRegistry;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    private static final ComponentTransformer CIPHERCHANNELS_TRANSFORMER = new ComponentTransformer();

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
    private Component cipherchannels$transformForDisplay(Component content) {
        return CIPHERCHANNELS_TRANSFORMER.transform(content);
    }

    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    private void cipherchannels$suppressPlaintextLog(GuiMessage message, CallbackInfo callback) {
        if (TransformedMessageRegistry.contains(message.content())) {
            callback.cancel();
        }
    }
}
