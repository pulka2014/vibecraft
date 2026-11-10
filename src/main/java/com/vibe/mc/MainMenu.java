package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.Random;

/** Главное меню в стиле MC: жёлтый заголовок, splash, кнопки. */
public class MainMenu {

    private enum Screen { MAIN, SINGLEPLAYER, SETTINGS, DEBUG }

    private final FontRenderer font;
    private final Shader shader;
    private int vao, vbo;
    private Screen screen = Screen.MAIN;
    private float mouseX = 0, mouseY = 0;
    private boolean open = true;
    private final java.util.List<float[]> batch = new java.util.ArrayList<>();
    private int selectedSlot = 1;
    private String pendingAction = null;
    private final String splash = pickSplash();
    private final float splashAngle = (float)(new Random().nextDouble() * 40 - 20);

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

    private static String pickSplash() {
        String[] s = {
            "Now with pressure plates!",
            "100% Java!",
            "Also try real Minecraft!",
            "Made by Vibe!",
            "Runs on CachyOS!",
            "LWJGL inside",
            "No Forge required",
            "Beware of the creepers",
            "8 worlds max!",
            "Diamond tools included",
            "Watch out for lava",
            "XRay not allowed :)",
            "GLSL 330 core",
            "Press F3 for debug",
        };
        return s[new Random().nextInt(s.length)];
    }

    public MainMenu(FontRenderer font) {
        this.font = font;
        shader = new Shader(VERT, FRAG);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
    }

    public boolean isOpen() { return open; }
    public void setOpen(boolean v) { open = v; if (!v) screen = Screen.MAIN; }
    public void setMouse(float nx, float ny) { mouseX = nx; mouseY = ny; }
    public void clearAction() { pendingAction = null; }
    public String pollAction() { String a = pendingAction; pendingAction = null; return a; }
    public boolean isInGame() { return screen == Screen.MAIN && pendingAction != null && pendingAction.startsWith("play:"); }

    private void drawQuad(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        // 6 вершин × 6 float
        batch.add(new float[]{
            x0,y0,r,g,b,a,  x1,y0,r,g,b,a,  x1,y1,r,g,b,a,
            x0,y0,r,g,b,a,  x1,y1,r,g,b,a,  x0,y1,r,g,b,a
        });
    }

    private boolean hover(float x0, float y0, float x1, float y1) {
        return mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1;
    }

    private void button(float cx, float cy, float w, float h, String label) {
        float x0 = cx - w/2, y0 = cy - h/2, x1 = cx + w/2, y1 = cy + h/2;
        boolean hov = hover(x0, y0, x1, y1);
        drawButtonRect(x0, y0, x1, y1, hov);
        float ch = 0.035f;
        float tw = font.width(label, ch);
        font.drawString(label, cx - tw/2f, cy + ch/2f, ch);
    }

    /** Плотная серая кнопка в стиле MC: заливка + светлая/тёмная окантовка. */
    /** Кладёт 5 прямоугольников кнопки в общий батч. */
    private void drawButtonRect(float x0, float y0, float x1, float y1, boolean hov) {
        float r = hov ? 0.62f : 0.50f;
        float g = r, b = r;
        drawQuad(x0, y0, x1, y1, r, g, b, 1f);                                   // fill
        drawQuad(x0, y1 - 0.006f, x1, y1, 0.78f, 0.78f, 0.78f, 1f);              // top
        drawQuad(x0, y0, x1, y0 + 0.006f, 0.20f, 0.20f, 0.20f, 1f);              // bottom
        drawQuad(x0, y0, x0 + 0.004f, y1, 0.70f, 0.70f, 0.70f, 1f);              // left
        drawQuad(x1 - 0.004f, y0, x1, y1, 0.25f, 0.25f, 0.25f, 1f);              // right
    }

