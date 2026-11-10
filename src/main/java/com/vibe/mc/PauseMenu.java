package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class PauseMenu {
    private final FontRenderer font;
    private boolean open = false;
    private final Shader shader;
    private int vao, vbo;

    private final List<Button> buttons = new ArrayList<>();
    private float mouseX = 0, mouseY = 0;
    private long openedAt = 0L;
    private boolean settingsOpen = false;
    private final java.util.List<float[]> batch = new java.util.ArrayList<>();
    public boolean isSettingsOpen() { return settingsOpen; }
    public void openSettings() { settingsOpen = true; openedAt = System.nanoTime(); }
    public void closeSettings() { settingsOpen = false; }

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec2 aPos;
        layout(location=1) in vec4 aColor;
        out vec4 vColor;
        void main() { vColor = aColor; gl_Position = vec4(aPos, 0.0, 1.0); }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec4 vColor;
        out vec4 FragColor;
        void main() { FragColor = vColor; }
        """;

    public PauseMenu(FontRenderer font) {
        this.font = font;
        shader = new Shader(VERT, FRAG);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        float[] quad = { -1,-1, 0,0,0,0.55f,  1,-1, 0,0,0,0.55f,  1,1, 0,0,0,0.55f,  -1,-1, 0,0,0,0.55f,  1,1, 0,0,0,0.55f,  -1,1, 0,0,0,0.55f };
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(quad.length);
            fb.put(quad).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        }
        int stride = 6 * 4;
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, stride, 2L * 4L);
        GL20.glEnableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
    }

    public boolean isOpen() { return open; }
    /** true, если меню открыто, но ещё «горячее» (<500мс) — Esc игнорируется. */
    public boolean isJustOpened() { return open && (System.nanoTime() - openedAt) < 500_000_000L; }

    public void open() {
        open = true;
        openedAt = System.nanoTime();
        buttons.clear();
        float bw = 0.5f, bh = 0.08f;
        float cx = 0f;
        float y = 0.10f;
        buttons.add(new Button(cx - bw/2, y,             cx + bw/2, y + bh,        "Resume",   () -> {}));
        buttons.add(new Button(cx - bw/2, y - bh*1.5f,   cx + bw/2, y - bh*0.5f,  "Save",     () -> {}));
        buttons.add(new Button(cx - bw/2, y - bh*3.0f,   cx + bw/2, y - bh*2.0f,  "Settings", () -> {}));
        buttons.add(new Button(cx - bw/2, y - bh*4.5f,   cx + bw/2, y - bh*3.5f,  "Quit",     () -> {}));
        System.out.println("[Pause] open");
    }

    public void close() { open = false; System.out.println("[Pause] close"); }

    public void setMouse(float ndcX, float ndcY) {
        this.mouseX = ndcX;
        this.mouseY = ndcY;
        for (Button b : buttons) b.hovered = b.contains(ndcX, ndcY);
    }

    /** Возвращает имя нажатой кнопки или null. */
    public String click() {
        if (settingsOpen) {
            // 3 кнопки настроек: FOV-, FOV+, Back (плюс Save при желании — не тут)
            float w = 0.30f, h = 0.08f, gap = 0.02f;
            float x0 = -w/2f, y0 = 0.10f;
            if (mouseX >= x0 && mouseX <= x0 + w && mouseY >= y0 && mouseY <= y0 + h) {
                SettingsHolder.get().fov = Math.max(40f, SettingsHolder.get().fov - 5f);
                SettingsHolder.get().save();
                return "settings:fov-";
            }
            y0 -= (h + gap);
            if (mouseX >= x0 && mouseX <= x0 + w && mouseY >= y0 && mouseY <= y0 + h) {
                SettingsHolder.get().fov = Math.min(110f, SettingsHolder.get().fov + 5f);
                SettingsHolder.get().save();
                return "settings:fov+";
            }
            y0 -= (h + gap);
            if (mouseX >= x0 && mouseX <= x0 + w && mouseY >= y0 && mouseY <= y0 + h) {
                SettingsHolder.get().renderDistance = Math.max(40, SettingsHolder.get().renderDistance - 20);
                SettingsHolder.get().save();
                return "settings:rd-";
            }
            y0 -= (h + gap);
            if (mouseX >= x0 && mouseX <= x0 + w && mouseY >= y0 && mouseY <= y0 + h) {
                SettingsHolder.get().renderDistance = Math.min(200, SettingsHolder.get().renderDistance + 20);
                SettingsHolder.get().save();
                return "settings:rd+";
            }
            y0 -= (h + gap);
            if (mouseX >= x0 && mouseX <= x0 + w && mouseY >= y0 && mouseY <= y0 + h) {
                SettingsHolder.get().mouseSensitivity = Math.max(0.02f, SettingsHolder.get().mouseSensitivity - 0.02f);
                SettingsHolder.get().save();
                return "settings:ms-";
            }
            y0 -= (h + gap);
            if (mouseX >= x0 && mouseX <= x0 + w && mouseY >= y0 && mouseY <= y0 + h) {
                SettingsHolder.get().mouseSensitivity = Math.min(0.40f, SettingsHolder.get().mouseSensitivity + 0.02f);
                SettingsHolder.get().save();
                return "settings:ms+";
            }
            y0 -= (h + gap);
            if (mouseX >= x0 && mouseX <= x0 + w && mouseY >= y0 && mouseY <= y0 + h) {
                closeSettings();
                return "settings:back";
            }
            return null;
        }
        for (Button b : buttons) {
            if (b.contains(mouseX, mouseY)) {
                b.click();
                return b.label;
            }
        }
        return null;
    }

    private void drawRect(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        batch.add(new float[]{
            x0,y0,r,g,b,a,  x1,y0,r,g,b,a,  x1,y1,r,g,b,a,
            x0,y0,r,g,b,a,  x1,y1,r,g,b,a,  x0,y1,r,g,b,a
        });
    }

    private void flushBatch() {
        int total = 0;
        for (float[] f : batch) total += f.length;
        if (total == 0) return;
        float[] v = new float[total];
        int k = 0;
        for (float[] f : batch) { System.arraycopy(f, 0, v, k, f.length); k += f.length; }
        shader.bind();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo); // ПЕРЕД glBufferData
        try (MemoryStack st = MemoryStack.stackPush()) {
            FloatBuffer fb = st.mallocFloat(v.length);
            fb.put(v).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        }
        int stride = 6 * 4;
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, stride, 2L*4L);
        GL20.glEnableVertexAttribArray(1);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, v.length / 6);
        GL30.glBindVertexArray(0);
        batch.clear();
    }

    public void render() {
        if (!open) return;
        if (settingsOpen) { renderSettings(); return; }
        batch.clear();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawRect(-1, -1, 1, 1, 0, 0, 0, 0.55f);
        for (Button b : buttons) drawMcButton(b.x0, b.y0, b.x1, b.y1, b.hovered);
        flushBatch();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        String title = "PAUSED";
        float th = 0.08f;
        float tw = font.width(title, th);
        font.drawString(title, -tw/2f, 0.3f, th);
        for (Button b : buttons) {
            float ch = 0.04f;
            float w = font.width(b.label, ch);
            float tx = (b.x0 + b.x1) / 2f - w / 2f;
            float ty = (b.y0 + b.y1) / 2f + ch / 2f;
            font.drawString(b.label, tx, ty, ch);
        }
    }

    private void renderSettings() {
        batch.clear();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawRect(-1, -1, 1, 1, 0.05f, 0.05f, 0.08f, 0.85f);
        String[] labels = {"FOV -", "FOV +", "Dist -", "Dist +", "Sens -", "Sens +", "Back"};
        float w = 0.30f, h = 0.08f, gap = 0.02f;
        float x0 = -w/2f;
        float y0 = 0.10f;
        for (int i = 0; i < labels.length; i++) {
            boolean hover = mouseX >= x0 && mouseX <= x0 + w && mouseY >= y0 && mouseY <= y0 + h;
            drawMcButton(x0, y0, x0 + w, y0 + h, hover);
            y0 -= (h + gap);
        }
        flushBatch();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        // тексты поверх (уже после flush)
        float ch = 0.035f;
        float ytxt = 0.10f;
        for (String lb : labels) {
            float tw = font.width(lb, ch);
            font.drawString(lb, -tw/2f, ytxt + h/2f + ch/2f, ch);
            ytxt -= (h + gap);
        }
        String info = String.format("FOV %.0f  Dist %d  Sens %.2f",
            SettingsHolder.get().fov, SettingsHolder.get().renderDistance, SettingsHolder.get().mouseSensitivity);
        font.drawString(info, -font.width(info, ch)/2f, 0.62f, ch);
    }

    /** Кладёт MC-кнопку в общий батч (заливка + обводка). */
    private void drawMcButton(float x0, float y0, float x1, float y1, boolean hover) {
        float r = hover ? 0.62f : 0.50f;
        drawRect(x0, y0, x1, y1, r, r, r, 1f);
        drawRect(x0, y1 - 0.006f, x1, y1, 0.78f, 0.78f, 0.78f, 1f);
        drawRect(x0, y0, x1, y0 + 0.006f, 0.20f, 0.20f, 0.20f, 1f);
        drawRect(x0, y0, x0 + 0.004f, y1, 0.70f, 0.70f, 0.70f, 1f);
        drawRect(x1 - 0.004f, y0, x1, y1, 0.25f, 0.25f, 0.25f, 1f);
    }
}
