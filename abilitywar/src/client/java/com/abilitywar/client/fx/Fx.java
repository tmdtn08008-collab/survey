package com.abilitywar.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** 클라이언트 이펙트 기본형. */
public abstract class Fx {
    public static final int PURPLE = 0x9B5CFF;
    public static final int DEEP = 0x5A1FBF;
    public static final int PINK = 0xD38CFF;
    public static final int PALE = 0xEBDDFF;

    protected int age;
    protected int life;

    protected Fx(int life) {
        this.life = life;
    }

    /** @return true 면 제거. */
    public boolean tick(ClientWorld world) {
        age++;
        return age >= life;
    }

    public abstract void render(Ctx c);

    protected float t(Ctx c) {
        return (age + c.tickDelta) / life;
    }

    public static final class Ctx {
        public VertexConsumerProvider.Immediate imm;
        public MatrixStack matrices;
        public Matrix4f m;
        public Quaternionf camRot;
        public Vec3d cam;
        public float tickDelta;
        public ClientWorld world;
        public MinecraftClient mc;

        public Vec3d rel(Vec3d p) {
            return p.subtract(cam);
        }
    }

    /** 카메라에 너무 가까운 큰 스프라이트는 흐리게 (화면이 하얗게 덮이는 것 방지). */
    public static float nearFade(Vec3d rel, float size) {
        double d = rel.length();
        double lim = Math.max(0.6, size * 0.9);
        return (float) Math.max(0, Math.min(1, (d - lim * 0.35) / lim));
    }

    public static float r(int rgb) {
        return ((rgb >> 16) & 255) / 255f;
    }

    public static float g(int rgb) {
        return ((rgb >> 8) & 255) / 255f;
    }

    public static float b(int rgb) {
        return (rgb & 255) / 255f;
    }
}
