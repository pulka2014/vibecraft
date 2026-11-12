package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

/** Печь: top=grid[0], fuel=grid[1], result=grid[2]. Топливо не тратится без сырья. */
public class FurnaceScreen {
    private final Inventory inventory;
    private final int atlasTex, itemAtlasTex;
    private final FontRenderer font;
    private final Shader shader;
    private int vao, vbo;
    private boolean open = false;
    private float mouseX = 0, mouseY = 0;
    private Inventory.Slot held = null;
    public final Inventory.Slot[] grid = new Inventory.Slot[3];
    private double burnTime = 0, maxBurnTime = 1;
    private float smeltProgress = 0;

    public static final float SLOT = 0.08f;
    public static final float GAP = 0.005f;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec2 aPos;
        layout(location=1) in vec2 aUV;
        layout(location=2) in vec4 aColor;
        out vec2 vUV; out vec4 vColor;
        void main() { vUV = aUV; vColor = aColor; gl_Position = vec4(aPos, 0.0, 1.0); }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec2 vUV; in vec4 vColor;
        uniform sampler2D uTex;
        uniform int uUseTex;
        out vec4 FragColor;
        void main() {
            if (uUseTex == 1) FragColor = texture(uTex, vUV) * vColor;
            else FragColor = vColor;
        }
        """;

    public FurnaceScreen(int atlasTex, int itemAtlasTex, Inventory inv, FontRenderer font) {
        this.atlasTex = atlasTex; this.itemAtlasTex = itemAtlasTex;
        this.inventory = inv; this.font = font;
        for (int i = 0; i < 3; i++) grid[i] = new Inventory.Slot();
        shader = new Shader(VERT, FRAG);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
    }
    public boolean isOpen() { return open; }
    public void open() { open = true; }
    public void close() {
        open = false;
        // held возвращаем, а grid ОСТАЁТСЯ — уголь/сырьё не выпадают
        if (held != null && !held.isEmpty()) {
            if (held.isBlock()) inventory.addBlock(held.block, held.count); else inventory.addItem(held.item, held.count);
            held = null;
        }
    }
    public void setMouse(float nx, float ny) { mouseX = nx; mouseY = ny; }

    // === ТОЧНАЯ КОПИЯ InventoryScreen.slotRect ===
    private float[] invSlotRect(int i) {
        float totalW = 9 * SLOT + 8 * GAP;
        float startX = -totalW / 2f;
        int col, row;
        if (i < Inventory.HOTBAR) { col = i; row = 3; }
        else { int m = i - Inventory.HOTBAR; col = m % 9; row = 2 - (m / 9); }
        float x0 = startX + col * (SLOT + GAP);
        float y0 = -0.1f + row * (SLOT + GAP) + (row == 3 ? -0.05f : 0);
        if (i < Inventory.HOTBAR) {
            y0 = -0.9f;
        } else {
            int m = i - Inventory.HOTBAR;
            int c = m % 9, r = m / 9;
            x0 = startX + c * (SLOT + GAP);
            y0 = -0.15f - r * (SLOT + GAP);
        }
        return new float[]{x0, y0, x0 + SLOT, y0 + SLOT};
    }

    private float[] slotRect(int i) {
        if (i == 0) return new float[]{-0.30f, 0.30f, -0.22f, 0.38f};  // top
        if (i == 1) return new float[]{-0.30f, 0.15f, -0.22f, 0.23f};  // fuel
        return new float[]{-0.10f, 0.22f, -0.02f, 0.30f};              // result
    }

    // === updateFurnace: топливо только если есть сырьё ===
    public boolean updateFurnace() {
        Inventory.Slot mat = grid[0];
        Inventory.Slot fuel = grid[1];
        if (mat.isEmpty() || !isSmeltable(mat)) return false;
        if (burnTime <= 0) {
            if (fuel.isEmpty() || !isFuel(fuel)) return false;
            maxBurnTime = fuelFor(fuel);
            burnTime = maxBurnTime;
            fuel.count--; if (fuel.count <= 0) fuel.clear();
        }
        burnTime -= 0.05;
        smeltProgress += 0.02f;
        if (smeltProgress >= 1f) {
            smeltProgress = 0;
            Inventory.Slot res = grid[2];
            ItemType resultItem = null;
            BlockType resultBlock = null;
            if (mat.isItem() && mat.item == ItemType.RAW_IRON) resultItem = ItemType.IRON_INGOT;
            else if (mat.isItem() && mat.item == ItemType.WHEAT) resultItem = ItemType.BREAD;
            else if (mat.isBlock() && mat.block == BlockType.SAND) resultBlock = BlockType.GLASS;
            else if (mat.isBlock() && mat.block == BlockType.WOOD) resultItem = ItemType.COAL;

            if (resultItem != null) {
                if (res.isEmpty()) { res.item = resultItem; res.count = 1; }
                else if (res.isItem() && res.item == resultItem && res.count < Inventory.MAX_STACK) res.count++;
                else return false;
            } else if (resultBlock != null) {
                if (res.isEmpty()) { res.block = resultBlock; res.count = 1; }
                else if (res.isBlock() && res.block == resultBlock && res.count < Inventory.MAX_STACK) res.count++;
                else return false;
            } else return false;
            
            mat.count--; if (mat.count <= 0) mat.clear();
            return true;
        }
        return false;
    }

    private boolean isFuel(Inventory.Slot s) {
        if (s.isBlock() && (s.block == BlockType.WOOD || s.block == BlockType.PLANKS)) return true;
        if (s.isItem() && s.item == ItemType.COAL) return true;
        return false;
    }
    private float fuelFor(Inventory.Slot s) {
        if (s.isItem() && s.item == ItemType.COAL) return 8f;
        if (s.isBlock() && s.block == BlockType.WOOD) return 4f;
        if (s.isBlock() && s.block == BlockType.PLANKS) return 1.5f;
        return 0f;
    }
    private boolean isSmeltable(Inventory.Slot s) {
        if (s.isItem()) {
            return s.item == ItemType.RAW_IRON || s.item == ItemType.WHEAT;
        }
        if (s.isBlock()) {
            return s.block == BlockType.SAND || s.block == BlockType.WOOD;
        }
        return false;
    }

    public void click(boolean right) {
        for (int i = 0; i < 3; i++) {
            float[] r = slotRect(i);
            if (inside(r)) { clickSlot(grid[i], right); return; }
        }
        for (int i = 0; i < Inventory.TOTAL; i++) {
            float[] r = invSlotRect(i);
            if (inside(r)) { clickSlot(inventory.slot(i), right); return; }
        }
    }
    private void clickSlot(Inventory.Slot s, boolean right) {
        if (held == null) {
            if (s.isEmpty()) return;
            if (right) {
                int take = (s.count + 1) / 2;
                held = s.isBlock() ? new Inventory.Slot(s.block, take) : new Inventory.Slot(s.item, take);
                s.count -= take; if (s.count <= 0) s.clear();
            } else { held = s.copy(); s.clear(); }
        } else {
            if (right) {
                if (s.isEmpty()) { if (held.isBlock()) s.block = held.block; else s.item = held.item; s.count = 1; }
                else if (s.isBlock() && held.isBlock() && s.block == held.block) s.count++;
                else if (s.isItem() && held.isItem() && s.item == held.item) s.count++;
                else return;
                held.count--; if (held.count <= 0) held = null;
            } else {
                if (s.isEmpty()) { s.block = held.block; s.item = held.item; s.count = held.count; held = null; }
                else if (s.isBlock() && held.isBlock() && s.block == held.block) {
                    int can = Math.min(Inventory.MAX_STACK - s.count, held.count);
                    s.count += can; held.count -= can; if (held.count <= 0) held = null;
                } else if (s.isItem() && held.isItem() && s.item == held.item) {
                    int can = Math.min(Inventory.MAX_STACK - s.count, held.count);
                    s.count += can; held.count -= can; if (held.count <= 0) held = null;
                }
            }
        }
    }
    private boolean inside(float[] r) { return mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3]; }

    private void drawQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        float[] v = { x0,y0,u0,v0,r,g,b,a, x1,y0,u1,v0,r,g,b,a, x1,y1,u1,v1,r,g,b,a, x0,y1,u0,v1,r,g,b,a };
        try (MemoryStack st = MemoryStack.stackPush()) {
            FloatBuffer fb = st.mallocFloat(v.length); fb.put(v).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
    }

    private void drawSlotBg(float[] r) {
        GL20.glUniform1i(shader.uniform("uUseTex"), 0);
        drawQuad(r[0], r[1], r[2], r[3], 0,0,0,0, 0.2f,0.2f,0.2f,0.9f);
        drawQuad(r[0], r[1], r[2], r[1]+0.006f, 0,0,0,0, 0.5f,0.5f,0.5f,1);
        drawQuad(r[0], r[3]-0.006f, r[2], r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
        drawQuad(r[0], r[1], r[0]+0.006f, r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
        drawQuad(r[2]-0.006f, r[1], r[2], r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
    }

    private void drawIcon(Inventory.Slot s, float[] r) {
        if (s == null || s.isEmpty()) return;
        float pad = SLOT * 0.15f;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (s.isBlock()) {
            int tile = s.block.tileTop;
            float tu = TextureAtlas.TILE_U;
            GL20.glUniform1i(shader.uniform("uUseTex"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
            drawQuad(r[0]+pad, r[1]+pad, r[2]-pad, r[3]-pad, tile*tu, 1f, (tile+1)*tu, 0f, 1,1,1,1);
        } else if (s.isItem()) {
            float tu = ItemAtlas.TILE_U;
            GL20.glUniform1i(shader.uniform("uUseTex"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
            drawQuad(r[0]+pad, r[1]+pad, r[2]-pad, r[3]-pad, s.item.id*tu, 1f, (s.item.id+1)*tu, 0f, 1,1,1,1);
        }
        GL11.glDisable(GL11.GL_BLEND);
    }

    public void render() {
        if (!open) return;
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

        GL20.glUniform1i(shader.uniform("uUseTex"), 0);
        drawQuad(-1, -1, 1, 1, 0,0,0,0, 0f,0f,0f,0.5f);

        // 3 слота печи
        for (int i = 0; i < 3; i++) {
            float[] r = slotRect(i);
            drawSlotBg(r);
            drawIcon(grid[i], r);
        }

        // 27 + 9 как в InventoryScreen
        for (int i = 0; i < Inventory.TOTAL; i++) {
            float[] r = invSlotRect(i);
            drawSlotBg(r);
            drawIcon(inventory.slot(i), r);
        }

        // held
        if (held != null && !held.isEmpty()) {
            float sz = SLOT * 0.7f;
            Inventory.Slot tmp = new Inventory.Slot();
            tmp.block = held.block; tmp.item = held.item; tmp.count = held.count;
            float[] r = new float[]{mouseX-sz/2, mouseY-sz/2, mouseX+sz/2, mouseY+sz/2};
            drawIcon(tmp, r);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);

        // числа
        for (int i = 0; i < 3; i++) if (!grid[i].isEmpty()) {
            float[] r = slotRect(i);
            float ch = SLOT * 0.45f;
            String txt = String.valueOf(grid[i].count);
            font.drawString(txt, r[2] - font.width(txt, ch) - 0.008f, r[1] + ch + 0.004f, ch);
        }
        for (int i = 0; i < Inventory.TOTAL; i++) {
            Inventory.Slot s = inventory.slot(i);
            if (s.isEmpty()) continue;
            float[] r = invSlotRect(i);
            float ch = SLOT * 0.45f;
            String txt = String.valueOf(s.count);
            font.drawString(txt, r[2] - font.width(txt, ch) - 0.008f, r[1] + ch + 0.004f, ch);
        }
    }
}
