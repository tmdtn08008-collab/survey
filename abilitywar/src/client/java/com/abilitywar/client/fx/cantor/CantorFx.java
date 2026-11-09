package com.abilitywar.client.fx.cantor;

import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Debris;
import com.abilitywar.client.fx.Draw;
import com.abilitywar.client.fx.Effects;
import com.abilitywar.client.fx.Fx;
import com.abilitywar.client.fx.HandTracker;
import com.abilitywar.client.fx.Particles;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.client.fx.VfxManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.abilitywar.client.fx.Effects.R;
import static com.abilitywar.client.fx.Effects.randDir;
import static com.abilitywar.client.fx.Effects.rf;
import static com.abilitywar.client.fx.Particles.Kind;

/** 성가사 이펙트. */
public final class CantorFx {
    public static final int GOLD = 0xFFD86A;
    public static final int WHITE_GOLD = 0xFFF2C8;
    /** 버프 색: 용맹 / 수호 / 신속 / 은총. */
    static final int[] BUFF_COLOR = {0xFFA040, 0x7FD4FF, 0x8CFF8C, 0xFFE89A};
    static final Identifier NOTE = Tex.fx("cantor_note");
    static final Identifier CIRCLE = Tex.fx("cantor_circle");
    static final Identifier DOME = Tex.fx("cantor_dome");
    static final Identifier BELL = Tex.fx("cantor_bell");

    static float r(int c) {
        return ((c >> 16) & 255) / 255f;
    }

    static float g(int c) {
        return ((c >> 8) & 255) / 255f;
    }

    static float b(int c) {
        return (c & 255) / 255f;
    }

