package dev.cipherchannels.channels;

import dev.cipherchannels.protocol.FramePreview;
import dev.cipherchannels.protocol.TransportMode;

public record MessagePreflight(Kind kind, ChannelStatus channelStatus, TransportMode transport,
                               FramePreview frame, OutboundBlockReason blockReason) {
    public enum Kind { PASSTHROUGH, READY, BLOCKED }

    public static MessagePreflight passthrough(ChannelStatus status) {
        return new MessagePreflight(Kind.PASSTHROUGH, status, null, null, OutboundBlockReason.NONE);
    }

    public static MessagePreflight ready(ChannelStatus status, FramePreview frame) {
        return new MessagePreflight(Kind.READY, status, frame.transport(), frame, OutboundBlockReason.NONE);
    }

    public static MessagePreflight blocked(ChannelStatus status, TransportMode transport,
                                           FramePreview frame, OutboundBlockReason reason) {
        return new MessagePreflight(Kind.BLOCKED, status, transport, frame, reason);
    }

    public boolean ready() {
        return kind == Kind.READY;
    }
}
