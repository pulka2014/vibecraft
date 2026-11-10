package com.vibe.mc;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class SoundSystem {
    private long device, context;
    private boolean ready = false;
    private final Map<String, Integer> buffers = new HashMap<>();
    private final int[] sources = new int[16];
    private int nextSource = 0;

    public SoundSystem() {
        try {
            device = ALC10.alcOpenDevice((ByteBuffer) null);
            if (device == 0) { System.out.println("[Sound] no device"); return; }
            ALCCapabilities caps = ALC.createCapabilities(device);
            context = ALC10.alcCreateContext(device, (IntBuffer) null);
            ALC10.alcMakeContextCurrent(context);
            AL.createCapabilities(caps);
            AL10.alGenSources(sources);
            ready = true;
            Logger.info("[Sound] OpenAL ready");
            checkAllSounds();
        } catch (Throwable t) {
            System.out.println("[Sound] disabled: " + t.getMessage());
        }
    }

    private int loadBuffer(String classpath) {
        if (buffers.containsKey(classpath)) return buffers.get(classpath);
        try (InputStream in = SoundSystem.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) { System.out.println("[Sound] not found " + classpath); return 0; }
            byte[] bytes = in.readAllBytes();
            AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
            AudioFormat fmt = ais.getFormat();
            byte[] pcm = ais.readAllBytes();
            boolean stereo = fmt.getChannels() == 2;
            int alFormat = stereo ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
            ByteBuffer pcmBuf = MemoryUtil.memAlloc(pcm.length);
            pcmBuf.put(pcm).flip();
            int buf = AL10.alGenBuffers();
            AL10.alBufferData(buf, alFormat, pcmBuf, (int) fmt.getSampleRate());
            MemoryUtil.memFree(pcmBuf);
            buffers.put(classpath, buf);
            System.out.println("[Sound] loaded " + classpath + " (" + pcm.length + " bytes)");
            return buf;
        } catch (Exception e) {
            System.out.println("[Sound] failed to load " + classpath + ": " + e.getMessage());
            return 0;
        }
    }

    public void play(String classpath, float volume, float pitch) {
        if (!ready) return;
        int buf = loadBuffer(classpath);
        if (buf == 0) return;
        int src = sources[nextSource];
        nextSource = (nextSource + 1) % sources.length;
        AL10.alSourceStop(src);
        AL10.alSourcei(src, AL10.AL_BUFFER, buf);
        AL10.alSourcef(src, AL10.AL_GAIN, volume);
        AL10.alSourcef(src, AL10.AL_PITCH, pitch);
        AL10.alSourcePlay(src);
    }

    private String soundFor(BlockType type) {
        switch (type) {
            case GRASS: return "sounds/grass.wav";
            case DIRT:  return "sounds/dirt.wav";
            case STONE: return "sounds/stone.wav";
            case SAND:  return "sounds/sand.wav";
            case WOOD:  return "sounds/wood.wav";
            case PLANKS: return "sounds/wood.wav";   // доски звучат как дерево
            case LEAVES: return "sounds/grass.wav";  // листья — как трава
            case CRAFTING_TABLE: return "sounds/wood.wav";
            case FURNACE: return "sounds/stone.wav";
            case COAL_ORE: case IRON_ORE: case DIAMOND_ORE: case COBBLESTONE: return "sounds/stone.wav";
            case PRESSURE_PLATE: return "sounds/wood.wav";
            default:    return "sounds/stone.wav";
        }
    }

    public void playBreak(BlockType type) {
        play(soundFor(type), 0.7f, 0.9f + (float)Math.random() * 0.2f);
    }

    public void playPlace(BlockType type) {
        play(soundFor(type), 0.6f, 1.1f + (float)Math.random() * 0.2f);
    }

    public void playStep(BlockType under) {
        String s;
        switch (under) {
            case GRASS: s = "sounds/step.wav"; break;
            case DIRT:  s = "sounds/step_gravel.wav"; break;
            case STONE: s = "sounds/step_stone.wav"; break;
            case SAND:  s = "sounds/step_sand.wav"; break;
            case WOOD:  s = "sounds/step_wood.wav"; break;
            case PLANKS: s = "sounds/step_wood.wav"; break;
            case LEAVES: s = "sounds/step.wav"; break;
            default:    s = "sounds/step_stone.wav"; break;
        }
        play(s, 0.35f, 0.85f + (float)Math.random() * 0.3f);
    }


    private void checkAllSounds() {
        String[] files = {
            "sounds/grass.wav", "sounds/dirt.wav", "sounds/stone.wav",
            "sounds/sand.wav", "sounds/wood.wav",
            "sounds/step.wav", "sounds/step_stone.wav", "sounds/step_sand.wav",
            "sounds/step_wood.wav", "sounds/step_gravel.wav"
        };
        int found = 0, missing = 0;
        for (String f : files) {
            boolean ok = SoundSystem.class.getClassLoader().getResource(f) != null;
            if (ok) { found++; Logger.info("[Sound] " + f + " OK"); }
            else   { missing++; Logger.warn("[Sound] " + f + " MISSING"); }
        }
        Logger.info("[Sound] total: " + found + " OK, " + missing + " missing");
    }

    public void destroy() {
        if (!ready) return;
        AL10.alDeleteSources(sources);
        for (int b : buffers.values()) AL10.alDeleteBuffers(b);
        ALC10.alcMakeContextCurrent(0);
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
    }
}
