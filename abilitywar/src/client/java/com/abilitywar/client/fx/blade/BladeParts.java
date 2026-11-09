package com.abilitywar.client.fx.blade;

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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 마검사의 마검 (손에 든 검 + 허리의 칼집). 손 기준 각도는 애니메이션의 item 본을 따른다.
 * 검 공간: +Z = 칼끝 방향, +Y = 날(폭) 방향, +X = 두께 방향. 단위 = 블록.
 */
public final class BladeParts {
    static final Identifier STEEL = Tex.fx("blade_steel");
    static final Identifier RUNES = Tex.fx("blade_runes");
    static final Identifier METAL = Tex.fx("blade_metal");
    public static final int VIOLET = 0xB44CFF;

    public static boolean isBlade(PlayerEntity p) {
        return "blade".equals(((AWPlayer) p).aw$getAbilityKey());
    }

    static AnimState.Action action(PlayerEntity p) {
        AnimState st = PlayerAnimator.peek(p);
        return st == null ? null : st.current();
    }

    /** 궁극기 때 검이 거대해지는 배율 (1 = 보통). */
    public static float giant(PlayerEntity p, float now) {
        AnimState.Action a = action(p);
        if (a == null || !a.clip.id.equals("blade.ult")) return 1;
        float t = a.time(now);
        if (t < 18) return 1;
        if (t < 27) {
            float k = (t - 18) / 9f;
            float ob = 1 + 2.70158f * (float) Math.pow(k - 1, 3) + 1.70158f * (float) Math.pow(k - 1, 2);
            return 1 + 8.5f * ob;
        }
        if (t < 56) return 9.5f;
        if (t < 64) return 1 + 8.5f * (1 - (t - 56) / 8f);
        return 1;
    }

    /** 마력 빛 세기. */
    public static float glow(PlayerEntity p, float now) {
        AnimState.Action a = action(p);
        float g = 0.45f + 0.1f * MathHelper.sin(now * 0.15f);
        if (a == null || !a.clip.id.startsWith("blade.")) return g;
        String id = a.clip.id;
        float t = a.time(now);
        if (id.startsWith("blade.skill1") && t < 7) return 0.8f + 0.9f * (t / 7f);
        if (id.startsWith("blade.skill") || id.startsWith("blade.ult") || id.startsWith("blade.phantom")) return 1.2f;
        if (id.startsWith("blade.attack")) return 0.9f;
        return g;
    }

