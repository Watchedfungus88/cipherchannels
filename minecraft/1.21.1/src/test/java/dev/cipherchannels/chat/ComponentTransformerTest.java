package dev.cipherchannels.chat;

import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.IncomingResult;
import dev.cipherchannels.protocol.FrameFailure;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTransformerTest {
    private static final String WIRE = "A".repeat(256);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void authenticatedFramesPreserveFormattingHoverAndAdjacentNodeStyles() {
        ChannelRecord channel = new ChannelRecord(UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null);
        ComponentTransformer transformer = new ComponentTransformer(wire -> IncomingResult.authenticated("hello", channel));
        Style style = Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("original hover")));
        Component source = Component.literal("[VIP] ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(WIRE.substring(0, 91)).withStyle(style))
            .append(Component.literal(WIRE.substring(91)).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" suffix").withStyle(ChatFormatting.RED));
        Component transformed = transformer.transform(source);
        assertTrue(transformed.getString().startsWith("[VIP] hello"));
        assertTrue(transformed.getString().endsWith(" suffix"));
        assertFalse(transformed.getString().contains(WIRE));
        Component plaintext = transformed.getSiblings().stream()
            .filter(part -> part.getString().equals("hello")).findFirst().orElseThrow();
        assertEquals(style.getColor(), plaintext.getStyle().getColor());
        assertTrue(plaintext.getStyle().isUnderlined());
        HoverEvent hover = plaintext.getStyle().getHoverEvent();
        assertNotNull(hover);
        assertTrue(hover.getValue(HoverEvent.Action.SHOW_TEXT).getString().contains(WIRE));
        assertTrue(TransformedMessageRegistry.contains(transformed));
    }

    @Test
    void templatesFailuresReplayAndLegacyTextNeverRevealUnauthenticatedPlaintext() {
        ChannelRecord channel = new ChannelRecord(UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null);
        ComponentTransformer valid = new ComponentTransformer(wire -> IncomingResult.authenticated("hello", channel));
        Component template = Component.translatableWithFallback("test.chat", "%s", Component.literal("[VIP] " + WIRE));
        assertTrue(valid.transform(template).getString().contains("[VIP] hello"));
        for (FrameFailure failure : List.of(FrameFailure.UNKNOWN_CHANNEL, FrameFailure.AUTHENTICATION_FAILED,
            FrameFailure.AUTHENTICATED_INVALID, FrameFailure.REPLAYED)) {
            Component transformed = new ComponentTransformer(wire -> IncomingResult.failed(failure))
                .transform(Component.literal(WIRE));
            assertFalse(transformed.getString().contains("secret plaintext"));
            assertEquals(failure != FrameFailure.REPLAYED, transformed.getString().contains(WIRE));
        }
        Component legacy = Component.literal("~CC1:abcdefghijklmnop~");
        assertSame(legacy, valid.transform(legacy));
    }

    @Test
    void randomizedComponentsNeverProduceUnauthenticatedPlaintext() {
        java.util.Random random = new java.util.Random(0xC1F3E1L);
        ComponentTransformer transformer = new ComponentTransformer(wire -> IncomingResult.failed(FrameFailure.MALFORMED));
        for (int iteration = 0; iteration < 50_000; iteration++) {
            int length = random.nextInt(400);
            StringBuilder text = new StringBuilder(length);
            for (int index = 0; index < length; index++) {
                text.append((char) random.nextInt(Character.MAX_VALUE + 1));
            }
            assertFalse(transformer.transform(Component.literal(text.toString())).getString()
                .contains("authenticated test plaintext"));
        }
    }
}
