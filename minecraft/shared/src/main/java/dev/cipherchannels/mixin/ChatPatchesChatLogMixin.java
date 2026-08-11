package dev.cipherchannels.mixin;

import dev.cipherchannels.chat.ChatLogProtection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "obro1961.chatpatches.ChatLog", remap = false)
public abstract class ChatPatchesChatLogMixin {
    @ModifyVariable(method = "addHistory", at = @At("HEAD"), argsOnly = true, require = 0)
    private static String cipherchannels$sanitizeHistory(String value) {
        return ChatLogProtection.sanitizeHistory(value);
    }

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Component cipherchannels$sanitizeMessage(Component value) {
        return ChatLogProtection.sanitizeMessage(value);
    }
}
