package dev.cipherchannels.chat;

import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.IncomingResult;
import dev.cipherchannels.protocol.Base32768Codec;
import dev.cipherchannels.protocol.FrameFailure;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTransformerTest {
    private static final String WIRE = "A".repeat(256);

    @Test
    void transformsOnlyACompleteLiteralAndPreservesStylesSiblingsAndExistingHover() {
        ChannelRecord channel = new ChannelRecord(UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null);
        ComponentTransformer transformer = new ComponentTransformer(wire -> IncomingResult.authenticated("hello", channel));
        Style wireStyle = Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("original hover")));
        Component unaffected = Component.literal(" sibling").withStyle(ChatFormatting.GREEN);
        Component source = Component.empty().withStyle(ChatFormatting.BLUE)
            .append(Component.literal(WIRE).withStyle(wireStyle))
            .append(unaffected);

        Component transformed = transformer.transform(source);
        assertEquals(Style.EMPTY.withColor(ChatFormatting.BLUE), transformed.getStyle());
        assertTrue(transformed.getString().contains("hello"));
        assertFalse(transformed.getString().contains(WIRE));
        assertEquals(Style.EMPTY.withColor(ChatFormatting.GREEN), transformed.getSiblings().getLast().getStyle());
        assertTrue(TransformedMessageRegistry.contains(transformed));

        Component plaintext = flatten(transformed).stream()
            .filter(component -> component.getString().equals("hello"))
            .findFirst().orElseThrow();
        assertEquals(wireStyle.getColor(), plaintext.getStyle().getColor());
        assertTrue(plaintext.getStyle().isUnderlined());
        HoverEvent hover = plaintext.getStyle().getHoverEvent();
        assertTrue(hover instanceof HoverEvent.ShowText);
        String hoverText = ((HoverEvent.ShowText) hover).value().getString();
        assertTrue(hoverText.contains(WIRE));
        assertTrue(hoverText.contains("original hover"));
    }

    @Test
    void unknownAlteredAndReplayFramesNeverRevealUnauthenticatedPlaintext() {
        for (FrameFailure failure : List.of(FrameFailure.UNKNOWN_CHANNEL,
            FrameFailure.AUTHENTICATION_FAILED, FrameFailure.AUTHENTICATED_INVALID, FrameFailure.REPLAYED)) {
            ComponentTransformer transformer = new ComponentTransformer(wire -> IncomingResult.failed(failure));
            Component transformed = transformer.transform(Component.literal(WIRE));
            assertFalse(transformed.getString().contains("secret plaintext"));
            if (failure == FrameFailure.REPLAYED) {
                assertFalse(transformed.getString().contains(WIRE));
            } else {
                assertTrue(transformed.getString().contains(WIRE));
            }
        }
    }

    @Test
    void transformsFramesInsideCommonServerAndModFormatting() {
        ChannelRecord channel = new ChannelRecord(UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null);
        ComponentTransformer transformer = new ComponentTransformer(wire -> IncomingResult.authenticated("hello", channel));
        String prefix = "[01:12:19] [L] henryyonrice: ";
        String suffix = " [local]";
        Component source = Component.literal(prefix + WIRE + suffix).withStyle(ChatFormatting.RED);

        Component transformed = transformer.transform(source);
        assertTrue(transformed.getString().startsWith(prefix + "hello"));
        assertTrue(transformed.getString().endsWith(suffix));
        assertFalse(transformed.getString().contains(WIRE));
        assertTrue(TransformedMessageRegistry.contains(transformed));
    }

    @Test
    void transformsHighCapacityFrameInsideFormattedLiteral() {
        String highCapacityWire = Base32768Codec.encode(new byte[Base32768Codec.BINARY_LENGTH]);
        ChannelRecord channel = new ChannelRecord(UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null);
        ComponentTransformer transformer = new ComponentTransformer(wire -> IncomingResult.authenticated("private", channel));

        Component transformed = transformer.transform(
            Component.literal("[01:12:19] [L] player: " + highCapacityWire));
        assertTrue(transformed.getString().startsWith("[01:12:19] [L] player: private"));
        assertFalse(transformed.getString().contains(highCapacityWire));
    }

    @Test
    void transformsComponentAndStringArgumentsInsideServerChatTemplates() {
        ChannelRecord channel = new ChannelRecord(UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null);
        ComponentTransformer transformer = new ComponentTransformer(wire -> IncomingResult.authenticated("hello", channel));
        Component componentArgument = Component.translatableWithFallback("test.chat.component", "<%s> %s",
            Component.literal("player"), Component.literal("[L] " + WIRE));
        Component stringArgument = Component.translatableWithFallback("test.chat.string", "%s", "[L] " + WIRE);

        Component transformedComponent = transformer.transform(componentArgument);
        Component transformedString = transformer.transform(stringArgument);
        assertTrue(transformedComponent.getString().contains("[L] hello"));
        assertTrue(transformedString.getString().contains("[L] hello"));
        assertFalse(transformedComponent.getString().contains(WIRE));
        assertFalse(transformedString.getString().contains(WIRE));
        assertTrue(TransformedMessageRegistry.contains(transformedComponent));
        assertTrue(TransformedMessageRegistry.contains(transformedString));
    }
    @Test
    void transformsOneIntactFrameAcrossAdjacentLiteralNodes() {
        ChannelRecord channel = new ChannelRecord(UUID.randomUUID(), "Friends", "0123-4567-89AB-CDEF", null);
        ComponentTransformer transformer = new ComponentTransformer(wire -> IncomingResult.authenticated("across nodes", channel));
        Component source = Component.literal("[L] ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(WIRE.substring(0, 91)).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(WIRE.substring(91)).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" suffix").withStyle(ChatFormatting.RED));
        Component transformed = transformer.transform(source);
        assertTrue(transformed.getString().startsWith("[L] across nodes"));
        assertTrue(transformed.getString().endsWith(" suffix"));
        assertFalse(transformed.getString().contains(WIRE));
        Component plaintext = flatten(transformed).stream().filter(part -> part.getString().equals("across nodes")).findFirst().orElseThrow();
        assertEquals(Style.EMPTY.withColor(ChatFormatting.GOLD).getColor(), plaintext.getStyle().getColor());
    }

    @Test
    void legacyAdjacentAndAmbiguousFramesRemainUntouched() {
        ComponentTransformer transformer = new ComponentTransformer(wire -> {
            throw new AssertionError("decryptor must not run");
        });
        Component legacy = Component.literal("~CC1:abcdefghijklmnop~");
        Component adjacent = Component.literal("x" + WIRE);
        Component ambiguous = Component.literal(WIRE + " " + "B".repeat(256));
        assertSame(legacy, transformer.transform(legacy));
        assertSame(adjacent, transformer.transform(adjacent));
        assertSame(ambiguous, transformer.transform(ambiguous));
    }

    @Test
    void componentTransformerFuzzingNeverAuthenticatesMalformedText() {
        java.util.Random random = new java.util.Random(0xC1F3E1L);
        ComponentTransformer transformer = new ComponentTransformer(wire -> IncomingResult.failed(FrameFailure.MALFORMED));
        for (int iteration = 0; iteration < 50_000; iteration++) {
            int length = random.nextInt(400);
            StringBuilder text = new StringBuilder(length);
            for (int index = 0; index < length; index++) {
                text.append((char) random.nextInt(Character.MAX_VALUE + 1));
            }
            Component transformed = transformer.transform(Component.literal(text.toString()));
            assertFalse(transformed.getString().contains("authenticated test plaintext"));
        }
    }

    private static List<Component> flatten(Component root) {
        List<Component> result = new ArrayList<>();
        result.add(root);
        for (Component sibling : root.getSiblings()) {
            result.addAll(flatten(sibling));
        }
        return result;
    }

}
