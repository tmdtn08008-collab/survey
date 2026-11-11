package com.abilitywar.client.fx.paladin;

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
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

import static com.abilitywar.client.fx.Effects.R;
import static com.abilitywar.client.fx.Effects.rf;
import static com.abilitywar.client.fx.Particles.Kind;

/** 성기사 이펙트: 빛기둥, 심판의 일격, 정화의 땅, 빛의 사슬, 성검강림 (거대한 성검), 성스러운 오라. */
public final class PaladinFx {
    public static final int GOLD = 0xFFE7A0;
    public static final int DEEP_GOLD = 0xFFC860;
    public static final int SKY = 0x9FD8FF;
    public static final int WHITE = 0xFFFDF4;

    /** 오라가 켜진 성기사 → 마지막 알림 시각. */
    static final Map<Integer, Long> AURA = new HashMap<>();

    public static boolean auraOn(int id) {
        return AURA.containsKey(id);
    }

    static float r(int c) {
        return ((c >> 16) & 255) / 255f;
    }

    static float g(int c) {
        return ((c >> 8) & 255) / 255f;
    }

    static float b(int c) {
        return (c & 255) / 255f;
    }

    public static boolean spawn(String type, NbtCompound n, ClientWorld w, Vec3d p) {
        switch (type) {
            case "paladin_pillar" -> VfxManager.add(new Pillar(w, p, n.contains("s") ? n.getFloat("s") : 1f));
            case "paladin_judgment" -> judgment(w, p, n.getFloat("r"));
            case "paladin_consecrate" -> VfxManager.add(new Consecrate(w, p, n.getFloat("r"), n.getInt("d")));
            case "paladin_chains" -> VfxManager.add(new Chains(n.getInt("c"), n.getInt("e"), n.getInt("d")));
            case "paladin_ult" -> VfxManager.add(new GiantSword(w, n.getInt("c"), new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz")), n.getInt("d")));
            case "paladin_aura" -> aura(w, n.getInt("c"), n.getBoolean("on"), n.getBoolean("q"));
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void tick(ClientWorld w) {
        long now = w.getTime();
        AURA.entrySet().removeIf(en -> now - en.getValue() > 70 || w.getEntityById(en.getKey()) == null);
        for (Map.Entry<Integer, Long> en : AURA.entrySet()) {
            Entity e = w.getEntityById(en.getKey());
            if (e == null || e.isInvisible()) continue;
            if ((now + e.getId()) % 3 == 0) {
                double a = R.nextDouble() * Math.PI * 2, rr = rf(0.5f, 7.5f);
                Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr), new Vec3d(0, rf(0.03f, 0.07f), 0), rf(20, 34), rf(0.04f, 0.07f));
                pp.color(R.nextBoolean() ? GOLD : WHITE, 0.85f);
                pp.drag = 0.97f;
            }
        }
    }

    public static void clear() {
        AURA.clear();
    }

    public static void render(Fx.Ctx c) {
        if (AURA.isEmpty()) return;
        float now = c.world.getTime() + c.tickDelta;
        for (Map.Entry<Integer, Long> en : AURA.entrySet()) {
            Entity e = c.world.getEntityById(en.getKey());
            if (e == null || e.isInvisible()) continue;
            Vec3d pos = e.getLerpedPos(c.tickDelta);
            Vec3d gnd = Effects.ground(c.world, pos.add(0, 0.3, 0), 3);
            Vec3d base = (gnd != null ? gnd : pos).add(0, 0.04, 0).subtract(c.cam);
            float pulse = 0.75f + 0.25f * MathHelper.sin(now * 0.12f);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            ring(gl, c.m, base, 8f, 0.1f, GOLD, 0.28f * pulse, 96);
            ring(gl, c.m, base.add(0, 0.01, 0), 7.6f, 0.04f, WHITE, 0.18f * pulse, 96);
            // 8방향으로 뻗은 빛살 (성역 표시)
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + now * 0.004;
                Vec3d d = new Vec3d(Math.cos(a), 0, Math.sin(a)), s = new Vec3d(-d.z, 0, d.x);
                Vec3d p0 = base.add(d.multiply(1.2)), p1 = base.add(d.multiply(7.6));
                quad(gl, c.m, p0.add(s.multiply(0.05)), p0.subtract(s.multiply(0.05)), p1.subtract(s.multiply(0.2)), p1.add(s.multiply(0.2)), GOLD, 0.0f, 0.22f * pulse);
            }
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
            Draw.flat(ci, c.m, base.x, base.y + 0.01, base.z, 1.2f, now * 0.02f, r(GOLD), g(GOLD), b(GOLD), 0.55f * pulse);
        }
    }

    // ================================================================ 그리기 도우미
    static void ring(VertexConsumer vc, Matrix4f m, Vec3d c, float rad, float w, int col, float alpha, int seg) {
        float cr = r(col), cg = g(col), cb = b(col);
        for (int band = 0; band < 2; band++) {
            float r0 = band == 0 ? rad - w : rad, r1 = band == 0 ? rad : rad + w;
            float a0 = band == 0 ? 0 : alpha, a1 = band == 0 ? alpha : 0;
            for (int i = 0; i < seg; i++) {
                double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
                float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
                Draw.vc(vc, m, (float) c.x + c0 * r0, (float) c.y, (float) c.z + s0 * r0, cr, cg, cb, a0);
                Draw.vc(vc, m, (float) c.x + c0 * r1, (float) c.y, (float) c.z + s0 * r1, cr, cg, cb, a1);
                Draw.vc(vc, m, (float) c.x + c1 * r1, (float) c.y, (float) c.z + s1 * r1, cr, cg, cb, a1);
                Draw.vc(vc, m, (float) c.x + c1 * r0, (float) c.y, (float) c.z + s1 * r0, cr, cg, cb, a0);
            }
        }
    }

    static void quad(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b2, Vec3d c, Vec3d d, int col, float a0, float a1) {
        float cr = r(col), cg = g(col), cb = b(col);
        Draw.vc(vc, m, (float) a.x, (float) a.y, (float) a.z, cr, cg, cb, a1);
        Draw.vc(vc, m, (float) b2.x, (float) b2.y, (float) b2.z, cr, cg, cb, a1);
        Draw.vc(vc, m, (float) c.x, (float) c.y, (float) c.z, cr, cg, cb, a0);
        Draw.vc(vc, m, (float) d.x, (float) d.y, (float) d.z, cr, cg, cb, a0);
    }

    /** 세로 빛기둥 (카메라를 향함): 아래 bot, 높이 h. */
    static void beam(Fx.Ctx c, Vec3d bot, float h, float w, int col, float a) {
        VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
        Draw.streak(st, c.m, bot.add(0, h, 0), new Vec3d(0, 1, 0), h, w, r(col), g(col), b(col), a);
        Draw.streak(st, c.m, bot.add(0, h * 0.2, 0), new Vec3d(0, -1, 0), h * 0.2f, w, r(col), g(col), b(col), a);
        VertexConsumer gw = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
        Draw.billboard(gw, c.m, c.camRot, bot.x, bot.y + 0.2, bot.z, w * 3f, 0, r(col), g(col), b(col), a * 0.6f);
    }

    // ================================================================ 빛기둥 (평타 막타 · 심판)
    public static final class Pillar extends Fx {
        final Vec3d p;
        final float s;

        public Pillar(ClientWorld w, Vec3d at, float s) {
            super(16);
            Vec3d gnd = Effects.ground(w, at.add(0, 0.6, 0), 3);
            this.p = gnd != null ? gnd : at;
            this.s = s;
            for (int i = 0; i < (int) (14 * s); i++) {
                double a = R.nextDouble() * Math.PI * 2;
                Particles.P pp = Particles.spawn(Kind.GLOW, p.add(Math.cos(a) * 0.3 * s, 0.1, Math.sin(a) * 0.3 * s), new Vec3d(Math.cos(a) * 0.08, rf(0.1f, 0.3f), Math.sin(a) * 0.08), rf(10, 18), rf(0.05f, 0.1f));
                pp.color(i % 2 == 0 ? WHITE : GOLD, 1f);
            }
            VfxManager.add(new Effects.Impact(p.add(0, 0.5, 0), "punch", 0.8f * s, GOLD));
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta, t = a / life;
            float drop = MathHelper.clamp(a / 3f, 0, 1);
            float fade = 1 - t * t;
            Vec3d bot = p.subtract(c.cam);
            beam(c, bot, 7f * s * drop, 0.45f * s * (1 - 0.5f * t), WHITE, 0.95f * fade);
            beam(c, bot, 7f * s * drop, 0.9f * s, GOLD, 0.45f * fade);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            ring(gl, c.m, bot.add(0, 0.04, 0), 0.4f + 1.4f * s * t, 0.12f, GOLD, 0.8f * fade, 40);
        }
    }

    // ================================================================ 1. 심판의 일격: 땅에 십자 빛
    static void judgment(ClientWorld w, Vec3d p, float rad) {
        VfxManager.add(new Cross(w, p, rad));
        BlockState st = Effects.groundState(w, p);
        if (st != null) for (int i = 0; i < 16; i++) {
            double a = R.nextDouble() * Math.PI * 2;
            Debris.spawn(st, p.add(Math.cos(a) * 1.2, 0.1, Math.sin(a) * 1.2), new Vec3d(Math.cos(a) * 0.15, rf(0.25f, 0.45f), Math.sin(a) * 0.15), rf(0.12f, 0.25f), rf(16, 26), null, 0, true);
        }
        Effects.sparks(p.add(0, 0.5, 0), 26, 0.5f, GOLD, 0.08f);
        CameraController.hitstop(1.2f);
    }

    static final class Cross extends Fx {
        final Vec3d p;
        final float rad, rot;

        Cross(ClientWorld w, Vec3d at, float rad) {
            super(26);
            Vec3d gnd = Effects.ground(w, at.add(0, 0.6, 0), 3);
            this.p = (gnd != null ? gnd : at).add(0, 0.03, 0);
            this.rad = rad;
            this.rot = R.nextFloat() * 6.28f;
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta, t = a / life;
            float grow = Effects.outBack(Math.min(1, a / 5f)), fade = Math.min(1, (life - a) / 10f);
            Vec3d base = p.subtract(c.cam);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (int i = 0; i < 4; i++) {
                double ang = rot + i * Math.PI / 2;
                Vec3d d = new Vec3d(Math.cos(ang), 0, Math.sin(ang)), s = new Vec3d(-d.z, 0, d.x);
                float len = rad * 1.6f * grow * (i % 2 == 0 ? 1f : 0.75f);
                Vec3d e0 = base.add(d.multiply(len));
                quad(gl, c.m, base.add(s.multiply(0.35)), base.subtract(s.multiply(0.35)), e0.subtract(s.multiply(0.05)), e0.add(s.multiply(0.05)), WHITE, 0f, 0.9f * fade);
                quad(gl, c.m, base.add(s.multiply(0.8)), base.subtract(s.multiply(0.8)), e0.subtract(s.multiply(0.2)), e0.add(s.multiply(0.2)), GOLD, 0f, 0.45f * fade);
            }
            for (int j = 0; j < 2; j++) {
                float tt = MathHelper.clamp((a - j * 3) / 12f, 0, 1);
                if (tt <= 0 || tt >= 1) continue;
                ring(gl, c.m, base.add(0, 0.02, 0), rad * 1.3f * (1 - (1 - tt) * (1 - tt)), 0.25f, j == 0 ? GOLD : WHITE, (1 - tt) * 0.85f, 64);
            }
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y + 0.01, base.z, rad * grow, rot + a * 0.02f, r(GOLD), g(GOLD), b(GOLD), 0.7f * fade);
        }
    }

