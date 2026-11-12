package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

public class CheatUI {
    private final FontRenderer font;
    private final Shader shader;
    private int vao, vbo;

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

    public CheatUI(FontRenderer font) {
        this.font = font;
        this.shader = new Shader(VERT, FRAG);
        this.vao = GL30.glGenVertexArrays();
        this.vbo = GL15.glGenBuffers();
    }

    private void drawQuad(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        float[] v = { x0,y0,r,g,b,a, x1,y0,r,g,b,a, x1,y1,r,g,b,a, x0,y0,r,g,b,a, x1,y1,r,g,b,a, x0,y1,r,g,b,a };
        try (MemoryStack st = MemoryStack.stackPush()) {
            FloatBuffer fb = st.mallocFloat(v.length); fb.put(v).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    public void render() {
        if (!Cheats.enabled) return;

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

        // Чёрный фон справа сверху
        drawQuad(0.45f, 0.35f, 0.95f, 0.95f, 0f, 0f, 0f, 0.6f);
        GL30.glBindVertexArray(0);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        float ch = 0.025f;
        float x = 0.48f;
        float y = 0.90f;
        font.drawString("CHEATS ENABLED", x, y, 0.03f);
        y -= 0.04f;
        font.drawString("[F4] XRay: " + (Cheats.xray ? "ON" : "OFF"), x, y, ch); y -= 0.03f;
        font.drawString("[F6] Fly: " + (Cheats.fly ? "ON" : "OFF"), x, y, ch); y -= 0.03f;
        font.drawString("[F7] Speed: " + (Cheats.speed ? "ON" : "OFF"), x, y, ch); y -= 0.03f;
        font.drawString("[F9] Inf blocks: " + (Cheats.infiniteBlocks ? "ON" : "OFF"), x, y, ch); y -= 0.03f;
        font.drawString("[F10] Night: " + (Cheats.nightVision ? "ON" : "OFF"), x, y, ch); y -= 0.03f;
        font.drawString("[F11] God: " + (Cheats.godMode ? "ON" : "OFF"), x, y, ch); y -= 0.03f;
    }
}
