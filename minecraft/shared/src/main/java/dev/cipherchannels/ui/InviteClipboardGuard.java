package dev.cipherchannels.ui;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class InviteClipboardGuard {
    private static final Lease LEASE = new Lease();

    private InviteClipboardGuard() {}

    public static synchronized void copy(UUID channelId, String invite) {
        Minecraft.getInstance().keyboardHandler.setClipboard(invite);
        LEASE.copy(channelId, invite);
    }

    public static synchronized void clearMatching(UUID channelId) {
        String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (LEASE.clearMatching(channelId, clipboard)) Minecraft.getInstance().keyboardHandler.setClipboard("");
    }

    public static synchronized void close() {
        clearMatching(null);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static final class Lease {
        private byte[] copiedDigest;
        private UUID copiedChannel;

        synchronized void copy(UUID channelId, String invite) {
            replaceDigest(digest(invite));
            copiedChannel = channelId;
        }

        synchronized boolean clearMatching(UUID channelId, String clipboard) {
            if (channelId != null && !channelId.equals(copiedChannel)) return false;
            return clear(clipboard);
        }

        private boolean clear(String clipboard) {
            if (copiedDigest == null) return false;
            byte[] current = digest(clipboard);
            boolean matches = MessageDigest.isEqual(copiedDigest, current);
            java.util.Arrays.fill(current, (byte) 0);
            replaceDigest(null);
            copiedChannel = null;
            return matches;
        }

        private void replaceDigest(byte[] next) {
            if (copiedDigest != null) java.util.Arrays.fill(copiedDigest, (byte) 0);
            copiedDigest = next;
        }
    }
}
