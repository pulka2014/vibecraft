package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

/**
 * Экран крафта 3x3 (верстак). 9 слотов + результат.
 * ЛКМ/ПКМ — как в InventoryScreen.
 */
public class CraftingScreen {
    private final Inventory inventory;
    private final int atlasTex, itemAtlasTex;
    private final FontRenderer font;
    private final Shader shader;
    private int vao, vbo;

    private boolean open = false;
    private float mouseX = 0, mouseY = 0;
    private Inventory.Slot held = null;
    private final Inventory.Slot[] grid = new Inventory.Slot[9];
    private BlockType resultBlock = null;
    private ItemType resultItem = null;
    private int resultCount = 0;

    public static final float SLOT = 0.08f;
    public static final float GAP = 0.005f;

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

    public CraftingScreen(int atlasTex, int itemAtlasTex, Inventory inventory, FontRenderer font) {
        this.atlasTex = atlasTex;
        this.itemAtlasTex = itemAtlasTex;
        this.inventory = inventory;
        this.font = font;
        for (int i = 0; i < 9; i++) grid[i] = new Inventory.Slot();
        shader = new Shader(VERT, FRAG);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
    }

    public boolean isOpen() { return open; }
    public void open() { open = true; recompute(); Logger.info("[Craft] open 3x3"); }
    public void close() {
        open = false;
        // вернуть всё из сетки и held в инвентарь
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

    private float[] gridRect(int i) {
        float baseX = -0.3f;
        float baseY = 0.3f;
        int col = i % 3, row = i / 3;
        float x0 = baseX + col * (SLOT + GAP);
        float y0 = baseY - row * (SLOT + GAP);
        return new float[]{x0, y0, x0 + SLOT, y0 + SLOT};
    }
    private float[] resultRect() {
        float baseX = -0.3f + 4 * (SLOT + GAP);
        float baseY = 0.3f - 1 * (SLOT + GAP);
        return new float[]{baseX, baseY, baseX + SLOT, baseY + SLOT};
    }

    private void recompute() {
        Crafting.Result r = Crafting.match3x3(grid);
        if (r != null) { resultBlock = r.block; resultItem = r.item; resultCount = r.count; }
        else { resultBlock = null; resultItem = null; resultCount = 0; }
    }

    public void click(boolean rightClick) {
        // результат
        float[] rr = resultRect();
        if (inside(rr)) {
            if (resultBlock == null && resultItem == null) return;

            // инструменты не стакаются (max 1)
            boolean isTool = (resultItem != null && resultItem.toolType != null);
            int maxStack = isTool ? 1 : 64;

            // сколько крафтов можно сделать
            int maxCrafts = Integer.MAX_VALUE;
            for (Inventory.Slot s : grid) {
                if (!s.isEmpty()) maxCrafts = Math.min(maxCrafts, s.count);
            }
            if (maxCrafts == Integer.MAX_VALUE) maxCrafts = 0;
            if (maxCrafts <= 0) return;

            if (!rightClick) {
                // ЛКМ: всё возможное (maxCrafts), но не больше maxStack
                int crafts = isTool ? 1 : maxCrafts;
                int total = resultCount * crafts;

                if (held == null) {
                    held = (resultBlock != null) ? new Inventory.Slot(resultBlock, total) : new Inventory.Slot(resultItem, total);
                } else if ((resultBlock != null && held.isBlock() && held.block == resultBlock) ||
                           (resultItem != null && held.isItem() && held.item == resultItem)) {
                    if (held.count + total > maxStack) {
                        int can = maxStack - held.count;
                        if (can <= 0) return;
                        int canCrafts = can / resultCount;
                        if (canCrafts <= 0) return;
                        total = resultCount * canCrafts;
                        crafts = canCrafts;
                    }
                    held.count += total;
                } else {
                    return;
                }
                for (int c = 0; c < crafts; c++) Crafting.consume(grid);
                recompute();
            } else {
                // ПКМ: 1 крафт
                if (held == null) {
                    held = (resultBlock != null) ? new Inventory.Slot(resultBlock, resultCount) : new Inventory.Slot(resultItem, resultCount);
                } else if ((resultBlock != null && held.isBlock() && held.block == resultBlock) ||
                           (resultItem != null && held.isItem() && held.item == resultItem)) {
                    if (held.count + resultCount > maxStack) return;
                    held.count += resultCount;
                } else {
                    return;
                }
                Crafting.consume(grid);
                recompute();
            }
            return;
        }        // инвентарь (27+9) — клики
        float invBaseY = -0.5f;
        for (int i = 0; i < Inventory.TOTAL; i++) {
            int col, row;
            if (i < Inventory.HOTBAR) { col = i; row = 3; }
            else { int m = i - Inventory.HOTBAR; col = m % 9; row = 2 - (m / 9); }
            float x0 = -0.4f + col * (SLOT + GAP);
            float y0 = invBaseY - row * (SLOT + GAP);
            float x1 = x0 + SLOT, y1 = y0 + SLOT;
            if (mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1) {
                Inventory.Slot s = inventory.slot(i);
                if (held == null) {
                    if (s.isEmpty()) return;
                    if (rightClick) {
                        int take = (s.count + 1) / 2;
                        held = s.isBlock() ? new Inventory.Slot(s.block, take) : new Inventory.Slot(s.item, take);
                        s.count -= take; if (s.count <= 0) s.clear();
                    } else { held = s.copy(); s.clear(); }
                } else {
                    // maxStack = 1 для инструментов
                    int maxStack = (held.isItem() && held.item.toolType != null) ? 1 : 64;
                    if (rightClick) {
                        if (s.isEmpty()) { if (held.isBlock()) s.block = held.block; else s.item = held.item; s.count = 1; }
                        else if ((held.isBlock() && s.isBlock() && s.block == held.block) || (held.isItem() && s.isItem() && s.item == held.item)) {
                            if (s.count + 1 > maxStack) return;
                            s.count++;
                        }
                        else return;
                        held.count--; if (held.count <= 0) held = null;
                    } else {
                        if (s.isEmpty()) { if (held.isBlock()) s.block = held.block; else s.item = held.item; s.count = held.count; held = null; }
                        else if ((held.isBlock() && s.isBlock() && s.block == held.block) || (held.isItem() && s.isItem() && s.item == held.item)) {
                            int can = Math.min(maxStack - s.count, held.count); s.count += can; held.count -= can; if (held.count <= 0) held = null;
                        }
                    }
                }
                return;
            }
        }
        // сетка
        for (int i = 0; i < 9; i++) {
            float[] r = gridRect(i);
            if (inside(r)) {
                Inventory.Slot s = grid[i];
                if (held == null) {
                    if (s.isEmpty()) return;
                    if (rightClick) {
                        int take = (s.count + 1) / 2;
                        held = s.isBlock() ? new Inventory.Slot(s.block, take) : new Inventory.Slot(s.item, take);
                        s.count -= take;
                        if (s.count <= 0) s.clear();
                    } else {
                        held = s.copy(); s.clear();
                    }
                } else {
                    if (rightClick) {
                        if (s.isEmpty()) { if (held.isBlock()) s.block = held.block; else s.item = held.item; s.count = 1; }
                        else if ((held.isBlock() && s.isBlock() && s.block == held.block) || (held.isItem() && s.isItem() && s.item == held.item)) s.count++;
                        else return;
                        held.count--; if (held.count <= 0) held = null;
                    } else {
                        if (s.isEmpty()) { if (held.isBlock()) s.block = held.block; else s.item = held.item; s.count = held.count; held = null; }
                        else if ((held.isBlock() && s.isBlock() && s.block == held.block) || (held.isItem() && s.isItem() && s.item == held.item)) {
                            int can = Math.min(64 - s.count, held.count); s.count += can; held.count -= can; if (held.count <= 0) held = null;
                        }
                    }
                }
                recompute();
                return;
            }
        }
        recompute();
    }

    private boolean inside(float[] r) {
        return mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3];
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
        if (s.isBlock()) {
            int tile = s.block.tileTop;
            float tu = TextureAtlas.TILE_U;
            float u0 = tile * tu, u1 = u0 + tu;
            float tr=1,tg=1,tb=1;
            if (s.block == BlockType.GRASS) { tr=0.35f; tg=0.85f; tb=0.30f; }
            else if (s.block == BlockType.LEAVES) { tr=0.25f; tg=0.65f; tb=0.20f; }
            GL20.glUniform1i(shader.uniform("uUseTex"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
            drawQuad(r[0]+pad, r[1]+pad, r[2]-pad, r[3]-pad, u0, 1f, u1, 0f, tr,tg,tb,1f);
        } else if (s.isItem()) {
            float tu = ItemAtlas.TILE_U;
            float u0 = s.item.id * tu, u1 = u0 + tu;
            GL20.glUniform1i(shader.uniform("uUseTex"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
            drawQuad(r[0]+pad, r[1]+pad, r[2]-pad, r[3]-pad, u0, 1f, u1, 0f, 1f,1f,1f,1f);
        }
    }

    public void render() {
        if (!open) return;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
        GL20.glUniform1i(shader.uniform("uTex"), 0);
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

        for (int i = 0; i < 9; i++) {
            float[] r = gridRect(i);
            drawSlotBg(r);
            drawIcon(grid[i], r);
        }
        float[] rr = resultRect();
        drawSlotBg(rr);
        if (resultBlock != null) {
            int tile = resultBlock.tileTop; float tu = TextureAtlas.TILE_U;
            float u0 = tile * tu, u1 = u0 + tu; float pad = SLOT*0.15f;
            GL20.glUniform1i(shader.uniform("uUseTex"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
            drawQuad(rr[0]+pad, rr[1]+pad, rr[2]-pad, rr[3]-pad, u0, 1f, u1, 0f, 1f,1f,1f,1f);
        } else if (resultItem != null) {
            float tu = ItemAtlas.TILE_U;
            float u0 = resultItem.id * tu, u1 = u0 + tu; float pad = SLOT*0.15f;
            GL20.glUniform1i(shader.uniform("uUseTex"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
            drawQuad(rr[0]+pad, rr[1]+pad, rr[2]-pad, rr[3]-pad, u0, 1f, u1, 0f, 1f,1f,1f,1f);
        }

        // инвентарь (27 слотов) + хотбар (9) снизу
        float invBaseY = -0.5f;
        for (int i = 0; i < Inventory.TOTAL; i++) {
            int col, row;
            if (i < Inventory.HOTBAR) { col = i; row = 3; }
            else { int m = i - Inventory.HOTBAR; col = m % 9; row = 2 - (m / 9); }
            float x0 = -0.4f + col * (SLOT + GAP);
            float y0 = invBaseY - row * (SLOT + GAP);
            float x1 = x0 + SLOT, y1 = y0 + SLOT;
            GL20.glUniform1i(shader.uniform("uUseTex"), 0);
            drawQuad(x0, y0, x1, y1, 0,0,0,0, 0.2f,0.2f,0.2f,0.9f);
            drawQuad(x0, y0, x1, y0+0.006f, 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawQuad(x0, y1-0.006f, x1, y1, 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawQuad(x0, y0, x0+0.006f, y1, 0,0,0,0, 0.5f,0.5f,0.5f,1);
            drawQuad(x1-0.006f, y0, x1, y1, 0,0,0,0, 0.5f,0.5f,0.5f,1);
            Inventory.Slot s = inventory.slot(i);
            if (s == null || s.isEmpty()) continue;
            float pad = SLOT * 0.15f;
            if (s.isBlock()) {
                int tile = s.block.tileTop;
                float tu = TextureAtlas.TILE_U;
                float u0 = tile * tu, u1 = u0 + tu;
                float tr=1,tg=1,tb=1;
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

        if (held != null && !held.isEmpty()) {
            float sz = SLOT * 0.7f;
            if (held.isBlock()) {
                int tile = held.block.tileTop; float tu = TextureAtlas.TILE_U;
                float u0 = tile * tu, u1 = u0 + tu;
                float tr = 1f, tg = 1f, tb = 1f;
                if (held.block == BlockType.GRASS) { tr = 0.35f; tg = 0.85f; tb = 0.30f; }
                else if (held.block == BlockType.LEAVES) { tr = 0.25f; tg = 0.65f; tb = 0.20f; }
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
                drawQuad(mouseX-sz/2, mouseY-sz/2, mouseX+sz/2, mouseY+sz/2, u0, 1f, u1, 0f, tr,tg,tb,1f);
            } else if (held.isItem()) {
                float tu = ItemAtlas.TILE_U;
                float u0 = held.item.id * tu, u1 = u0 + tu;
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
                drawQuad(mouseX-sz/2, mouseY-sz/2, mouseX+sz/2, mouseY+sz/2, u0, 1f, u1, 0f, 1f,1f,1f,1f);
            }
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);

        // числа: сетка
        for (int i = 0; i < 9; i++) {
            Inventory.Slot s = grid[i];
            if (s == null || s.isEmpty()) continue;
            float[] r = gridRect(i);
            float ch = SLOT * 0.45f;
            String txt = String.valueOf(s.count);
            float w = font.width(txt, ch);
            font.drawString(txt, r[2] - w - 0.008f, r[1] + ch + 0.004f, ch);
        }
        // число результата
        if (resultCount > 0) {
            float ch = SLOT * 0.45f;
            String txt = String.valueOf(resultCount);
            float w = font.width(txt, ch);
            font.drawString(txt, rr[2] - w - 0.008f, rr[1] + ch + 0.004f, ch);
        }
        // числа: инвентарь (27+9)
        // invBaseY уже объявлен выше (в рендере)
        for (int i = 0; i < Inventory.TOTAL; i++) {
            Inventory.Slot s = inventory.slot(i);
            if (s == null || s.isEmpty()) continue;
            int col, row;
            if (i < Inventory.HOTBAR) { col = i; row = 3; }
            else { int m = i - Inventory.HOTBAR; col = m % 9; row = 2 - (m / 9); }
            float x0 = -0.4f + col * (SLOT + GAP);
            float y0 = invBaseY - row * (SLOT + GAP);
            float ch = SLOT * 0.45f;
            String txt = String.valueOf(s.count);
            float w = font.width(txt, ch);
            font.drawString(txt, x0 + SLOT - w - 0.008f, y0 + ch + 0.004f, ch);
        }
    }
}
