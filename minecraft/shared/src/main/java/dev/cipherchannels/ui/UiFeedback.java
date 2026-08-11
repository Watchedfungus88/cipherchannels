package dev.cipherchannels.ui;

import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public record UiFeedback(Severity severity, Component message) {
    public enum Severity { INFO, SUCCESS, WARNING, ERROR }

    public UiFeedback {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    public ChatFormatting color() {
        return switch (severity) {
            case INFO -> ChatFormatting.AQUA;
            case SUCCESS -> ChatFormatting.GREEN;
            case WARNING -> ChatFormatting.YELLOW;
            case ERROR -> ChatFormatting.RED;
        };
    }

    public static UiFeedback fromFormatting(Component message, ChatFormatting color) {
        Severity severity = switch (color) {
            case GREEN -> Severity.SUCCESS;
            case RED -> Severity.ERROR;
            case YELLOW -> Severity.WARNING;
            default -> Severity.INFO;
        };
        return new UiFeedback(severity, message);
    }
}
