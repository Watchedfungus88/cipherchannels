package dev.cipherchannels.chat;

import dev.cipherchannels.protocol.FrameScanner;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.minecraft.network.chat.Component;

public final class ChatLogProtection {
    public enum State { NOT_INSTALLED, DISABLED, PROTECTED, UNSAFE }
    public static final String PLACEHOLDER = "[CipherChannels encrypted message intentionally not stored]";
    private static final ThreadLocal<byte[]> OUTGOING = new ThreadLocal<>();
    private static volatile boolean hookConfigured;

    private ChatLogProtection() {}

    public static State state() {
        try {
            ClassLoader loader = ChatLogProtection.class.getClassLoader();
            Class<?> log = Class.forName("obro1961.chatpatches.ChatLog", false, loader);
            Class<?> main = Class.forName("obro1961.chatpatches.ChatPatches", false, loader);
            Object config = main.getField("config").get(null);
            if (config == null || !config.getClass().getField("chatlog").getBoolean(config)) return State.DISABLED;
            Method history = log.getDeclaredMethod("addHistory", String.class);
            Method message = log.getDeclaredMethod("addMessage", Component.class);
            return hookConfigured && history.getReturnType() == void.class && message.getReturnType() == void.class
                ? State.PROTECTED : State.UNSAFE;
        } catch (ClassNotFoundException exception) {
            return State.NOT_INSTALLED;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return State.UNSAFE;
        }
    }

    public static boolean allowsEncryption() { return state() != State.UNSAFE; }
    public static void setHookConfigured(boolean configured) { hookConfigured = configured; }
    public static void prepareOutgoing(String plaintext) { OUTGOING.set(digest(plaintext)); }

    public static void clearOutgoing() {
        byte[] value = OUTGOING.get();
        if (value != null) java.util.Arrays.fill(value, (byte) 0);
        OUTGOING.remove();
    }

    public static String sanitizeHistory(String value) {
        byte[] expected = OUTGOING.get();
        byte[] actual = digest(value);
        boolean matches = expected != null && MessageDigest.isEqual(expected, actual);
        java.util.Arrays.fill(actual, (byte) 0);
        return matches || !FrameScanner.scan(value).isEmpty() ? PLACEHOLDER : value;
    }

    public static Component sanitizeMessage(Component value) {
        return TransformedMessageRegistry.contains(value) || !FrameScanner.scan(value.getString()).isEmpty()
            ? Component.literal(PLACEHOLDER) : value;
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
