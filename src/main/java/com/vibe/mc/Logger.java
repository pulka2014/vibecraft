package com.vibe.mc;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Пишет в latest.log + дублирует в консоль.
 * Формат: [HH:mm:ss] [LEVEL] message
 */
public class Logger {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static PrintWriter out;
    private static boolean enabled = false;

    public static void init() {
        try {
            Files.deleteIfExists(Paths.get("latest.log"));
            out = new PrintWriter(new BufferedWriter(new FileWriter("latest.log", true)));
            enabled = true;
            log("INFO", "=== VibeCraft started " + LocalDateTime.now() + " ===");
        } catch (IOException e) {
            System.err.println("[Logger] cannot create latest.log: " + e.getMessage());
        }
    }

    public static void log(String level, String msg) {
        String line = "[" + LocalDateTime.now().format(TIME) + "] [" + level + "] " + msg;
        System.out.println(line);
        if (enabled && out != null) {
            out.println(line);
            out.flush();
        }
    }

    public static void info(String msg) { log("INFO", msg); }
    public static void warn(String msg) { log("WARN", msg); }
    public static void error(String msg) { log("ERROR", msg); }

    public static void close() {
        if (enabled && out != null) {
            log("INFO", "=== VibeCraft stopped ===");
            out.close();
            enabled = false;
        }
    }
}
