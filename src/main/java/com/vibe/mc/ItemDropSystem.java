package com.vibe.mc;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemDropSystem {
    private int itemAtlasTex = 0;
    public void setItemAtlas(int tex) { this.itemAtlasTex = tex; }
    private final List<DroppedItem> items = new ArrayList<>();
    private final Random rng = new Random();
    private int vao = 0, vbo = 0;
    private Shader shader;
    private int uProj, uView, uModel, uTex, uUseAtlas;

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
        uniform int uUseAtlas;
        out vec4 FragColor;
        void main() {
            vec4 c = texture(uTex, vUV);
            if (uUseAtlas == 1) c.rgb *= vec3(0.35, 0.85, 0.30); // травяной дроп
            FragColor = c;
        }
        """;

    private void ensureInit() {
        if (shader != null) return;
        shader = new Shader(VERT, FRAG);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
    }

    public ItemDropSystem() {
        shader = new Shader(VERT, FRAG);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
    }

    public void spawnItem(int bx, int by, int bz, ItemType item) {
        Vector3f p = new Vector3f(bx + 0.5f, by + 0.5f, bz + 0.5f);
        Vector3f v = new Vector3f((rng.nextFloat() - 0.5f) * 1.5f, 2.5f, (rng.nextFloat() - 0.5f) * 1.5f);
        items.add(new DroppedItem(p, v, item));
    }
    public void spawn(int bx, int by, int bz, BlockType type) {
        Vector3f p = new Vector3f(bx + 0.5f, by + 0.5f, bz + 0.5f);
        Vector3f v = new Vector3f((rng.nextFloat() - 0.5f) * 1.5f, 2.5f, (rng.nextFloat() - 0.5f) * 1.5f);
        items.add(new DroppedItem(p, v, type));
    }

    public void update(float dt, World world, Inventory inv, Vector3f playerFeet) {
        Vector3f playerCenter = new Vector3f(playerFeet).add(0f, 0.9f, 0f);
        for (int i = items.size() - 1; i >= 0; i--) {
            DroppedItem it = items.get(i);
            it.age += dt;
            it.vel.y -= 20f * dt;
            Vector3f next = new Vector3f(it.pos).add(new Vector3f(it.vel).mul(dt));
            int bx = (int) Math.floor(next.x);
            int by = (int) Math.floor(next.y - 0.125f);
            int bz = (int) Math.floor(next.z);
            if (world.isSolid(bx, by, bz)) {
                next.y = by + 1 + 0.125f;
                it.vel.y = 0;
                it.vel.x *= 0.7f;
                it.vel.z *= 0.7f;
                it.onGround = true;
            }
            it.pos.set(next);
            if (it.pos.distanceSquared(playerCenter) < 2.25f && it.age > 0.3f) {
                Vector3f toPlayer = new Vector3f(playerCenter).sub(it.pos).normalize().mul(6f);
                it.vel.set(toPlayer);
                if (it.pos.distanceSquared(playerCenter) < 0.3f) {
                    if (it.item != null) inv.addItem(it.item, 1); else inv.addBlock(it.type, 1);
                    items.remove(i);
                }
            }
        }
    }

    /** Записывает куб блока или квад иконки предмета с UV, вписанным в нужный тайл. */
    private void drawBlockCube(BlockType type, float size) {
        int tile = type.tileSide;
        float tu = TextureAtlas.TILE_U;
        float u0 = tile * tu, u1 = u0 + tu;
        float s = size * 0.5f;

        // 6 граней * 4 вершины * (3 pos + 2 uv) = 6*4*5 = 120 float
        float[] v = new float[6 * 4 * 5];
        // Порядок: +X, -X, +Y, -Y, +Z, -Z. Каждая грань — 4 вершины по 3 координаты.
        int k = 0;
        float[][][] faces = {
            {{s,-s, s},{s,-s,-s},{s, s,-s},{s, s, s}},     // +X
            {{-s,-s,-s},{-s,-s, s},{-s, s, s},{-s, s,-s}}, // -X
            {{-s, s, s},{s, s, s},{s, s,-s},{-s, s,-s}},   // +Y
            {{-s,-s,-s},{s,-s,-s},{s,-s, s},{-s,-s, s}},   // -Y
            {{-s,-s, s},{s,-s, s},{s, s, s},{-s, s, s}},   // +Z
            {{s,-s,-s},{-s,-s,-s},{-s, s,-s},{s, s,-s}}    // -Z
        };
        for (float[][] face : faces) {
            v[k++] = face[0][0]; v[k++] = face[0][1]; v[k++] = face[0][2]; v[k++] = u0; v[k++] = 1f;
            v[k++] = face[1][0]; v[k++] = face[1][1]; v[k++] = face[1][2]; v[k++] = u1; v[k++] = 1f;
            v[k++] = face[2][0]; v[k++] = face[2][1]; v[k++] = face[2][2]; v[k++] = u1; v[k++] = 0f;
            v[k++] = face[3][0]; v[k++] = face[3][1]; v[k++] = face[3][2]; v[k++] = u0; v[k++] = 0f;
        }
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = MemoryUtil.memAllocFloat(v.length);
        fb.put(v).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(fb);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5*4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5*4, 3L*4L);
        GL20.glEnableVertexAttribArray(1);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 4, 4);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 8, 4);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 12, 4);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 16, 4);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 20, 4);
        GL30.glBindVertexArray(0);
    }

    private void drawItemQuad(ItemType item, float size) {
        float tu = ItemAtlas.TILE_U;
        float u0 = item.id * tu, u1 = u0 + tu;
        float s = size * 0.5f;
        float[] v = {
            -s,-s,0, u0, 1f,
             s,-s,0, u1, 1f,
             s, s,0, u1, 0f,
            -s, s,0, u0, 0f,
        };
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = MemoryUtil.memAllocFloat(v.length);
        fb.put(v).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(fb);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5*4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5*4, 3L*4L);
        GL20.glEnableVertexAttribArray(1);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
        GL30.glBindVertexArray(0);
    }

    public void render(Matrix4f proj, Matrix4f view, int atlasTex) {
        if (items.isEmpty()) return;
        ensureInit();
        shader.bind();
        GL20.glUniform1i(uTex, 0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer pb = stack.mallocFloat(16);
            proj.get(pb); GL20.glUniformMatrix4fv(uProj, false, pb);
            view.get(pb); GL20.glUniformMatrix4fv(uView, false, pb);
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        for (DroppedItem it : items) {
            Matrix4f m = new Matrix4f()
                .translate(it.pos.x, it.pos.y, it.pos.z)
                .rotateY(it.age * 2f);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer pb = stack.mallocFloat(16);
                m.get(pb);
                GL20.glUniformMatrix4fv(uModel, false, pb);
            }
            if (it.item != null) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
                GL20.glUniform1i(uUseAtlas, 0);
                drawItemQuad(it.item, 0.5f);
            } else {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
                boolean tint = (it.type == BlockType.GRASS || it.type == BlockType.LEAVES);
                GL20.glUniform1i(uUseAtlas, tint ? 1 : 0);
                drawBlockCube(it.type, 0.25f);
            }
        }
        GL11.glDisable(GL11.GL_BLEND);
    }

    public int count() { return items.size(); }
}
