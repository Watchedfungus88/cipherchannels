package dev.cipherchannels.mixin;

import dev.cipherchannels.chat.ChatLogProtection;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CipherChannelsMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.endsWith("ChatPatchesChatLogMixin")) return true;
        ChatLogProtection.setHookConfigured(false);
        return compatibleChatPatches();
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.endsWith("ChatPatchesChatLogMixin")) return;
        String owner = ChatLogProtection.class.getName().replace('.', '/');
        ChatLogProtection.setHookConfigured(hasHook(targetClass, "addHistory", owner, "sanitizeHistory")
            && hasHook(targetClass, "addMessage", owner, "sanitizeMessage"));
    }

    private static boolean compatibleChatPatches() {
        try {
            Class<?> type = Class.forName("obro1961.chatpatches.ChatLog", false,
                CipherChannelsMixinPlugin.class.getClassLoader());
            return type.getDeclaredMethod("addHistory", String.class).getReturnType() == void.class
                && type.getDeclaredMethod("addMessage", Component.class).getReturnType() == void.class;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    private static boolean hasHook(ClassNode type, String targetName, String owner, String sanitizer) {
        for (var target : type.methods) {
            if (!target.name.equals(targetName)) continue;
            for (AbstractInsnNode instruction : target.instructions) {
                if (!(instruction instanceof MethodInsnNode handler) || !handler.owner.equals(type.name)) continue;
                for (var method : type.methods) {
                    if (!method.name.equals(handler.name) || !method.desc.equals(handler.desc)) continue;
                    for (AbstractInsnNode nested : method.instructions) {
                        if (nested instanceof MethodInsnNode call && call.owner.equals(owner)
                            && call.name.equals(sanitizer)) return true;
                    }
                }
            }
        }
        return false;
    }
}
