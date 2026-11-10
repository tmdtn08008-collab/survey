package com.abilitywar.client.fx.angel;

import com.abilitywar.client.anim.AnimState;
import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Draw;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.client.fx.cantor.CantorParts;
import com.abilitywar.client.fx.dragon.DragonParts;
import com.abilitywar.data.AWPlayer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** 천사의 몸: 하얀 날개 (걷기 = 접힘, 비행 = 펼침), 머리 위 광륜, 오른손의 빛 구슬. */
public final class AngelParts {
    static final Identifier WING = Tex.fx("angel_wing");
    static final Identifier WING_GLOW = Tex.fx("angel_wing_glow");
    static final float[] HALO = {1f, 0.96f, 0.82f};

    public static boolean isAngel(PlayerEntity p) {
        return "angel".equals(((AWPlayer) p).aw$getAbilityKey());
    }

    public static boolean flying(PlayerEntity p) {
        return ((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_FLYING);
    }

    static AnimState.Action action(PlayerEntity p) {
        AnimState st = PlayerAnimator.peek(p);
        return st == null ? null : st.current();
    }

    /** 스킬을 쓰는 중: 손의 구슬이 커지고 날개가 반쯤 펼쳐짐. */
    static float casting(PlayerEntity p, float now) {
        AnimState.Action a = action(p);
        if (a == null || !a.clip.id.startsWith("angel.")) return 0;
        String id = a.clip.id;
        if (id.startsWith("angel.skill") || id.startsWith("angel.attack"))
            return DragonParts.env(a.time(now), 0, 2, a.clip.length - 3, a.clip.length);
        return 0;
    }

    /** 날개를 펼치는 정도 목표값 (0 = 접힘, 1 = 활짝). */
    static float spreadTarget(PlayerEntity p, float now) {
        float s = flying(p) ? 1 : 0;
        AnimState.Action a = action(p);
        if (a != null && a.clip.id.startsWith("angel.")) {
            float t = a.time(now), L = a.clip.length;
            switch (a.clip.id) {
                case "angel.ult" -> s = Math.max(s, DragonParts.env(t, 2, 10, L - 8, L));
                case "angel.mode", "angel.select" -> s = Math.max(s, 1.15f * DragonParts.env(t, 0, 4, L - 6, L));
                case "angel.skill1", "angel.skill2", "angel.skill3", "angel.skill4" -> s = Math.max(s, 0.6f * DragonParts.env(t, 0, 3, L - 4, L));
                default -> {
                }
            }
        }
        return s;
    }

    /** 엔티티별 날개 상태: [펼침, 마지막 시각, 날갯짓 위상]. */
    static final Map<Integer, float[]> WINGS = new HashMap<>();

    static float[] wingState(PlayerEntity p, float now) {
        float[] s = WINGS.computeIfAbsent(p.getId(), k -> new float[]{flying(p) ? 1 : 0, now, 0});
        float dt = MathHelper.clamp(now - s[1], 0, 5);
        if (dt > 0) {
            s[1] = now;
            float target = spreadTarget(p, now);
            float k = 1 - (float) Math.exp(-dt * 0.28f);
            s[0] += (target - s[0]) * k;
            // 날갯짓 속도: 비행 중 이동하면 빠르게
            double sp = Math.sqrt(MathHelper.square(p.getX() - p.prevX) + MathHelper.square(p.getZ() - p.prevZ));
            boolean air = !p.isOnGround();
            float rate = air && flying(p) ? 0.1f + (float) Math.min(0.08, sp * 0.3) : 0.06f;
            s[2] += dt * rate;
        }
        return s;
    }

    public static void forget(int id) {
        WINGS.remove(id);
    }

    public static void clear() {
        WINGS.clear();
    }

    // ------------------------------------------------------------------ 날개 (몸통 공간: +Y 아래, +Z 뒤, +X 왼쪽)
    static void renderWings(MatrixStack ms, VertexConsumerProvider vcp, int light, PlayerEntity p, float now) {
        float[] st = wingState(p, now);
        float s = MathHelper.clamp(st[0], 0, 1.15f);
        float se = Math.min(1, s);
        boolean air = !p.isOnGround() && flying(p);
        double sp = Math.sqrt(MathHelper.square(p.getX() - p.prevX) + MathHelper.square(p.getZ() - p.prevZ));
        float sweep = air ? (float) Math.min(1, sp * 2.2) : 0;
        float flapAmp = air ? 16 - 6 * sweep : 5;
        float flap = MathHelper.sin(st[2] * 6.2832f) * flapAmp * se + (s > 1 ? (s - 1) * 60 * MathHelper.sin(now * 0.6f) : 0);
        // 접힘(0) → 펼침(1)
        float down = MathHelper.lerp(se, 110, -6) + flap;
        float size = MathHelper.lerp(se, 1.45f, 2.7f);
        float tilt = MathHelper.lerp(se, 14, -10 - 10 * sweep);
        float yaw = MathHelper.lerp(se, 4, 4 + 32 * sweep);
        float bend = MathHelper.lerp(se, 8, 18);
        float spread = MathHelper.lerp(se, 1.9f, 1.6f);
        float ry = MathHelper.lerp(se, 0.2f, 2.2f), rz = MathHelper.lerp(se, 2.8f, 2.5f);
        // 빛 테두리를 먼저 (깊이를 쓰지 않음) 그리고 그 위에 날개: 같은 면에 두 번 그리면 줄무늬(z-fighting)가 생김.
        // 두 날개가 겹치는 가운데도 같은 면이 되지 않게 좌우를 앞뒤로 살짝 띄움.
        float gl = 0.35f + 0.45f * se;
        for (int pass = 0; pass < 2; pass++) {
            for (int side = 0; side < 2; side++) {
                boolean left = side == 1;
                float sx = left ? 1 : -1;
                ms.push();
                ms.translate(sx * spread / 16f, ry / 16f, (rz + (left ? 0.25f : 0f)) / 16f);
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tilt));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(left ? -yaw : yaw));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(left ? down : -down));
                if (pass == 0) DragonParts.wingQuad(ms, vcp.getBuffer(RenderLayer.getEyes(WING_GLOW)), light, size * 1.01f, gl, gl, gl, 1, !left, bend);
                else DragonParts.wingQuad(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(WING)), light, size, 1, 1, 1, 1, !left, bend);
                ms.pop();
            }
        }
    }

    // ------------------------------------------------------------------ 빛 구슬 (손 공간)
    static void orb(MatrixStack ms, VertexConsumerProvider vcp, float now, float size) {
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer g = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float pulse = 0.85f + 0.15f * MathHelper.sin(now * 0.35f);
        float s = size * pulse;
        Draw.sphereColor(g, m, Vec3d.ZERO, s, 6, 10, 1f, 0.98f, 0.85f, 0.9f);
        Draw.sphereColor(g, m, Vec3d.ZERO, s * 1.8f, 6, 10, 0.5f, 0.6f, 0.45f, 0.25f);
        Draw.sphereColor(g, m, Vec3d.ZERO, s * 2.8f, 6, 10, 0.25f, 0.32f, 0.3f, 0.12f);
        // 도는 작은 빛 고리
        int seg = 20;
        float rr = s * 2.2f, w = s * 0.18f;
        float tilt = now * 0.05f;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg + now * 0.12, a1 = (i + 1) * Math.PI * 2 / seg + now * 0.12;
            float x0 = (float) Math.cos(a0) * rr, z0 = (float) Math.sin(a0) * rr, x1 = (float) Math.cos(a1) * rr, z1 = (float) Math.sin(a1) * rr;
            float y0 = MathHelper.sin(tilt) * x0 * 0.4f, y1 = MathHelper.sin(tilt) * x1 * 0.4f;
            float al = 0.7f * (0.5f + 0.5f * (float) Math.sin(a0 * 3));
            g.vertex(m, x0, y0 - w, z0).color(1f, 0.95f, 0.7f, al).next();
            g.vertex(m, x1, y1 - w, z1).color(1f, 0.95f, 0.7f, al).next();
            g.vertex(m, x1, y1 + w, z1).color(1f, 0.95f, 0.7f, al).next();
            g.vertex(m, x0, y0 + w, z0).color(1f, 0.95f, 0.7f, al).next();
        }
    }

    // ------------------------------------------------------------------ 1인칭: 손의 빛 구슬
    public static void fpOrb(MatrixStack ms, VertexConsumerProvider vcp, PlayerEntity p, int arm) {
        if (!isAngel(p) || arm != 0 || !p.getMainHandStack().isEmpty()) return;
        float now = ClientClock.now();
        ms.push();
        ms.translate(0, 0.1f, -0.78f);
        orb(ms, vcp, now, 0.05f + 0.035f * casting(p, now));
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
            if (!isAngel(p) || p.isInvisible()) return;
            float now = ClientClock.now();
            PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
            float cast = casting(p, now);
            // 광륜
            ms.push();
            model.head.rotate(ms);
            CantorParts.renderHalo(ms, vcp, HALO, now, cast * 0.5f);
            ms.pop();
            // 날개
            ms.push();
            model.body.rotate(ms);
            renderWings(ms, vcp, light, p, now);
            ms.pop();
            // 빛 구슬 (맨손)
            if (!p.getMainHandStack().isEmpty()) return;
            ms.push();
            model.setArmAngle(Arm.RIGHT, ms);
            ms.translate(-0.0625f, 0.74f, 0);
            orb(ms, vcp, now, 0.05f + 0.04f * cast);
            ms.pop();
        }
    }

    private AngelParts() {
    }
}
