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

    public static boolean validChannelName(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 48) return false;
        for (int index = 0; index < trimmed.length(); index++) {
            if (Character.isISOControl(trimmed.charAt(index))) return false;
        }
        return true;
    }
}
