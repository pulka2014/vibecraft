package com.vibe.mc;

import java.io.IOException;
import java.nio.file.*;

/** 8 слотов миров в ~/.local/share/vibecraft/worlds/<N>/world.dat */
public class Worlds {
    public static final int MAX = 8;
    public static Path root() {
        return Paths.get(System.getProperty("user.home"), ".local", "share", "vibecraft", "worlds");
    }
    public static Path dir(int slot) { return root().resolve(String.valueOf(slot)); }
    public static Path file(int slot) { return dir(slot).resolve("world.dat"); }
    public static boolean exists(int slot) { return Files.exists(file(slot)); }
    public static void ensureDir(int slot) throws IOException { Files.createDirectories(dir(slot)); }
    public static long size(int slot) {
        try { return exists(slot) ? Files.size(file(slot)) : 0L; } catch (IOException e) { return 0L; }
    }
    public static void delete(int slot) {
        try {
            Path dir = dir(slot);
            if (Files.exists(dir)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            }
        } catch (IOException ignored) {}
    }
    public static String label(int slot) {
        if (slot < 1 || slot > MAX) return "?";
        if (!exists(slot)) return "Slot " + slot + ": empty";
        return "Slot " + slot + ": " + (size(slot) / 1024) + " KB";
    }
}
