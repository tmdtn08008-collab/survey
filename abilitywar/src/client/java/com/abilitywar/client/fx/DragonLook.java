package com.abilitywar.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * 엔더 드래곤을 더 "용" 답게:
 * <ul>
 *   <li>모델에 뿔 (뒤로 휘는 큰 뿔 · 이마 뿔 · 코뿔 · 턱 가시 · 볼 가시), 목 · 꼬리 마디마다 등가시 · 옆가시,
 *       등의 큰 가시판, 어깨 · 엉덩이 가시, 날개 관절 발톱 · 날개 끝 뼈를 덧붙임 (바닐라 텍스처를 그대로 씀 — 셰이더와도 잘 맞음).</li>
 *   <li>콧구멍에서 용의 숨결 연기, 눈가의 보랏빛, 날개 끝 · 꼬리 끝의 포털 가루, 체력이 낮으면 몸에서 영혼 불꽃.</li>
 * </ul>
 */
public final class DragonLook {
    /** EnderDragonEntityRenderer.getTexturedModelData 에서 부름 (EnderDragonRendererMixin). 모델 단위: -Y 위, -Z 앞. */
    public static void addParts(ModelPartData root) {
        ModelPartData head = root.getChild("head");
        for (int side : new int[]{1, -1}) {
            String s = side > 0 ? "l" : "r";
            // 뒤로 휘며 솟는 큰 뿔 (뼈 색, 세 마디): 뿌리 → 위로 솟고 → 끝이 뒤로 휨
            ModelPartData h = horn(head, "aw_horn_" + s, 5.5f * side, -7, 1, -1.5708f + 0.4f * side, -0.75f, 14, 4);
            h = horn(h, "aw_horn2", 14, 0, 0, 0.05f * side, 0.4f, 11, 3);
            horn(h, "aw_horn3", 11, 0, 0, 0.05f * side, 0.45f, 8, 2);
            // 이마 뿔 (짧고 곧게)
            horn(head, "aw_brow_" + s, 6.5f * side, -7, -6, -1.5708f + 0.25f * side, -1.0f, 8, 2);
            // 볼 가시 (세 개, 뒤로 갈수록 짧게)
            for (int k = 0; k < 3; k++)
                horn(head, "aw_cheek_" + s + k, 8f * side, -3 + k * 3, -6 + k * 3, -1.5708f + (0.95f + k * 0.12f) * side, -0.15f, 6 - k, 1);
            // 턱 가시
            horn(head.getChild("jaw"), "aw_chin_" + s, 5f * side, 4, -3, -1.5708f + 0.35f * side, 0.55f, 7, 2);
        }
        // 코뿔
        horn(head, "aw_nose", 0, -1, -19, -1.5708f, -1.15f, 6, 2);
        horn(head, "aw_nose2", 0, -1, -14, -1.5708f, -1.25f, 4, 1);
        // 목 (꼬리도 같은 마디를 씀): 등가시 끝을 더 길게 + 옆가시
        ModelPartData neck = root.getChild("neck");
        neck.addChild("aw_spine", ModelPartBuilder.create().uv(48, 0).cuboid(-0.5f, -4f, -1.5f, 1, 4, 3), ModelTransform.of(0, -9, 0.5f, -0.3f, 0, 0));
        for (int side : new int[]{1, -1})
            neck.addChild("aw_side_" + (side > 0 ? "l" : "r"), ModelPartBuilder.create().uv(48, 0).cuboid(-0.5f, -0.5f, 0, 1, 1, 5),
                    ModelTransform.of(4.5f * side, -4, 0, 0.85f, 0.7f * side, 0));
        // 등: 큰 가시판 (바닐라 가시 위로 더 높게) + 어깨 · 엉덩이 가시
        ModelPartData body = root.getChild("body");
        for (int z : new int[]{-10, 10, 30})
            body.addChild("aw_plate" + (z + 10), ModelPartBuilder.create().uv(220, 53).cuboid(-0.75f, -7f, -4f, 1.5f, 7, 8),
                    ModelTransform.of(0, -6, z + 6, -0.25f, 0, 0));
        for (int side : new int[]{1, -1}) {
            String s = side > 0 ? "l" : "r";
            body.addChild("aw_shoulder_" + s, ModelPartBuilder.create().uv(220, 53).cuboid(-0.75f, -0.75f, 0, 1.5f, 1.5f, 9),
                    ModelTransform.of(11f * side, 2, -12, 0.75f, 1.0f * side, 0));
            body.addChild("aw_hip_" + s, ModelPartBuilder.create().uv(220, 53).cuboid(-0.75f, -0.75f, 0, 1.5f, 1.5f, 8),
                    ModelTransform.of(11f * side, 2, 34, 0.7f, 0.9f * side, 0));
        }
        // 날개: 관절 발톱 (앞으로) + 날개 끝 뼈
        for (int side : new int[]{1, -1}) {
            String s = side > 0 ? "left" : "right";
            ModelPartData wing = root.getChild(s + "_wing");
            wing.addChild("aw_claw", ModelPartBuilder.create().uv(112, 136).cuboid(-1f, -1f, -7f, 2, 2, 7), ModelTransform.of(56f * side, -2, -3, 0.35f, 0, 0));
            wing.addChild("aw_claw2", ModelPartBuilder.create().uv(112, 136).cuboid(-0.5f, -0.5f, -5f, 1, 1, 5), ModelTransform.of(52f * side, -2, -3, 0.15f, -0.3f * side, 0));
            ModelPartData tip = wing.getChild(s + "_wing_tip");
            tip.addChild("aw_tipbone", ModelPartBuilder.create().uv(112, 136).cuboid(side > 0 ? 0 : -10f, -1f, -1f, 10, 2, 2), ModelTransform.of(56f * side, 0, 0, 0, 0.12f * side, 0));
        }
    }

