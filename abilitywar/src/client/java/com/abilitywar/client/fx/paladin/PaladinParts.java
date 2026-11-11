package com.abilitywar.client.fx.paladin;

import com.abilitywar.client.anim.AnimState;
import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.dragon.DragonParts;
import com.abilitywar.data.AWPlayer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** 성기사의 성검: 흰 강철 날 + 금빛 홈(빛남) + 날개 모양 금 손잡이 + 하늘색 보석. 3인칭·1인칭. */
public final class PaladinParts {
    public static boolean isPaladin(PlayerEntity p) {
        return "paladin".equals(((AWPlayer) p).aw$getAbilityKey());
    }

    /** 스킬 중에는 칼날의 빛이 세짐. */
    static float glow(PlayerEntity p, float now) {
        float g = PaladinFx.auraOn(p.getId()) ? 0.6f + 0.15f * MathHelper.sin(now * 0.2f) : 0.3f + 0.1f * MathHelper.sin(now * 0.1f);
        AnimState st = PlayerAnimator.peek(p);
        AnimState.Action a = st == null ? null : st.current();
        if (a != null && a.clip.id.startsWith("paladin.") && !a.clip.id.contains("attack") && !a.clip.id.contains("walk") && !a.clip.id.contains("idle") && !a.clip.id.contains("run"))
            g = Math.max(g, DragonParts.env(a.time(now), 0, 3, a.clip.length - 5, a.clip.length));
        return g;
    }

    /** 칼 공간: 손잡이 끝이 원점, 날은 -Z 쪽. L = 날 길이. */
    public static void renderSword(MatrixStack ms, VertexConsumerProvider vcp, float L, float W, float glow) {
        renderSword(ms, vcp, L, W, glow, new float[]{0.9f, 0.92f, 0.98f}, new float[]{1f, 0.84f, 0.4f}, new float[]{1f, 0.86f, 0.45f}, new float[]{0.55f, 0.85f, 1f});
    }

