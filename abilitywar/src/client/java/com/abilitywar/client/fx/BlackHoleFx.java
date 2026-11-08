package com.abilitywar.client.fx;

import com.abilitywar.entity.VoidSingularityEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.abilitywar.client.fx.Fx.*;

/** 블랙홀 (공허 궁극기) 렌더링/클라이언트 파티클. */
public final class BlackHoleFx {
    private static final Random R = new Random();

    static List<VoidSingularityEntity> all(ClientWorld w) {
        List<VoidSingularityEntity> list = new ArrayList<>();
        for (Entity e : w.getEntities()) if (e instanceof VoidSingularityEntity v) list.add(v);
        return list;
    }

    /** 크기 (0..1+). */
    public static float scale(VoidSingularityEntity e, float td) {
        float a = e.age + td;
        int dur = e.getDuration();
        float grow = Effects.outBack(Math.min(1, a / 18f));
        float shrink = 1;
        if (a > dur - 12) {
            float k = Math.min(1, (a - (dur - 12)) / 12f);
            shrink = 1 - k * k;
        }
        float breathe = 1 + 0.04f * (float) Math.sin(a * 0.25);
        return Math.max(0, grow * shrink * breathe);
    }

    public static void tick(ClientWorld w) {
        for (VoidSingularityEntity e : all(w)) {
            float s = scale(e, 0);
            if (s <= 0.05f) continue;
            Vec3d c = e.getPos();
            // 빨려드는 줄기
            int n = (int) (10 * s);
            for (int i = 0; i < n; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                double rr = 5 + R.nextDouble() * 9;
                double y = (R.nextDouble() - 0.5) * 3.5;
                Vec3d p = c.add(Math.cos(a) * rr, y, Math.sin(a) * rr);
                Vec3d tang = new Vec3d(-Math.sin(a), 0, Math.cos(a)).multiply(0.25);
                Particles.P pp = Particles.spawn(Particles.Kind.STREAK, p, tang, 60, 0.07f + R.nextFloat() * 0.08f);
                int col = i % 3 == 0 ? PALE : (i % 3 == 1 ? PINK : PURPLE);
                pp.color(col, 1f);
                pp.attract = c;
                pp.attractStrength = 0.035f;
                pp.drag = 0.94f;
                pp.streakLen = 1.6f;
            }
            if (e.age % 2 == 0) {
                double a = R.nextDouble() * Math.PI * 2;
                double rr = 3 + R.nextDouble() * 8;
                Particles.P pp = Particles.spawn(Particles.Kind.SMOKE_DARK, c.add(Math.cos(a) * rr, (R.nextDouble() - 0.5) * 2, Math.sin(a) * rr), Vec3d.ZERO, 50, 0.8f);
                pp.color(0x06000C, 0.55f);
                pp.size1 = 0.2f;
                pp.attract = c;
                pp.attractStrength = 0.02f;
                pp.drag = 0.95f;
            }
            // 땅에서 뜯겨 올라가는 블록 조각
            if (e.age % 2 == 0 && s > 0.6f) {
                double a = R.nextDouble() * Math.PI * 2;
                double rr = 2 + R.nextDouble() * 10;
                int bx = MathHelper.floor(c.x + Math.cos(a) * rr), bz = MathHelper.floor(c.z + Math.sin(a) * rr);
                int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
                if (top > c.y - 14 && top < c.y + 4) {
                    BlockPos bp = new BlockPos(bx, top, bz);
                    BlockState st = w.getBlockState(bp);
                    if (!st.isAir()) {
                        Vec3d sp = Vec3d.ofCenter(bp).add(0, 0.6, 0);
                        Debris.spawn(st, sp, new Vec3d(0, 0.15, 0), 0.25f + R.nextFloat() * 0.35f, 90, c, 0.05f, false);
                        BlockStateParticleEffect bpe = new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
                        for (int k = 0; k < 4; k++) w.addParticle(bpe, sp.x + (R.nextDouble() - 0.5), sp.y, sp.z + (R.nextDouble() - 0.5), 0, 0.2, 0);
                    }
                }
            }
        }
    }

