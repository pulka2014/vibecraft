package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

public class Hotbar {
    private int selected = 0;
    private final Inventory inventory;
    private final int atlasTex, itemAtlasTex;
    private final Shader shader;
    private final FontRenderer font;
    private int vao, vbo;

    public static final float SLOT = 0.09f;
    public static final float GAP  = 0.006f;
    public static final float BOTTOM = -0.97f;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec2 aPos;
        layout(location=1) in vec2 aUV;
        layout(location=2) in vec4 aColor;
        out vec2 vUV;
        out vec4 vColor;
        void main() { vUV = aUV; vColor = aColor; gl_Position = vec4(aPos, 0.0, 1.0); }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec2 vUV;
        in vec4 vColor;
        uniform sampler2D uTex;
        uniform int uUseTex;
        out vec4 FragColor;
        void main() {
            if (uUseTex == 1) FragColor = texture(uTex, vUV) * vColor;
            else FragColor = vColor;
        }
        """;

    public Hotbar(int atlasTex, int itemAtlasTex, Inventory inventory, FontRenderer font) {
        this.atlasTex = atlasTex;
        this.itemAtlasTex = itemAtlasTex;
        this.inventory = inventory;
        this.font = font;
        shader = new Shader(VERT, FRAG);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
    }

    public void select(int i) { if (i >= 0 && i < Inventory.HOTBAR) selected = i; }
    public int selectedIndex() { return selected; }
    public Inventory.Slot selectedSlot() {
        Inventory.Slot s = inventory.slot(selected);
        return (s != null && !s.isEmpty()) ? s : null;
    }
    public BlockType selectedBlock() {
        Inventory.Slot s = selectedSlot();
        return (s != null && s.isBlock()) ? s.block : null;
    }
    public void consumeSelected() {
        Inventory.Slot s = inventory.slot(selected);
        if (s != null && !s.isEmpty()) { s.count--; if (s.count <= 0) s.clear(); }
    }

    private void drawQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        float[] v = { x0,y0,u0,v0,r,g,b,a, x1,y0,u1,v0,r,g,b,a, x1,y1,u1,v1,r,g,b,a, x0,y1,u0,v1,r,g,b,a };
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(v.length);
            fb.put(v).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
    }

    public void render() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        int stride = 8 * 4;
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L*4L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, stride, 4L*4L);
        GL20.glEnableVertexAttribArray(2);

        float totalW = Inventory.HOTBAR * SLOT + (Inventory.HOTBAR - 1) * GAP;
        float startX = -totalW / 2f;
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            float x0 = startX + i * (SLOT + GAP);
            float x1 = x0 + SLOT;
            float y0 = BOTTOM, y1 = BOTTOM + SLOT;
            GL20.glUniform1i(shader.uniform("uUseTex"), 0);
            drawQuad(x0, y0, x1, y1, 0,0,0,0, 0.15f,0.15f,0.15f,0.85f);
            float r = (i == selected) ? 1f : 0.35f;
            float t = 0.008f;
            drawQuad(x0, y0, x1, y0+t, 0,0,0,0, r,r,r,1);
            drawQuad(x0, y1-t, x1, y1, 0,0,0,0, r,r,r,1);
            drawQuad(x0, y0, x0+t, y1, 0,0,0,0, r,r,r,1);
            drawQuad(x1-t, y0, x1, y1, 0,0,0,0, r,r,r,1);

            Inventory.Slot s = inventory.slot(i);
            if (s == null || s.isEmpty()) continue;
            float pad = SLOT * 0.15f;
            if (s.isBlock()) {
                int tile = s.block.tileTop;
                float tu = TextureAtlas.TILE_U;
                float u0 = tile * tu, u1 = u0 + tu;
                float tr = 1f, tg = 1f, tb = 1f;
                if (s.block == BlockType.GRASS) { tr=0.35f; tg=0.85f; tb=0.30f; }
                else if (s.block == BlockType.LEAVES) { tr=0.25f; tg=0.65f; tb=0.20f; }
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
                drawQuad(x0+pad, y0+pad, x1-pad, y1-pad, u0, 1f, u1, 0f, tr,tg,tb,1f);
            } else if (s.isItem()) {
                float tu = ItemAtlas.TILE_U;
                float u0 = s.item.id * tu, u1 = u0 + tu;
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
                drawQuad(x0+pad, y0+pad, x1-pad, y1-pad, u0, 1f, u1, 0f, 1f,1f,1f,1f);
            }
        }
        GL30.glBindVertexArray(0);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        for (int i = 0; i < Inventory.HOTBAR; i++) {
            Inventory.Slot s = inventory.slot(i);
            if (s == null || s.isEmpty()) continue;
            float x0 = startX + i * (SLOT + GAP);
            float y0 = BOTTOM;
            float charH = SLOT * 0.45f;
            String txt = String.valueOf(s.count);
            float w = font.width(txt, charH);
            font.drawString(txt, x0 + SLOT - w - 0.008f, y0 + charH + 0.004f, charH);
        }
    }
}
