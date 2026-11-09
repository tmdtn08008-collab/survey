package com.abilitywar.client.fx.blood;

import com.abilitywar.client.anim.AnimState;
import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.client.fx.dragon.DragonParts;
import com.abilitywar.data.AWPlayer;
import net.minecraft.client.model.ModelPart;
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
import org.joml.Matrix4f;

/** 혈귀의 몸: 핏줄과 붉은 눈, 피의 발톱, (궁극기) 박쥐 날개. */
public final class BloodParts {
    static final Identifier VEINS = Tex.fx("blood_veins_skin");
    static final Identifier GLOW = Tex.fx("blood_glow_skin");
    static final Identifier WING = Tex.fx("blood_wing");
    static final Identifier WING_GLOW = Tex.fx("blood_wing_glow");

    public static boolean isBlood(PlayerEntity p) {
        return "blood".equals(((AWPlayer) p).aw$getAbilityKey());
    }

    static AnimState.Action action(PlayerEntity p) {
        AnimState st = PlayerAnimator.peek(p);
        return st == null ? null : st.current();
    }

    /** 발톱 길이 (평소에도 짧게 나와 있음). */
    public static float claws(PlayerEntity p, float now) {
        AnimState.Action a = action(p);
        float c = 0.55f;
        if (a != null && a.clip.id.startsWith("blood.") && !a.clip.id.equals("blood.swarm")) {
            String id = a.clip.id;
            if (id.startsWith("blood.attack") || id.startsWith("blood.skill") || id.startsWith("blood.ult"))
                c = Math.max(c, 0.55f + 0.45f * DragonParts.env(a.time(now), 0, 2, a.clip.length - 3, a.clip.length));
        }
        return c;
    }

    /** 박쥐 날개: 궁극기 동안 (펼침 정도). */
    public static float wings(PlayerEntity p, float now) {
        AnimState.Action a = action(p);
        if (a == null) return 0;
        float t = a.time(now);
        if (a.clip.id.equals("blood.ult")) return DragonParts.env(t, 10, 18, 64, 72);
        if (a.clip.id.equals("blood.select")) return DragonParts.env(t, 4, 9, 20, 26);
        return 0;
    }

