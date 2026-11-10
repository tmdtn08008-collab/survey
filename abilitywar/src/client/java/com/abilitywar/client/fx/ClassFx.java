package com.abilitywar.client.fx;

import com.abilitywar.client.camera.CameraController;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.abilitywar.client.fx.Effects.R;
import static com.abilitywar.client.fx.Effects.randDir;
import static com.abilitywar.client.fx.Effects.rf;
import static com.abilitywar.client.fx.Particles.Kind;

/**
 * 1차 직업 (전사·마법사·탱커·힐러) 과 수호기사 이펙트.
 * basic_ring / basic_bolt / basic_bubble / basic_slash / basic_blink / basic_taunted,
 * guardian_shout / rush / dome / slam / ult / stance / intercept.
 */
public final class ClassFx {
    public static final int GOLD = 0xF0C860;
    public static final int STEEL = 0x8FB8E8;
    public static final int PALE_GOLD = 0xFFF2C8;
    public static final int TAUNT = 0xFF4A3A;

    /** 수호 태세인 플레이어 → 마지막으로 알림 받은 시각. */
    static final Map<Integer, Long> STANCE = new HashMap<>();
    /** 도발당한 대상 → [끝나는 시각, 도발한 사람]. */
    static final Map<Integer, long[]> TAUNTED = new HashMap<>();

    public static boolean inStance(Entity e) {
        return STANCE.containsKey(e.getId());
    }

