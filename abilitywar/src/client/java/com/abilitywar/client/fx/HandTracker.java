package com.abilitywar.client.fx;

import com.abilitywar.client.anim.FirstPersonRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/** 렌더링된 플레이어의 주먹 월드 좌표를 기록한다 (이펙트 부착용). */
public final class HandTracker {
    private static final Map<Integer, Vec3d[]> HANDS = new HashMap<>();
    private static final Map<Integer, Long> FRAME = new HashMap<>();
    private static final Matrix4f INV_VIEW = new Matrix4f();
    private static Vec3d camPos = Vec3d.ZERO;
    private static long frame;

    static Matrix4f invView() {
        return INV_VIEW;
    }

    static Vec3d cameraPos() {
        return camPos;
    }

    public static void beginFrame(Matrix4f view, Vec3d cam) {
        INV_VIEW.set(view).invert();
        camPos = cam;
        frame++;
    }

    static void record(Entity e, int arm, MatrixStack ms) {
        Matrix4f m = ms.peek().getPositionMatrix();
        Vector4f v = new Vector4f(0, 0, 0, 1);
        m.transform(v);
        INV_VIEW.transform(v);
        Vec3d[] arr = HANDS.computeIfAbsent(e.getId(), k -> new Vec3d[2]);
        arr[arm] = new Vec3d(v.x + camPos.x, v.y + camPos.y, v.z + camPos.z);
        FRAME.put(e.getId(), frame);
    }

    /** @param arm 0 = 오른손, 1 = 왼손 */
    public static Vec3d hand(Entity e, int arm, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e == mc.player && mc.options.getPerspective().isFirstPerson() && !com.abilitywar.client.camera.CameraController.isCameraOverridden()) {
            Camera cam = mc.gameRenderer.getCamera();
            // 손 렌더 공간(+X 오른쪽, -Z 앞) -> 카메라 회전 공간(+X 왼쪽, +Z 앞)
            Vector3f src = FirstPersonRenderer.FIST_VIEW[arm];
            Vector3f fv = new Vector3f(-src.x, src.y, -src.z);
            fv.rotate(cam.getRotation());
            return cam.getPos().add(fv.x, fv.y, fv.z);
        }
        Long f = FRAME.get(e.getId());
        Vec3d[] arr = HANDS.get(e.getId());
        if (f != null && arr != null && arr[arm] != null && frame - f <= 2) return arr[arm];
        float yaw = (e instanceof net.minecraft.entity.LivingEntity le ? MathHelper.lerp(tickDelta, le.prevBodyYaw, le.bodyYaw) : e.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
        double side = arm == 0 ? -0.38 : 0.38;
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -fz, rz = fx;
        Vec3d base = e.getLerpedPos(tickDelta);
        return base.add(fx * 0.35 - rx * side, e.getHeight() * 0.55, fz * 0.35 - rz * side);
    }

    public static void cleanup() {
        FRAME.entrySet().removeIf(en -> frame - en.getValue() > 200);
        HANDS.keySet().retainAll(FRAME.keySet());
    }

    /** 플레이어 렌더러에 붙는 피처: 주먹 위치 기록만 한다. */
    public static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        public Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            AfterImages.capture(entity, matrices, this.getContextModel());
            for (int arm = 0; arm < 2; arm++) {
                matrices.push();
                this.getContextModel().setArmAngle(arm == 0 ? Arm.RIGHT : Arm.LEFT, matrices);
                matrices.translate(arm == 0 ? -0.0625f : 0.0625f, 0.62f, 0f);
                record(entity, arm, matrices);
                matrices.pop();
            }
        }
    }

    private HandTracker() {
    }
}
