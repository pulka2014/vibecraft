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

public class ItemDropSystem {
    private int itemAtlasTex = 0;
    public void setItemAtlas(int tex) { this.itemAtlasTex = tex; }
    private final List<DroppedItem> items = new ArrayList<>();
    private final Random rng = new Random();
    private final int vao, vbo, ebo;
    private final Shader shader;
    private final int uProj, uView, uModel, uTex, uUVOffset, uTintColor;

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

    // маленький куб 0.25, UV: u = 0..TILE_U (один тайл), v = 0..1 (весь тайл по высоте)
    private static final float[] CUBE = {
        // front
        -0.125f,-0.125f, 0.125f, 0f,1f,   0.125f,-0.125f, 0.125f, TextureAtlas.TILE_U,1f,   0.125f,0.125f, 0.125f, TextureAtlas.TILE_U,0f,  -0.125f,0.125f, 0.125f, 0f,0f,
        // back
         0.125f,-0.125f,-0.125f, 0f,1f,  -0.125f,-0.125f,-0.125f, TextureAtlas.TILE_U,1f,  -0.125f,0.125f,-0.125f, TextureAtlas.TILE_U,0f,   0.125f,0.125f,-0.125f, 0f,0f,
        // top
        -0.125f,0.125f, 0.125f, 0f,1f,   0.125f,0.125f, 0.125f, TextureAtlas.TILE_U,1f,   0.125f,0.125f,-0.125f, TextureAtlas.TILE_U,0f,  -0.125f,0.125f,-0.125f, 0f,0f,
        // bottom
        -0.125f,-0.125f,-0.125f, 0f,1f,   0.125f,-0.125f,-0.125f, TextureAtlas.TILE_U,1f,   0.125f,-0.125f, 0.125f, TextureAtlas.TILE_U,0f,  -0.125f,-0.125f, 0.125f, 0f,0f,
        // left
        -0.125f,-0.125f,-0.125f, 0f,1f,  -0.125f,-0.125f, 0.125f, TextureAtlas.TILE_U,1f,  -0.125f,0.125f, 0.125f, TextureAtlas.TILE_U,0f,  -0.125f,0.125f,-0.125f, 0f,0f,
        // right
         0.125f,-0.125f, 0.125f, 0f,1f,   0.125f,-0.125f,-0.125f, TextureAtlas.TILE_U,1f,   0.125f,0.125f,-0.125f, TextureAtlas.TILE_U,0f,   0.125f,0.125f, 0.125f, 0f,0f,
    };
    private static final int[] INDICES = {
        0,1,2, 2,3,0, 4,5,6, 6,7,4, 8,9,10, 10,11,8,
        12,13,14, 14,15,12, 16,17,18, 18,19,16, 20,21,22, 22,23,20
    };

    public ItemDropSystem() {
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

    public void render(Matrix4f proj, Matrix4f view, int atlasTex) {
        if (items.isEmpty()) return;
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
        for (DroppedItem it : items) {
            Matrix4f m = new Matrix4f()
                .translate(it.pos.x, it.pos.y, it.pos.z)
                .rotateY(it.age * 2f)
                .rotateX(45f * (float)Math.PI/180f)
                .rotateZ(45f * (float)Math.PI/180f);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer pb = stack.mallocFloat(16);
                m.get(pb);
                GL20.glUniformMatrix4fv(uModel, false, pb);
            }
            if (it.item != null) {
                // 2D иконка: UV задаём напрямую (0..TILE_U), без uUVOffset
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
                GL20.glUniform1f(uUVOffset, it.item.id * ItemAtlas.TILE_U);
                float s = 0.25f;
                float[] verts = {
                    -s, -s, 0, 0f, 1f,
                     s, -s, 0, ItemAtlas.TILE_U, 1f,
                     s,  s, 0, ItemAtlas.TILE_U, 0f,
                    -s,  s, 0, 0f, 0f,
                };
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer fb = stack.mallocFloat(verts.length);
                    fb.put(verts).flip();
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
                }
                GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
            } else {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
                GL20.glUniform1f(uUVOffset, it.type.tileSide * TextureAtlas.TILE_U);
                GL20.glUniform1f(uUVOffset, 0f); // блок: UV уже в CUBE 0..TILE_U, без сдвига
                if (it.type == BlockType.GRASS) GL20.glUniform3f(uTintColor, 0.35f, 0.85f, 0.30f);
                else if (it.type == BlockType.LEAVES) GL20.glUniform3f(uTintColor, 0.25f, 0.65f, 0.20f);
                else GL20.glUniform3f(uTintColor, 1f, 1f, 1f);
                GL11.glDrawElements(GL11.GL_TRIANGLES, INDICES.length, GL11.GL_UNSIGNED_INT, 0);
            }
        }
        GL30.glBindVertexArray(0);
    }

    public int count() { return items.size(); }
}
