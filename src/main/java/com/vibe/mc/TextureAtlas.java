package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class TextureAtlas {
    public static final int TILE = 16;
    public static final int COUNT = 26;
    public static final int W = TILE * COUNT;
    public static final int H = TILE;
    public static final float TILE_U = 1.0f / COUNT; // ширина одного тайла в UV

    private static final String[] FILES = {
        "textures/grass_block_top.png",     // 0
        "textures/grass_block_side.png",    // 1
        "textures/dirt.png",                // 2
        "textures/stone.png",               // 3
        "textures/sand.png",                // 4
        "textures/oak_log.png",             // 5
        "textures/oak_log_top.png",         // 6
        "textures/oak_planks.png",          // 7
        "textures/oak_leaves.png",          // 8
        "textures/crafting_table_top.png",  // 9
        "textures/crafting_table_side.png", // 10
        "textures/crafting_table_front.png",// 11
        "textures/crafting_table_top.png",  // 12 (bottom)
        "textures/oak_pressure_plate.png",  // 13
        "textures/cobblestone.png",         // 14
        "textures/coal_ore.png",            // 15
        "textures/iron_ore.png",            // 16
        "textures/diamond_ore.png",         // 17
        "textures/furnace_top.png",         // 18
        "textures/furnace_side.png",        // 19
        "textures/furnace_front.png",       // 20
        "textures/water_placeholder.png",
        "textures/wheat.png",
        "textures/iron_block.png",
        "textures/coal_block.png"
    };

    public static int create() {
        System.out.println("[TextureAtlas] start");
        byte[] atlas = new byte[W * H * 4];

        for (int t = 0; t < COUNT; t++) {
            System.out.println("[TextureAtlas] loading " + FILES[t]);
            BufferedImage img = loadPng(FILES[t]);
            int x0 = t * TILE;
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    int argb = img.getRGB(x, y);
                    int dst = ((y * W) + (x0 + x)) * 4;
                    atlas[dst + 0] = (byte) ((argb >> 16) & 0xFF); // R
                    atlas[dst + 1] = (byte) ((argb >> 8)  & 0xFF); // G
                    atlas[dst + 2] = (byte) ( argb        & 0xFF); // B
                    atlas[dst + 3] = (byte) ((argb >> 24) & 0xFF); // A
                }
            }
        }

        System.out.println("[TextureAtlas] uploading to GL");
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        ByteBuffer buf = MemoryUtil.memAlloc(atlas.length);
        buf.put(atlas).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, W, H, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        MemoryUtil.memFree(buf);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        System.out.println("[TextureAtlas] done, tex=" + tex);
        return tex;
    }

    private static BufferedImage fallback() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
            img.setRGB(x, y, ((x / 4 + y / 4) % 2 == 0) ? 0xFFFF00FF : 0xFF000000);
        return img;
    }

    private static BufferedImage loadPng(String classpath) {
        try (InputStream in = TextureAtlas.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) { Logger.warn("[Texture] not found " + classpath + " — fallback magenta"); return fallback(); }
            BufferedImage img = ImageIO.read(in);
            if (img == null) { Logger.warn("[Texture] ImageIO null for " + classpath + " — fallback magenta"); return fallback(); }
            if (img.getWidth() != 16 || img.getHeight() != 16) {
                Logger.warn("[Texture] " + classpath + " wrong size " + img.getWidth() + "x" + img.getHeight() + " — fallback magenta");
                return fallback();
            }
            // DEBUG average color
            long rr=0, gg=0, bb=0; int n=img.getWidth()*img.getHeight();
            for (int y=0;y<img.getHeight();y++) for (int x=0;x<img.getWidth();x++) {
                int c = img.getRGB(x,y);
                rr += (c>>16)&0xFF; gg += (c>>8)&0xFF; bb += c&0xFF;
            }
            Logger.info("[Texture] " + classpath + " OK avgRGB=(" + (rr/n) + "," + (gg/n) + "," + (bb/n) + ")");
            return img;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + classpath, e);
        }
    }
}
