package com.abilitywar.client.fx;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 가벼운 자체 파티클 시스템 (가산/반투명, 빌보드/줄기). */
public final class Particles {
    public enum Kind {
        GLOW(Tex.GLOW, true), SPARK(Tex.SPARK, true), FLARE(Tex.FLARE, true), STREAK(Tex.STREAK, true),
        SMOKE_DARK(Tex.SMOKE, false), SMOKE_ADD(Tex.SMOKE, true), WISP_DARK(Tex.WISP, false), WISP_ADD(Tex.WISP, true),
        RING(Tex.RING_SOFT, true), FLAME(Tex.fx("flame"), true);
        final Identifier tex;
        final boolean additive;

        Kind(Identifier tex, boolean additive) {
            this.tex = tex;
            this.additive = additive;
        }
    }

    public static final class P {
        public Kind kind;
        public double x, y, z, px, py, pz, vx, vy, vz;
        public float age, life, size0, size1, rot, rotV;
        public float r = 1, g = 1, b = 1, a = 1;
        public float drag = 0.9f, grav;
        public boolean streak;
        public float streakLen = 1f;
        /** 끌림 중심 (블랙홀). */
        public Vec3d attract;
        public float attractStrength;
        public float fadeIn = 0.1f;

        public P color(int rgb, float alpha) {
            r = ((rgb >> 16) & 255) / 255f;
            g = ((rgb >> 8) & 255) / 255f;
            b = (rgb & 255) / 255f;
            a = alpha;
            return this;
        }
    }

    public static final Random RND = new Random();
    private static final List<P> LIST = new ArrayList<>();
    private static final int MAX = 6000;

    public static P spawn(Kind kind, double x, double y, double z, double vx, double vy, double vz, float life, float size) {
        P p = new P();
        p.kind = kind;
        p.x = p.px = x;
        p.y = p.py = y;
        p.z = p.pz = z;
        p.vx = vx;
        p.vy = vy;
        p.vz = vz;
        p.life = Math.max(1, life);
        p.size0 = size;
        p.size1 = size * 0.3f;
        p.rot = RND.nextFloat() * 6.28f;
        p.streak = kind == Kind.STREAK;
        if (LIST.size() < MAX) LIST.add(p);
        return p;
    }

    public static P spawn(Kind kind, Vec3d pos, Vec3d vel, float life, float size) {
        return spawn(kind, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, life, size);
    }

    public static void tick() {
        for (int i = LIST.size() - 1; i >= 0; i--) {
            P p = LIST.get(i);
            p.px = p.x;
            p.py = p.y;
            p.pz = p.z;
            p.age++;
            if (p.age >= p.life) {
                LIST.set(i, LIST.get(LIST.size() - 1));
                LIST.remove(LIST.size() - 1);
                continue;
            }
            if (p.attract != null) {
                double dx = p.attract.x - p.x, dy = p.attract.y - p.y, dz = p.attract.z - p.z;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 0.35) {
                    p.age = p.life;
                    continue;
                }
                double k = p.attractStrength / Math.max(0.6, d);
                p.vx += dx / d * k;
                p.vy += dy / d * k;
                p.vz += dz / d * k;
            }
            p.vy -= p.grav;
            p.vx *= p.drag;
            p.vy *= p.drag;
            p.vz *= p.drag;
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            p.rot += p.rotV;
        }
    }

    public static void clear() {
        LIST.clear();
    }

    public static int count() {
        return LIST.size();
    }

    public static void render(VertexConsumerProvider.Immediate imm, Matrix4f m, Quaternionf camRot, Vec3d cam, float tickDelta) {
        if (LIST.isEmpty()) return;
        Map<Kind, List<P>> byKind = new EnumMap<>(Kind.class);
        for (P p : LIST) byKind.computeIfAbsent(p.kind, k -> new ArrayList<>()).add(p);
        // 어두운 것 먼저, 가산 나중
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<Kind, List<P>> e : byKind.entrySet()) {
                Kind k = e.getKey();
                if ((pass == 0) == k.additive) continue;
                VertexConsumer vc = imm.getBuffer(k.additive ? AWRenderLayers.additive(k.tex) : AWRenderLayers.translucent(k.tex));
                for (P p : e.getValue()) {
                    float t = (p.age + tickDelta) / p.life;
                    if (t >= 1) continue;
                    double x = MathHelper.lerp(tickDelta, p.px, p.x) - cam.x;
                    double y = MathHelper.lerp(tickDelta, p.py, p.y) - cam.y;
                    double z = MathHelper.lerp(tickDelta, p.pz, p.z) - cam.z;
                    float size = p.size0 + (p.size1 - p.size0) * t;
                    float fade = Math.min(1f, t / Math.max(0.001f, p.fadeIn)) * (1f - t * t);
                    float a = p.a * fade;
                    if (p.streak) {
                        Vec3d vel = new Vec3d(p.vx, p.vy, p.vz);
                        float len = (float) Math.max(size * 1.5, vel.length() * 2.2 * p.streakLen);
                        Draw.streak(vc, m, new Vec3d(x, y, z), vel, len, size * 0.35f, p.r, p.g, p.b, a);
                    } else {
                        Draw.billboard(vc, m, camRot, x, y, z, size, p.rot + p.rotV * tickDelta, p.r, p.g, p.b, a);
                    }
                }
            }
        }
    }

    private Particles() {
    }
}
