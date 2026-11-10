package com.abilitywar.client.fx.guardian;

import com.abilitywar.client.anim.AnimState;
import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.ClassFx;
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

/** 수호기사의 몸: 왼팔의 커다란 방패 (강철 + 금 테두리 + 문장). 수호 태세/스킬 때 문장이 빛남. */
public final class GuardianParts {
    public static boolean isGuardian(PlayerEntity p) {
        return "guardian".equals(((AWPlayer) p).aw$getAbilityKey());
    }

    static AnimState.Action action(PlayerEntity p) {
        AnimState st = PlayerAnimator.peek(p);
        return st == null ? null : st.current();
    }

    /** 문장 빛 세기: 수호 태세 = 은은하게, 스킬/궁극기 동작 중 = 밝게. */
    static float glow(PlayerEntity p, float now) {
        float g = ClassFx.inStance(p) ? 0.55f + 0.15f * MathHelper.sin(now * 0.15f) : 0.12f;
        AnimState.Action a = action(p);
        if (a != null && a.clip.id.startsWith("guardian.") && !a.clip.id.contains("attack"))
            g = Math.max(g, DragonParts.env(a.time(now), 0, 3, a.clip.length - 5, a.clip.length));
        return g;
    }

    static float half(float v) {
        // 위 45% 는 곧게, 아래는 둥글게 좁아져 뾰족
        return v < 0.45f ? 1f : (float) Math.sqrt(Math.max(0, 1 - (v - 0.45f) / 0.55f));
    }

    /**
     * 방패 공간: 원점 = 방패 중심, 앞면이 +X, 가로 = Z, 세로 = Y (+Y 아래, 모델 공간과 같음).
     * W = 반폭, H = 전체 높이, T = 반두께.
     */
    static void renderShield(MatrixStack ms, VertexConsumerProvider vcp, float W, float H, float T, float glow) {
        Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer solid = vcp.getBuffer(AWRenderLayers.SOLID_COLOR);
        int rows = 10;
        float rim = W * 0.13f;
        for (int i = 0; i < rows; i++) {
            float v0 = (float) i / rows, v1 = (float) (i + 1) / rows;
            float y0 = -H / 2 + H * v0, y1 = -H / 2 + H * v1;
            float h0 = half(v0) * W, h1 = half(v1) * W;
            float g0 = Math.max(0, h0 - rim), g1 = Math.max(0, h1 - rim);
            // 앞면: 강철 (가운데가 약간 볼록하게 밝음)
            float sh = 0.85f + 0.15f * (1 - Math.abs(v0 - 0.4f));
            face(solid, m, T, y0, y1, -g0, g0, -g1, g1, 0.42f * sh, 0.5f * sh, 0.62f * sh);
            // 앞면 금 테두리
            face(solid, m, T + 0.001f, y0, y1, -h0, -g0, -h1, -g1, 0.95f, 0.76f, 0.32f);
            face(solid, m, T + 0.001f, y0, y1, g0, h0, g1, h1, 0.95f, 0.76f, 0.32f);
            // 뒷면 (가죽)
            face(solid, m, -T, y0, y1, -h0, h0, -h1, h1, 0.3f, 0.2f, 0.12f);
            // 옆면 (두께)
            side(solid, m, T, -h0, y0, -h1, y1, 0.8f, 0.62f, 0.25f);
            side(solid, m, T, h0, y0, h1, y1, 0.8f, 0.62f, 0.25f);
        }
        // 위 테두리 (두꺼운 금띠)
        float top = -H / 2;
        box(solid, m, -T - 0.004f, T + 0.012f, top - 0.012f, top + rim * 0.9f, -W - 0.004f, W + 0.004f, 0.98f, 0.8f, 0.36f);
        // 문장: 금 십자 + 가운데 보석
        float ex = T + 0.004f, cy = -H / 2 + H * 0.4f;
        box(solid, m, ex - 0.002f, ex + 0.018f, cy - H * 0.26f, cy + H * 0.3f, -W * 0.09f, W * 0.09f, 1f, 0.84f, 0.42f);
        box(solid, m, ex - 0.002f, ex + 0.018f, cy - H * 0.1f, cy - H * 0.02f, -W * 0.55f, W * 0.55f, 1f, 0.84f, 0.42f);
        box(solid, m, ex + 0.01f, ex + 0.03f, cy - H * 0.09f, cy - H * 0.03f, -W * 0.12f, W * 0.12f, 0.55f, 0.85f, 1f);
        // 문장 빛
        VertexConsumer gl = vcp.getBuffer(AWRenderLayers.ADDITIVE_COLOR);
        float a = glow;
        if (a > 0.02f) {
            float gx = ex + 0.035f;
            glowQuad(gl, m, gx, cy - H * 0.06f, 0, W * (0.7f + 0.5f * a), 1f, 0.85f, 0.45f, a * 0.8f);
            glowQuad(gl, m, gx + 0.005f, cy - H * 0.06f, 0, W * 0.3f, 1f, 1f, 1f, a * 0.6f);
            // 테두리를 따라 흐르는 빛
            for (int i = 0; i < rows; i++) {
                float v0 = (float) i / rows, v1 = (float) (i + 1) / rows;
                float y0 = -H / 2 + H * v0, y1 = -H / 2 + H * v1;
                float h0 = half(v0) * W, h1 = half(v1) * W;
                float g0 = Math.max(0, h0 - rim), g1 = Math.max(0, h1 - rim);
                float fa = a * 0.5f;
                faceA(gl, m, T + 0.006f, y0, y1, -h0, -g0, -h1, -g1, 1f, 0.82f, 0.4f, fa);
                faceA(gl, m, T + 0.006f, y0, y1, g0, h0, g1, h1, 1f, 0.82f, 0.4f, fa);
            }
        }
    }

