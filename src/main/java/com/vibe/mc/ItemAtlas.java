package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Атлас иконок предметов: 16x16 на предмет, горизонтально.
 * Порядок = ItemType.id.
 */
public class ItemAtlas {
    public static final int TILE = 16;
    public static final int COUNT = ItemType.values().length;
    public static final int W = TILE * COUNT;
    public static final int H = TILE;
    public static final float TILE_U = 1.0f / COUNT;

    public static int create() {
        byte[] atlas = new byte[W * H * 4];
        for (ItemType t : ItemType.values()) {
            BufferedImage img = loadPng(t.texture);
            int x0 = t.id * TILE;
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    int argb = img.getRGB(x, y);
                    int dst = ((y * W) + (x0 + x)) * 4;
                    atlas[dst + 0] = (byte) ((argb >> 16) & 0xFF);
                    atlas[dst + 1] = (byte) ((argb >> 8)  & 0xFF);
                    atlas[dst + 2] = (byte) ( argb        & 0xFF);
                    atlas[dst + 3] = (byte) ((argb >> 24) & 0xFF);
                }
            }
        }
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
        Logger.info("[ItemAtlas] " + COUNT + " items, " + W + "x" + H);
        return tex;
    }

    private static BufferedImage loadPng(String path) {
        try (InputStream in = ItemAtlas.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new RuntimeException("not found: " + path);
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new RuntimeException("null: " + path);
            return img;
        } catch (Exception e) {
            throw new RuntimeException("failed: " + path, e);
        }
    }
}
