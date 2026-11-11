package com.vibe.mc;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class Main {

    /** Всё «игровое состояние» — final-объект, который захватывают лямбды. */
    private static final class GameState {
        World world;
        Player player;
        TimeOfDay time;
        Hotbar hotbar;
        InventoryScreen invScreen;
        CraftingScreen craftScreen;
        FurnaceScreen furnaceScreen;
        LoadingScreen loadingScreen;
        boolean loadingScreenActive = false;
        Integer activeSlot = null;
    }

    private static double stepTimer = 0;
    private static final Vector3f lastPos = new Vector3f();
    private static boolean lastPosInit = false;

                private static final String VERT = """
        #version 330 core
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec2 aUV;
        layout(location=2) in float aTint;
        uniform mat4 uProj;
        uniform mat4 uView;
        uniform mat4 uModel;
        out vec2 vUV;
        out float vTint;
        void main() {
            vUV = aUV;
            vTint = aTint;
            gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);
        }
        """;

                private static final String FRAG = """
        #version 330 core
        in vec2 vUV;
        in float vTint;
        uniform sampler2D uTex;
        uniform float uSun;
        out vec4 FragColor;
        void main() {
            vec4 c = texture(uTex, vUV);
            if (vTint > 1.5) c.rgb *= vec3(0.25, 0.65, 0.20);
            else if (vTint > 0.5) c.rgb *= vec3(0.35, 0.85, 0.30);
            c.rgb *= uSun;
            FragColor = c;
        }
        """;

    private static final class LoadedWorld {
        final World world; final SaveManager.SaveData save;
        LoadedWorld(World world, SaveManager.SaveData save) { this.world = world; this.save = save; }
    }

    private static LoadedWorld loadOrCreateWorld(Integer slot) {
        SaveManager.setSlot(slot);
        SaveManager.SaveData save = SaveManager.load();
        if (save != null) {
            World w = World.empty(12345);
            int i = 0;
            for (int cx = 0; cx < World.SIZE; cx++)
                for (int cz = 0; cz < World.SIZE; cz++) {
                    Chunk c = w.chunks.get(new ChunkPos(cx, cz));
                    if (c != null && i < save.chunks.length) {
                        c.setRaw(save.chunks[i]);
                        if (save.rotations != null && i < save.rotations.length) c.setRotationsRaw(save.rotations[i]);
                    }
                    i++;
                }
            // w.rebuildAll() — вызывается в главном потоке (GL), см. enterWorld()
            return new LoadedWorld(w, save);
        } else {
            World w = new World((int)(Math.random() * Integer.MAX_VALUE));
            return new LoadedWorld(w, null);
        }
    }

    private static void applySaveTo(GameState gs, SaveManager.SaveData save) {
        // Проверяем, нужно ли спавнить в центре (если save == null или px, pz почти нули)
        boolean spawnCenter = (save == null) || (Math.abs(save.px) < 0.1f && Math.abs(save.pz) < 0.1f);
        System.out.println("[Main] applySaveTo: save=" + (save != null) + " px=" + (save != null ? save.px : "null") + " pz=" + (save != null ? save.pz : "null") + " spawnCenter=" + spawnCenter);
        if (spawnCenter) {
            float cx = World.SIZE * Chunk.SX / 2f;
            float cz = World.SIZE * Chunk.SZ / 2f;
            float spawnY = 64f;
            for (int y = Chunk.SY - 1; y >= 0; y--) {
                if (gs.world.getBlock((int)cx, y, (int)cz) != BlockType.AIR) {
                    spawnY = y + 2f;
                    break;
                }
            }
            gs.player.position.set(cx + 0.5f, spawnY, cz + 0.5f);
            gs.player.yaw = 0f;
            gs.player.pitch = 0f;
            System.out.println("[Main] spawn in center: " + gs.player.position);
        } else {
            gs.player.position.set(save.px, save.py, save.pz);
            gs.player.yaw = save.yaw;
            gs.player.pitch = save.pitch;
        }
        if (save == null) return;
        for (int i = 0; i < Inventory.TOTAL; i++) {
            int bid = save.invSlots[i][0];
            int iid = save.invSlots[i][1];
            int c = save.invSlots[i][2];
            if (c <= 0) continue;
            Inventory.Slot s = gs.world.inventory.slot(i);
            if (bid >= 0) { s.block = BlockType.byId((byte)bid); s.count = c; }
            else if (iid >= 0) { s.item = ItemType.byId((byte)iid); s.count = c; }
        }
        gs.time.setT(save.timeOfDay);
    }

    public static void main(String[] args) {
        Logger.init();
        Settings settings = Settings.load();
        SettingsHolder.set(settings);

        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW init failed");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_RESIZABLE == 0 ? 0 : GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        long window = GLFW.glfwCreateWindow(1280, 720, "VibeCraft", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) { GLFW.glfwTerminate(); throw new RuntimeException("window"); }
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);
        GL.createCapabilities();

        int tex = TextureAtlas.create();
        int itemTex = ItemAtlas.create();

        Shader shader = new Shader(VERT, FRAG);
        int uProj  = shader.uniform("uProj");
        int uView  = shader.uniform("uView");
        int uModel = shader.uniform("uModel");
        int uTex   = shader.uniform("uTex");
        int uSun   = shader.uniform("uSun");

        GL11.glEnable(GL11.GL_DEPTH_TEST);

        FontRenderer font = new FontRenderer();
        SoundSystem sound = new SoundSystem();
        PauseMenu pause = new PauseMenu(font);
        MainMenu mainMenu = new MainMenu(font);
        CrackOverlay crack = new CrackOverlay();
        Crosshair crosshair = new Crosshair();
        WorldHighlight highlight = new WorldHighlight();
        MiningSystem mining = new MiningSystem();
        LeafDecay leafDecay = new LeafDecay();
        DebugOverlay debug = new DebugOverlay(font);
        boolean[] showDebug = {false};

        // стартовое состояние: пустой мир-заглушка, активируется по Play
        GameState gs = new GameState();
        gs.world = World.empty(12345);
        gs.world.drops.setItemAtlas(itemTex);
        gs.player = new Player();
                    gs.player.position.set(World.SIZE * Chunk.SX / 2f + 0.5f, 70f, World.SIZE * Chunk.SZ / 2f + 0.5f);
                    gs.player.yaw = 0f;
                    gs.player.pitch = 0f;
        gs.time = new TimeOfDay();
        gs.hotbar = new Hotbar(tex, itemTex, gs.world.inventory, font);
        gs.invScreen = new InventoryScreen(tex, itemTex, gs.world.inventory, font);
        gs.craftScreen = new CraftingScreen(tex, itemTex, gs.world.inventory, font);
        gs.furnaceScreen = new FurnaceScreen(tex, itemTex, gs.world.inventory, font);
        gs.loadingScreen = new LoadingScreen(font);

        final long win = window;
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);

        GLFW.glfwSetCursorPosCallback(window, (w, x, y) -> {
            int[] ww = new int[1], hh = new int[1];
            GLFW.glfwGetWindowSize(w, ww, hh);
            if (ww[0] <= 0 || hh[0] <= 0) return;
            float nx = (float)(x / ww[0] * 2.0 - 1.0);
            float ny = (float)(1.0 - y / hh[0] * 2.0);
            mainMenu.setMouse(nx, ny);
            pause.setMouse(nx, ny);
            gs.invScreen.setMouse(nx, ny);
            gs.craftScreen.setMouse(nx, ny);
            gs.furnaceScreen.setMouse(nx, ny);
            if (!mainMenu.isOpen() && !pause.isOpen() && !gs.invScreen.isOpen() && !gs.craftScreen.isOpen()) {
                gs.player.onMouseMove(x, y);
            }
        });

        GLFW.glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            if (action != GLFW.GLFW_PRESS) return;

            if (mainMenu.isOpen()) {
                if (key == GLFW.GLFW_KEY_ESCAPE) GLFW.glfwSetWindowShouldClose(win, true);
                return;
            }

            if (key == GLFW.GLFW_KEY_E && !pause.isOpen()) {
                if (gs.craftScreen.isOpen()) {
                    gs.craftScreen.close();
                    GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                } else if (gs.furnaceScreen.isOpen()) {
                    gs.furnaceScreen.close();
                    GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                } else if (gs.invScreen.isOpen()) {
                    gs.invScreen.close();
                    GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                } else {
                    gs.invScreen.open();
                    GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                }
                return;
            }

            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (pause.isJustOpened()) return;
                if (pause.isOpen()) {
                    pause.close();
                    GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                } else {
                    pause.open();
                    GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                }
                return;
            }

            if (pause.isOpen() && key == GLFW.GLFW_KEY_Q) {
                try { SaveManager.save(gs.world, gs.player, gs.time); } catch (Exception e) {}
                pause.close();
                mainMenu.setOpen(true);
                GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                return;
            }

            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
                gs.hotbar.select(key - GLFW.GLFW_KEY_1);
                return;
            }

            if (key == GLFW.GLFW_KEY_F8) {
                XRay.enabled = !XRay.enabled;
                System.out.println("[XRay] " + (XRay.enabled ? "ON" : "OFF"));
                for (Chunk c : gs.world.chunks.values()) c.buildMesh();
                return;
            }
            if (key == GLFW.GLFW_KEY_F3) {
                showDebug[0] = !showDebug[0];
                System.out.println("[Main] debug " + (showDebug[0] ? "on" : "off"));
                return;
            }
            if (key == GLFW.GLFW_KEY_F5) {
                try { SaveManager.save(gs.world, gs.player, gs.time); }
                catch (Exception e) { System.out.println("[Save] error: " + e.getMessage()); }
                return;
            }
        });

        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (mainMenu.isOpen()) {
                if (action == GLFW.GLFW_PRESS && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) mainMenu.click(true);
                return;
            }
            if (gs.invScreen.isOpen()) {
                if (action == GLFW.GLFW_PRESS && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT))
                    gs.invScreen.click(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                return;
            }
            if (gs.craftScreen.isOpen()) {
                if (action == GLFW.GLFW_PRESS && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT))
                    gs.craftScreen.click(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                return;
            }
            if (gs.furnaceScreen.isOpen()) {
                if (action == GLFW.GLFW_PRESS && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT))
                    gs.furnaceScreen.click(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                return;
            }
            if (pause.isOpen()) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                    String clicked = pause.click();
                    if ("Resume".equals(clicked)) { pause.close(); GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED); }
                    if ("Save".equals(clicked)) { try { SaveManager.save(gs.world, gs.player, gs.time); } catch (Exception e) {} }
                    if ("Settings".equals(clicked)) { pause.openSettings(); }
                    if ("Quit".equals(clicked)) {
                        try { SaveManager.save(gs.world, gs.player, gs.time); } catch (Exception e) {}
                        pause.close();
                        mainMenu.setOpen(true);
                        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                    }
                    if ("settings:back".equals(clicked)) { pause.closeSettings(); }
                }
                return;
            }
            if (action != GLFW.GLFW_PRESS) return;

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                Inventory.Slot sel = gs.hotbar.selectedSlot();
                if (sel != null && sel.isItem() && sel.item == ItemType.APPLE) {
                    sel.count--; if (sel.count <= 0) sel.clear();
                    sound.play("sounds/step.wav", 0.6f, 1.6f);
                    System.out.println("[Eat] apple");
                    return;
                }
            }

            int[] hit = gs.world.raycast(gs.player.getEyePos(), gs.player.getForward(), 6f);
            if (hit == null) return;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                BlockType target = gs.world.getBlock(hit[0], hit[1], hit[2]);
                // Если игрок присел — не открываем GUI, ставим блок
                boolean guiAllowed = !gs.player.crouching;
                if (guiAllowed && target == BlockType.CRAFTING_TABLE) {
                    if (!gs.craftScreen.isOpen()) {
                        gs.craftScreen.open();
                        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                    }
                    return;
                }
                if (guiAllowed && target == BlockType.FURNACE) {
                    if (!gs.furnaceScreen.isOpen()) {
                        gs.furnaceScreen.open();
                        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                    }
                    return;
                }
                BlockType selBlock = gs.hotbar.selectedBlock();
                if (selBlock == null) return;
                int px = hit[0] + hit[3], py = hit[1] + hit[4], pz = hit[2] + hit[5];
                if (hit[3] == 0 && hit[4] == 0 && hit[5] == 0) return;
                if (selBlock == BlockType.FURNACE) {
                    // front должен смотреть НА игрока => rot по -yaw
                    // yaw 0 = восток(+X), 90 = юг(+Z), 180 = запад(-X), 270 = север(-Z)
                    // front (в chunk) на -Z при rot 2; согласуем:
                    float yaw = ((gs.player.yaw % 360) + 360) % 360;
                    int rot;
                    if (yaw >= 315 || yaw < 45) rot = 3;       // игрок смотрит на +X => front на -X
                    else if (yaw >= 45 && yaw < 135) rot = 2;   // +Z => -Z
                    else if (yaw >= 135 && yaw < 225) rot = 1;  // -X => +X
                    else rot = 0;                               // -Z => +Z
                    gs.world.placeBlock(px, py, pz, selBlock, gs.player.position, rot);
                } else {
                    gs.world.placeBlock(px, py, pz, selBlock, gs.player.position, hit[3], hit[4], hit[5]);
                }
                if (gs.world.isSolid(px, py, pz)) {
                    gs.hotbar.consumeSelected();
                    sound.playPlace(selBlock);
                }
            }
        });

        Matrix4f proj = new Matrix4f().perspective((float)Math.toRadians(SettingsHolder.get().fov), 1280f/720f, 0.1f, 300f);
        Matrix4f model = new Matrix4f();

        double lastTime = GLFW.glfwGetTime();
        int frames = 0; double fpsTimer = 0; int fps = 0; double saveTimer = 0;

        // ---- CRASH HANDLER ----
        String[] crashTrace = { null };
        try {
        while (!GLFW.glfwWindowShouldClose(window)) {
            double now = GLFW.glfwGetTime();
            float dt = (float)(now - lastTime);
            lastTime = now;

            // ---- экран загрузки ----
            if (gs.loadingScreenActive) {
                GL11.glClearColor(0.05f, 0.05f, 0.07f, 1f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                gs.loadingScreen.render();
                GLFW.glfwSwapBuffers(window);
                GLFW.glfwPollEvents();
                // Enter — применить мир и зайти в игру
                if (gs.loadingScreen.finished && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS) {
                    // Берём мир, который уже загружен в фоновом потоке (без повторного чтения файла)
                    World w = gs.loadingScreen.world;
                    if (w == null) {
                        // fallback (если фон не успел)
                        LoadedWorld lw = loadOrCreateWorld(gs.activeSlot);
                        w = lw.world;
                        applySaveTo(gs, lw.save);
                    } else {
                        // данные игрока/инвентаря уже есть в world (применялись в фоне? нет — нужно применить save)
                        // Но save у нас в LoadedWorld, который остался в фоне.
                        // Придётся применить save здесь, но без повторного чтения chunks:
                        SaveManager.SaveData save = SaveManager.load();
                        applySaveTo(gs, save);
                    }
                    w.drops.setItemAtlas(itemTex);
                    gs.world = w;
                    gs.player = new Player();
                    gs.player.position.set(World.SIZE * Chunk.SX / 2f + 0.5f, 70f, World.SIZE * Chunk.SZ / 2f + 0.5f);
                    gs.player.yaw = 0f;
                    gs.player.pitch = 0f;
                    gs.time = new TimeOfDay();
                    gs.hotbar = new Hotbar(tex, itemTex, gs.world.inventory, font);
                    gs.invScreen = new InventoryScreen(tex, itemTex, gs.world.inventory, font);
                    gs.craftScreen = new CraftingScreen(tex, itemTex, gs.world.inventory, font);
                    gs.furnaceScreen = new FurnaceScreen(tex, itemTex, gs.world.inventory, font);
                    lastPosInit = false;
                    // Перестраиваем ВСЕ меши в главном потоке (GL-контекст есть)
                    gs.world.rebuildAll();
                    gs.loadingScreenActive = false;
                    GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                    System.out.println("[Main] play slot " + gs.activeSlot);
                }
                if (gs.loadingScreen.error && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS) {
                    gs.loadingScreenActive = false;
                    mainMenu.setOpen(true);
                }
                lastTime = GLFW.glfwGetTime();
                continue;
            }

            // ---- главное меню ----
            if (mainMenu.isOpen()) {
                float[] sky = new float[]{0.08f, 0.10f, 0.14f};
                GL11.glClearColor(sky[0], sky[1], sky[2], 1f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                mainMenu.render();
                String act = mainMenu.pollAction();
                if (act != null) {
                    if (act.startsWith("play:")) {
                        int slot = Integer.parseInt(act.substring(5));
                        gs.activeSlot = slot;
                        final int slotFinal = slot;

                        // Создаём LoadingScreen и запускаем загрузку в фоне
                        gs.loadingScreen = new LoadingScreen(font);
                        gs.loadingScreen.total.set(World.SIZE * Chunk.SX * World.SIZE * Chunk.SZ);
                        gs.loadingScreen.loaded.set(0);
                        gs.loadingScreen.finished = false;
                        gs.loadingScreen.error = false;
                        gs.loadingScreen.errorMsg = "";
                        mainMenu.setOpen(false);
                        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                        gs.loadingScreenActive = true;

                        gs.loadingScreen.initMap();
                        Thread t = new Thread(() -> {
                            try {
                                LoadedWorld lw = loadOrCreateWorld(slotFinal);
                                World w = lw.world;
                                int loadedChunks = 0;
                                int totalBlocks = World.SIZE * Chunk.SX;
                                for (int bz = 0; bz < totalBlocks; bz++) {
                                    for (int bx = 0; bx < totalBlocks; bx++) {
                                        BlockType topBlock = BlockType.AIR;
                                        for (int y = Chunk.SY - 1; y >= 0; y--) {
                                            BlockType b = w.getBlock(bx, y, bz);
                                            if (b != BlockType.AIR) { topBlock = b; break; }
                                        }
                                        
                                        int color = 0x000000;
                                        if (topBlock == BlockType.GRASS) color = 0x4CAF50;
                                        else if (topBlock == BlockType.DIRT) color = 0x795548;
                                        else if (topBlock == BlockType.STONE || topBlock == BlockType.COBBLESTONE) color = 0x888888;
                                        else if (topBlock == BlockType.SAND) color = 0xDCD6A0;
                                        else if (topBlock == BlockType.WOOD || topBlock == BlockType.PLANKS) color = 0x8B5A2B;
                                        else if (topBlock == BlockType.LEAVES) color = 0x2E7D32;
                                        else if (topBlock == BlockType.COAL_ORE || topBlock == BlockType.IRON_ORE || topBlock == BlockType.DIAMOND_ORE) color = 0x444444;
                                        else if (topBlock == BlockType.AIR) color = 0x000000;
                                        else color = 0x555555;
                                        
                                        gs.loadingScreen.setChunkColor(bx, bz, color);
                                        loadedChunks++;
                                        if (loadedChunks % 100 == 0) {
                                            gs.loadingScreen.loaded.set(loadedChunks);
                                        }
                                    }
                                }
                                gs.loadingScreen.loaded.set(totalBlocks * totalBlocks);
                                w.drops.setItemAtlas(itemTex);
                                gs.loadingScreen.world = w;
                                gs.loadingScreen.finished = true;
                            } catch (Exception ex) {
                                gs.loadingScreen.error = true;
                                gs.loadingScreen.errorMsg = ex.getMessage();
                                ex.printStackTrace();
                            }
                        }, "WorldLoader");
                        t.setDaemon(true);
                        t.start();
                    } else if (act.startsWith("delete:")) {
                        int slot = Integer.parseInt(act.substring(7));
                        Worlds.delete(slot);
                        System.out.println("[Main] delete slot " + slot);
                    } else if ("quit".equals(act)) {
                        GLFW.glfwSetWindowShouldClose(window, true);
                    } else if ("debug:toggle".equals(act)) {
                        showDebug[0] = !showDebug[0];
                    } else if ("debug:xray".equals(act)) {
                        XRay.enabled = !XRay.enabled;
                        for (Chunk c : gs.world.chunks.values()) c.buildMesh();
                    }
                }
                GLFW.glfwSwapBuffers(window);
                GLFW.glfwPollEvents();
                continue;
            }

            // ---- игра ----
            gs.world.flushRebuilds();
            if (!pause.isOpen()) {
                gs.time.update(dt);
                gs.player.update(window, gs.world, dt);
                boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
                if (leftDown && !gs.invScreen.isOpen() && !gs.craftScreen.isOpen() && !gs.furnaceScreen.isOpen() && !pause.isOpen() && !mainMenu.isOpen()) {
                    Inventory.Slot heldSlot = gs.hotbar.selectedSlot();
                    BlockType beforeBreak = null;
                    boolean broke = mining.update(dt, gs.world, gs.player, heldSlot);
                    if (broke) {
                        BlockType broken = mining.getLastBrokenBlock();
                        if (broken != null) sound.playBreak(broken);
                    }
                } else {
                    mining.reset();
                }
                if (!lastPosInit) { lastPos.set(gs.player.position); lastPosInit = true; }
                float moved = lastPos.distance(gs.player.position);
                lastPos.set(gs.player.position);
                if (gs.player.onGround && moved > 0.01f) {
                    stepTimer += dt;
                    if (stepTimer >= 0.38) {
                        stepTimer = 0;
                        sound.playStep(gs.world.blockUnderPlayer(gs.player));
                    }
                } else stepTimer = 0.3;
                gs.world.particles.update(dt);
                leafDecay.update(dt, gs.world);
                gs.world.drops.update(dt, gs.world, gs.world.inventory, gs.player.position);
                if (gs.furnaceScreen.isOpen()) gs.furnaceScreen.updateFurnace();
            }

            frames++; fpsTimer += dt;
            if (fpsTimer >= 1.0) { fps = frames; frames = 0; fpsTimer = 0; }

            int[] hit = gs.world.raycast(gs.player.getEyePos(), gs.player.getForward(), 6f);
            if (showDebug[0]) debug.update(gs.world, gs.player, gs.time, fps, hit, dt);

            saveTimer += dt;
            if (saveTimer >= 5.0) {
                saveTimer = 0;
                try { SaveManager.save(gs.world, gs.player, gs.time); } catch (Exception ignored) {}
            }

            float[] sky = gs.time.skyColor();
            GL11.glClearColor(sky[0], sky[1], sky[2], 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            proj.identity().perspective((float)Math.toRadians(SettingsHolder.get().fov), 1280f/720f, 0.1f, 300f);
            shader.bind();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer pb = stack.mallocFloat(16);
                proj.get(pb); GL20.glUniformMatrix4fv(uProj, false, pb);
                gs.player.getViewMatrix().get(pb); GL20.glUniformMatrix4fv(uView, false, pb);
                model.identity().get(pb); GL20.glUniformMatrix4fv(uModel, false, pb);
            }
            GL20.glUniform1i(uTex, 0);
            GL20.glUniform1f(uSun, gs.time.sunBrightness());
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);

            gs.world.render(proj, gs.player.getViewMatrix(), uProj, uView, uModel, uTex, tex, uSun,
                gs.time.sunBrightness(), -1, -1, sky[0], sky[1], sky[2], gs.player.position);

            if (mining.isMining() && hit != null) crack.render(hit, mining.getProgress(), proj, gs.player.getViewMatrix(), gs.world);
            highlight.render(hit, proj, gs.player.getViewMatrix(), gs.world);
            gs.world.particles.render(proj, gs.player.getViewMatrix(), tex);
            gs.world.drops.render(proj, gs.player.getViewMatrix(), tex);
            crosshair.render();
            gs.hotbar.render();
            gs.invScreen.render();
            gs.craftScreen.render();
            gs.furnaceScreen.render();
            if (showDebug[0]) debug.render();
            pause.render();

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }

        try { SaveManager.save(gs.world, gs.player, gs.time); } catch (Exception ignored) {}
        } catch (Throwable ex) {
            java.io.StringWriter sw = new java.io.StringWriter();
            ex.printStackTrace(new java.io.PrintWriter(sw));
            crashTrace[0] = sw.toString();
            System.err.println("[FATAL] " + crashTrace[0]);
            Logger.error("[FATAL] " + crashTrace[0]);
        }

        // Если был краш — рисуем экран ошибки, пока пользователь не нажмёт Enter
        if (crashTrace[0] != null) {
            System.err.println("[Main] Rendering crash screen...");
            while (!GLFW.glfwWindowShouldClose(window)) {
                GL11.glClearColor(0.1f, 0.0f, 0.0f, 1f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                int ww[] = new int[1], hh[] = new int[1];
                GLFW.glfwGetWindowSize(window, ww, hh);
                // Рисуем текст через DebugOverlay.font? Есть только font в main — используем его
                // (font доступен как локальная переменная, но мы в catch — надо вынести font выше try)
                try {
                    // Рисуем только первые 25 строк stacktrace
                    String[] lines = crashTrace[0].split("\n");
                    float ch = 0.025f;
                    float y = 0.9f;
                    for (int i = 0; i < Math.min(lines.length, 25); i++) {
                        font.drawString(lines[i].length() > 120 ? lines[i].substring(0, 120) : lines[i], -0.95f, y, ch);
                        y -= ch * 1.3f;
                    }
                    font.drawString("Press ENTER to copy stacktrace and close", -0.95f, -0.95f, 0.03f);
                } catch (Throwable ignored) {}
                GLFW.glfwSwapBuffers(window);
                GLFW.glfwPollEvents();
                if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS) {
                    GLFW.glfwSetClipboardString(window, crashTrace[0]);
                    System.err.println("[Main] stacktrace copied to clipboard");
                    break;
                }
            }
        }

        sound.destroy();
        Logger.close();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }
}
