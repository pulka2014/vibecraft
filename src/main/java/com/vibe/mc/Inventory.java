package com.vibe.mc;

/**
 * Инвентарь: 9 слотов хотбара (0-8) + 27 слотов основного (9-35).
 * Слот хранит ЛИБО BlockType (блок), ЛИБО ItemType (предмет).
 */
public class Inventory {
    public static class Slot {
        public BlockType block;
        public ItemType item;
        public int count;
        public Slot() {}
        public Slot(BlockType b, int c) { block = b; count = c; }
        public Slot(ItemType it, int c) { item = it; count = c; }
        public boolean isEmpty() { return (block == null && item == null) || count <= 0; }
        public boolean isBlock() { return block != null; }
        public boolean isItem() { return item != null; }
        public Slot copy() {
            Slot s = new Slot();
            s.block = block; s.item = item; s.count = count;
            return s;
        }
        public void clear() { block = null; item = null; count = 0; }
    }

    public static final int HOTBAR = 9;
    public static final int MAIN = 27;
    public static final int TOTAL = HOTBAR + MAIN;
    public static final int MAX_STACK = 64;

    private final Slot[] slots = new Slot[TOTAL];
    public Inventory() { for (int i = 0; i < TOTAL; i++) slots[i] = new Slot(); }

    public boolean addBlock(BlockType type, int n) {
        int remaining = n;
        for (Slot s : slots) if (s.isBlock() && s.block == type && s.count < MAX_STACK && remaining > 0) {
            int can = Math.min(MAX_STACK - s.count, remaining); s.count += can; remaining -= can;
        }
        for (int i = 0; i < TOTAL && remaining > 0; i++) if (slots[i].isEmpty()) {
            int put = Math.min(MAX_STACK, remaining); slots[i].block = type; slots[i].count = put; remaining -= put;
        }
        if (remaining < n) Logger.info("[Inventory] +" + (n - remaining) + " " + type);
        return remaining == 0;
    }

    public boolean addItem(ItemType type, int n) {
        int remaining = n;
        for (Slot s : slots) if (s.isItem() && s.item == type && s.count < MAX_STACK && remaining > 0) {
            int can = Math.min(MAX_STACK - s.count, remaining); s.count += can; remaining -= can;
        }
        for (int i = 0; i < TOTAL && remaining > 0; i++) if (slots[i].isEmpty()) {
            int put = Math.min(MAX_STACK, remaining); slots[i].item = type; slots[i].count = put; remaining -= put;
        }
        if (remaining < n) Logger.info("[Inventory] +" + (n - remaining) + " " + type);
        return remaining == 0;
    }

    public Slot slot(int i) { return (i >= 0 && i < TOTAL) ? slots[i] : null; }
    public int getBlock(BlockType t) { int s=0; for (Slot x:slots) if (x.isBlock()&&x.block==t) s+=x.count; return s; }
    public int getItem(ItemType t)   { int s=0; for (Slot x:slots) if (x.isItem()&&x.item==t)  s+=x.count; return s; }
    public int slotCount() { int n=0; for (Slot s:slots) if (!s.isEmpty()) n++; return n; }

    public Slot take(int i) {
        Slot s = slots[i];
        if (s.isEmpty()) return null;
        Slot out = s.copy(); s.clear(); return out;
    }

    public void put(int i, Slot stack) {
        if (stack == null || stack.isEmpty()) return;
        Slot s = slots[i];
        int maxStack = (stack.isItem() && stack.item.toolType != null) ? 1 : MAX_STACK;
        if (s.isEmpty()) { s.block = stack.block; s.item = stack.item; s.count = stack.count; stack.clear(); }
        else if ((s.isBlock() && stack.isBlock() && s.block == stack.block) ||
                 (s.isItem() && stack.isItem() && s.item == stack.item)) {
            int can = Math.min(maxStack - s.count, stack.count);
            s.count += can; stack.count -= can;
            if (stack.count <= 0) stack.clear();
        } else {
            BlockType tb = s.block; ItemType ti = s.item; int tc = s.count;
            s.block = stack.block; s.item = stack.item; s.count = stack.count;
            stack.block = tb; stack.item = ti; stack.count = tc;
        }
    }
}
