package com.vibe.mc;

import org.joml.Vector3f;

public class DroppedItem {
    public final Vector3f pos;
    public final Vector3f vel;
    public final BlockType type;
    public final ItemType item;
    public float age;
    public boolean onGround;

    public DroppedItem(Vector3f pos, Vector3f vel, BlockType type) {
        this.pos = pos; this.vel = vel; this.type = type; this.item = null;
    }
    public DroppedItem(Vector3f pos, Vector3f vel, ItemType item) {
        this.pos = pos; this.vel = vel; this.type = null; this.item = item;
    }
}