    public void click(boolean left) {
        if (!left) return;
        switch (screen) {
            case MAIN -> {
                float cx = 0f;
                float w = 0.42f, h = 0.08f, gap = 0.02f;
                float y = 0.30f;
                if (hover(cx-w/2, y-h/2, cx+w/2, y+h/2)) { screen = Screen.SINGLEPLAYER; return; }
                y -= (h + gap);
                if (hover(cx-w/2, y-h/2, cx+w/2, y+h/2)) { screen = Screen.SETTINGS; return; }
                y -= (h + gap);
                if (hover(cx-w/2, y-h/2, cx+w/2, y+h/2)) { screen = Screen.DEBUG; return; }
                y -= (h + gap);
                if (hover(cx-w/2, y-h/2, cx+w/2, y+h/2)) { pendingAction = "quit"; return; }
            }
            case SINGLEPLAYER -> {
                for (int s = 1; s <= Worlds.MAX; s++) {
                    float[] r = slotRect(s);
                    if (hover(r[0], r[1], r[2], r[3])) { selectedSlot = s; pendingAction = "play:" + s; return; }
                }
                float[] back = { -0.12f, -0.92f, 0.12f, -0.84f };
                if (hover(back[0], back[1], back[2], back[3])) { screen = Screen.MAIN; return; }
                // ПКМ по слоту — удалить (простая защита без confirm)
                for (int s = 1; s <= Worlds.MAX; s++) {
                    float[] r = slotRect(s);
                    if (hover(r[0], r[1], r[2], r[3])) return;
                }
            }
            case SETTINGS -> {
                float[][] boxes = settingsButtonBoxes();
                for (int i = 0; i < boxes.length; i++) {
                    float[] b = boxes[i];
                    if (hover(b[0], b[1], b[2], b[3])) {
                        Settings st = SettingsHolder.get();
                        switch (i) {
                            case 0 -> st.fov = Math.max(40f, st.fov - 5f);
                            case 1 -> st.fov = Math.min(110f, st.fov + 5f);
                            case 2 -> st.renderDistance = Math.max(40, st.renderDistance - 20);
                            case 3 -> st.renderDistance = Math.min(200, st.renderDistance + 20);
                            case 4 -> st.mouseSensitivity = Math.max(0.02f, st.mouseSensitivity - 0.02f);
                            case 5 -> st.mouseSensitivity = Math.min(0.40f, st.mouseSensitivity + 0.02f);
                        }
                        st.save();
                        return;
                    }
                }
                float[] back = { -0.12f, -0.92f, 0.12f, -0.84f };
                if (hover(back[0], back[1], back[2], back[3])) { screen = Screen.MAIN; return; }
            }
            case DEBUG -> {
                float[][] boxes = debugButtonBoxes();
                for (int i = 0; i < boxes.length; i++) {
                    float[] b = boxes[i];
                    if (hover(b[0], b[1], b[2], b[3])) {
                        switch (i) {
                            case 0 -> pendingAction = "debug:toggle";
                            case 1 -> pendingAction = "debug:xray";
                        }
                        return;
                    }
                }
                float[] back = { -0.12f, -0.92f, 0.12f, -0.84f };
                if (hover(back[0], back[1], back[2], back[3])) { screen = Screen.MAIN; return; }
            }
        }
    }

    public void rightClick() {
        if (screen != Screen.SINGLEPLAYER) return;
        for (int s = 1; s <= Worlds.MAX; s++) {
            float[] r = slotRect(s);
            if (hover(r[0], r[1], r[2], r[3])) { pendingAction = "delete:" + s; return; }
        }
    }

    private float[] slotRect(int slot) {
        int i = slot - 1;
        int col = i % 2, row = i / 2;
        float w = 0.36f, h = 0.10f, gap = 0.02f;
        float totalW = 2*w + gap;
        float x0 = -totalW/2f + col*(w+gap);
        float y0 =  0.30f - row*(h+gap);
        return new float[]{x0, y0, x0+w, y0+h};
    }