    /**
     * 뼈 색 뿔 한 마디. 날개 뼈 텍스처 (u112 v136, 가로로 긴 띠) 를 쓰려고 상자를 X 방향으로 길게 만들고,
     * 안쪽 마디에서 Z 축으로 기울인 (tilt, 음수 = 위로) 뒤 바깥 마디에서 Y 축으로 돌림 (yaw, -π/2 = 뒤쪽).
     * 다음 마디는 돌려준 부분의 (len, 0, 0) 에 붙임.
     */
    static ModelPartData horn(ModelPartData parent, String name, float x, float y, float z, float yaw, float tilt, float len, float thick) {
        ModelPartData turn = parent.addChild(name, ModelPartBuilder.create(), ModelTransform.of(x, y, z, 0, yaw, 0));
        return turn.addChild("bone", ModelPartBuilder.create().uv(112, 136).cuboid(0, -thick / 2, -thick / 2, len, thick, thick), ModelTransform.of(0, 0, 0, 0, 0, tilt));
    }

    // ------------------------------------------------------------------ 파티클

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.isPaused()) return;
        Random r = mc.world.random;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderDragonEntity d) || !d.isAlive() || d.squaredDistanceTo(mc.player) > 180 * 180 || d.isInvisible()) continue;
            EnderDragonPart[] parts = d.getBodyParts();
            if (parts.length < 8) continue;
            Vec3d head = parts[0].getBoundingBox().getCenter(), neck = parts[1].getBoundingBox().getCenter();
            Vec3d dir = head.subtract(neck);
            dir = dir.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : dir.normalize();
            // 콧구멍 연기 (숨 쉬듯 2초마다 짙게)
            boolean exhale = (d.age % 40) < 10;
            Vec3d nose = head.add(dir.multiply(1.6)).add(0, 0.2, 0);
            for (int i = 0; i < (exhale ? 3 : 1); i++)
                mc.world.addParticle(ParticleTypes.DRAGON_BREATH, nose.x + r.nextGaussian() * 0.15, nose.y + r.nextGaussian() * 0.1, nose.z + r.nextGaussian() * 0.15,
                        dir.x * 0.08 + r.nextGaussian() * 0.01, 0.02, dir.z * 0.08 + r.nextGaussian() * 0.01);
            // 눈가 보랏빛
            if (r.nextInt(3) == 0) {
                Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0)).normalize().multiply(0.9 * (r.nextBoolean() ? 1 : -1));
                Vec3d eye = head.add(dir.multiply(0.4)).add(side).add(0, 0.55, 0);
                mc.world.addParticle(ParticleTypes.WITCH, eye.x, eye.y, eye.z, 0, 0.01, 0);
            }
            // 날개 끝 · 꼬리 끝
            for (int w : new int[]{6, 7}) {
                Vec3d wc = parts[w].getBoundingBox().getCenter();
                Vec3d out = wc.subtract(parts[2].getBoundingBox().getCenter()).multiply(1, 0, 1);
                if (out.lengthSquared() > 1e-4) wc = wc.add(out.normalize().multiply(3.5));
                if (r.nextInt(2) == 0) mc.world.addParticle(ParticleTypes.PORTAL, wc.x, wc.y, wc.z, r.nextGaussian() * 0.2, -0.1, r.nextGaussian() * 0.2);
            }
            Vec3d tail = parts[5].getBoundingBox().getCenter();
            if (r.nextInt(2) == 0) mc.world.addParticle(ParticleTypes.REVERSE_PORTAL, tail.x, tail.y, tail.z, r.nextGaussian() * 0.03, 0.02, r.nextGaussian() * 0.03);
            // 체력이 낮으면 몸의 갈라진 틈에서 영혼 불꽃
            float hp = d.getHealth() / Math.max(1, d.getMaxHealth());
            if (hp < 0.35f && r.nextFloat() < (0.35f - hp) * 3f) {
                Vec3d b = parts[2].getBoundingBox().getCenter().add(r.nextGaussian() * 1.5, r.nextGaussian() * 0.6, r.nextGaussian() * 1.5);
                mc.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, b.x, b.y, b.z, 0, 0.05, 0);
                if (r.nextInt(3) == 0) mc.world.addParticle(ParticleTypes.LARGE_SMOKE, b.x, b.y, b.z, 0, 0.03, 0);
            }
        }
    }

    private DragonLook() {
    }
}
