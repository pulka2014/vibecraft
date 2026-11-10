package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * Простой крестик в центре экрана через NDC-координаты.
 * Рисуется после всего, с выключенным depth test.
 */
public class Crosshair {
    private final Shader shader;
    private int vao, vbo;
    private final int vertexCount;

    public Crosshair() {
        String vert = """
            #version 330 core
            layout(location=0) in vec2 aPos;
            void main() { gl_Position = vec4(aPos, 0.0, 1.0); }
            """;
        String frag = """
            #version 330 core
            out vec4 FragColor;
            void main() { FragColor = vec4(1.0, 1.0, 1.0, 0.9); }
            """;
        shader = new Shader(vert, frag);

        // 2 линии по 2 треугольника: горизонталь и вертикаль, толщина 0.008 в NDC
        float t = 0.0025f;
        float l = 0.022f;
        float[] v = {
            -l,-t,  l,-t,  l, t,  -l,-t,  l, t, -l, t,   // горизонт
            -t,-l,  t,-l,  t, l,  -t,-l,  t, l, -t, l    // вертикаль
        };
        vertexCount = v.length / 2;

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(v.length);
            fb.put(v).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        }
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * 4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
    }

    public void render() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        shader.bind();
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
