package com.vibe.mc;

import java.io.*;
import java.nio.file.*;

public class SaveManager {
    /** Файл определяется слотом; null = старый корневой world.dat. */
    private static Integer currentSlot = null;
    public static void setSlot(Integer slot) { currentSlot = slot; }
    private static java.nio.file.Path path() {
        return (currentSlot == null) ? java.nio.file.Paths.get("world.dat") : Worlds.file(currentSlot);
    }
    private static final int MAGIC = 0x56494245; // "VIBE"
    private static final int VERSION = 4;

    public static class SaveData {
        public byte[][] chunks; // 5x5
        public float px, py, pz;
        public float yaw, pitch;
        public int[][] invSlots = new int[Inventory.TOTAL][3];
        public float timeOfDay = 0.25f;
    }

    public static void save(World world, Player player, TimeOfDay time) throws IOException {
        SaveData d = new SaveData();
        d.chunks = new byte[World.SIZE * World.SIZE][];
        int i = 0;
        for (int cx = 0; cx < World.SIZE; cx++) {
            for (int cz = 0; cz < World.SIZE; cz++) {
                Chunk c = world.chunks.get(new ChunkPos(cx, cz));
                d.chunks[i++] = (c != null) ? c.rawBlocks() : new byte[Chunk.SX * Chunk.SY * Chunk.SZ];
            }
        }
        d.px = player.position.x; d.py = player.position.y; d.pz = player.position.z;
        d.yaw = player.yaw; d.pitch = player.pitch;
        d.invSlots = new int[Inventory.TOTAL][3];
        for (int j = 0; j < Inventory.TOTAL; j++) {
            Inventory.Slot s = world.inventory.slot(j);
            d.invSlots[j][0] = s.isBlock() ? s.block.id : -1;
            d.invSlots[j][1] = s.isItem() ? s.item.id : -1;
            d.invSlots[j][2] = s.count;
        }
        d.timeOfDay = time.getT();

        if (currentSlot != null) Worlds.ensureDir(currentSlot);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path())))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(World.SIZE);
            for (byte[] b : d.chunks) { out.writeInt(b.length); out.write(b); }
            out.writeFloat(d.px); out.writeFloat(d.py); out.writeFloat(d.pz);
            out.writeFloat(d.yaw); out.writeFloat(d.pitch);
            for (int[] row : d.invSlots) { out.writeInt(row[0]); out.writeInt(row[1]); out.writeInt(row[2]); }
            out.writeFloat(d.timeOfDay);
        }
        Logger.info("[Save] saved " + World.SIZE + "x" + World.SIZE + " chunks to " + path());
    }

    public static SaveData load() {
        Path p = path();
        if (!Files.exists(p)) { System.out.println("[Save] no " + p + ", new world"); return null; }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
            if (in.readInt() != MAGIC) { System.out.println("[Save] bad magic"); return null; }
            int ver = in.readInt();
            if (ver != VERSION) { System.out.println("[Save] version mismatch " + ver + " != " + VERSION); return null; }
            int size = in.readInt();
            if (size != World.SIZE) { System.out.println("[Save] world size mismatch " + size); return null; }
            SaveData d = new SaveData();
            d.chunks = new byte[size * size][];
            for (int i = 0; i < d.chunks.length; i++) {
                int len = in.readInt();
                d.chunks[i] = new byte[len];
                in.readFully(d.chunks[i]);
            }
            d.px = in.readFloat(); d.py = in.readFloat(); d.pz = in.readFloat();
            d.yaw = in.readFloat(); d.pitch = in.readFloat();
            for (int j = 0; j < Inventory.TOTAL; j++) {
                d.invSlots[j][0] = in.readInt();
                d.invSlots[j][1] = in.readInt();
                d.invSlots[j][2] = in.readInt();
            }
            d.timeOfDay = in.readFloat();
            Logger.info("[Save] loaded " + d.chunks.length + " chunks from " + p + " (VERSION " + VERSION + ")");
            return d;
        } catch (IOException e) {
            System.out.println("[Save] failed: " + e.getMessage());
            return null;
        }
    }
}
