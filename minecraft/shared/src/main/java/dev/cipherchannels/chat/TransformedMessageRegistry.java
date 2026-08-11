package dev.cipherchannels.chat;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;

public final class TransformedMessageRegistry {
    private static final Set<Component> TRANSFORMED = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    private TransformedMessageRegistry() {}

    public static Component mark(Component component) {
        TRANSFORMED.add(component);
        return component;
    }

    public static boolean contains(Component component) { return TRANSFORMED.contains(component); }
}
