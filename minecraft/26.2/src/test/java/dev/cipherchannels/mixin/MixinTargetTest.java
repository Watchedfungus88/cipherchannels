package dev.cipherchannels.mixin;

import java.time.Instant;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MixinTargetTest {
    @Test
    void resolvedMinecraftTargetsMatchEveryChatHook() {
        assertDoesNotThrow(() -> ClientPacketListener.class.getDeclaredMethod("sendChat", String.class));
        assertDoesNotThrow(() -> ClientPacketListener.class.getDeclaredMethod("sendCommand", String.class));
        assertDoesNotThrow(() -> ChatScreen.class.getDeclaredMethod("handleChatInput", String.class, boolean.class));
        assertDoesNotThrow(() -> ChatScreen.class.getDeclaredMethod("normalizeChatMessage", String.class));
        assertDoesNotThrow(() -> ChatScreen.class.getDeclaredMethod("onEdited", String.class));
        assertDoesNotThrow(() -> ChatScreen.class.getDeclaredMethod("keyPressed", KeyEvent.class));
        assertDoesNotThrow(() -> ChatComponent.class.getDeclaredMethod("addMessage", net.minecraft.network.chat.Component.class,
            MessageSignature.class, GuiMessageSource.class, GuiMessageTag.class));
        assertDoesNotThrow(() -> SignedMessageBody.class.getDeclaredConstructor(String.class, Instant.class, long.class,
            LastSeenMessages.class));
        assertDoesNotThrow(() -> ServerboundChatPacket.class.getDeclaredConstructor(String.class, Instant.class, long.class,
            MessageSignature.class, LastSeenMessages.Update.class));
    }
}
