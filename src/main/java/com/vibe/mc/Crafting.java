package com.vibe.mc;

public class Crafting {
    public static class Result {
        public final BlockType block;
        public final ItemType item;
        public final int count;
        public Result(BlockType b, int c) { block = b; item = null; count = c; }
        public Result(ItemType it, int c) { block = null; item = it; count = c; }
    }

    public static Result match(Inventory.Slot[] g) {
        // shapeless: 1 WOOD → 4 PLANKS
        int wood = 0;
        for (Inventory.Slot s : g) if (s.isBlock() && s.block == BlockType.WOOD) wood++;
        if (wood == 1) return new Result(BlockType.PLANKS, 4);

        // shaped: 4 PLANKS 2x2 → CRAFTING_TABLE
        if (allBlock(g, BlockType.PLANKS)) return new Result(BlockType.CRAFTING_TABLE, 1);

        // shaped: 2 PLANKS горизонтально (ряд 0 ИЛИ ряд 1) → PRESSURE_PLATE
        if (isPlanks(g[0]) && isPlanks(g[1]) && g[2].isEmpty() && g[3].isEmpty())
            return new Result(BlockType.PRESSURE_PLATE, 1);
        if (g[0].isEmpty() && g[1].isEmpty() && isPlanks(g[2]) && isPlanks(g[3]))
            return new Result(BlockType.PRESSURE_PLATE, 1);

        // shaped: 2 PLANKS вертикально (колонка 0 ИЛИ колонка 1) → 4 STICK
        if (isPlanks(g[0]) && isPlanks(g[2]) && g[1].isEmpty() && g[3].isEmpty())
            return new Result(ItemType.STICK, 4);
        if (g[0].isEmpty() && g[2].isEmpty() && isPlanks(g[1]) && isPlanks(g[3]))
            return new Result(ItemType.STICK, 4);

        return null;
    }

    private static boolean isPlanks(Inventory.Slot s) {
        return !s.isEmpty() && s.isBlock() && s.block == BlockType.PLANKS;
    }
    private static boolean allBlock(Inventory.Slot[] g, BlockType b) {
        for (Inventory.Slot s : g) if (s.isEmpty() || !s.isBlock() || s.block != b) return false;
        return true;
    }


