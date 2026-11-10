package com.vibe.mc;

/**
 * Прогресс ломания блока. Скорость зависит от инструмента и блока.
 */
public class MiningSystem {
    private int targetX = -1, targetY = -1, targetZ = -1;
    private float progress = 0f; // 0..1
    private BlockType lastBrokenBlock = null;

    /** Начать/продолжить ломание блока. Возвращает true, если блок сломан. */
    public boolean update(float dt, World world, Player player, Inventory.Slot heldSlot) {
        // луч из камеры
        int[] hit = world.raycast(player.getEyePos(), player.getForward(), 6f);
        if (hit == null) { reset(); return false; }

        int bx = hit[0], by = hit[1], bz = hit[2];
        if (bx != targetX || by != targetY || bz != targetZ) {
            reset();
            targetX = bx; targetY = by; targetZ = bz;
        }

        BlockType block = world.getBlock(bx, by, bz);
        if (block == BlockType.AIR) { reset(); return false; }

        // скорость
        float speed = 1f; // рука
        String heldTool = null;
        if (heldSlot != null && heldSlot.isItem() && heldSlot.item.toolType != null) {
            heldTool = heldSlot.item.toolType;
            if (block.toolType != null && block.toolType.equals(heldSlot.item.toolType)) {
                speed = heldSlot.item.speed;
            } else {
                speed = 1f;
            }
        }
        // Жёсткий баланс: правильный инструмент в 5 раз быстрее руки,
        // неправильный — в 4 раза медленнее (как в MC).
        if (heldTool != null && block.toolType != null && block.toolType.equals(heldTool)) {
            speed = Math.max(speed, 4f) * 1.25f;  // точно быстрее руки
        } else if (heldTool != null) {
            speed = 0.25f;                         // неправильный инструмент — хуже руки
        }
        float hardness = block.hardness;
        if (hardness <= 0) { // мгновенно
            world.breakBlock(bx, by, bz);
            reset();
            return true;
        }

        float perSec = speed / hardness;
        progress += perSec * dt;

        if (progress >= 1f) {
            lastBrokenBlock = block;
            Logger.info("[Mining] broke " + block + " held=" + (heldSlot == null ? "null" :
                (heldSlot.isItem() ? heldSlot.item : heldSlot.block)) + " speed=" + String.format("%.2f", speed)
                + " perSec=" + String.format("%.2f", perSec) + " hardness=" + hardness);
            // камень/руда — только киркой (иначе без дропа)
            // дроп: камень/руды — кирка, листья — ножницы, остальное — всегда
            boolean canDrop = true;
            if (block == BlockType.STONE || block == BlockType.COAL_ORE
                || block == BlockType.IRON_ORE || block == BlockType.DIAMOND_ORE) {
                if (heldSlot == null || !heldSlot.isItem() || heldSlot.item.toolType == null
                    || !heldSlot.item.toolType.equals("pickaxe")) {
                    canDrop = false;
                }
            } else if (block == BlockType.LEAVES) {
                // листья — только ножницами; если ножниц нет — без дропа блока, но с шансом 5% яблоко
                boolean hasShears = heldSlot != null && heldSlot.isItem() && heldSlot.item.toolType != null
                    && heldSlot.item.toolType.equals("shears");
                if (!hasShears) canDrop = false;
                if (Math.random() < 0.05) {
                    world.setBlock(bx, by, bz, BlockType.AIR);
                    if (!XRay.enabled) world.rebuildChunkAt(bx, bz);
                    world.particles.spawnBreak(bx, by, bz, BlockType.LEAVES);
                    world.drops.spawnItem(bx, by, bz, ItemType.APPLE);
                    reset();
                    return true;
                }
            }
            // камень дропает булыжник
            if (block == BlockType.STONE && canDrop) {
                world.setBlock(bx, by, bz, BlockType.AIR);
                if (!XRay.enabled) world.rebuildChunkAt(bx, bz);
                world.particles.spawnBreak(bx, by, bz, BlockType.STONE);
                world.drops.spawn(bx, by, bz, BlockType.COBBLESTONE);
            } else if (block == BlockType.COAL_ORE && canDrop) {
                world.setBlock(bx, by, bz, BlockType.AIR);
                if (!XRay.enabled) world.rebuildChunkAt(bx, bz);
                world.particles.spawnBreak(bx, by, bz, BlockType.COAL_ORE);
                world.drops.spawnItem(bx, by, bz, ItemType.COAL);
            } else if (block == BlockType.IRON_ORE && canDrop) {
                world.setBlock(bx, by, bz, BlockType.AIR);
                if (!XRay.enabled) world.rebuildChunkAt(bx, bz);
                world.particles.spawnBreak(bx, by, bz, BlockType.IRON_ORE);
                world.drops.spawnItem(bx, by, bz, ItemType.RAW_IRON);
            } else if (block == BlockType.DIAMOND_ORE && canDrop) {
                world.setBlock(bx, by, bz, BlockType.AIR);
                if (!XRay.enabled) world.rebuildChunkAt(bx, bz);
                world.particles.spawnBreak(bx, by, bz, BlockType.DIAMOND_ORE);
                world.drops.spawnItem(bx, by, bz, ItemType.DIAMOND);
            } else {
                world.breakBlock(bx, by, bz, canDrop);
            }
            reset();
            return true;
        }
        return false;
    }

    public void reset() {
        targetX = -1; targetY = -1; targetZ = -1;
        progress = 0f;
        // lastBrokenBlock НЕ сбрасываем — Main должен прочитать после break
    }

    public BlockType getLastBrokenBlock() { return lastBrokenBlock; }
    public boolean isMining() { return targetX != -1; }
    public float getProgress() { return progress; }
    public int getTargetX() { return targetX; }
    public int getTargetY() { return targetY; }
    public int getTargetZ() { return targetZ; }
}
