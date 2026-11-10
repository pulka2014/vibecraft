package com.vibe.mc;

/** Прямоугольная кнопка в NDC. */
public class Button {
    public final float x0, y0, x1, y1;
    public final String label;
    public final Runnable action;
    public boolean hovered = false;

    public Button(float x0, float y0, float x1, float y1, String label, Runnable action) {
        this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        this.label = label;
        this.action = action;
    }

    public boolean contains(float mx, float my) {
        return mx >= x0 && mx <= x1 && my >= y0 && my <= y1;
    }

    public void click() { action.run(); }
}
