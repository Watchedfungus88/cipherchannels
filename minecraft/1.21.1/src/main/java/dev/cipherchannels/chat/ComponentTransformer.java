package dev.cipherchannels.chat;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.IncomingResult;
import dev.cipherchannels.protocol.FrameCandidate;
import dev.cipherchannels.protocol.FrameFailure;
import dev.cipherchannels.protocol.FrameScanner;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class ComponentTransformer {
    private final Function<String, IncomingResult> decryptor;

    public ComponentTransformer() {
        this(wire -> CipherChannels.channels().decryptIncoming(wire));
    }

    ComponentTransformer(Function<String, IncomingResult> decryptor) {
        this.decryptor = Objects.requireNonNull(decryptor, "decryptor");
    }

    public Component transform(Component source) {
        if (!ChatLogProtection.allowsEncryption()) {
            return source;
        }
        TransformResult flattened = transformAcrossNodes(source);
        if (flattened != null) {
            return TransformedMessageRegistry.mark(flattened.component);
        }
        TransformResult result = transformInternal(source);
        return result.changed ? TransformedMessageRegistry.mark(result.component) : source;
    }

    private TransformResult transformAcrossNodes(Component source) {
        List<FlatPart> parts = new ArrayList<>();
        flatten(source, Style.EMPTY, parts);
        boolean crossing = false;
        for (int start = 0; start < parts.size();) {
            if (parts.get(start).component != null) {
                start++;
                continue;
            }
            int end = start;
            StringBuilder text = new StringBuilder();
            while (end < parts.size() && parts.get(end).component == null) text.append(parts.get(end++).text);
            for (FrameCandidate candidate : FrameScanner.scan(text.toString())) {
                if (leafAt(parts, start, end, candidate.start()) != leafAt(parts, start, end, candidate.endExclusive() - 1)) {
                    crossing = true;
                    break;
                }
            }
            if (crossing) break;
            start = end;
        }
        if (!crossing) return null;
        MutableComponent result = Component.empty();
        for (int start = 0; start < parts.size();) {
            FlatPart part = parts.get(start);
            if (part.component != null) {
                result.append(transformInternal(part.component).component);
                start++;
                continue;
            }
            int end = start;
            while (end < parts.size() && parts.get(end).component == null) end++;
            appendRun(result, parts, start, end);
            start = end;
        }
        return new TransformResult(result, true);
    }

    private void flatten(Component source, Style inherited, List<FlatPart> parts) {
        Style effective = source.getStyle().applyTo(inherited);
        if (source.getContents() instanceof PlainTextContents plain) {
            parts.add(new FlatPart(plain.text(), effective, null));
        } else {
            parts.add(new FlatPart(null, null, source.plainCopy().withStyle(effective)));
        }
        for (Component sibling : source.getSiblings()) flatten(sibling, effective, parts);
    }

    private void appendRun(MutableComponent result, List<FlatPart> parts, int start, int end) {
        StringBuilder text = new StringBuilder();
        for (int index = start; index < end; index++) text.append(parts.get(index).text);
        int cursor = 0;
        for (FrameCandidate candidate : FrameScanner.scan(text.toString())) {
            appendRange(result, parts, start, end, cursor, candidate.start());
            IncomingResult incoming = decryptor.apply(candidate.wire());
            Style style = parts.get(leafAt(parts, start, end, candidate.start())).style;
            if (incoming.authenticated()) {
                result.append(Component.literal(incoming.plaintext()).withStyle(plaintextStyle(style, candidate.wire())));
                result.append(authenticatedBadge(incoming.channel().name()));
            } else if (incoming.failure() == FrameFailure.REPLAYED) {
                result.append(Component.translatable("cipherchannels.chat.replay_blocked").withStyle(ChatFormatting.RED));
            } else {
                appendRange(result, parts, start, end, candidate.start(), candidate.endExclusive());
                result.append(diagnosticBadge(incoming.failure()));
            }
            cursor = candidate.endExclusive();
        }
        appendRange(result, parts, start, end, cursor, text.length());
    }

    private static void appendRange(MutableComponent result, List<FlatPart> parts, int start, int end,
                                    int from, int to) {
        int offset = 0;
        for (int index = start; index < end; index++) {
            FlatPart part = parts.get(index);
            int next = offset + part.text.length();
            int localStart = Math.max(from, offset) - offset;
            int localEnd = Math.min(to, next) - offset;
            if (localEnd > localStart) result.append(Component.literal(part.text.substring(localStart, localEnd)).withStyle(part.style));
            offset = next;
        }
    }

    private static int leafAt(List<FlatPart> parts, int start, int end, int position) {
        int offset = 0;
        for (int index = start; index < end; index++) {
            offset += parts.get(index).text.length();
            if (position < offset) return index;
        }
        return end - 1;
    }

    private TransformResult transformInternal(Component source) {
        boolean changed = false;
        MutableComponent result;
        if (source.getContents() instanceof PlainTextContents plain) {
            LiteralResult literal = transformLiteral(plain.text(), source.getStyle());
            result = literal.component;
            changed = literal.changed;
        } else if (source.getContents() instanceof TranslatableContents translatable) {
            TransformResult translated = transformTranslatable(translatable, source.getStyle());
            result = translated.component;
            changed = translated.changed;
        } else {
            result = source.plainCopy();
        }
        for (Component sibling : source.getSiblings()) {
            TransformResult transformedSibling = transformInternal(sibling);
            result.append(transformedSibling.component);
            changed |= transformedSibling.changed;
        }
        return new TransformResult(result, changed);
    }

    private TransformResult transformTranslatable(TranslatableContents contents, Style inheritedStyle) {
        Object[] arguments = contents.getArgs().clone();
        boolean changed = false;
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            if (argument instanceof Component component) {
                TransformResult transformed = transformInternal(component);
                if (transformed.changed) {
                    arguments[index] = transformed.component;
                    changed = true;
                }
            } else if (argument instanceof String text) {
                LiteralResult transformed = transformLiteral(text, Style.EMPTY);
                if (transformed.changed) {
                    arguments[index] = transformed.component;
                    changed = true;
                }
            }
        }

        TranslatableContents resultContents = changed
            ? new TranslatableContents(contents.getKey(), contents.getFallback(), arguments)
            : contents;
        return new TransformResult(MutableComponent.create(resultContents).withStyle(inheritedStyle), changed);
    }

    private LiteralResult transformLiteral(String text, Style inheritedStyle) {
        List<FrameCandidate> candidates = FrameScanner.scan(text);
        if (candidates.isEmpty()) {
            return new LiteralResult(Component.literal(text).withStyle(inheritedStyle), false);
        }
        MutableComponent result = Component.empty().withStyle(inheritedStyle);
        int cursor = 0;
        for (FrameCandidate candidate : candidates) {
            result.append(Component.literal(text.substring(cursor, candidate.start())).withStyle(inheritedStyle));
            IncomingResult incoming = decryptor.apply(candidate.wire());
            if (incoming.authenticated()) {
                result.append(Component.literal(incoming.plaintext()).withStyle(plaintextStyle(inheritedStyle, candidate.wire())));
                result.append(authenticatedBadge(incoming.channel().name()));
            } else if (incoming.failure() == FrameFailure.REPLAYED) {
                result.append(Component.translatable("cipherchannels.chat.replay_blocked").withStyle(ChatFormatting.RED));
            } else {
                result.append(Component.literal(candidate.wire()).withStyle(inheritedStyle));
                result.append(diagnosticBadge(incoming.failure()));
            }
            cursor = candidate.endExclusive();
        }
        result.append(Component.literal(text.substring(cursor)).withStyle(inheritedStyle));
        return new LiteralResult(result, true);
    }

    private static Component authenticatedBadge(String name) {
        Component tooltip = Component.translatable("cipherchannels.chat.badge.authenticated", name)
            .append("\n").append(Component.translatable("cipherchannels.chat.badge.authorship"));
        return Component.translatable("cipherchannels.chat.badge.label").withStyle(style ->
            style.withColor(ChatFormatting.AQUA).withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip)));
    }

    private static Style plaintextStyle(Style original, String wire) {
        MutableComponent tooltip = Component.translatable("cipherchannels.chat.hover.wire").append("\n")
            .append(Component.literal(wire).withStyle(ChatFormatting.GRAY));
        HoverEvent hover = original.getHoverEvent();
        Component existing = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_TEXT);
        if (existing != null) {
            tooltip.append("\n\n").append(Component.translatable("cipherchannels.chat.hover.original"))
                .append("\n").append(existing);
        }
        return original.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
    }

    private static Component diagnosticBadge(FrameFailure failure) {
        String key = switch (failure) {
            case MALFORMED -> "cipherchannels.chat.diagnostic.malformed";
            case UNKNOWN_CHANNEL -> "cipherchannels.chat.diagnostic.unknown";
            case AUTHENTICATION_FAILED -> "cipherchannels.chat.diagnostic.altered";
            case AUTHENTICATED_INVALID -> "cipherchannels.chat.diagnostic.invalid_content";
            case REPLAYED -> "cipherchannels.chat.diagnostic.replay";
        };
        ChatFormatting color = failure == FrameFailure.UNKNOWN_CHANNEL ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.translatable(key).withStyle(color);
    }

    private record TransformResult(MutableComponent component, boolean changed) {}

    private record LiteralResult(MutableComponent component, boolean changed) {}

    private record FlatPart(String text, Style style, MutableComponent component) {}
}
