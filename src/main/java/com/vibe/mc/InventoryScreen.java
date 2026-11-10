package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * Экран инвентаря: 27 слотов сеткой 9x3 + хотбар 9 внизу.
 * ЛКМ: взять/положить стак. Курсор таскает предмет.
 */
public class InventoryScreen {
    private final Inventory inventory;
    private final int atlasTex;
    private final FontRenderer font;
    private final Shader shader;
    private int vao, vbo;

    private boolean open = false;
    private float mouseX = 0, mouseY = 0;
    private Inventory.Slot held = null; // «в руке»
    private final Inventory.Slot[] craftGrid = new Inventory.Slot[4]; // 2x2
    private BlockType craftResultBlock = null;
    private ItemType craftResultItem = null;
    private int craftResultCount = 0;

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

    private final int itemAtlasTex;
    public InventoryScreen(int atlasTex, int itemAtlasTex, Inventory inventory, FontRenderer font) {
        this.atlasTex = atlasTex;
        this.itemAtlasTex = itemAtlasTex;
        this.inventory = inventory;
        this.font = font;
        for (int i = 0; i < 4; i++) craftGrid[i] = new Inventory.Slot();
        shader = new Shader(VERT, FRAG);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
    }

    public boolean isOpen() { return open; }
    public void open() { open = true; System.out.println("[Inv] open"); }
    public void close() {
        open = false;
        if (held != null && !held.isEmpty()) {
            // вернуть в первый свободный слот (не add, чтобы не дублировать)
            boolean placed = false;
            for (int i = 0; i < Inventory.TOTAL; i++) {
                if (inventory.slot(i).isEmpty()) {
                    if (held.isBlock()) inventory.slot(i).block = held.block; else inventory.slot(i).item = held.item;
                    inventory.slot(i).count = held.count;
                    placed = true;
                    break;
                }
            }
            if (!placed) { if (held.isBlock()) inventory.addBlock(held.block, held.count); else inventory.addItem(held.item, held.count); }
            held = null;
        }
        for (int i = 0; i < 4; i++) {
            Inventory.Slot s = craftGrid[i];
            if (!s.isEmpty()) {
                if (s.isBlock()) inventory.addBlock(s.block, s.count); else inventory.addItem(s.item, s.count);
                s.clear();
            }
        }
    }

    public void setMouse(float ndcX, float ndcY) { mouseX = ndcX; mouseY = ndcY; }

