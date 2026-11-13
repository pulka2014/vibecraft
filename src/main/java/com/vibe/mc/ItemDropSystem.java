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
    private int vao, vbo;
    private Shader shader = null;
    private int uProj = -1, uView = -1, uModel = -1, uTex = -1, uTint = -1;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec2 aUV;
        layout(location=2) in float aTint;
        uniform mat4 uProj, uView, uModel;
        out vec2 vUV;
        out float vTint;
        void main() { vUV = aUV; vTint = aTint; gl_Position = uProj * uView * uModel * vec4(aPos,1.0); }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec2 vUV;
        in float vTint;
        uniform sampler2D uTex;
        uniform vec3 uTintColor;
        out vec4 FragColor;
        void main() {
            vec4 c = texture(uTex, vUV);
            if (vTint > 0.5) c.rgb *= uTintColor;
            FragColor = c;
        }
        """;

    private void ensureInit() {
        if (shader != null) return;
        shader = new Shader(VERT, FRAG);
        uProj = shader.uniform("uProj");
        uView = shader.uniform("uView");
        uModel = shader.uniform("uModel");
        uTex = shader.uniform("uTex");
        uTint = shader.uniform("uTintColor");
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
    }

    // === Старые методы ===
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

    // === Новые методы для Q (выбрасывания) ===
    public void spawnWithVelocity(float x, float y, float z, BlockType type, float vx, float vy, float vz) {
        items.add(new DroppedItem(new Vector3f(x, y, z), new Vector3f(vx, vy, vz), type));
    }
    public void spawnItemWithVelocity(float x, float y, float z, ItemType item, float vx, float vy, float vz) {
        items.add(new DroppedItem(new Vector3f(x, y, z), new Vector3f(vx, vy, vz), item));
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
            // Подбираем только если дроп «полежал» 0.5 сек и игрок рядом
            if (it.age > 0.5f && it.pos.distanceSquared(playerCenter) < 2.25f) {
                Vector3f toPlayer = new Vector3f(playerCenter).sub(it.pos).normalize().mul(6f);
                it.vel.set(toPlayer);
                if (it.pos.distanceSquared(playerCenter) < 0.3f) {
                    if (it.item != null) inv.addItem(it.item, 1); else inv.addBlock(it.type, 1);
                    items.remove(i);
                }
            }
        }
    }

    private void drawBlockCube(BlockType type, float size) {
        float s = size * 0.5f;
        float tu = TextureAtlas.TILE_U;

        // Определяем тайлы и tint
        int topTile, sideTile, bottomTile, frontTile;
        float[] tints = new float[6]; // tint для каждой грани (+X, -X, +Y, -Y, +Z, -Z)

        if (type == BlockType.GRASS) {
            topTile = type.tileTop;    // серая маска
            sideTile = type.tileSide;
            bottomTile = type.tileTop;
            frontTile = type.tileSide;
            tints[2] = 1f; // +Y (верх)
            tints[3] = 1f; // -Y (низ)
        } else if (type == BlockType.LEAVES) {
            topTile = type.tileSide;
            sideTile = type.tileSide;
            bottomTile = type.tileSide;
            frontTile = type.tileSide;
            // tint для листьев (все грани)
            tints[0] = tints[1] = tints[2] = tints[3] = tints[4] = tints[5] = 1f;
        } else {
            topTile = type.tileSide;
            sideTile = type.tileSide;
            bottomTile = type.tileSide;
            frontTile = type.tileSide;
        }

        // 6 граней: +X, -X, +Y, -Y, +Z, -Z
        int[] faceTiles = {sideTile, sideTile, topTile, bottomTile, frontTile, sideTile};
        float[][][] faces = {
            {{s,-s, s},{s,-s,-s},{s, s,-s},{s, s, s}},     // +X
            {{-s,-s,-s},{-s,-s, s},{-s, s, s},{-s, s,-s}}, // -X
            {{-s, s, s},{s, s, s},{s, s,-s},{-s, s,-s}},   // +Y
            {{-s,-s,-s},{s,-s,-s},{s,-s, s},{-s,-s, s}},   // -Y
            {{-s,-s, s},{s,-s, s},{s, s, s},{-s, s, s}},   // +Z
            {{s,-s,-s},{-s,-s,-s},{-s, s,-s},{s, s,-s}}    // -Z
        };
        float[][] uv = {{0,1},{1,1},{1,0},{0,0}};

        float[] v = new float[6 * 4 * 6]; // pos(3) + uv(2) + tint(1)
        int k = 0;
        for (int f = 0; f < 6; f++) {
            int tile = faceTiles[f];
            float u0 = tile * tu, u1 = u0 + tu;
            float tint = tints[f];
            for (int i = 0; i < 4; i++) {
                v[k++] = faces[f][i][0];
                v[k++] = faces[f][i][1];
                v[k++] = faces[f][i][2];
                v[k++] = u0 + (u1 - u0) * uv[i][0];
                v[k++] = uv[i][1];
                v[k++] = tint;
            }
        }

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = org.lwjgl.system.MemoryUtil.memAllocFloat(v.length);
        fb.put(v).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        org.lwjgl.system.MemoryUtil.memFree(fb);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 6*4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 6*4, 3L*4L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, 6*4, 5L*4L);
        GL20.glEnableVertexAttribArray(2);
        for (int i = 0; i < 6; i++) GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, i*4, 4);
        GL30.glBindVertexArray(0);
    }

    private void drawItemQuad(ItemType item, float size) {
        float tu = ItemAtlas.TILE_U;
        float u0 = item.id * tu, u1 = u0 + tu;
        float s = size * 0.5f;
        float[] v = {
            -s,-s,0, u0, 1f, 0f,
             s,-s,0, u1, 1f, 0f,
             s, s,0, u1, 0f, 0f,
            -s, s,0, u0, 0f, 0f,
        };
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = MemoryUtil.memAllocFloat(v.length);
        fb.put(v).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(fb);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 6*4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 6*4, 3L*4L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, 6*4, 5L*4L);
        GL20.glEnableVertexAttribArray(2);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
        GL30.glBindVertexArray(0);
    }

    public void render(Matrix4f proj, Matrix4f view, int atlasTex) {
        if (items.isEmpty()) return;
        ensureInit();
        shader.bind();
        GL20.glUniform1i(uTex, 0);
        GL20.glUniform3f(uTint, 0.4f, 0.9f, 0.3f); // мягкий зелёный
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
                drawItemQuad(it.item, 0.5f);
            } else {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
                drawBlockCube(it.type, 0.25f);
            }
        }
        GL11.glDisable(GL11.GL_BLEND);
    }

    public int count() { return items.size(); }
}