    public static void render(Fx.Ctx c) {
        List<VoidSingularityEntity> list = all(c.world);
        if (list.isEmpty()) return;
        // 1) 중심 (엔드 포털 셰이더: 깊이 기록)
        for (VoidSingularityEntity e : list) {
            float s = scale(e, c.tickDelta);
            if (s <= 0.01f) continue;
            Vec3d p = e.getLerpedPos(c.tickDelta).subtract(c.cam);
            // 카메라가 중심 안에 있으면(붙잡힘) 구를 그리지 않는다 - 대신 화면이 공허로 어두워짐
            if (p.length() < 1.55f * s * 1.25f) continue;
            VertexConsumer ep = c.imm.getBuffer(RenderLayer.getEndPortal());
            Draw.spherePos(ep, c.m, p, 1.55f * s, 16, 24);
        }
        c.imm.draw();
        for (VoidSingularityEntity e : list) {
            float s = scale(e, c.tickDelta);
            if (s <= 0.01f) continue;
            float a = e.age + c.tickDelta;
            Vec3d wp = e.getLerpedPos(c.tickDelta);
            Vec3d p = wp.subtract(c.cam);
            float core = 1.55f * s;
            boolean inside = p.length() < core * 1.25f;
            // 바닥 마법진
            Vec3d g = Effects.ground(c.world, wp, 14);
            if (g != null) {
                Vec3d gp = g.add(0, 0.05, 0).subtract(c.cam);
                float dist = (float) (wp.y - g.y);
                float k = MathHelper.clamp(1 - dist / 14f, 0.2f, 1f);
                VertexConsumer dk = c.imm.getBuffer(AWRenderLayers.translucent(Tex.GLOW));
                Draw.flat(dk, c.m, gp.x, gp.y - 0.02, gp.z, 7.5f * s, 0, 0.02f, 0, 0.04f, 0.75f * k);
                VertexConsumer mc = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE));
                Draw.flat(mc, c.m, gp.x, gp.y, gp.z, 6.0f * s, a * 0.02f, r(PURPLE), g(PURPLE), b(PURPLE), 0.75f * k);
                VertexConsumer mc2 = c.imm.getBuffer(AWRenderLayers.additive(Tex.MAGIC_CIRCLE2));
                Draw.flat(mc2, c.m, gp.x, gp.y + 0.01, gp.z, 3.6f * s, -a * 0.035f, r(PINK), g(PINK), b(PINK), 0.6f * k);
                VertexConsumer cr = c.imm.getBuffer(AWRenderLayers.translucent(Tex.CRACK));
                Draw.flat(cr, c.m, gp.x, gp.y + 0.005, gp.z, 4.5f * s, 0.7f, 0.03f, 0, 0.06f, 0.8f * k);
            }
            if (inside) continue;
            // 바깥 광륜
            VertexConsumer gl = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            Draw.billboard(gl, c.m, c.camRot, p.x, p.y, p.z, core * 4.2f, 0, r(DEEP), g(DEEP), b(DEEP), 0.75f);
            Draw.billboard(gl, c.m, c.camRot, p.x, p.y, p.z, core * 2.3f, 0, r(PURPLE), g(PURPLE), b(PURPLE), 0.8f);
            // 어두운 가장자리
            VertexConsumer dkr = c.imm.getBuffer(AWRenderLayers.translucent(Tex.RING_SOFT));
            Draw.billboard(dkr, c.m, c.camRot, p.x, p.y, p.z, core * 1.18f, 0, 0, 0, 0, 0.95f);
            // 강착원반 (기울어짐 + 세차운동)
            double tilt = Math.toRadians(16 + 4 * Math.sin(a * 0.02));
            double prec = a * 0.004;
            Vec3d ax = new Vec3d(Math.cos(prec), 0, Math.sin(prec));
            Vec3d ay0 = new Vec3d(-Math.sin(prec), 0, Math.cos(prec));
            Vec3d ay = ay0.multiply(Math.cos(tilt)).add(0, Math.sin(tilt), 0);
            // 어두운 원반 (밝은 하늘에서도 대비가 생기도록)
            VertexConsumer swd = c.imm.getBuffer(AWRenderLayers.translucent(Tex.SWIRL));
            Draw.annulus(swd, c.m, p, ax, ay, core * 1.2f, core * 5.2f, 64, -a * 0.008f, 3, 0.05f, 0.0f, 0.12f, 0.85f, 0.15f);
            VertexConsumer sw = c.imm.getBuffer(AWRenderLayers.additive(Tex.SWIRL));
            Draw.annulus(sw, c.m, p, ax, ay, core * 1.25f, core * 4.6f, 64, -a * 0.008f, 3, 1f, 0.75f, 1f, 1f, 0.25f);
            Draw.annulus(sw, c.m, p, ax, ay, core * 1.25f, core * 3.2f, 64, -a * 0.011f + 0.2f, 4, 0.85f, 0.45f, 1f, 0.9f, 0.1f);
            Draw.annulus(sw, c.m, p, ax, ay, core * 1.45f, core * 6.2f, 64, -a * 0.005f + 0.37f, 2, 0.6f, 0.3f, 1, 0.6f, 0f);
            // 렌즈 효과: 카메라를 향한 고리 (원반 빛이 위아래로 휘어 보이는 느낌)
            Vec3d toCam = p.multiply(-1).normalize();
            Vec3d upv = Math.abs(toCam.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
            Vec3d bx = upv.crossProduct(toCam).normalize();
            Vec3d by = toCam.crossProduct(bx).normalize();
            Draw.annulus(sw, c.m, p, bx, by, core * 1.02f, core * 1.75f, 48, -a * 0.012f, 2, 1, 0.9f, 1, 0.9f, 0f);
            VertexConsumer ring = c.imm.getBuffer(AWRenderLayers.additive(Tex.RING));
            Draw.billboard(ring, c.m, c.camRot, p.x, p.y, p.z, core * 1.12f, 0, r(PALE), g(PALE), b(PALE), 0.95f);
            // 맥동 충격파
            float pt = (a % 24) / 24f;
            VertexConsumer swv = c.imm.getBuffer(AWRenderLayers.additive(Tex.SHOCKWAVE));
            Draw.billboard(swv, c.m, c.camRot, p.x, p.y, p.z, core * (1.3f + pt * 5f), 0, r(PURPLE), g(PURPLE), b(PURPLE), (1 - pt) * 0.35f);
            // 번개
            VertexConsumer lt = c.imm.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
            long seed = (long) (a / 2) + e.getId() * 1000L;
            Random rr = new Random(seed);
            for (int i = 0; i < 4; i++) {
                double ang = rr.nextDouble() * Math.PI * 2;
                Vec3d d0 = ax.multiply(Math.cos(ang)).add(ay.multiply(Math.sin(ang)));
                Vec3d from = p.add(d0.multiply(core * 1.1));
                Vec3d to = p.add(d0.multiply(core * (2.6 + rr.nextDouble() * 2.5))).add(0, (rr.nextDouble() - 0.5) * 1.5, 0);
                Draw.lightning(lt, c.m, from, to, seed * 13 + i, 0.03f * s, 8, 0.6f, r(PINK), g(PINK), b(PINK), 0.85f);
            }
        }
    }

    /** 가까이 있을수록 1 (화면 보라색/어둠). */
    public static float proximity(ClientWorld w, Vec3d cam) {
        float best = 0;
        for (VoidSingularityEntity e : all(w)) {
            double d = e.getPos().distanceTo(cam);
            float k = (float) MathHelper.clamp(1 - (d - 3) / 22, 0, 1) * Math.min(1, scale(e, 0));
            if (d < 2.6) k = 1.45f;
            best = Math.max(best, k);
        }
        return best;
    }

    private BlackHoleFx() {
    }
}
