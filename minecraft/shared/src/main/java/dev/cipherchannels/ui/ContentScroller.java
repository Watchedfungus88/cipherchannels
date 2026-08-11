package dev.cipherchannels.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.AbstractWidget;

public final class ContentScroller {
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> baseY = new IdentityHashMap<>();
    private int top;
    private int bottom;
    private int offset;
    private int maximum;

    public void reset(int top, int bottom) {
        widgets.clear();
        baseY.clear();
        this.top = top;
        this.bottom = Math.max(top + 1, bottom);
        offset = 0;
        maximum = 0;
    }

    public void track(AbstractWidget widget) {
        if (baseY.containsKey(widget) || widget.getY() + widget.getHeight() <= top) return;
        widgets.add(widget);
        baseY.put(widget, widget.getY());
    }

    public void finish() {
        int extent = top;
        for (AbstractWidget widget : widgets) {
            extent = Math.max(extent, baseY.get(widget) + widget.getHeight());
        }
        maximum = Math.max(0, extent - bottom);
        offset = Math.min(offset, maximum);
        apply();
    }

    public boolean scroll(double amount) {
        if (maximum == 0) return false;
        int next = Math.max(0, Math.min(maximum, offset - (int) Math.round(amount * 18)));
        if (next == offset) return false;
        offset = next;
        apply();
        return true;
    }

    public boolean contains(double mouseY) { return mouseY >= top && mouseY < bottom; }

    private void apply() {
        for (AbstractWidget widget : widgets) {
            int y = baseY.get(widget) - offset;
            widget.setY(y);
            widget.visible = y >= top && y + widget.getHeight() <= bottom;
        }
    }
}
