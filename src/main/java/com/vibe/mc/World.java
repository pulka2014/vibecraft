package com.vibe.mc;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public class World {
    public static final int SIZE = 5;
    public final Map<ChunkPos, Chunk> chunks = new HashMap<>();
    public final ParticleSystem particles = new ParticleSystem();
    public final ItemDropSystem drops = new ItemDropSystem();
    public final Inventory inventory = new Inventory();
    public final int seed;
    public final java.util.Set<ChunkPos> pendingRebuild = new java.util.HashSet<>();

    public World(int seed) {
        this.seed = seed;
        for (int cx = 0; cx < SIZE; cx++) {
            for (int cz = 0; cz < SIZE; cz++) {
                Chunk c = new Chunk();
                c.generate(seed + cx * 1000L + cz * 31L, cx * Chunk.SX, cz * Chunk.SZ);
                chunks.put(new ChunkPos(cx, cz), c);
            }
        }
        for (Chunk c : chunks.values()) c.buildMesh();
        Logger.info("[World] generated " + chunks.size() + " chunks (" + SIZE + "x" + SIZE + ")");
    }

    public static World empty(int seed) {
        World w = new World(seed, true);
        return w;
    }

    private World(int seed, boolean empty) {
        this.seed = seed;
        if (!empty) return;
        for (int cx = 0; cx < SIZE; cx++)
            for (int cz = 0; cz < SIZE; cz++)
                chunks.put(new ChunkPos(cx, cz), new Chunk());
    }

    public void rebuildAll() { for (Chunk c : chunks.values()) c.buildMesh(); }

    public BlockType getBlock(int wx, int wy, int wz) {
        if (wx < 0 || wz < 0 || wy < 0 || wy >= Chunk.SY) return BlockType.AIR;
        int cx = Math.floorDiv(wx, Chunk.SX), cz = Math.floorDiv(wz, Chunk.SZ);
        Chunk c = chunks.get(new ChunkPos(cx, cz));
        if (c == null) return BlockType.AIR;
        return c.get(Math.floorMod(wx, Chunk.SX), wy, Math.floorMod(wz, Chunk.SZ));
    }

    public void setBlock(int wx, int wy, int wz, BlockType t) {
        if (wx < 0 || wz < 0 || wy < 0 || wy >= Chunk.SY) return;
        int cx = Math.floorDiv(wx, Chunk.SX), cz = Math.floorDiv(wz, Chunk.SZ);
        Chunk c = chunks.get(new ChunkPos(cx, cz));
        if (c == null) return;
        c.set(Math.floorMod(wx, Chunk.SX), wy, Math.floorMod(wz, Chunk.SZ), t);
    }

    public boolean isSolid(int wx, int wy, int wz) {
        return getBlock(wx, wy, wz) != BlockType.AIR;
    }

    /** Высота коллизии блока: 1.0 для обычных, 1/16 для плиты. */
    public float collisionHeight(int wx, int wy, int wz) {
        BlockType b = getBlock(wx, wy, wz);
        if (b == BlockType.AIR) return 0f;
        if (b == BlockType.PRESSURE_PLATE) return 1f / 16f;
        return 1f;
    }

    public BlockType blockAt(float x, float y, float z) {
        return getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public BlockType blockUnderPlayer(Player player) {
        float hw = Player.WIDTH / 2f;
        float[] xs = { player.position.x - hw + 0.01f, player.position.x + hw - 0.01f };
        float[] zs = { player.position.z - hw + 0.01f, player.position.z + hw - 0.01f };
        float[] ys = { player.position.y - 0.01f, player.position.y - 0.05f, player.position.y - 0.1f };
        for (float y : ys) {
            for (float x : xs) {
                for (float z : zs) {
                    BlockType b = blockAt(x, y, z);
                    if (b != BlockType.AIR) return b;
                }
            }
        }
        return BlockType.AIR;
    }

    // ==================== raycast ====================
    public int[] raycast(Vector3f origin, Vector3f dir, float maxDist) {
        float ox = origin.x, oy = origin.y, oz = origin.z;
        float dx = dir.x, dy = dir.y, dz = dir.z;

        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);

        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        float tDeltaX = dx == 0 ? Float.MAX_VALUE : Math.abs(1f / dx);
        float tDeltaY = dy == 0 ? Float.MAX_VALUE : Math.abs(1f / dy);
        float tDeltaZ = dz == 0 ? Float.MAX_VALUE : Math.abs(1f / dz);

        float tMaxX = dx == 0 ? Float.MAX_VALUE : ((dx > 0 ? (x + 1 - ox) : (ox - x)) * tDeltaX);
        float tMaxY = dy == 0 ? Float.MAX_VALUE : ((dy > 0 ? (y + 1 - oy) : (oy - y)) * tDeltaY);
        float tMaxZ = dz == 0 ? Float.MAX_VALUE : ((dz > 0 ? (z + 1 - oz) : (oz - z)) * tDeltaZ);

        int nx = 0, ny = 0, nz = 0;
        float t = 0;
        while (t <= maxDist) {
            BlockType b = getBlock(x, y, z);
            if (b != BlockType.AIR) {
                if (b == BlockType.PRESSURE_PLATE) {
                    float[] r = rayAABB(ox, oy, oz, dx, dy, dz, x, y, z, 1f/16f, maxDist);
                    if (r != null) {
                        return new int[]{x, y, z, (int)r[0], (int)r[1], (int)r[2]};
                    }
                } else {
                    return new int[]{x, y, z, nx, ny, nz};
                }
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX; t = tMaxX; tMaxX += tDeltaX; nx = -stepX; ny = 0; nz = 0;
            } else if (tMaxY < tMaxZ) {
                y += stepY; t = tMaxY; tMaxY += tDeltaY; nx = 0; ny = -stepY; nz = 0;
            } else {
                z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; nx = 0; ny = 0; nz = -stepZ;
            }
        }
        return null;
    }

    private float[] rayAABB(float ox, float oy, float oz, float dx, float dy, float dz,
                            int bx, int by, int bz, float h, float maxDist) {
        float minX = bx, maxX = bx + 1;
        float minY = by, maxY = by + h;
        float minZ = bz, maxZ = bz + 1;

        float tmin = 0, tmax = maxDist;
        int nx = 0, ny = 0, nz = 0;

        if (Math.abs(dx) < 1e-8f) {
            if (ox < minX || ox > maxX) return null;
        } else {
            float t1 = (minX - ox) / dx, t2 = (maxX - ox) / dx;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) { tmin = t1; nx = dx > 0 ? -1 : 1; ny = 0; nz = 0; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return null;
        }
        if (Math.abs(dy) < 1e-8f) {
            if (oy < minY || oy > maxY) return null;
        } else {
            float t1 = (minY - oy) / dy, t2 = (maxY - oy) / dy;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) { tmin = t1; nx = 0; ny = dy > 0 ? -1 : 1; nz = 0; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return null;
        }
        if (Math.abs(dz) < 1e-8f) {
            if (oz < minZ || oz > maxZ) return null;
        } else {
            float t1 = (minZ - oz) / dz, t2 = (maxZ - oz) / dz;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) { tmin = t1; nx = 0; ny = 0; nz = dz > 0 ? -1 : 1; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return null;
        }
        if (tmin < 0) return null;
        return new float[]{nx, ny, nz, tmin};
    }

    // ==================== block changes ====================
    public void breakBlock(int x, int y, int z) {
        breakBlock(x, y, z, true);
    }
    public void breakBlock(int x, int y, int z, boolean canDrop) {
        BlockType type = getBlock(x, y, z);
        if (type == BlockType.AIR) return;
        setBlock(x, y, z, BlockType.AIR);
        rebuildChunkAt(x, z);
        particles.spawnBreak(x, y, z, type);
        if (canDrop) drops.spawn(x, y, z, type);
    }

    public void placeBlock(int x, int y, int z, BlockType type, Vector3f playerFeet) {
        placeBlock(x, y, z, type, playerFeet, 0, 0, 1);
    }

    /**
     * @param nx,ny,nz — нормаль к грани, на которую смотрит игрок.
     * Для FURNACE ставим поворот так, чтобы его «лицо» смотрело на игрока.
     */
    public void placeBlock(int x, int y, int z, BlockType type, Vector3f playerFeet,
                           int nx, int ny, int nz) {
        if (isSolid(x, y, z)) return;
        float pw = Player.WIDTH / 2f;
        float pMinX = playerFeet.x - pw, pMaxX = playerFeet.x + pw;
        float pMinY = playerFeet.y, pMaxY = playerFeet.y + Player.HEIGHT;
        float pMinZ = playerFeet.z - pw, pMaxZ = playerFeet.z + pw;
        if (pMaxX > x && pMinX < x + 1 && pMaxY > y && pMinY < y + 1 && pMaxZ > z && pMinZ < z + 1) return;
        setBlock(x, y, z, type);
        if (type == BlockType.FURNACE) {
            int fx = nx, fz = nz;
            // Если нормаль вертикальная (поставили на землю/потолок) — берём направление от блока к игроку
            if (fx == 0 && fz == 0) {
                float dxp = playerFeet.x - (x + 0.5f);
                float dzp = playerFeet.z - (z + 0.5f);
                if (Math.abs(dxp) > Math.abs(dzp)) {
                    fx = dxp > 0 ? 1 : -1;
                } else {
                    fz = dzp > 0 ? 1 : -1;
                }
            }
            int rot;
            if (fx > 0) rot = 1;       // front на +X
            else if (fx < 0) rot = 3;  // front на -X
            else if (fz > 0) rot = 0;  // front на +Z
            else rot = 2;              // front на -Z
            int cx = Math.floorDiv(x, Chunk.SX), cz = Math.floorDiv(z, Chunk.SZ);
            Chunk c = chunks.get(new ChunkPos(cx, cz));
            if (c != null) c.setRotation(Math.floorMod(x, Chunk.SX), y, Math.floorMod(z, Chunk.SZ), rot);
        }
        rebuildChunkAt(x, z);
    }

    public void rebuildChunkAt(int wx, int wz) {
        int cx = Math.floorDiv(wx, Chunk.SX), cz = Math.floorDiv(wz, Chunk.SZ);
        pendingRebuild.add(new ChunkPos(cx, cz));
    }

    /** Вызывается из основного цикла (до рендера) — пересобирает все отложенные меши. */
    public void flushRebuilds() {
        if (pendingRebuild.isEmpty()) return;
        for (ChunkPos pos : pendingRebuild) {
            Chunk c = chunks.get(pos);
            if (c != null) c.buildMesh();
        }
        pendingRebuild.clear();
    }

    public void render(Matrix4f proj, Matrix4f view, int uProj, int uView, int uModel,
                       int uTex, int atlasTex, int uSun, float sun, int uFogNear, int uFogFar,
                       float fogR, float fogG, float fogB, Vector3f cam) {
        // uFogNear/uFogFar в шейдере не используются — параметры оставлены для совместимости вызова
        for (Map.Entry<ChunkPos, Chunk> e : chunks.entrySet()) {
            ChunkPos p = e.getKey();
            Chunk c = e.getValue();
            float cx = (p.x() + 0.5f) * Chunk.SX;
            float cz = (p.z() + 0.5f) * Chunk.SZ;
            float dist = (float) Math.sqrt((cx - cam.x)*(cx - cam.x) + (cz - cam.z)*(cz - cam.z));
            if (dist > com.vibe.mc.SettingsHolder.get().renderDistance) continue;
            Matrix4f model = new Matrix4f().translate(p.x() * Chunk.SX, 0f, p.z() * Chunk.SZ);
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                java.nio.FloatBuffer pb = stack.mallocFloat(16);
                model.get(pb);
                org.lwjgl.opengl.GL20.glUniformMatrix4fv(uModel, false, pb);
            }
            c.render();
        }
    }
}
