package com.vibe.mc;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class CrackOverlay {
    private final Shader shader;
    private final int[] textures = new int[10];
    private int vao, vbo;
    private final int uProj, uView, uModel, uTex, uAlpha;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec2 aUV;
        uniform mat4 uProj, uView, uModel;
        out vec2 vUV;
        void main() { vUV = aUV; gl_Position = uProj * uView * uModel * vec4(aPos, 1.0); }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec2 vUV;
        uniform sampler2D uTex;
        uniform float uAlpha;
        out vec4 FragColor;
        void main() {
            vec4 c = texture(uTex, vUV);
            FragColor = vec4(0.0, 0.0, 0.0, c.a * uAlpha);
        }
        """;

    public CrackOverlay() {
        shader = new Shader(VERT, FRAG);
        uProj = shader.uniform("uProj");
        uView = shader.uniform("uView");
        uModel = shader.uniform("uModel");
        uTex = shader.uniform("uTex");
        uAlpha = shader.uniform("uAlpha");

        for (int i = 0; i < 10; i++) textures[i] = loadTexture("textures/destroy_stage_" + i + ".png");

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * 4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * 4, 3L * 4L);
        GL20.glEnableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
    }

    private int loadTexture(String path) {
        try (InputStream in = CrackOverlay.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) { System.out.println("[Crack] not found " + path); return 0; }
            BufferedImage img = ImageIO.read(in);
            int w = img.getWidth(), h = img.getHeight();
            ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(w * h * 4);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int i = (y * w + x) * 4;
                buf.put(i + 0, (byte) ((argb >> 16) & 0xFF));
                buf.put(i + 1, (byte) ((argb >> 8) & 0xFF));
                buf.put(i + 2, (byte) (argb & 0xFF));
                buf.put(i + 3, (byte) ((argb >> 24) & 0xFF));
            }
            int tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            org.lwjgl.system.MemoryUtil.memFree(buf);
            return tex;
        } catch (Exception e) {
            System.out.println("[Crack] failed " + path + ": " + e.getMessage());
            return 0;
        }
    }

    public void render(int[] hit, float progress, Matrix4f proj, Matrix4f view, World world) {
        if (hit == null || progress <= 0f) return;
        int stage = (int) (progress * 10);
        if (stage < 0) stage = 0;
        if (stage > 9) stage = 9;

        int bx = hit[0], by = hit[1], bz = hit[2];

        int[][] dirs = {{0,1,0},{0,-1,0},{0,0,1},{0,0,-1},{1,0,0},{-1,0,0}};
        float[][][] faceVerts = {
            {{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
            {{0,0,1},{0,0,0},{1,0,0},{1,0,1}},
            {{0,0,1},{1,0,1},{1,1,1},{0,1,1}},
            {{1,0,0},{0,0,0},{0,1,0},{1,1,0}},
            {{1,0,1},{1,0,0},{1,1,0},{1,1,1}},
            {{0,0,0},{0,0,1},{0,1,1},{0,1,0}},
        };
        float[][] uv = {{0,1},{1,1},{1,0},{0,0}};

        shader.bind();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[stage]);
        GL20.glUniform1i(uTex, 0);
        GL20.glUniform1f(uAlpha, 1.0f);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer pb = stack.mallocFloat(16);
            proj.get(pb); GL20.glUniformMatrix4fv(uProj, false, pb);
            view.get(pb); GL20.glUniformMatrix4fv(uView, false, pb);
            Matrix4f model = new Matrix4f().translate(0f, 0f, 0f);
            model.get(pb); GL20.glUniformMatrix4fv(uModel, false, pb);
        }

        for (int f = 0; f < 6; f++) {
            int nx = dirs[f][0], ny = dirs[f][1], nz = dirs[f][2];
            if (world.getBlock(bx + nx, by + ny, bz + nz) != BlockType.AIR) continue;

            // сдвиг наружу на 0.001, чтобы не z-fight с гранью блока
            float ox = nx * 0.001f, oy = ny * 0.001f, oz = nz * 0.001f;
            float[] verts = new float[20];
            for (int i = 0; i < 4; i++) {
                verts[i*5 + 0] = bx + faceVerts[f][i][0] + ox;
                verts[i*5 + 1] = by + faceVerts[f][i][1] + oy;
                verts[i*5 + 2] = bz + faceVerts[f][i][2] + oz;
                verts[i*5 + 3] = uv[i][0];
                verts[i*5 + 4] = uv[i][1];
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(verts.length);
                fb.put(verts).flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
            }
            GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
        }

        GL30.glBindVertexArray(0);
        GL11.glDisable(GL11.GL_BLEND);
    }
}
