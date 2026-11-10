package com.vibe.mc;

import java.io.*;
import java.nio.file.*;

/**
 * Отдельная программа: читает world.dat (VERSION 4) и пишет world.txt.
 * Запуск: java -cp build/classes/java/main com.vibe.mc.WorldDump
 */
public class WorldDump {
    private static final int MAGIC = 0x56494245; // "VIBE"
    private static final int VERSION = 4;

    public static void main(String[] args) throws Exception {
        String inFile = args.length > 0 ? args[0] : "world.dat";
        String outFile = args.length > 1 ? args[1] : "world.txt";

        if (!Files.exists(Paths.get(inFile))) {
            System.err.println("Файл не найден: " + inFile);
            return;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(inFile))));
             PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outFile)))) {

            if (in.readInt() != MAGIC) { System.err.println("Неверный magic"); return; }
            int ver = in.readInt();
            if (ver != VERSION) { System.err.println("Версия не совпадает: " + ver + " != " + VERSION); return; }
            int size = in.readInt();

            out.println("=== VibeCraft world dump ===");
            out.println("version: " + ver);
            out.println("worldSize: " + size + "x" + size);
            out.println();

            // чанки
            int chunkCount = size * size;
            byte[][] chunks = new byte[chunkCount][];
            for (int i = 0; i < chunkCount; i++) {
                int len = in.readInt();
                chunks[i] = new byte[len];
                in.readFully(chunks[i]);
            }

            // игрок
            float px = in.readFloat(), py = in.readFloat(), pz = in.readFloat();
            float yaw = in.readFloat(), pitch = in.readFloat();
            out.println("player: x=" + px + " y=" + py + " z=" + pz + " yaw=" + yaw + " pitch=" + pitch);
            out.println();

            // инвентарь
            int totalSlots = Inventory.TOTAL; // 36
            out.println("inventory:");
            for (int i = 0; i < totalSlots; i++) {
                int bid = in.readInt();
                int iid = in.readInt();
                int count = in.readInt();
                if (count <= 0) continue;
                String name;
                if (bid >= 0) {
                    BlockType b = BlockType.byId((byte) bid);
                    name = "block:" + (b != null ? b.name() : "?" + bid);
                } else if (iid >= 0) {
                    ItemType it = ItemType.byId((byte) iid);
                    name = "item:" + (it != null ? it.name() : "?" + iid);
                } else {
                    name = "?";
                }
                out.println("  slot " + i + ": " + name + " x" + count);
            }
            out.println();

            // время суток
            float timeOfDay = in.readFloat();
            out.println("timeOfDay: " + timeOfDay);
            out.println();

            // чанки — карта по Y
            for (int ci = 0; ci < chunkCount; ci++) {
                int cx = ci / size;
                int cz = ci % size;
                out.println("=== chunk (" + cx + "," + cz + ") ===");
                byte[] blocks = chunks[ci];
                for (int y = Chunk.SY - 1; y >= 0; y--) {
                    // проверка: есть ли хоть один не-AIR в слое
                    boolean any = false;
                    for (int x = 0; x < Chunk.SX && !any; x++)
                        for (int z = 0; z < Chunk.SZ && !any; z++)
                            if (blocks[(y * Chunk.SZ + z) * Chunk.SX + x] != 0) any = true;
                    if (!any) continue;

                    out.println("y=" + y + ":");
                    for (int z = 0; z < Chunk.SZ; z++) {
                        StringBuilder sb = new StringBuilder("  ");
                        for (int x = 0; x < Chunk.SX; x++) {
                            byte id = blocks[(y * Chunk.SZ + z) * Chunk.SX + x];
                            sb.append(symbolFor(id));
                        }
                        out.println(sb);
                    }
                }
                out.println();
            }

            System.out.println("Готово: " + outFile);
        }
    }

    private static char symbolFor(byte id) {
        BlockType b = BlockType.byId(id);
        if (b == null) return '?';
        switch (b) {
            case AIR: return '.';
            case GRASS: return 'G';
            case DIRT: return 'D';
            case STONE: return 'S';
            case SAND: return 's';
            case WOOD: return 'W';
            case PLANKS: return 'P';
            case LEAVES: return 'L';
            case CRAFTING_TABLE: return 'T';
            case PRESSURE_PLATE: return '_';
            case COBBLESTONE: return 'C';
            case COAL_ORE: return 'o';
            case IRON_ORE: return 'I';
            case DIAMOND_ORE: return 'd';
            default: return '?';
        }
    }
}
