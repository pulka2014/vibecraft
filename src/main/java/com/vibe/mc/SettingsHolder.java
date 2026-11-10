package com.vibe.mc;

/** Глобальный доступ к текущим настройкам для World/Player/Main. */
public class SettingsHolder {
    private static Settings current = new Settings();
    public static Settings get() { return current; }
    public static void set(Settings s) { current = s; }
}
