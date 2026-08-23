package dev.cipherchannels.chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.Component;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatLogProtectionTest {
    @AfterEach void clear() { ChatLogProtection.clearOutgoing(); }

    @Test
    void replacesOnlyPreparedPlaintextOrEncryptedFramesInPersistentHistory() {
        ChatLogProtection.prepareOutgoing("private words");
        assertEquals(ChatLogProtection.PLACEHOLDER, ChatLogProtection.sanitizeHistory("private words"));
        assertEquals("different words", ChatLogProtection.sanitizeHistory("different words"));
        assertEquals(ChatLogProtection.PLACEHOLDER, ChatLogProtection.sanitizeHistory("A".repeat(256)));
        assertEquals(ChatLogProtection.PLACEHOLDER,
            ChatLogProtection.sanitizeMessage(Component.literal("[VIP] " + "A".repeat(256))).getString());
    }
}
