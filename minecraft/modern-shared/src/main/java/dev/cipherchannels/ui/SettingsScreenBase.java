package dev.cipherchannels.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

abstract class SettingsScreenBase extends Screen {
    SettingsScreenBase(Component title) { super(title); }

    protected abstract boolean scrollContent(double amount);

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN && scrollContent(-4)) return true;
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP && scrollContent(4)) return true;
        return super.keyPressed(event);
    }
}