    // Единый источник правды для позиций кнопок настроек/дебага:
    // возвращает [x0,y0,x1,y1] для каждой кнопки.
    private float[][] settingsButtonBoxes() {
        float w = 0.30f, h = 0.08f;
        float[][] pos = {
            {-0.32f, 0.20f}, {0.02f, 0.20f},
            {-0.32f, 0.10f}, {0.02f, 0.10f},
            {-0.32f, 0.00f}, {0.02f, 0.00f},
        };
        float[][] out = new float[pos.length][];
        for (int i = 0; i < pos.length; i++) {
            out[i] = new float[]{ pos[i][0], pos[i][1], pos[i][0] + w, pos[i][1] + h };
        }
        return out;
    }
    private float[][] debugButtonBoxes() {
        float w = 0.40f, h = 0.08f;
        float[][] pos = { {-0.20f, 0.10f}, {-0.20f, 0.00f} };
        float[][] out = new float[pos.length][];
        for (int i = 0; i < pos.length; i++) {
            out[i] = new float[]{ pos[i][0], pos[i][1], pos[i][0] + w, pos[i][1] + h };
        }
        return out;
    }

    public void render() {
        batch.clear();
        drawQuad(-1, -1, 1, 1, 0.10f, 0.06f, 0.04f, 1f);
        drawQuad(-1, -1, 1, -0.70f, 0.16f, 0.11f, 0.06f, 1f);

        if (screen == Screen.MAIN) renderMain();
        else if (screen == Screen.SINGLEPLAYER) renderSingleplayer();
        else if (screen == Screen.SETTINGS) renderSettings();
        else renderDebug();

        // сливаем батч в один VBO
        int total = 0;
        for (float[] f : batch) total += f.length;
        float[] v = new float[total];
        int k = 0;
        for (float[] f : batch) { System.arraycopy(f, 0, v, k, f.length); k += f.length; }

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
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
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        // текст поверх — FontRenderer сам настраивает VAO/VBO и цвет
        if (screen == Screen.MAIN) renderMainText();
        else if (screen == Screen.SINGLEPLAYER) renderSingleplayerText();
        else if (screen == Screen.SETTINGS) renderSettingsText();
        else renderDebugText();
    }

