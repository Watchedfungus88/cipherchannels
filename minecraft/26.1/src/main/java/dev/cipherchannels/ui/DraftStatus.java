package dev.cipherchannels.ui;

import dev.cipherchannels.channels.MessagePreflight;
import net.minecraft.network.chat.Component;

public record DraftStatus(Component message, int color, MessagePreflight preflight) {}
