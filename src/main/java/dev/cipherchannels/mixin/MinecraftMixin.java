package dev.cipherchannels.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cipherchannels.CipherChannelsClient;
import dev.cipherchannels.ui.ChannelManagerScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "handleGlobalKeyPress", at = @At("HEAD"), cancellable = true)
    private void cipherchannels$openManager(InputConstants.Key key, boolean controlDown,
                                            CallbackInfoReturnable<Boolean> callback) {
        if (CipherChannelsClient.matchesOpenScreenKey(key)) {
            Minecraft minecraft = (Minecraft) (Object) this;
            if (minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new ChannelManagerScreen(null));
                callback.setReturnValue(true);
            }
        }
    }
}
