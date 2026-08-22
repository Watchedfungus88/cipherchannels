package dev.cipherchannels.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

abstract class FormScreenBase extends Screen {
    FormScreenBase(Component title) { super(title); }

    protected abstract void submitForm();

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submitForm();
            return true;
        }
        return super.keyPressed(event);
    }
}
