package dev.cipherchannels.ui;

public final class UiPolicy {
    private UiPolicy() {}

    public static int editorLimit(boolean encryptionIntent, int currentDraftCharacters) {
        return encryptionIntent || currentDraftCharacters > 256 ? 4096 : 256;
    }

    public static int textBackgroundColor(double opacity) {
        double clamped = Math.max(0.0D, Math.min(1.0D, opacity));
        return ((int) Math.round(clamped * 255.0D)) << 24;
    }
}
