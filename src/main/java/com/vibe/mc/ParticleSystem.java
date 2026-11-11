package com.vibe.mc;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleSystem {
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();
    private int vao = 0, vbo = 0, ebo = 0;
    private Shader shader = null;
    private int uProj = -1, uView = -1, uModel = -1, uTex = -1, uUVOffset = -1, uTintColor = -1;
    private static final int MAX_PARTICLES = 2000;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec2 aUV;
        uniform mat4 uProj, uView, uModel;
        out vec2 vUV;
        void main() { vUV = aUV; gl_Position = uProj * uView * uModel * vec4(aPos,1.0); }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec2 vUV;
        uniform sampler2D uTex;
        uniform float uUVOffset;
        uniform vec3 uTintColor;
        out vec4 FragColor;
        void main() {
            vec4 c = texture(uTex, vUV + vec2(uUVOffset, 0.0));
            c.rgb *= uTintColor;
            FragColor = c;
        }
        """;

    private static final float[] CUBE = {
        -0.5f,-0.5f, 0.5f, 0f,1f,   0.5f,-0.5f, 0.5f, TextureAtlas.TILE_U,1f,   0.5f,0.5f, 0.5f, TextureAtlas.TILE_U,0f,  -0.5f,0.5f, 0.5f, 0f,0f,
         0.5f,-0.5f,-0.5f, 0f,1f,  -0.5f,-0.5f,-0.5f, TextureAtlas.TILE_U,1f,  -0.5f,0.5f,-0.5f, TextureAtlas.TILE_U,0f,   0.5f,0.5f,-0.5f, 0f,0f,
        -0.5f,0.5f, 0.5f, 0f,1f,   0.5f,0.5f, 0.5f, TextureAtlas.TILE_U,1f,   0.5f,0.5f,-0.5f, TextureAtlas.TILE_U,0f,  -0.5f,0.5f,-0.5f, 0f,0f,
        -0.5f,-0.5f,-0.5f, 0f,1f,   0.5f,-0.5f,-0.5f, TextureAtlas.TILE_U,1f,   0.5f,-0.5f, 0.5f, TextureAtlas.TILE_U,0f,  -0.5f,-0.5f, 0.5f, 0f,0f,
        -0.5f,-0.5f,-0.5f, 0f,1f,  -0.5f,-0.5f, 0.5f, TextureAtlas.TILE_U,1f,  -0.5f,0.5f, 0.5f, TextureAtlas.TILE_U,0f,  -0.5f,0.5f,-0.5f, 0f,0f,
         0.5f,-0.5f, 0.5f, 0f,1f,   0.5f,-0.5f,-0.5f, TextureAtlas.TILE_U,1f,   0.5f,0.5f,-0.5f, TextureAtlas.TILE_U,0f,   0.5f,0.5f, 0.5f, 0f,0f,
    };
    private static final int[] INDICES = {
        0,1,2, 2,3,0, 4,5,6, 6,7,4, 8,9,10, 10,11,8,
        12,13,14, 14,15,12, 16,17,18, 18,19,16, 20,21,22, 22,23,20
    };

    /** Ленивая инициализация — только в главном потоке (из render). */
    private void ensureInit() {
        if (shader != null) return;
        shader = new Shader(VERT, FRAG);
        uProj = shader.uniform("uProj");
        uView = shader.uniform("uView");
        uModel = shader.uniform("uModel");
        uTex = shader.uniform("uTex");
        uUVOffset = shader.uniform("uUVOffset");
        uTintColor = shader.uniform("uTintColor");
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        ebo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(CUBE.length);
            fb.put(CUBE).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
            IntBuffer ib = stack.mallocInt(INDICES.length);
            ib.put(INDICES).flip();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
        }
        int stride = 5 * 4;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3L*4L);
        GL20.glEnableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
    }

    public void spawnBreak(int bx, int by, int bz, BlockType type) {
        for (int i = 0; i < 12; i++) {
            if (particles.size() >= MAX_PARTICLES) break;
            float px = bx + 0.2f + rng.nextFloat() * 0.6f;
            float py = by + 0.2f + rng.nextFloat() * 0.6f;
            float pz = bz + 0.2f + rng.nextFloat() * 0.6f;
            Vector3f v = new Vector3f((rng.nextFloat() - 0.5f) * 3f, 1.5f + rng.nextFloat() * 3f, (rng.nextFloat() - 0.5f) * 3f);
            particles.add(new Particle(new Vector3f(px, py, pz), v, type, 0.8f + rng.nextFloat() * 0.6f, 0.12f + rng.nextFloat() * 0.08f));
        }
    }

    public void update(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) { particles.remove(i); continue; }
            p.vel.y -= 20f * dt;
            p.pos.add(new Vector3f(p.vel).mul(dt));
            if (p.pos.y < 0.1f) { p.pos.y = 0.1f; p.vel.y = 0; p.vel.x *= 0.6f; p.vel.z *= 0.6f; }
        }
    }

    public void render(Matrix4f proj, Matrix4f view, int atlasTex) {
        if (particles.isEmpty()) return;
        ensureInit();
        shader.bind();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
        GL20.glUniform1i(uTex, 0);
        GL30.glBindVertexArray(vao);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer pb = stack.mallocFloat(16);
            proj.get(pb); GL20.glUniformMatrix4fv(uProj, false, pb);
            view.get(pb); GL20.glUniformMatrix4fv(uView, false, pb);
        }
        for (Particle p : particles) {
            Matrix4f m = new Matrix4f().translate(p.pos.x, p.pos.y, p.pos.z).scale(p.size);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer pb = stack.mallocFloat(16);
                m.get(pb);
                GL20.glUniformMatrix4fv(uModel, false, pb);
            }
            GL20.glUniform1f(uUVOffset, p.type.tileSide * TextureAtlas.TILE_U);
            if (p.type == BlockType.GRASS) GL20.glUniform3f(uTintColor, 0.35f, 0.85f, 0.30f);
            else if (p.type == BlockType.LEAVES) GL20.glUniform3f(uTintColor, 0.25f, 0.65f, 0.20f);
            else GL20.glUniform3f(uTintColor, 1f, 1f, 1f);
            GL11.glDrawElements(GL11.GL_TRIANGLES, INDICES.length, GL11.GL_UNSIGNED_INT, 0);
        }
        GL30.glBindVertexArray(0);
    }

    public int count() { return particles.size(); }
}
