package com.vibe.mc;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import javax.imageio.ImageIO;

/**
 * Рисует ASCII-текст из ванильного ascii.png (128x128, 16x16 символов по 8x8).
 * Каждый символ — квад в NDC. Координаты (x,y) — левый верхний угол в NDC.
 * charSize — размер символа в NDC по высоте.
 */
public class FontRenderer {
    private final Shader shader;
    private final int atlasTex;
    private int vao, vbo;
    private int uColor;

    private static final String VERT = """
        #version 330 core
        layout(location=0) in vec2 aPos;
        layout(location=1) in vec2 aUV;
        out vec2 vUV;
        void main() { vUV = aUV; gl_Position = vec4(aPos, 0.0, 1.0); }
        """;
    private static final String FRAG = """
        #version 330 core
        in vec2 vUV;
        uniform sampler2D uTex;
        uniform vec4 uColor;
        out vec4 FragColor;
        void main() {
            vec4 c = texture(uTex, vUV);
            if (c.a < 0.1) discard;
            FragColor = vec4(uColor.rgb, uColor.a);
        }
        """;

    public FontRenderer() {
        shader = new Shader(VERT, FRAG);
        uColor = shader.uniform("uColor");
        atlasTex = loadAtlas();
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
    }

    private int loadAtlas() {
        try (InputStream in = FontRenderer.class.getClassLoader().getResourceAsStream("textures/ascii.png")) {
            if (in == null) throw new RuntimeException("ascii.png not found");
            BufferedImage img = ImageIO.read(in);
            int w = img.getWidth(), h = img.getHeight();
            ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(w * h * 4);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int i = (y * w + x) * 4;
                buf.put(i + 0, (byte) ((argb >> 16) & 0xFF));
                buf.put(i + 1, (byte) ((argb >> 8) & 0xFF));
                buf.put(i + 2, (byte) (argb & 0xFF));
                buf.put(i + 3, (byte) ((argb >> 24) & 0xFF));
            }
            int tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            org.lwjgl.system.MemoryUtil.memFree(buf);
            System.out.println("[FontRenderer] ascii.png loaded " + w + "x" + h);
            return tex;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ascii.png", e);
        }
    }

    /** Рисует строку. x,y — NDC левый верх. charH — высота символа в NDC. */
    public void drawString(String s, float x, float y, float charH) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        shader.bind();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
        GL20.glUniform1i(shader.uniform("uTex"), 0);
        GL20.glUniform4f(uColor, 1.0f, 1.0f, 1.0f, 1.0f);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * 4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * 4, 2L * 4L);
        GL20.glEnableVertexAttribArray(1);

        float charW = charH * 0.6f; // пропорция шрифта (8x8 символ, но рисуем уже)
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') { cx = x; y -= charH * 1.4f; continue; }
            if (c < 32 || c > 127) c = '?';
            int idx = c;
            float u0 = (idx % 16) / 16f;
            float v0 = (idx / 16) / 16f;
            float u1 = u0 + 1f/16f;
            float v1 = v0 + 1f/16f;
            float[] verts = {
                cx,        y,          u0, v0,
                cx + charW, y,         u1, v0,
                cx + charW, y - charH, u1, v1,
                cx,        y - charH,  u0, v1,
            };
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(verts.length);
                fb.put(verts).flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
            }
            GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
            cx += charW * 0.9f;
        }
        GL30.glBindVertexArray(0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** Ширина строки в NDC при данной высоте символа. */
    public float width(String s, float charH) {
        return s.length() * charH * 0.6f * 0.9f;
    }

    /** То же, но с явным цветом (r,g,b,a). */
    public void drawStringColored(String s, float x, float y, float charH, float cr, float cg, float cb, float ca) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        shader.bind();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
        GL20.glUniform1i(shader.uniform("uTex"), 0);
        GL20.glUniform4f(uColor, cr, cg, cb, ca);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * 4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * 4, 2L * 4L);
        GL20.glEnableVertexAttribArray(1);

        float charW = charH * 0.6f;
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') { cx = x; y -= charH * 1.4f; continue; }
            if (c < 32 || c > 127) c = '?';
            int idx = c;
            float u0 = (idx % 16) / 16f;
            float v0 = (idx / 16) / 16f;
            float u1 = u0 + 1f/16f;
            float v1 = v0 + 1f/16f;
            float[] verts = {
                cx,        y,          u0, v0,
                cx + charW, y,         u1, v0,
                cx + charW, y - charH, u1, v1,
                cx,        y - charH,  u0, v1,
            };
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(verts.length);
                fb.put(verts).flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
            }
            GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
            cx += charW * 0.9f;
        }
        GL30.glBindVertexArray(0);
        GL20.glUniform4f(uColor, 1, 1, 1, 1);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** Рисует строку, поворачивая КАЖДЫЙ символ вокруг его центра на angleRad. */
    public void drawStringColoredRotated(String str, float x, float y, float charH,
                                         float angleRad, float cr, float cg, float cb, float ca) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        shader.bind();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
        GL20.glUniform1i(shader.uniform("uTex"), 0);
        GL20.glUniform4f(uColor, cr, cg, cb, ca);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * 4, 0L);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * 4, 2L * 4L);
        GL20.glEnableVertexAttribArray(1);

        float charW = charH * 0.6f;
        float cos = (float)Math.cos(angleRad), sin = (float)Math.sin(angleRad);
        float cx = x;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\n') { cx = x; y -= charH * 1.4f; continue; }
            if (c < 32 || c > 127) c = '?';
            float u0 = (c % 16) / 16f;
            float v0 = (c / 16) / 16f;
            float u1 = u0 + 1f/16f;
            float v1 = v0 + 1f/16f;
            // центр символа
            float mx = cx + charW/2f;
            float my = y - charH/2f;
            // 4 угла (dx, dy относительно центра)
            float[] dxdy = {
                -charW/2f,  charH/2f,
                 charW/2f,  charH/2f,
                 charW/2f, -charH/2f,
                -charW/2f, -charH/2f
            };
            float[] uvs = { u0, v0, u1, v0, u1, v1, u0, v1 };
            float[] v = new float[16];
            for (int k = 0; k < 4; k++) {
                float rx = dxdy[k*2]   * cos - dxdy[k*2+1] * sin;
                float ry = dxdy[k*2]   * sin + dxdy[k*2+1] * cos;
                v[k*4 + 0] = mx + rx;
                v[k*4 + 1] = my + ry;
                v[k*4 + 2] = uvs[k*2];
                v[k*4 + 3] = uvs[k*2+1];
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                fb.put(v).flip();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);
            }
            GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
            cx += charW * 0.9f;
        }
        GL30.glBindVertexArray(0);
        GL20.glUniform4f(uColor, 1, 1, 1, 1);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