    // ================================================================ 3. 정화의 땅
    static final class Consecrate extends Fx {
        final Vec3d p;
        final float rad;

        Consecrate(ClientWorld w, Vec3d at, float rad, int dur) {
            super(Math.max(20, dur));
            Vec3d gnd = Effects.ground(w, at.add(0, 0.6, 0), 3);
            this.p = (gnd != null ? gnd : at).add(0, 0.03, 0);
            this.rad = rad;
        }

        @Override
        public boolean tick(ClientWorld w) {
            for (int i = 0; i < 3; i++) {
                double a = R.nextDouble() * Math.PI * 2, rr = Math.sqrt(R.nextDouble()) * rad;
                Particles.P pp = Particles.spawn(i == 0 ? Kind.STREAK : Kind.GLOW, p.add(Math.cos(a) * rr, 0.05, Math.sin(a) * rr), new Vec3d(0, rf(0.05f, 0.14f), 0), rf(16, 28), rf(0.04f, 0.08f));
                pp.color(i == 0 ? WHITE : GOLD, 0.9f);
                pp.drag = 0.97f;
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float grow = Effects.outBack(Math.min(1, a / 8f)), fade = Math.min(1, (life - a) / 12f);
            float pulse = 0.8f + 0.2f * MathHelper.sin(a * 0.25f);
            Vec3d base = p.subtract(c.cam);
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y, base.z, rad * grow, a * 0.012f, r(GOLD), g(GOLD), b(GOLD), 0.75f * fade * pulse);
            VertexConsumer c2 = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
            Draw.flat(c2, c.m, base.x, base.y + 0.01, base.z, rad * 0.55f * grow, -a * 0.03f, r(WHITE), g(WHITE), b(WHITE), 0.6f * fade);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            ring(gl, c.m, base.add(0, 0.02, 0), rad * grow, 0.16f, GOLD, 0.85f * fade, 80);
            // 가운데 큰 십자 (땅에 새겨짐)
            float L = rad * 0.85f * grow, W = 0.32f;
            quad(gl, c.m, base.add(-W, 0.03, -L), base.add(W, 0.03, -L), base.add(W, 0.03, L), base.add(-W, 0.03, L), WHITE, 0.55f * fade, 0.55f * fade);
            quad(gl, c.m, base.add(-L * 0.6, 0.031, -W - L * 0.25), base.add(L * 0.6, 0.031, -W - L * 0.25), base.add(L * 0.6, 0.031, W - L * 0.25), base.add(-L * 0.6, 0.031, W - L * 0.25), WHITE, 0.55f * fade, 0.55f * fade);
            // 둘레에서 솟는 빛 벽
            for (int i = 0; i < 48; i++) {
                double t0 = i * Math.PI * 2 / 48, t1 = (i + 1) * Math.PI * 2 / 48;
                Vec3d q0 = base.add(Math.cos(t0) * rad * grow, 0, Math.sin(t0) * rad * grow), q1 = base.add(Math.cos(t1) * rad * grow, 0, Math.sin(t1) * rad * grow);
                quad(gl, c.m, q0, q1, q1.add(0, 1.6 * pulse, 0), q0.add(0, 1.6 * pulse, 0), GOLD, 0f, 0.3f * fade);
            }
        }
    }

