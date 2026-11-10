package com.vibe.mc;

import org.joml.Vector3f;

public class Particle {
    public final Vector3f pos;
    public final Vector3f vel;
    public final BlockType type;
    public float life; // секунды
    public final float size;

    public Particle(Vector3f pos, Vector3f vel, BlockType type, float life, float size) {
        this.pos = pos;
        this.vel = vel;
        this.type = type;
        this.life = life;
        this.size = size;
    }
}