    public static boolean spawn(String type, NbtCompound n, ClientWorld w, Vec3d p) {
        switch (type) {
            case "basic_ring" -> ring(w, n.getInt("c"), p, n.getFloat("r"), n.getInt("col") & 0xFFFFFF, n.getInt("k"));
            case "basic_bolt" -> VfxManager.add(new Bolt(w, n));
            case "basic_bubble" -> VfxManager.add(new Bubble(n.getInt("e"), n.getInt("d"), n.getInt("col") & 0xFFFFFF));
            case "basic_slash" -> VfxManager.add(new Slash(w, n.getInt("c"), n.getInt("k"), n.getInt("col") & 0xFFFFFF, n.getBoolean("full")));
            case "basic_blink" -> blink(w, new Vec3d(n.getDouble("fx"), n.getDouble("fy"), n.getDouble("fz")), p, n.getInt("col") & 0xFFFFFF);
            case "basic_taunted" -> taunted(w, n.getInt("e"), n.getInt("c"));
            case "guardian_shout" -> shout(w, n.getInt("c"), p, n.getFloat("r"));
            case "guardian_rush" -> VfxManager.add(new Rush(n.getInt("c"), new Vec3d(n.getFloat("dx"), 0, n.getFloat("dz")), n.getInt("d")));
            case "guardian_dome" -> VfxManager.add(new Dome(w, p, n.getFloat("r"), n.getInt("d"), n.getBoolean("u")));
            case "guardian_slam" -> slam(w, p, n.getFloat("r"));
            case "guardian_ult" -> VfxManager.add(new UltShield(w, n.getInt("c"), p, n.getInt("d")));
            case "guardian_stance" -> stance(w, n.getInt("c"), n.getBoolean("on"), n.getBoolean("q"));
            case "guardian_intercept" -> VfxManager.add(new Intercept(n.getInt("a"), n.getInt("b")));
            case "level_up" -> levelUp(w, n.getInt("e"), n.getInt("l"));
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void tick(ClientWorld w) {
        long now = w.getTime();
        STANCE.entrySet().removeIf(en -> now - en.getValue() > 70 || w.getEntityById(en.getKey()) == null);
        for (Iterator<Map.Entry<Integer, long[]>> it = TAUNTED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, long[]> en = it.next();
            Entity e = w.getEntityById(en.getKey());
            if (e == null || !e.isAlive() || en.getValue()[0] < now) it.remove();
        }
        // 수호 태세: 발밑의 금빛 문장 + 떠오르는 빛
        for (Map.Entry<Integer, Long> en : STANCE.entrySet()) {
            Entity e = w.getEntityById(en.getKey());
            if (e == null || e.isInvisible()) continue;
            if ((now + e.getId()) % 4 == 0) {
                double a = R.nextDouble() * Math.PI * 2;
                Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(Math.cos(a) * 0.7, 0.1, Math.sin(a) * 0.7), new Vec3d(0, rf(0.03f, 0.06f), 0), rf(16, 24), rf(0.05f, 0.08f));
                pp.color(GOLD, 0.9f);
            }
        }
    }

    public static void clear() {
        STANCE.clear();
        TAUNTED.clear();
    }

    public static void render(Fx.Ctx c) {
        if (STANCE.isEmpty() && TAUNTED.isEmpty()) return;
        float now = c.world.getTime() + c.tickDelta;
        VertexConsumer gl;
        // 수호 태세: 발밑 문장 + 8칸 안의 같은 팀과 잇는 금빛 끈
        for (Map.Entry<Integer, Long> en : STANCE.entrySet()) {
            Entity g = c.world.getEntityById(en.getKey());
            if (!(g instanceof PlayerEntity gp) || g.isInvisible()) continue;
            Vec3d gpos = g.getLerpedPos(c.tickDelta);
            Vec3d base = gpos.add(0, 0.04, 0).subtract(c.cam);
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
            float pulse = 0.75f + 0.25f * MathHelper.sin(now * 0.15f);
            Draw.flat(ci, c.m, base.x, base.y, base.z, 1.25f, now * 0.02f, Fx.r(GOLD), Fx.g(GOLD), Fx.b(GOLD), 0.55f * pulse);
            gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR); // 다른 렌더 레이어를 받은 뒤에는 다시 받아야 함
            ring(gl, c.m, base.add(0, 0.01, 0), 8f, 0.06f, GOLD, 0.18f * pulse, 72);
            Vec3d from = gpos.add(0, 1.1, 0);
            for (PlayerEntity o : c.world.getPlayers()) {
                if (o == g || !o.isAlive() || o.isSpectator() || !gp.isTeammate(o) || o.squaredDistanceTo(g) > 64) continue;
                Vec3d to = o.getLerpedPos(c.tickDelta).add(0, 1.0, 0);
                tether(gl, c, from, to, 0.035f, GOLD, 0.45f * pulse, now);
            }
        }
        // 도발당한 대상: 머리 위 붉은 느낌표
        long tnow = c.world.getTime();
        gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        for (Map.Entry<Integer, long[]> en : TAUNTED.entrySet()) {
            Entity e = c.world.getEntityById(en.getKey());
            if (e == null || Effects.isLocalFp(e)) continue;
            float left = en.getValue()[0] - tnow - c.tickDelta;
            float a = MathHelper.clamp(left / 10f, 0, 1) * MathHelper.clamp((60 - left) / 4f, 0, 1);
            float pop = 1 + 0.6f * Math.max(0, 1 - (60 - left) / 6f);
            Vec3d at = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() + 0.55 + 0.05 * MathHelper.sin(now * 0.3f), 0).subtract(c.cam);
            bang(gl, c.m, c.camRot, at, 0.16f * pop, TAUNT, a);
        }
    }

    // ================================================================ 공용 그리기
    /** 가산 단색 고리 (수평, 반지름 rad, 두께 w). */
    static void ring(VertexConsumer vc, Matrix4f m, Vec3d c, float rad, float w, int col, float alpha, int seg) {
        float cr = Fx.r(col), cg = Fx.g(col), cb = Fx.b(col);
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

    /** 세로 벽 고리 (원통 옆면): 바닥 반지름 rad, 높이 h, 위로 갈수록 투명. */
    static void wall(VertexConsumer vc, Matrix4f m, Vec3d c, float rad, float h, int col, float alpha, int seg) {
        float cr = Fx.r(col), cg = Fx.g(col), cb = Fx.b(col);
        for (int i = 0; i < seg; i++) {
            double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) (c.x + Math.cos(t0) * rad), z0 = (float) (c.z + Math.sin(t0) * rad);
            float x1 = (float) (c.x + Math.cos(t1) * rad), z1 = (float) (c.z + Math.sin(t1) * rad);
            Draw.vc(vc, m, x0, (float) c.y, z0, cr, cg, cb, alpha);
            Draw.vc(vc, m, x0, (float) c.y + h, z0, cr, cg, cb, 0);
            Draw.vc(vc, m, x1, (float) c.y + h, z1, cr, cg, cb, 0);
            Draw.vc(vc, m, x1, (float) c.y, z1, cr, cg, cb, alpha);
        }
    }

    /** 카메라를 향한 단색 사각형 (가로 hw, 세로 hh). */
    static void card(VertexConsumer vc, Matrix4f m, Quaternionf rot, Vec3d c, float hw, float hh, int col, float a) {
        Vector3f ax = new Vector3f(hw, 0, 0).rotate(rot), ay = new Vector3f(0, hh, 0).rotate(rot);
        float cr = Fx.r(col), cg = Fx.g(col), cb = Fx.b(col);
        float x = (float) c.x, y = (float) c.y, z = (float) c.z;
        Draw.vc(vc, m, x - ax.x - ay.x, y - ax.y - ay.y, z - ax.z - ay.z, cr, cg, cb, a);
        Draw.vc(vc, m, x - ax.x + ay.x, y - ax.y + ay.y, z - ax.z + ay.z, cr, cg, cb, a);
        Draw.vc(vc, m, x + ax.x + ay.x, y + ax.y + ay.y, z + ax.z + ay.z, cr, cg, cb, a);
        Draw.vc(vc, m, x + ax.x - ay.x, y + ax.y - ay.y, z + ax.z - ay.z, cr, cg, cb, a);
    }

    /** 느낌표 (도발 표시). */
    static void bang(VertexConsumer vc, Matrix4f m, Quaternionf rot, Vec3d c, float s, int col, float a) {
        Vector3f up = new Vector3f(0, 1, 0).rotate(rot);
        Vec3d u = new Vec3d(up.x, up.y, up.z);
        // 번짐
        card(vc, m, rot, c.add(u.multiply(s * 0.4)), s * 1.6f, s * 2.6f, col, a * 0.18f);
        card(vc, m, rot, c.add(u.multiply(s * 0.9)), s * 0.32f, s * 1.1f, col, a);
        card(vc, m, rot, c.add(u.multiply(-s * 0.95)), s * 0.34f, s * 0.34f, col, a);
        card(vc, m, rot, c.add(u.multiply(s * 0.9)), s * 0.14f, s * 0.9f, 0xFFFFFF, a * 0.6f);
    }

    /** 살짝 처지며 흐르는 빛 끈. */
    static void tether(VertexConsumer vc, Fx.Ctx c, Vec3d a, Vec3d b, float width, int col, float alpha, float now) {
        int seg = 12;
        Vec3d prev = null;
        float cr = Fx.r(col), cg = Fx.g(col), cb = Fx.b(col);
        for (int i = 0; i <= seg; i++) {
            float t = (float) i / seg;
            double sag = Math.sin(t * Math.PI) * 0.35;
            Vec3d p = a.lerp(b, t).add(0, -sag, 0).subtract(c.cam);
            if (prev != null) {
                Vec3d d = p.subtract(prev);
                Vec3d side = d.crossProduct(prev.multiply(-1));
                if (side.lengthSquared() < 1e-10) side = new Vec3d(0, 1, 0);
                side = side.normalize().multiply(width);
                float flow = 0.55f + 0.45f * MathHelper.sin(now * 0.4f - t * 9f);
                float al = alpha * flow;
                Draw.vc(vc, c.m, (float) (prev.x - side.x), (float) (prev.y - side.y), (float) (prev.z - side.z), cr, cg, cb, al);
                Draw.vc(vc, c.m, (float) (prev.x + side.x), (float) (prev.y + side.y), (float) (prev.z + side.z), cr, cg, cb, al);
                Draw.vc(vc, c.m, (float) (p.x + side.x), (float) (p.y + side.y), (float) (p.z + side.z), cr, cg, cb, al);
                Draw.vc(vc, c.m, (float) (p.x - side.x), (float) (p.y - side.y), (float) (p.z - side.z), cr, cg, cb, al);
            }
            prev = p;
        }
    }

    static Vec3d fwd(Entity e) {
        float yaw = (e instanceof LivingEntity le ? le.bodyYaw : e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    // ================================================================ 레벨 업 / 전직
    static void levelUp(ClientWorld w, int id, int lv) {
        Entity e = w.getEntityById(id);
        if (e == null) return;
        boolean job = lv < 0;
        if (e == MinecraftClient.getInstance().player) com.abilitywar.client.rpg.RpgClient.levelUpFx(lv);
        VfxManager.add(new LevelUp(id, job));
        Vec3d at = e.getPos();
        for (int i = 0; i < (job ? 60 : 30); i++) {
            double a = R.nextDouble() * Math.PI * 2, rr = rf(0.3f, job ? 1.6f : 1.0f);
            Particles.P pp = Particles.spawn(i % 4 == 0 ? Kind.STREAK : Kind.GLOW, at.add(Math.cos(a) * rr, rf(0f, 0.4f), Math.sin(a) * rr), new Vec3d(0, rf(0.1f, job ? 0.35f : 0.22f), 0), rf(16, 30), rf(0.05f, 0.1f));
            pp.color(i % 3 == 0 ? 0xFFFFFF : GOLD, 1f);
            pp.drag = 0.94f;
        }
        if (job) {
            VfxManager.add(new Effects.Flash(at.add(0, 1.2, 0), 5f, PALE_GOLD, 14, Tex.FLARE));
            VfxManager.add(new Ring(w, at, 4f, GOLD, 3));
        }
        if (lv == -2) {
            // 3차 전직: 하늘까지 솟는 금빛 기둥 + 큰 고리 두 겹 + 화면 흔들림
            VfxManager.add(new Ring(w, at, 8f, GOLD, 0));
            VfxManager.add(new Ring(w, at, 5f, Effects.abilityColor(e), 3));
            VfxManager.add(new Effects.Flash(at.add(0, 2, 0), 9f, 0xFFF6DC, 18, Tex.FLARE));
            for (int i = 0; i < 40; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                Particles.P pp = Particles.spawn(Kind.STREAK, at.add(Math.cos(a) * 0.6, 0.2, Math.sin(a) * 0.6), new Vec3d(Math.cos(a) * 0.05, rf(0.5f, 0.9f), Math.sin(a) * 0.05), rf(14, 22), 0.09f);
                pp.color(i % 2 == 0 ? GOLD : 0xFFFFFF, 1f);
                pp.drag = 0.97f;
            }
            CameraController.addShake(0.4f, 12);
        }
    }

    /** 몸을 감싸며 올라가는 금빛 고리 + 빛기둥. */
    static final class LevelUp extends Fx {
        final int id;
        final boolean job;

        LevelUp(int id, boolean job) {
            super(job ? 60 : 34);
            this.id = id;
            this.job = job;
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null) return;
            float a = age + c.tickDelta;
            float fade = Math.min(1, (life - a) / 10f);
            Vec3d base = e.getLerpedPos(c.tickDelta).subtract(c.cam);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            // 1인칭 본인: 카메라가 기둥 안에 들어가므로 바닥 고리만 은은하게
            if (Effects.isLocalFp(e)) {
                for (int j = 0; j < 2; j++) {
                    float t = MathHelper.clamp((a - j * 5) / 16f, 0, 1);
                    if (t <= 0 || t >= 1) continue;
                    ring(gl, c.m, base.add(0, 0.05, 0), 0.9f + 2.2f * t, 0.12f, j == 0 ? GOLD : 0xFFFFFF, (1 - t) * 0.6f, 48);
                }
                return;
            }
            int rings = job ? 5 : 3;
            for (int j = 0; j < rings; j++) {
                float t = MathHelper.clamp((a - j * 4) / 18f, 0, 1);
                if (t <= 0 || t >= 1) continue;
                float y = t * (job ? 3.2f : 2.4f);
                ring(gl, c.m, base.add(0, y, 0), 0.8f - 0.25f * t, 0.08f, j % 2 == 0 ? GOLD : 0xFFFFFF, (1 - t) * 0.9f, 40);
            }
            wall(gl, c.m, base.add(0, 0.02, 0), 0.75f, (job ? 3.5f : 2.2f) * Math.min(1, a / 6f), GOLD, 0.35f * fade, 40);
            if (!Effects.isLocalFp(e)) {
                VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
                Draw.flat(ci, c.m, base.x, base.y + 0.03, base.z, (job ? 2.2f : 1.3f) * Effects.outBack(Math.min(1, a / 6f)), a * 0.05f, r(GOLD), g(GOLD), b(GOLD), 0.7f * fade);
            }
        }
    }

    // ================================================================ 1차 직업: 고리 (함성/서리/치유/축복/도발)
    static void ring(ClientWorld w, int caster, Vec3d p, float r, int col, int k) {
        VfxManager.add(new Ring(w, p, r, col, k));
        Vec3d chest = p.add(0, 1.1, 0);
        switch (k) {
            case 0 -> { // 함성: 바깥으로 튀는 불티 + 먼지
                for (int i = 0; i < 26; i++) {
                    double a = i * Math.PI * 2 / 26;
                    Particles.P pp = Particles.spawn(Kind.STREAK, chest.add(Math.cos(a) * 0.5, rf(-0.3f, 0.3f), Math.sin(a) * 0.5), new Vec3d(Math.cos(a) * 0.45, rf(-0.02f, 0.06f), Math.sin(a) * 0.45), rf(8, 12), rf(0.05f, 0.09f));
                    pp.color(i % 3 == 0 ? 0xFFF0C0 : col, 1f);
                    pp.drag = 0.86f;
                }
                dust(w, p, r * 0.6f, 14);
                CameraController.addShake(0.15f, 5);
            }
            case 1 -> { // 서리: 얼음 조각이 튀고 바닥에 서리가 퍼짐
                for (int i = 0; i < 30; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double rr = rf(0.3f, r);
                    Particles.P pp = Particles.spawn(i % 2 == 0 ? Kind.SPARK : Kind.GLOW, p.add(Math.cos(a) * rr, rf(0.1f, 0.6f), Math.sin(a) * rr), new Vec3d(Math.cos(a) * 0.05, rf(0.08f, 0.2f), Math.sin(a) * 0.05), rf(14, 26), rf(0.05f, 0.1f));
                    pp.color(i % 3 == 0 ? 0xFFFFFF : col, 1f);
                    pp.grav = 0.012f;
                }
                for (int i = 0; i < 18; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    Particles.spawn(Kind.SMOKE_ADD, p.add(Math.cos(a) * r * 0.5, 0.2, Math.sin(a) * r * 0.5), new Vec3d(Math.cos(a) * 0.12, 0.01, Math.sin(a) * 0.12), rf(18, 28), rf(0.5f, 0.9f)).color(0xD8F4FF, 0.3f);
                }
            }
            case 2, 3 -> { // 치유 / 축복: 떠오르는 빛
                for (int i = 0; i < 24; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double rr = rf(0.2f, r);
                    Particles.P pp = Particles.spawn(Kind.GLOW, p.add(Math.cos(a) * rr, rf(0.1f, 0.4f), Math.sin(a) * rr), new Vec3d(0, rf(0.05f, 0.12f), 0), rf(18, 30), rf(0.06f, 0.11f));
                    pp.color(i % 3 == 0 ? 0xFFFFFF : col, 1f);
                    pp.drag = 0.95f;
                }
            }
            default -> { // 도발: 붉은 충격파
                for (int i = 0; i < 18; i++) {
                    double a = i * Math.PI * 2 / 18;
                    Particles.P pp = Particles.spawn(Kind.STREAK, chest.add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4), new Vec3d(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5), 10, 0.08f);
                    pp.color(TAUNT, 1f);
                    pp.drag = 0.85f;
                }
                CameraController.addShake(0.12f, 4);
            }
        }
    }

    static void dust(ClientWorld w, Vec3d p, float r, int n) {
        for (int i = 0; i < n; i++) {
            double a = R.nextDouble() * Math.PI * 2;
            Particles.spawn(Kind.SMOKE_DARK, p.add(Math.cos(a) * r * 0.3, 0.15, Math.sin(a) * r * 0.3), new Vec3d(Math.cos(a) * 0.2, rf(0.01f, 0.05f), Math.sin(a) * 0.2), rf(16, 26), rf(0.4f, 0.8f)).color(0x6A5E50, 0.35f);
        }
    }

    static final class Ring extends Fx {
        final Vec3d p;
        final float rad;
        final int col, k;

        Ring(ClientWorld w, Vec3d at, float rad, int col, int k) {
            super(k == 1 ? 46 : 24);
            Vec3d g = Effects.ground(w, at.add(0, 0.5, 0), 3);
            this.p = (g != null ? g : at).add(0, 0.03, 0);
            this.rad = rad;
            this.col = col;
            this.k = k;
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float t = a / life;
            Vec3d base = p.subtract(c.cam);
            float fade = Math.min(1, (life - a) / 8f);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            float e = 1 - (1 - Math.min(1, a / 10f)) * (1 - Math.min(1, a / 10f));
            Draw.flat(sw, c.m, base.x, base.y + 0.01, base.z, rad * 1.1f * e, 0, r(col), g(col), b(col), (1 - Math.min(1, a / 12f)) * 0.9f);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            switch (k) {
                case 1 -> {
                    // 서리 문양이 바닥에 남았다가 녹음
                    VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(Tex.CRACK));
                    Draw.flat(ci, c.m, base.x, base.y + 0.02, base.z, rad * Math.min(1, a / 5f), 0.6f, r(col), g(col), b(col), 0.7f * fade);
                    gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
                    ring(gl, c.m, base.add(0, 0.03, 0), rad * Math.min(1, a / 5f), 0.12f, 0xE8FBFF, 0.6f * fade, 64);
                    wall(gl, c.m, base, rad * Math.min(1, a / 5f), 0.9f * fade, col, 0.35f * fade, 64);
                }
                case 2, 3 -> {
                    VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
                    float grow = Effects.outBack(Math.min(1, a / 7f));
                    Draw.flat(ci, c.m, base.x, base.y + 0.02, base.z, rad * 0.9f * grow, a * 0.04f, r(col), g(col), b(col), 0.75f * fade);
                    gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
                    for (int j = 0; j < 3; j++) {
                        float tt = MathHelper.clamp((a - j * 3) / 14f, 0, 1);
                        if (tt <= 0 || tt >= 1) continue;
                        ring(gl, c.m, base.add(0, 0.2 + tt * 2.2, 0), rad * (0.85f - 0.3f * tt), 0.16f, j % 2 == 0 ? col : 0xFFFFFF, (1 - tt) * 0.7f, 56);
                    }
                }
                case 4 -> {
                    for (int j = 0; j < 3; j++) {
                        float tt = MathHelper.clamp((a - j * 4) / 12f, 0, 1);
                        if (tt <= 0 || tt >= 1) continue;
                        float ee = 1 - (1 - tt) * (1 - tt);
                        ring(gl, c.m, base.add(0, 0.05, 0), rad * ee, 0.2f, TAUNT, (1 - tt) * 0.8f, 72);
                        wall(gl, c.m, base, rad * ee, 0.6f, TAUNT, (1 - tt) * 0.35f, 72);
                    }
                }
                default -> {
                    for (int j = 0; j < 2; j++) {
                        float tt = MathHelper.clamp((a - j * 3) / 10f, 0, 1);
                        if (tt <= 0 || tt >= 1) continue;
                        float ee = 1 - (1 - tt) * (1 - tt);
                        ring(gl, c.m, base.add(0, 0.9, 0), rad * ee, 0.25f, col, (1 - tt) * 0.6f, 64);
                        wall(gl, c.m, base, rad * ee, 1.4f * (1 - tt), col, (1 - tt) * 0.4f, 64);
                    }
                }
            }
        }
    }

    // ================================================================ 마법 구체 (마법사 평타·화염구, 힐러 평타)
    static final class Bolt extends Fx {
        final int caster, target, col, travel;
        final float size;
        final Vec3d end;
        Vec3d start, cur, prev;
        final Vec3d[] trail = new Vec3d[10];
        int trailN;
        final boolean fire;

        Bolt(ClientWorld w, NbtCompound n) {
            super(n.getInt("t") + 1);
            caster = n.getInt("c");
            target = n.getInt("e");
            col = n.getInt("col") & 0xFFFFFF;
            travel = Math.max(1, n.getInt("t"));
            size = n.getFloat("s");
            end = new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz"));
            fire = ((col >> 16) & 255) > 200 && ((col >> 8) & 255) < 160 && (col & 255) < 100;
            Entity e = w.getEntityById(caster);
            start = e != null ? HandTracker.hand(e, 0, 1f) : end;
            cur = start;
        }

        Vec3d target(ClientWorld w) {
            Entity e = target >= 0 ? w.getEntityById(target) : null;
            return e != null ? e.getPos().add(0, e.getHeight() * 0.55, 0) : end;
        }

        @Override
        public boolean tick(ClientWorld w) {
            prev = cur;
            float k = Math.min(1, (age + 1f) / travel);
            Vec3d tgt = target(w);
            Vec3d mid = start.lerp(tgt, 0.5).add(0, 0.25 * start.distanceTo(tgt) * 0.08, 0);
            cur = start.lerp(mid, k).lerp(mid.lerp(tgt, k), k);
            System.arraycopy(trail, 0, trail, 1, trail.length - 1);
            trail[0] = cur;
            trailN = Math.min(trail.length, trailN + 1);
            for (int i = 0; i < (fire ? 3 : 2); i++) {
                Particles.P pp = Particles.spawn(fire ? Kind.FLAME : Kind.GLOW, cur.add(rf(-0.08f, 0.08f) * size, rf(-0.08f, 0.08f) * size, rf(-0.08f, 0.08f) * size), randDir().multiply(0.02), rf(8, 14), rf(0.08f, 0.14f) * size);
                pp.color(i == 0 ? 0xFFFFFF : col, 1f);
            }
            if (age + 1 >= travel) {
                VfxManager.add(new Effects.Flash(cur, 1.4f * size, col, 8, Tex.FLARE));
                VfxManager.add(new Effects.Impact(cur, "punch", 0.6f * size, col));
                Effects.sparks(cur, (int) (10 * size), 0.35f, col, 0.07f);
                if (fire) for (int i = 0; i < 16; i++)
                    Particles.spawn(Kind.FLAME, cur, randDir().multiply(rf(0.05f, 0.2f)), rf(10, 18), rf(0.15f, 0.3f)).color(i % 2 == 0 ? 0xFFD070 : col, 1f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            if (cur == null) return;
            Vec3d p = prev == null ? cur : prev.lerp(cur, c.tickDelta);
            Vec3d rp = p.subtract(c.cam);
            float pulse = 0.9f + 0.1f * MathHelper.sin((age + c.tickDelta) * 1.3f);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(gl, c.m, c.camRot, rp.x, rp.y, rp.z, 0.55f * size * pulse, 0, r(col), g(col), b(col), 0.9f * nearFade(rp, 0.5f));
            Draw.billboard(gl, c.m, c.camRot, rp.x, rp.y, rp.z, 0.2f * size, 0, 1, 1, 1, nearFade(rp, 0.3f));
            VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Draw.billboard(fl, c.m, c.camRot, rp.x, rp.y, rp.z, 0.4f * size, (age + c.tickDelta) * 0.3f, r(col), g(col), b(col), 0.7f * nearFade(rp, 0.5f));
            if (trailN >= 2) {
                Vec3d[] pts = new Vec3d[trailN];
                float[] wd = new float[trailN], al = new float[trailN];
                for (int i = 0; i < trailN; i++) {
                    pts[i] = (i == 0 ? p : trail[i]).subtract(c.cam);
                    float k = 1 - (float) i / trailN;
                    wd[i] = 0.16f * size * k;
                    al[i] = 0.7f * k;
                }
                VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
                Draw.ribbon(st, c.m, pts, trailN, wd, r(col), g(col), b(col), al, 1f, 0);
            }
        }
    }

    // ================================================================ 보호막 (마나 방패 등)
    static final class Bubble extends Fx {
        final int id, col;

        Bubble(int id, int dur, int col) {
            super(Math.max(10, dur));
            this.id = id;
            this.col = col;
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(id);
            if (e == null || !e.isAlive()) return true;
            if (age % 5 == 0) {
                Vec3d d = randDir();
                Particles.P pp = Particles.spawn(Kind.GLOW, e.getPos().add(0, 1, 0).add(d.multiply(1.05)), d.multiply(0.01), 14, 0.05f);
                pp.color(col, 0.9f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(id);
            if (e == null || Effects.isLocalFp(e)) return;
            float a = age + c.tickDelta;
            float in = Effects.outBack(Math.min(1, a / 6f));
            float fade = Math.min(1, (life - a) / 8f);
            Vec3d ctr = e.getLerpedPos(c.tickDelta).add(0, e.getHeight() * 0.5, 0).subtract(c.cam);
            float rad = (e.getHeight() * 0.62f + 0.15f) * in;
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            Draw.sphereColor(gl, c.m, ctr, rad, 10, 16, r(col), g(col), b(col), 0.07f * fade);
            // 도는 고리 두 개
            for (int j = 0; j < 2; j++) {
                float ang = a * (j == 0 ? 0.08f : -0.06f) + j * 1.2f;
                Vec3d ax = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
                Vec3d ay = new Vec3d(0, Math.cos(0.6 + j), Math.sin(0.6 + j)).normalize();
                Vec3d n = ax.crossProduct(ay).normalize();
                ay = n.crossProduct(ax).normalize();
                tiltRing(gl, c.m, ctr, ax, ay, rad * 1.02f, 0.05f, col, 0.55f * fade, 48);
            }
            VertexConsumer rs = c.imm.getBuffer(AWRenderLayers.additive(Tex.RUNE_SHIELD));
            Draw.billboard(rs, c.m, c.camRot, ctr.x, ctr.y, ctr.z, rad * 1.1f, a * 0.02f, r(col), g(col), b(col), 0.18f * fade * nearFade(ctr, rad));
        }
    }

    static void tiltRing(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d ax, Vec3d ay, float rad, float w, int col, float alpha, int seg) {
        float cr = Fx.r(col), cg = Fx.g(col), cb = Fx.b(col);
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

    // ================================================================ 베기 (전사 평타·회전베기)
    static final class Slash extends Fx {
        final int caster, combo, col;
        final boolean full;
        final Vec3d origin, fwd;

        Slash(ClientWorld w, int caster, int combo, int col, boolean full) {
            super(full ? 9 : 7);
            this.caster = caster;
            this.combo = combo;
            this.col = col;
            this.full = full;
            Entity e = w.getEntityById(caster);
            origin = e != null ? e.getPos().add(0, full ? 0.9 : 1.15, 0) : Vec3d.ZERO;
            fwd = e != null ? (full ? ClassFx.fwd(e) : e.getRotationVec(1f)) : new Vec3d(0, 0, 1);
            if (full && e != null) {
                for (int i = 0; i < 20; i++) {
                    double a = i * Math.PI * 2 / 20;
                    Particles.P pp = Particles.spawn(Kind.STREAK, origin.add(Math.cos(a) * 2.6, rf(-0.2f, 0.3f), Math.sin(a) * 2.6), new Vec3d(-Math.sin(a) * 0.3, 0, Math.cos(a) * 0.3), rf(6, 10), 0.07f);
                    pp.color(i % 2 == 0 ? 0xFFFFFF : col, 1f);
                }
                dust(w, e.getPos(), 3, 10);
            }
        }

        @Override
        public void render(Ctx c) {
            if (origin == Vec3d.ZERO) return;
            Entity e = c.world.getEntityById(caster);
            if (e != null && Effects.isLocalFp(e) && !full) return; // 1인칭 베기는 FpEffects 가 그림
            float a = age + c.tickDelta;
            float sweep = Math.min(1, a / (full ? 5f : 3.5f));
            float fade = Math.min(1, (life - a) / 4f);
            Vec3d o = origin.subtract(c.cam);
            Vec3d f = fwd.normalize();
            Vec3d right = f.crossProduct(new Vec3d(0, 1, 0));
            if (right.lengthSquared() < 1e-4) right = new Vec3d(1, 0, 0);
            right = right.normalize();
            Vec3d up = right.crossProduct(f).normalize();
            // 베는 면: 콤보마다 기울기 다르게 (0: 오른→왼, 1: 왼→오른, 2: 위→아래)
            Vec3d ax, ay;
            double from, to;
            if (full) {
                ax = new Vec3d(1, 0, 0);
                ay = new Vec3d(0, 0, 1);
                double base = Math.atan2(f.z, f.x);
                from = base;
                to = base + Math.PI * 2;
            } else if (combo == 2) {
                ax = f;
                ay = up;
                from = Math.toRadians(100);
                to = Math.toRadians(-40);
            } else {
                double tilt = Math.toRadians(combo == 0 ? 20 : -20);
                ax = f;
                ay = right.multiply(Math.cos(tilt)).add(up.multiply(Math.sin(tilt)));
                from = Math.toRadians(combo == 0 ? 70 : -70);
                to = -from;
            }
            float r0 = full ? 1.0f : 0.9f, r1 = full ? 3.2f : 2.6f;
            int seg = full ? 40 : 18;
            double head = from + (to - from) * sweep;
            double tailLen = (to - from) * (full ? 0.6 : 0.75);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            float cr = r(col), cg = g(col), cb = b(col);
            for (int i = 0; i < seg; i++) {
                double t0 = (double) i / seg, t1 = (double) (i + 1) / seg;
                double a0 = head - tailLen * t0, a1 = head - tailLen * t1;
                if ((to > from && (a0 < from || a1 < from)) || (to < from && (a0 > from || a1 > from))) continue;
                float al0 = (float) (1 - t0) * 0.85f * fade, al1 = (float) (1 - t1) * 0.85f * fade;
                Vec3d d0 = ax.multiply(Math.cos(a0)).add(ay.multiply(Math.sin(a0))), d1 = ax.multiply(Math.cos(a1)).add(ay.multiply(Math.sin(a1)));
                Vec3d p00 = o.add(d0.multiply(r0)), p01 = o.add(d0.multiply(r1)), p11 = o.add(d1.multiply(r1)), p10 = o.add(d1.multiply(r0));
                Draw.vc(gl, c.m, (float) p00.x, (float) p00.y, (float) p00.z, cr, cg, cb, 0);
                Draw.vc(gl, c.m, (float) p01.x, (float) p01.y, (float) p01.z, 1, 1, 1, al0);
                Draw.vc(gl, c.m, (float) p11.x, (float) p11.y, (float) p11.z, 1, 1, 1, al1);
                Draw.vc(gl, c.m, (float) p10.x, (float) p10.y, (float) p10.z, cr, cg, cb, 0);
                // 바깥 번짐
                Vec3d q01 = o.add(d0.multiply(r1 + 0.35)), q11 = o.add(d1.multiply(r1 + 0.35));
                Draw.vc(gl, c.m, (float) p01.x, (float) p01.y, (float) p01.z, cr, cg, cb, al0 * 0.8f);
                Draw.vc(gl, c.m, (float) q01.x, (float) q01.y, (float) q01.z, cr, cg, cb, 0);
                Draw.vc(gl, c.m, (float) q11.x, (float) q11.y, (float) q11.z, cr, cg, cb, 0);
                Draw.vc(gl, c.m, (float) p11.x, (float) p11.y, (float) p11.z, cr, cg, cb, al1 * 0.8f);
            }
        }
    }

    // ================================================================ 순간이동 (마법사)
    static void blink(ClientWorld w, Vec3d from, Vec3d to, int col) {
        Vec3d a = from.add(0, 1, 0), b = to.add(0, 1, 0);
        VfxManager.add(new Effects.Flash(a, 2.2f, col, 9, Tex.FLARE));
        VfxManager.add(new Effects.Flash(b, 2.6f, col, 10, Tex.FLARE));
        VfxManager.add(new Effects.Flash(b, 2.0f, 0xFFFFFF, 6, Tex.RING_SOFT));
        int n = (int) Math.min(30, from.distanceTo(to) * 3);
        for (int i = 0; i < n; i++) {
            Vec3d p = a.lerp(b, (float) i / n).add(rf(-0.3f, 0.3f), rf(-0.6f, 0.6f), rf(-0.3f, 0.3f));
            Particles.P pp = Particles.spawn(Kind.GLOW, p, randDir().multiply(0.02), rf(10, 18), rf(0.05f, 0.09f));
            pp.color(i % 3 == 0 ? 0xFFFFFF : col, 1f);
        }
        Effects.sparks(a, 14, 0.25f, col, 0.06f);
        Effects.sparks(b, 18, 0.3f, col, 0.07f);
    }

    // ================================================================ 도발 표시
    static void taunted(ClientWorld w, int id, int caster) {
        Entity e = w.getEntityById(id);
        if (e == null) return;
        TAUNTED.put(id, new long[]{w.getTime() + 60, caster});
        Vec3d head = e.getPos().add(0, e.getHeight() + 0.3, 0);
        Effects.sparks(head, 6, 0.15f, TAUNT, 0.05f);
        Entity c = w.getEntityById(caster);
        if (c != null) VfxManager.add(new Intercept(caster, id, TAUNT, 7));
    }

    // ================================================================ 수호기사
    static void shout(ClientWorld w, int caster, Vec3d p, float r) {
        VfxManager.add(new Ring(w, p, r, GOLD, 0));
        VfxManager.add(new Ring(w, p, r * 0.6f, TAUNT, 4));
        Entity e = w.getEntityById(caster);
        Vec3d at = e != null ? e.getPos().add(fwd(e).multiply(0.5)).add(0, 1.2, 0) : p.add(0, 1.2, 0);
        VfxManager.add(new Effects.Flash(at, 3.2f, GOLD, 10, Tex.RUNE_SHIELD));
        VfxManager.add(new Effects.Flash(at, 2.4f, 0xFFFFFF, 6, Tex.FLARE));
        Effects.sparks(at, 26, 0.5f, GOLD, 0.08f);
        dust(w, p, r * 0.5f, 18);
        CameraController.addShake(0.2f, 6);
    }

    static void slam(ClientWorld w, Vec3d p, float r) {
        VfxManager.add(new Slam(w, p, r));
        BlockState st = Effects.groundState(w, p);
        if (st != null) for (int i = 0; i < 26; i++) {
            double a = R.nextDouble() * Math.PI * 2;
            double rr = rf(0.4f, r * 0.8f);
            Debris.spawn(st, p.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr), new Vec3d(Math.cos(a) * rf(0.05f, 0.2f), rf(0.25f, 0.55f), Math.sin(a) * rf(0.05f, 0.2f)), rf(0.12f, 0.3f), rf(20, 34), null, 0, true);
        }
        dust(w, p, r, 26);
        for (int i = 0; i < 30; i++) {
            double a = i * Math.PI * 2 / 30;
            Particles.P pp = Particles.spawn(Kind.STREAK, p.add(Math.cos(a) * 0.6, 0.2, Math.sin(a) * 0.6), new Vec3d(Math.cos(a) * 0.55, rf(0.05f, 0.15f), Math.sin(a) * 0.55), rf(8, 12), 0.08f);
            pp.color(i % 3 == 0 ? 0xFFFFFF : GOLD, 1f);
            pp.drag = 0.86f;
        }
        VfxManager.add(new Effects.Flash(p.add(0, 0.4, 0), 4f, GOLD, 9, Tex.FLARE));
        CameraController.hitstop(1.5f);
    }

    static final class Slam extends Fx {
        final Vec3d p;
        final float rad;
        final float[] spikes = new float[10];

        Slam(ClientWorld w, Vec3d at, float rad) {
            super(30);
            Vec3d g = Effects.ground(w, at.add(0, 0.5, 0), 3);
            this.p = (g != null ? g : at).add(0, 0.03, 0);
            this.rad = rad;
            for (int i = 0; i < spikes.length; i++) spikes[i] = rf(0, 6.28f);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            Vec3d base = p.subtract(c.cam);
            float fade = Math.min(1, (life - a) / 10f);
            VertexConsumer cr = c.imm.getBuffer(AWRenderLayers.translucent(Tex.CRACK));
            Draw.flat(cr, c.m, base.x, base.y + 0.01, base.z, rad * 1.1f * Math.min(1, a / 3f), spikes[0], 0.25f, 0.2f, 0.12f, 0.8f * fade);
            VertexConsumer cg = c.imm.getBuffer(AWRenderLayers.additive(Tex.CRACK));
            Draw.flat(cg, c.m, base.x, base.y + 0.02, base.z, rad * 1.1f * Math.min(1, a / 3f), spikes[0], r(GOLD), g(GOLD), b(GOLD), 0.8f * fade * (1 - Math.min(1, a / 30f)));
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            for (int j = 0; j < 2; j++) {
                float t = MathHelper.clamp((a - j * 3) / 10f, 0, 1);
                if (t <= 0 || t >= 1) continue;
                float e = 1 - (1 - t) * (1 - t);
                ring(gl, c.m, base.add(0, 0.05, 0), rad * 1.2f * e, 0.3f, j == 0 ? GOLD : 0xFFFFFF, (1 - t) * 0.85f, 72);
                wall(gl, c.m, base, rad * 1.2f * e, 1.6f * (1 - t), GOLD, (1 - t) * 0.5f, 72);
            }
            // 둘레에서 솟는 금빛 기둥
            float rise = MathHelper.clamp(a / 4f, 0, 1) * fade;
            VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
            for (int i = 0; i < spikes.length; i++) {
                double ang = spikes[i];
                Vec3d bot = base.add(Math.cos(ang) * rad * 0.85, 0, Math.sin(ang) * rad * 0.85);
                Draw.streak(st, c.m, bot.add(0, 2.2 * rise, 0), new Vec3d(0, 1, 0), (float) (2.2 * rise), 0.18f, r(GOLD), g(GOLD), b(GOLD), 0.8f * rise);
            }
        }
    }

    /** 방패 돌진: 앞에 빛나는 방패 벽 + 발밑 먼지. */
    static final class Rush extends Fx {
        final int caster;
        final Vec3d dir;

        Rush(int caster, Vec3d dir, int dur) {
            super(dur);
            this.caster = caster;
            this.dir = dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : dir.normalize();
            AfterImages.request(caster, 4, 2, GOLD, 10);
        }

        @Override
        public boolean tick(ClientWorld w) {
            Entity e = w.getEntityById(caster);
            if (e == null) return true;
            Vec3d at = e.getPos();
            for (int i = 0; i < 3; i++)
                Particles.spawn(Kind.SMOKE_DARK, at.add(rf(-0.4f, 0.4f), 0.1, rf(-0.4f, 0.4f)), dir.multiply(-0.1).add(0, 0.04, 0), rf(14, 22), rf(0.35f, 0.6f)).color(0x6A5E50, 0.35f);
            Vec3d front = at.add(dir.multiply(1.0)).add(0, 1.0, 0);
            Particles.P pp = Particles.spawn(Kind.STREAK, front.add(rf(-0.5f, 0.5f), rf(-0.6f, 0.6f), rf(-0.5f, 0.5f)), dir.multiply(-0.4), 6, 0.06f);
            pp.color(GOLD, 1f);
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            Entity e = c.world.getEntityById(caster);
            if (e == null) return;
            float a = age + c.tickDelta;
            float fade = Math.min(1, (life - a) / 3f) * Math.min(1, a / 2f);
            Vec3d front = e.getLerpedPos(c.tickDelta).add(dir.multiply(1.0)).add(0, 1.0, 0).subtract(c.cam);
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            VertexConsumer rs = c.imm.getBuffer(AWRenderLayers.additive(Tex.RUNE_SHIELD));
            Draw.quad(rs, c.m, front, side.multiply(1.0), new Vec3d(0, 1.0, 0), r(GOLD), g(GOLD), b(GOLD), 0.75f * fade);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.quad(sw, c.m, front.add(dir.multiply(0.1)), side.multiply(1.3), new Vec3d(0, 1.3, 0), 1, 0.95f, 0.8f, 0.4f * fade);
        }
    }

    /** 수호의 결계 / 금성철벽의 반구. */
    public static final class Dome extends Fx {
        final Vec3d p;
        final float rad;
        final boolean ult;

        Dome(ClientWorld w, Vec3d at, float rad, int dur, boolean ult) {
            super(dur);
            Vec3d g = Effects.ground(w, at.add(0, 0.5, 0), 3);
            this.p = (g != null ? g : at).add(0, 0.02, 0);
            this.rad = rad;
            this.ult = ult;
            for (int i = 0; i < (ult ? 50 : 26); i++) {
                double a = R.nextDouble() * Math.PI * 2;
                Particles.P pp = Particles.spawn(Kind.GLOW, p.add(Math.cos(a) * rad, 0.1, Math.sin(a) * rad), new Vec3d(0, rf(0.08f, 0.2f), 0), rf(18, 30), rf(0.07f, 0.12f));
                pp.color(i % 3 == 0 ? 0xFFFFFF : GOLD, 1f);
            }
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age % 3 == 0) {
                double a = R.nextDouble() * Math.PI * 2, rr = Math.sqrt(R.nextDouble()) * rad;
                Particles.P pp = Particles.spawn(Kind.GLOW, p.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr), new Vec3d(0, rf(0.02f, 0.05f), 0), rf(20, 34), rf(0.04f, 0.07f));
                pp.color(PALE_GOLD, 0.8f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float grow = Effects.outBack(Math.min(1, a / 8f));
            float fade = Math.min(1, (life - a) / 10f);
            float pulse = 0.85f + 0.15f * MathHelper.sin(a * 0.2f);
            float R0 = rad * grow;
            Vec3d base = p.subtract(c.cam);
            // 바닥 문장
            VertexConsumer ci = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
            Draw.flat(ci, c.m, base.x, base.y + 0.01, base.z, R0, a * 0.01f, r(GOLD), g(GOLD), b(GOLD), 0.7f * fade);
            VertexConsumer c2 = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
            Draw.flat(c2, c.m, base.x, base.y + 0.02, base.z, R0 * 0.5f, -a * 0.02f, r(PALE_GOLD), g(PALE_GOLD), b(PALE_GOLD), 0.55f * fade);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            ring(gl, c.m, base.add(0, 0.03, 0), R0, 0.18f, GOLD, 0.8f * fade * pulse, 96);
            // 반구 껍질 (위도선 + 경도선 + 옅은 면)
            int lat = 7, lon = ult ? 16 : 12;
            float shell = (ult ? 0.06f : 0.045f) * fade;
            for (int i = 0; i < lat; i++) {
                double th0 = Math.PI / 2 * i / lat, th1 = Math.PI / 2 * (i + 1) / lat;
                for (int j = 0; j < lon * 2; j++) {
                    double ph0 = Math.PI * 2 * j / (lon * 2), ph1 = Math.PI * 2 * (j + 1) / (lon * 2);
                    float al0 = shell * (1.4f - (float) i / lat), al1 = shell * (1.4f - (float) (i + 1) / lat);
                    domeV(gl, c.m, base, R0, th0, ph0, GOLD, al0);
                    domeV(gl, c.m, base, R0, th1, ph0, GOLD, al1);
                    domeV(gl, c.m, base, R0, th1, ph1, GOLD, al1);
                    domeV(gl, c.m, base, R0, th0, ph1, GOLD, al0);
                }
            }
            // 위도선: 아래에서 위로 흐르는 빛
            for (int i = 1; i < 5; i++) {
                double th = Math.PI / 2 * i / 5;
                float flow = 0.5f + 0.5f * MathHelper.sin(a * 0.25f - i * 1.3f);
                ring(gl, c.m, base.add(0, Math.sin(th) * R0, 0), (float) (Math.cos(th) * R0), 0.05f, PALE_GOLD, 0.45f * fade * flow, 72);
            }
            // 경도선
            for (int j = 0; j < lon; j++) {
                double ph = Math.PI * 2 * j / lon + a * 0.004;
                for (int i = 0; i < 12; i++) {
                    double th0 = Math.PI / 2 * i / 12, th1 = Math.PI / 2 * (i + 1) / 12;
                    Vec3d q0 = base.add(Math.cos(th0) * Math.cos(ph) * R0, Math.sin(th0) * R0, Math.cos(th0) * Math.sin(ph) * R0);
                    Vec3d q1 = base.add(Math.cos(th1) * Math.cos(ph) * R0, Math.sin(th1) * R0, Math.cos(th1) * Math.sin(ph) * R0);
                    float al = 0.35f * fade * (1 - (float) i / 14);
                    Vec3d side = new Vec3d(-Math.sin(ph), 0, Math.cos(ph)).multiply(0.04);
                    Draw.vc(gl, c.m, (float) (q0.x - side.x), (float) q0.y, (float) (q0.z - side.z), r(GOLD), g(GOLD), b(GOLD), al);
                    Draw.vc(gl, c.m, (float) (q0.x + side.x), (float) q0.y, (float) (q0.z + side.z), r(GOLD), g(GOLD), b(GOLD), al);
                    Draw.vc(gl, c.m, (float) (q1.x + side.x), (float) q1.y, (float) (q1.z + side.z), r(GOLD), g(GOLD), b(GOLD), al);
                    Draw.vc(gl, c.m, (float) (q1.x - side.x), (float) q1.y, (float) (q1.z - side.z), r(GOLD), g(GOLD), b(GOLD), al);
                }
            }
            if (ult) {
                // 둘레의 빛기둥 + 꼭대기 문장
                VertexConsumer st = c.imm.getBuffer(AWRenderLayers.additive(Tex.STREAK));
                for (int j = 0; j < 8; j++) {
                    double ph = Math.PI * 2 * j / 8 + a * 0.01;
                    Vec3d bot = base.add(Math.cos(ph) * R0, 0, Math.sin(ph) * R0);
                    float h = 4.5f * pulse;
                    Draw.streak(st, c.m, bot.add(0, h, 0), new Vec3d(0, 1, 0), h, 0.3f, r(GOLD), g(GOLD), b(GOLD), 0.6f * fade);
                }
                VertexConsumer top = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
                Draw.flat(top, c.m, base.x, base.y + R0 + 0.3, base.z, R0 * 0.35f, a * 0.03f, r(PALE_GOLD), g(PALE_GOLD), b(PALE_GOLD), 0.5f * fade);
            }
        }

        static void domeV(VertexConsumer vc, Matrix4f m, Vec3d c, float rad, double th, double ph, int col, float a) {
            Draw.vc(vc, m, (float) (c.x + Math.cos(th) * Math.cos(ph) * rad), (float) (c.y + Math.sin(th) * rad), (float) (c.z + Math.cos(th) * Math.sin(ph) * rad), Fx.r(col), Fx.g(col), Fx.b(col), a);
        }
    }

    /** 금성철벽: 하늘에서 내려와 박히는 거대한 방패. */
    public static final class UltShield extends Fx {
        public static final int LAND = 22;
        final int caster;
        final Vec3d at, fwd;
        boolean landed;

        UltShield(ClientWorld w, int caster, Vec3d at, int dur) {
            super(dur);
            this.caster = caster;
            Entity e = w.getEntityById(caster);
            Vec3d f = e != null ? ClassFx.fwd(e) : new Vec3d(0, 0, 1);
            this.fwd = f;
            Vec3d spot = at.subtract(f.multiply(2.2));
            Vec3d g = Effects.ground(w, spot.add(0, 1, 0), 4);
            this.at = g != null ? g : spot;
        }

        @Override
        public boolean tick(ClientWorld w) {
            if (age < LAND) {
                // 하늘에서 모이는 빛
                for (int i = 0; i < 4; i++) {
                    Vec3d sp = at.add(rf(-3, 3), rf(6, 12), rf(-3, 3));
                    Particles.P pp = Particles.spawn(Kind.STREAK, sp, new Vec3d(0, -0.4, 0), rf(10, 16), rf(0.05f, 0.1f));
                    pp.color(i % 2 == 0 ? GOLD : 0xFFFFFF, 1f);
                }
            }
            if (age == LAND && !landed) {
                landed = true;
                slam(w, at, 6);
                VfxManager.add(new Effects.Flash(at.add(0, 3, 0), 9f, PALE_GOLD, 14, Tex.FLARE));
                CameraController.addShake(0.6f, 14);
            }
            if (age > LAND && age % 6 == 0) {
                Particles.P pp = Particles.spawn(Kind.GLOW, at.add(rf(-1.8f, 1.8f), rf(0.2f, 6f), rf(-0.3f, 0.3f)), new Vec3d(0, 0.03, 0), 24, 0.08f);
                pp.color(GOLD, 0.9f);
            }
            return super.tick(w);
        }

        @Override
        public void render(Ctx c) {
            float a = age + c.tickDelta;
            float fade = Math.min(1, (life - a) / 16f) * Math.min(1, a / 5f);
            // 내려오는 높이
            float drop = a < LAND ? 14f * (float) Math.pow(1 - a / LAND, 2.2) : 0f;
            float settle = a >= LAND ? 0.25f * (float) Math.exp(-(a - LAND) * 0.3f) * MathHelper.sin((a - LAND) * 1.6f) : 0;
            Vec3d base = at.add(0, drop + settle - 0.6, 0).subtract(c.cam);
            Vec3d side = new Vec3d(-fwd.z, 0, fwd.x);
            float W = 2.6f, H = 7.2f;
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            // 방패 면 (위는 평평, 아래 뾰족) + 금 테두리
            int rows = 14;
            for (int i = 0; i < rows; i++) {
                float v0 = (float) i / rows, v1 = (float) (i + 1) / rows;
                float h0 = shieldHalf(v0) * W, h1 = shieldHalf(v1) * W;
                Vec3d c0 = base.add(0, H * (1 - v0), 0), c1 = base.add(0, H * (1 - v1), 0);
                float al = 0.16f * fade;
                quad4(gl, c.m, c0.subtract(side.multiply(h0)), c0.add(side.multiply(h0)), c1.add(side.multiply(h1)), c1.subtract(side.multiply(h1)), PALE_GOLD, al, al);
                // 테두리
                for (int s = -1; s <= 1; s += 2) {
                    Vec3d o0 = c0.add(side.multiply(s * h0)), o1 = c1.add(side.multiply(s * h1));
                    Vec3d i0 = c0.add(side.multiply(s * Math.max(0, h0 - 0.28))), i1 = c1.add(side.multiply(s * Math.max(0, h1 - 0.28)));
                    quad4(gl, c.m, i0, o0, o1, i1, GOLD, 0.85f * fade, 0.85f * fade);
                }
            }
            // 위 테두리
            Vec3d tl = base.add(0, H, 0).subtract(side.multiply(W)), tr = base.add(0, H, 0).add(side.multiply(W));
            quad4(gl, c.m, tl, tr, tr.add(0, -0.3, 0), tl.add(0, -0.3, 0), GOLD, 0.85f * fade, 0.85f * fade);
            // 가운데 문장 (십자)
            Vec3d mid = base.add(0, H * 0.58, 0).add(fwd.multiply(0.02));
            quad4(gl, c.m, mid.add(0, 1.8, 0).subtract(side.multiply(0.22)), mid.add(0, 1.8, 0).add(side.multiply(0.22)), mid.add(0, -2.0, 0).add(side.multiply(0.22)), mid.add(0, -2.0, 0).subtract(side.multiply(0.22)), 0xFFFFFF, 0.6f * fade, 0.6f * fade);
            quad4(gl, c.m, mid.add(0, 0.62, 0).subtract(side.multiply(1.3)), mid.add(0, 0.62, 0).add(side.multiply(1.3)), mid.add(0, 0.18, 0).add(side.multiply(1.3)), mid.add(0, 0.18, 0).subtract(side.multiply(1.3)), 0xFFFFFF, 0.6f * fade, 0.6f * fade);
            VertexConsumer fl = c.imm.getBuffer(AWRenderLayers.additive(Tex.FLARE));
            Vec3d gem = mid.add(0, 0.4, 0);
            Draw.billboard(fl, c.m, c.camRot, gem.x, gem.y, gem.z, 1.6f * (0.85f + 0.15f * MathHelper.sin(a * 0.3f)), a * 0.02f, r(GOLD), g(GOLD), b(GOLD), 0.8f * fade * nearFade(gem, 1.5f));
        }

        static float shieldHalf(float v) {
            return v < 0.45f ? 1f : (float) Math.sqrt(Math.max(0, 1 - (v - 0.45f) / 0.55f));
        }
    }

    static void quad4(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int col, float a0, float a1) {
        float cr = Fx.r(col), cg = Fx.g(col), cb = Fx.b(col);
        Draw.vc(vc, m, (float) a.x, (float) a.y, (float) a.z, cr, cg, cb, a0);
        Draw.vc(vc, m, (float) b.x, (float) b.y, (float) b.z, cr, cg, cb, a0);
        Draw.vc(vc, m, (float) c.x, (float) c.y, (float) c.z, cr, cg, cb, a1);
        Draw.vc(vc, m, (float) d.x, (float) d.y, (float) d.z, cr, cg, cb, a1);
    }

    static void stance(ClientWorld w, int id, boolean on, boolean quiet) {
        if (on) STANCE.put(id, w.getTime());
        else STANCE.remove(id);
        if (quiet) return;
        Entity e = w.getEntityById(id);
        if (e == null) return;
        Vec3d at = e.getPos();
        VfxManager.add(new Ring(w, at, on ? 8f : 2f, on ? GOLD : STEEL, on ? 3 : 0));
        Vec3d front = at.add(fwd(e).multiply(0.5)).add(0, 1.2, 0);
        VfxManager.add(new Effects.Flash(front, on ? 2.6f : 1.6f, on ? GOLD : STEEL, 9, Tex.RUNE_SHIELD));
        Effects.sparks(front, on ? 18 : 8, 0.3f, on ? GOLD : STEEL, 0.06f);
    }

    /** 대신 맞기: 아군 → 수호기사로 빛이 빨려감 (도발 표시도 같은 모양, 색만 다름). */
    static final class Intercept extends Fx {
        final int a, b, col;

        Intercept(int a, int b) {
            this(a, b, GOLD, 9);
            MinecraftClient mc = MinecraftClient.getInstance();
            Entity v = mc.world != null ? mc.world.getEntityById(b) : null;
            Entity g = mc.world != null ? mc.world.getEntityById(a) : null;
            if (v != null) {
                VfxManager.add(new Effects.Flash(v.getPos().add(0, 1.1, 0), 1.4f, GOLD, 6, Tex.RUNE_SHIELD));
                Effects.sparks(v.getPos().add(0, 1.1, 0), 6, 0.2f, GOLD, 0.05f);
            }
            if (g != null) Effects.sparks(g.getPos().add(0, 1.1, 0), 8, 0.25f, GOLD, 0.06f);
        }

        Intercept(int a, int b, int col, int life) {
            super(life);
            this.a = a;
            this.b = b;
            this.col = col;
        }

        @Override
        public void render(Ctx c) {
            Entity ga = c.world.getEntityById(a), gb = c.world.getEntityById(b);
            if (ga == null || gb == null) return;
            float t = t(c);
            Vec3d p0 = gb.getLerpedPos(c.tickDelta).add(0, gb.getHeight() * 0.6, 0);
            Vec3d p1 = ga.getLerpedPos(c.tickDelta).add(0, ga.getHeight() * 0.6, 0);
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            Draw.lightning(gl, c.m, p0.subtract(c.cam), p1.subtract(c.cam), a * 31L + b + age / 2, 0.05f * (1 - t) + 0.02f, 8, 0.25f, r(col), g(col), b(col), 0.8f * (1 - t));
        }
    }

    private ClassFx() {
    }
}