    // ================================================================ 4. 빛의 사슬
    static final class Chains extends Fx {
        final int caster, target;

        Chains(int caster, int target, int dur) {
            super(Math.max(10, dur));
            this.caster = caster;
            this.target = target;
        }

        @Override
        public boolean tick(net.minecraft.client.world.ClientWorld w) {
            Entity t = w.getEntityById(target);
            if (t == null || !t.isAlive()) return true;
            if (age % 4 == 0) Effects.sparks(t.getPos().add(0, t.getHeight() * 0.5, 0), 3, 0.15f, GOLD, 0.05f);
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity ce = c.world.getEntityById(caster), te = c.world.getEntityById(target);
            if (te == null) return;
            float a = age + c.tickDelta;
            float fade = Math.min(1, (life - a) / 8f);
            float shoot = MathHelper.clamp(a / 4f, 0, 1);
            Vec3d tc = te.getLerpedPos(c.tickDelta).add(0, te.getHeight() * 0.5, 0);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            // 대상을 휘감는 사슬 고리 3개
            for (int k = 0; k < 3; k++) {
                float ang = a * (k % 2 == 0 ? 0.12f : -0.1f) + k * 2.1f;
                Vec3d ax = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
                Vec3d ay = new Vec3d(-Math.sin(ang) * 0.3, 1, Math.cos(ang) * 0.3).normalize();
                Vec3d n2 = ax.crossProduct(ay).normalize();
                ay = n2.crossProduct(ax).normalize();
                chainRing(gl, c, tc.add(0, (k - 1) * te.getHeight() * 0.28, 0).subtract(c.cam), ax, n2, te.getWidth() * 0.8f + 0.2f, shoot * fade);
            }
            if (ce == null) return;
            Vec3d hand = HandTracker.hand(ce, 0, c.tickDelta);
            Vec3d to = hand.lerp(tc, shoot);
            chainLine(gl, c, hand.subtract(c.cam), to.subtract(c.cam), a, fade);
        }

