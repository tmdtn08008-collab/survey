package com.abilitywar.client.fx.warlock;

import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Draw;
import com.abilitywar.client.fx.Effects;
import com.abilitywar.client.fx.Fx;
import com.abilitywar.client.fx.HandTracker;
import com.abilitywar.client.fx.Particles;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.client.fx.VfxManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.abilitywar.client.fx.Effects.R;
import static com.abilitywar.client.fx.Effects.randDir;
import static com.abilitywar.client.fx.Effects.rf;
import static com.abilitywar.client.fx.Particles.Kind;

/** 흑마법사 이펙트: 흑마법 구체, 소환진, 하수인 표시, 영혼, 광선, 사슬, 영혼 강탈. */
public final class WarlockFx {
    public static final int GREEN = 0x48E8A0;
    public static final int PALE = 0xB8FFD8;
    public static final int VIOLET = 0x7A3CC8;
    public static final int DARK = 0x0A0612;
    static final Identifier CIRCLE = Tex.fx("warlock_circle");
    static final Identifier SOUL = Tex.fx("warlock_soul");
    static final Identifier CHAIN = Tex.fx("warlock_chain");

    static float r(int c) {
        return ((c >> 16) & 255) / 255f;
    }

    static float g(int c) {
        return ((c >> 8) & 255) / 255f;
    }

    static float b(int c) {
        return (c & 255) / 255f;
    }

    /** 하수인 표시: 엔티티 id → 표시 시작 시각. */
    static final Map<Integer, Long> MINIONS = new HashMap<>();
    /** 땅에 떨어진 영혼: id → [x, y, z, 생성 시각, 수명, 크기, 날아가는 대상(-1), 날아가기 시작 시각]. */
    static final Map<Integer, double[]> ORBS = new HashMap<>();

