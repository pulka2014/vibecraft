package com.vibe.mc;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * Чёрная рамка вокруг блока под прицелом.
 * Рисует 12 рёбер куба через GL_LINES.
 */
public class WorldHighlight {
    private final Shader shader;
    private final int vao, vbo;
    private final int uProj, uView, uModel;
    private static final float E = 0.002f; // чуть снаружи блока, чтобы не z-fight

    // 12 рёбер куба: пары вершин
    private static final float[] EDGES = {
        0,0,0,  1,0,0,   1,0,0,  1,0,1,   1,0,1,  0,0,1,   0,0,1,  0,0,0,
        0,1,0,  1,1,0,   1,1,0,  1,1,1,   1,1,1,  0,1,1,   0,1,1,  0,1,0,
        0,0,0,  0,1,0,   1,0,0,  1,1,0,   1,0,1,  1,1,1,   0,0,1,  0,1,1,
    };

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        uniform mat4 uProj, uView, uModel;
        void main() { gl_Position = uProj * uView * uModel * vec4(aPos, 1.0); }
        """;
    private static final String FRAG = """
        #version 330 core
        out vec4 FragColor;
        void main() { FragColor = vec4(0.0, 0.0, 0.0, 1.0); }
        """;

    public WorldHighlight() {
        shader = new Shader(VERT, FRAG);
        uProj = shader.uniform("uProj");
        uView = shader.uniform("uView");
        uModel = shader.uniform("uModel");
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(EDGES.length);
            fb.put(EDGES).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        }
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * 4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
    }

    public void render(int[] hit, Matrix4f proj, Matrix4f view, World world) {
        if (hit == null) return;
        float height = 1f;
        if (world != null && world.getBlock(hit[0], hit[1], hit[2]) == BlockType.PRESSURE_PLATE) {
            height = 1f / 16f;
        }
        float x = hit[0] - E, y = hit[1] - E, z = hit[2] - E;
        float sx = 1f + 2 * E;
        float sy = height + 2 * E;
        float sz = 1f + 2 * E;
        Matrix4f model = new Matrix4f().translate(x, y, z).scale(sx, sy, sz);
        shader.bind();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer pb = stack.mallocFloat(16);
            proj.get(pb); GL20.glUniformMatrix4fv(uProj, false, pb);
            view.get(pb); GL20.glUniformMatrix4fv(uView, false, pb);
            model.get(pb); GL20.glUniformMatrix4fv(uModel, false, pb);
        }
        GL11.glLineWidth(2f);
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_LINES, 0, EDGES.length / 3);
        GL30.glBindVertexArray(0);
    }
}
