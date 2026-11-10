package com.vibe.mc;

public enum ItemType {
    STICK          (0, "textures/stick.png",          null,      1f),
    APPLE          (1, "textures/apple.png",          null,      1f),
    WOODEN_PICKAXE (2, "textures/wooden_pickaxe.png", "pickaxe", 4f),
    STONE_PICKAXE  (3, "textures/stone_pickaxe.png",  "pickaxe", 6f),
    WOODEN_AXE     (4, "textures/wooden_axe.png",     "axe",     4f),
    STONE_AXE      (5, "textures/stone_axe.png",      "axe",     6f),
    WOODEN_SHOVEL  (6, "textures/wooden_shovel.png",  "shovel",  4f),
    STONE_SHOVEL   (7, "textures/stone_shovel.png",   "shovel",  6f),
    COAL           (8, "textures/coal.png",           null,      1f),
    RAW_IRON       (9, "textures/raw_iron.png",       null,      1f),
    DIAMOND        (10, "textures/diamond.png",       null,      1f),
    DIAMOND_PICKAXE(11, "textures/diamond_pickaxe.png", "pickaxe", 8f),
    DIAMOND_AXE    (12, "textures/diamond_axe.png",    "axe",     8f),
    DIAMOND_SHOVEL (13, "textures/diamond_shovel.png", "shovel",  8f);

    public final byte id;
    public final String texture;
    public final String toolType;
    public final float speed;

    ItemType(int id, String tex, String tool, float speed) {
        this.id = (byte) id;
        this.texture = tex;
        this.toolType = tool;
        this.speed = speed;
    }

    private static final ItemType[] BY_ID = new ItemType[values().length];
    static { for (ItemType t : values()) BY_ID[t.id] = t; }
    public static ItemType byId(byte id) {
        if (id < 0 || id >= BY_ID.length) return null;
        return BY_ID[id];
    }
}
