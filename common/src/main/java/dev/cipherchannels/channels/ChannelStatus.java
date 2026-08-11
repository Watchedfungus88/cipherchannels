package dev.cipherchannels.channels;

public record ChannelStatus(TransportState state, ChannelRecord activeChannel) {}
