package com.abilitywar.client.fx.blade;

import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.AfterImages;
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
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.abilitywar.client.fx.Effects.R;
import static com.abilitywar.client.fx.Effects.randDir;
import static com.abilitywar.client.fx.Effects.rf;
import static com.abilitywar.client.fx.Particles.Kind;

/** 마검사 이펙트. */
public final class BladeFx {
    public static final int VIOLET = 0xB44CFF;
    public static final int PINK = 0xF0B8FF;
    public static final int DEEP = 0x5A14A0;
    public static final int DARK = 0x14041E;

    public static boolean spawn(String type, NbtCompound n, ClientWorld w, Vec3d p) {
        switch (type) {
            case "blade_slash" -> VfxManager.add(new Slash(w, n.getInt("e"), n.getFloat("yaw"), n.getFloat("tilt"), n.getFloat("s"), n.getInt("k")));
            case "blade_afterimage" -> {
                AfterImages.requestAt(n.getInt("e"), p, VIOLET, 14);
                wisps(p, 10);
            }
            case "blade_mark" -> {
                MARKS.put(n.getInt("e"), (long) n.getInt("d"));
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) VfxManager.add(new Burst(e.getPos().add(0, e.getHeight() + 0.5, 0), 0.6f, 8));
            }
            case "blade_mark_burst" -> {
                MARKS.remove(n.getInt("e"));
                VfxManager.add(new Burst(p, 1.5f, 14));
            }
            case "blade_charge" -> VfxManager.add(new Charge(n.getInt("e"), n.getBoolean("enh")));
            case "blade_draw" -> VfxManager.add(new DrawTrail(w, n.getInt("e"), new Vec3d(n.getDouble("fx"), n.getDouble("fy"), n.getDouble("fz")), p, n.getBoolean("enh")));
            case "blade_cut" -> VfxManager.add(new Cut(p, n.getBoolean("enh")));
            case "blade_crescent" -> VfxManager.add(new Crescent(p, new Vec3d(n.getDouble("dx"), n.getDouble("dy"), n.getDouble("dz")), n.getBoolean("fast")));
            case "blade_rift" -> VfxManager.add(new Rift(p, 1f, 16));
            case "blade_phantom_burst" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) {
                    AfterImages.request(e.getId(), 3, 1, PINK, 10);
                    VfxManager.add(new Burst(e.getPos().add(0, 1, 0), 1.2f, 12));
                }
            }
            case "blade_burst" -> VfxManager.add(new Explosion(w, p, n.contains("s") ? n.getFloat("s") : 1f));
            case "blade_guard" -> VfxManager.add(new Guard(n.getInt("c"), n.getInt("d")));
            case "blade_guard_fade" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                if (e != null) wisps(e.getPos().add(0, 1, 0), 12);
            }
            case "blade_parry" -> {
                Entity e = w.getEntityById(n.getInt("c"));
                if (e != null) VfxManager.add(new Parry(e));
                if (e == MinecraftClient.getInstance().player) com.abilitywar.client.fx.FpEffects.counterFlash();
            }
            case "blade_phantom" -> {
                Vec3d to = new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz"));
                VfxManager.add(new PhantomSword(w, n.getInt("e"), p, n.getFloat("yaw"), to));
                // 차원 포털: 들어가는 곳과 나오는 곳
                Vec3d dir = to.subtract(p);
                VfxManager.add(new Portal(p, dir, 16));
                VfxManager.add(new Portal(to, dir, 14));
            }
            case "blade_ult" -> VfxManager.add(new Ult(w, n, p));
            default -> {
                return false;
            }
        }
        return true;
    }

    static void wisps(Vec3d p, int n) {
        for (int i = 0; i < n; i++) {
            Particles.spawn(Kind.WISP_ADD, p.add(rf(-0.4f, 0.4f), rf(0, 1.6f), rf(-0.4f, 0.4f)), randDir().multiply(0.03).add(0, 0.03, 0), rf(10, 18), rf(0.2f, 0.4f)).color(i % 2 == 0 ? VIOLET : PINK, 0.7f);
            Particles.spawn(Kind.SMOKE_DARK, p.add(rf(-0.3f, 0.3f), rf(0.2f, 1.6f), rf(-0.3f, 0.3f)), randDir().multiply(0.02).add(0, 0.02, 0), rf(14, 22), rf(0.3f, 0.5f)).color(DARK, 0.45f);
        }
    }

    // ================================================================ 마력 표식
    static final Map<Integer, Long> MARKS = new HashMap<>();
    private static final Map<Integer, Long> MARK_END = new HashMap<>();

    public static void tick(ClientWorld w) {
        long now = w.getTime();
        for (Iterator<Map.Entry<Integer, Long>> it = MARKS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> en = it.next();
            MARK_END.putIfAbsent(en.getKey(), now + en.getValue());
            Entity e = w.getEntityById(en.getKey());
            if (e == null || !e.isAlive() || now > MARK_END.get(en.getKey())) {
                MARK_END.remove(en.getKey());
                it.remove();
                continue;
            }
            if (now % 3 == 0)
                Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.4f, 0.4f), rf(0.2f, e.getHeight()), rf(-0.4f, 0.4f)), new Vec3d(0, 0.03, 0), 14, 0.06f).color(VIOLET, 1f);
        }
    }

    public static void render(Fx.Ctx c) {
        for (int id : MARKS.keySet()) {
            Entity e = c.world.getEntityById(id);
            if (e == null) continue;
            Vec3d p = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() + 0.55, 0).subtract(c.cam);
            float t = c.world.getTime() + c.tickDelta;
            float a = 0.85f * Fx.nearFade(p, 0.6f);
            VertexConsumer v = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
            Draw.billboard(v, c.m, c.camRot, p.x, p.y, p.z, 0.36f, t * 0.06f, Fx.r(VIOLET), Fx.g(VIOLET), Fx.b(VIOLET), a);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(g, c.m, c.camRot, p.x, p.y, p.z, 0.5f, 0, 0.7f, 0.3f, 1f, a * 0.5f);
            Vec3d f = e.getLerpedPos(c.tickDelta).add(0, 0.03, 0).subtract(c.cam);
            Draw.flat(c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE)), c.m, f.x, f.y, f.z, e.getWidth() * 0.9f + 0.4f, -t * 0.03f, 0.6f, 0.25f, 1f, a * 0.55f);
        }
    }

    public static void clear() {
        MARKS.clear();
        MARK_END.clear();
    }

    // ================================================================ 검격 (호)
    static final class Slash extends Fx {
        final int entity, kind;
        final float yaw, tilt, scale;
        Vec3d center;

        Slash(ClientWorld w, int entity, float yaw, float tilt, float scale, int kind) {
            super(kind == 1 ? 10 : 8);
            this.entity = entity;
            this.yaw = yaw;
            this.tilt = tilt;
            this.scale = scale;
            this.kind = kind;
            Entity e = w.getEntityById(entity);
            center = e != null ? e.getPos() : Vec3d.ZERO;
            if (kind == 1 && e != null) {
                Vec3d[] b = basis(yaw, tilt);
                Vec3d c = e.getPos().add(0, 1.1, 0).add(b[0].multiply(1.2 * scale));
                Effects.sparks(c, 16, 0.5f, PINK, 0.1f);
            }
        }

        static Vec3d[] basis(float yaw, float tilt) {
            float y = yaw * MathHelper.RADIANS_PER_DEGREE;
            Vec3d f = new Vec3d(-MathHelper.sin(y), 0, MathHelper.cos(y));
            Vec3d right = new Vec3d(-f.z, 0, f.x);
            double tr = Math.toRadians(tilt);
            Vec3d u = right.multiply(Math.cos(tr)).add(new Vec3d(0, 1, 0).multiply(Math.sin(tr)));
            return new Vec3d[]{f, u, u.crossProduct(f).normalize()};
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(entity);
            Vec3d base = e != null ? e.getLerpedPos(c.tickDelta) : center;
            boolean fp = e != null && Effects.isLocalFp(e);
            float t = t(c);
            float head = Math.min(1, t * 3.4f), tail = Math.max(0, (t - 0.1f) * 1.5f);
            if (head <= tail) return;
            int arcs = kind == 2 ? 2 : 1;
            for (int arc = 0; arc < arcs; arc++) {
                Vec3d[] b = basis(yaw, tilt + arc * 90);
                Vec3d cc = base.add(0, 1.15, 0).add(b[0].multiply(fp ? 0.9 : 0.4));
                float rad = 1.35f * scale;
                int n = 22;
                Vec3d[] pts = new Vec3d[n];
                float[] wds = new float[n], als = new float[n];
                for (int i = 0; i < n; i++) {
                    float k = tail + (head - tail) * i / (n - 1);
                    float a = 1.5f + (-1.5f - 1.5f) * k;
                    pts[i] = cc.add(b[0].multiply(Math.cos(a) * rad * 0.7)).add(b[1].multiply(Math.sin(a) * rad)).subtract(c.cam);
                    float env = (float) Math.sin(Math.PI * i / (n - 1));
                    wds[i] = (kind == 1 ? 0.2f : 0.13f) * scale * (0.25f + 0.75f * env);
                    als[i] = env * (1 - t * t) * (fp ? 0.6f : 1f);
                }
                VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
                for (int i = 0; i < n; i++) wds[i] *= 2.6f;
                Draw.ribbon(dk, c.m, pts, n, wds, 0.06f, 0.0f, 0.1f, als, 0.6f, 0);
                VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
                for (int i = 0; i < n; i++) wds[i] /= 1.3f;
                Draw.ribbon(g, c.m, pts, n, wds, Fx.r(VIOLET), Fx.g(VIOLET), Fx.b(VIOLET), als, 0.6f, 0);
                for (int i = 0; i < n; i++) wds[i] /= 3.2f;
                Draw.ribbon(g, c.m, pts, n, wds, 1f, 0.92f, 1f, als, 0.6f, 0);
            }
        }
    }

    // ================================================================ 작은 폭발 (표식 등)
    static final class Burst extends Fx {
        final Vec3d p;
        final float s;

        Burst(Vec3d p, float s, int life) {
            super(life);
            this.p = p;
            this.s = s;
            for (int i = 0; i < 12; i++) {
                Particles.spawn(Kind.GLOW, p, randDir().multiply(rf(0.1f, 0.35f) * s), rf(10, 18), rf(0.05f, 0.09f)).color(i % 2 == 0 ? VIOLET : PINK, 1f);
                Particles.spawn(Kind.WISP_ADD, p, randDir().multiply(rf(0.03f, 0.1f) * s), rf(10, 16), rf(0.2f, 0.4f) * s).color(VIOLET, 0.7f);
            }
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float nf = nearFade(rp, s * 2);
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(f, c.m, c.camRot, rp.x, rp.y, rp.z, s * 1.5f * (0.4f + t), 0, 0.85f, 0.6f, 1f, (1 - t) * (1 - t) * nf);
            VertexConsumer r = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.billboard(r, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.3f + t * 1.5f), 0, r(VIOLET), g(VIOLET), b(VIOLET), (1 - t) * 0.8f * nf);
        }
    }

    // ================================================================ 1. 발도: 마력 집중
    static final class Charge extends Fx {
        final int id;
        final boolean enh;

        Charge(int id, boolean enh) {
            super(7);
            this.id = id;
            this.enh = enh;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(id);
            if (e != null) {
                Vec3d h = HandTracker.hand(e, 0, 1f);
                for (int i = 0; i < (enh ? 6 : 4); i++) {
                    Vec3d from = h.add(randDir().multiply(rf(1.0f, 2.2f)));
                    Particles.P pp = Particles.spawn(Kind.GLOW, from, h.subtract(from).multiply(0.16), 8, rf(0.05f, 0.09f));
                    pp.color(i % 2 == 0 ? VIOLET : PINK, 1f);
                    pp.drag = 1f;
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            Vec3d h = HandTracker.hand(e, 0, c.tickDelta).subtract(c.cam);
            float t = t(c);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            float s = (enh ? 0.9f : 0.6f) * (0.5f + t);
            Draw.billboard(g, c.m, c.camRot, h.x, h.y, h.z, s, 0, 0.7f, 0.3f, 1f, 0.8f * nearFade(h, s));
            VertexConsumer r = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.billboard(r, c.m, c.camRot, h.x, h.y, h.z, 1.2f * (1 - t) + 0.1f, 0, r(PINK), g(PINK), b(PINK), t * 0.8f * nearFade(h, 1));
        }
    }

    // ================================================================ 1. 발도: 지나간 자리의 잔상
    static final class DrawTrail extends Fx {
        final Vec3d a, b;
        final boolean enh;

        DrawTrail(ClientWorld w, int id, Vec3d from, Vec3d to, boolean enh) {
            super(26);
            this.a = from.add(0, 1.0, 0);
            this.b = to.add(0, 1.0, 0);
            this.enh = enh;
            Entity e = w.getEntityById(id);
            if (e != null) {
                for (int i = 0; i < 5; i++) AfterImages.requestAt(id, from.lerp(to, i / 5.0), i % 2 == 0 ? VIOLET : PINK, 10 + i * 2);
            }
            Vec3d d = b.subtract(a);
            for (int i = 0; i < 40; i++) {
                double k = R.nextDouble();
                Vec3d p = a.add(d.multiply(k)).add(rf(-0.4f, 0.4f), rf(-0.6f, 0.6f), rf(-0.4f, 0.4f));
                Particles.spawn(i % 3 == 0 ? Kind.STREAK : Kind.WISP_ADD, p, d.normalize().multiply(rf(0.05f, 0.3f)), rf(10, 22), rf(0.15f, 0.35f)).color(i % 2 == 0 ? VIOLET : PINK, 0.8f);
            }
            Effects.sparks(b, 18, 0.5f, PINK, 0.1f);
            CameraController.hitstop(enh ? 3f : 2f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d d = b.subtract(a);
            Vec3d flat = new Vec3d(-d.z, 0, d.x).normalize();
            int n = 16;
            Vec3d[] pts = new Vec3d[n];
            float[] wds = new float[n], als = new float[n];
            float flash = Math.max(0, 1 - t * 4);
            for (int i = 0; i < n; i++) {
                double k = (double) i / (n - 1);
                pts[i] = a.add(d.multiply(k)).add(0, Math.sin(k * Math.PI) * 0.15, 0).subtract(c.cam);
                float env = (float) Math.pow(Math.sin(Math.PI * k), 0.6);
                wds[i] = (enh ? 0.55f : 0.4f) * env * (1 - t * 0.4f);
                als[i] = env * (1 - t);
            }
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
            Draw.ribbon(dk, c.m, pts, n, wds, 0.05f, 0, 0.09f, als, 0.4f, -t * 2);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
            for (int i = 0; i < n; i++) wds[i] *= 0.7f;
            Draw.ribbon(g, c.m, pts, n, wds, Fx.r(VIOLET), Fx.g(VIOLET), Fx.b(VIOLET), als, 0.4f, -t * 2);
            for (int i = 0; i < n; i++) {
                wds[i] *= 0.25f + flash;
                als[i] = Math.min(1, als[i] * (0.4f + flash));
            }
            Draw.ribbon(g, c.m, pts, n, wds, 1f, 0.95f, 1f, als, 0.4f, 0);
        }
    }

    // ================================================================ 1. 발도: 베인 자리 (X자)
    static final class Cut extends Fx {
        final Vec3d p;
        final boolean enh;
        final float r0 = R.nextFloat() * 6.28f;

        Cut(Vec3d p, boolean enh) {
            super(10);
            this.p = p;
            this.enh = enh;
            Effects.sparks(p, enh ? 26 : 16, 0.55f, PINK, 0.1f);
            for (int i = 0; i < 10; i++) Particles.spawn(Kind.WISP_ADD, p, randDir().multiply(0.08), rf(8, 14), rf(0.3f, 0.5f)).color(VIOLET, 0.8f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float s = (enh ? 1.6f : 1.2f);
            float e = Math.min(1, t * 4);
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            for (int i = 0; i < 2; i++) {
                double ang = r0 + i * Math.PI / 2 + 0.6;
                Vec3d dir = new Vec3d(Math.cos(ang), Math.sin(ang), 0);
                Vec3d dirW = new Vec3d(dir.x, dir.y, 0);
                // 화면을 향한 판에서 대각선 획
                Vec3d right = new Vec3d(c.camRot.transform(new org.joml.Vector3f(1, 0, 0)));
                Vec3d up = new Vec3d(c.camRot.transform(new org.joml.Vector3f(0, 1, 0)));
                Vec3d axis = right.multiply(dirW.x).add(up.multiply(dirW.y));
                Draw.streak(st, c.m, rp.subtract(axis.multiply(s * e)), axis, s * 2 * e, 0.16f * (1 - t), 1f, 0.85f, 1f, (1 - t));
            }
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(f, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.5f + t), 0, 0.8f, 0.5f, 1f, (1 - t) * nearFade(rp, s));
        }
    }

    // ================================================================ 2. 파공참: 날아가는 초승달
    static final class Crescent extends Fx {
        Vec3d p;
        final Vec3d dir, side;
        final boolean fast;

        Crescent(Vec3d p, Vec3d dir, boolean fast) {
            super(13);
            this.p = p;
            this.dir = dir.normalize();
            Vec3d s = this.dir.crossProduct(new Vec3d(0, 1, 0));
            this.side = s.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : s.normalize();
            this.fast = fast;
        }

        Vec3d at(float a) {
            return p.add(dir.multiply(Math.min(a, 10) * 1.6));
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age < 10) {
                Vec3d c = at(age);
                Vec3d up = side.crossProduct(dir).normalize();
                for (int i = 0; i < 6; i++) {
                    double a = rf(-1.3f, 1.3f);
                    Vec3d q = c.add(up.multiply(Math.sin(a) * 1.3)).subtract(dir.multiply(Math.cos(a) * 0.6 - 0.3));
                    Particles.spawn(i % 2 == 0 ? Kind.WISP_ADD : Kind.GLOW, q, dir.multiply(rf(0.05f, 0.25f)), rf(8, 14), i % 2 == 0 ? rf(0.2f, 0.35f) : 0.07f).color(i % 3 == 0 ? PINK : VIOLET, 0.85f);
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float fade = a < 9 ? 1 : Math.max(0, 1 - (a - 9) / 4f);
            float grow = Math.min(1, a / 2f);
            Vec3d center = at(a);
            Vec3d up = side.crossProduct(dir).normalize();
            int n = 20;
            Vec3d[] pts = new Vec3d[n];
            float[] wds = new float[n], als = new float[n];
            float h = 1.45f * grow * (fast ? 1.15f : 1f);
            for (int i = 0; i < n; i++) {
                double k = (double) i / (n - 1);
                double ang = -1.35 + 2.7 * k;
                pts[i] = center.add(up.multiply(Math.sin(ang) * h)).subtract(dir.multiply((1 - Math.cos(ang)) * 0.75 * h)).subtract(c.cam);
                float env = (float) Math.sin(Math.PI * k);
                wds[i] = 0.42f * env * grow;
                als[i] = env * fade;
            }
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
            Draw.ribbon(dk, c.m, pts, n, wds, 0.05f, 0, 0.1f, als, 0.5f, -a * 0.2f);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
            for (int i = 0; i < n; i++) wds[i] *= 0.75f;
            Draw.ribbon(g, c.m, pts, n, wds, Fx.r(VIOLET), Fx.g(VIOLET), Fx.b(VIOLET), als, 0.5f, -a * 0.2f);
            for (int i = 0; i < n; i++) wds[i] *= 0.3f;
            Draw.ribbon(g, c.m, pts, n, wds, 1f, 0.92f, 1f, als, 0.5f, 0);
            Vec3d cp = center.subtract(c.cam);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(gl, c.m, c.camRot, cp.x, cp.y, cp.z, 2.2f * grow, 0, 0.6f, 0.25f, 1f, 0.45f * fade * nearFade(cp, 2));
        }
    }

    // ================================================================ 2. 공중의 마력 균열
    static final class Rift extends Fx {
        final Vec3d p;
        final float s;
        final long seed = R.nextLong();
        final double ang = R.nextDouble() * Math.PI;

        Rift(Vec3d p, float s, int life) {
            super(life);
            this.p = p;
            this.s = s;
            for (int i = 0; i < 16; i++)
                Particles.spawn(Kind.GLOW, p, randDir().multiply(rf(0.1f, 0.3f) * s), rf(10, 18), 0.07f).color(i % 2 == 0 ? VIOLET : PINK, 1f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            float open = Math.min(1, t * 5) * (1 - Math.max(0, (t - 0.6f) / 0.4f));
            Vec3d rp = p.subtract(c.cam);
            Vec3d axis = new Vec3d(Math.cos(ang) * 0.35, 1, Math.sin(ang) * 0.35).normalize().multiply(1.6 * s * (0.4 + 0.6 * open));
            VertexConsumer lg = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            Draw.lightning(lg, c.m, rp.subtract(axis), rp.add(axis), seed, 0.22f * s * open, 9, 0.25f * s, Fx.r(VIOLET), Fx.g(VIOLET), Fx.b(VIOLET), open);
            Draw.lightning(lg, c.m, rp.subtract(axis), rp.add(axis), seed, 0.07f * s * open, 9, 0.25f * s, 1f, 0.9f, 1f, open);
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.billboard(dk, c.m, c.camRot, rp.x, rp.y, rp.z, 1.3f * s * open, 0, 0.02f, 0, 0.05f, 0.7f * open);
            float rt = Math.min(1, age / 10f + c.tickDelta / 10f);
            if (rt < 1) {
                VertexConsumer r = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
                Draw.billboard(r, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.4f + rt * 1.6f), 0, r(VIOLET), g(VIOLET), b(VIOLET), (1 - rt) * 0.35f * nearFade(rp, 2));
            }
        }
    }

    // ================================================================ 마력 폭발
    static final class Explosion extends Fx {
        final Vec3d p;
        final float s;

        Explosion(ClientWorld w, Vec3d p, float s) {
            super(22);
            this.p = p;
            this.s = s;
            Effects.sparks(p, (int) (30 * s), 0.8f * s, PINK, 0.12f);
            for (int i = 0; i < 18 * s; i++) {
                Particles.spawn(Kind.WISP_ADD, p, randDir().multiply(rf(0.1f, 0.3f) * s), rf(12, 20), rf(0.4f, 0.8f) * s).color(i % 2 == 0 ? VIOLET : DEEP, 0.8f);
                Particles.spawn(Kind.SMOKE_DARK, p, randDir().multiply(rf(0.05f, 0.15f) * s).add(0, 0.04, 0), rf(18, 30), rf(0.5f, 0.9f) * s).color(DARK, 0.55f).size1 = 1.6f * s;
            }
            CameraController.hitstop(2f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float e = 1 - (1 - t) * (1 - t);
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            float ft = Math.min(1, t * 3);
            Draw.billboard(f, c.m, c.camRot, rp.x, rp.y, rp.z, 3.5f * s * (0.3f + ft), 0, 0.9f, 0.7f, 1f, (1 - ft) * nearFade(rp, 3 * s));
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(g, c.m, c.camRot, rp.x, rp.y, rp.z, 3f * s * e, 0, r(VIOLET), g(VIOLET), b(VIOLET), (1 - t) * 0.8f * nearFade(rp, 3 * s));
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.billboard(sw, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.6f + 3 * e), 0, r(VIOLET), g(VIOLET), b(VIOLET), (1 - t) * 0.38f * nearFade(rp, 3));
        }
    }

    // ================================================================ 4. 마검 반격: 마력 방어막
    static final class Guard extends Fx {
        final int id;

        Guard(int id, int d) {
            super(d);
            this.id = id;
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null || Effects.isLocalFp(e)) return;
            float a = age + c.tickDelta;
            float vis = Math.min(1, a / 3f) * Math.min(1, (life - a) / 3f);
            float yaw = (e instanceof LivingEntity le ? MathHelper.lerpAngleDegrees(c.tickDelta, le.prevBodyYaw, le.bodyYaw) : e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
            Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
            Vec3d center = e.getLerpedPos(c.tickDelta).add(0, 1.2, 0).add(fwd.multiply(0.7)).subtract(c.cam);
            float size = 0.95f * vis;
            double ang = a * 0.06;
            Vec3d ax = right.multiply(Math.cos(ang)).add(new Vec3d(0, Math.sin(ang), 0)).multiply(size);
            Vec3d ay = right.multiply(-Math.sin(ang)).add(new Vec3d(0, Math.cos(ang), 0)).multiply(size);
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.quad(dk, c.m, center, right.multiply(size * 1.1), new Vec3d(0, size * 1.1, 0), 0.03f, 0, 0.06f, 0.5f * vis);
            VertexConsumer rs = c.imm.getBuffer(AWRenderLayers.additive(Tex.RUNE_SHIELD));
            Draw.quad(rs, c.m, center, ax, ay, r(VIOLET), g(VIOLET), b(VIOLET), vis * (0.75f + 0.25f * MathHelper.sin(a * 1.1f)));
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.quad(ring, c.m, center, right.multiply(size * 1.2), new Vec3d(0, size * 1.2, 0), r(PINK), g(PINK), b(PINK), vis * 0.6f);
        }
    }

    // ================================================================ 4. 튕겨냄
    static final class Parry extends Fx {
        final Vec3d p;

        Parry(Entity e) {
            super(10);
            float yaw = (e instanceof LivingEntity le ? le.bodyYaw : e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
            p = e.getPos().add(-Math.sin(yaw) * 0.7, 1.3, Math.cos(yaw) * 0.7);
            for (int i = 0; i < 30; i++) {
                Particles.P pp = Particles.spawn(Kind.SPARK, p, randDir().multiply(rf(0.2f, 0.6f)), rf(8, 14), rf(0.06f, 0.12f));
                pp.color(i % 3 == 0 ? 0xFFFFFF : i % 2 == 0 ? PINK : VIOLET, 1f);
                pp.grav = 0.03f;
            }
            CameraController.hitstop(3f);
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(f, c.m, c.camRot, rp.x, rp.y, rp.z, 2.4f * (0.3f + t), t * 2, 1f, 0.9f, 1f, (1 - t) * nearFade(rp, 2));
            VertexConsumer r = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.billboard(r, c.m, c.camRot, rp.x, rp.y, rp.z, 0.4f + t * 3, 0, r(VIOLET), g(VIOLET), b(VIOLET), (1 - t) * 0.8f * nearFade(rp, 2));
        }
    }

    // ================================================================ T. 잔영보: 남겨진 검의 잔상
    static final class PhantomSword extends Fx {
        final Vec3d at, to;
        final float yaw;

        PhantomSword(ClientWorld w, int id, Vec3d at, float yaw, Vec3d to) {
            super(21);
            this.at = at;
            this.to = to;
            this.yaw = yaw;
            AfterImages.requestAt(id, at, VIOLET, 20);
            wisps(at, 14);
            Vec3d d = to.subtract(at);
            for (int i = 0; i < 24; i++) {
                Vec3d p = at.add(d.multiply(R.nextDouble())).add(0, rf(0.3f, 1.6f), 0);
                Particles.spawn(Kind.STREAK, p, d.normalize().multiply(rf(0.2f, 0.5f)), rf(6, 12), rf(0.08f, 0.14f)).color(i % 2 == 0 ? VIOLET : PINK, 0.8f);
            }
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age % 2 == 0) Particles.spawn(Kind.GLOW, at.add(rf(-0.3f, 0.3f), rf(0.2f, 1.4f), rf(-0.3f, 0.3f)), new Vec3d(0, 0.05, 0), 12, 0.07f).color(VIOLET, 1f);
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float pulse = 0.6f + 0.4f * MathHelper.sin(a * (0.4f + a * 0.05f));
            MatrixStack ms = c.matrices;
            ms.push();
            ms.translate(at.x - c.cam.x, at.y - c.cam.y + 0.05, at.z - c.cam.z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw + 20));
            // 땅에 꽂힌 검 (칼끝이 아래)
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-75));
            ms.translate(0, 0, -1.25f);
            BladeParts.renderSword(ms, c.imm, 0xF000F0, 1.2f + pulse, 1.3f, a);
            ms.pop();
            Vec3d rp = at.add(0, 0.7, 0).subtract(c.cam);
            VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(g, c.m, c.camRot, rp.x, rp.y, rp.z, 1.6f * pulse, 0, 0.6f, 0.25f, 1f, 0.5f * nearFade(rp, 1.5f));
            Vec3d fp = at.add(0, 0.03, 0).subtract(c.cam);
            Draw.flat(c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE)), c.m, fp.x, fp.y, fp.z, 1.4f, a * 0.05f, 0.6f, 0.25f, 1f, 0.7f * pulse);
        }
    }

    // ================================================================ T. 잔영보: 차원 포털
    static final class Portal extends Fx {
        final Vec3d p, right;

        Portal(Vec3d at, Vec3d dir, int life) {
            super(life);
            this.p = at.add(0, 1.0, 0);
            Vec3d h = new Vec3d(dir.x, 0, dir.z);
            if (h.lengthSquared() < 1e-4) h = new Vec3d(0, 0, 1);
            h = h.normalize();
            this.right = new Vec3d(-h.z, 0, h.x);
            for (int i = 0; i < 18; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                Vec3d off = right.multiply(Math.cos(a) * 1.1).add(0, Math.sin(a) * 1.4, 0);
                Particles.P pp = Particles.spawn(Kind.GLOW, p.add(off), off.multiply(-0.08), rf(8, 14), 0.07f);
                pp.color(i % 2 == 0 ? VIOLET : PINK, 1f);
            }
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float open = Math.min(1, a / 3f) * Math.min(1, (life - a) / 5f);
            if (open <= 0) return;
            Vec3d rp = p.subtract(c.cam);
            Vec3d ax = right.multiply(1.05 * open), ay = new Vec3d(0, 1.4 * open, 0);
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.quad(dk, c.m, rp, ax.multiply(0.9), ay.multiply(0.9), 0.02f, 0f, 0.05f, 0.85f * open);
            double rot = a * 0.35;
            Vec3d sx = ax.multiply(Math.cos(rot)).add(ay.multiply(Math.sin(rot) * 0.75));
            Vec3d sy = ay.multiply(Math.cos(rot)).subtract(ax.multiply(Math.sin(rot) * 1.3));
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SWIRL));
            Draw.quad(sw, c.m, rp, sx, sy, r(VIOLET), g(VIOLET), b(VIOLET), 0.95f * open);
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.quad(ring, c.m, rp, ax.multiply(1.08), ay.multiply(1.08), r(PINK), g(PINK), b(PINK), 0.8f * open);
            Vec3d gp = p.add(0, -0.97, 0).subtract(c.cam);
            Draw.flat(c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE)), c.m, gp.x, gp.y, gp.z, 1.3f * open, a * 0.05f, r(VIOLET), g(VIOLET), b(VIOLET), 0.7f * open);
        }
    }

    // ================================================================ R. 천지개벽
    static final class Ult extends Fx {
        final int caster;
        final Vec3d start, fwd, right;
        final double groundY;
        final Vec3d[] rifts = new Vec3d[7];

        Ult(ClientWorld w, NbtCompound n, Vec3d p) {
            super(100);
            caster = n.getInt("c");
            start = p;
            fwd = new Vec3d(n.getDouble("fx"), 0, n.getDouble("fz")).normalize();
            right = new Vec3d(-fwd.z, 0, fwd.x);
            groundY = n.getDouble("gy");
            for (int i = 0; i < rifts.length; i++) {
                double a = i / (double) rifts.length * Math.PI * 2 + R.nextDouble() * 0.5;
                double d = rf(4.5f, 8f);
                rifts[i] = start.add(Math.cos(a) * d, rf(1.5f, 4.5f), Math.sin(a) * d);
            }
        }

        Vec3d base() {
            return new Vec3d(start.x, groundY, start.z);
        }

        Vec3d swordTip(ClientWorld w) {
            Entity c = w.getEntityById(caster);
            Vec3d h = c != null ? HandTracker.hand(c, 0, 1f) : start.add(0, 1.5, 0);
            return h.add(0, age >= 22 && age < 58 ? 9 : 1.2, 0);
        }

        @Override
        public boolean tick(ClientWorld w) {
            // 1~2. 주변 마력이 검으로 빨려 들어감
            if (age < 30) {
                Vec3d tip = swordTip(w);
                for (int i = 0; i < 10; i++) {
                    Vec3d from = tip.add(randDir().multiply(rf(4, 9)));
                    Particles.P pp = Particles.spawn(i % 3 == 0 ? Kind.WISP_ADD : Kind.GLOW, from, tip.subtract(from).multiply(0.1), 10, i % 3 == 0 ? rf(0.3f, 0.5f) : rf(0.06f, 0.1f));
                    pp.color(i % 2 == 0 ? VIOLET : PINK, 0.9f);
                    pp.drag = 1f;
                }
            }
            if (age == 22) {
                for (Vec3d r : rifts) VfxManager.add(new Rift(r, 1.8f, 50));
                Effects.sparks(swordTip(w), 50, 1.2f, PINK, 0.14f);
            }
            // 5~6. 초대형 참격이 나아가며 폭발
            if (age >= 48 && age < 58) {
                double front = (age - 47) * 2.8;
                Vec3d at = base().add(fwd.multiply(front));
                if (age % 2 == 0) VfxManager.add(new Explosion(w, at.add(0, 1.2, 0), 1.6f));
                BlockState st = Effects.groundState(w, at);
                if (st != null) {
                    for (int i = 0; i < 6; i++) {
                        Vec3d side = right.multiply(rf(-3.5f, 3.5f));
                        Debris.spawn(st, at.add(side).add(0, 0.2, 0), new Vec3d(rf(-0.15f, 0.15f), rf(0.4f, 0.8f), rf(-0.15f, 0.15f)), rf(0.25f, 0.5f), rf(20, 34), null, 0, true);
                    }
                }
            }
            if (age == 60) {
                for (int i = 1; i <= 5; i++) VfxManager.add(new Explosion(w, base().add(fwd.multiply(i * 5.0)).add(0, 1, 0), 2.4f));
                CameraController.hitstop(4f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            // 바닥 마법진
            Vec3d gp = base().add(0, 0.04, 0).subtract(c.cam);
            if (a < 60) {
                float grow = Effects.outBack(Math.min(1, a / 10f));
                float fade = a > 48 ? Math.max(0, 1 - (a - 48) / 12f) : 1;
                Draw.flat(c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE)), c.m, gp.x, gp.y, gp.z, 5f * grow, a * 0.02f, r(VIOLET), g(VIOLET), b(VIOLET), 0.9f * fade);
                Draw.flat(c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2)), c.m, gp.x, gp.y + 0.01, gp.z, 7f * grow, -a * 0.015f, r(PINK), g(PINK), b(PINK), 0.5f * fade);
            }
            // 검 끝의 빛 (마력 응축)
            if (a < 58) {
                Vec3d tip = swordTip(c.world).subtract(c.cam);
                float k = Math.min(1, a / 26f);
                VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
                Draw.billboard(g, c.m, c.camRot, tip.x, tip.y, tip.z, 1.0f + 2.5f * k, 0, 0.7f, 0.3f, 1f, 0.4f * nearFade(tip, 3));
                VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
                Draw.billboard(f, c.m, c.camRot, tip.x, tip.y, tip.z, 1.5f + 2f * k, a * 0.05f, 0.85f, 0.6f, 1f, 0.25f * k * nearFade(tip, 3));
            }
            // 초대형 참격: 세로로 선 거대한 초승달이 앞으로 나아감 + 지나간 땅의 상처
            if (a >= 46 && a < 64) {
                float front = Math.max(0, (a - 47) * 2.8f);
                float fade = a < 58 ? 1 : Math.max(0, 1 - (a - 58) / 6f);
                Vec3d c0 = base().add(fwd.multiply(front)).add(0, 3.2, 0);
                int n = 24;
                Vec3d[] pts = new Vec3d[n];
                float[] wds = new float[n], als = new float[n];
                for (int i = 0; i < n; i++) {
                    double k = (double) i / (n - 1);
                    double ang = -1.4 + 2.8 * k;
                    pts[i] = c0.add(right.multiply(Math.sin(ang) * 4.6)).add(0, Math.cos(ang) * 3.4 - 1.2, 0).subtract(fwd.multiply((1 - Math.cos(ang)) * 1.6)).subtract(c.cam);
                    float env = (float) Math.sin(Math.PI * k);
                    wds[i] = 1.3f * env;
                    als[i] = env * fade;
                }
                VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
                Draw.ribbon(dk, c.m, pts, n, wds, 0.05f, 0, 0.08f, als, 0.3f, -a * 0.2f);
                VertexConsumer g = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
                for (int i = 0; i < n; i++) wds[i] *= 0.8f;
                Draw.ribbon(g, c.m, pts, n, wds, Fx.r(VIOLET), Fx.g(VIOLET), Fx.b(VIOLET), als, 0.3f, -a * 0.2f);
                for (int i = 0; i < n; i++) wds[i] *= 0.3f;
                Draw.ribbon(g, c.m, pts, n, wds, 1f, 0.95f, 1f, als, 0.3f, 0);
            }
            if (a >= 47 && a < 100) {
                float len = Math.min(28, Math.max(0, (a - 47) * 2.8f));
                float fade = a < 70 ? 1 : Math.max(0, 1 - (a - 70) / 30f);
                Vec3d s0 = base().add(0, 0.06, 0);
                Vec3d s1 = s0.add(fwd.multiply(len));
                Vec3d w = right.multiply(1.1);
                VertexConsumer cr = c.imm.getBuffer(AWRenderLayers.translucent(Tex.CRACK));
                Draw.quadUV(cr, c.m, s0.lerp(s1, 0.5).subtract(c.cam), w, fwd.multiply(len / 2), 0.06f, 0.0f, 0.08f, 0.85f * fade, 0, 0, 1, Math.max(1, len / 3));
                VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.CRACK));
                float fl = 0.7f + 0.3f * MathHelper.sin(a * 0.6f);
                Draw.quadUV(gl, c.m, s0.lerp(s1, 0.5).add(0, 0.01, 0).subtract(c.cam), w, fwd.multiply(len / 2), 0.75f * fl, 0.3f * fl, 1f * fl, 0.9f * fade, 0, 0, 1, Math.max(1, len / 3));
            }
        }
    }

    private BladeFx() {
    }
}