    public static boolean spawn(String type, NbtCompound n, ClientWorld w, Vec3d p) {
        switch (type) {
            case "warlock_bolt" -> VfxManager.add(new Bolt(w, n.getInt("c"), n.getInt("e"), new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz")), n.getInt("n")));
            case "warlock_summon" -> VfxManager.add(new Summon(w, p, n.getBoolean("big")));
            case "warlock_minion" -> MINIONS.put(n.getInt("e"), w.getTime());
            case "warlock_unsummon" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                Vec3d at = e != null ? e.getPos() : p;
                MINIONS.remove(n.getInt("e"));
                for (int i = 0; i < 24; i++) {
                    Particles.spawn(Kind.SMOKE_DARK, at.add(rf(-0.5f, 0.5f), rf(0, 1.8f), rf(-0.5f, 0.5f)), new Vec3d(0, -rf(0.02f, 0.06f), 0), rf(14, 24), rf(0.3f, 0.6f)).color(DARK, 0.6f);
                    if (i % 3 == 0) Particles.spawn(Kind.GLOW, at.add(rf(-0.4f, 0.4f), rf(0.2f, 1.6f), rf(-0.4f, 0.4f)), new Vec3d(0, -0.05, 0), 14, 0.07f).color(GREEN, 1f);
                }
            }
            case "warlock_soul" -> ORBS.put(n.getInt("id"), new double[]{p.x, p.y, p.z, w.getTime(), n.getInt("life"), n.getFloat("s"), -1, 0});
            case "warlock_soul_take" -> {
                double[] o = ORBS.get(n.getInt("id"));
                if (o != null) {
                    o[6] = n.getInt("c");
                    o[7] = w.getTime();
                }
            }
            case "warlock_soul_fade" -> {
                double[] o = ORBS.remove(n.getInt("id"));
                if (o != null) for (int i = 0; i < 10; i++)
                    Particles.spawn(Kind.GLOW, new Vec3d(o[0], o[1], o[2]).add(randDir().multiply(0.3)), new Vec3d(0, rf(0.02f, 0.06f), 0), rf(14, 22), 0.06f).color(PALE, 0.8f);
            }
            case "warlock_beam" -> VfxManager.add(new Beam(n.getInt("c"), n.getInt("d")));
            case "warlock_bind" -> VfxManager.add(new Bind(w, n.getInt("e"), n.getInt("d")));
            case "warlock_ult" -> VfxManager.add(new Hand(w, n.getInt("c"), n.getInt("e"), p));
            case "warlock_rip" -> VfxManager.add(new Rip(w, n.getInt("e"), n.getInt("c")));
            case "warlock_captured" -> {
                Entity e = w.getEntityById(n.getInt("e"));
                if (e != null) {
                    for (int i = 0; i < 40; i++) {
                        Particles.P pp = Particles.spawn(i % 3 == 0 ? Kind.GLOW : Kind.SMOKE_DARK, e.getPos().add(rf(-0.5f, 0.5f) * e.getWidth(), rf(0, e.getHeight()), rf(-0.5f, 0.5f) * e.getWidth()),
                                randDir().multiply(0.08), rf(16, 28), i % 3 == 0 ? 0.08f : rf(0.3f, 0.6f));
                        pp.color(i % 3 == 0 ? GREEN : DARK, i % 3 == 0 ? 1f : 0.6f);
                    }
                    CameraController.hitstop(3f);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void tick(ClientWorld w) {
        long now = w.getTime();
        // 하수인: 발밑에서 영혼 불꽃이 피어오름
        for (Iterator<Map.Entry<Integer, Long>> it = MINIONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> en = it.next();
            Entity e = w.getEntityById(en.getKey());
            if (e == null || !e.isAlive()) {
                if (now - en.getValue() > 40) it.remove();
                continue;
            }
            if (now % 4 == 0) {
                Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.4f, 0.4f) * e.getWidth(), rf(0.1f, e.getHeight() * 0.8f), rf(-0.4f, 0.4f) * e.getWidth()), new Vec3d(0, rf(0.02f, 0.05f), 0), rf(12, 20), 0.05f);
                pp.color(GREEN, 0.9f);
            }
        }
        // 영혼: 사라지는 중 / 날아가 도착
        for (Iterator<Map.Entry<Integer, double[]>> it = ORBS.entrySet().iterator(); it.hasNext(); ) {
            double[] o = it.next().getValue();
            if (o[6] >= 0) {
                if (now - o[7] >= 8) {
                    Entity c = w.getEntityById((int) o[6]);
                    if (c != null) for (int i = 0; i < 8; i++)
                        Particles.spawn(Kind.GLOW, c.getPos().add(0, 1.1, 0).add(randDir().multiply(0.3)), randDir().multiply(0.05), 12, 0.07f).color(GREEN, 1f);
                    it.remove();
                }
            } else if (now > o[3] + o[4] + 20) it.remove();
            else if (now % 3 == 0) {
                Particles.P pp = Particles.spawn(Kind.GLOW, new Vec3d(o[0], o[1], o[2]).add(rf(-0.2f, 0.2f), rf(-0.1f, 0.3f), rf(-0.2f, 0.2f)), new Vec3d(0, 0.04, 0), 14, 0.05f);
                pp.color(PALE, 0.7f);
            }
        }
    }

    public static void render(Fx.Ctx c) {
        float t = c.world.getTime() + c.tickDelta;
        // 하수인 발밑 소환진
        if (!MINIONS.isEmpty()) {
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            for (Map.Entry<Integer, Long> en : MINIONS.entrySet()) {
                Entity e = c.world.getEntityById(en.getKey());
                if (e == null || !e.isAlive()) continue;
                Vec3d p = e.getLerpedPos(c.tickDelta).subtract(c.cam);
                float in = Math.min(1, (t - en.getValue()) / 10f);
                Draw.flat(ci, c.m, p.x, p.y + 0.03, p.z, Math.max(0.6f, e.getWidth() * 0.9f), t * 0.03f, r(GREEN), g(GREEN), b(GREEN), 0.45f * in);
            }
        }
        // 땅에 떨어진 영혼
        if (!ORBS.isEmpty()) {
            Vector3f rv = c.camRot.transform(new Vector3f(1, 0, 0)), uv = c.camRot.transform(new Vector3f(0, 1, 0));
            Vec3d right = new Vec3d(rv.x, rv.y, rv.z), up = new Vec3d(uv.x, uv.y, uv.z);
            VertexConsumer sv = c.imm.getBuffer(AWRenderLayers.additive(SOUL));
            long now = c.world.getTime();
            for (double[] o : ORBS.values()) {
                Vec3d p = new Vec3d(o[0], o[1], o[2]);
                float alpha;
                if (o[6] >= 0) {
                    Entity col = c.world.getEntityById((int) o[6]);
                    float k = MathHelper.clamp((float) ((now - o[7] + c.tickDelta) / 8.0), 0, 1);
                    if (col != null) p = p.lerp(col.getLerpedPos(c.tickDelta).add(0, 1.1, 0), k * k).add(0, Math.sin(k * Math.PI) * 0.8, 0);
                    alpha = 1 - k * 0.5f;
                } else {
                    float age = (float) (now - o[3] + c.tickDelta);
                    float left = (float) (o[4] - age);
                    alpha = Math.min(1, age / 6f) * MathHelper.clamp(left / 40f, 0, 1);
                    // 마지막 2초는 깜빡임
                    if (left < 40) alpha *= 0.6f + 0.4f * MathHelper.sin(age * 0.9f);
                    p = p.add(0, 0.12 * Math.sin(age * 0.12 + o[0]), 0);
                }
                Vec3d rp = p.subtract(c.cam);
                float s = (float) o[5] * 0.32f;
                double wob = 0.12 * Math.sin(t * 0.2 + o[0]);
                Vec3d ax = right.multiply(Math.cos(wob) * s).add(up.multiply(Math.sin(wob) * s));
                Vec3d ay = up.multiply(Math.cos(wob) * s * 1.5).subtract(right.multiply(Math.sin(wob) * s * 1.5));
                Draw.quadUV(sv, c.m, rp, ax, ay, r(PALE), g(PALE), b(PALE), alpha, 0, 0, 1, 1);
            }
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            for (double[] o : ORBS.values()) {
                if (o[6] >= 0) continue;
                Vec3d rp = new Vec3d(o[0], o[1], o[2]).subtract(c.cam);
                Draw.billboard(gl, c.m, c.camRot, rp.x, rp.y, rp.z, (float) o[5] * 0.7f, 0, r(GREEN), g(GREEN), b(GREEN), 0.35f);
            }
        }
    }

    public static void clear() {
        MINIONS.clear();
        ORBS.clear();
    }

    // ================================================================ 작은 섬광
    static final class Burst extends Fx {
        final Vec3d p;
        final float s;

        Burst(Vec3d p, float s) {
            super(12);
            this.p = p;
            this.s = s;
            for (int i = 0; i < 10 * s; i++) {
                Particles.spawn(i % 2 == 0 ? Kind.GLOW : Kind.SMOKE_DARK, p, randDir().multiply(rf(0.05f, 0.18f) * s), rf(10, 18), i % 2 == 0 ? 0.07f : 0.35f * s)
                        .color(i % 2 == 0 ? GREEN : DARK, i % 2 == 0 ? 1f : 0.6f);
            }
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            Vec3d rp = p.subtract(c.cam);
            float nf = nearFade(rp, s);
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.billboard(dk, c.m, c.camRot, rp.x, rp.y, rp.z, 1.2f * s * (0.5f + t), 0, 0.03f, 0.0f, 0.06f, (1 - t) * 0.8f * nf);
            VertexConsumer rg = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING_SOFT));
            Draw.billboard(rg, c.m, c.camRot, rp.x, rp.y, rp.z, s * (0.3f + 1.4f * t), 0, r(GREEN), g(GREEN), b(GREEN), (1 - t) * 0.7f * nf);
        }
    }