        static void chainRing(VertexConsumer gl, Ctx c, Vec3d ctr, Vec3d ax, Vec3d ay, float rad, float a) {
            int links = 14;
            for (int i = 0; i < links; i++) {
                double t = (i + 0.5) * Math.PI * 2 / links;
                Vec3d pos = ctr.add(ax.multiply(Math.cos(t) * rad)).add(ay.multiply(Math.sin(t) * rad));
                Vec3d tan = ax.multiply(-Math.sin(t)).add(ay.multiply(Math.cos(t))).normalize();
                Vec3d up = ax.crossProduct(ay).normalize();
                link(gl, c, pos, tan, i % 2 == 0 ? up : tan.crossProduct(up).normalize(), 0.12f, a);
            }
        }

        static void chainLine(VertexConsumer gl, Ctx c, Vec3d a, Vec3d b2, float time, float fade) {
            Vec3d d = b2.subtract(a);
            double len = d.length();
            if (len < 0.2) return;
            Vec3d dir = d.normalize();
            Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0));
            if (side.lengthSquared() < 1e-4) side = new Vec3d(1, 0, 0);
            side = side.normalize();
            Vec3d up = side.crossProduct(dir).normalize();
            int n = (int) (len / 0.22);
            for (int i = 0; i < n; i++) {
                double t = (i + 0.5) / n;
                double sag = Math.sin(t * Math.PI) * 0.15;
                Vec3d pos = a.add(d.multiply(t)).add(0, -sag, 0);
                float flow = 0.6f + 0.4f * MathHelper.sin(time * 0.5f - i * 0.6f);
                link(gl, c, pos, dir, i % 2 == 0 ? up : side, 0.11f, fade * flow);
            }
        }

        /** 사슬 고리 하나: 길쭉한 마름모 테두리. */
        static void link(VertexConsumer gl, Ctx c, Vec3d p, Vec3d along, Vec3d across, float s, float a) {
            Vec3d L = along.multiply(s), W = across.multiply(s * 0.5);
            Vec3d[] pts = {p.add(L), p.add(W), p.subtract(L), p.subtract(W)};
            for (int i = 0; i < 4; i++) {
                Vec3d p0 = pts[i], p1 = pts[(i + 1) % 4];
                Vec3d mid = p0.add(p1).multiply(0.5);
                Vec3d off = mid.subtract(p).normalize().multiply(s * 0.12);
                quad(gl, c.m, p0, p1, p1.subtract(off), p0.subtract(off), DEEP_GOLD, a, a);
                quad(gl, c.m, p0.add(off.multiply(0.5)), p1.add(off.multiply(0.5)), p1, p0, WHITE, a * 0.6f, a * 0.6f);
            }
        }
    }

    // ================================================================ R. 성검강림: 거대한 성검
    public static final class GiantSword extends Fx {
        public static final int LAND = 24;
        final int caster;
        final Vec3d at;
        final float yaw;
        boolean landed;

        GiantSword(ClientWorld w, int caster, Vec3d at, int dur) {
            super(dur + LAND);
            this.caster = caster;
            this.at = at;
            Entity e = w.getEntityById(caster);
            this.yaw = e instanceof LivingEntity le ? le.bodyYaw : 0;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age < LAND) {
                for (int i = 0; i < 4; i++) {
                    Vec3d sp = at.add(rf(-3, 3), rf(8, 18), rf(-3, 3));
                    Particles.P pp = Particles.spawn(Kind.STREAK, sp, new Vec3d(0, -0.5, 0), rf(10, 16), rf(0.05f, 0.1f));
                    pp.color(i % 2 == 0 ? GOLD : WHITE, 1f);
                }
            }
            if (age == LAND && !landed) {
                landed = true;
                VfxManager.add(new Effects.Flash(at.add(0, 3, 0), 12f, WHITE, 18, Tex.FLARE));
                VfxManager.add(new Cross(w, at, 7));
                VfxManager.add(new Pillar(w, at, 3f));
                BlockState st = Effects.groundState(w, at);
                if (st != null) for (int i = 0; i < 40; i++) {
                    double a = R.nextDouble() * Math.PI * 2, rr = rf(0.5f, 5f);
                    Debris.spawn(st, at.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr), new Vec3d(Math.cos(a) * rf(0.05f, 0.25f), rf(0.3f, 0.7f), Math.sin(a) * rf(0.05f, 0.25f)), rf(0.15f, 0.35f), rf(22, 36), null, 0, true);
                }
                Effects.sparks(at.add(0, 1, 0), 60, 0.9f, GOLD, 0.1f);
                CameraController.addShake(0.8f, 16);
            }
            if (age > LAND && age % 4 == 0) {
                Particles.P pp = Particles.spawn(Kind.GLOW, at.add(rf(-0.6f, 0.6f), rf(0.5f, 10f), rf(-0.6f, 0.6f)), new Vec3d(0, 0.04, 0), 26, 0.08f);
                pp.color(GOLD, 0.9f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float fade = Math.min(1, (life - a) / 20f) * Math.min(1, a / 4f);
            float drop = a < LAND ? 30f * (float) Math.pow(1 - a / LAND, 2.4) : 0f;
            float wob = a >= LAND ? 0.3f * (float) Math.exp(-(a - LAND) * 0.25f) * MathHelper.sin((a - LAND) * 1.7f) : 0;
            Vec3d rp = at.subtract(c.cam);
            MatrixStack ms = c.matrices;
            ms.push();
            ms.translate(rp.x, rp.y + drop - 2.5 + wob, rp.z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            ms.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(90)); // 칼 -Z → 아래 (-Y): 칼끝이 땅을 향함
            ms.translate(0, 0, 13.5f);
            PaladinParts.renderSword(ms, c.imm, 13.5f, 1.6f, 0.6f + 0.4f * fade);
            ms.pop();
            // 박힌 자리: 빛 고리 + 하늘로 이어진 빛
            if (a >= LAND) {
                VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
                ring(gl, c.m, rp.add(0, 0.05, 0), 6f, 0.2f, GOLD, 0.6f * fade, 96);
                beam(c, rp, 22f, 1.2f, WHITE, 0.25f * fade);
            }
        }
    }

    // ================================================================ T. 성스러운 오라
    static void aura(ClientWorld w, int id, boolean on, boolean quiet) {
        if (on) AURA.put(id, w.getTime());
        else AURA.remove(id);
        if (quiet) return;
        Entity e = w.getEntityById(id);
        if (e == null) return;
        VfxManager.add(new Pillar(w, e.getPos(), on ? 1.2f : 0.6f));
        Effects.sparks(e.getPos().add(0, 1.2, 0), on ? 20 : 8, 0.3f, GOLD, 0.06f);
    }

    static boolean isPaladinPlayer(Entity e) {
        return e instanceof PlayerEntity p && PaladinParts.isPaladin(p);
    }

    private PaladinFx() {
    }
}