    // ----- текстовые пассы (без прямоугольников) -----
    private void renderMainText() {
        String title = "VibeCraft";
        float th = 0.13f;
        float tw = font.width(title, th);
        // тень
        font.drawStringColored(title, -tw/2f + 0.004f, 0.62f - 0.004f, th, 0.25f, 0.20f, 0.0f, 1.0f);
        // жёлтый
        font.drawStringColored(title, -tw/2f, 0.62f, th, 1.0f, 1.0f, 0.2f, 1.0f);
        // splash ниже и правее — «выныривает» из-под заголовка, как в MC
        float sh = 0.026f;
        float sx = tw/2f - 0.02f;           // правее правого края заголовка
        float sy = 0.55f;                    // заметно ниже
        // тёмная подложка для читаемости
        font.drawStringColored(splash, sx + 0.0015f, sy - 0.0015f, sh, 0f, 0f, 0f, 1f);
        font.drawStringColored(splash, sx, sy, sh, 1.0f, 1.0f, 0.2f, 1.0f);
        // подписи кнопок
        float cx = 0f, w = 0.42f, h = 0.08f, gap = 0.02f, y = 0.30f;
        String[] labels = {"Singleplayer", "Settings", "Debug", "Quit"};
        for (String lb : labels) {
            float ch = 0.035f;
            float lw = font.width(lb, ch);
            font.drawString(lb, cx - lw/2f, y + ch/2f, ch);
            y -= (h + gap);
        }
        String v = "VibeCraft 0.2  |  " + (int)SettingsHolder.get().fov + " FOV";
        font.drawString(v, -0.95f, -0.96f, 0.025f);
    }
    private void renderSingleplayerText() {
        String title = "Select World";
        float th = 0.09f;
        font.drawString(title, -font.width(title, th)/2f, 0.55f, th);
        for (int s = 1; s <= Worlds.MAX; s++) {
            float[] r = slotRect(s);
            String txt = Worlds.label(s);
            float ch = 0.035f;
            font.drawString(txt, r[0] + 0.02f, (r[1]+r[3])/2f + ch/2f, ch);
        }
        float ch = 0.035f;
        font.drawString("Back", -font.width("Back", ch)/2f, -0.88f + ch/2f, ch);
        font.drawString("LMB = play, RMB = delete", -0.95f, -0.96f, 0.022f);
    }
    private void renderSettingsText() {
        Settings st = SettingsHolder.get();
        String title = "Settings";
        float th = 0.09f;
        font.drawString(title, -font.width(title, th)/2f, 0.55f, th);
        String[] labels = {"FOV -", "FOV +", "Dist -", "Dist +", "Sens -", "Sens +"};
        // (нет отрисовки кнопок в renderSettings: он только текст, кнопки рисует renderSettingsRects)
        float[][] boxes = settingsButtonBoxes();
        for (int i = 0; i < labels.length; i++) {
            float ch = 0.03f;
            font.drawString(labels[i], boxes[i][0] + 0.02f, boxes[i][1] + 0.04f + ch/2f, ch);
        }
        String info = String.format("FOV %.0f   Dist %d   Sens %.2f", st.fov, st.renderDistance, st.mouseSensitivity);
        font.drawString(info, -font.width(info, 0.03f)/2f, -0.50f, 0.03f);
        float ch = 0.035f;
        font.drawString("Back", -font.width("Back", ch)/2f, -0.88f + ch/2f, ch);
    }
    private void renderDebugText() {
        String title = "Debug";
        float th = 0.09f;
        font.drawString(title, -font.width(title, th)/2f, 0.55f, th);
        String[] labels = {"Toggle F3 overlay", "Toggle XRay (F8)"};
        float[] pos = { -0.20f, 0.10f, -0.20f, 0.00f };
        for (int i = 0; i < 2; i++) {
            float ch = 0.03f;
            font.drawString(labels[i], pos[i*2] + 0.02f, pos[i*2+1] + 0.04f + ch/2f, ch);
        }
        float ch = 0.035f;
        font.drawString("Back", -font.width("Back", ch)/2f, -0.88f + ch/2f, ch);
    }

    private void renderMain() {
        // жёлтый заголовок
        String title = "VibeCraft";
        float th = 0.13f;
        float tw = font.width(title, th);
        // тень
        font.drawStringColored(title, -tw/2f + 0.004f, 0.55f - 0.004f, th, 0.25f, 0.20f, 0.0f, 1.0f);
        // жёлтый
        font.drawStringColored(title, -tw/2f, 0.55f, th, 1.0f, 1.0f, 0.2f, 1.0f);

        // splash — жёлтый, «наискосок» (мы не умеем поворот в font.drawString, поэтому просто рядом)
        float sh = 0.028f;
        font.drawStringColored(splash, 0.06f, 0.50f, sh, 1.0f, 1.0f, 0.2f, 1.0f);

        // кнопки
        float cx = 0f;
        float w = 0.42f, h = 0.08f, gap = 0.02f;
        float y = 0.30f;
        button(cx, y, w, h, "Singleplayer");
        y -= (h + gap);
        button(cx, y, w, h, "Settings");
        y -= (h + gap);
        button(cx, y, w, h, "Debug");
        y -= (h + gap);
        button(cx, y, w, h, "Quit");

        String v = "VibeCraft 0.2  |  " + (SettingsHolder.get().fov + " FOV");
        font.drawString(v, -0.95f, -0.96f, 0.025f);
    }

