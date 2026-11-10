package com.abilitywar.client.fx.angel;

import com.abilitywar.ability.angel.AngelAbility;
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
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

/** 천사 이펙트: 빛 구슬, 치유의 파동, 축복, 치유의 빛줄기, 천상의 심판 (하늘의 마법진 + 빛줄기), 깃털. */
public final class AngelFx {
    public static final int HOLY = 0xFFF4D6;
    public static final int GOLD = 0xFFD978;
    public static final int HEAL = 0x9CFFB8;
    public static final int SKY = 0x9FD8FF;
    static final Identifier CIRCLE = Tex.fx("angel_circle");
    static final Identifier FEATHER = Tex.fx("angel_feather");
    static final Identifier BEAM = Tex.fx("angel_beam");

    static float r(int c) {
        return ((c >> 16) & 255) / 255f;
    }

    static float g(int c) {
        return ((c >> 8) & 255) / 255f;
    }

    static float b(int c) {
        return (c & 255) / 255f;
    }

    /** 축복받은 대상 → 끝나는 시각 (머리 위 금빛 광륜). */
    static final Map<Integer, Long> BLESSED = new HashMap<>();

    public static boolean spawn(String type, NbtCompound n, ClientWorld w, Vec3d p) {
        switch (type) {
            case "angel_orb" -> VfxManager.add(new Orb(w, n.getInt("c"), n.getInt("e"),
                    new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz")), n.getBoolean("h"), n.getInt("n")));
            case "angel_heal" -> heal(w, n.getInt("e"), n.getFloat("a"));
            case "angel_wave" -> VfxManager.add(new Wave(w, p, n.getFloat("r"), n.getInt("k")));
            case "angel_buffed" -> {
                boolean big = n.getBoolean("big");
                VfxManager.add(new Buffed(n.getInt("e"), big));
                if (big) BLESSED.put(n.getInt("e"), w.getTime() + 400);
            }
            case "angel_bless" -> VfxManager.add(new Bless(n.getInt("e")));
            case "angel_stream" -> VfxManager.add(new Stream(n.getInt("c"), n.getInt("e"), n.getInt("d")));
            case "angel_bloom" -> VfxManager.add(new Bloom(w, n.getInt("e")));
            case "angel_ult" -> VfxManager.add(new Ult(n.getInt("c"), p, n.getInt("d")));
            case "angel_beam" -> VfxManager.add(new Beam(w, new Vec3d(n.getDouble("fx"), n.getDouble("fy"), n.getDouble("fz")), p, n.getBoolean("ally")));
            case "angel_mode" -> mode(w, n.getInt("c"), n.getBoolean("fly"));
            default -> {
                return false;
            }
        }
        return true;
    }

    // ================================================================ 깃털 / 치유 십자 입자
    /** [x,y,z, vx,vy,vz, age,life, size, rot,rotV, phase, r,g,b] */
    static final List<float[]> FEATHERS = new ArrayList<>();
    /** [x,y,z, vy, age,life, size, r,g,b] */
    static final List<float[]> PLUS = new ArrayList<>();

    static void feather(Vec3d p, Vec3d v, float life, float size, int col) {
        if (FEATHERS.size() > 500) return;
        FEATHERS.add(new float[]{(float) p.x, (float) p.y, (float) p.z, (float) v.x, (float) v.y, (float) v.z, 0, life, size,
                rf(0, 6.28f), rf(-0.12f, 0.12f), rf(0, 6.28f), r(col), g(col), b(col)});
    }

    static void featherBurst(Vec3d c, int n, float speed, int col) {
        for (int i = 0; i < n; i++) {
            Vec3d d = randDir();
            feather(c, new Vec3d(d.x * speed, Math.abs(d.y) * speed * 0.8 + 0.04, d.z * speed), rf(40, 70), rf(0.14f, 0.22f), col);
        }
    }

    static void plus(Vec3d p, float vy, float life, float size, int col) {
        if (PLUS.size() > 300) return;
        PLUS.add(new float[]{(float) p.x, (float) p.y, (float) p.z, vy, 0, life, size, r(col), g(col), b(col)});
    }

    static void heal(ClientWorld w, int id, float amount) {
        Entity e = w.getEntityById(id);
        if (e == null) return;
        int n = Math.min(10, 2 + (int) (amount * 0.7f));
        for (int i = 0; i < n; i++) {
            Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.45f, 0.45f), rf(0.1f, 1.7f), rf(-0.45f, 0.45f)), new Vec3d(0, rf(0.03f, 0.08f), 0), rf(14, 24), rf(0.06f, 0.11f));
            pp.color(i % 3 == 0 ? HOLY : HEAL, 1f);
            pp.drag = 0.96f;
        }
        if (!Effects.isLocalFp(e)) for (int i = 0; i < Math.min(4, 1 + (int) (amount / 3)); i++)
            plus(e.getPos().add(rf(-0.5f, 0.5f), rf(0.6f, 1.8f), rf(-0.5f, 0.5f)), rf(0.025f, 0.05f), rf(18, 26), rf(0.1f, 0.16f), i % 2 == 0 ? HEAL : HOLY);
        if (amount >= 5) featherBurst(e.getPos().add(0, 1.2, 0), 3, 0.08f, 0xFFFFFF);
    }

    static void mode(ClientWorld w, int id, boolean fly) {
        Entity e = w.getEntityById(id);
        if (e == null) return;
        Vec3d at = e.getPos();
        VfxManager.add(new Gust(at, fly));
        featherBurst(at.add(0, 1.2, 0), fly ? 14 : 8, fly ? 0.2f : 0.1f, 0xFFFFFF);
        if (fly) {
            BlockState st = Effects.groundState(w, at);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI * 2 / 16;
                Particles.spawn(Kind.SMOKE_ADD, at.add(Math.cos(a) * 0.6, 0.1, Math.sin(a) * 0.6), new Vec3d(Math.cos(a) * 0.3, 0.02, Math.sin(a) * 0.3), rf(12, 18), rf(0.3f, 0.5f)).color(0xE8F0FF, 0.35f);
            }
            if (st != null) for (int i = 0; i < 6; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                Debris.spawn(st, at.add(Math.cos(a) * 0.8, 0.1, Math.sin(a) * 0.8), new Vec3d(Math.cos(a) * 0.15, rf(0.1f, 0.25f), Math.sin(a) * 0.15), rf(0.1f, 0.2f), rf(14, 22), null, 0, true);
            }
        }
    }

    public static void tick(ClientWorld w) {
        long now = w.getTime();
        for (Iterator<float[]> it = FEATHERS.iterator(); it.hasNext(); ) {
            float[] q = it.next();
            // 하늘하늘 떨어짐: 좌우로 흔들리며 천천히
            float sway = MathHelper.sin(q[6] * 0.15f + q[11]) * 0.02f;
            q[3] = q[3] * 0.9f + sway * MathHelper.cos(q[11]);
            q[5] = q[5] * 0.9f + sway * MathHelper.sin(q[11]);
            q[4] = q[4] * 0.9f - 0.0035f;
            if (q[4] < -0.05f) q[4] = -0.05f;
            q[0] += q[3];
            q[1] += q[4];
            q[2] += q[5];
            q[9] += q[10] + sway * 2;
            q[6]++;
            if (q[6] >= q[7]) it.remove();
        }
        for (Iterator<float[]> it = PLUS.iterator(); it.hasNext(); ) {
            float[] q = it.next();
            q[1] += q[3];
            q[3] *= 0.94f;
            q[4]++;
            if (q[4] >= q[5]) it.remove();
        }
        // 비행 중인 천사: 날개에서 깃털이 날림
        for (PlayerEntity p : w.getPlayers()) {
            if (!AngelParts.isAngel(p) || p.isInvisible()) continue;
            if (AngelParts.flying(p) && !p.isOnGround() && (now + p.getId()) % 7 == 0) {
                float yaw = p.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
                Vec3d back = new Vec3d(MathHelper.sin(yaw), 0, -MathHelper.cos(yaw));
                Vec3d side = new Vec3d(-back.z, 0, back.x).multiply(rf(-1.6f, 1.6f));
                feather(p.getPos().add(0, 1.3, 0).add(back.multiply(0.4)).add(side), new Vec3d(0, -0.01, 0), rf(50, 80), rf(0.12f, 0.18f), 0xFFFFFF);
            }
            Long until = BLESSED.get(p.getId());
            if (until != null && now % 6 == 0) {
                Particles.P pp = Particles.spawn(Kind.GLOW, p.getPos().add(rf(-0.4f, 0.4f), rf(0.2f, 1.8f), rf(-0.4f, 0.4f)), new Vec3d(0, 0.03, 0), 16, 0.05f);
                pp.color(GOLD, 0.9f);
            }
        }
        for (Iterator<Map.Entry<Integer, Long>> it = BLESSED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> en = it.next();
            Entity e = w.getEntityById(en.getKey());
            if (en.getValue() < now || e == null || !e.isAlive()) {
                it.remove();
                continue;
            }
            if (!(e instanceof PlayerEntity) && now % 6 == 0) {
                Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.4f, 0.4f), rf(0.2f, e.getHeight()), rf(-0.4f, 0.4f)), new Vec3d(0, 0.03, 0), 16, 0.05f);
                pp.color(GOLD, 0.9f);
            }
        }
        AngelParts.WINGS.keySet().removeIf(id -> w.getEntityById(id) == null);
    }

    public static void render(Fx.Ctx c) {
        long now = c.world.getTime();
        float t = now + c.tickDelta;
        Vector3f rv = c.camRot.transform(new Vector3f(1, 0, 0)), uv = c.camRot.transform(new Vector3f(0, 1, 0));
        Vec3d right = new Vec3d(rv.x, rv.y, rv.z), up = new Vec3d(uv.x, uv.y, uv.z);
        // 축복의 광륜 (머리 위)
        if (!BLESSED.isEmpty()) {
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (Map.Entry<Integer, Long> en : BLESSED.entrySet()) {
                Entity e = c.world.getEntityById(en.getKey());
                if (e == null || Effects.isLocalFp(e) || e.isInvisible()) continue;
                float left = MathHelper.clamp((en.getValue() - t) / 20f, 0, 1);
                Vec3d h = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() + 0.32 + 0.03 * Math.sin(t * 0.15), 0).subtract(c.cam);
                glowRing(gl, c.m, h, new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), 0.3f, 0.06f, GOLD, 0.9f * left, 32);
            }
        }
        // 치유 십자
        if (!PLUS.isEmpty()) {
            VertexConsumer gv = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            for (float[] q : PLUS) {
                float a = q[4] + c.tickDelta;
                float k = a / q[5];
                float alpha = Math.min(1, a / 3f) * (1 - k * k);
                Vec3d p = new Vec3d(q[0], q[1] + q[3] * c.tickDelta, q[2]).subtract(c.cam);
                alpha *= Fx.nearFade(p, 0.8f);
                if (alpha <= 0.01f) continue;
                float s = q[6] * (1 + 0.15f * (1 - Math.min(1, a / 4f)));
                Draw.quad(gv, c.m, p, right.multiply(s), up.multiply(s * 0.32f), q[7], q[8], q[9], alpha);
                Draw.quad(gv, c.m, p, right.multiply(s * 0.32f), up.multiply(s), q[7], q[8], q[9], alpha);
            }
        }
        // 깃털
        if (!FEATHERS.isEmpty()) {
            VertexConsumer fv = c.imm.getBuffer(AWRenderLayers.translucent(FEATHER));
            for (float[] q : FEATHERS) {
                float a = q[6] + c.tickDelta;
                float k = a / q[7];
                float alpha = Math.min(1, a / 4f) * Math.min(1, (1 - k) * 4);
                Vec3d p = new Vec3d(q[0] + q[3] * c.tickDelta, q[1] + q[4] * c.tickDelta, q[2] + q[5] * c.tickDelta).subtract(c.cam);
                alpha *= Fx.nearFade(p, 1.6f);
                if (alpha <= 0.01f) continue;
                float s = q[8];
                double rot = q[9];
                Vec3d ax = right.multiply(Math.cos(rot) * s * 0.5).add(up.multiply(Math.sin(rot) * s * 0.5));
                Vec3d ay = up.multiply(Math.cos(rot) * s).subtract(right.multiply(Math.sin(rot) * s));
                Draw.quad(fv, c.m, p, ax, ay, q[12], q[13], q[14], alpha * 0.95f);
            }
        }
    }

    public static void clear() {
        FEATHERS.clear();
        PLUS.clear();
        BLESSED.clear();
        AngelParts.clear();
    }

    // ================================================================ 도우미

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
                Draw.vc(vc, m, (float) p00.x, (float) p00.y, (float) p00.z, cr, cg, cb, a0);
                Draw.vc(vc, m, (float) p01.x, (float) p01.y, (float) p01.z, cr, cg, cb, a1);
                Draw.vc(vc, m, (float) p11.x, (float) p11.y, (float) p11.z, cr, cg, cb, a1);
                Draw.vc(vc, m, (float) p10.x, (float) p10.y, (float) p10.z, cr, cg, cb, a0);
            }
        }
    }

    /** a → b 빛줄기 (카메라를 향한 판, 빛줄기 텍스처가 흐름). a, b 는 카메라 기준 좌표. fa, fb = 양 끝에서 흐려지는 길이 비율. */
    static void beam(VertexConsumer vc, Fx.Ctx c, Vec3d a, Vec3d b, float width, int col, float alpha, float scroll, float fa, float fb) {
        Vec3d d = b.subtract(a);
        double len = d.length();
        if (len < 1e-4) return;
        Vec3d mid = a.add(b).multiply(0.5);
        Vec3d side = d.crossProduct(mid.multiply(-1));
        side = side.lengthSquared() < 1e-8 ? new Vec3d(1, 0, 0) : side.normalize().multiply(width);
        float vLen = (float) (len / Math.max(0.5, width * 6));
        float[] ks = {0, fa, 1 - fb, 1};
        float[] as = {fa > 0 ? 0 : alpha, alpha, alpha, fb > 0 ? 0 : alpha};
        float cr = r(col), cg = g(col), cb = b(col);
        for (int i = 0; i < 3; i++) {
            if (ks[i + 1] - ks[i] <= 1e-4) continue;
            Vec3d p0 = a.add(d.multiply(ks[i])), p1 = a.add(d.multiply(ks[i + 1]));
            float v0 = scroll + vLen * ks[i], v1 = scroll + vLen * ks[i + 1];
            Draw.v(vc, c.m, (float) (p0.x - side.x), (float) (p0.y - side.y), (float) (p0.z - side.z), cr, cg, cb, as[i], 0, v0);
            Draw.v(vc, c.m, (float) (p0.x + side.x), (float) (p0.y + side.y), (float) (p0.z + side.z), cr, cg, cb, as[i], 1, v0);
            Draw.v(vc, c.m, (float) (p1.x + side.x), (float) (p1.y + side.y), (float) (p1.z + side.z), cr, cg, cb, as[i + 1], 1, v1);
            Draw.v(vc, c.m, (float) (p1.x - side.x), (float) (p1.y - side.y), (float) (p1.z - side.z), cr, cg, cb, as[i + 1], 0, v1);
        }
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
                pp.color(i % 2 == 0 ? col : HOLY, 1f);
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

    // ================================================================ 평타: 빛 구슬
    static final class Orb extends Fx {
        final int target, n, color;
        final boolean healing;
        final Vec3d from, point;
        final int travel;
        boolean arrived;

        Orb(ClientWorld w, int caster, int target, Vec3d point, boolean healing, int n) {
            super(30);
            this.target = target;
            this.n = n;
            this.healing = healing;
            this.color = healing ? HEAL : HOLY;
            Entity c = w.getEntityById(caster);
            Vec3d h = c != null ? HandTracker.hand(c, 0, 1f).add(c.getRotationVec(1f).multiply(0.4)) : point;
            this.from = h;
            Entity t = target >= 0 ? w.getEntityById(target) : null;
            this.point = point;
            double dist = (t != null ? Effects.chest(t, 1f) : point).distanceTo(h);
            this.travel = t != null ? Math.max(2, Math.min(5, (int) (dist / 3.5))) : 6;
            for (int i = 0; i < 5; i++) Particles.spawn(Kind.SPARK, h, randDir().multiply(0.07), 6, 0.04f).color(i % 2 == 0 ? color : GOLD, 1f);
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
                    VfxManager.add(new Burst(e, n > 1 ? 0.9f : 0.55f, color));
                    if (healing) {
                        for (int i = 0; i < n + 1; i++) plus(e.add(rf(-0.4f, 0.4f), rf(-0.2f, 0.5f), rf(-0.4f, 0.4f)), 0.04f, 20, 0.13f, i % 2 == 0 ? HEAL : HOLY);
                        if (n > 1) featherBurst(e, 4, 0.1f, 0xFFFFFF);
                    } else {
                        Effects.sparks(e, 6 * n, 0.25f, GOLD, 0.06f);
                    }
                }
            }
            if (age < travel) {
                Vec3d p = from.lerp(end(w, 1f), (age + 0.5) / travel);
                Particles.P pp = Particles.spawn(Kind.GLOW, p, Vec3d.ZERO, 10, 0.08f);
                pp.color(color, 0.8f);
                if (R.nextInt(3) == 0) feather(p, new Vec3d(0, 0.02, 0), rf(25, 40), 0.1f, 0xFFFFFF);
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
            Vec3d upv = side.crossProduct(d).normalize();
            Vec3d[] ps = new Vec3d[n];
            for (int i = 0; i < n; i++) {
                float k = MathHelper.clamp((a - i * 0.3f) / travel, 0, 1);
                double ang = i * Math.PI * 2 / 3 + k * Math.PI * 2;
                double rad = n > 1 ? 0.3 * Math.sin(k * Math.PI) : 0;
                ps[i] = from.add(d.multiply(k)).add(side.multiply(Math.cos(ang) * rad)).add(upv.multiply(Math.sin(ang) * rad)).subtract(c.cam);
            }
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            for (Vec3d p : ps) Draw.streak(st, c.m, p, d, 1.2f, 0.12f, r(color), g(color), b(color), 0.7f);
            VertexConsumer gv = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            for (Vec3d p : ps) Draw.billboard(gv, c.m, c.camRot, p.x, p.y, p.z, 0.45f, 0, r(color), g(color), b(color), 0.8f * nearFade(p, 0.5f));
            VertexConsumer fv = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            for (Vec3d p : ps) Draw.billboard(fv, c.m, c.camRot, p.x, p.y, p.z, 0.32f, a * 0.3f, 1f, 1f, 0.95f, 1f);
        }
    }

    // ================================================================ 1/2. 치유·축복의 파동
    static final class Wave extends Fx {
        final Vec3d p;
        final float rad;
        final int k, col, col2;

        Wave(ClientWorld w, Vec3d p, float rad, int k) {
            super(26);
            this.p = p;
            this.rad = rad;
            this.k = k;
            this.col = k == 0 ? HEAL : GOLD;
            this.col2 = k == 0 ? HOLY : SKY;
            VfxManager.add(new Burst(p.add(0, 1.2, 0), 1.3f, HOLY));
            featherBurst(p.add(0, 1.0, 0), 16, 0.22f, 0xFFFFFF);
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                Particles.P pp = Particles.spawn(Kind.GLOW, p.add(Math.cos(a) * rad * 0.3, 0.15, Math.sin(a) * rad * 0.3),
                        new Vec3d(Math.cos(a) * 0.22, rf(0.02f, 0.08f), Math.sin(a) * 0.22), rf(14, 22), rf(0.07f, 0.12f));
                pp.color(i % 2 == 0 ? col : col2, 1f);
                pp.drag = 0.9f;
            }
            CameraController.hitstop(1f);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float grow = Effects.outBack(Math.min(1, a / 7f));
            float fade = Math.min(1, (life - a) / 8f);
            Vec3d base = p.add(0, 0.05, 0).subtract(c.cam);
            // 바닥 마법진
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y, base.z, rad * grow, a * 0.03f, r(col), g(col), b(col), 0.85f * fade);
            Draw.flat(ci, c.m, base.x, base.y + 0.01, base.z, rad * 0.45f * grow, -a * 0.05f, r(col2), g(col2), b(col2), 0.6f * fade);
            // 퍼지는 파동
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            for (int j = 0; j < 2; j++) {
                float t = MathHelper.clamp((a - j * 4) / 12f, 0, 1);
                if (t <= 0 || t >= 1) continue;
                float e = 1 - (1 - t) * (1 - t);
                Draw.flat(sw, c.m, base.x, base.y + 0.02 + j * 0.01, base.z, rad * 1.1f * e, 0, r(col2), g(col2), b(col2), (1 - t) * 0.8f);
            }
            // 둘레에서 솟는 짧은 빛기둥
            VertexConsumer bv = c.imm.getBuffer(AWRenderLayers.additive(BEAM));
            float rise = MathHelper.clamp((a - 2) / 8f, 0, 1);
            for (int i = 0; i < 10; i++) {
                double ang = i * Math.PI * 2 / 10 + a * 0.02;
                Vec3d bot = base.add(Math.cos(ang) * rad * grow, 0, Math.sin(ang) * rad * grow);
                beam(bv, c, bot, bot.add(0, 2.4 * rise, 0), 0.22f, i % 2 == 0 ? col : col2, 0.7f * fade * rise, -a * 0.08f, 0f, 0.5f);
            }
            // 위로 오르는 빛 고리
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (int j = 0; j < 3; j++) {
                float t = MathHelper.clamp((a - j * 3) / 12f, 0, 1);
                if (t <= 0 || t >= 1) continue;
                glowRing(gl, c.m, base.add(0, 0.2 + t * 2.4, 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), rad * (0.9f - 0.3f * t), 0.22f, j % 2 == 0 ? col : col2, (1 - t) * 0.75f, 56);
            }
        }
    }

    // ================================================================ 버프가 걸리는 순간 (오르는 고리 + 빛기둥, 큰 축복은 빛의 날개)
    static final class Buffed extends Fx {
        final int id;
        final boolean big;

        Buffed(int id, boolean big) {
            super(big ? 34 : 20);
            this.id = id;
            this.big = big;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(id);
            if (e != null && age == 0) {
                for (int i = 0; i < (big ? 22 : 12); i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.5f, 0.5f), rf(0f, 0.4f), rf(-0.5f, 0.5f)), new Vec3d(0, rf(0.08f, 0.18f), 0), rf(12, 18), 0.07f);
                    pp.color(i % 3 == 0 ? SKY : GOLD, 1f);
                    pp.drag = 0.93f;
                }
                if (big) {
                    VfxManager.add(new Burst(e.getPos().add(0, e.getHeight() * 0.6, 0), 1.6f, GOLD));
                    featherBurst(e.getPos().add(0, e.getHeight() * 0.7, 0), 12, 0.18f, 0xFFF8E8);
                    if (e == MinecraftClient.getInstance().player) CameraController.addShake(0.15f, 5);
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float a = age + c.tickDelta;
            float t = a / life;
            float fade = 1 - t;
            boolean fp = Effects.isLocalFp(e);
            Vec3d p = e.getLerpedPos(c.tickDelta).subtract(c.cam);
            float h = e.getHeight();
            // 발밑 고리
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING_SOFT));
            Draw.flat(ring, c.m, p.x, p.y + 0.05, p.z, 0.5f + (big ? 1.6f : 1.0f) * t, 0, r(GOLD), g(GOLD), b(GOLD), 0.85f * fade);
            // 오르는 빛 고리
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (int j = 0; j < (big ? 3 : 2); j++) {
                float k = MathHelper.clamp((a - j * 4) / 12f, 0, 1);
                if (k <= 0 || k >= 1) continue;
                glowRing(gl, c.m, p.add(0, k * (h + 0.4), 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), 0.65f - 0.15f * k, 0.12f, j == 1 ? SKY : GOLD, (1 - k) * 0.9f, 36);
            }
            if (fp) return;
            // 빛기둥
            VertexConsumer bv = c.imm.getBuffer(AWRenderLayers.additive(BEAM));
            beam(bv, c, p, p.add(0, (h + 1.2) * (0.5f + 0.5f * Math.min(1, a / 4f)), 0), big ? 0.6f : 0.4f, GOLD, 0.55f * fade, -a * 0.1f, 0f, 0.55f);
            // 큰 축복: 등 뒤에 펼쳐지는 빛의 날개
            if (big) {
                float wk = com.abilitywar.client.fx.dragon.DragonParts.env(a, 0, 6, life - 10, life);
                if (wk > 0.01f) {
                    float yaw = (e instanceof PlayerEntity pe ? MathHelper.lerp(c.tickDelta, pe.prevBodyYaw, pe.bodyYaw) : e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
                    Vec3d back = new Vec3d(MathHelper.sin(yaw), 0, -MathHelper.cos(yaw));
                    Vec3d rgt = new Vec3d(-back.z, 0, back.x);
                    Vec3d root = p.add(0, h * 0.72, 0).add(back.multiply(0.35));
                    VertexConsumer wv = c.imm.getBuffer(AWRenderLayers.additive(AngelParts.WING));
                    float sz = 1.7f * (0.6f + 0.4f * Effects.outBack(Math.min(1, a / 6f)));
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3d out = rgt.multiply(s);
                        // 텍스처: 뿌리 = 왼쪽 아래 (u0, v1), 날개 끝 = 오른쪽 위 (u1, v0)
                        Vec3d ax = out.multiply(sz * 0.5), ay = new Vec3d(0, sz * 0.5, 0).add(back.multiply(sz * 0.1));
                        Vec3d cc = root.add(ax).add(ay).subtract(new Vec3d(0, sz * 0.1, 0));
                        Draw.quadUV(wv, c.m, cc, ax, ay, 1f, 0.9f, 0.6f, 0.75f * wk, 0, 0, 1, 1);
                    }
                }
            }
        }
    }

    // ================================================================ 3. 축복: 하늘에서 내려오는 금빛
    static final class Bless extends Fx {
        final int id;

        Bless(int id) {
            super(18);
            this.id = id;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(id);
            if (e != null && age < 7) {
                for (int i = 0; i < 3; i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(rf(-0.8f, 0.8f), e.getHeight() + rf(2.5f, 4f), rf(-0.8f, 0.8f)), new Vec3d(0, -0.22, 0), 14, rf(0.06f, 0.1f));
                    pp.color(GOLD, 1f);
                    pp.drag = 1f;
                }
            }
            if (e != null && age == 6) feather(e.getPos().add(0, e.getHeight() + 3, 0), new Vec3d(0, -0.03, 0), 60, 0.25f, 0xFFF0C8);
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float a = age + c.tickDelta;
            Vec3d p = e.getLerpedPos(c.tickDelta).subtract(c.cam);
            float h = e.getHeight();
            Vec3d top = p.add(0, h + 4.5, 0);
            float in = Math.min(1, a / 5f), out = Math.min(1, (life - a) / 6f);
            // 머리 위의 작은 마법진
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.flat(ci, c.m, top.x, top.y, top.z, 1.4f * Effects.outBack(in), a * 0.08f, r(GOLD), g(GOLD), b(GOLD), 0.9f * out);
            // 내려꽂히는 금빛
            float drop = MathHelper.clamp((a - 4) / 3f, 0, 1);
            if (drop > 0) {
                VertexConsumer bv = c.imm.getBuffer(AWRenderLayers.additive(BEAM));
                Vec3d bottom = top.add(0, -(h + 4.5) * drop, 0);
                beam(bv, c, top, bottom, 0.5f, GOLD, 0.8f * out, -a * 0.15f, 0.4f, 0f);
                beam(bv, c, top, bottom, 0.2f, 0xFFFFFF, 0.9f * out, -a * 0.2f, 0.4f, 0f);
            }
        }
    }

    // ================================================================ 4. 치유의 빛줄기 (손 → 대상)
    static final class Stream extends Fx {
        final int caster, target, dur;

        Stream(int caster, int target, int dur) {
            super(dur + 6);
            this.caster = caster;
            this.target = target;
            this.dur = dur;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity c = w.getEntityById(caster), t = w.getEntityById(target);
            if (c != null && t != null && age < dur) {
                Vec3d h = HandTracker.hand(c, 0, 1f), e = Effects.chest(t, 1f);
                for (int i = 0; i < 3; i++) {
                    Particles.P pp = Particles.spawn(Kind.GLOW, h.lerp(e, R.nextFloat()), e.subtract(h).normalize().multiply(0.2), rf(6, 10), rf(0.05f, 0.09f));
                    pp.color(i == 0 ? HOLY : HEAL, 1f);
                    pp.drag = 0.95f;
                }
                if (age % 3 == 0) plus(e.add(rf(-0.4f, 0.4f), rf(-0.3f, 0.6f), rf(-0.4f, 0.4f)), 0.04f, 18, 0.12f, HEAL);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity ce = c.world.getEntityById(caster), te = c.world.getEntityById(target);
            if (ce == null || te == null) return;
            float a = age + c.tickDelta;
            float k = Math.min(1, a / 3f) * Math.min(1, (life - a) / 6f);
            if (k <= 0) return;
            Vec3d h = HandTracker.hand(ce, 0, c.tickDelta).subtract(c.cam), e = Effects.chest(te, c.tickDelta).subtract(c.cam);
            boolean self = ce == te;
            if (self) h = e.add(0, 2.5, 0);
            Vec3d d = e.subtract(h);
            Vec3d side = d.crossProduct(new Vec3d(0, 1, 0));
            side = side.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : side.normalize();
            Vec3d upv = side.crossProduct(d).normalize();
            // 가운데 줄기
            VertexConsumer bv = c.imm.getBuffer(AWRenderLayers.additive(BEAM));
            beam(bv, c, h, e, 0.32f * k, HEAL, 0.85f * k, -a * 0.25f, 0.1f, 0.1f);
            beam(bv, c, h, e, 0.12f * k, 0xFFFFFF, 0.9f * k, -a * 0.3f, 0.1f, 0.1f);
            // 감아 도는 빛 두 줄
            int N = 18;
            Vec3d[] pts = new Vec3d[N];
            float[] wid = new float[N], al = new float[N];
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            for (int s = 0; s < 2; s++) {
                for (int i = 0; i < N; i++) {
                    float u = i / (float) (N - 1);
                    double ang = u * Math.PI * 4 - a * 0.5 + s * Math.PI;
                    double rad = 0.25 * Math.sin(u * Math.PI);
                    pts[i] = h.add(d.multiply(u)).add(side.multiply(Math.cos(ang) * rad)).add(upv.multiply(Math.sin(ang) * rad));
                    wid[i] = 0.06f * k;
                    al[i] = 0.8f * k * (float) Math.sin(u * Math.PI);
                }
                Draw.ribbon(st, c.m, pts, N, wid, r(s == 0 ? GOLD : HOLY), g(s == 0 ? GOLD : HOLY), b(s == 0 ? GOLD : HOLY), al, 0.5f, -a * 0.1f);
            }
            // 대상을 감싸는 빛
            if (!Effects.isLocalFp(te)) {
                VertexConsumer gv = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
                Draw.billboard(gv, c.m, c.camRot, e.x, e.y, e.z, 1.4f, 0, r(HEAL), g(HEAL), b(HEAL), 0.45f * k * Fx.nearFade(e, 1.4f));
            }
            if (!self && !Effects.isLocalFp(ce)) {
                VertexConsumer fv = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
                Draw.billboard(fv, c.m, c.camRot, h.x, h.y, h.z, 0.5f, a * 0.2f, 1f, 1f, 0.9f, 0.9f * k);
            }
        }
    }

    // ================================================================ 4. 치유의 개화
    static final class Bloom extends Fx {
        final int id;

        Bloom(ClientWorld w, int id) {
            super(28);
            this.id = id;
            Entity e = w.getEntityById(id);
            if (e != null) {
                Vec3d cc = e.getPos().add(0, e.getHeight() * 0.55, 0);
                VfxManager.add(new Burst(cc, 1.8f, HEAL));
                featherBurst(cc, 14, 0.2f, 0xFFFFFF);
                for (int i = 0; i < 5; i++) plus(cc.add(rf(-0.6f, 0.6f), rf(-0.2f, 0.8f), rf(-0.6f, 0.6f)), rf(0.04f, 0.07f), rf(20, 28), rf(0.14f, 0.2f), i % 2 == 0 ? HEAL : HOLY);
                if (e == MinecraftClient.getInstance().player) CameraController.addShake(0.1f, 4);
            }
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float a = age + c.tickDelta;
            float open = Effects.outBack(Math.min(1, a / 8f));
            float fade = Math.min(1, (life - a) / 10f);
            Vec3d p = e.getLerpedPos(c.tickDelta).subtract(c.cam);
            // 바닥 마법진
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            Draw.flat(ci, c.m, p.x, p.y + 0.05, p.z, 2.2f * open, a * 0.04f, r(HEAL), g(HEAL), b(HEAL), 0.85f * fade);
            // 꽃잎 (발밑에서 비스듬히 피어남)
            VertexConsumer gv = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            for (int ring = 0; ring < 2; ring++) {
                int n = ring == 0 ? 8 : 6;
                for (int i = 0; i < n; i++) {
                    double ang = i * Math.PI * 2 / n + ring * 0.4 + a * 0.01;
                    Vec3d dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
                    float len = (ring == 0 ? 1.3f : 0.9f) * open;
                    double lift = Math.toRadians(ring == 0 ? 25 + 20 * (1 - open) : 50);
                    Vec3d pd = dir.multiply(Math.cos(lift)).add(0, Math.sin(lift), 0);
                    Vec3d cc = p.add(0, 0.1, 0).add(pd.multiply(len * 0.5));
                    Vec3d wv = new Vec3d(-dir.z, 0, dir.x).multiply(len * 0.22);
                    int col = ring == 0 ? HEAL : HOLY;
                    Draw.quad(gv, c.m, cc, wv, pd.multiply(len * 0.55), r(col), g(col), b(col), 0.75f * fade);
                }
            }
            // 빛기둥
            VertexConsumer bv = c.imm.getBuffer(AWRenderLayers.additive(BEAM));
            beam(bv, c, p, p.add(0, 3.2 * open, 0), 0.7f, HEAL, 0.6f * fade, -a * 0.1f, 0f, 0.5f);
            beam(bv, c, p, p.add(0, 3.2 * open, 0), 0.25f, 0xFFFFFF, 0.8f * fade, -a * 0.14f, 0f, 0.5f);
            // 오르는 고리
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (int j = 0; j < 2; j++) {
                float k = MathHelper.clamp((a - j * 5) / 14f, 0, 1);
                if (k <= 0 || k >= 1) continue;
                glowRing(gl, c.m, p.add(0, 0.1 + k * 2.4, 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), 1.1f - 0.4f * k, 0.18f, j == 0 ? HEAL : HOLY, (1 - k) * 0.8f, 40);
            }
        }
    }

    // ================================================================ T. 날개 바람
    static final class Gust extends Fx {
        final Vec3d p;
        final boolean fly;

        Gust(Vec3d p, boolean fly) {
            super(16);
            this.p = p;
            this.fly = fly;
        }

        @Override
        public void render(Ctx c) {
            float t = t(c);
            float e = 1 - (1 - t) * (1 - t);
            Vec3d base = p.add(0, 0.06, 0).subtract(c.cam);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.flat(sw, c.m, base.x, base.y, base.z, 0.5f + e * (fly ? 3.5f : 2f), 0, 0.95f, 0.97f, 1f, (1 - t) * (fly ? 0.8f : 0.5f));
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            glowRing(gl, c.m, base.add(0, fly ? 0.3 + e * 0.6 : 1.6 - e * 1.4, 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), 0.6f + e * 1.2f, 0.18f, SKY, (1 - t) * 0.6f, 40);
        }
    }

    // ================================================================ R. 천상의 심판: 하늘의 마법진들
    static final class Ult extends Fx {
        final int caster;
        final Vec3d o;
        final Vec3d[] circles = new Vec3d[7];
        final int dur;

        Ult(int caster, Vec3d o, int dur) {
            super(dur + 10);
            this.caster = caster;
            this.o = o;
            this.dur = dur;
            for (int i = 0; i < circles.length; i++) circles[i] = AngelAbility.circle(o, i);
        }

        float appear(float a, int i) {
            return MathHelper.clamp((a - 6 - i * 2.5f) / 8f, 0, 1);
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age > 6 && age < dur) {
                // 마법진에서 빛 가루가 내려옴
                for (int i = 0; i < 3; i++) {
                    Vec3d cc = circles[R.nextInt(circles.length)];
                    Particles.P pp = Particles.spawn(Kind.GLOW, cc.add(rf(-2.5f, 2.5f), -0.2, rf(-2.5f, 2.5f)), new Vec3d(0, -rf(0.1f, 0.2f), 0), rf(40, 60), rf(0.06f, 0.1f));
                    pp.color(R.nextBoolean() ? GOLD : HOLY, 0.9f);
                    pp.drag = 1f;
                }
                if (age % 4 == 0) feather(o.add(rf(-10, 10), rf(9, 13), rf(-10, 10)), new Vec3d(0, -0.04, 0), rf(80, 120), rf(0.2f, 0.3f), 0xFFFFFF);
            }
            for (int i = 0; i < circles.length; i++) {
                if (age == 6 + (int) (i * 2.5f) + 4) VfxManager.add(new Burst(circles[i], i == 0 ? 2.4f : 1.4f, HOLY));
            }
            if (age == 28) CameraController.addShake(0.2f, 6);
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float out = 1 - MathHelper.clamp((a - dur + 4) / 12f, 0, 1);
            Vec3d base = o.subtract(c.cam);
            // 땅의 마법진 + 하늘의 마법진들
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
            float g0 = Effects.outBack(MathHelper.clamp(a / 10f, 0, 1));
            Draw.flat(ci, c.m, base.x, base.y + 0.05, base.z, 10f * g0, a * 0.01f, r(GOLD), g(GOLD), b(GOLD), 0.55f * out);
            for (int i = 0; i < circles.length; i++) {
                float k = appear(a, i);
                if (k <= 0) continue;
                Vec3d cc = circles[i].subtract(c.cam);
                float size = (i == 0 ? 6.5f : 3.2f) * Effects.outBack(k);
                int col = i == 0 ? HOLY : GOLD;
                float rot = a * (i % 2 == 0 ? 0.02f : -0.03f) + i;
                Draw.flat(ci, c.m, cc.x, cc.y, cc.z, size, rot, r(col), g(col), b(col), 0.95f * k * out);
                Draw.flat(ci, c.m, cc.x, cc.y - 0.05, cc.z, size * 0.55f, -rot * 1.6f, 1f, 1f, 1f, 0.6f * k * out);
            }
            // 마법진 테두리 빛 + 마법진을 잇는 큰 고리
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (int i = 0; i < circles.length; i++) {
                float k = appear(a, i);
                if (k <= 0) continue;
                Vec3d cc = circles[i].subtract(c.cam);
                float size = (i == 0 ? 6.5f : 3.2f) * Effects.outBack(k);
                float draw = MathHelper.clamp(k * 1.4f, 0, 1);
                glowRing(gl, c.m, cc, new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), size * 1.02f, 0.3f, i == 0 ? HOLY : GOLD, 0.8f * draw * out, 64);
            }
            float link = MathHelper.clamp((a - 22) / 8f, 0, 1);
            if (link > 0) {
                glowRing(gl, c.m, base.add(0, 14, 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), 9f, 0.35f, GOLD, 0.6f * link * out, 96);
                glowRing(gl, c.m, base.add(0, 14.3, 0), new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), 5.2f, 0.25f, HOLY, 0.5f * link * out, 72);
            }
            // 하늘에서 내려오는 부드러운 빛 (god ray)
            float ray = MathHelper.clamp((a - 16) / 12f, 0, 1) * out;
            if (ray > 0) {
                VertexConsumer bv = c.imm.getBuffer(AWRenderLayers.additive(BEAM));
                Vec3d top = circles[0].subtract(c.cam);
                beam(bv, c, top, base.add(0, 0.1, 0), 3.2f, HOLY, 0.16f * ray, -a * 0.02f, 0.15f, 0.4f);
                for (int i = 1; i < circles.length; i++) {
                    Vec3d cc = circles[i].subtract(c.cam);
                    beam(bv, c, cc, cc.add(0, -cc.y + base.y, 0), 1.3f, GOLD, 0.08f * ray, -a * 0.03f, 0.15f, 0.4f);
                }
            }
            // 시전자 둘레의 빛
            Entity ce = c.world.getEntityById(caster);
            if (ce != null) {
                Vec3d cp = Effects.chest(ce, c.tickDelta).subtract(c.cam);
                float hk = MathHelper.clamp((a - 6) / 10f, 0, 1) * out;
                VertexConsumer gv = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
                Draw.billboard(gv, c.m, c.camRot, cp.x, cp.y, cp.z, 3.2f, 0, 1f, 0.93f, 0.75f, 0.3f * hk * nearFade(cp, 3));
            }
        }
    }

    // ================================================================ R. 천상의 빛줄기 (마법진 → 대상)
    static final class Beam extends Fx {
        final Vec3d from, to;
        final boolean ally;
        final int col;

        Beam(ClientWorld w, Vec3d from, Vec3d to, boolean ally) {
            super(16);
            this.from = from;
            this.to = to;
            this.ally = ally;
            this.col = ally ? HEAL : HOLY;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age == 2) {
                VfxManager.add(new Burst(to.add(0, 0.5, 0), ally ? 1.2f : 1.6f, ally ? HEAL : GOLD));
                VfxManager.add(new Burst(from, 1.2f, HOLY));
                if (ally) {
                    for (int i = 0; i < 3; i++) plus(to.add(rf(-0.5f, 0.5f), rf(0.6f, 1.6f), rf(-0.5f, 0.5f)), 0.05f, 22, 0.15f, i % 2 == 0 ? HEAL : HOLY);
                    featherBurst(to.add(0, 1, 0), 4, 0.1f, 0xFFFFFF);
                } else {
                    BlockState st = Effects.groundState(w, to);
                    if (st != null) for (int i = 0; i < 8; i++) {
                        double a = R.nextDouble() * Math.PI * 2;
                        Debris.spawn(st, to.add(Math.cos(a) * 0.6, 0.2, Math.sin(a) * 0.6), new Vec3d(Math.cos(a) * 0.2, rf(0.3f, 0.55f), Math.sin(a) * 0.2), rf(0.15f, 0.3f), rf(16, 26), null, 0, true);
                    }
                    for (int i = 0; i < 10; i++) {
                        double a = i * Math.PI * 2 / 10;
                        Particles.spawn(Kind.SMOKE_ADD, to.add(Math.cos(a) * 0.8, 0.15, Math.sin(a) * 0.8), new Vec3d(Math.cos(a) * 0.12, 0.03, Math.sin(a) * 0.12), rf(12, 20), rf(0.4f, 0.6f)).color(0xE8DCC0, 0.35f);
                    }
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player != null && mc.player.getPos().distanceTo(to) < 14) CameraController.addShake(0.12f, 4);
                }
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float drop = MathHelper.clamp(a / 2f, 0, 1);
            float fade = a < 6 ? 1 : Math.max(0, 1 - (a - 6) / 10f);
            Vec3d f = from.subtract(c.cam), t = to.subtract(c.cam);
            Vec3d end = f.lerp(t, drop);
            float w = (ally ? 0.55f : 0.75f) * (1.15f - 0.5f * (a / life));
            VertexConsumer bv = c.imm.getBuffer(AWRenderLayers.additive(BEAM));
            beam(bv, c, f, end, w * 1.8f, ally ? HEAL : GOLD, 0.45f * fade, -a * 0.2f, 0.12f, 0f);
            beam(bv, c, f, end, w, col, 0.9f * fade, -a * 0.25f, 0.12f, 0f);
            beam(bv, c, f, end, w * 0.35f, 0xFFFFFF, 1f * fade, -a * 0.3f, 0.12f, 0f);
            float tk = Math.max(0, (a - 2) / 12f);
            if (tk > 0 && tk < 1) {
                float e = 1 - (1 - tk) * (1 - tk);
                VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
                Draw.flat(sw, c.m, t.x, t.y + 0.06, t.z, 0.5f + e * (ally ? 2f : 3f), 0, r(col), g(col), b(col), (1 - tk) * 0.9f);
                VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(CIRCLE));
                Draw.flat(ci, c.m, t.x, t.y + 0.05, t.z, 1.4f, a * 0.1f, r(ally ? HEAL : GOLD), g(ally ? HEAL : GOLD), b(ally ? HEAL : GOLD), 0.8f * fade);
            }
        }
    }

    private AngelFx() {
    }
}
