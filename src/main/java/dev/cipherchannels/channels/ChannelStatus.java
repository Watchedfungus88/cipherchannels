package dev.cipherchannels.channels;

public record ChannelStatus(TransportState state, ChannelRecord activeChannel, String reason) {
    public boolean encrypts() {
        return state == TransportState.READY;
    }
}
