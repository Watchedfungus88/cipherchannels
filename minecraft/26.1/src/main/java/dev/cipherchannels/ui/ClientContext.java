package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.ChannelRecord;
import dev.cipherchannels.channels.ChannelStatus;
import dev.cipherchannels.channels.MessagePreflight;
import dev.cipherchannels.channels.OutboundBlockReason;
import dev.cipherchannels.channels.ServerBinding;
import dev.cipherchannels.channels.TransportState;
import dev.cipherchannels.protocol.FramePreview;
import dev.cipherchannels.protocol.TransportMode;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

public final class ClientContext {
    private static String lastNotice = "";
    private static long lastNoticeAt;

    private ClientContext() {}

    public static ServerBinding currentServer() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null || server.ip == null || server.ip.isBlank()) {
            return null;
        }
        try {
            ServerAddress address = ServerAddress.parseString(server.ip);
            return ServerBinding.of(address.getHost(), address.getPort());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static String normalizeDraft(String draft) {
        return StringUtils.normalizeSpace(Objects.requireNonNull(draft, "draft").trim());
    }

    public static void notice(String message) {
        notice(Component.literal(message));
    }

    public static void notice(Component message) {
        String deduplicationKey = message.getString();
        long now = System.nanoTime();
        if (deduplicationKey.equals(lastNotice) && now - lastNoticeAt < 2_000_000_000L) {
            return;
        }
        lastNotice = deduplicationKey;
        lastNoticeAt = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.getChat().addClientSystemMessage(
                Component.translatable("cipherchannels.notice.prefix", message));
        }
    }

    public static Component entryButtonLabel() {
        ChannelStatus status = CipherChannels.channels().status(currentServer());
        String state = switch (status.state()) {
            case OFF -> "off";
            case READY -> "on";
            case NO_CHANNEL, SUSPENDED -> "blocked";
        };
        return Component.translatable("cipherchannels.chat.button." + state);
    }

    public static DraftStatus draftStatus(String draft) {
        String normalized = normalizeDraft(draft);
        if (normalized.startsWith("/")) {
            if (normalized.length() > 256) {
                return new DraftStatus(Component.translatable("cipherchannels.chat.status.command_too_long"), 0xFFFF5555, null);
            }
            return new DraftStatus(Component.translatable("cipherchannels.chat.status.command_plaintext"), 0xFFFFAA00, null);
        }

        MessagePreflight preflight = CipherChannels.channels().preflightOutgoing(normalized, currentServer());
        if (preflight.kind() == MessagePreflight.Kind.PASSTHROUGH) {
            if (normalized.length() > 256) {
                return new DraftStatus(Component.translatable("cipherchannels.chat.status.public_too_long"), 0xFFFF5555, preflight);
            }
            return new DraftStatus(Component.translatable("cipherchannels.chat.status.off"), 0xFFAAAAAA, preflight);
        }

        ChannelStatus channelStatus = preflight.channelStatus();
        ChannelRecord channel = channelStatus.activeChannel();
        String name = channel == null ? Component.translatable("cipherchannels.channel.none").getString() : channel.name();
        if (normalized.isEmpty() && channelStatus.state() == TransportState.READY) {
            return new DraftStatus(Component.translatable("cipherchannels.chat.status.encrypted",
                name, transportName(preflight.transport())), 0xFF55FFFF, preflight);
        }
        if (preflight.ready()) {
            FramePreview preview = preflight.frame();
            Component text = preview.compressed()
                ? Component.translatable("cipherchannels.chat.status.ready_compressed", preview.sourceBytes())
                : Component.translatable("cipherchannels.chat.status.ready_raw", preview.sourceBytes(), preview.capacity());
            return new DraftStatus(text, 0xFF55FF55, preflight);
        }
        return new DraftStatus(blockExplanation(preflight), 0xFFFF5555, preflight);
    }

    public static Component blockExplanation(MessagePreflight preflight) {
        OutboundBlockReason reason = preflight.blockReason();
        ChannelRecord channel = preflight.channelStatus() == null ? null : preflight.channelStatus().activeChannel();
        return switch (reason) {
            case NO_CHANNEL -> Component.translatable("cipherchannels.block.no_key",
                channel == null ? Component.translatable("cipherchannels.channel.none") : channel.name());
            case BINDING_MISMATCH -> Component.translatable("cipherchannels.block.binding",
                channel == null || channel.binding() == null ? "?" : channel.binding().displayName());
            case EMPTY -> Component.translatable("cipherchannels.block.empty");
            case MALFORMED_UNICODE -> Component.translatable("cipherchannels.block.unicode");
            case SOURCE_TOO_LARGE -> Component.translatable("cipherchannels.block.source_too_large",
                preflight.frame() == null ? "?" : preflight.frame().sourceBytes());
            case DOES_NOT_FIT -> Component.translatable("cipherchannels.block.does_not_fit",
                preflight.frame() == null ? "?" : preflight.frame().sourceBytes(),
                preflight.transport() == null ? "?" : preflight.transport().rawCapacity());
            case VANILLA_LIMIT -> Component.translatable("cipherchannels.block.vanilla_limit");
            case ENCRYPTION_FAILED -> Component.translatable("cipherchannels.block.encryption_failed");
            case NONE -> Component.translatable("cipherchannels.block.unknown");
        };
    }

    public static Component transportName(TransportMode mode) {
        return Component.translatable(mode == TransportMode.ASCII_COMPATIBILITY
            ? "cipherchannels.transport.compatibility" : "cipherchannels.transport.high_capacity");
    }
}
