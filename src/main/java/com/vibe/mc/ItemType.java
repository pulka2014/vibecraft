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
    DIAMOND_SHOVEL (13, "textures/diamond_shovel.png", "shovel",  8f),
    IRON_INGOT     (14, "textures/iron_ingot.png",      null,      1f),
    IRON_PICKAXE   (15, "textures/iron_pickaxe.png",    "pickaxe", 7f),
    IRON_AXE       (16, "textures/iron_axe.png",        "axe",     7f),
    IRON_SHOVEL    (17, "textures/iron_shovel.png",     "shovel",  7f),

    // Новые предметы (1.0.2)
    WHEAT_SEEDS    (18, "textures/wheat_seeds.png",     null,      1f),
    WHEAT          (19, "textures/wheat.png",           null,      1f),
    BREAD          (20, "textures/bread.png",           null,      1f),
    WOODEN_SWORD   (21, "textures/wooden_sword.png",    "sword",   4f),
    STONE_SWORD    (22, "textures/stone_sword.png",     "sword",   6f),
    IRON_SWORD     (23, "textures/iron_sword.png",      "sword",   7f),
    DIAMOND_SWORD  (24, "textures/diamond_sword.png",   "sword",   8f),
    WOODEN_HOE     (25, "textures/wooden_hoe.png",      "hoe",     4f),
    STONE_HOE      (26, "textures/stone_hoe.png",       "hoe",     6f),
    IRON_HOE       (27, "textures/iron_hoe.png",        "hoe",     7f),
    DIAMOND_HOE    (28, "textures/diamond_hoe.png",     "hoe",     8f),
    BUCKET         (29, "textures/bucket.png",          null,      1f),
    WATER_BUCKET   (30, "textures/water_bucket.png",    null,      1f);

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

    public int tier() {
        return switch (this) {
            case WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_SWORD, WOODEN_HOE -> 0;
            case STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_SWORD, STONE_HOE -> 1;
            case IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_SWORD, IRON_HOE -> 2;
            case DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_SWORD, DIAMOND_HOE -> 3;
            default -> -1;
        };
    }

    private static final ItemType[] BY_ID = new ItemType[values().length];
    static { for (ItemType t : values()) BY_ID[t.id] = t; }
    public static ItemType byId(byte id) {
        if (id < 0 || id >= BY_ID.length) return null;
        return BY_ID[id];
    }
}
