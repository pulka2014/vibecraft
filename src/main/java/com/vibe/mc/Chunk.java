package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class Chunk {
    public static final int SX = 16, SY = 64, SZ = 16;
    private final byte[] blocks = new byte[SX * SY * SZ];
    private final byte[] rotations = new byte[SX * SY * SZ]; // для FURNACE: 0..3

    private int vao = 0, vbo = 0, ebo = 0;
    private int indexCount = 0;

    public Chunk() {}

    public int idx(int x, int y, int z) { return (y * SZ + z) * SX + x; }
    public byte[] rawBlocks() { return blocks.clone(); }
    public byte[] rawRotations() { return rotations.clone(); }
    public void setRotationsRaw(byte[] r) {
        if (r.length == rotations.length) System.arraycopy(r, 0, rotations, 0, rotations.length);
    }
    public void setRaw(byte[] data) {
        if (data.length != blocks.length) throw new RuntimeException("bad chunk size: " + data.length);
        System.arraycopy(data, 0, blocks, 0, blocks.length);
    }

    public int getRotation(int x, int y, int z) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) return 0;
        return rotations[idx(x, y, z)] & 3;
    }
    public void setRotation(int x, int y, int z, int rot) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) return;
        rotations[idx(x, y, z)] = (byte)(rot & 3);
    }

    public BlockType get(int x, int y, int z) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) return BlockType.AIR;
        return BlockType.byId(blocks[idx(x, y, z)]);
    }

    public void set(int x, int y, int z, BlockType b) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) return;
        blocks[idx(x, y, z)] = b.id;
    }







    /** Тонкая плита 1/16: 6 граней, все рисуются (x-ray fix — не проверяем соседей). */
    private void buildThinBlock(java.util.List<Float> verts, java.util.List<Integer> inds, int x, int y, int z) {
        final float H = 1f / 16f;
        int tile = BlockType.PRESSURE_PLATE.tileTop;
        float tu = TextureAtlas.TILE_U;
        float u0 = tile * tu, u1 = u0 + tu;

        // 6 граней, вершины (x,y,z), UV
        float[][][] faces = {
            // top (y=H): весь тайл
            {{0,H,0, u0,1},{1,H,0, u1,1},{1,H,1, u1,0},{0,H,1, u0,0}},
            // bottom (y=0): весь тайл (перевёрнут)
            {{0,0,1, u0,0},{1,0,1, u1,0},{1,0,0, u1,1},{0,0,0, u0,1}},
            // +Z (front): v от 0 до 1 (весь тайл, сжат по высоте)
            {{0,0,1, u0,1},{1,0,1, u1,1},{1,H,1, u1,0},{0,H,1, u0,0}},
            // -Z (back)
            {{1,0,0, u0,1},{0,0,0, u1,1},{0,H,0, u1,0},{1,H,0, u0,0}},
            // +X (right)
            {{1,0,1, u0,1},{1,0,0, u1,1},{1,H,0, u1,0},{1,H,1, u0,0}},
            // -X (left)
            {{0,0,0, u0,1},{0,0,1, u1,1},{0,H,1, u1,0},{0,H,0, u0,0}},
        };
        for (float[][] f : faces) {
            int base = verts.size() / 6;
            for (int i = 0; i < 4; i++) {
                verts.add(x + f[i][0]);
                verts.add(y + f[i][1]);
                verts.add(z + f[i][2]);
                verts.add(f[i][3]);
                verts.add(f[i][4]);
                verts.add(0f);
            }
            inds.add(base); inds.add(base+1); inds.add(base+2);
            inds.add(base+2); inds.add(base+3); inds.add(base);
        }
    }


    /** Генерирует ландшафт с офсетом (мировые координаты) + деревья. */
    public void generate(long seed, int offsetX, int offsetZ) {
        // карта высот: шум в мировых координатах
        int[][] height = new int[SX][SZ];
        int baseGx = offsetX / 4;
        int baseGz = offsetZ / 4;
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                int gx = baseGx + x / 4;
                int gz = baseGz + z / 4;
                float fx = (x % 4) / 4f, fz = (z % 4) / 4f;
                int h00 = rawNoise(gx,     gz,     seed);
                int h10 = rawNoise(gx + 1, gz,     seed);
                int h01 = rawNoise(gx,     gz + 1, seed);
                int h11 = rawNoise(gx + 1, gz + 1, seed);
                float a = h00 + (h10 - h00) * fx;
                float b = h01 + (h11 - h01) * fx;
                float h = a + (b - a) * fz;
                height[x][z] = 8 + Math.round(h);
            }
        }
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                int h = height[x][z];
                for (int y = 0; y < SY; y++) {
                    BlockType b;
                    if (y == 0) b = BlockType.STONE;
                    else if (y < h - 4) b = BlockType.STONE;
                    else if (y < h) b = BlockType.DIRT;
                    else if (y == h) b = BlockType.GRASS;
                    else b = BlockType.AIR;
                    set(x, y, z, b);
                }
            }
        }
        // деревья
        java.util.Random rng = new java.util.Random(seed * 31L + offsetX * 1000L + offsetZ);
        java.util.List<int[]> trees = new java.util.ArrayList<>();
        for (int x = 1; x < SX - 1; x++) {
            for (int z = 1; z < SZ - 1; z++) {
                if (rng.nextFloat() >= 0.05f) continue;
                int h = height[x][z];
                boolean tooClose = false;
                for (int[] t : trees) {
                    int dx = t[0] - x, dz = t[1] - z;
                    if (dx*dx + dz*dz < 16) { tooClose = true; break; }
                }
                if (tooClose) continue;
                trees.add(new int[]{x, z});
                placeTree(x, z, h, rng);
            }
        }
        Logger.info("[Chunk] (" + (offsetX/16) + "," + (offsetZ/16) + ") trees=" + trees.size());

        // ============ РУДЫ ============
        java.util.Random oreRng = new java.util.Random(seed * 7L + offsetX * 13L + offsetZ * 17L);
        // уголь: 5-50, 40 попыток (чаще)
        for (int i = 0; i < 40; i++) {
            int ox = oreRng.nextInt(SX), oz = oreRng.nextInt(SZ);
            int oy = 5 + oreRng.nextInt(45);
            placeOre(ox, oy, oz, BlockType.COAL_ORE, oreRng, 4 + oreRng.nextInt(5));
        }
        // железо: 5-40, 25 попыток (чаще)
        for (int i = 0; i < 25; i++) {
            int ox = oreRng.nextInt(SX), oz = oreRng.nextInt(SZ);
            int oy = 5 + oreRng.nextInt(35);
            placeOre(ox, oy, oz, BlockType.IRON_ORE, oreRng, 3 + oreRng.nextInt(4));
        }
        // алмазы: 1-16, 8 попыток (чаще)
        for (int i = 0; i < 8; i++) {
            int ox = oreRng.nextInt(SX), oz = oreRng.nextInt(SZ);
            int oy = 1 + oreRng.nextInt(15);
            placeOre(ox, oy, oz, BlockType.DIAMOND_ORE, oreRng, 2 + oreRng.nextInt(3));
        }
    }

    /** Кладёт руду (2-8 блоков рядом, заменяя камень). */
    private void placeOre(int x, int y, int z, BlockType ore, java.util.Random rng, int count) {
        for (int i = 0; i < count; i++) {
            int ox = x + rng.nextInt(3) - 1;
            int oy = y + rng.nextInt(3) - 1;
            int oz = z + rng.nextInt(3) - 1;
            if (inBounds(ox, oy, oz) && get(ox, oy, oz) == BlockType.STONE) {
                set(ox, oy, oz, ore);
            }
        }
    }

    private static int rawNoise(int gx, int gz, long seed) {
        float n1 = valueNoise(gx, gz, seed) * 6f;
        float n2 = valueNoise(gx * 2, gz * 2, seed * 7L) * 3f;
        float n3 = valueNoise(gx * 4, gz * 4, seed * 13L) * 1f;
        return Math.round(n1 + n2 + n3);
    }
    private static float valueNoise(int gx, int gz, long seed) {
        long h = gx * 374761393L + gz * 668265263L + seed * 1274126177L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (float) (Math.abs(h) % 1000) / 1000f;
    }

    /** Дерево: ствол 4-6 + крона (3 слоя + верхушка). */
    private void placeTree(int x, int z, int groundY, java.util.Random rng) {
        int trunkH = 4 + rng.nextInt(3);
        for (int i = 1; i <= trunkH; i++) {
            if (inBounds(x, groundY + i, z)) set(x, groundY + i, z, BlockType.WOOD);
        }
        int topY = groundY + trunkH;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (Math.abs(dx) + Math.abs(dz) <= 1 && inBounds(x+dx, topY-2, z+dz))
                set(x+dx, topY-2, z+dz, BlockType.LEAVES);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (inBounds(x+dx, topY-1, z+dz)) set(x+dx, topY-1, z+dz, BlockType.LEAVES);
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (Math.abs(dx) + Math.abs(dz) <= 1 && inBounds(x+dx, topY, z+dz))
                set(x+dx, topY, z+dz, BlockType.LEAVES);
        }
        if (inBounds(x, topY+1, z)) set(x, topY+1, z, BlockType.LEAVES);
    }
    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < SX && y >= 0 && y < SY && z >= 0 && z < SZ;
    }

    public void buildMesh() {
        List<Float> verts = new ArrayList<>();
        List<Integer> inds = new ArrayList<>();

        // направления: +Y, -Y, +Z, -Z, +X, -X
        int[][] dirs = {
            {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}, {1,0,0}, {-1,0,0}
        };
        // для каждой стороны — 4 вершины (по часовой, чтобы cull face работал)
        float[][][] faceVerts = {
            // top (+Y)
            {{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
            // bottom (-Y)
            {{0,0,1},{0,0,0},{1,0,0},{1,0,1}},
            // +Z (front)
            {{0,0,1},{1,0,1},{1,1,1},{0,1,1}},
            // -Z (back)
            {{1,0,0},{0,0,0},{0,1,0},{1,1,0}},
            // +X (right)
            {{1,0,1},{1,0,0},{1,1,0},{1,1,1}},
            // -X (left)
            {{0,0,0},{0,0,1},{0,1,1},{0,1,0}},
        };

        for (int x = 0; x < SX; x++) {
            for (int y = 0; y < SY; y++) {
                for (int z = 0; z < SZ; z++) {
                    BlockType b = get(x, y, z);
                    if (b == BlockType.AIR) continue;
                    // X-ray: только руды
                    if (XRay.enabled) {
                        if (b != BlockType.COAL_ORE && b != BlockType.IRON_ORE && b != BlockType.DIAMOND_ORE) continue;
                    }
                    // плита: рисуем только 1/16 высоты
                    if (b == BlockType.PRESSURE_PLATE) {
                        buildThinBlock(verts, inds, x, y, z);
                        continue;
                    }


                    for (int f = 0; f < 6; f++) {
                        int nx = x + dirs[f][0];
                        int ny = y + dirs[f][1];
                        int nz = z + dirs[f][2];
                        BlockType neighbor = get(nx, ny, nz);
                        // плита — прозрачная для граней соседа (x-ray fix)
                        if (neighbor != BlockType.AIR && neighbor != BlockType.PRESSURE_PLATE) continue;

                        int tile;
                        if (b == BlockType.FURNACE) {
                            int rot = getRotation(x, y, z);
                            // front-грань у нас face == 2 (dir +Z). Логика:
                            // rot 0 = front на +Z, 1 = +X, 2 = -Z, 3 = -X.
                            int frontFace = switch (rot) {
                                case 0 -> 2;   // +Z
                                case 1 -> 4;   // +X
                                case 2 -> 3;   // -Z
                                default -> 5;  // -X
                            };
                            if (f == frontFace) tile = b.tileFront;
                            else if (f == 0) tile = b.tileTop;      // верх
                            else if (f == 1) tile = b.tileTop;      // низ тоже top (в MC низ печи = top)
                            else tile = b.tileSide;
                        } else {
                            tile = b.tileFor(f);
                        }
                        float u0 = tile * TextureAtlas.TILE_U;
                        float u1 = u0 + TextureAtlas.TILE_U;
                        float v0 = 1.0f, v1 = 0.0f;

                        int baseIdx = verts.size() / 6;
                        float[][] fv = faceVerts[f];
                        float[][] uv = {{u0,v0},{u1,v0},{u1,v1},{u0,v1}};
                        float tint = 0f;
                        if (f == 0 && b == BlockType.GRASS) tint = 1f;
                        else if (b == BlockType.LEAVES) tint = 2f;
                        if (f == 0 && b == BlockType.GRASS) tint = 1f;
                        else if (b == BlockType.LEAVES) tint = 2f;

                        for (int i = 0; i < 4; i++) {
                            verts.add(x + fv[i][0]);
                            verts.add(y + fv[i][1]);
                            verts.add(z + fv[i][2]);
                            verts.add(uv[i][0]);
                            verts.add(uv[i][1]);
                            verts.add(tint);
                        }
                        inds.add(baseIdx);     inds.add(baseIdx + 1); inds.add(baseIdx + 2);
                        inds.add(baseIdx + 2); inds.add(baseIdx + 3); inds.add(baseIdx);
                    }
                }
            }
        }

        indexCount = inds.size();
        if (indexCount == 0) return; // пустой чанк — не трогаем VBO/VAO

        if (vao == 0) vao = GL30.glGenVertexArrays();
        if (vbo == 0) vbo = GL15.glGenBuffers();
        if (ebo == 0) ebo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        // memAlloc вместо MemoryStack — меш большой, стек 64КБ не влезает
        FloatBuffer fb = MemoryUtil.memAllocFloat(verts.size());
        for (int i = 0; i < verts.size(); i++) fb.put(verts.get(i));
        fb.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(fb);

        IntBuffer ib = MemoryUtil.memAllocInt(inds.size());
        for (int i = 0; i < inds.size(); i++) ib.put(inds.get(i));
        ib.flip();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);

        int stride = 6 * 4;
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3L * 4L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, stride, 5L * 4L);
        GL20.glEnableVertexAttribArray(2);
        GL30.glBindVertexArray(0);
        int err = org.lwjgl.opengl.GL11.glGetError();
        if (err != org.lwjgl.opengl.GL11.GL_NO_ERROR) {
            Logger.error("[Chunk] GL error after buildMesh: 0x" + Integer.toHexString(err)
                + " verts=" + (verts.size() / 6) + " idx=" + indexCount);
        }

        Logger.info("[Chunk] mesh " + (verts.size() / 6) + " verts, " + indexCount + " idx");
    }

    public void render() {
        if (indexCount == 0) return;
        if (vao == 0 || vbo == 0 || ebo == 0) return;
        GL30.glBindVertexArray(vao);
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(0);
    }
}