    // ==================== 3x3 ====================
    public static Result match3x3(Inventory.Slot[] g) {
        // shapeless: 1 WOOD → 4 PLANKS
        int wood = 0;
        for (Inventory.Slot s : g) if (s.isBlock() && s.block == BlockType.WOOD) wood++;
        if (wood == 1) return new Result(BlockType.PLANKS, 4);

        // 4 PLANKS 2x2 (в любом углу 3x3) → CRAFTING_TABLE
        for (int ox = 0; ox < 2; ox++) for (int oy = 0; oy < 2; oy++) {
            if (isPlanks3(g, ox, oy) && isPlanks3(g, ox+1, oy) && isPlanks3(g, ox, oy+1) && isPlanks3(g, ox+1, oy+1)
                && countNonEmpty(g) == 4) {
                return new Result(BlockType.CRAFTING_TABLE, 1);
            }
        }

        // 2 PLANKS горизонтально → PRESSURE_PLATE (любой ряд)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                if (isPlanks3(g, col, row) && isPlanks3(g, col+1, row) && countNonEmpty(g) == 2) {
                    return new Result(BlockType.PRESSURE_PLATE, 1);
                }
            }
        }

        // 2 PLANKS вертикально → 4 STICK (любая колонка)
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 2; row++) {
                if (isPlanks3(g, col, row) && isPlanks3(g, col, row+1) && countNonEmpty(g) == 2) {
                    return new Result(ItemType.STICK, 4);
                }
            }
        }


        // деревянная кирка: 3 доски сверху + 2 палки
        if (countNonEmpty(g) == 5
            && isPlanks3(g, 0, 0) && isPlanks3(g, 1, 0) && isPlanks3(g, 2, 0)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.WOODEN_PICKAXE, 1);
        }

        // каменная кирка: 3 камня сверху + 2 палки
        if (countNonEmpty(g) == 5
            && isBlock3(g, 0, 0, BlockType.STONE) && isBlock3(g, 1, 0, BlockType.STONE) && isBlock3(g, 2, 0, BlockType.STONE)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.STONE_PICKAXE, 1);
        }

        // деревянный топор (слева)
        if (countNonEmpty(g) == 5
            && isPlanks3(g, 0, 0) && isPlanks3(g, 1, 0) && isPlanks3(g, 0, 1)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.WOODEN_AXE, 1);
        }
        // деревянный топор (справа)
        if (countNonEmpty(g) == 5
            && isPlanks3(g, 1, 0) && isPlanks3(g, 2, 0) && isPlanks3(g, 2, 1)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.WOODEN_AXE, 1);
        }
        // каменный топор (слева)
        if (countNonEmpty(g) == 5
            && isBlock3(g, 0, 0, BlockType.STONE) && isBlock3(g, 1, 0, BlockType.STONE) && isBlock3(g, 0, 1, BlockType.STONE)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.STONE_AXE, 1);
        }
        // каменный топор (справа)
        if (countNonEmpty(g) == 5
            && isBlock3(g, 1, 0, BlockType.STONE) && isBlock3(g, 2, 0, BlockType.STONE) && isBlock3(g, 2, 1, BlockType.STONE)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.STONE_AXE, 1);
        }

        // алмазная кирка: 3 алмаза сверху + 2 палки
        if (countNonEmpty(g) == 5
            && isItem3(g, 0, 0, ItemType.DIAMOND) && isItem3(g, 1, 0, ItemType.DIAMOND) && isItem3(g, 2, 0, ItemType.DIAMOND)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.DIAMOND_PICKAXE, 1);
        }
        // алмазный топор (слева)
        if (countNonEmpty(g) == 5
            && isItem3(g, 0, 0, ItemType.DIAMOND) && isItem3(g, 1, 0, ItemType.DIAMOND) && isItem3(g, 0, 1, ItemType.DIAMOND)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.DIAMOND_AXE, 1);
        }
        // алмазная лопата: 1 алмаз + 2 палки
        if (countNonEmpty(g) == 3
            && isItem3(g, 1, 0, ItemType.DIAMOND)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.DIAMOND_SHOVEL, 1);
        }

        // деревянная лопата: 1 доска + 2 палки
        if (countNonEmpty(g) == 3
            && isPlanks3(g, 1, 0)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.WOODEN_SHOVEL, 1);
        }
        // каменная лопата
        if (countNonEmpty(g) == 3
            && isBlock3(g, 1, 0, BlockType.STONE)
            && isItem3(g, 1, 1, ItemType.STICK) && isItem3(g, 1, 2, ItemType.STICK)) {
            return new Result(ItemType.STONE_SHOVEL, 1);
        }

        return null;
    }

    private static boolean isBlock3(Inventory.Slot[] g, int col, int row, BlockType block) {
        Inventory.Slot s = g[row * 3 + col];
        return !s.isEmpty() && s.isBlock() && s.block == block;
    }
    private static boolean isItem3(Inventory.Slot[] g, int col, int row, ItemType item) {
        Inventory.Slot s = g[row * 3 + col];
        return !s.isEmpty() && s.isItem() && s.item == item;
    }
    private static boolean isPlanks3(Inventory.Slot[] g, int col, int row) {
        Inventory.Slot s = g[row * 3 + col];
        return !s.isEmpty() && s.isBlock() && s.block == BlockType.PLANKS;
    }
    private static int countNonEmpty(Inventory.Slot[] g) {
        int n = 0;
        for (Inventory.Slot s : g) if (!s.isEmpty()) n++;
        return n;
    }

    public static void consume(Inventory.Slot[] g) {
        System.out.println("[Consume] grid: " + java.util.Arrays.toString(java.util.Arrays.stream(g).map(s -> s.isEmpty() ? "-" : (s.isBlock() ? s.block.name() : s.item.name()) + "x" + s.count).toArray()));
        for (Inventory.Slot s : g) if (!s.isEmpty()) { s.count--; if (s.count <= 0) s.clear(); }
    }
}
