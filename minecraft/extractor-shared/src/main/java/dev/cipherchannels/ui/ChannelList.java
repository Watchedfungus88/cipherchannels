package dev.cipherchannels.ui;

import dev.cipherchannels.CipherChannels;
import dev.cipherchannels.channels.ChannelRecord;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class ChannelList extends ObjectSelectionList<ChannelList.ChannelEntry> {
    private final Font font;
    private final int rowWidth;
    private final Consumer<ChannelRecord> select;
    private final Consumer<ChannelRecord> use;

    ChannelList(Minecraft minecraft, int width, int height, int y, int itemHeight, int rowWidth,
                Consumer<ChannelRecord> select, Consumer<ChannelRecord> use) {
        super(minecraft, width, height, y, itemHeight);
        this.font = minecraft.font;
        this.rowWidth = rowWidth;
        this.select = select;
        this.use = use;
        centerListVertically = false;
    }

    void addRecord(ChannelRecord record, boolean selected) {
        ChannelEntry entry = new ChannelEntry(record);
        addEntry(entry);
        if (selected) setSelected(entry);
    }

    @Override
    public void setSelected(ChannelEntry entry) {
        super.setSelected(entry);
        if (entry != null) select.accept(entry.record);
    }

    @Override
    public int getRowWidth() {
        return Math.max(1, Math.min(rowWidth, getWidth() - 20));
    }

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
            Component name = Component.literal(record.name());
            Component activeLabel = Component.translatable("cipherchannels.manager.row.active");
            Component status = Component.translatable(ready
                ? "cipherchannels.manager.row.key_loaded" : "cipherchannels.manager.row.key_needed");
            int activeWidth = active ? font.width(activeLabel) : 0;
            drawText(graphics, name, getX() + 4, getY() + 6,
                Math.max(1, getWidth() - 8 - (active ? activeWidth + 8 : 0)), 0xFFFFFFFF);
            if (active) graphics.text(font, activeLabel, getX() + getWidth() - activeWidth - 4,
                getY() + 6, 0xFFAAAAAA, false);
            drawText(graphics, status, getX() + 4, getY() + 20, getWidth() - 8,
                ready ? 0xFFAAAAAA : 0xFFFFAA00);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) return false;
            ChannelList.this.setSelected(this);
            if (doubleClick) use.accept(record);
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                use.accept(record);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_SPACE) {
                ChannelList.this.setSelected(this);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            boolean active = record.id().equals(CipherChannels.channels().config().activeChannelId());
            boolean ready = CipherChannels.channels().hasSessionKey(record.id());
            return Component.translatable("cipherchannels.manager.row.narration", record.name(),
                Component.translatable(active ? "cipherchannels.value.active" : "cipherchannels.value.inactive"),
                Component.translatable(ready
                    ? "cipherchannels.manager.row.key_loaded" : "cipherchannels.manager.row.key_needed"));
        }
    }

    private void drawText(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int color) {
        FormattedCharSequence rendered = font.width(text) <= maxWidth
            ? text.getVisualOrderText()
            : Component.literal(font.plainSubstrByWidth(text.getString(),
                Math.max(1, maxWidth - font.width("…"))) + "…").getVisualOrderText();
        graphics.text(font, rendered, x, y, color, false);
    }
}
