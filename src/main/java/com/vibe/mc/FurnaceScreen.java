package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

/** Печь 3 слота: 0=материал, 1=топливо, 2=результат. */
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

    public static final float SLOT = 0.08f;
    public static final float GAP = 0.005f;
    private double burnTime = 0, maxBurnTime = 1;
    private float smeltProgress = 0;
    private BlockType activeFuel = null;

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
    public void open() { open = true; System.out.println("[Furnace] open"); }
    public void close() {
        open = false;
        for (Inventory.Slot s : grid) if (!s.isEmpty()) {
            if (s.isBlock()) inventory.addBlock(s.block, s.count); else inventory.addItem(s.item, s.count);
            s.clear();
        }
        if (held != null && !held.isEmpty()) {
            if (held.isBlock()) inventory.addBlock(held.block, held.count); else inventory.addItem(held.item, held.count);
            held = null;
        }
    }
    public void setMouse(float nx, float ny) { mouseX = nx; mouseY = ny; }

    private float[] slotRect(int i) {
        // 3 слота: top (вход), bottom (топливо), right (результат)
        if (i == 0) return new float[]{-0.30f, 0.30f, -0.22f, 0.38f};
        if (i == 1) return new float[]{-0.30f, 0.15f, -0.22f, 0.23f};
        return new float[]{-0.10f, 0.22f, -0.02f, 0.30f};
    }

    /** Плавка: раз в ~10 секунд. Возвращает true, если что-то переплавлено. */
    public boolean updateFurnace() {
        Inventory.Slot fuel = grid[1];
        Inventory.Slot mat = grid[0];
        // Если нет сырья — топливо не тратим
        if (mat.isEmpty() || !smeltable(mat, grid[2])) {
            return false;
        }
        if (burnTime <= 0) {
            if (!fuel.isEmpty() && isFuel(fuel)) {
                maxBurnTime = fuelForSlot(fuel);
                burnTime = maxBurnTime;
                fuel.count--; if (fuel.count <= 0) fuel.clear();
            } else {
                return false; // нет топлива — не плавим
            }
        }
        if (burnTime > 0) burnTime -= 0.05;
        Inventory.Slot res = grid[2];
        smeltProgress += 0.02f;
        if (smeltProgress >= 1f) {
            smeltProgress = 0;
            if (res.isEmpty()) { res.item = ItemType.IRON_INGOT; res.count = 1; }
            else if (res.isItem() && res.item == ItemType.IRON_INGOT && res.count < Inventory.MAX_STACK) res.count++;
            else return false;
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
    private float fuelForSlot(Inventory.Slot s) {
        if (s.isItem() && s.item == ItemType.COAL) return 8f;
        if (s.isBlock() && s.block == BlockType.WOOD) return 4f;
        if (s.isBlock() && s.block == BlockType.PLANKS) return 1.5f;
        return 0f;
    }
    private boolean smeltable(Inventory.Slot mat, Inventory.Slot res) {
        return mat.isItem() && mat.item == ItemType.RAW_IRON
            && (res.isEmpty() || (res.isItem() && res.item == ItemType.IRON_INGOT));
    }

    public void click(boolean right) {
        System.out.println("[Furnace.click] right=" + right + " x=" + mouseX + " y=" + mouseY);
        for (int i = 0; i < 3; i++) {
            float[] r = slotRect(i);
            if (mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3]) {
                if (held == null) {
                    if (!grid[i].isEmpty()) { held = grid[i].copy(); grid[i].clear(); }
                } else {
                    if (grid[i].isEmpty()) { grid[i].block = held.block; grid[i].item = held.item; grid[i].count = held.count; held = null; }
                    else if ((held.isBlock() && grid[i].isBlock() && held.block == grid[i].block)
                          || (held.isItem() && grid[i].isItem() && held.item == grid[i].item)) {
                        int can = Math.min(Inventory.MAX_STACK - grid[i].count, held.count);
                        grid[i].count += can; held.count -= can; if (held.count <= 0) held = null;
                    }
                }
                return;
            }
        }
        // инвентарь (27+9): тот же layout, что в InventoryScreen
        for (int i = 0; i < Inventory.TOTAL; i++) {
            float[] r = invSlotRect(i);
            if (mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3]) {
                Inventory.Slot s = inventory.slot(i);
                if (held == null) {
                    if (s.isEmpty()) return;
                    held = s.copy(); s.clear();
                } else {
                    if (s.isEmpty()) { s.block = held.block; s.item = held.item; s.count = held.count; held = null; }
                    else if ((held.isBlock() && s.isBlock() && held.block == s.block)
                          || (held.isItem() && s.isItem() && held.item == s.item)) {
                        int can = Math.min(Inventory.MAX_STACK - s.count, held.count);
                        s.count += can; held.count -= can; if (held.count <= 0) held = null;
                    }
                }
                return;
            }
        }
    }

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

    private void drawQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        float[] v = { x0,y0,u0,v0,r,g,b,a, x1,y0,u1,v0,r,g,b,a, x1,y1,u1,v1,r,g,b,a, x0,y1,u0,v1,r,g,b,a };
        try (MemoryStack st = MemoryStack.stackPush()) {
            FloatBuffer fb = st.mallocFloat(v.length); fb.put(v).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
    }

    private void drawIcon(Inventory.Slot s, float[] r) {
        if (s == null || s.isEmpty()) return;
        float pad = SLOT * 0.15f;
        if (s.isBlock()) {
            int tile = s.block.tileTop; float tu = TextureAtlas.TILE_U;
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

        drawQuad(-1, -1, 1, 1, 0,0,0,0, 0,0,0,0.5f);
        // слоты печи — тёмные
        for (int i = 0; i < 3; i++) {
            float[] r = slotRect(i);
            drawQuad(r[0], r[1], r[2], r[3], 0,0,0,0, 0.1f,0.1f,0.1f,1f);
            drawQuad(r[0], r[1], r[2], r[1]+0.006f, 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawQuad(r[0], r[3]-0.006f, r[2], r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawQuad(r[0], r[1], r[0]+0.006f, r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawQuad(r[2]-0.006f, r[1], r[2], r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawIcon(grid[i], r);
        }
        // инвентарь — светлее
        for (int i = 0; i < Inventory.TOTAL; i++) {
            float[] r = invSlotRect(i);
            drawQuad(r[0], r[1], r[2], r[3], 0,0,0,0, 0.22f,0.22f,0.22f,1f);
            drawQuad(r[0], r[1], r[2], r[1]+0.006f, 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawQuad(r[0], r[3]-0.006f, r[2], r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawQuad(r[0], r[1], r[0]+0.006f, r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawQuad(r[2]-0.006f, r[1], r[2], r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawIcon(inventory.slot(i), r);
        }
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
        for (int i = 0; i < 3; i++) {
            if (grid[i].isEmpty()) continue;
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