    static void face(VertexConsumer vc, Matrix4f m, float x, float y0, float y1, float za0, float zb0, float za1, float zb1, float r, float g, float b) {
        vc.vertex(m, x, y0, za0).color(r, g, b, 1).next();
        vc.vertex(m, x, y0, zb0).color(r, g, b, 1).next();
        vc.vertex(m, x, y1, zb1).color(r, g, b, 1).next();
        vc.vertex(m, x, y1, za1).color(r, g, b, 1).next();
    }

    static void faceA(VertexConsumer vc, Matrix4f m, float x, float y0, float y1, float za0, float zb0, float za1, float zb1, float r, float g, float b, float a) {
        vc.vertex(m, x, y0, za0).color(r, g, b, a).next();
        vc.vertex(m, x, y0, zb0).color(r, g, b, a).next();
        vc.vertex(m, x, y1, zb1).color(r, g, b, a).next();
        vc.vertex(m, x, y1, za1).color(r, g, b, a).next();
    }

    static void side(VertexConsumer vc, Matrix4f m, float T, float z0, float y0, float z1, float y1, float r, float g, float b) {
        vc.vertex(m, -T, y0, z0).color(r, g, b, 1).next();
        vc.vertex(m, T, y0, z0).color(r, g, b, 1).next();
        vc.vertex(m, T, y1, z1).color(r, g, b, 1).next();
        vc.vertex(m, -T, y1, z1).color(r, g, b, 1).next();
    }

    static void glowQuad(VertexConsumer vc, Matrix4f m, float x, float y, float z, float s, float r, float g, float b, float a) {
        vc.vertex(m, x, y - s, z).color(r, g, b, 0).next();
        vc.vertex(m, x, y, z - s).color(r, g, b, a).next();
        vc.vertex(m, x, y + s, z).color(r, g, b, 0).next();
        vc.vertex(m, x, y, z + s).color(r, g, b, a).next();
    }

    static void box(VertexConsumer vc, Matrix4f m, float x0, float x1, float y0, float y1, float z0, float z1, float r, float g, float b) {
        float[][] f = {
                {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1},
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0},
                {x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1},
                {x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0},
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}};
        float[] shade = {1f, 0.6f, 0.8f, 0.8f, 0.95f, 0.55f};
        for (int i = 0; i < 6; i++) {
            float k = shade[i];
            for (int v = 0; v < 4; v++) vc.vertex(m, f[i][v * 3], f[i][v * 3 + 1], f[i][v * 3 + 2]).color(r * k, g * k, b * k, 1).next();
        }
    }

    // ------------------------------------------------------------------ 1인칭 (왼손에 붙여 화면 기준으로)
    /** 1인칭: 왼 주먹 위치(손 렌더 공간 = 카메라 기준)에 방패를 세워 화면 왼쪽 아래에 앞면이 보이게. */
    public static void fpShield(VertexConsumerProvider vcp, PlayerEntity p, org.joml.Vector3f fist) {
        if (!isGuardian(p) || !p.getOffHandStack().isEmpty()) return;
        float now = ClientClock.now();
        MatrixStack ms = new MatrixStack();
        ms.translate(fist.x - 0.07f, fist.y + 0.13f, fist.z + 0.1f);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-8));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(24));
        // 방패 공간 → 화면: 앞면(+X) 이 카메라 쪽(+Z), 방패 아래(+Y) 가 화면 아래
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
        renderShield(ms, vcp, 0.2f, 0.56f, 0.022f, glow(p, now));
    }

    // ------------------------------------------------------------------ 피처 렌더러
    public static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        public Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (!isGuardian(p) || p.isInvisible() || !p.getOffHandStack().isEmpty()) return;
            float now = ClientClock.now();
            ms.push();
            getContextModel().setArmAngle(Arm.LEFT, ms);
            // 왼팔 바깥쪽 (+X), 팔뚝 가운데. 앞을 향해 살짝 돌림
            ms.translate(0.2f, 0.42f, -0.02f);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-22));
            renderShield(ms, vcp, 0.34f, 0.98f, 0.035f, glow(p, now));
            ms.pop();
        }
    }

    private GuardianParts() {
    }
}