    static int mix(int a, int b, float k) {
        int r = (int) MathHelper.lerp(k, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) MathHelper.lerp(k, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) MathHelper.lerp(k, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    // ================================================================ 성가사 상태 (선율 / 음표)
    static final Map<Integer, int[]> STATE = new HashMap<>();
    /** 버프: 엔티티 → [종류, 끝나는 시각] 목록. */
    static final Map<Integer, List<long[]>> BUFFS = new HashMap<>();
    static final Map<Integer, Long> SILENCED = new HashMap<>();

    public static int mode(int id) {
        int[] s = STATE.get(id);
        return s == null ? 0 : Math.min(2, s[0]);
    }

    public static boolean spawn(String type, NbtCompound n, ClientWorld w, Vec3d p) {
        switch (type) {
            case "cantor_state" -> STATE.put(n.getInt("c"), new int[]{n.getInt("m"), n.getInt("n")});
            case "cantor_note" -> VfxManager.add(new NoteShot(w, n.getInt("c"), n.getInt("e"),
                    new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz")), n.getInt("n"), n.getInt("m")));
            case "cantor_wave" -> VfxManager.add(new Wave(w, n.getInt("c"), p, n.getFloat("r"), n.getBoolean("h"), BUFF_COLOR[Math.min(3, n.getInt("k"))]));
            case "cantor_buff" -> {
                int e = n.getInt("e"), k = n.getInt("k");
                long until = w.getTime() + n.getInt("d");
                List<long[]> list = BUFFS.computeIfAbsent(e, x -> new ArrayList<>());
                if (k == 9) {
                    for (int i = 0; i < 4; i++) putBuff(list, i, until);
                } else putBuff(list, k, until);
                VfxManager.add(new BuffPulse(e, k == 9 ? -1 : k));
            }
            case "cantor_heal" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) for (int i = 0; i < 8; i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.4f, 0.4f), rf(0.1f, 1.6f), rf(-0.4f, 0.4f)), new Vec3d(0, rf(0.03f, 0.07f), 0), rf(14, 22), rf(0.06f, 0.11f));
                    pp.color(i % 2 == 0 ? 0xFFF2A0 : 0xB8FF9A, 1f);
                    pp.drag = 0.97f;
                }
            }
            case "cantor_sanctuary" -> VfxManager.add(new Sanctuary(p, n.getFloat("r"), n.getInt("d"), n.getBoolean("h")));
            case "cantor_repel" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) Effects.sparks(Effects.chest(e, 1f), 6, 0.2f, GOLD, 0.06f);
            }
            case "cantor_bell" -> VfxManager.add(new Bell(w, p, n.getInt("rings"), n.getBoolean("h")));
            case "cantor_ward" -> VfxManager.add(new Ward(n.getInt("c"), n.getInt("d")));
            case "cantor_ward_burst" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                if (e != null) {
                    VfxManager.add(new Burst(e.getPos().add(0, 1.1, 0), 1.4f, WHITE_GOLD));
                    noteBurst(e.getPos().add(0, 1.2, 0), 14, 0.22f, GOLD);
                    if (e == MinecraftClient.getInstance().player) com.abilitywar.client.fx.FpEffects.counterFlash();
                }
            }
            case "cantor_ward_fade" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                if (e != null) for (int i = 0; i < 16; i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.6f, 0.6f), rf(0.4f, 1.8f), rf(-0.6f, 0.6f)), new Vec3d(0, -0.03, 0), rf(12, 20), 0.05f);
                    pp.color(GOLD, 0.6f);
                    pp.grav = 0.01f;
                }
            }
            case "cantor_pillar" -> VfxManager.add(new Pillar(w, p, n.getBoolean("big")));
            case "cantor_silence" -> SILENCED.put(n.getInt("e"), w.getTime() + n.getInt("d"));
            case "cantor_switch" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                int m = Math.min(2, n.getInt("m"));
                int[] st = STATE.computeIfAbsent(n.getInt("c"), x -> new int[2]);
                st[0] = m;
                if (e != null) {
                    noteBurst(e.getPos().add(0, 1.3, 0), 10, 0.16f, BUFF_COLOR[m]);
                    VfxManager.add(new Burst(e.getPos().add(0, 2.1, 0), 0.7f, BUFF_COLOR[m]));
                }
            }
            case "cantor_ult" -> VfxManager.add(new Ult(w, n.getInt("c"), p));
            default -> {
                return false;
            }
        }
        return true;
    }

    static void putBuff(List<long[]> list, int k, long until) {
        for (long[] b : list) {
            if (b[0] == k) {
                b[1] = Math.max(b[1], until);
                return;
            }
        }
        list.add(new long[]{k, until});
    }

    // ================================================================ 음표 입자 (전용)
    static final List<float[]> NOTES = new ArrayList<>();

    /** pos, vel, 수명, 크기, 색. [x,y,z,vx,vy,vz,age,life,size,frame,r,g,b,phase] */
    static void note(Vec3d p, Vec3d v, float life, float size, int color) {
        if (NOTES.size() > 600) return;
        NOTES.add(new float[]{(float) p.x, (float) p.y, (float) p.z, (float) v.x, (float) v.y, (float) v.z, 0, life, size, R.nextBoolean() ? 0 : 1,
                r(color), g(color), b(color), R.nextFloat() * 6.28f});
    }

    static void noteBurst(Vec3d c, int n, float speed, int color) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + R.nextDouble() * 0.3;
            Vec3d v = new Vec3d(Math.cos(a) * speed, rf(0.02f, 0.1f), Math.sin(a) * speed);
            note(c, v, rf(16, 26), rf(0.12f, 0.2f), color);
        }
    }

    public static void tick(ClientWorld w) {
        long now = w.getTime();
        for (Iterator<float[]> it = NOTES.iterator(); it.hasNext(); ) {
            float[] q = it.next();
            q[0] += q[3];
            q[1] += q[4];
            q[2] += q[5];
            q[3] *= 0.93f;
            q[5] *= 0.93f;
            q[4] = q[4] * 0.93f + 0.004f;
            q[6]++;
            if (q[6] >= q[7]) it.remove();
        }
        for (Iterator<Map.Entry<Integer, List<long[]>>> it = BUFFS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, List<long[]>> en = it.next();
            Entity e = w.getEntityById(en.getKey());
            en.getValue().removeIf(b -> b[1] < now);
            if (e == null || !e.isAlive() || en.getValue().isEmpty()) {
                it.remove();
                continue;
            }
            if (now % 5 == 0) {
                long[] b = en.getValue().get((int) (now / 5 % en.getValue().size()));
                Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.45f, 0.45f), rf(0.05f, 0.4f), rf(-0.45f, 0.45f)), new Vec3d(0, rf(0.03f, 0.06f), 0), rf(14, 20), 0.05f);
                pp.color(BUFF_COLOR[(int) b[0]], 0.9f);
                pp.drag = 0.98f;
            }
        }
        SILENCED.entrySet().removeIf(en -> en.getValue() < now || w.getEntityById(en.getKey()) == null);
        STATE.keySet().removeIf(id -> w.getEntityById(id) == null);
    }

    public static void render(Fx.Ctx c) {
        long now = c.world.getTime();
        float t = now + c.tickDelta;
        Vector3f rv = c.camRot.transform(new Vector3f(1, 0, 0)), uv = c.camRot.transform(new Vector3f(0, 1, 0));
        Vec3d right = new Vec3d(rv.x, rv.y, rv.z), up = new Vec3d(uv.x, uv.y, uv.z);
        // 버프 고리 (발밑)
        if (!BUFFS.isEmpty()) {
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING_SOFT));
            for (Map.Entry<Integer, List<long[]>> en : BUFFS.entrySet()) {
                Entity e = c.world.getEntityById(en.getKey());
                if (e == null) continue;
                Vec3d p = e.getLerpedPos(c.tickDelta).subtract(c.cam);
                int i = 0;
                for (long[] b : en.getValue()) {
                    int col = BUFF_COLOR[(int) b[0]];
                    float left = Math.min(1, (b[1] - now) / 20f);
                    float size = 0.62f + i * 0.13f + 0.03f * MathHelper.sin(t * 0.2f + i);
                    Draw.flat(ring, c.m, p.x, p.y + 0.04 + i * 0.02, p.z, size, t * 0.05f * (i % 2 == 0 ? 1 : -1), r(col), g(col), b(col), 0.55f * left);
                    i++;
                }
            }
        }
        // 성가사 머리 위 음표 (화음)
        VertexConsumer nv = c.imm.getBuffer(AWRenderLayers.additive(NOTE));
        for (Map.Entry<Integer, int[]> en : STATE.entrySet()) {
            int notes = en.getValue()[1];
            if (notes <= 0) continue;
            Entity e = c.world.getEntityById(en.getKey());
            if (e == null || Effects.isLocalFp(e) || e.isInvisible()) continue;
            Vec3d h = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() + 0.55, 0).subtract(c.cam);
            boolean full = notes >= 3;
            int col = full ? WHITE_GOLD : GOLD;
            for (int i = 0; i < notes; i++) {
                double a = t * 0.08 + i * Math.PI * 2 / 3;
                Vec3d q = h.add(Math.cos(a) * 0.5, 0.08 * Math.sin(t * 0.2 + i), Math.sin(a) * 0.5);
                float s = full ? 0.13f + 0.03f * MathHelper.sin(t * 0.4f) : 0.11f;
                Draw.quadUV(nv, c.m, q, right.multiply(s), up.multiply(s), r(col), g(col), b(col), 0.95f, i % 2 == 0 ? 0 : 0.5f, 0, i % 2 == 0 ? 0.5f : 1, 1);
            }
        }
        // 침묵 표시
        for (Map.Entry<Integer, Long> en : SILENCED.entrySet()) {
            Entity e = c.world.getEntityById(en.getKey());
            if (e == null || Effects.isLocalFp(e)) continue;
            Vec3d h = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() + 0.45, 0).subtract(c.cam);
            Draw.quadUV(nv, c.m, h, right.multiply(0.18), up.multiply(0.18), 0.8f, 0.82f, 1f, 0.9f, 0, 0, 0.5f, 1);
        }
        if (!SILENCED.isEmpty()) {
            VertexConsumer sl = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            for (Map.Entry<Integer, Long> en : SILENCED.entrySet()) {
                Entity e = c.world.getEntityById(en.getKey());
                if (e == null || Effects.isLocalFp(e)) continue;
                Vec3d h = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() + 0.45, 0).subtract(c.cam);
                Draw.billboard(sl, c.m, c.camRot, h.x, h.y, h.z, 0.3f, (float) Math.PI / 4, 1f, 0.2f, 0.25f, 1f);
            }
        }
        // 음표 입자
        nv = c.imm.getBuffer(AWRenderLayers.additive(NOTE));
        for (float[] q : NOTES) {
            float a = q[6] + c.tickDelta;
            float k = a / q[7];
            float alpha = Math.min(1, a / 3f) * (1 - k * k);
            Vec3d p = new Vec3d(q[0] + q[3] * c.tickDelta, q[1] + q[4] * c.tickDelta, q[2] + q[5] * c.tickDelta).subtract(c.cam);
            alpha *= Fx.nearFade(p, 1.2f);
            if (alpha <= 0.01f) continue;
            float s = q[8] * (1 + 0.1f * MathHelper.sin(a * 0.5f + q[13]));
            float f0 = q[9] == 0 ? 0 : 0.5f;
            double wob = 0.25 * Math.sin(a * 0.3 + q[13]);
            Vec3d ax = right.multiply(Math.cos(wob) * s).add(up.multiply(Math.sin(wob) * s));
            Vec3d ay = up.multiply(Math.cos(wob) * s).subtract(right.multiply(Math.sin(wob) * s));
            Draw.quadUV(nv, c.m, p, ax, ay, q[10], q[11], q[12], alpha, f0, 0, f0 + 0.5f, 1);
        }
    }

    public static void clear() {
        STATE.clear();
        BUFFS.clear();
        SILENCED.clear();
        NOTES.clear();
    }

    /** 부드러운 빛 고리 (ADDITIVE_COLOR): 중심 c, 반축 ax, ay. */
    static void glowRing(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d ax, Vec3d ay, float rad, float w, int col, float alpha, int seg) {
        float cr = r(col), cg = g(col), cb = b(col);
        for (int band = 0; band < 2; band++) {
            float r0 = band == 0 ? rad - w : rad, r1 = band == 0 ? rad : rad + w;
            float a0 = band == 0 ? 0 : alpha, a1 = band == 0 ? alpha : 0;
            for (int i = 0; i < seg; i++) {
                double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
                Vec3d d0 = ax.multiply(Math.cos(t0)).add(ay.multiply(Math.sin(t0))), d1 = ax.multiply(Math.cos(t1)).add(ay.multiply(Math.sin(t1)));
                Vec3d p00 = c.add(d0.multiply(r0)), p01 = c.add(d0.multiply(r1)), p11 = c.add(d1.multiply(r1)), p10 = c.add(d1.multiply(r0));
                vc.vertex(m, (float) p00.x, (float) p00.y, (float) p00.z).color(cr, cg, cb, a0).next();
                vc.vertex(m, (float) p01.x, (float) p01.y, (float) p01.z).color(cr, cg, cb, a1).next();
                vc.vertex(m, (float) p11.x, (float) p11.y, (float) p11.z).color(cr, cg, cb, a1).next();
                vc.vertex(m, (float) p10.x, (float) p10.y, (float) p10.z).color(cr, cg, cb, a0).next();
            }
        }
    }

    /** 하늘에서 내려오는 빛기둥 (카메라를 향한 세로 판). */
    static void beam(VertexConsumer vc, Fx.Ctx c, Vec3d bottom, float height, float width, int col, float alpha) {
        Vec3d mid = bottom.add(0, height / 2, 0);
        Vec3d toCam = mid.multiply(-1);
        Vec3d side = new Vec3d(0, 1, 0).crossProduct(toCam);
        side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
        Draw.quadUV(vc, c.m, mid, new Vec3d(0, height / 2, 0), side.multiply(width), r(col), g(col), b(col), alpha, 0, 0, 1, 1);
    }

    // ================================================================ 작은 섬광
    static final class Burst extends Fx {
        final Vec3d p;
        final float s;
        final int col;

        Burst(Vec3d p, float s, int col) {
            super(12);
            this.p = p;
            this.s = s;
            this.col = col;
            for (int i = 0; i < 10 * s; i++) {
                Particles.P pp = Particles.spawn(Kind.SPARK, p, randDir().multiply(rf(0.08f, 0.25f) * s), rf(8, 14), rf(0.04f, 0.08f));
                pp.color(i % 2 == 0 ? col : WHITE_GOLD, 1f);
            }
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float nf = nearFade(rp, s);
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(f, c.m, c.camRot, rp.x, rp.y, rp.z, 1.3f * s * (0.4f + t), 0, r(col), g(col), b(col), (1 - t) * (1 - t) * nf);
            VertexConsumer rg = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING_SOFT));
            Draw.billboard(rg, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.3f + 1.6f * t), 0, r(col), g(col), b(col), (1 - t) * 0.7f * nf);
        }
    }

    // ================================================================ 평타: 음표탄
    static final class NoteShot extends Fx {
        final int caster, target, n, color;
        final Vec3d from, point;
        final int travel;
        boolean arrived;

        NoteShot(ClientWorld w, int caster, int target, Vec3d point, int n, int mode) {
            super(30);
            this.caster = caster;
            this.target = target;
            this.n = n;
            this.color = mix(GOLD, BUFF_COLOR[Math.min(2, mode)], 0.35f);
            Entity c = w.getEntityById(caster);
            Vec3d h = c != null ? HandTracker.hand(c, 0, 1f).add(c.getRotationVec(1f).multiply(0.5)) : point;
            this.from = h;
            Entity t = target >= 0 ? w.getEntityById(target) : null;
            this.point = point;
            double dist = (t != null ? Effects.chest(t, 1f) : point).distanceTo(h);
            this.travel = t != null ? Math.max(2, Math.min(5, (int) (dist / 3.5))) : 6;
            for (int i = 0; i < 4; i++) Particles.spawn(Kind.SPARK, h, randDir().multiply(0.06), 6, 0.04f).color(color, 1f);
        }

        Vec3d end(ClientWorld w, float td) {
            Entity t = target >= 0 ? w.getEntityById(target) : null;
            return t != null ? Effects.chest(t, td) : point;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age == travel && !arrived) {
                arrived = true;
                Vec3d e = end(w, 1f);
                if (target >= 0) {
                    VfxManager.add(new Burst(e, n > 1 ? 0.9f : 0.5f, color));
                    for (int i = 0; i < n * 2; i++) note(e, randDir().multiply(0.12).add(0, 0.05, 0), 14, 0.12f, color);
                }
            }
            if (age < travel) {
                Vec3d p = from.lerp(end(w, 1f), (age + 0.5) / travel);
                Particles.P pp = Particles.spawn(Kind.GLOW, p, Vec3d.ZERO, 8, 0.07f);
                pp.color(color, 0.8f);
            }
            return age < travel + 1 && super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            if (a >= travel) return;
            Vec3d e = end(c.world, c.tickDelta);
            Vector3f rv = c.camRot.transform(new Vector3f(1, 0, 0)), uv = c.camRot.transform(new Vector3f(0, 1, 0));
            Vec3d right = new Vec3d(rv.x, rv.y, rv.z), up = new Vec3d(uv.x, uv.y, uv.z);
            Vec3d d = e.subtract(from);
            Vec3d side = d.crossProduct(new Vec3d(0, 1, 0));
            side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
            Vec3d[] ps = new Vec3d[n];
            for (int i = 0; i < n; i++) {
                float k = MathHelper.clamp((a - i * 0.35f) / travel, 0, 1);
                double off = n > 1 ? (i - 1) * 0.35 * Math.sin(k * Math.PI) : 0;
                ps[i] = from.add(d.multiply(k)).add(side.multiply(off)).add(0, Math.sin(k * Math.PI) * 0.25, 0).subtract(c.cam);
            }
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            for (Vec3d p : ps) Draw.streak(st, c.m, p.subtract(d.normalize().multiply(0.45)), d, 1.0f, 0.1f, r(color), g(color), b(color), 0.6f);
            VertexConsumer nv = c.imm.getBuffer(AWRenderLayers.additive(NOTE));
            for (int i = 0; i < n; i++) {
                float s = 0.28f;
                int fr = (i + age) % 2;
                Draw.quadUV(nv, c.m, ps[i], right.multiply(s), up.multiply(s), r(color), g(color), b(color), 1f, fr * 0.5f, 0, fr * 0.5f + 0.5f, 1);
            }
        }
    }

    // ================================================================ 1. 용맹의 찬가: 소리의 파동
    static final class Wave extends Fx {
        final int caster;
        final Vec3d p;
        final float r;
        final boolean h;
        final int col;

        Wave(ClientWorld w, int caster, Vec3d p, float r, boolean h, int col) {
            super(22);
            this.caster = caster;
            this.p = p;
            this.r = r;
            this.h = h;
            this.col = col;
            noteBurst(p.add(0, 1.2, 0), h ? 26 : 16, h ? 0.4f : 0.3f, col);
            noteBurst(p.add(0, 0.6, 0), 10, 0.25f, GOLD);
            if (h) VfxManager.add(new Burst(p.add(0, 1.2, 0), 1.6f, WHITE_GOLD));
            CameraController.hitstop(1.5f);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            Vec3d base = p.add(0, 0.06, 0).subtract(c.cam);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            for (int k = 0; k < 3; k++) {
                float t = MathHelper.clamp((a - k * 3) / 12f, 0, 1);
                if (t <= 0 || t >= 1) continue;
                float e = 1 - (1 - t) * (1 - t);
                Draw.flat(sw, c.m, base.x, base.y + k * 0.02, base.z, r * e, 0, r(col), g(col), b(col), (1 - t) * 0.8f);
            }
            // 공중의 소리 고리 (가슴 높이, 위아래로 벌어짐)
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (int k = 0; k < (h ? 4 : 3); k++) {
                float t = MathHelper.clamp((a - k * 2.5f) / 10f, 0, 1);
                if (t <= 0 || t >= 1) continue;
                float rad = 0.6f + t * r * 0.8f;
                Vec3d cc = base.add(0, 1.1 + (k - 1) * 0.35 * t, 0);
                glowRing(gl, c.m, cc, new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), rad, 0.18f, k % 2 == 0 ? col : WHITE_GOLD, (1 - t) * 0.7f, 48);
            }
            // 원형 마법진
            float fade = Math.min(1, a / 3f) * Math.max(0, 1 - (a - 12) / 10f);
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y + 0.01, base.z, 2.4f + a * 0.05f, a * 0.06f, r(GOLD), g(GOLD), b(GOLD), 0.8f * fade);
        }
    }

    // ================================================================ 버프가 걸리는 순간 (빛기둥 + 고리)
    static final class BuffPulse extends Fx {
        final int id, kind;

        BuffPulse(int id, int kind) {
            super(14);
            this.id = id;
            this.kind = kind;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(id);
            if (e != null && age == 0) {
                int col = kind < 0 ? WHITE_GOLD : BUFF_COLOR[kind];
                note(e.getPos().add(0, e.getHeight() + 0.3, 0), new Vec3d(0, 0.06, 0), 22, 0.22f, col);
                for (int i = 0; i < 10; i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.5f, 0.5f), rf(0f, 0.3f), rf(-0.5f, 0.5f)), new Vec3d(0, rf(0.08f, 0.16f), 0), rf(10, 16), 0.07f);
                    pp.color(col, 1f);
                    pp.drag = 0.94f;
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float t = t(c);
            int col = kind < 0 ? WHITE_GOLD : BUFF_COLOR[kind];
            Vec3d p = e.getLerpedPos(c.tickDelta).subtract(c.cam);
            float fade = (1 - t);
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            if (!Effects.isLocalFp(e)) beam(st, c, p, 2.6f * (0.4f + t), 0.5f, col, 0.6f * fade);
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING_SOFT));
            Draw.flat(ring, c.m, p.x, p.y + 0.05, p.z, 0.4f + 1.2f * t, 0, r(col), g(col), b(col), 0.8f * fade);
        }
    }

    // ================================================================ 2. 수호의 성역
    static final class Sanctuary extends Fx {
        final Vec3d p;
        final float rad;
        final boolean h;

        Sanctuary(Vec3d p, float rad, int d, boolean h) {
            super(d);
            this.p = p;
            this.rad = rad;
            this.h = h;
            noteBurst(p.add(0, 1, 0), 18, 0.3f, BUFF_COLOR[1]);
            VfxManager.add(new Burst(p.add(0, 0.5, 0), 1.4f, BUFF_COLOR[1]));
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age % 3 == 0) {
                double a = R.nextDouble() * Math.PI * 2, d = Math.sqrt(R.nextDouble()) * rad;
                Particles.P pp = Particles.spawn(Kind.GLOW, p.add(Math.cos(a) * d, 0.1, Math.sin(a) * d), new Vec3d(0, rf(0.03f, 0.07f), 0), rf(20, 30), rf(0.05f, 0.09f));
                pp.color(R.nextBoolean() ? GOLD : BUFF_COLOR[1], 0.9f);
                pp.drag = 0.99f;
                if (age % 12 == 0) note(p.add(Math.cos(a) * d * 0.7, 0.4, Math.sin(a) * d * 0.7), new Vec3d(0, 0.04, 0), 26, 0.16f, GOLD);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float grow = Effects.outBack(Math.min(1, a / 8f));
            float fade = Math.min(1, (life - a) / 10f);
            float R0 = rad * grow;
            Vec3d base = p.add(0, 0.03, 0).subtract(c.cam);
            // 바닥 마법진
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y, base.z, R0 * 1.02f, a * 0.015f, r(GOLD), g(GOLD), b(GOLD), 0.75f * fade);
            Draw.flat(ci, c.m, base.x, base.y + 0.01, base.z, R0 * 0.55f, -a * 0.03f, r(BUFF_COLOR[1]), g(BUFF_COLOR[1]), b(BUFF_COLOR[1]), 0.5f * fade);
            // 돔 (반구): 가장자리가 진하고 위로 갈수록 옅어짐
            VertexConsumer dm = c.imm.getBuffer(AWRenderLayers.additive(DOME));
            int lat = 8, lon = 32;
            float shimmer = 0.5f + 0.5f * MathHelper.sin(a * 0.2f);
            for (int i = 0; i < lat; i++) {
                double th0 = Math.PI / 2 * i / lat, th1 = Math.PI / 2 * (i + 1) / lat;
                float al0 = (float) (0.5 * (1 - i / (double) lat) + 0.1) * fade, al1 = (float) (0.5 * (1 - (i + 1) / (double) lat) + 0.1) * fade;
                for (int j = 0; j < lon; j++) {
                    double ph0 = 2 * Math.PI * j / lon, ph1 = 2 * Math.PI * (j + 1) / lon;
                    float u0 = j / (float) lon * 6, u1 = (j + 1) / (float) lon * 6, v0 = i / (float) lat * 1.5f + a * 0.01f, v1 = (i + 1) / (float) lat * 1.5f + a * 0.01f;
                    float cr = MathHelper.lerp(shimmer, 1f, 0.6f), cg = MathHelper.lerp(shimmer, 0.86f, 0.85f), cb = MathHelper.lerp(shimmer, 0.5f, 1f);
                    dv(dm, c.m, base, R0, th0, ph0, cr, cg, cb, al0, u0, v0);
                    dv(dm, c.m, base, R0, th1, ph0, cr, cg, cb, al1, u0, v1);
                    dv(dm, c.m, base, R0, th1, ph1, cr, cg, cb, al1, u1, v1);
                    dv(dm, c.m, base, R0, th0, ph1, cr, cg, cb, al0, u1, v0);
                }
            }
            // 가장자리 빛 고리
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            glowRing(gl, c.m, base.add(0, 0.05, 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), R0, 0.35f, GOLD, 0.8f * fade, 64);
            // 가운데 빛기둥
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            beam(st, c, base, R0 * 1.1f, 0.35f, WHITE_GOLD, 0.35f * fade);
        }

        static void dv(VertexConsumer vc, Matrix4f m, Vec3d c, float rad, double th, double ph, float r, float g, float b, float a, float u, float v) {
            float x = (float) (c.x + Math.cos(th) * Math.cos(ph) * rad);
            float y = (float) (c.y + Math.sin(th) * rad);
            float z = (float) (c.z + Math.cos(th) * Math.sin(ph) * rad);
            Draw.v(vc, m, x, y, z, r, g, b, a, u, v);
        }
    }

    // ================================================================ 3. 천상의 종
    static final class Bell extends Fx {
        static final float[][] PROFILE = {
                {0.0f, 2.75f}, {0.32f, 2.72f}, {0.55f, 2.6f}, {0.72f, 2.3f}, {0.8f, 1.9f}, {0.86f, 1.45f}, {0.95f, 1.0f},
                {1.12f, 0.55f}, {1.38f, 0.2f}, {1.52f, 0.04f}, {1.5f, -0.02f}, {1.3f, 0.06f}};
        final Vec3d p;
        final int rings;
        final boolean h;
        final float scale;
        boolean landed;

        Bell(ClientWorld w, Vec3d p, int rings, boolean h) {
            super(11 + (rings - 1) * 7 + 22);
            this.p = p;
            this.rings = rings;
            this.h = h;
            this.scale = h ? 1.2f : 1f;
        }

        float y(float a) {
            if (a < 8) {
                float k = a / 8f;
                return 16 * (1 - k * k);
            }
            float endT = 11 + (rings - 1) * 7 + 10;
            if (a > endT) {
                float k = (a - endT) / 12f;
                return 6 * k * k;
            }
            return 0;
        }

        float alpha(float a) {
            float endT = 11 + (rings - 1) * 7 + 10;
            return a > endT ? Math.max(0, 1 - (a - endT) / 12f) : Math.min(1, a / 2f);
        }

        float wobble(float a) {
            float w = 0;
            if (a >= 8) w += 7 * (float) Math.sin((a - 8) * 1.6) * (float) Math.exp(-(a - 8) / 4.0);
            for (int k = 0; k < rings; k++) {
                float tr = 11 + k * 7;
                if (a >= tr) w += 9 * (float) Math.sin((a - tr) * 1.3) * (float) Math.exp(-(a - tr) / 5.0);
            }
            return w;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age == 8 && !landed) {
                landed = true;
                CameraController.hitstop(3f);
                VfxManager.add(new Burst(p.add(0, 0.6, 0), 1.6f, WHITE_GOLD));
                BlockState st = Effects.groundState(w, p);
                if (st != null) for (int i = 0; i < 14; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    Debris.spawn(st, p.add(Math.cos(a) * 1.4, 0.2, Math.sin(a) * 1.4), new Vec3d(Math.cos(a) * 0.25, rf(0.25f, 0.55f), Math.sin(a) * 0.25), rf(0.2f, 0.4f), rf(18, 28), null, 0, true);
                }
                for (int i = 0; i < 18; i++) {
                    double a = i * Math.PI * 2 / 18;
                    Particles.spawn(Kind.SMOKE_ADD, p.add(Math.cos(a) * 1.5, 0.15, Math.sin(a) * 1.5), new Vec3d(Math.cos(a) * 0.15, 0.02, Math.sin(a) * 0.15), rf(14, 22), rf(0.4f, 0.6f)).color(0xC8B48A, 0.4f);
                }
            }
            for (int k = 0; k < rings; k++) {
                if (age == 11 + k * 7) {
                    noteBurst(p.add(0, 1.6 * scale, 0), 14, 0.35f, k == 0 ? BUFF_COLOR[2] : GOLD);
                    VfxManager.add(new Burst(p.add(0, 2.2 * scale, 0), 1.0f, GOLD));
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            Vec3d base = p.subtract(c.cam);
            // 예고: 바닥의 원 + 그림자
            if (a < 12) {
                float k = Math.min(1, a / 8f);
                VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
                Draw.flat(ci, c.m, base.x, base.y + 0.03, base.z, 3.5f * k, a * 0.05f, r(GOLD), g(GOLD), b(GOLD), 0.8f * (a < 8 ? 1 : (12 - a) / 4f));
                VertexConsumer sh = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
                Draw.flat(sh, c.m, base.x, base.y + 0.02, base.z, 1.8f * k, 0, 0, 0, 0, 0.5f * k);
            }
            // 종소리 파동
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            for (int k = -1; k < rings; k++) {
                float tr = k < 0 ? 8 : 11 + k * 7;
                float t = (a - tr) / 12f;
                if (t <= 0 || t >= 1) continue;
                float e = 1 - (1 - t) * (1 - t);
                int col = k <= 0 ? (k < 0 ? WHITE_GOLD : BUFF_COLOR[2]) : GOLD;
                Draw.flat(sw, c.m, base.x, base.y + 0.05, base.z, 1.5f + e * (k < 0 ? 5 : 8), 0, r(col), g(col), b(col), (1 - t) * 0.85f);
            }
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (int k = 0; k < rings; k++) {
                float t = (a - (11 + k * 7)) / 12f;
                if (t <= 0 || t >= 1) continue;
                float e = 1 - (1 - t) * (1 - t);
                int col = k == 0 ? BUFF_COLOR[2] : GOLD;
                for (int j = 0; j < 2; j++) {
                    Vec3d cc = base.add(0, 0.8 + j * 1.0, 0);
                    glowRing(gl, c.m, cc, new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), 1.6f + e * 6, 0.25f, col, (1 - t) * 0.6f, 48);
                }
            }
            // 종 본체 (돌림 모양 메시, 엔티티 조명)
            float yy = y(a), al = alpha(a);
            if (al <= 0.01f) return;
            c.matrices.push();
            c.matrices.translate(base.x, base.y + yy, base.z);
            c.matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(wobble(a)));
            c.matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(25));
            c.matrices.scale(scale, scale, scale);
            Matrix4f m = c.matrices.peek().getPositionMatrix();
            Matrix3f nm = c.matrices.peek().getNormalMatrix();
            VertexConsumer vc = c.imm.getBuffer(RenderLayer.getEntityTranslucent(BELL));
            int seg = 28;
            for (int i = 0; i < PROFILE.length - 1; i++) {
                float r0 = PROFILE[i][0], y0 = PROFILE[i][1], r1 = PROFILE[i + 1][0], y1 = PROFILE[i + 1][1];
                float v0 = (2.75f - y0) / 2.8f, v1 = (2.75f - y1) / 2.8f;
                float dy = y1 - y0, dr = r1 - r0;
                float nlen = (float) Math.sqrt(dy * dy + dr * dr);
                float ny = -dr / nlen, nr = dy / nlen;
                if (nr < 0) {
                    nr = -nr;
                    ny = -ny;
                }
                for (int j = 0; j < seg; j++) {
                    double a0 = j * Math.PI * 2 / seg, a1 = (j + 1) * Math.PI * 2 / seg;
                    float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                    float u0 = j / (float) seg, u1 = (j + 1) / (float) seg;
                    bv(vc, m, nm, c0 * r0, y0, s0 * r0, u0, v0, c0 * nr, ny, s0 * nr, al);
                    bv(vc, m, nm, c0 * r1, y1, s0 * r1, u0, v1, c0 * nr, ny, s0 * nr, al);
                    bv(vc, m, nm, c1 * r1, y1, s1 * r1, u1, v1, c1 * nr, ny, s1 * nr, al);
                    bv(vc, m, nm, c1 * r0, y0, s1 * r0, u1, v0, c1 * nr, ny, s1 * nr, al);
                }
            }
            // 꼭대기 고리
            float hr = 0.22f;
            for (int j = 0; j < 12; j++) {
                double a0 = j * Math.PI * 2 / 12, a1 = (j + 1) * Math.PI * 2 / 12;
                float x0 = (float) Math.cos(a0) * hr, y0 = 2.95f + (float) Math.sin(a0) * hr, x1 = (float) Math.cos(a1) * hr, y1 = 2.95f + (float) Math.sin(a1) * hr;
                bv(vc, m, nm, x0, y0, -0.05f, 0.5f, 0.1f, 0, 0, -1, al);
                bv(vc, m, nm, x1, y1, -0.05f, 0.5f, 0.1f, 0, 0, -1, al);
                bv(vc, m, nm, x1 * 0.6f, 2.95f + (y1 - 2.95f) * 0.6f, -0.05f, 0.5f, 0.12f, 0, 0, -1, al);
                bv(vc, m, nm, x0 * 0.6f, 2.95f + (y0 - 2.95f) * 0.6f, -0.05f, 0.5f, 0.12f, 0, 0, -1, al);
            }
            c.matrices.pop();
            // 종 둘레의 빛
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Vec3d mid = base.add(0, yy + 1.4 * scale, 0);
            Draw.billboard(f, c.m, c.camRot, mid.x, mid.y, mid.z, 3.2f * scale, 0, 1f, 0.82f, 0.4f, 0.35f * al * nearFade(mid, 3));
            if (a < 8) {
                VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
                beam(st, c, base.add(0, yy + 2, 0), 10, 0.9f, WHITE_GOLD, 0.35f);
            }
        }

        static void bv(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float nx, float ny, float nz, float a) {
            vc.vertex(m, x, y, z).color(1f, 1f, 1f, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(n, nx, ny, nz).next();
        }
    }

    // ================================================================ 4. 응답송 (반격 자세): 앞에 세운 빛의 마법진
    static final class Ward extends Fx {
        final int id;

        Ward(int id, int d) {
            super(d);
            this.id = id;
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float a = age + c.tickDelta;
            float vis = Math.min(1, a / 3f) * Math.min(1, (life - a) / 3f);
            boolean fp = Effects.isLocalFp(e);
            float yaw = MathHelper.lerp(c.tickDelta, e.prevYaw, e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
            Vec3d f = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
            Vec3d rgt = new Vec3d(-f.z, 0, f.x);
            Vec3d cc = e.getLerpedPos(c.tickDelta).add(0, 1.15, 0).add(f.multiply(fp ? 1.3 : 0.8)).subtract(c.cam);
            float s = (fp ? 0.9f : 1.0f) * (0.8f + 0.2f * vis);
            double rot = a * 0.08;
            Vec3d ax = rgt.multiply(Math.cos(rot) * s).add(new Vec3d(0, 1, 0).multiply(Math.sin(rot) * s));
            Vec3d ay = new Vec3d(0, 1, 0).multiply(Math.cos(rot) * s).subtract(rgt.multiply(Math.sin(rot) * s));
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.quad(ci, c.m, cc, ax, ay, r(GOLD), g(GOLD), b(GOLD), (fp ? 0.55f : 0.9f) * vis);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            Vec3d body = e.getLerpedPos(c.tickDelta).add(0, 1.0, 0).subtract(c.cam);
            glowRing(gl, c.m, body.add(0, -0.9, 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), 1.1f, 0.25f, GOLD, 0.6f * vis, 40);
            if (!fp) Draw.sphereColor(gl, c.m, body, 1.15f, 8, 14, 0.25f, 0.2f, 0.08f, 0.1f * vis);
        }
    }

    // ================================================================ 심판의 빛 (하늘에서 내리꽂히는 빛기둥)
    static final class Pillar extends Fx {
        final Vec3d p;
        final boolean big;

        Pillar(ClientWorld w, Vec3d p, boolean big) {
            super(18);
            this.p = p;
            this.big = big;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age == 2) {
                VfxManager.add(new Burst(p.add(0, 0.4, 0), big ? 1.6f : 1.1f, WHITE_GOLD));
                for (int i = 0; i < (big ? 18 : 10); i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, p.add(rf(-0.4f, 0.4f), rf(0, 3), rf(-0.4f, 0.4f)), new Vec3d(0, rf(0.1f, 0.3f), 0), rf(10, 18), rf(0.06f, 0.1f));
                    pp.color(WHITE_GOLD, 1f);
                }
                if (big) CameraController.hitstop(1.5f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float drop = Math.min(1, a / 2.5f);
            float fade = a < 10 ? 1 : Math.max(0, 1 - (a - 10) / 8f);
            float H = 28, w = big ? 1.1f : 0.7f;
            Vec3d base = p.subtract(c.cam);
            Vec3d top = base.add(0, H, 0);
            Vec3d bottom = top.add(0, -H * drop, 0);
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            float len = (float) (top.y - bottom.y);
            beam(st, c, bottom, len, w * (1.2f - 0.4f * (a / life)), GOLD, 0.8f * fade);
            beam(st, c, bottom, len, w * 0.35f, 0xFFFFFF, 0.9f * fade);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            float t = Math.max(0, (a - 2) / 12f);
            if (t > 0 && t < 1) Draw.flat(sw, c.m, base.x, base.y + 0.05, base.z, 0.6f + t * (big ? 4 : 2.6f), 0, r(GOLD), g(GOLD), b(GOLD), (1 - t) * 0.9f);
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y + 0.04, base.z, (big ? 1.8f : 1.2f), a * 0.1f, r(GOLD), g(GOLD), b(GOLD), 0.8f * fade);
        }
    }

    // ================================================================ R. GLORIA: 천상의 빛, 거대한 광륜, 합창의 빛기둥
    static final class Ult extends Fx {
        final int caster;
        final Vec3d start, fwd;
        final float[][] rays = new float[12][];
        final float[][] choir = new float[12][];
        boolean bloomed;

        Ult(ClientWorld w, int caster, Vec3d start) {
            super(80);
            this.caster = caster;
            this.start = start;
            Entity c = w.getEntityById(caster);
            float yaw = c != null ? c.getYaw() : 0;
            fwd = new Vec3d(-MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE), 0, MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE));
            for (int i = 0; i < rays.length; i++) rays[i] = new float[]{rf(0, 6.28f), rf(5, 13), rf(0.8f, 1.8f), rf(0, 6.28f)};
            for (int i = 0; i < choir.length; i++) choir[i] = new float[]{(float) (i * Math.PI * 2 / choir.length), rf(0, 4)};
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity c = w.getEntityById(caster);
            if (age > 4 && age < 68) {
                // 빛 가루가 내려옴
                for (int i = 0; i < 4; i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, start.add(rf(-13, 13), rf(8, 14), rf(-13, 13)), new Vec3d(0, -rf(0.08f, 0.16f), 0), rf(40, 60), rf(0.05f, 0.1f));
                    pp.color(R.nextBoolean() ? GOLD : WHITE_GOLD, 0.9f);
                    pp.drag = 1f;
                }
                if (c != null && age % 2 == 0) {
                    double a = age * 0.35;
                    note(c.getPos().add(Math.cos(a) * 1.4, 0.2, Math.sin(a) * 1.4), new Vec3d(0, 0.12, 0), 26, 0.2f, age % 4 == 0 ? GOLD : WHITE_GOLD);
                }
            }
            if (age == 48 && !bloomed) {
                bloomed = true;
                Vec3d cp = c != null ? c.getPos().add(0, 1.2, 0) : start.add(0, 3, 0);
                VfxManager.add(new Burst(cp, 3f, WHITE_GOLD));
                noteBurst(cp, 40, 0.7f, GOLD);
                noteBurst(cp, 24, 0.45f, WHITE_GOLD);
                CameraController.hitstop(3f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float in = MathHelper.clamp(a / 14f, 0, 1), out = 1 - MathHelper.clamp((a - 66) / 12f, 0, 1);
            float k = in * out;
            Vec3d base = start.subtract(c.cam);
            // 하늘에서 쏟아지는 빛줄기
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            for (float[] ry : rays) {
                double ang = ry[0] + a * 0.006;
                Vec3d b = base.add(Math.cos(ang) * ry[1], 0, Math.sin(ang) * ry[1]);
                float flick = 0.6f + 0.4f * MathHelper.sin(a * 0.15f + ry[3]);
                beam(st, c, b, 40, ry[2], WHITE_GOLD, 0.22f * k * flick);
            }
            // 바닥의 거대한 마법진
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            float grow = Effects.outBack(Math.min(1, a / 14f));
            Draw.flat(ci, c.m, base.x, base.y + 0.04, base.z, 9f * grow, a * 0.01f, r(GOLD), g(GOLD), b(GOLD), 0.75f * k);
            Draw.flat(ci, c.m, base.x, base.y + 0.05, base.z, 4.5f * grow, -a * 0.02f, 1f, 0.95f, 0.8f, 0.6f * k);
            // 합창의 빛기둥 (원 둘레에서 솟아오름)
            st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            float rise = MathHelper.clamp((a - 10) / 20f, 0, 1);
            for (float[] ch : choir) {
                double ang = ch[0] + a * 0.01;
                Vec3d b = base.add(Math.cos(ang) * 10, 0, Math.sin(ang) * 10);
                beam(st, c, b, 7 * rise, 0.45f, GOLD, 0.7f * k * rise);
            }
            // 시전자 뒤의 거대한 광륜
            Entity ce = c.world.getEntityById(caster);
            if (ce != null) {
                Vec3d cp = ce.getLerpedPos(c.tickDelta);
                float hk = MathHelper.clamp((a - 14) / 12f, 0, 1) * out;
                if (hk > 0) {
                    Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
                    Vec3d hc = cp.add(0, 2.3, 0).subtract(fwd.multiply(0.7)).subtract(c.cam);
                    VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
                    float rr = 1.7f + 0.3f * Effects.outBack(hk);
                    glowRing(gl, c.m, hc, right, new Vec3d(0, 1, 0), rr, 0.28f, WHITE_GOLD, 0.95f * hk, 64);
                    glowRing(gl, c.m, hc, right, new Vec3d(0, 1, 0), rr * 1.25f, 0.12f, GOLD, 0.6f * hk, 64);
                    // 광륜의 빛살
                    st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
                    for (int i = 0; i < 16; i++) {
                        double ang = i * Math.PI * 2 / 16 + a * 0.01;
                        Vec3d d = right.multiply(Math.cos(ang)).add(new Vec3d(0, 1, 0).multiply(Math.sin(ang)));
                        Draw.streak(st, c.m, hc.add(d.multiply(rr * 1.55)), d, rr * 0.7f, 0.08f, 1f, 0.9f, 0.6f, 0.5f * hk);
                    }
                    VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
                    Draw.billboard(f, c.m, c.camRot, hc.x, hc.y, hc.z, rr * 2.4f, 0, 1f, 0.85f, 0.5f, 0.18f * hk * nearFade(hc, 2));
                }
            }
            // GLORIA: 퍼지는 빛의 파동
            float t = (a - 48) / 14f;
            if (t > 0 && t < 1) {
                float e = 1 - (1 - t) * (1 - t);
                VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
                Draw.flat(sw, c.m, base.x, base.y + 0.1, base.z, 2 + e * 17, 0, 1f, 0.92f, 0.65f, (1 - t) * 0.95f);
                VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
                glowRing(gl, c.m, base.add(0, 1.5, 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), 1 + e * 15, 0.8f, WHITE_GOLD, (1 - t) * 0.7f, 72);
            }
        }
    }

    private CantorFx() {
    }
}
