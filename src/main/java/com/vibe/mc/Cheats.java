package com.vibe.mc;

public class Cheats {
    public static boolean enabled = false;
    
    public static boolean xray = false;
    public static boolean fly = false;
    public static boolean speed = false;
    public static boolean infiniteBlocks = false;
    public static boolean nightVision = false;
    public static boolean godMode = false;

    public static void reset() {
        enabled = false;
        xray = false; fly = false; speed = false; infiniteBlocks = false; nightVision = false; godMode = false;
        XRay.enabled = false;
    }
}
