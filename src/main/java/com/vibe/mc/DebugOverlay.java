package com.vibe.mc;

import java.util.ArrayList;
import java.util.List;

public class DebugOverlay {
    private final FontRenderer font;
    private final List<String> lines = new ArrayList<>();

    public DebugOverlay(FontRenderer font) {
        this.font = font;
    }

    public void update(World world, Player player, TimeOfDay time, int fps, int[] hit, float dt) {
        lines.clear();
        lines.add("VibeCraft  FPS: " + fps + "  " + time.phase() + "  sun=" + String.format("%.2f", time.sunBrightness()));
        lines.add(String.format("XYZ: %.2f / %.2f / %.2f", player.position.x, player.position.y, player.position.z));
        lines.add(String.format("Yaw: %.1f  Pitch: %.1f  %s",
                player.yaw, player.pitch,
                player.onGround ? "ground" : "air"));
        lines.add(String.format("Chunk: 0,0  Blocks: %d/%d", 0, Chunk.SX * Chunk.SY * Chunk.SZ));
        lines.add("Looking at: " + (hit == null ? "air" :
                world.getBlock(hit[0], hit[1], hit[2]) + " (" + hit[0] + "," + hit[1] + "," + hit[2] + ")"));
        lines.add("Particles: " + world.particles.count() + "  Drops: " + world.drops.count());
        lines.add("Inventory: " + world.inventory.slotCount() + "/" + Inventory.TOTAL + " slots used");
    }

    public void render() {
        float charH = 0.035f;
        float x = -0.98f;
        float y = 0.95f;
        for (String line : lines) {
            font.drawString(line, x, y, charH);
            y -= charH * 1.4f;
        }
    }
}
