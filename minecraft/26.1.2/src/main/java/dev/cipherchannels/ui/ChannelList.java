package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.ChannelRecord;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class ChannelList extends ObjectSelectionList<ChannelList.ChannelEntry> {
    private final Font font;
    private final Consumer<ChannelRecord> select;

    ChannelList(Minecraft minecraft, int width, int height, int y, int itemHeight,
                Consumer<ChannelRecord> select) {
        super(minecraft, width, height, y, itemHeight);
        this.font = minecraft.font;
        this.select = select;
        centerListVertically = false;
    }

    void addRecord(ChannelRecord record) {
        addEntry(new ChannelEntry(record));
    }

    @Override
    public int getRowWidth() {
        return Math.max(1, getWidth() - 20);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                         float tickProgress) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, tickProgress);
        outline(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF777777);
    }

    @Override
    protected void extractSelection(GuiGraphicsExtractor graphics, ChannelEntry entry, int color) {}

    final class ChannelEntry extends ObjectSelectionList.Entry<ChannelEntry> {
        private final ChannelRecord record;

        ChannelEntry(ChannelRecord record) {
            this.record = record;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float tickProgress) {
            boolean active = record.id().equals(CipherChannels.channels().config().activeChannelId());
            boolean ready = CipherChannels.channels().hasSessionKey(record.id());
            int left = getX() + 2;
            int top = getY() + 2;
            int right = getX() + getWidth() - 2;
            int bottom = getY() + getHeight() - 2;
            int border = isFocused() ? 0xFFFFFFFF
                : active ? 0xFF55FFFF : hovered ? 0xFFAAAAAA : 0xFF555555;
            int background = active ? 0xD0183030 : hovered ? 0xC02A2A2A : 0xB0121212;
            graphics.fill(left, top, right, bottom, border);
            graphics.fill(left + 1, top + 1, right - 1, bottom - 1, background);
            if (active) {
                graphics.fill(left + 1, top + 1, left + 4, bottom - 1, 0xFF55FFFF);
            }
            Component name = Component.literal(active ? "✓ " + record.name() : record.name());
            Component status = Component.translatable(ready
                ? "cipherchannels.channels.row.ready" : "cipherchannels.channels.row.key_needed");
            if (record.binding() != null) {
                status = status.copy().append(Component.translatable("cipherchannels.channels.row.bound",
                    record.binding().displayName()));
            }
            int textX = left + 8;
            int textWidth = Math.max(1, right - textX - 8);
            drawText(graphics, name, textX, top + 5, textWidth, active ? 0xFF55FFFF : 0xFFFFFFFF);
            drawText(graphics, status, textX, top + 20, textWidth, ready ? 0xFF55FF55 : 0xFFFFAA00);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return false;
            }
            select.accept(record);
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() != GLFW.GLFW_KEY_ENTER && event.key() != GLFW.GLFW_KEY_KP_ENTER
                && event.key() != GLFW.GLFW_KEY_SPACE) return false;
            select.accept(record);
            return true;
        }

        @Override
        public Component getNarration() {
            boolean active = record.id().equals(CipherChannels.channels().config().activeChannelId());
            return Component.translatable("cipherchannels.channels.row.narration", record.name(),
                active ? Component.translatable("cipherchannels.value.active")
                    : Component.translatable("cipherchannels.value.inactive"),
                CipherChannels.channels().hasSessionKey(record.id())
                    ? Component.translatable("cipherchannels.value.ready")
                    : Component.translatable("cipherchannels.value.key_needed"),
                record.binding() == null ? Component.translatable("cipherchannels.overview.binding.unbound")
                    : Component.translatable("cipherchannels.overview.binding.bound", record.binding().displayName()));
        }
    }

    private void drawText(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int color) {
        FormattedCharSequence rendered = font.width(text) <= maxWidth
            ? text.getVisualOrderText()
            : Component.literal(font.plainSubstrByWidth(text.getString(),
                Math.max(1, maxWidth - font.width("…"))) + "…").getVisualOrderText();
        graphics.text(font, rendered, x, y, color, false);
    }

    private static void outline(GuiGraphicsExtractor graphics, int left, int top,
                                int right, int bottom, int color) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        graphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }
}
