package dev.cipherchannels.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

abstract class SettingsScreenBase extends Screen {
    SettingsScreenBase(Component title) { super(title); }

    protected abstract boolean scrollContent(double amount);

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN && scrollContent(-4)) return true;
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP && scrollContent(4)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
