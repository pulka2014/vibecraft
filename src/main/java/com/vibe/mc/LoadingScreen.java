package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadingScreen {
    private final Shader shader;
    private final FontRenderer font;
    private int vao, vbo;

    public final AtomicInteger total = new AtomicInteger(0);
    public final AtomicInteger loaded = new AtomicInteger(0);
    public volatile boolean finished = false;
    public volatile boolean error = false;
    public volatile String errorMsg = "";
    public volatile World world = null;

    private volatile int[][] mapColors = null;
    public static final int MAP_SIZE = 25;

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

    public LoadingScreen(FontRenderer font) {
        this.font = font;
        this.shader = new Shader(VERT, FRAG);
        this.vao = GL30.glGenVertexArrays();
        this.vbo = GL15.glGenBuffers();
        initMap();
    }

    public void initMap() {
        int[][] m = new int[MAP_SIZE][MAP_SIZE];
        for (int i = 0; i < MAP_SIZE; i++)
            for (int j = 0; j < MAP_SIZE; j++)
                m[i][j] = -1;
        this.mapColors = m;
    }

    public synchronized void setChunkColor(int cx, int cz, int rgb) {
        if (mapColors == null) initMap();
        mapColors[cz][cx] = rgb;
    }

    private void addQuad(List<Float> v, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        v.add(x0); v.add(y0); v.add(r); v.add(g); v.add(b); v.add(a);
        v.add(x1); v.add(y0); v.add(r); v.add(g); v.add(b); v.add(a);
        v.add(x1); v.add(y1); v.add(r); v.add(g); v.add(b); v.add(a);
        v.add(x0); v.add(y0); v.add(r); v.add(g); v.add(b); v.add(a);
        v.add(x1); v.add(y1); v.add(r); v.add(g); v.add(b); v.add(a);
        v.add(x0); v.add(y1); v.add(r); v.add(g); v.add(b); v.add(a);
    }

    public void render() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();

        List<Float> verts = new ArrayList<>();

        // 1) Фон
        addQuad(verts, -1, -1, 1, 1, 0.08f, 0.10f, 0.14f, 1f);

        // 2) Карта 25x25 (занимает почти весь экран)
        if (mapColors != null) {
            float cell = 1.8f / MAP_SIZE; // почти весь экран
            float x0 = -0.9f, y0 = -0.9f;
            for (int cz = 0; cz < MAP_SIZE; cz++) {
                for (int cx = 0; cx < MAP_SIZE; cx++) {
                    int c = mapColors[cz][cx];
                    float r, g, b;
                    if (c == -1) { r = 0.18f; g = 0.18f; b = 0.22f; }
                    else { r = ((c >> 16) & 0xFF)/255f; g = ((c >> 8) & 0xFF)/255f; b = (c & 0xFF)/255f; }
                    float px0 = x0 + cx * cell;
                    float py0 = y0 + (MAP_SIZE - 1 - cz) * cell;
                    addQuad(verts, px0, py0, px0 + cell, py0 + cell, r, g, b, 1f);
                }
            }
        }

        // 3) Прогресс-бар
        float totalF = Math.max(1, total.get());
        float prog = loaded.get() / totalF;
        addQuad(verts, -0.5f, -0.98f, 0.5f, -0.95f, 0.2f, 0.2f, 0.2f, 1f);
        addQuad(verts, -0.5f, -0.98f, -0.5f + 1.0f * prog, -0.95f, 0.2f, 0.8f, 0.2f, 1f);

        // Отрисовка одним вызовом
        if (!verts.isEmpty()) {
            float[] v = new float[verts.size()];
            for (int i = 0; i < verts.size(); i++) v[i] = verts.get(i);
            if (v.length == 0) return; // защита

            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            try (MemoryStack st = MemoryStack.stackPush()) {
                FloatBuffer fb = st.mallocFloat(v.length);
                fb.put(v).flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
            }
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 6*4, 0L);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 6*4, 2L*4L);
            GL20.glEnableVertexAttribArray(1);

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, v.length / 6);
            GL30.glBindVertexArray(0);
        }

        // 4) Текст (отдельно, через FontRenderer)
        font.drawString("Loading world...", -0.2f, 0.95f, 0.04f);
        String pct = String.format("%.0f%%", prog * 100);
        font.drawString(pct, -0.1f, -0.92f, 0.03f);
        if (finished) font.drawString("Ready! Press ENTER", -0.2f, -0.88f, 0.035f);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
