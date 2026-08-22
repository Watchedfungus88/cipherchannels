package dev.cipherchannels.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

abstract class FormScreenBase extends Screen {
    FormScreenBase(Component title) { super(title); }

    protected abstract void submitForm();

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submitForm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
