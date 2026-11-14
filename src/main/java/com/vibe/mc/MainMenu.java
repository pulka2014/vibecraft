package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Главное меню: MC-style жёлтый заголовок, splash, кнопки. Singleplayer = 8 миров. */
public class MainMenu {

    private enum Screen { MAIN, SINGLEPLAYER, SETTINGS, DEBUG }

    private final FontRenderer font;
    private final Shader shader;
    private int vao, vbo;
    private boolean open = true;
    private Screen screen = Screen.MAIN;
    private float mouseX = 0, mouseY = 0;
    private int selectedSlot = 1;
    private String pendingAction = null;
    private final String splash = pickSplash();
    private int confirmDeleteSlot = 0;
    private int waitingForKey = 0; // 0 = нет, 1 = Crouch, 2 = Dive

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
            "Now with pressure plates!", "100% Java!", "Also try real Minecraft!",
            "Made by Vibe!", "Runs on CachyOS!", "LWJGL inside", "No Forge required",
            "Beware of the creepers", "8 worlds max!", "Diamond tools included",
            "Watch out for lava", "XRay not allowed :)", "GLSL 330 core", "Press F3 for debug"
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
    public String pollAction() { String a = pendingAction; pendingAction = null; return a; }

    private void drawQuad(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        float[] v = { x0,y0,r,g,b,a, x1,y0,r,g,b,a, x1,y1,r,g,b,a, x0,y0,r,g,b,a, x1,y1,r,g,b,a, x0,y1,r,g,b,a };
        try (MemoryStack st = MemoryStack.stackPush()) {
            FloatBuffer fb = st.mallocFloat(v.length); fb.put(v).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    /** Серые непрозрачные MC-кнопки. */
    private void drawButtonRect(float x0, float y0, float x1, float y1, boolean hov) {
        float r = hov ? 0.62f : 0.50f;
        drawQuad(x0, y0, x1, y1, r, r, r, 1f);
        drawQuad(x0, y1 - 0.006f, x1, y1, 0.78f, 0.78f, 0.78f, 1f);
        drawQuad(x0, y0, x1, y0 + 0.006f, 0.20f, 0.20f, 0.20f, 1f);
        drawQuad(x0, y0, x0 + 0.004f, y1, 0.70f, 0.70f, 0.70f, 1f);
        drawQuad(x1 - 0.004f, y0, x1, y1, 0.25f, 0.25f, 0.25f, 1f);
    }

    private boolean hover(float x0, float y0, float x1, float y1) {
        return mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1;
    }

    private float[] slotRect(int slot) {
        int i = slot - 1;
        int col = i % 2, row = i / 2;
        float w = 0.36f, h = 0.10f, gap = 0.02f;
        float totalW = 2*w + gap;
        float x0 = -totalW/2f + col*(w+gap);
        float y0 = 0.30f - row*(h+gap);
        return new float[]{x0, y0, x0+w, y0+h};
    }
    private float[] crossRect(int slot) {
        float[] r = slotRect(slot);
        float size = 0.03f;
        float pad = 0.005f;
        return new float[]{ r[2] - size - pad, r[3] - size - pad, r[2] - pad, r[3] - pad };
    }
    private float[] newWorldRect() {
        return new float[]{ -0.16f, -0.30f, 0.16f, -0.22f };
    }
    private float[] backRect() {
        return new float[]{ -0.12f, -0.92f, 0.12f, -0.84f };
    }

    public void click(boolean left) {
        if (!left) return;
        switch (screen) {
            case MAIN -> {
                float cx = 0f, w = 0.42f, h = 0.08f, gap = 0.02f, y = 0.05f;
                if (hover(cx-w/2, y-h/2, cx+w/2, y+h/2)) { screen = Screen.SINGLEPLAYER; return; }
                y -= (h + gap);
                if (hover(cx-w/2, y-h/2, cx+w/2, y+h/2)) { screen = Screen.SETTINGS; return; }
                y -= (h + gap);
                if (hover(cx-w/2, y-h/2, cx+w/2, y+h/2)) { screen = Screen.DEBUG; return; }
                y -= (h + gap);
                if (hover(cx-w/2, y-h/2, cx+w/2, y+h/2)) { pendingAction = "quit"; return; }
            }
            case SINGLEPLAYER -> {
                if (confirmDeleteSlot > 0) {
                    float[] yes = { -0.20f, -0.10f, 0.00f, -0.02f };
                    float[] no  = {  0.02f, -0.10f, 0.22f, -0.02f };
                    if (hover(yes[0], yes[1], yes[2], yes[3])) { pendingAction = "delete:" + confirmDeleteSlot; confirmDeleteSlot = 0; return; }
                    if (hover(no[0], no[1], no[2], no[3])) { confirmDeleteSlot = 0; return; }
                    return;
                }
                // Крестики (удаление) — проверяем ПЕРВЫМИ, чтобы клик по ним не запускал игру
                for (int s = 1; s <= Worlds.MAX; s++) {
                    if (!Worlds.exists(s)) continue;
                    float[] c = crossRect(s);
                    if (hover(c[0], c[1], c[2], c[3])) {
                        confirmDeleteSlot = s;
                        return;
                    }
                }
                for (int s = 1; s <= Worlds.MAX; s++) {
                    float[] r = slotRect(s);
                    if (hover(r[0], r[1], r[2], r[3])) { selectedSlot = s; pendingAction = "play:" + s; return; }
                }
                float[] nw = newWorldRect();
                if (hover(nw[0], nw[1], nw[2], nw[3])) {
                    int slot = -1;
                    for (int s = 1; s <= Worlds.MAX; s++) if (!Worlds.exists(s)) { slot = s; break; }
                    if (slot < 0) return;
                    pendingAction = "play:" + slot;
                    return;
                }
                float[] back = backRect();
                if (hover(back[0], back[1], back[2], back[3])) { screen = Screen.MAIN; return; }
            }
            case SETTINGS -> {
                float[][] boxes = settingsBoxes();
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
                            case 6 -> { waitingForKey = 1; }
                            case 7 -> { waitingForKey = 2; }
                        }
                        st.save();
                        return;
                    }
                }
                float[] back = backRect();
                if (hover(back[0], back[1], back[2], back[3])) { screen = Screen.MAIN; return; }
            }
            case DEBUG -> {
                float[][] boxes = debugBoxes();
                for (int i = 0; i < boxes.length; i++) {
                    float[] b = boxes[i];
                    if (hover(b[0], b[1], b[2], b[3])) {
                        pendingAction = (i == 0) ? "debug:toggle" : "debug:xray";
                        return;
                    }
                }
                float[] back = backRect();
                if (hover(back[0], back[1], back[2], back[3])) { screen = Screen.MAIN; return; }
            }
        }
    }

    public boolean onKeyPress(int key) {
        if (waitingForKey == 0) return false;
        Settings st = SettingsHolder.get();
        if (waitingForKey == 1) { st.keyCrouch = key; }
        else if (waitingForKey == 2) { st.keyDive = key; }
        st.save();
        waitingForKey = 0;
        return true;
    }

    public boolean isWaitingForKey() { return waitingForKey != 0; }

    public void rightClick() {
        if (screen != Screen.SINGLEPLAYER || confirmDeleteSlot > 0) return;
        for (int s = 1; s <= Worlds.MAX; s++) {
            float[] r = slotRect(s);
            if (hover(r[0], r[1], r[2], r[3])) { if (Worlds.exists(s)) confirmDeleteSlot = s; return; }
        }
    }

    private float[][] settingsBoxes() {
        float w = 0.30f, h = 0.08f;
        float[][] pos = {
            {-0.32f, 0.20f}, {0.02f, 0.20f},
            {-0.32f, 0.10f}, {0.02f, 0.10f},
            {-0.32f, 0.00f}, {0.02f, 0.00f},
            {-0.32f, -0.10f}, {0.02f, -0.10f}, // новые: Crouch / Dive
        };
        float[][] out = new float[pos.length][];
        for (int i = 0; i < pos.length; i++) out[i] = new float[]{ pos[i][0], pos[i][1], pos[i][0]+w, pos[i][1]+h };
        return out;
    }
    private float[][] debugBoxes() {
        return new float[][]{
            {-0.20f, 0.10f, 0.20f, 0.18f},
            {-0.20f, 0.00f, 0.20f, 0.08f},
        };
    }

    public void render() {
        if (!open) return;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        int stride = 6 * 4;
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, stride, 2L*4L);
        GL20.glEnableVertexAttribArray(1);

        drawQuad(-1, -1, 1, 1, 0.10f, 0.06f, 0.04f, 1f);
        drawQuad(-1, -1, 1, -0.70f, 0.16f, 0.11f, 0.06f, 1f);

        switch (screen) {
            case MAIN -> renderMain();
            case SINGLEPLAYER -> renderSingleplayer();
            case SETTINGS -> renderSettings();
            case DEBUG -> renderDebug();
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);

        switch (screen) {
            case MAIN -> renderMainText();
            case SINGLEPLAYER -> renderSingleplayerText();
            case SETTINGS -> renderSettingsText();
            case DEBUG -> renderDebugText();
        }
    }

    private void renderMain() {
        float cx = 0f, w = 0.42f, h = 0.08f, gap = 0.02f, y = 0.05f;
        for (String lb : new String[]{"Singleplayer", "Settings", "Debug", "Quit"}) {
            drawButtonRect(cx-w/2, y-h/2, cx+w/2, y+h/2, hover(cx-w/2, y-h/2, cx+w/2, y+h/2));
            y -= (h + gap);
        }
    }
    private void renderMainText() {
        String title = "VibeCraft";
        float th = 0.13f, tw = font.width(title, th);
        font.drawStringColored(title, -tw/2f + 0.004f, 0.62f - 0.004f, th, 0.25f, 0.20f, 0.0f, 1f);
        font.drawStringColored(title, -tw/2f, 0.62f, th, 1f, 1f, 0.2f, 1f);
        // splash: наклон 45° + пульсация ±10%
        long ns = System.nanoTime();
        float pulse = 1.0f + 0.10f * (float)Math.sin(ns / 300_000_000.0); // ±10% каждые ~2 сек
        float sh = 0.026f * pulse;
        float angle = (float)Math.toRadians(45);
        float sx = tw/2f - 0.02f;
        float sy = 0.55f;
        // тень (наклонённая)
        font.drawStringColoredRotated(splash, sx + 0.0015f, sy - 0.0015f, sh, angle, 0f, 0f, 0f, 1f);
        // жёлтый
        font.drawStringColoredRotated(splash, sx, sy, sh, angle, 1f, 1f, 0.2f, 1f);
        float cx = 0f, h = 0.08f, gap = 0.02f, y = 0.05f;
        for (String lb : new String[]{"Singleplayer", "Settings", "Debug", "Quit"}) {
            float ch = 0.035f;
            font.drawString(lb, cx - font.width(lb, ch)/2f, y + ch/2f, ch);
            y -= (h + gap);
        }
        font.drawString("VibeCraft " + DebugOverlay.version() + "  |  " + (int)SettingsHolder.get().fov + " FOV", -0.95f, -0.96f, 0.025f);
    }

    private void renderSingleplayer() {
        for (int s = 1; s <= Worlds.MAX; s++) {
            float[] r = slotRect(s);
            drawButtonRect(r[0], r[1], r[2], r[3], hover(r[0], r[1], r[2], r[3]));
            if (Worlds.exists(s)) {
                float[] c = crossRect(s);
                boolean ch = hover(c[0], c[1], c[2], c[3]);
                // Красный фон крестика
                drawQuad(c[0], c[1], c[2], c[3], ch?0.85f:0.65f, ch?0.15f:0.20f, 0.15f, 1f);
                // Сам крестик (белые полоски)
                float t = 0.004f;
                float cx0 = c[0] + t, cx1 = c[2] - t;
                float cy0 = c[1] + t, cy1 = c[3] - t;
                // Диагональ 1 (\) — строим двумя тонкими прямоугольниками
                drawQuad(cx0, cy0, cx1, cy0 + 0.006f, 1f,1f,1f,1f);
                drawQuad(cx0, cy0, cx0 + 0.006f, cy1, 1f,1f,1f,1f);
                drawQuad(cx1 - 0.006f, cy0, cx1, cy1, 1f,1f,1f,1f);
                drawQuad(cx0, cy1 - 0.006f, cx1, cy1, 1f,1f,1f,1f);
            }
        }
        float[] nw = newWorldRect();
        drawButtonRect(nw[0], nw[1], nw[2], nw[3], hover(nw[0], nw[1], nw[2], nw[3]));
        float[] back = backRect();
        drawButtonRect(back[0], back[1], back[2], back[3], hover(back[0], back[1], back[2], back[3]));

        if (confirmDeleteSlot > 0) {
            drawQuad(-1, -1, 1, 1, 0, 0, 0, 0.6f);
            float x0 = -0.17f, y0 = -0.15f, x1 = 0.17f, y1 = 0.15f;
            drawQuad(x0, y0, x1, y1, 0.35f, 0.35f, 0.35f, 1f);
            float[] yes = { -0.20f, -0.10f, 0.00f, -0.02f };
            float[] no  = {  0.02f, -0.10f, 0.22f, -0.02f };
            drawButtonRect(yes[0], yes[1], yes[2], yes[3], hover(yes[0], yes[1], yes[2], yes[3]));
            drawButtonRect(no[0], no[1], no[2], no[3], hover(no[0], no[1], no[2], no[3]));
        }
    }
    private void renderSingleplayerText() {
        String title = "Select World";
        float th = 0.09f;
        font.drawString(title, -font.width(title, th)/2f, 0.55f, th);
        for (int s = 1; s <= Worlds.MAX; s++) {
            float[] r = slotRect(s);
            String txt = Worlds.label(s);
            float ch = 0.03f;
            font.drawString(txt, r[0] + 0.02f, (r[1]+r[3])/2f + ch/2f, ch);
        }
        float[] nw = newWorldRect();
        float nwH = 0.032f;
        font.drawString("New World", (nw[0]+nw[2])/2f - font.width("New World", nwH)/2f, (nw[1]+nw[3])/2f + nwH/2f, nwH);
        float[] back = backRect();
        float ch = 0.035f;
        font.drawString("Back", (back[0]+back[2])/2f - font.width("Back", ch)/2f, (back[1]+back[3])/2f + ch/2f, ch);
        font.drawString("LMB = play, X = delete", -0.95f, -0.96f, 0.022f);
        if (confirmDeleteSlot > 0) {
            String q = "Delete slot " + confirmDeleteSlot + "?";
            float qh = 0.035f;
            font.drawString(q, -font.width(q, qh)/2f, 0.06f, qh);
            font.drawString("Yes", -0.10f - font.width("Yes", 0.03f)/2f, -0.045f, 0.03f);
            font.drawString("No",   0.12f - font.width("No", 0.03f)/2f,  -0.045f, 0.03f);
        }
    }

    private void renderSettings() {
        float[][] boxes = settingsBoxes();
        for (float[] b : boxes) drawButtonRect(b[0], b[1], b[2], b[3], hover(b[0], b[1], b[2], b[3]));
        float[] back = backRect();
        drawButtonRect(back[0], back[1], back[2], back[3], hover(back[0], back[1], back[2], back[3]));
    }
    private void renderSettingsText() {
        Settings st = SettingsHolder.get();
        String title = "Settings";
        float th = 0.09f;
        font.drawString(title, -font.width(title, th)/2f, 0.55f, th);
        String[] labels = {"FOV -", "FOV +", "Dist -", "Dist +", "Sens -", "Sens +"};
        float[][] boxes = settingsBoxes();
        for (int i = 0; i < labels.length; i++) {
            float ch = 0.03f;
            font.drawString(labels[i], boxes[i][0] + 0.02f, boxes[i][1] + 0.04f + ch/2f, ch);
        }
        String info = String.format("FOV %.0f   Dist %d   Sens %.2f", st.fov, st.renderDistance, st.mouseSensitivity);
        font.drawString(info, -font.width(info, 0.03f)/2f, -0.50f, 0.03f);
        // Подписи клавиш
        String crouchText = (waitingForKey == 1) ? "Press any key..." : "Crouch: " + org.lwjgl.glfw.GLFW.glfwGetKeyName(st.keyCrouch, 0);
        String diveText = (waitingForKey == 2) ? "Press any key..." : "Dive: " + org.lwjgl.glfw.GLFW.glfwGetKeyName(st.keyDive, 0);
        font.drawString(crouchText, -0.32f, -0.10f + 0.04f + 0.03f, 0.025f);
        font.drawString(diveText, 0.02f, -0.10f + 0.04f + 0.03f, 0.025f);
        float[] back = backRect();
        float ch = 0.035f;
        font.drawString("Back", (back[0]+back[2])/2f - font.width("Back", ch)/2f, (back[1]+back[3])/2f + ch/2f, ch);
    }

    private void renderDebug() {
        float[][] boxes = debugBoxes();
        for (float[] b : boxes) drawButtonRect(b[0], b[1], b[2], b[3], hover(b[0], b[1], b[2], b[3]));
        float[] back = backRect();
        drawButtonRect(back[0], back[1], back[2], back[3], hover(back[0], back[1], back[2], back[3]));
    }
    private void renderDebugText() {
        String title = "Debug";
        float th = 0.09f;
        font.drawString(title, -font.width(title, th)/2f, 0.55f, th);
        String[] labels = {"Toggle F3 overlay", "Toggle XRay (F8)"};
        float[][] boxes = debugBoxes();
        for (int i = 0; i < labels.length; i++) {
            float ch = 0.03f;
            font.drawString(labels[i], boxes[i][0] + 0.02f, boxes[i][1] + 0.04f + ch/2f, ch);
        }
        float[] back = backRect();
        float ch = 0.035f;
        font.drawString("Back", (back[0]+back[2])/2f - font.width("Back", ch)/2f, (back[1]+back[3])/2f + ch/2f, ch);
    }
}
