package com.vibe.mc;

/**
 * Время суток: 0.0 = рассвет, 0.25 = полдень, 0.5 = закат, 0.75 = полночь.
 * Цикл ~10 минут (600 сек).
 */
public class TimeOfDay {
    private static final float CYCLE = 600f; // секунд на полный цикл
    private float t = 0.25f; // начинаем в полдень

    public void update(float dt) {
        t += dt / CYCLE;
        if (t >= 1f) t -= 1f;
    }

    /** Яркость солнца: 1.0 днём, 0.15 ночью, плавно. */
    public float sunBrightness() {
        // sin(2πt - π/2) даёт -1 в полночь, +1 в полдень
        float s = (float) Math.sin(2 * Math.PI * (t - 0.25));
        // нормализуем -1..1 → 0.15..1.0
        return 0.08f + (s + 1f) * 0.5f * 0.92f; // 0.08 ночью .. 1.0 днём
    }

    /** Цвет неба (r,g,b). */
    public float[] skyColor() {
        float b = sunBrightness();
        // день: (0.5,0.7,1.0), ночь: (0.02,0.03,0.08)
        return new float[]{
            0.02f + (0.5f - 0.02f) * b,
            0.03f + (0.7f - 0.03f) * b,
            0.08f + (1.0f - 0.08f) * b
        };
    }

    public boolean isDay() { return t > 0.0f && t < 0.5f; }
    public float getT() { return t; }
    public void setT(float v) { t = v - (float)Math.floor(v); }

    public String phase() {
        if (t < 0.05f || t > 0.95f) return "dawn";
        if (t < 0.45f) return "day";
        if (t < 0.55f) return "dusk";
        return "night";
    }
}
