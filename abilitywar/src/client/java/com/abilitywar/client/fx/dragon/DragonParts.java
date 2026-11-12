package com.abilitywar.client.fx.dragon;

import com.abilitywar.client.anim.AnimState;
import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.data.AWPlayer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 용기사의 몸에 붙는 부위: 날개, 꼬리, 발톱, 비늘 문양.
 * 보이는 정도/자세는 재생 중인 애니메이션과 용화 상태로 결정 (따로 동기화할 필요 없음).
 */
public final class DragonParts {
    static final Identifier WING = Tex.fx("dragon_wing");
    static final Identifier WING_GLOW = Tex.fx("dragon_wing_glow");
    static final Identifier TAIL = Tex.fx("dragon_tail");
    static final Identifier SCALES = Tex.fx("dragon_scales_skin");
    static final Identifier GLOW = Tex.fx("dragon_glow_skin");
    public static final Identifier SHIELD = Tex.fx("scale_shield");

    public static boolean isDragon(PlayerEntity p) {
        return "dragon".equals(((AWPlayer) p).aw$getAbilityKey());
    }

    public static boolean transformed(PlayerEntity p) {
        return ((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_TRANSFORMED);
    }

    public static float smooth(float x) {
        x = MathHelper.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    public static float env(float t, float in0, float in1, float out0, float out1) {
        return smooth((t - in0) / Math.max(0.01f, in1 - in0)) * (1 - smooth((t - out0) / Math.max(0.01f, out1 - out0)));
    }

    // ------------------------------------------------------------------ 자세
    public static final class WingPose {
        public float amount, spread, flap, sweep, scale = 1;
    }

    public static final class TailPose {
        public float amount, swing, lift, length = 1;
    }

    static AnimState.Action action(PlayerEntity p) {
        AnimState st = PlayerAnimator.peek(p);
        return st == null ? null : st.current();
    }

    public static WingPose wings(PlayerEntity p, float now) {
        WingPose w = new WingPose();
        if (transformed(p)) {
            w.amount = 1;
            w.spread = 0.42f + 0.06f * MathHelper.sin(now * 0.12f);
            w.flap = 6 * MathHelper.sin(now * 0.12f) + (p.isOnGround() ? 0 : 14 * MathHelper.sin(now * 0.45f));
        }
        AnimState.Action a = action(p);
        if (a == null || !a.clip.id.startsWith("dragon.")) return w;
        float t = a.time(now), L = a.clip.length;
        String id = a.clip.id;
        float e = 0, spread = 0, flap = 0, sweep = 0, scale = 1;
        switch (id) {
            case "dragon.skill2", "dragon.skill2_double" -> {
                boolean dbl = id.endsWith("double");
                e = env(t, 0, 2.5f, L - 4, L);
                scale = 1.45f;
                spread = 0.15f + 0.95f * smooth(t / 4f);
                flap = t < 4 ? 20 * smooth(t / 4f) : t < 6 ? 20 - 45 * smooth((t - 4) / 2f) : -25 + 20 * smooth((t - 6) / 4f);
                sweep = strike(t, 7, 9, 12) + (dbl ? strike(t, 12, 14, 17) : 0);
            }
            case "dragon.ult" -> {
                e = env(t, 4, 14, L - 6, L);
                scale = 1.35f;
                spread = 1.05f;
                flap = 18 * MathHelper.sin(t * 0.22f) + (t > 40 && t < 56 ? 25 * MathHelper.sin(t * 0.6f) : 0);
            }
            case "dragon.transform" -> {
                e = env(t, 1, 5, L + 10, L + 11);
                spread = t < 5 ? 0.1f : 0.45f + 0.7f * Math.max(0, 1 - (t - 6) / 10f);
                flap = t < 5 ? -10 : 30 * Math.max(0, 1 - (t - 5) / 8f);
            }
            default -> {
                return w;
            }
        }
        if (e <= 0) return w;
        w.spread = MathHelper.lerp(e, w.spread, spread);
        w.flap = MathHelper.lerp(e, w.flap, flap);
        w.sweep = MathHelper.lerp(e, w.sweep, sweep);
        w.scale = MathHelper.lerp(e, w.scale, scale);
        w.amount = Math.max(w.amount, e);
        return w;
    }

    static float strike(float t, float a, float b, float c) {
        if (t < a || t > c + 3) return 0;
        if (t < b) return -25 * smooth((t - a) / (b - a));
        if (t < c) return -25 + 110 * smooth((t - b) / (c - b));
        return 85 * (1 - smooth((t - c) / 3f));
    }

    public static TailPose tail(PlayerEntity p, float now) {
        TailPose tp = new TailPose();
        if (transformed(p)) {
            tp.amount = 1;
            tp.swing = 14 * MathHelper.sin(now * 0.09f);
            tp.lift = -8 + 5 * MathHelper.sin(now * 0.13f);
        }
        AnimState.Action a = action(p);
        if (a == null || !a.clip.id.startsWith("dragon.")) return tp;
        float t = a.time(now), L = a.clip.length;
        float e = 0, swing = 0, lift = 0, len = 1;
        switch (a.clip.id) {
            case "dragon.skill3", "dragon.skill3_air" -> {
                e = env(t, 8, 10, 15, 18);
                len = 1.5f;
                swing = -70 + 260 * smooth((t - 10) / 3f);
                lift = 10;
            }
            case "dragon.skill4_success" -> {
                e = env(t, 6, 8, 13, 16);
                len = 1.5f;
                lift = t < 10 ? 70 * smooth((t - 6) / 4f) : 70 - 120 * smooth((t - 10) / 2f);
            }
            case "dragon.ult" -> {
                e = env(t, 6, 16, L - 6, L);
                swing = 20 * MathHelper.sin(t * 0.15f);
                lift = -5;
                len = 1.2f;
            }
            case "dragon.transform" -> {
                e = env(t, 3, 7, L + 10, L + 11);
                swing = 40 * MathHelper.sin(t * 0.5f) * Math.max(0, 1 - t / 14f);
            }
            default -> {
                return tp;
            }
        }
        if (e <= 0) return tp;
        tp.swing = MathHelper.lerp(e, tp.swing, swing);
        tp.lift = MathHelper.lerp(e, tp.lift, lift);
        tp.length = MathHelper.lerp(e, tp.length, len);
        tp.amount = Math.max(tp.amount, e);
        return tp;
    }

    public static float claws(PlayerEntity p, float now) {
        float c = transformed(p) ? 1 : 0;
        AnimState.Action a = action(p);
        if (a != null && a.clip.id.startsWith("dragon.")) {
            float t = a.time(now), L = a.clip.length;
            String id = a.clip.id;
            if (id.startsWith("dragon.skill1") || id.startsWith("dragon.skill3") || id.equals("dragon.skill4_success") || id.equals("dragon.ult"))
                c = Math.max(c, env(t, 0, 2, L - 3, L));
        }
        return c;
    }

    public static float scales(PlayerEntity p, float now) {
        float s = transformed(p) ? 1 : 0;
        AnimState.Action a = action(p);
        if (a != null && (a.clip.id.equals("dragon.skill4") || a.clip.id.equals("dragon.skill4_fail")))
            s = Math.max(s, env(a.time(now), 0, 3, a.clip.length - 4, a.clip.length));
        return s;
    }

    // ------------------------------------------------------------------ 그리기 도우미 (엔티티 정점 형식)
    static void ev(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float r, float g, float b, float a, float u, float v, int light, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, nx, ny, nz).next();
    }

    /**
     * 날개 하나 (wing 공간: 바깥 +X, 위 -Y). 안쪽 판과 바깥 판 두 장으로 나눠서
     * 바깥 판을 뒤로 꺾는다 (평평한 판처럼 보이지 않게).
     */
    public static void wingQuad(MatrixStack ms, VertexConsumer vc, int light, float size, float r, float g, float b, float alpha, boolean mirror, float bendDeg) {
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        float y0 = -0.88f * size, y1 = 0.12f * size;
        float sgn = mirror ? -1 : 1;
        float hingeU = 0.42f;
        float xh = sgn * (-0.04f + hingeU) * size;
        double th = Math.toRadians(bendDeg);
        float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
        float[] us = {0, hingeU, 1};
        float[][] px = new float[3][];
        for (int i = 0; i < 3; i++) {
            float x = sgn * (-0.04f + us[i]) * size;
            if (i < 2) px[i] = new float[]{x, 0};
            else {
                // 경첩 기준으로 뒤(+Z)로 꺾음
                float dx = Math.abs(x - xh);
                px[i] = new float[]{xh + sgn * dx * cs, dx * sn};
            }
        }
        for (int p = 0; p < 2; p++) {
            float xa = px[p][0], za = px[p][1], xb = px[p + 1][0], zb = px[p + 1][1];
            float ua = us[p], ub = us[p + 1];
            // 면 법선 (대략)
            float nx = -(zb - za), nz = (xb - xa);
            float nl = (float) Math.sqrt(nx * nx + nz * nz);
            nx /= nl;
            nz /= nl;
            // 앞면/뒷면을 법선 쪽으로 아주 살짝 띄움: 같은 면에 겹쳐 그리면 줄무늬(z-fighting)가 생김
            float ox = nx * 0.004f, oz = nz * 0.004f;
            ev(vc, m, n, xa + ox, y1, za + oz, r, g, b, alpha, ua, 1, light, nx, 0, nz);
            ev(vc, m, n, xb + ox, y1, zb + oz, r, g, b, alpha, ub, 1, light, nx, 0, nz);
            ev(vc, m, n, xb + ox, y0, zb + oz, r, g, b, alpha, ub, 0, light, nx, 0, nz);
            ev(vc, m, n, xa + ox, y0, za + oz, r, g, b, alpha, ua, 0, light, nx, 0, nz);
            ev(vc, m, n, xa - ox, y0, za - oz, r, g, b, alpha, ua, 0, light, -nx, 0, -nz);
            ev(vc, m, n, xb - ox, y0, zb - oz, r, g, b, alpha, ub, 0, light, -nx, 0, -nz);
            ev(vc, m, n, xb - ox, y1, zb - oz, r, g, b, alpha, ub, 1, light, -nx, 0, -nz);
            ev(vc, m, n, xa - ox, y1, za - oz, r, g, b, alpha, ua, 1, light, -nx, 0, -nz);
        }
    }

    /** 몸통 공간에서 양 날개. */
    static void renderWings(MatrixStack ms, VertexConsumerProvider vcp, int light, WingPose w) {
        if (w.amount <= 0.01f) return;
        float size = 1.55f * w.scale * (0.3f + 0.7f * w.amount);
        float fold = (1 - MathHelper.clamp(w.spread, 0, 1.2f)) * 78;
        for (int side = 0; side < 2; side++) {
            boolean left = side == 1; // 모델 +X = 왼쪽
            float sx = left ? 1 : -1;
            ms.push();
            ms.translate(sx * 1.6f / 16f, 3.2f / 16f, 2.4f / 16f);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12));
            float yaw = (left ? -fold : fold) + (left ? w.sweep : -w.sweep);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(left ? -w.flap - 18 : w.flap + 18));
            float bend = 16 + fold * 0.45f + w.flap * 0.25f;
            wingQuad(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(WING)), light, size, 1, 1, 1, w.amount, !left, bend);
            // 가산 합성은 알파를 무시하므로 색으로 세기를 조절 (텍스처는 미리 곱한 알파)
            float gl = 0.75f * w.amount;
            wingQuad(ms, vcp.getBuffer(RenderLayer.getEyes(WING_GLOW)), light, size, gl, gl, gl, 1, !left, bend);
            ms.pop();
        }
    }

    /** 모델 공간에서 꼬리. */
    static void renderTail(MatrixStack ms, VertexConsumerProvider vcp, int light, TailPose tp, float now) {
        if (tp.amount <= 0.01f) return;
        int N = 12;
        float len = 1.25f * tp.length * (0.25f + 0.75f * tp.amount);
        Vec3d[] pts = new Vec3d[N + 1];
        pts[0] = new Vec3d(0, 11.2 / 16.0, 2.1 / 16.0);
        double yaw0 = Math.toRadians(tp.swing), pitch0 = Math.toRadians(-tp.lift);
        for (int i = 0; i < N; i++) {
            double k = (double) i / N;
            double yaw = yaw0 * (0.35 + 0.65 * k) + Math.sin(now * 0.2 - i * 0.5) * 0.08;
            double pitch = 0.35 + pitch0 * (0.5 + 0.5 * k) - k * 0.25 * (1 - Math.abs(tp.lift) / 90.0);
            Vec3d dir = new Vec3d(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            pts[i + 1] = pts[i].add(dir.multiply(len / N));
        }
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TAIL));
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        for (int i = 0; i < N; i++) {
            float r0 = (float) (0.1 * (1 - (double) i / N) + 0.012) * (0.4f + 0.6f * tp.amount);
            float r1 = (float) (0.1 * (1 - (double) (i + 1) / N) + 0.012) * (0.4f + 0.6f * tp.amount);
            Vec3d a = pts[i], b = pts[i + 1];
            Vec3d d = b.subtract(a).normalize();
            Vec3d s1 = d.crossProduct(new Vec3d(0, 1, 0));
            if (s1.lengthSquared() < 1e-6) s1 = new Vec3d(1, 0, 0);
            s1 = s1.normalize();
            Vec3d s2 = d.crossProduct(s1).normalize();
            Vec3d[] ring = {s1, s2, s1.multiply(-1), s2.multiply(-1)};
            float u0 = (float) i / N, u1 = (float) (i + 1) / N;
            for (int f = 0; f < 4; f++) {
                Vec3d c0 = ring[f], c1 = ring[(f + 1) % 4];
                Vec3d nn = c0.add(c1).normalize();
                float v0 = f / 4f, v1 = (f + 1) / 4f;
                ev(vc, m, n, (float) (a.x + c0.x * r0), (float) (a.y + c0.y * r0), (float) (a.z + c0.z * r0), 1, 1, 1, tp.amount, u0 * 4, v0, light, (float) nn.x, (float) nn.y, (float) nn.z);
                ev(vc, m, n, (float) (b.x + c0.x * r1), (float) (b.y + c0.y * r1), (float) (b.z + c0.z * r1), 1, 1, 1, tp.amount, u1 * 4, v0, light, (float) nn.x, (float) nn.y, (float) nn.z);
                ev(vc, m, n, (float) (b.x + c1.x * r1), (float) (b.y + c1.y * r1), (float) (b.z + c1.z * r1), 1, 1, 1, tp.amount, u1 * 4, v1, light, (float) nn.x, (float) nn.y, (float) nn.z);
                ev(vc, m, n, (float) (a.x + c1.x * r0), (float) (a.y + c1.y * r0), (float) (a.z + c1.z * r0), 1, 1, 1, tp.amount, u0 * 4, v1, light, (float) nn.x, (float) nn.y, (float) nn.z);
            }
        }
        // 등줄기 가시 (빛남)
        VertexConsumer g = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        for (int i = 1; i < N - 1; i += 2) {
            Vec3d a = pts[i], b = pts[i + 1];
            Vec3d d = b.subtract(a).normalize();
            Vec3d up = d.crossProduct(new Vec3d(1, 0, 0)).normalize().multiply(-1);
            if (up.y > 0) up = up.multiply(-1);
            float h = (float) (0.09 * (1 - (double) i / N) + 0.02) * tp.amount;
            Vec3d tip = a.add(b).multiply(0.5).add(up.multiply(h + 0.06));
            float rr = 1f * tp.amount, gg = 0.45f * tp.amount, bb = 0.12f * tp.amount;
            g.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(rr, gg, bb, 1).next();
            g.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(rr, gg, bb, 1).next();
            g.vertex(m, (float) tip.x, (float) tip.y, (float) tip.z).color(rr, gg, bb, 1).next();
            g.vertex(m, (float) tip.x, (float) tip.y, (float) tip.z).color(rr, gg, bb, 1).next();
        }
    }

    /** 주먹 끝(손 공간: 손 방향 +Y 또는 -Z)에서 뻗는 발톱 3개. */
    public static void renderClaws(MatrixStack ms, VertexConsumerProvider vcp, float amount, boolean fpForward) {
        if (amount <= 0.01f) return;
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer solid = vcp.getBuffer(AWRenderLayers.SOLID_COLOR);
        VertexConsumer glow = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float L = 0.36f * amount;
        int seg = 6;
        for (int k = -1; k <= 1; k++) {
            float ox = k * 0.075f;
            float len = L * (k == 0 ? 1.1f : 0.92f);
            for (int i = 0; i < seg; i++) {
                float t0 = (float) i / seg, t1 = (float) (i + 1) / seg;
                float[] p0 = clawPoint(t0, len, ox, fpForward), p1 = clawPoint(t1, len, ox, fpForward);
                float w0 = 0.032f * (1 - t0) + 0.003f, w1 = 0.032f * (1 - t1) + 0.003f;
                float c0 = 0.95f - 0.6f * t0, c1 = 0.95f - 0.6f * t1;
                // 가로판 + 세로판 (어느 각도에서도 보이게)
                quadC(solid, m, p0, p1, w0, w1, true, c0, c0 * 0.88f, c0 * 0.7f, c1, c1 * 0.88f, c1 * 0.7f);
                quadC(solid, m, p0, p1, w0, w1, false, c0 * 0.9f, c0 * 0.78f, c0 * 0.6f, c1 * 0.9f, c1 * 0.78f, c1 * 0.6f);
                float ga = amount * (0.35f + 0.65f * t1);
                quadC(glow, m, p0, p1, w0 * 1.8f, w1 * 1.8f, true, ga, ga * 0.4f, ga * 0.1f, ga, ga * 0.4f, ga * 0.1f);
            }
        }
    }

    static float[] clawPoint(float t, float len, float ox, boolean fpForward) {
        float along = t * len;
        float curve = (float) Math.sin(t * Math.PI * 0.5) * len * 0.35f;
        if (fpForward) return new float[]{ox, -curve * 0.6f, -along};
        return new float[]{ox, along, -curve};
    }

    /** a→b 판. horizontal 이면 X 방향 폭, 아니면 (진행방향 × X) 방향 폭. */
    static void quadC(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float wa, float wb, boolean horizontal,
                      float r0, float g0, float b0, float r1, float g1, float b1) {
        float wx, wy, wz;
        if (horizontal) {
            wx = 1;
            wy = 0;
            wz = 0;
        } else {
            float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
            // d × X = (0, dz, -dy)
            float ly = dz, lz = -dy;
            float len = (float) Math.sqrt(ly * ly + lz * lz);
            if (len < 1e-6f) {
                ly = 0;
                lz = 1;
                len = 1;
            }
            wx = 0;
            wy = ly / len;
            wz = lz / len;
        }
        vc.vertex(m, a[0] - wx * wa, a[1] - wy * wa, a[2] - wz * wa).color(r0, g0, b0, 1).next();
        vc.vertex(m, a[0] + wx * wa, a[1] + wy * wa, a[2] + wz * wa).color(r0, g0, b0, 1).next();
        vc.vertex(m, b[0] + wx * wb, b[1] + wy * wb, b[2] + wz * wb).color(r1, g1, b1, 1).next();
        vc.vertex(m, b[0] - wx * wb, b[1] - wy * wb, b[2] - wz * wb).color(r1, g1, b1, 1).next();
    }

    /** 반격 자세: 앞쪽의 비늘 방패 (몸통 공간). */
    static void renderShield(MatrixStack ms, VertexConsumerProvider vcp, float amount, float now) {
        if (amount <= 0.01f) return;
        Matrix4f m = ms.peek().getPositionMatrix();
        float s = 0.62f * (0.6f + 0.4f * amount);
        float z = -0.55f;
        float y = 0.35f;
        float rot = now * 0.05f;
        float cs = (float) Math.cos(rot) * s, sn = (float) Math.sin(rot) * s;
        VertexConsumer v = vcp.getBuffer(AWRenderLayers.additive(SHIELD));
        float a = amount * (0.75f + 0.25f * (float) Math.sin(now * 0.8));
        v.vertex(m, -cs - sn, y - sn + cs, z).texture(0, 1).color(1f, 0.5f, 0.15f, a).next();
        v.vertex(m, cs - sn, y + sn + cs, z).texture(1, 1).color(1f, 0.5f, 0.15f, a).next();
        v.vertex(m, cs + sn, y + sn - cs, z).texture(1, 0).color(1f, 0.5f, 0.15f, a).next();
        v.vertex(m, -cs + sn, y - sn - cs, z).texture(0, 0).color(1f, 0.5f, 0.15f, a).next();
    }

    // ------------------------------------------------------------------ 1인칭
    /** 1인칭 반격 자세의 비늘 방패 세기. */
    public static float fpShieldAmount(PlayerEntity p, float now) {
        if (!isDragon(p)) return 0;
        AnimState.Action a = action(p);
        if (a == null || !a.clip.id.equals("dragon.skill4")) return 0;
        return env(a.time(now), 0, 3, a.clip.length - 3, a.clip.length);
    }

    /** 1인칭 팔 (팔 모델 공간) 위에 비늘 덮기. */
    public static void fpArmOverlay(MatrixStack ms, VertexConsumerProvider vcp, int light, PlayerEntity p, net.minecraft.client.model.ModelPart part) {
        if (!isDragon(p)) return;
        float now = ClientClock.now();
        float sc = scales(p, now);
        if (sc <= 0.01f) return;
        part.render(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(SCALES)), light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 0.9f * sc);
        float pulse = 0.65f + 0.35f * MathHelper.sin(now * 0.2f);
        part.render(ms, vcp.getBuffer(RenderLayer.getEyes(GLOW)), light, OverlayTexture.DEFAULT_UV, pulse * sc, pulse * sc, pulse * sc, 1);
    }

    /** 1인칭 발톱 (어깨 기준 공간, 손 방향 -Z). */
    public static void fpClaws(MatrixStack ms, VertexConsumerProvider vcp, PlayerEntity p, int arm) {
        if (!isDragon(p)) return;
        float cl = claws(p, ClientClock.now());
        if (cl <= 0.01f) return;
        ms.push();
        ms.translate(0, 0.02f, -0.7f);
        renderClaws(ms, vcp, cl, true);
        ms.pop();
    }

    // ------------------------------------------------------------------ 피처 렌더러
    public static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        public Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (!isDragon(p) || p.isInvisible()) return;
            float now = ClientClock.now();
            PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
            // 비늘 + 빛나는 문양
            float sc = scales(p, now);
            if (sc > 0.01f) {
                model.render(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(SCALES)), light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 0.9f * sc);
                float pulse = 0.65f + 0.35f * MathHelper.sin(now * 0.2f);
                model.render(ms, vcp.getBuffer(RenderLayer.getEyes(GLOW)), light, OverlayTexture.DEFAULT_UV, pulse * sc, pulse * sc, pulse * sc, 1);
            }
            // 날개 / 반격 방패 (몸통 기준)
            WingPose w = wings(p, now);
            AnimState.Action a = action(p);
            float shield = 0;
            if (a != null && a.clip.id.equals("dragon.skill4")) shield = env(a.time(now), 0, 3, a.clip.length - 3, a.clip.length);
            if (w.amount > 0.01f || shield > 0.01f) {
                ms.push();
                model.body.rotate(ms);
                renderWings(ms, vcp, light, w);
                renderShield(ms, vcp, shield, now);
                ms.pop();
            }
            renderTail(ms, vcp, light, tail(p, now), now);
            float cl = claws(p, now);
            if (cl > 0.01f) {
                for (int arm = 0; arm < 2; arm++) {
                    ms.push();
                    model.setArmAngle(arm == 0 ? Arm.RIGHT : Arm.LEFT, ms);
                    ms.translate(arm == 0 ? -0.0625f : 0.0625f, 0.6f, -0.02f);
                    renderClaws(ms, vcp, cl, false);
                    ms.pop();
                }
            }
        }
    }

    private DragonParts() {
    }
}