    /** Координаты слота i (0..35) в NDC: хотбар 0-8, основной 9-35. */
    private float[] slotRect(int i) {
        float totalW = 9 * SLOT + 8 * GAP;
        float startX = -totalW / 2f;
        int col, row;
        if (i < Inventory.HOTBAR) { col = i; row = 3; }
        else { int m = i - Inventory.HOTBAR; col = m % 9; row = 2 - (m / 9); }
        float x0 = startX + col * (SLOT + GAP);
        float y0 = -0.1f + row * (SLOT + GAP) + (row == 3 ? -0.05f : 0);
        // проще: основной инвентарь сверху, хотбар снизу
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

    private float[] craftSlotRect(int i) {
        // 2x2 слева сверху: колонка i%2, ряд i/2
        float baseX = -0.35f;
        float baseY = 0.35f;
        float x0 = baseX + (i % 2) * (SLOT + GAP);
        float y0 = baseY - (i / 2) * (SLOT + GAP);
        return new float[]{x0, y0, x0 + SLOT, y0 + SLOT};
    }

    private float[] resultSlotRect() {
        float baseX = -0.35f + 3 * (SLOT + GAP);
        float baseY = 0.35f - 0.5f * (SLOT + GAP);
        return new float[]{baseX, baseY, baseX + SLOT, baseY + SLOT};
    }

    private void recomputeCraft() {
        Crafting.Result r = Crafting.match(craftGrid);
        if (r != null) {
            craftResultBlock = r.block;
            craftResultItem = r.item;
            craftResultCount = r.count;
        } else {
            craftResultBlock = null; craftResultItem = null; craftResultCount = 0;
        }
    }

    public void click(boolean rightClick) {
        // результат крафта
        float[] rr = resultSlotRect();
        if (mouseX >= rr[0] && mouseX <= rr[2] && mouseY >= rr[1] && mouseY <= rr[3]) {
            if (craftResultBlock == null && craftResultItem == null) return;

            // сколько крафтов можно сделать
            int maxCrafts = Integer.MAX_VALUE;
            for (Inventory.Slot s : craftGrid) {
                if (!s.isEmpty()) maxCrafts = Math.min(maxCrafts, s.count);
            }
            if (maxCrafts == Integer.MAX_VALUE) maxCrafts = 0;
            if (maxCrafts <= 0) return;

            if (!rightClick) {
                // ЛКМ: 1 крафт
                if (held == null) {
                    held = (craftResultBlock != null) ? new Inventory.Slot(craftResultBlock, craftResultCount) : new Inventory.Slot(craftResultItem, craftResultCount);
                } else if ((craftResultBlock != null && held.isBlock() && held.block == craftResultBlock) ||
                           (craftResultItem != null && held.isItem() && held.item == craftResultItem)) {
                    held.count += craftResultCount;
                } else {
                    return;
                }
                Crafting.consume(craftGrid);
                recomputeCraft();
            } else {
                // ПКМ: 1 крафт
                if (held == null) {
                    held = (craftResultBlock != null) ? new Inventory.Slot(craftResultBlock, craftResultCount) : new Inventory.Slot(craftResultItem, craftResultCount);
                } else if ((craftResultBlock != null && held.isBlock() && held.block == craftResultBlock) ||
                           (craftResultItem != null && held.isItem() && held.item == craftResultItem)) {
                    held.count += craftResultCount;
                } else {
                    return;
                }
                Crafting.consume(craftGrid);
                recomputeCraft();
            }
            return;
        }
        for (int i = 0; i < 4; i++) {
            float[] r = craftSlotRect(i);
            if (mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3]) {
                if (held == null) {
                    if (!craftGrid[i].isEmpty()) {
                        if (rightClick) {
                            // взять половину (вверх)
                            int take = (craftGrid[i].count + 1) / 2;
                            held = craftGrid[i].isBlock() ? new Inventory.Slot(craftGrid[i].block, take) : new Inventory.Slot(craftGrid[i].item, take);
                            craftGrid[i].count -= take;
                            if (craftGrid[i].count <= 0) { craftGrid[i].clear(); }
                        } else {
                            held = craftGrid[i].isBlock() ? new Inventory.Slot(craftGrid[i].block, craftGrid[i].count) : new Inventory.Slot(craftGrid[i].item, craftGrid[i].count);
                            craftGrid[i].clear();
                        }
                    }
                } else {
                    if (rightClick) {
                        // положить 1
                        if (craftGrid[i].isEmpty()) { if (held.isBlock()) craftGrid[i].block = held.block; else craftGrid[i].item = held.item; craftGrid[i].count = 1; }
                        else if ((held.isBlock() && craftGrid[i].isBlock() && craftGrid[i].block == held.block) || (held.isItem() && craftGrid[i].isItem() && craftGrid[i].item == held.item)) craftGrid[i].count++;
                        else { recomputeCraft(); return; }
                        held.count--;
                        if (held.count <= 0) held = null;
                    } else {
                        if (craftGrid[i].isEmpty()) { if (held.isBlock()) { craftGrid[i].block = held.block; } else { craftGrid[i].item = held.item; } craftGrid[i].count = held.count; held = null; }
                        else if ((held.isBlock() && craftGrid[i].isBlock() && craftGrid[i].block == held.block) || (held.isItem() && craftGrid[i].isItem() && craftGrid[i].item == held.item)) {
                            int can = Math.min(64 - craftGrid[i].count, held.count);
                            craftGrid[i].count += can; held.count -= can;
                            if (held.count <= 0) held = null;
                        }
                    }
                }
                recomputeCraft();
                return;
            }
        }
        // основной инвентарь
        for (int i = 0; i < Inventory.TOTAL; i++) {
            float[] r = slotRect(i);
            if (mouseX >= r[0] && mouseX <= r[2] && mouseY >= r[1] && mouseY <= r[3]) {
                Inventory.Slot s = inventory.slot(i);
                if (held == null) {
                    if (s.isEmpty()) return;
                    if (rightClick) {
                        // взять половину
                        int take = (s.count + 1) / 2;
                        held = s.isBlock() ? new Inventory.Slot(s.block, take) : new Inventory.Slot(s.item, take);
                        s.count -= take;
                        if (s.count <= 0) { s.clear(); }
                    } else {
                        held = inventory.take(i);
                    }
                } else {
                    if (rightClick) {
                        // положить 1
                        if (s.isEmpty()) { if (held.isBlock()) s.block = held.block; else s.item = held.item; s.count = 1; }
                        else if ((held.isBlock() && s.isBlock() && s.block == held.block) || (held.isItem() && s.isItem() && s.item == held.item)) s.count++;
                        else return;
                        held.count--;
                        if (held.count <= 0) held = null;
                    } else {
                        Inventory.Slot copy = held;
                        inventory.put(i, copy);
                        held = (copy.count > 0) ? copy : null;
                    }
                }
                return;
            }
        }
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

    private void drawSlot(int i) {
        float[] r = slotRect(i);
        GL20.glUniform1i(shader.uniform("uUseTex"), 0);
        drawQuad(r[0], r[1], r[2], r[3], 0,0,0,0, 0.2f,0.2f,0.2f,0.9f);
        drawQuad(r[0], r[1], r[2], r[1]+0.006f, 0,0,0,0, 0.5f,0.5f,0.5f,1);
        drawQuad(r[0], r[3]-0.006f, r[2], r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
        drawQuad(r[0], r[1], r[0]+0.006f, r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);
        drawQuad(r[2]-0.006f, r[1], r[2], r[3], 0,0,0,0, 0.5f,0.5f,0.5f,1);

        Inventory.Slot s = inventory.slot(i);
        if (s != null && !s.isEmpty()) {
            float pad = SLOT * 0.15f;
            if (s.isBlock()) {
                int tile = s.block.tileTop;
                float tu = TextureAtlas.TILE_U;
                float u0 = tile * tu, u1 = u0 + tu;
                float tr = 1f, tg = 1f, tb = 1f;
                if (s.block == BlockType.GRASS) { tr = 0.35f; tg = 0.85f; tb = 0.30f; }
                else if (s.block == BlockType.LEAVES) { tr = 0.25f; tg = 0.65f; tb = 0.20f; }
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
                drawQuad(r[0]+pad, r[1]+pad, r[2]-pad, r[3]-pad, u0, 1f, u1, 0f, tr, tg, tb, 1f);
            } else if (s.isItem()) {
                float tu = ItemAtlas.TILE_U;
                float u0 = s.item.id * tu, u1 = u0 + tu;
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
                drawQuad(r[0]+pad, r[1]+pad, r[2]-pad, r[3]-pad, u0, 1f, u1, 0f, 1f, 1f, 1f, 1f);
            }
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

        // затемнение
        GL20.glUniform1i(shader.uniform("uUseTex"), 0);
        drawQuad(-1, -1, 1, 1, 0,0,0,0, 0f,0f,0f,0.5f);

        // слоты инвентаря
        for (int i = 0; i < Inventory.TOTAL; i++) drawSlot(i);

        // сетка крафта 2x2
        for (int i = 0; i < 4; i++) {
            float[] r = craftSlotRect(i);
            GL20.glUniform1i(shader.uniform("uUseTex"), 0);
            drawQuad(r[0], r[1], r[2], r[3], 0,0,0,0, 0.25f,0.25f,0.25f,0.9f);
            drawQuad(r[0], r[1], r[2], r[1]+0.006f, 0,0,0,0, 0.6f,0.6f,0.6f,1);
            drawQuad(r[0], r[3]-0.006f, r[2], r[3], 0,0,0,0, 0.6f,0.6f,0.6f,1);
            drawQuad(r[0], r[1], r[0]+0.006f, r[3], 0,0,0,0, 0.6f,0.6f,0.6f,1);
            drawQuad(r[2]-0.006f, r[1], r[2], r[3], 0,0,0,0, 0.6f,0.6f,0.6f,1);
            Inventory.Slot s = craftGrid[i];
            if (s == null || s.isEmpty()) continue;
            float pad = SLOT * 0.15f;
            if (s.isBlock()) {
                int tile = s.block.tileTop;
                float tu = TextureAtlas.TILE_U;
                float u0 = tile * tu, u1 = u0 + tu;
                float tr = 1f, tg = 1f, tb = 1f;
                if (s.block == BlockType.GRASS) { tr = 0.35f; tg = 0.85f; tb = 0.30f; }
                else if (s.block == BlockType.LEAVES) { tr = 0.25f; tg = 0.65f; tb = 0.20f; }
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
                drawQuad(r[0]+pad, r[1]+pad, r[2]-pad, r[3]-pad, u0, 1f, u1, 0f, tr, tg, tb, 1f);
            } else if (s.isItem()) {
                float tu = ItemAtlas.TILE_U;
                float u0 = s.item.id * tu, u1 = u0 + tu;
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
                drawQuad(r[0]+pad, r[1]+pad, r[2]-pad, r[3]-pad, u0, 1f, u1, 0f, 1f, 1f, 1f, 1f);
            }
        }

        // результат
        float[] rr = resultSlotRect();
        GL20.glUniform1i(shader.uniform("uUseTex"), 0);
        drawQuad(rr[0], rr[1], rr[2], rr[3], 0,0,0,0, 0.3f,0.3f,0.3f,0.9f);
        drawQuad(rr[0], rr[1], rr[2], rr[1]+0.006f, 0,0,0,0, 0.7f,0.7f,0.7f,1);
        drawQuad(rr[0], rr[3]-0.006f, rr[2], rr[3], 0,0,0,0, 0.7f,0.7f,0.7f,1);
        drawQuad(rr[0], rr[1], rr[0]+0.006f, rr[3], 0,0,0,0, 0.7f,0.7f,0.7f,1);
        drawQuad(rr[2]-0.006f, rr[1], rr[2], rr[3], 0,0,0,0, 0.7f,0.7f,0.7f,1);
        if (craftResultBlock != null) {
            int tile = craftResultBlock.tileTop;
            float tu = TextureAtlas.TILE_U;
            float u0 = tile * tu, u1 = u0 + tu;
            float pad = SLOT * 0.15f;
            GL20.glUniform1i(shader.uniform("uUseTex"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
            drawQuad(rr[0]+pad, rr[1]+pad, rr[2]-pad, rr[3]-pad, u0, 1f, u1, 0f, 1f, 1f, 1f, 1f);
        } else if (craftResultItem != null) {
            float tu = ItemAtlas.TILE_U;
            float u0 = craftResultItem.id * tu, u1 = u0 + tu;
            float pad = SLOT * 0.15f;
            GL20.glUniform1i(shader.uniform("uUseTex"), 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
            drawQuad(rr[0]+pad, rr[1]+pad, rr[2]-pad, rr[3]-pad, u0, 1f, u1, 0f, 1f, 1f, 1f, 1f);
        }

        // held у курсора
        if (held != null && !held.isEmpty()) {
            float sz = SLOT * 0.7f;
            if (held.isBlock()) {
                int tile = held.block.tileTop;
                float tu = TextureAtlas.TILE_U;
                float u0 = tile * tu, u1 = u0 + tu;
                float tr = 1f, tg = 1f, tb = 1f;
                if (held.block == BlockType.GRASS) { tr = 0.35f; tg = 0.85f; tb = 0.30f; }
                else if (held.block == BlockType.LEAVES) { tr = 0.25f; tg = 0.65f; tb = 0.20f; }
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
                drawQuad(mouseX - sz/2, mouseY - sz/2, mouseX + sz/2, mouseY + sz/2, u0, 1f, u1, 0f, tr, tg, tb, 1f);
            } else if (held.isItem()) {
                float tu = ItemAtlas.TILE_U;
                float u0 = held.item.id * tu, u1 = u0 + tu;
                GL20.glUniform1i(shader.uniform("uUseTex"), 1);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, itemAtlasTex);
                float tr = 1f, tg = 1f, tb = 1f;
                if (held.block == BlockType.GRASS) { tr = 0.35f; tg = 0.85f; tb = 0.30f; }
                else if (held.block == BlockType.LEAVES) { tr = 0.25f; tg = 0.65f; tb = 0.20f; }
                drawQuad(mouseX - sz/2, mouseY - sz/2, mouseX + sz/2, mouseY + sz/2, u0, 1f, u1, 0f, tr, tg, tb, 1f);
            }
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL30.glBindVertexArray(0);

        // ===== ЧИСЛА (после всего, через font) =====
        // инвентарь
        for (int i = 0; i < Inventory.TOTAL; i++) {
            Inventory.Slot s = inventory.slot(i);
            if (s == null || s.isEmpty()) continue;
            float[] r = slotRect(i);
            float ch = SLOT * 0.45f;
            String txt = String.valueOf(s.count);
            float w = font.width(txt, ch);
            font.drawString(txt, r[2] - w - 0.008f, r[1] + ch + 0.004f, ch);
        }
        // сетка крафта
        for (int i = 0; i < 4; i++) {
            Inventory.Slot s = craftGrid[i];
            if (s == null || s.isEmpty()) continue;
            float[] r = craftSlotRect(i);
            float ch = SLOT * 0.45f;
            String txt = String.valueOf(s.count);
            float w = font.width(txt, ch);
            font.drawString(txt, r[2] - w - 0.008f, r[1] + ch + 0.004f, ch);
        }
        // результат
        if (craftResultCount > 0) {
            float ch = SLOT * 0.45f;
            String txt = String.valueOf(craftResultCount);
            float w = font.width(txt, ch);
            font.drawString(txt, rr[2] - w - 0.008f, rr[1] + ch + 0.004f, ch);
        }
        // held
        if (held != null && !held.isEmpty()) {
            float ch = SLOT * 0.45f;
            String txt = String.valueOf(held.count);
            float w = font.width(txt, ch);
            font.drawString(txt, mouseX + SLOT*0.3f, mouseY + ch*0.3f, ch);
        }

}
}
