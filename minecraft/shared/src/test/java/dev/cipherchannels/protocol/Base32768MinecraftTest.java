package dev.cipherchannels.protocol;

import net.minecraft.util.StringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Base32768MinecraftTest {
    @Test
    void everyRepertoireCharacterIsAllowedInChat() {
        for (char character : Base32768Codec.alphabet()) {
            assertTrue(StringUtil.isAllowedChatCharacter(character),
                () -> "Rejected U+" + Integer.toHexString(character));
        }
    }
}