    private void renderSingleplayer() {
        String title = "Select World";
        float th = 0.09f;
        font.drawString(title, -font.width(title, th)/2f, 0.55f, th);
        for (int s = 1; s <= Worlds.MAX; s++) {
            float[] r = slotRect(s);
            boolean hov = hover(r[0], r[1], r[2], r[3]);
            boolean exists = Worlds.exists(s);
            drawButtonRect(r[0], r[1], r[2], r[3], hov);
            String txt = Worlds.label(s);
            float ch = 0.035f;
            font.drawString(txt, r[0] + 0.02f, (r[1]+r[3])/2f + ch/2f, ch);
        }
        // Back
        float[] back = { -0.12f, -0.92f, 0.12f, -0.84f };
        boolean hov = hover(back[0], back[1], back[2], back[3]);
        drawButtonRect(back[0], back[1], back[2], back[3], hov);
        float ch = 0.035f;
        font.drawString("Back", -font.width("Back", ch)/2f, -0.88f + ch/2f, ch);
        font.drawString("LMB = play, RMB = delete", -0.95f, -0.96f, 0.022f);
    }

    private void renderSettings() {
        Settings st = SettingsHolder.get();
        float[][] boxes = settingsButtonBoxes();
        for (float[] b : boxes) {
            boolean hov = hover(b[0], b[1], b[2], b[3]);
            drawButtonRect(b[0], b[1], b[2], b[3], hov);
        }
        String title = "Settings";
        float th = 0.09f;
        font.drawString(title, -font.width(title, th)/2f, 0.55f, th);
        String[] labels = {"FOV -", "FOV +", "Dist -", "Dist +", "Sens -", "Sens +"};
        float[] y = { 0.20f, 0.10f, 0.00f, 0.00f, 0.00f, 0.00f };
        // рисуем в 2 колонки
        float[][] pos = {
            {-0.32f, 0.20f}, {0.02f, 0.20f},
            {-0.32f, 0.10f}, {0.02f, 0.10f},
            {-0.32f, 0.00f}, {0.02f, 0.00f},
        };
        for (int i = 0; i < labels.length; i++) {
            float x0 = pos[i][0], y0 = pos[i][1], x1 = x0 + 0.30f, y1 = y0 + 0.08f;
            boolean hov = hover(x0, y0, x1, y1);
            drawButtonRect(x0, y0, x1, y1, hov);
            float ch = 0.03f;
            font.drawString(labels[i], x0 + 0.02f, y0 + 0.04f + ch/2f, ch);
        }
        String info = String.format("FOV %.0f   Dist %d   Sens %.2f", st.fov, st.renderDistance, st.mouseSensitivity);
        font.drawString(info, -font.width(info, 0.03f)/2f, -0.50f, 0.03f);
        float[] back = { -0.12f, -0.92f, 0.12f, -0.84f };
        boolean hov = hover(back[0], back[1], back[2], back[3]);
        drawButtonRect(back[0], back[1], back[2], back[3], hov);
        float ch = 0.035f;
        font.drawString("Back", -font.width("Back", ch)/2f, -0.88f + ch/2f, ch);
    }

    private void renderDebug() {
        float[][] boxes = debugButtonBoxes();
        for (float[] b : boxes) {
            boolean hov = hover(b[0], b[1], b[2], b[3]);
            drawButtonRect(b[0], b[1], b[2], b[3], hov);
        }
        String title = "Debug";
        float th = 0.09f;
        font.drawString(title, -font.width(title, th)/2f, 0.55f, th);
        String[] labels = {"Toggle F3 overlay", "Toggle XRay (F8)"};
        for (int i = 0; i < boxes.length; i++) {
            float ch = 0.03f;
            font.drawString(labels[i], boxes[i][0] + 0.02f, boxes[i][1] + 0.04f + ch/2f, ch);
        }
        float[] back = { -0.12f, -0.92f, 0.12f, -0.84f };
        boolean hov = hover(back[0], back[1], back[2], back[3]);
        drawButtonRect(back[0], back[1], back[2], back[3], hov);
        float ch = 0.035f;
        font.drawString("Back", -font.width("Back", ch)/2f, -0.88f + ch/2f, ch);
    }
}
