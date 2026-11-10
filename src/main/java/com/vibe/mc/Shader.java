package com.vibe.mc;

import org.lwjgl.opengl.GL20;

public class Shader {
    private final int program;

    public Shader(String vertSrc, String fragSrc) {
        int vs = compile(GL20.GL_VERTEX_SHADER, vertSrc);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, fragSrc);
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Link error: " + GL20.glGetProgramInfoLog(program));
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Compile error: " + GL20.glGetShaderInfoLog(id));
        }
        return id;
    }

    public void bind() { GL20.glUseProgram(program); }
    public int id() { return program; }

    public int uniform(String name) {
        return GL20.glGetUniformLocation(program, name);
    }
}
