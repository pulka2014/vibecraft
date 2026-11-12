package com.vibe.mc;

public enum BlockType {
    AIR            (0, -1, -1, -1, -1, 0f, null),
    GRASS          (1,  0,  1,  2,  1, 0.6f, "shovel"),
    DIRT           (2, 2, 2, 2, 2, 0.5f, "shovel"),
    STONE(3, 3, 3, 3, 3, 1.5f, "pickaxe"),
    SAND           (4,  4,  4,  4,  4, 0.5f, "shovel"),
    WOOD           (5,  6,  5,  6,  5, 2.0f, "axe"),
    PLANKS         (6,  7,  7,  7,  7, 2.0f, "axe"),
    LEAVES         (7,  8,  8,  8,  8, 0.2f, null),
    CRAFTING_TABLE (8,  9, 10,  7, 11, 2.5f, "axe"),
    PRESSURE_PLATE (9, 13, 13, 13, 13, 0.5f, "axe"),
    COBBLESTONE(10, 14, 14, 14, 14, 1.5f, "pickaxe"),
    COAL_ORE(11, 15, 15, 15, 15, 3.0f, "pickaxe"),
    IRON_ORE(12, 16, 16, 16, 16, 3.0f, "pickaxe"),
    DIAMOND_ORE(13, 17, 17, 17, 17, 3.0f, "pickaxe"),
    FURNACE        (14, 18, 19, 18, 20, 3.5f, "pickaxe"),
    WATER       (15, 21, 21, 21, 21, 0.0f, null),
    WHEAT       (16, 22, 22, 22, 22, 0.1f, null),
    FARMLAND    (17, 2, 2, 2, 2, 0.5f, "shovel"),
    IRON_BLOCK  (18, 23, 23, 23, 23, 3.0f, "pickaxe"),
    COAL_BLOCK  (19, 24, 24, 24, 24, 3.0f, "pickaxe"),
    GLASS       (20, 25, 25, 25, 25, 0.3f, null);

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

    /** Минимальный tier инструмента для дропа: 0=дерево,1=камень,2=железо,3=алмаз, -1=рука. */
    public int minTier() {
        return switch (this) {
            case STONE, COAL_ORE -> 0;
            case IRON_ORE, COBBLESTONE -> 1;
            case FURNACE -> 0;
            case DIAMOND_ORE -> 2;
            default -> -1;
        };
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