    // ------------------------------------------------------------------ 그리기
    static void renderWings(MatrixStack ms, VertexConsumerProvider vcp, int light, float amount, float now) {
        if (amount <= 0.01f) return;
        float size = 2.1f * (0.3f + 0.7f * amount);
        float flap = 12 * MathHelper.sin(now * 0.28f);
        for (int side = 0; side < 2; side++) {
            boolean left = side == 1;
            float sx = left ? 1 : -1;
            ms.push();
            ms.translate(sx * 1.6f / 16f, 3.0f / 16f, 2.4f / 16f);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10));
            float yaw = (left ? -8 : 8) * (1 - amount);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(left ? -flap - 22 : flap + 22));
            float bend = 20 + flap * 0.3f;
            DragonParts.wingQuad(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(WING)), light, size, 1, 1, 1, amount, !left, bend);
            float gl = 0.9f * amount;
            DragonParts.wingQuad(ms, vcp.getBuffer(RenderLayer.getEyes(WING_GLOW)), light, size, gl, gl, gl, 1, !left, bend);
            ms.pop();
        }
    }

    /** 손 끝에서 뻗는 피의 발톱 3개 (손 공간: 손 방향 +Y, fpForward 면 -Z). */
    static void renderClaws(MatrixStack ms, VertexConsumerProvider vcp, float amount, boolean fpForward, float now) {
        if (amount <= 0.01f) return;
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer solid = vcp.getBuffer(AWRenderLayers.SOLID_COLOR);
        VertexConsumer glow = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float L = 0.34f * amount * (fpForward ? 1.5f : 1f);
        int seg = 6;
        for (int k = -1; k <= 1; k++) {
            float ox = k * 0.07f;
            float len = L * (k == 0 ? 1.12f : 0.9f);
            for (int i = 0; i < seg; i++) {
                float t0 = (float) i / seg, t1 = (float) (i + 1) / seg;
                float[] p0 = pt(t0, len, ox, fpForward), p1 = pt(t1, len, ox, fpForward);
                float w0 = 0.03f * (1 - t0) + 0.003f, w1 = 0.03f * (1 - t1) + 0.003f;
                // 뿌리는 검붉고 끝으로 갈수록 선홍색, 빛은 끝에만 살짝
                float c0 = 0.28f + 0.5f * t0, c1 = 0.28f + 0.5f * t1;
                quad(solid, m, p0, p1, w0, w1, c0, c0 * 0.03f, c0 * 0.06f, c1, c1 * 0.03f, c1 * 0.06f);
                float pulse = 0.75f + 0.25f * MathHelper.sin(now * 0.3f + k);
                float ga0 = amount * 0.3f * t0 * t0 * pulse, ga1 = amount * 0.3f * t1 * t1 * pulse;
                quad(glow, m, p0, p1, w0 * 1.3f, w1 * 1.3f, ga0, ga0 * 0.03f, ga0 * 0.05f, ga1, ga1 * 0.03f, ga1 * 0.05f);
            }
        }
    }

    static float[] pt(float t, float len, float ox, boolean fp) {
        float along = t * len;
        float curve = (float) Math.sin(t * Math.PI * 0.5) * len * 0.45f;
        if (fp) return new float[]{ox, -curve * 0.6f, -along};
        return new float[]{ox, along, -curve};
    }

    static void quad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float wa, float wb, float r0, float g0, float b0, float r1, float g1, float b1) {
        vc.vertex(m, a[0] - wa, a[1], a[2]).color(r0, g0, b0, 1).next();
        vc.vertex(m, a[0] + wa, a[1], a[2]).color(r0, g0, b0, 1).next();
        vc.vertex(m, b[0] + wb, b[1], b[2]).color(r1, g1, b1, 1).next();
        vc.vertex(m, b[0] - wb, b[1], b[2]).color(r1, g1, b1, 1).next();
        // 세로판
        float dx = b[1] - a[1], dz = b[2] - a[2];
        float ly = dz, lz = -dx;
        float len = (float) Math.sqrt(ly * ly + lz * lz);
        if (len < 1e-6f) return;
        ly /= len;
        lz /= len;
        vc.vertex(m, a[0], a[1] - ly * wa, a[2] - lz * wa).color(r0, g0, b0, 1).next();
        vc.vertex(m, a[0], a[1] + ly * wa, a[2] + lz * wa).color(r0, g0, b0, 1).next();
        vc.vertex(m, b[0], b[1] + ly * wb, b[2] + lz * wb).color(r1, g1, b1, 1).next();
        vc.vertex(m, b[0], b[1] - ly * wb, b[2] - lz * wb).color(r1, g1, b1, 1).next();
    }

    // ------------------------------------------------------------------ 1인칭
    public static void fpArmOverlay(MatrixStack ms, VertexConsumerProvider vcp, int light, PlayerEntity p, ModelPart part) {
        if (!isBlood(p)) return;
        float now = ClientClock.now();
        part.render(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(VEINS)), light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 0.85f);
        float pulse = 0.55f + 0.45f * MathHelper.sin(now * 0.18f);
        part.render(ms, vcp.getBuffer(RenderLayer.getEyes(GLOW)), light, OverlayTexture.DEFAULT_UV, pulse, pulse, pulse, 1);
    }

    public static void fpClaws(MatrixStack ms, VertexConsumerProvider vcp, PlayerEntity p, int arm) {
        if (!isBlood(p)) return;
        float now = ClientClock.now();
        ms.push();
        ms.translate(0, 0.02f, -0.7f);
        renderClaws(ms, vcp, claws(p, now), true, now);
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
            if (!isBlood(p) || p.isInvisible()) return;
            float now = ClientClock.now();
            PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
            model.render(ms, vcp.getBuffer(RenderLayer.getEntityTranslucent(VEINS)), light, OverlayTexture.DEFAULT_UV, 1, 1, 1, 0.9f);
            float pulse = 0.6f + 0.4f * MathHelper.sin(now * 0.18f);
            model.render(ms, vcp.getBuffer(RenderLayer.getEyes(GLOW)), light, OverlayTexture.DEFAULT_UV, pulse, pulse, pulse, 1);
            float w = wings(p, now);
            if (w > 0.01f) {
                ms.push();
                model.body.rotate(ms);
                renderWings(ms, vcp, light, w, now);
                ms.pop();
            }
            if (!p.getMainHandStack().isEmpty()) return;
            float cl = claws(p, now);
            for (int arm = 0; arm < 2; arm++) {
                ms.push();
                model.setArmAngle(arm == 0 ? Arm.RIGHT : Arm.LEFT, ms);
                ms.translate(arm == 0 ? -0.0625f : 0.0625f, 0.6f, -0.02f);
                renderClaws(ms, vcp, cl, false, now);
                ms.pop();
            }
        }
    }

    private BloodParts() {
    }
}