    // ================================================================ 평타: 흑마법 구체
    static final class Bolt extends Fx {
        final int caster, target, n;
        final Vec3d from, point;
        final int travel;
        boolean arrived;

        Bolt(ClientWorld w, int caster, int target, Vec3d point, int n) {
            super(30);
            this.caster = caster;
            this.target = target;
            this.n = n;
            Entity c = w.getEntityById(caster);
            this.from = c != null ? HandTracker.hand(c, 0, 1f).add(c.getRotationVec(1f).multiply(0.4)) : point;
            this.point = point;
            Entity t = target >= 0 ? w.getEntityById(target) : null;
            double dist = (t != null ? Effects.chest(t, 1f) : point).distanceTo(from);
            this.travel = t != null ? Math.max(2, Math.min(5, (int) (dist / 3.5))) : 6;
        }

        Vec3d end(ClientWorld w, float td) {
            Entity t = target >= 0 ? w.getEntityById(target) : null;
            return t != null ? Effects.chest(t, td) : point;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age == travel && !arrived) {
                arrived = true;
                if (target >= 0) VfxManager.add(new Burst(end(w, 1f), n > 1 ? 1.0f : 0.55f));
            }
            if (age < travel) {
                Vec3d p = from.lerp(end(w, 1f), (age + 0.5) / travel);
                Particles.spawn(Kind.SMOKE_DARK, p, randDir().multiply(0.02), 10, 0.2f).color(DARK, 0.5f);
            }
            return age < travel + 1 && super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            if (a >= travel) return;
            Vec3d e = end(c.world, c.tickDelta);
            Vec3d d = e.subtract(from);
            Vec3d side = d.crossProduct(new Vec3d(0, 1, 0));
            side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
            Vec3d[] ps = new Vec3d[n];
            for (int i = 0; i < n; i++) {
                float k = MathHelper.clamp((a - i * 0.35f) / travel, 0, 1);
                double off = n > 1 ? (i - 1) * 0.4 * Math.sin(k * Math.PI) : 0;
                ps[i] = from.add(d.multiply(k)).add(side.multiply(off)).subtract(c.cam);
            }
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            for (Vec3d p : ps) Draw.streak(st, c.m, p.subtract(d.normalize().multiply(0.5)), d, 1.1f, 0.12f, r(GREEN), g(GREEN), b(GREEN), 0.7f);
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            for (Vec3d p : ps) Draw.billboard(dk, c.m, c.camRot, p.x, p.y, p.z, 0.32f, 0, 0.05f, 0, 0.1f, 0.95f);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            for (Vec3d p : ps) Draw.billboard(gl, c.m, c.camRot, p.x, p.y, p.z, 0.42f, 0, r(GREEN), g(GREEN), b(GREEN), 0.8f);
        }
    }

    // ================================================================ 소환진 (해골 / 사령)
    static final class Summon extends Fx {
        final Vec3d p;
        final boolean big;

        Summon(ClientWorld w, Vec3d p, boolean big) {
            super(26);
            this.p = p;
            this.big = big;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age < 12 && age % 2 == 0) {
                for (int i = 0; i < 3; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double rr = (big ? 1.4 : 1.0) * Math.sqrt(R.nextDouble());
                    Particles.P pp = Particles.spawn(Kind.GLOW, p.add(Math.cos(a) * rr, 0.05, Math.sin(a) * rr), new Vec3d(0, rf(0.08f, 0.2f), 0), rf(12, 20), rf(0.06f, 0.1f));
                    pp.color(i == 0 ? PALE : GREEN, 1f);
                    pp.drag = 0.95f;
                }
                Particles.spawn(Kind.SMOKE_DARK, p.add(rf(-0.8f, 0.8f), 0.1, rf(-0.8f, 0.8f)), new Vec3d(0, rf(0.03f, 0.08f), 0), rf(16, 26), rf(0.4f, 0.7f)).color(DARK, 0.6f);
            }
            if (age == 8) VfxManager.add(new Burst(p.add(0, 0.8, 0), big ? 1.4f : 1.0f));
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float grow = Effects.outBack(Math.min(1, a / 6f));
            float fade = Math.min(1, (life - a) / 8f);
            Vec3d base = p.add(0, 0.04, 0).subtract(c.cam);
            float R0 = (big ? 1.8f : 1.3f) * grow;
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.flat(dk, c.m, base.x, base.y, base.z, R0 * 1.3f, 0, 0.02f, 0.0f, 0.05f, 0.7f * fade);
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y + 0.01, base.z, R0, a * 0.06f, r(GREEN), g(GREEN), b(GREEN), 0.95f * fade);
            // 솟아오르는 빛기둥
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            float h = 2.5f * Math.min(1, a / 8f);
            Vec3d mid = base.add(0, h / 2, 0);
            Vec3d toCam = mid.multiply(-1);
            Vec3d side = new Vec3d(0, 1, 0).crossProduct(toCam);
            side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
            Draw.quadUV(st, c.m, mid, new Vec3d(0, h / 2, 0), side.multiply(R0 * 0.6), r(GREEN), g(GREEN), b(GREEN), 0.55f * fade * (a < 10 ? 1 : Math.max(0, 1 - (a - 10) / 8f)), 0, 0, 1, 1);
        }
    }

    // ================================================================ 3. 흑마법 광선
    static final class Beam extends Fx {
        final int id;

        Beam(int id, int d) {
            super(d);
            this.id = id;
        }

        Vec3d[] ends(ClientWorld w, Entity e, float td) {
            Vec3d eye = e.getCameraPosVec(td).subtract(0, 0.2, 0);
            Vec3d dir = e.getRotationVec(td);
            Vec3d hand = Effects.isLocalFp(e) ? eye.add(dir.multiply(0.8)).add(0, -0.25, 0) : HandTracker.hand(e, 0, td).lerp(HandTracker.hand(e, 1, td), 0.5).add(dir.multiply(0.3));
            Vec3d to = eye.add(dir.multiply(16));
            var hit = w.raycast(new RaycastContext(eye, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
            if (hit.getType() != HitResult.Type.MISS) to = hit.getPos();
            return new Vec3d[]{hand, to};
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(id);
            if (e != null && age > 1) {
                Vec3d[] se = ends(w, e, 1f);
                for (int i = 0; i < 3; i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, se[0].lerp(se[1], R.nextDouble()), randDir().multiply(0.04), rf(8, 14), rf(0.05f, 0.09f));
                    pp.color(i == 0 ? PALE : GREEN, 1f);
                }
                Particles.spawn(Kind.SMOKE_DARK, se[1], randDir().multiply(0.08), rf(10, 16), rf(0.3f, 0.5f)).color(DARK, 0.6f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float a = age + c.tickDelta;
            float k = Math.min(1, a / 2f) * Math.min(1, (life - a) / 3f);
            Vec3d[] se = ends(c.world, e, c.tickDelta);
            Vec3d s = se[0], d = se[1].subtract(s);
            Vec3d side = d.crossProduct(new Vec3d(0, 1, 0));
            side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
            Vec3d upv = side.crossProduct(d).normalize();
            int n = 24;
            Vec3d[] pts = new Vec3d[n];
            float[] wds = new float[n], als = new float[n];
            for (int i = 0; i < n; i++) {
                double q = (double) i / (n - 1);
                pts[i] = s.add(d.multiply(q)).subtract(c.cam);
                wds[i] = (0.3f + 0.08f * MathHelper.sin(a * 1.5f + i)) * k;
                als[i] = k;
            }
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
            Draw.ribbon(dk, c.m, pts, n, wds, 0.04f, 0.0f, 0.08f, als, 0.4f, -a * 0.5f);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
            for (int i = 0; i < n; i++) wds[i] *= 0.55f;
            Draw.ribbon(gl, c.m, pts, n, wds, r(GREEN), g(GREEN), b(GREEN), als, 0.4f, -a * 0.8f);
            for (int i = 0; i < n; i++) wds[i] *= 0.35f;
            Draw.ribbon(gl, c.m, pts, n, wds, 0.9f, 1f, 0.95f, als, 0.4f, -a);
            // 둘레를 감는 나선
            for (int sp = 0; sp < 2; sp++) {
                Vec3d[] hp = new Vec3d[n];
                float[] hw = new float[n];
                for (int i = 0; i < n; i++) {
                    double q = (double) i / (n - 1);
                    double ang = q * 14 - a * 0.9 + sp * Math.PI;
                    hp[i] = s.add(d.multiply(q)).add(side.multiply(Math.cos(ang) * 0.35)).add(upv.multiply(Math.sin(ang) * 0.35)).subtract(c.cam);
                    hw[i] = 0.05f * k;
                }
                Draw.ribbon(gl, c.m, hp, n, hw, sp == 0 ? r(GREEN) : r(VIOLET), sp == 0 ? g(GREEN) : g(VIOLET), sp == 0 ? b(GREEN) : b(VIOLET), als, 0.6f, 0);
            }
            Vec3d end = se[1].subtract(c.cam);
            VertexConsumer f = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(f, c.m, c.camRot, end.x, end.y, end.z, 1.1f * k, a * 0.3f, r(GREEN), g(GREEN), b(GREEN), 0.8f * k * nearFade(end, 1));
        }
    }

    // ================================================================ 4. 속박: 땅에서 솟은 어둠의 사슬
    static final class Bind extends Fx {
        final int id;
        final float[][] anchors = new float[5][];

        Bind(ClientWorld w, int id, int d) {
            super(d);
            this.id = id;
            for (int i = 0; i < anchors.length; i++) {
                double a = i * Math.PI * 2 / anchors.length + R.nextDouble() * 0.4;
                anchors[i] = new float[]{(float) Math.cos(a) * rf(1.4f, 1.9f), (float) Math.sin(a) * rf(1.4f, 1.9f), rf(0.4f, 1.3f)};
            }
            Entity e = w.getEntityById(id);
            if (e != null) VfxManager.add(new Burst(e.getPos().add(0, 0.5, 0), 1.2f));
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float a = age + c.tickDelta;
            float out = Math.min(1, a / 4f);
            float fade = Math.min(1, (life - a) / 6f);
            Vec3d base = e.getLerpedPos(c.tickDelta);
            Vec3d rb = base.subtract(c.cam);
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.flat(ci, c.m, rb.x, rb.y + 0.03, rb.z, 1.9f * Effects.outBack(Math.min(1, a / 6f)), -a * 0.02f, r(GREEN), g(GREEN), b(GREEN), 0.7f * fade);
            VertexConsumer ch = c.imm.getBuffer(AWRenderLayers.translucent(CHAIN));
            Vec3d[][] all = new Vec3d[anchors.length][];
            for (int k = 0; k < anchors.length; k++) {
                float[] an = anchors[k];
                Vec3d from = base.add(an[0], 0, an[1]);
                Vec3d to = base.add(0, e.getHeight() * an[2] * 0.6 + 0.2, 0);
                int n = 10;
                Vec3d[] pts = new Vec3d[n];
                float[] wds = new float[n], als = new float[n];
                for (int i = 0; i < n; i++) {
                    double q = (double) i / (n - 1) * out;
                    pts[i] = from.lerp(to, q).add(0, Math.sin(q * Math.PI) * 0.4, 0).subtract(c.cam);
                    wds[i] = 0.1f;
                    als[i] = fade;
                }
                all[k] = pts;
                double len = from.distanceTo(to) * out;
                Draw.ribbon(ch, c.m, pts, n, wds, 1, 1, 1, als, (float) (len / 0.4 / (n - 1)), 0);
            }
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(CHAIN));
            for (Vec3d[] pts : all) {
                float[] wds = new float[pts.length], als = new float[pts.length];
                for (int i = 0; i < pts.length; i++) {
                    wds[i] = 0.13f;
                    als[i] = fade * (0.5f + 0.5f * MathHelper.sin(a * 0.4f + i));
                }
                Draw.ribbon(gl, c.m, pts, pts.length, wds, r(GREEN) * 0.6f, g(GREEN) * 0.6f, b(GREEN) * 0.6f, als, 1.2f, 0);
            }
        }
    }

    // ================================================================ R. 영혼 강탈: 땅에서 솟은 거대한 어둠의 손이 대상을 움켜쥠
    static final class Hand extends Fx {
        final int caster, target;
        final Vec3d start;

        Hand(ClientWorld w, int caster, int target, Vec3d p) {
            super(50);
            this.caster = caster;
            this.target = target;
            this.start = p;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age < 30) {
                for (int i = 0; i < 4; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    Particles.spawn(Kind.SMOKE_DARK, start.add(Math.cos(a) * 2.5, 0.1, Math.sin(a) * 2.5), new Vec3d(-Math.cos(a) * 0.08, rf(0.02f, 0.08f), -Math.sin(a) * 0.08), rf(18, 28), rf(0.5f, 0.9f))
                            .color(DARK, 0.7f);
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            Entity t = c.world.getEntityById(target);
            Vec3d tp = t != null ? t.getLerpedPos(c.tickDelta) : start;
            float th = t != null ? t.getHeight() : 1.8f;
            float rise = Effects.outBack(MathHelper.clamp(a / 10f, 0, 1));
            float grip = MathHelper.clamp((a - 8) / 8f, 0, 1);
            float fade = a < 36 ? 1 : Math.max(0, 1 - (a - 36) / 10f);
            Vec3d base = start.add(0, 0.02, 0).subtract(c.cam);
            // 바닥: 어둠의 웅덩이 + 소환진
            VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
            Draw.flat(dk, c.m, base.x, base.y, base.z, 4.5f * rise, 0, 0.01f, 0, 0.03f, 0.85f * fade);
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y + 0.01, base.z, 3.4f * rise, a * 0.04f, r(GREEN), g(GREEN), b(GREEN), 0.9f * fade);
            // 손가락 5개: 땅에서 솟아 대상을 감싸 쥠
            VertexConsumer tdk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.TENDRIL));
            Vec3d[][] fingers = new Vec3d[5][];
            for (int f = 0; f < 5; f++) {
                double ang = f * Math.PI * 2 / 5 + 0.3;
                double rootR = 1.6;
                Vec3d root = start.add(Math.cos(ang) * rootR, 0, Math.sin(ang) * rootR);
                Vec3d tipOpen = start.add(Math.cos(ang) * 2.4, (th + 2.2) * rise, Math.sin(ang) * 2.4);
                Vec3d tipClose = tp.add(Math.cos(ang) * 0.25, th * 0.75, Math.sin(ang) * 0.25);
                Vec3d tip = tipOpen.lerp(tipClose, grip * grip * (3 - 2 * grip));
                int n = 12;
                Vec3d[] pts = new Vec3d[n];
                float[] wds = new float[n], als = new float[n];
                for (int i = 0; i < n; i++) {
                    double q = (double) i / (n - 1);
                    Vec3d mid = root.lerp(tip, q).add(Math.cos(ang) * Math.sin(q * Math.PI) * 0.9, 0, Math.sin(ang) * Math.sin(q * Math.PI) * 0.9);
                    pts[i] = mid.subtract(c.cam);
                    wds[i] = (0.5f * (1 - (float) q) + 0.08f) * rise;
                    als[i] = fade;
                }
                fingers[f] = pts;
                Draw.ribbon(tdk, c.m, pts, n, wds, 0.03f, 0.0f, 0.05f, als, 0.5f, a * 0.1f);
            }
            VertexConsumer tgl = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
            for (Vec3d[] pts : fingers) {
                float[] wds = new float[pts.length], als = new float[pts.length];
                for (int i = 0; i < pts.length; i++) {
                    wds[i] = 0.08f * rise;
                    als[i] = fade * 0.9f;
                }
                Draw.ribbon(tgl, c.m, pts, pts.length, wds, r(GREEN), g(GREEN), b(GREEN), als, 0.5f, a * 0.3f);
            }
        }
    }

    // ================================================================ 영혼을 뜯어냄: 대상에서 빛나는 영혼이 시전자 손으로
    static final class Rip extends Fx {
        final int target, caster;
        final Vec3d from;

        Rip(ClientWorld w, int target, int caster) {
            super(18);
            this.target = target;
            this.caster = caster;
            Entity t = w.getEntityById(target);
            this.from = t != null ? Effects.chest(t, 1f) : Vec3d.ZERO;
            VfxManager.add(new Burst(from, 2.0f));
            CameraController.hitstop(2f);
        }

        @Override
        public void render(Ctx c) {
            Entity ce = c.world.getEntityById(caster);
            if (ce == null) return;
            float a = age + c.tickDelta;
            float k = MathHelper.clamp(a / 10f, 0, 1);
            Vec3d to = HandTracker.hand(ce, 0, c.tickDelta);
            Vec3d p = from.lerp(to, k * k).add(0, Math.sin(k * Math.PI) * 1.2, 0);
            Vec3d rp = p.subtract(c.cam);
            float fade = a < 12 ? 1 : Math.max(0, 1 - (a - 12) / 6f);
            Vector3f rv = c.camRot.transform(new Vector3f(1, 0, 0)), uv = c.camRot.transform(new Vector3f(0, 1, 0));
            Vec3d right = new Vec3d(rv.x, rv.y, rv.z), up = new Vec3d(uv.x, uv.y, uv.z);
            VertexConsumer sv = c.imm.getBuffer(AWRenderLayers.additive(SOUL));
            Draw.quadUV(sv, c.m, rp, right.multiply(0.6), up.multiply(0.9), r(PALE), g(PALE), b(PALE), fade, 0, 0, 1, 1);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(gl, c.m, c.camRot, rp.x, rp.y, rp.z, 1.6f, 0, r(GREEN), g(GREEN), b(GREEN), 0.5f * fade);
            // 끌려가는 자국
            int n = 10;
            Vec3d[] pts = new Vec3d[n];
            float[] wds = new float[n], als = new float[n];
            for (int i = 0; i < n; i++) {
                double q = Math.max(0, k - i * 0.04);
                pts[i] = from.lerp(to, q * q).add(0, Math.sin(q * Math.PI) * 1.2, 0).subtract(c.cam);
                wds[i] = 0.25f * (1 - i / (float) n);
                als[i] = fade * (1 - i / (float) n);
            }
            VertexConsumer tg = c.imm.getBuffer(AWRenderLayers.additive(Tex.TENDRIL));
            Draw.ribbon(tg, c.m, pts, n, wds, r(GREEN), g(GREEN), b(GREEN), als, 0.6f, 0);
        }
    }

    private WarlockFx() {
    }
}