    /** 색을 바꾼 칼 (보스 대검 등): blade 날, trim 가드·폼멜, glowC 홈 빛, gem 보석. */
    public static void renderSword(MatrixStack ms, VertexConsumerProvider vcp, float L, float W, float glow, float[] blade, float[] trim, float[] glowC, float[] gem) {
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer s = vcp.getBuffer(AWRenderLayers.SOLID_COLOR);
        float T = W * 0.22f;
        // 손잡이 (짙은 남색 가죽) + 금 고리
        box(s, m, -W * 0.13f, W * 0.13f, -T * 1.1f, T * 1.1f, 0.02f, 0.2f, 0.12f, 0.16f, 0.34f);
        box(s, m, -W * 0.16f, W * 0.16f, -T * 1.3f, T * 1.3f, 0.09f, 0.11f, trim[0] * 0.95f, trim[1] * 0.93f, trim[2] * 0.85f);
        // 폼멜 (금 + 보석)
        box(s, m, -W * 0.22f, W * 0.22f, -W * 0.22f, W * 0.22f, 0.2f, 0.27f, trim[0] * 0.95f, trim[1] * 0.95f, trim[2] * 0.9f);
        // 가드: 가로 금 막대 + 양 끝 날개처럼 올라간 장식
        box(s, m, -W * 1.4f, W * 1.4f, -T * 1.6f, T * 1.6f, -0.02f, 0.03f, trim[0], trim[1], trim[2]);
        box(s, m, -W * 1.55f, -W * 1.2f, -T * 1.5f, T * 1.5f, -0.1f, 0.0f, trim[0], trim[1], trim[2]);
        box(s, m, W * 1.2f, W * 1.55f, -T * 1.5f, T * 1.5f, -0.1f, 0.0f, trim[0], trim[1], trim[2]);
        box(s, m, -W * 0.3f, W * 0.3f, -T * 2f, T * 2f, -0.06f, 0.04f, gem[0], gem[1], gem[2]); // 가운데 보석
        // 날: 넓은 몸통 + 끝으로 갈수록 좁아짐 (마디 6개)
        int seg = 6;
        for (int i = 0; i < seg; i++) {
            float z0 = -0.03f - L * i / seg, z1 = -0.03f - L * (i + 1) / seg;
            float k0 = i < seg - 2 ? 1f : (seg - i) / 2.5f, k1 = i + 1 < seg - 2 ? 1f : Math.max(0.05f, (seg - i - 1) / 2.5f);
            float w0 = W * 0.5f * k0, w1 = W * 0.5f * k1;
            taper(s, m, w0, w1, T, z0, z1, blade[0], blade[1], blade[2]);
        }
        // 날 가운데 금빛 홈 + 빛
        VertexConsumer g = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float a = glow;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * (T + 0.002f);
            quadYZ(g, m, x, -W * 0.09f, W * 0.09f, -0.06f, -0.06f - L * 0.8f, glowC[0], glowC[1], glowC[2], a);
        }
        // 날 둘레 은은한 빛
        quadXZ(g, m, 0, -W * 0.95f, W * 0.95f, -0.04f, -0.04f - L, glowC[0], glowC[1], glowC[2], a * 0.25f);
        quadYZ2(g, m, -W * 0.95f, W * 0.95f, -0.04f, -0.04f - L, glowC[0], glowC[1], glowC[2], a * 0.25f);
    }

    static void box(VertexConsumer vc, Matrix4f m, float x0, float x1, float y0, float y1, float z0, float z1, float r, float g, float b) {
        float[][] f = {
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0},
                {x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1},
                {x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1},
                {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0},
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}};
        float[] shade = {0.8f, 0.8f, 0.65f, 0.65f, 1f, 0.55f};
        for (int i = 0; i < 6; i++) {
            float k = shade[i];
            for (int v = 0; v < 4; v++) vc.vertex(m, f[i][v * 3], f[i][v * 3 + 1], f[i][v * 3 + 2]).color(r * k, g * k, b * k, 1).next();
        }
    }

    /** 날 한 마디: 가로 w0→w1, 두께 ±T (가운데가 두꺼운 마름모 단면). */
    static void taper(VertexConsumer vc, Matrix4f m, float w0, float w1, float T, float z0, float z1, float r, float g, float b) {
        // 위아래 두 면씩 (마름모 단면)
        float[] sh = {1f, 0.78f, 0.86f, 0.66f};
        int i = 0;
        for (int sy = -1; sy <= 1; sy += 2) {
            for (int sx = -1; sx <= 1; sx += 2) {
                float k = sh[i++];
                vc.vertex(m, 0, sy * T, z0).color(r * k, g * k, b * k, 1).next();
                vc.vertex(m, sx * w0, 0, z0).color(r * k, g * k, b * k, 1).next();
                vc.vertex(m, sx * w1, 0, z1).color(r * k, g * k, b * k, 1).next();
                vc.vertex(m, 0, sy * T * (w1 / Math.max(0.001f, w0)), z1).color(r * k, g * k, b * k, 1).next();
            }
        }
    }

    static void quadYZ(VertexConsumer vc, Matrix4f m, float x, float y0, float y1, float z0, float z1, float r, float g, float b, float a) {
        vc.vertex(m, x, y0, z0).color(r, g, b, a).next();
        vc.vertex(m, x, y1, z0).color(r, g, b, a).next();
        vc.vertex(m, x, y1, z1).color(r, g, b, 0).next();
        vc.vertex(m, x, y0, z1).color(r, g, b, 0).next();
    }

    static void quadXZ(VertexConsumer vc, Matrix4f m, float y, float x0, float x1, float z0, float z1, float r, float g, float b, float a) {
        vc.vertex(m, x0, y, z0).color(r, g, b, 0).next();
        vc.vertex(m, 0, y, z0).color(r, g, b, a).next();
        vc.vertex(m, 0, y, z1).color(r, g, b, 0).next();
        vc.vertex(m, x0, y, z1).color(r, g, b, 0).next();
        vc.vertex(m, 0, y, z0).color(r, g, b, a).next();
        vc.vertex(m, x1, y, z0).color(r, g, b, 0).next();
        vc.vertex(m, x1, y, z1).color(r, g, b, 0).next();
        vc.vertex(m, 0, y, z1).color(r, g, b, 0).next();
    }

    static void quadYZ2(VertexConsumer vc, Matrix4f m, float y0, float y1, float z0, float z1, float r, float g, float b, float a) {
        vc.vertex(m, 0, y0, z0).color(r, g, b, 0).next();
        vc.vertex(m, 0, 0, z0).color(r, g, b, a).next();
        vc.vertex(m, 0, 0, z1).color(r, g, b, 0).next();
        vc.vertex(m, 0, y0, z1).color(r, g, b, 0).next();
        vc.vertex(m, 0, 0, z0).color(r, g, b, a).next();
        vc.vertex(m, 0, y1, z0).color(r, g, b, 0).next();
        vc.vertex(m, 0, y1, z1).color(r, g, b, 0).next();
        vc.vertex(m, 0, 0, z1).color(r, g, b, 0).next();
    }

    // ------------------------------------------------------------------ 1인칭 (오른손)
    public static void fpSword(MatrixStack ms, VertexConsumerProvider vcp, PlayerEntity p, int arm) {
        if (arm != 0 || !isPaladin(p) || !p.getMainHandStack().isEmpty()) return;
        ms.push();
        ms.translate(0, 0.02f, -0.64f);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(48));
        renderSword(ms, vcp, 0.95f, 0.11f, glow(p, ClientClock.now()));
        ms.pop();
    }

    public static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        public Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (!isPaladin(p) || p.isInvisible() || !p.getMainHandStack().isEmpty()) return;
            ms.push();
            getContextModel().setArmAngle(Arm.RIGHT, ms);
            ms.translate(-0.0625f, 0.62f, -0.02f);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(80));
            renderSword(ms, vcp, 1.0f, 0.12f, glow(p, ClientClock.now()));
            ms.pop();
        }
    }

    private PaladinParts() {
    }
}
