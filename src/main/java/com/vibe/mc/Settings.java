package com.vibe.mc;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Простые настройки: FOV, render distance, mouse sensitivity. */
public class Settings {
    public float fov = 70f;
    public int renderDistance = 120;
    public float mouseSensitivity = 0.12f;
    public int keyCrouch = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
    public int keyDive = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
    public static final String FILE = "settings.properties";

    public static Settings load() {
        Settings s = new Settings();
        Path p = Paths.get(FILE);
        if (!Files.exists(p)) return s;
        try (InputStream in = Files.newInputStream(p)) {
            Properties props = new Properties();
            props.load(in);
            s.fov = Float.parseFloat(props.getProperty("fov", "70"));
            s.renderDistance = Integer.parseInt(props.getProperty("renderDistance", "120"));
            s.mouseSensitivity = Float.parseFloat(props.getProperty("mouseSensitivity", "0.12"));
            s.keyCrouch = Integer.parseInt(props.getProperty("keyCrouch", String.valueOf(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)));
            s.keyDive = Integer.parseInt(props.getProperty("keyDive", String.valueOf(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)));
        } catch (Exception e) { System.out.println("[Settings] load failed: " + e.getMessage()); }
        return s;
    }

    public void save() {
        try {
            Properties props = new Properties();
            props.setProperty("fov", String.valueOf(fov));
            props.setProperty("renderDistance", String.valueOf(renderDistance));
            props.setProperty("mouseSensitivity", String.valueOf(mouseSensitivity));
            props.setProperty("keyCrouch", String.valueOf(keyCrouch));
            props.setProperty("keyDive", String.valueOf(keyDive));
            try (OutputStream out = Files.newOutputStream(Paths.get(FILE))) {
                props.store(out, "VibeCraft settings");
            }
        } catch (IOException e) { System.out.println("[Settings] save failed: " + e.getMessage()); }
    }
}