    // ------------------------------------------------------------------ 그리기 (엔티티 정점 형식)
    static void ev(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float r, float g, float b, float a, float u, float v, int light, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, nx, ny, nz).next();
    }

    /**
     * z0→z1 로 이어지는 사각 기둥 (양 끝 단면 크기가 다를 수 있음). 단면: x 폭 tx, y 폭 wy.
     * cy0/cy1 = 단면 중심의 y (휘어진 모양용).
     */
    static void prism(VertexConsumer vc, Matrix4f m, Matrix3f n, float z0, float z1, float tx0, float wy0, float tx1, float wy1, float cy0, float cy1,
                      float r, float g, float b, float a, float u0, float u1, float v0, float v1, int light) {
        float[][] c0 = {{-tx0 / 2, cy0 - wy0 / 2}, {tx0 / 2, cy0 - wy0 / 2}, {tx0 / 2, cy0 + wy0 / 2}, {-tx0 / 2, cy0 + wy0 / 2}};
        float[][] c1 = {{-tx1 / 2, cy1 - wy1 / 2}, {tx1 / 2, cy1 - wy1 / 2}, {tx1 / 2, cy1 + wy1 / 2}, {-tx1 / 2, cy1 + wy1 / 2}};
        float[][] nrm = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int f = 0; f < 4; f++) {
            int i = f, j = (f + 1) % 4;
            // 면 법선: 아래(-y), 오른(+x), 위(+y), 왼(-x)
            float nx = nrm[(f + 1) % 4][0], ny = nrm[(f + 1) % 4][1];
            if (f == 0) { nx = 0; ny = -1; } else if (f == 1) { nx = 1; ny = 0; } else if (f == 2) { nx = 0; ny = 1; } else { nx = -1; ny = 0; }
            ev(vc, m, n, c0[i][0], c0[i][1], z0, r, g, b, a, u0, v0, light, nx, ny, 0);
            ev(vc, m, n, c0[j][0], c0[j][1], z0, r, g, b, a, u1, v0, light, nx, ny, 0);
            ev(vc, m, n, c1[j][0], c1[j][1], z1, r, g, b, a, u1, v1, light, nx, ny, 0);
            ev(vc, m, n, c1[i][0], c1[i][1], z1, r, g, b, a, u0, v1, light, nx, ny, 0);
        }
        // 양 끝 뚜껑
        ev(vc, m, n, c0[0][0], c0[0][1], z0, r, g, b, a, u0, v0, light, 0, 0, -1);
        ev(vc, m, n, c0[3][0], c0[3][1], z0, r, g, b, a, u0, v0, light, 0, 0, -1);
        ev(vc, m, n, c0[2][0], c0[2][1], z0, r, g, b, a, u1, v0, light, 0, 0, -1);
        ev(vc, m, n, c0[1][0], c0[1][1], z0, r, g, b, a, u1, v0, light, 0, 0, -1);
        ev(vc, m, n, c1[0][0], c1[0][1], z1, r, g, b, a, u0, v1, light, 0, 0, 1);
        ev(vc, m, n, c1[1][0], c1[1][1], z1, r, g, b, a, u1, v1, light, 0, 0, 1);
        ev(vc, m, n, c1[2][0], c1[2][1], z1, r, g, b, a, u1, v1, light, 0, 0, 1);
        ev(vc, m, n, c1[3][0], c1[3][1], z1, r, g, b, a, u0, v1, light, 0, 0, 1);
    }

    static void sv(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a, float u, float v) {
        vc.vertex(m, x, y, z).color(r, g, b, a).texture(u, v).next();
    }

    // 검날 단면 (z, 날 폭)
    static final float[][] BLADE = {{0.13f, 0.135f}, {0.5f, 0.128f}, {0.85f, 0.118f}, {1.12f, 0.105f}, {1.36f, 0f}};
    static final float THICK = 0.03f;

    /** 검 하나 (검 공간). glow = 마력 빛, giant = 거대화 중이면 >1 (더 밝게). */
    public static void renderSword(MatrixStack ms, VertexConsumerProvider vcp, int light, float glow, float giant, float time) {
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        boolean big = giant > 1.05f;
        float alpha = big ? 0.82f : 1f;
        RenderLayer steelL = big ? RenderLayer.getEntityTranslucent(STEEL) : RenderLayer.getEntityCutoutNoCull(STEEL);
        RenderLayer metalL = big ? RenderLayer.getEntityTranslucent(METAL) : RenderLayer.getEntityCutoutNoCull(METAL);
        // 검날
        VertexConsumer vc = vcp.getBuffer(steelL);
        for (int i = 0; i < BLADE.length - 1; i++) {
            float z0 = BLADE[i][0], z1 = BLADE[i + 1][0];
            float w0 = BLADE[i][1], w1 = BLADE[i + 1][1];
            float t1 = i == BLADE.length - 2 ? 0.004f : THICK;
            prism(vc, m, n, z0, z1, THICK, w0, t1, Math.max(0.004f, w1), 0, i == BLADE.length - 2 ? w0 * 0.15f : 0, 1, 1, 1, alpha, 0, 1, z0 / 1.4f, z1 / 1.4f, light);
        }
        // 손잡이 / 코등이 / 칼자루 끝
        VertexConsumer mc = vcp.getBuffer(metalL);
        prism(mc, m, n, -0.27f, 0.06f, 0.055f, 0.06f, 0.055f, 0.06f, 0, 0, 0.13f, 0.07f, 0.16f, alpha, 0, 1, 0, 1, light);
        for (float z = -0.22f; z < 0.04f; z += 0.07f) prism(mc, m, n, z, z + 0.018f, 0.062f, 0.068f, 0.062f, 0.068f, 0, 0, 0.32f, 0.18f, 0.4f, alpha, 0, 1, 0, 1, light);
        prism(mc, m, n, 0.06f, 0.13f, 0.075f, 0.36f, 0.07f, 0.4f, 0, 0, 0.2f, 0.14f, 0.26f, alpha, 0, 1, 0, 1, light);
        // 코등이 양 끝의 갈고리 (칼끝 쪽으로 휨)
        for (int s = -1; s <= 1; s += 2) {
            ms.push();
            ms.translate(0, s * 0.19f, 0.1f);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-s * 35));
            prism(mc, ms.peek().getPositionMatrix(), ms.peek().getNormalMatrix(), 0, 0.14f, 0.05f, 0.05f, 0.02f, 0.02f, 0, 0, 0.24f, 0.17f, 0.3f, alpha, 0, 1, 0, 1, light);
            ms.pop();
        }
        prism(mc, m, n, -0.33f, -0.26f, 0.085f, 0.085f, 0.085f, 0.085f, 0, 0, 0.3f, 0.22f, 0.34f, alpha, 0, 1, 0, 1, light);

        // 빛 (가산): 날 가장자리 + 룬 + 보석
        float g = glow * (big ? 1.6f : 1f);
        VertexConsumer rv = vcp.getBuffer(AWRenderLayers.additive(RUNES));
        float scroll = -time * 0.02f;
        for (int i = 0; i < BLADE.length - 1; i++) {
            float z0 = BLADE[i][0], z1 = BLADE[i + 1][0];
            float w0 = BLADE[i][1] * 0.5f + 0.006f, w1 = BLADE[i + 1][1] * 0.5f + 0.006f;
            float x = THICK / 2 + 0.002f;
            for (int sd = -1; sd <= 1; sd += 2) {
                float xx = sd * x;
                sv(rv, m, xx, -w0, z0, g, g, g, 1, 0, z0 / 1.4f + scroll);
                sv(rv, m, xx, w0, z0, g, g, g, 1, 1, z0 / 1.4f + scroll);
                sv(rv, m, xx, w1, z1, g, g, g, 1, 1, z1 / 1.4f + scroll);
                sv(rv, m, xx, -w1, z1, g, g, g, 1, 0, z1 / 1.4f + scroll);
            }
        }
        // 날 끝을 따라 번지는 빛 (조금 큰 판)
        VertexConsumer gv = vcp.getBuffer(AWRenderLayers.additive(Tex.GLOW));
        float gr = 0.7f * g * Fxc.r(VIOLET), gg = 0.7f * g * Fxc.g(VIOLET), gb = 0.7f * g * Fxc.b(VIOLET);
        float h = 0.11f;
        sv(gv, m, 0, -h, 0.12f, gr, gg, gb, 0.6f, 0, 0);
        sv(gv, m, 0, h, 0.12f, gr, gg, gb, 0.6f, 1, 0);
        sv(gv, m, 0, h, 1.42f, gr, gg, gb, 0.6f, 1, 1);
        sv(gv, m, 0, -h, 1.42f, gr, gg, gb, 0.6f, 0, 1);
        // 칼자루 보석
        float pg = 0.9f * g;
        sv(gv, m, 0.05f, -0.06f, -0.36f, pg, pg * 0.5f, pg, 1, 0, 0);
        sv(gv, m, 0.05f, 0.06f, -0.36f, pg, pg * 0.5f, pg, 1, 1, 0);
        sv(gv, m, 0.05f, 0.06f, -0.24f, pg, pg * 0.5f, pg, 1, 1, 1);
        sv(gv, m, 0.05f, -0.06f, -0.24f, pg, pg * 0.5f, pg, 1, 0, 1);
    }

    /** 허리의 칼집 (몸통 공간). */
    static void renderScabbard(MatrixStack ms, VertexConsumerProvider vcp, int light, float time) {
        ms.push();
        ms.translate(0.3f, 0.66f, -0.1f);
        // 칼끝이 뒤-아래로 가게 (모델 공간은 +Y 가 아래)
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-28));
        Matrix4f m = ms.peek().getPositionMatrix();
        Matrix3f n = ms.peek().getNormalMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(METAL));
        prism(vc, m, n, 0, 1.05f, 0.06f, 0.15f, 0.05f, 0.11f, 0, 0, 0.1f, 0.05f, 0.13f, 1, 0, 1, 0, 1, light);
        prism(vc, m, n, -0.02f, 0.08f, 0.075f, 0.17f, 0.075f, 0.17f, 0, 0, 0.3f, 0.22f, 0.36f, 1, 0, 1, 0, 1, light);
        prism(vc, m, n, 0.36f, 0.4f, 0.07f, 0.15f, 0.07f, 0.15f, 0, 0, 0.3f, 0.22f, 0.36f, 1, 0, 1, 0, 1, light);
        prism(vc, m, n, 0.95f, 1.1f, 0.06f, 0.12f, 0.03f, 0.05f, 0, 0, 0.3f, 0.22f, 0.36f, 1, 0, 1, 0, 1, light);
        // 보랏빛 장식줄
        VertexConsumer gv = vcp.getBuffer(AWRenderLayers.additive(Tex.GLOW));
        float a = 0.5f + 0.15f * MathHelper.sin(time * 0.1f);
        for (int sd = -1; sd <= 1; sd += 2) {
            float x = sd * 0.032f;
            sv(gv, m, x, -0.012f, 0.1f, a * 0.7f, a * 0.3f, a, 1, 0, 0);
            sv(gv, m, x, 0.012f, 0.1f, a * 0.7f, a * 0.3f, a, 1, 1, 0);
            sv(gv, m, x, 0.012f, 0.95f, a * 0.7f, a * 0.3f, a, 1, 1, 1);
            sv(gv, m, x, -0.012f, 0.95f, a * 0.7f, a * 0.3f, a, 1, 0, 1);
        }
        ms.pop();
    }

    /** 3인칭: 손 기준 검 자세 (item 본) 적용 후 그리기. 팔 공간에서 호출. */
    static void swordInHand(MatrixStack ms, VertexConsumerProvider vcp, int light, PlayerEntity p, float now) {
        float[] r = PlayerAnimator.itemOwner == p.getId() ? PlayerAnimator.ITEM_ROT : ZERO;
        float[] pos = PlayerAnimator.itemOwner == p.getId() ? PlayerAnimator.ITEM_POS : ZERO;
        ms.push();
        ms.translate(-1 / 16f, 9 / 16f, 0);
        ms.translate(pos[0] / 16f, pos[1] / 16f, pos[2] / 16f);
        // 기본: 팔을 내렸을 때 칼끝이 앞(-Z)
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(r[1]));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(r[0]));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(r[2]));
        float gi = giant(p, now);
        if (gi > 1.01f) ms.scale(gi, gi, gi);
        renderSword(ms, vcp, light, glow(p, now), gi, now);
        ms.pop();
    }

    static final float[] ZERO = new float[3];

    /** 1인칭: 어깨 기준 공간(손 방향 -Z)에서. */
    public static void fpSword(MatrixStack ms, VertexConsumerProvider vcp, int light, PlayerEntity p, float[] rot, float[] pos) {
        if (!isBlade(p)) return;
        float now = ClientClock.now();
        ms.push();
        ms.translate(0, 0.02f, -0.68f);
        ms.translate(pos[0] / 16f, pos[1] / 16f, -pos[2] / 16f);
        // 기본: 칼끝이 위-앞, 날이 앞
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-110));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rot[1]));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rot[0]));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot[2]));
        ms.scale(0.9f, 0.9f, 0.9f);
        renderSword(ms, vcp, light, glow(p, now), 1, now);
        ms.pop();
    }

    /** 색 도우미 (Fx 와 같은 계산). */
    static final class Fxc {
        static float r(int c) {
            return ((c >> 16) & 255) / 255f;
        }

        static float g(int c) {
            return ((c >> 8) & 255) / 255f;
        }

        static float b(int c) {
            return (c & 255) / 255f;
        }
    }

    // ------------------------------------------------------------------ 피처 렌더러
    public static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        public Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity p,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (!isBlade(p) || p.isInvisible()) return;
            float now = ClientClock.now();
            PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
            ms.push();
            model.body.rotate(ms);
            renderScabbard(ms, vcp, light, now);
            ms.pop();
            if (!p.getMainHandStack().isEmpty()) return;
            ms.push();
            model.setArmAngle(Arm.RIGHT, ms);
            swordInHand(ms, vcp, light, p, now);
            ms.pop();
        }
    }

    private BladeParts() {
    }
}
