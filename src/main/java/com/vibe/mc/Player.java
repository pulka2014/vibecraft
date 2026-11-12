package com.vibe.mc;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class Player {
    public final Vector3f position = new Vector3f(8f, 40f, 8f);
    public final Vector3f velocity = new Vector3f();
    public float yaw = -90f;
    public float pitch = 0f;
    public float walkSpeed = 5.5f;
    public float mouseSensitivity = 0.12f;
    public boolean onGround = false;
    public boolean crouching = false;
    public int health = 20;
    public int food = 20;
    public float foodTimer = 0f;

    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    public static final float EYE = 1.62f;
    public static final float GRAVITY = -25f;
    public static final float JUMP = 8.5f;

    private double lastMouseX = 0, lastMouseY = 0;
    private boolean firstMouse = true;

    public void onMouseMove(double x, double y) {
        if (firstMouse) { lastMouseX = x; lastMouseY = y; firstMouse = false; return; }
        float dx = (float) (x - lastMouseX);
        float dy = (float) (y - lastMouseY);
        lastMouseX = x; lastMouseY = y;
        float sens = SettingsHolder.get().mouseSensitivity;
        yaw += dx * sens;
        pitch -= dy * sens;
        if (pitch > 89f) pitch = 89f;
        if (pitch < -89f) pitch = -89f;
    }

    public void update(long window, World world, float dt) {
        Vector3f forward = getForward();
        forward.y = 0;
        forward.normalize();
        Vector3f right = new Vector3f(forward).cross(0f, 1f, 0f).normalize();

        Vector3f wish = new Vector3f();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) wish.add(forward);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) wish.sub(forward);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) wish.add(right);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) wish.sub(right);

        float speed = walkSpeed;
        crouching = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;
        if (crouching) speed *= 0.4f;
        else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) speed *= 1.25f;

        if (wish.lengthSquared() > 0) wish.normalize().mul(speed);
        velocity.x = wish.x;
        velocity.z = wish.z;

        // 1) прыжок — ПЕРВЫМ делом, пока onGround ещё с прошлого кадра
        boolean jumpHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        if (onGround && jumpHeld) {
            velocity.y = JUMP;
            onGround = false;
        }

        // 2) гравитация
        boolean inWater = world.getBlock((int)Math.floor(position.x), (int)Math.floor(position.y + 0.5f), (int)Math.floor(position.z)) == BlockType.WATER;
        if (inWater) {
            velocity.y *= 0.8f; // медленное падение
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) {
                velocity.y = 4f; // всплытие
            }
            if (velocity.y < -2f) velocity.y = -2f; // не тонем слишком быстро
        } else {
            velocity.y += GRAVITY * dt;
        }
        if (velocity.y < -50f) velocity.y = -50f;
        
        // Голод тратится при движении
        if (Math.abs(velocity.x) > 0.1f || Math.abs(velocity.z) > 0.1f) {
            foodTimer += dt;
            if (foodTimer > 5.0f) { food--; foodTimer = 0; if (food < 0) food = 0; }
        }
        
        // Урон от падения
        if (onGround && velocity.y < -10f && !Cheats.godMode) {
            health -= (int)(Math.abs(velocity.y) / 3f);
            if (health < 0) health = 0;
        }

        // 3) движение по осям (moveAxis выставит onGround при приземлении)
        float fallSpeed = velocity.y;
        moveAxis(world, velocity.x * dt, 0, 0);
        moveAxis(world, 0, velocity.y * dt, 0);
        moveAxis(world, 0, 0, velocity.z * dt);
        
        // Урон от падения: только если падал дольше 0.5 сек (высота > 4 блоков)
        if (onGround && fallSpeed < -14f) {
            int dmg = (int)((Math.abs(fallSpeed) - 14f) / 5f) + 1;
            health -= dmg;
            if (health < 0) health = 0;
        }
        
        
        if (position.y < 0f) { position.y = 0f; velocity.y = 0f; onGround = true; }

        // 4) пере-проверка «под ногами есть опора» (включая плиту)
        if (velocity.y <= 0) {
            float hw = WIDTH / 2f;
            float y = position.y - 0.05f;
            boolean under = false;
            for (float x : new float[]{position.x - hw + 0.01f, position.x + hw - 0.01f}) {
                for (float z : new float[]{position.z - hw + 0.01f, position.z + hw - 0.01f}) {
                    if (world.collisionHeight((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z)) > 0) {
                        under = true; break;
                    }
                }
            }
            if (under) onGround = true;
        } else {
            onGround = false;
        }
    }

    private void moveAxis(World world, float dx, float dy, float dz) {
        position.x += dx;
        position.y += dy;
        position.z += dz;

        float minX = position.x - WIDTH / 2, maxX = position.x + WIDTH / 2;
        float minY = position.y,             maxY = position.y + HEIGHT;
        float minZ = position.z - WIDTH / 2, maxZ = position.z + WIDTH / 2;

        int bx0 = (int) Math.floor(minX), bx1 = (int) Math.floor(maxX);
        int by0 = (int) Math.floor(minY), by1 = (int) Math.floor(maxY);
        int bz0 = (int) Math.floor(minZ), bz1 = (int) Math.floor(maxZ);

        for (int bx = bx0; bx <= bx1; bx++) {
            for (int by = by0; by <= by1; by++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    float h = world.collisionHeight(bx, by, bz);
                    if (h <= 0) continue;

                    boolean isPlate = (h < 0.5f);
                    float blockTop = by + h;

                    // плита не блокирует X/Z вообще
                    if (isPlate && dy == 0) continue;
                    // плита не потолок (можно прыгать сквозь)
                    if (isPlate && dy > 0) continue;
                    // если ноги уже выше верха блока — блок не мешает
                    if (by + h <= position.y + 0.0001f) continue;
                    // если голова ниже низа блока — блок не мешает
                    if (by >= position.y + HEIGHT - 0.0001f) continue;

                    if (dx != 0) {
                        if (dx > 0) position.x = bx - WIDTH / 2 - 0.001f;
                        else        position.x = bx + 1 + WIDTH / 2 + 0.001f;
                        velocity.x = 0;
                    }
                    if (dy != 0) {
                        if (dy > 0) {
                            position.y = by - HEIGHT - 0.001f;
                            velocity.y = 0;
                        } else {
                            position.y = blockTop + 0.001f;
                            velocity.y = 0;
                            onGround = true;
                        }
                    }
                    if (dz != 0) {
                        if (dz > 0) position.z = bz - WIDTH / 2 - 0.001f;
                        else        position.z = bz + 1 + WIDTH / 2 + 0.001f;
                        velocity.z = 0;
                    }
                }
            }
        }
        if (dy < 0 && velocity.y == 0) onGround = true;
        if (dy < 0 && velocity.y != 0) onGround = false;
    }

    public Vector3f getForward() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        return new Vector3f(
            (float) (Math.cos(pitchRad) * Math.cos(yawRad)),
            (float) Math.sin(pitchRad),
            (float) (Math.cos(pitchRad) * Math.sin(yawRad))
        ).normalize();
    }

    public Vector3f getEyePos() {
        float eye = EYE - (crouching ? 0.25f : 0f);
        return new Vector3f(position).add(0f, eye, 0f);
    }

    public Matrix4f getViewMatrix() {
        Vector3f eye = getEyePos();
        Vector3f center = new Vector3f(eye).add(getForward());
        return new Matrix4f().lookAt(eye, center, new Vector3f(0f, 1f, 0f));
    }
}
