package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/** Экран загрузки мира: карта чанков (сверху) + прогресс-бар. */
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

    // Карта: цвет на чанк (0..255 R/G/B), размер 5x5 -> 80x80 блоков
    private volatile int[][] mapColors = null;

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
    }

    public void setMap(int[][] colors) { this.mapColors = colors; }

    private void drawQuad(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        float[] v = { x0,y0,r,g,b,a, x1,y0,r,g,b,a, x1,y1,r,g,b,a, x0,y0,r,g,b,a, x1,y1,r,g,b,a, x0,y1,r,g,b,a };
        try (MemoryStack st = MemoryStack.stackPush()) {
            FloatBuffer fb = st.mallocFloat(v.length); fb.put(v).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    public void render() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 6*4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 6*4, 2L*4L);
        GL20.glEnableVertexAttribArray(1);

        drawQuad(-1, -1, 1, 1, 0.08f, 0.10f, 0.14f, 1f);

        // Заголовок
        font.drawStringColored("Загрузка мира...", -0.3f, 0.7f, 0.05f, 1f, 1f, 1f, 1f);

        // Прогресс-бар
        float totalF = Math.max(1, total.get());
        float prog = loaded.get() / totalF;
        drawQuad(-0.5f, -0.75f, 0.5f, -0.68f, 0.2f, 0.2f, 0.2f, 1f);
        drawQuad(-0.5f, -0.75f, -0.5f + 1.0f * prog, -0.68f, 0.2f, 0.8f, 0.2f, 1f);
        String pct = String.format("%.0f%% (%d / %d)", prog * 100, loaded.get(), total.get());
        font.drawStringColored(pct, -font.width(pct, 0.03f)/2f, -0.64f, 0.03f, 1f, 1f, 1f, 1f);

        // Карта
        if (mapColors != null) {
            int N = mapColors.length; // 80
            float cell = 1.6f / N;
            float x0 = -0.8f, y0 = 0.0f;
            for (int z = 0; z < N; z++) {
                for (int x = 0; x < N; x++) {
                    int c = mapColors[z][x];
                    float r = ((c >> 16) & 0xFF) / 255f;
                    float g = ((c >> 8) & 0xFF) / 255f;
                    float b = (c & 0xFF) / 255f;
                    float px0 = x0 + x * cell, py0 = y0 + (N - 1 - z) * cell;
                    drawQuad(px0, py0, px0 + cell, py0 + cell, r, g, b, 1f);
                }
            }
        }

        if (finished) {
            font.drawStringColored("Готово! Нажми Enter", -0.25f, -0.90f, 0.03f, 0.6f, 1f, 0.6f, 1f);
        }
        if (error) {
            font.drawStringColored("Ошибка: " + errorMsg, -0.5f, -0.90f, 0.025f, 1f, 0.4f, 0.4f, 1f);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);
    }
}
