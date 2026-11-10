package com.vibe.mc;

public enum BlockType {
    AIR            (0, -1, -1, -1, -1, 0f, null),
    GRASS          (1,  0,  1,  1,  1, 0.6f, "shovel"),
    DIRT           (2,  1,  1,  1,  1, 0.5f, "shovel"),
    STONE          (3,  2,  2,  2,  2, 1.5f, "pickaxe"),
    SAND           (4,  3,  3,  3,  3, 0.5f, "shovel"),
    WOOD           (5,  5,  4,  5,  4, 2.0f, "axe"),
    PLANKS         (6,  6,  6,  6,  6, 2.0f, "axe"),
    LEAVES         (7,  7,  7,  7,  7, 0.2f, null),
    CRAFTING_TABLE (8,  8,  9,  8, 10, 2.5f, "axe"),
    PRESSURE_PLATE (9, 12, 12, 12, 12, 0.5f, "axe"),
    COBBLESTONE    (10, 13, 13, 13, 13, 1.5f, "pickaxe"),
    COAL_ORE       (11, 14, 14, 14, 14, 3.0f, "pickaxe"),
    IRON_ORE       (12, 15, 15, 15, 15, 3.0f, "pickaxe"),
    DIAMOND_ORE    (13, 16, 16, 16, 16, 3.0f, "pickaxe"),
    FURNACE        (14, 18, 19, 18, 20, 3.5f, "pickaxe");

    public final byte id;
    public final int tileTop;
    public final int tileSide;
    public final int tileBottom;
    public final int tileFront;
    public final float hardness; // секунд рукой
    public final String toolType; // "pickaxe", "axe", "shovel", null

    BlockType(int id, int top, int side, int bottom) {
        this(id, top, side, bottom, side, 1.0f, null);
    }
    BlockType(int id, int top, int side, int bottom, int front) {
        this(id, top, side, bottom, front, 1.0f, null);
    }
    BlockType(int id, int top, int side, int bottom, int front, float hardness, String toolType) {
        this.id = (byte) id;
        this.tileTop = top;
        this.tileSide = side;
        this.tileBottom = bottom;
        this.tileFront = front;
        this.hardness = hardness;
        this.toolType = toolType;
    }

    public int tileFor(int face) {
        if (face == 0) return tileTop;
        if (face == 1) return tileBottom;
        if (face == 2) return tileFront;
        return tileSide;
    }

    private static final BlockType[] BY_ID = new BlockType[values().length];
    static {
        for (BlockType b : values()) BY_ID[b.id] = b;
    }
    public static BlockType byId(byte id) {
        if (id < 0 || id >= BY_ID.length) return AIR;
        return BY_ID[id];
    }
}
