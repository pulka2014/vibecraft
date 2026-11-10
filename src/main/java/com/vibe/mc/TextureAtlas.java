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
    public static final int COUNT = 21;
    public static final int W = TILE * COUNT;
    public static final int H = TILE;
    public static final float TILE_U = 1.0f / COUNT; // ширина одного тайла в UV

    private static final String[] FILES = {
        "textures/grass_block_top.png",
        "textures/dirt.png",
        "textures/stone.png",
        "textures/sand.png",
        "textures/oak_log.png",
        "textures/oak_log_top.png",
        "textures/oak_planks.png",
        "textures/oak_leaves.png",
        "textures/crafting_table_top.png",
        "textures/crafting_table_side.png",
        "textures/crafting_table_front.png",
        "textures/crafting_table_top.png",  // bottom = top
        "textures/oak_pressure_plate.png",  // tile 12
        "textures/cobblestone.png",         // tile 13
        "textures/coal_ore.png",            // tile 14
        "textures/iron_ore.png",            // tile 15
        "textures/diamond_ore.png",         // tile 16
        "textures/cobblestone.png",         // tile 17 (unused, holder)
        "textures/furnace_top.png",         // tile 18 (top)
        "textures/furnace_side.png",        // tile 19 (side)
        "textures/furnace_front.png"        // tile 20 (front)
    };

    public static int create() {
        System.out.println("[TextureAtlas] start");
        byte[] atlas = new byte[W * H * 4];

        for (int t = 0; t < COUNT; t++) {
            System.out.println("[TextureAtlas] loading " + FILES[t]);
            BufferedImage img = loadPng(FILES[t]);
            if (img.getWidth() != TILE || img.getHeight() != TILE) {
                throw new RuntimeException(FILES[t] + " is " + img.getWidth() + "x" + img.getHeight() + ", expected 16x16");
            }
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

    private static BufferedImage loadPng(String classpath) {
        try (InputStream in = TextureAtlas.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) throw new RuntimeException("Resource not found: " + classpath);
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new RuntimeException("ImageIO returned null for " + classpath);
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
