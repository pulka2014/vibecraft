package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

public class HUD {
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

    public HUD() {
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

    public void render(Player player) {
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

        // Здоровье (слева от хотбара)
        float hpX = -0.55f, hpY = -0.85f;
        for (int i = 0; i < 10; i++) {
            drawQuad(hpX + i*0.03f, hpY, hpX + i*0.03f + 0.02f, hpY + 0.04f, 0.2f, 0.2f, 0.2f, 1f); // фон
        }
        int hearts = player.health / 2;
        for (int i = 0; i < hearts; i++) {
            drawQuad(hpX + i*0.03f, hpY, hpX + i*0.03f + 0.02f, hpY + 0.04f, 1f, 0.2f, 0.2f, 1f); // сердце
        }

        // Голод (справа от хотбара)
        float fdX = 0.55f - 10*0.03f, fdY = -0.85f;
        for (int i = 0; i < 10; i++) {
            drawQuad(fdX + i*0.03f, fdY, fdX + i*0.03f + 0.02f, fdY + 0.04f, 0.2f, 0.2f, 0.2f, 1f);
        }
        int food = player.food / 2;
        for (int i = 0; i < food; i++) {
            drawQuad(fdX + i*0.03f, fdY, fdX + i*0.03f + 0.02f, fdY + 0.04f, 0.8f, 0.4f, 0f, 1f); // еда
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);
    }
}
