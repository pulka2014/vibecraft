package com.vibe.mc;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Decay листьев: если лист не прикреплён к дереву (BFS от WOOD) — исчезает.
 * Работает раз в секунду по всем чанкам.
 */
public class LeafDecay {
    private float timer = 0f;
    private static final float INTERVAL = 1f; // раз в секунду
    private static final int MAX_DIST = 6;    // радиус прикрепления

    public void update(float dt, World world) {
        timer += dt;
        if (timer < INTERVAL) return;
        timer = 0f;

        for (var entry : world.chunks.entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();
            int baseX = pos.x() * Chunk.SX;
            int baseZ = pos.z() * Chunk.SZ;

            // 1) найти все WOOD в чанке
            Set<Long> attached = new HashSet<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            for (int x = 0; x < Chunk.SX; x++) {
                for (int y = 0; y < Chunk.SY; y++) {
                    for (int z = 0; z < Chunk.SZ; z++) {
                        if (chunk.get(x, y, z) == BlockType.WOOD) {
                            long key = pack(x, y, z);
                            attached.add(key);
                            queue.add(new int[]{x, y, z, 0});
                        }
                    }
                }
            }

            // 2) BFS от WOOD: листья в радиусе MAX_DIST прикреплены
            while (!queue.isEmpty()) {
                int[] cur = queue.poll();
                int cx = cur[0], cy = cur[1], cz = cur[2], dist = cur[3];
                if (dist >= MAX_DIST) continue;
                for (int[] d : new int[][]{{0,1,0},{0,-1,0},{0,0,1},{0,0,-1},{1,0,0},{-1,0,0}}) {
                    int nx = cx + d[0], ny = cy + d[1], nz = cz + d[2];
                    if (nx < 0 || nx >= Chunk.SX || ny < 0 || ny >= Chunk.SY || nz < 0 || nz >= Chunk.SZ) continue;
                    if (chunk.get(nx, ny, nz) == BlockType.LEAVES) {
                        long key = pack(nx, ny, nz);
                        if (attached.add(key)) queue.add(new int[]{nx, ny, nz, dist + 1});
                    }
                }
            }

            // 3) удалить неприкреплённые листья (до 5 за тик)
            int removed = 0;
            final int MAX_PER_TICK = 5;
            for (int x = 0; x < Chunk.SX && removed < MAX_PER_TICK; x++) {
                for (int y = 0; y < Chunk.SY && removed < MAX_PER_TICK; y++) {
                    for (int z = 0; z < Chunk.SZ && removed < MAX_PER_TICK; z++) {
                        if (chunk.get(x, y, z) == BlockType.LEAVES && !attached.contains(pack(x, y, z))) {
                            chunk.set(x, y, z, BlockType.AIR);
                            removed++;
                        }
                    }
                }
            }
            if (removed > 0) chunk.buildMesh();
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) x << 20) | ((long) y << 10) | z;
    }
}
