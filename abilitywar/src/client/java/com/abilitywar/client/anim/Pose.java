package com.abilitywar.client.anim;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/**
 * 3인칭 포즈. rot = ModelPart 각도(도), pos = 기본 피벗에서의 모델공간 오프셋(px).
 * ROOT 는 [오른쪽, 위, 앞] px / [앞으로 숙임, 왼쪽 회전, 오른쪽 기울기] 도.
 */
public final class Pose {
    public static final float RAD = (float) (Math.PI / 180.0);
    public final float[][] rot = new float[AnimClip.BONES][3];
    public final float[][] pos = new float[AnimClip.BONES][3];

    // 기본 피벗 (플레이어 모델)
    static final float[][] DEFAULT_PIVOT = {
            {0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}, {0, 0, 0}, {0, 0, 0}
    };

    public void clear() {
        for (int b = 0; b < AnimClip.BONES; b++) {
            rot[b][0] = rot[b][1] = rot[b][2] = 0;
            pos[b][0] = pos[b][1] = pos[b][2] = 0;
        }
    }

    public void set(Pose o) {
        for (int b = 0; b < AnimClip.BONES; b++) {
            System.arraycopy(o.rot[b], 0, rot[b], 0, 3);
            System.arraycopy(o.pos[b], 0, pos[b], 0, 3);
        }
    }

    public void capture(PlayerEntityModel<?> m) {
        cap(AnimClip.HEAD, m.head);
        cap(AnimClip.BODY, m.body);
        cap(AnimClip.RIGHT_ARM, m.rightArm);
        cap(AnimClip.LEFT_ARM, m.leftArm);
        cap(AnimClip.RIGHT_LEG, m.rightLeg);
        cap(AnimClip.LEFT_LEG, m.leftLeg);
        rot[AnimClip.ROOT][0] = rot[AnimClip.ROOT][1] = rot[AnimClip.ROOT][2] = 0;
        pos[AnimClip.ROOT][0] = pos[AnimClip.ROOT][1] = pos[AnimClip.ROOT][2] = 0;
        rot[AnimClip.ITEM][0] = rot[AnimClip.ITEM][1] = rot[AnimClip.ITEM][2] = 0;
        pos[AnimClip.ITEM][0] = pos[AnimClip.ITEM][1] = pos[AnimClip.ITEM][2] = 0;
    }

    private void cap(int b, ModelPart p) {
        rot[b][0] = p.pitch / RAD;
        rot[b][1] = p.yaw / RAD;
        rot[b][2] = p.roll / RAD;
        pos[b][0] = p.pivotX - DEFAULT_PIVOT[b][0];
        pos[b][1] = p.pivotY - DEFAULT_PIVOT[b][1];
        pos[b][2] = p.pivotZ - DEFAULT_PIVOT[b][2];
    }

    private static final org.joml.Matrix3f RB = new org.joml.Matrix3f();
    private static final org.joml.Matrix3f RA = new org.joml.Matrix3f();
    private static final org.joml.Vector3f TMP = new org.joml.Vector3f();
    private static final org.joml.Vector3f EUL = new org.joml.Vector3f();
    static final float HIP_Y = 12f;

    /**
     * 몸통이 엉덩이를 중심으로 회전하고, 팔/머리가 몸통에 붙어 따라가도록 적용한다.
     * (팔 회전값은 몸통 기준 상대값으로 해석)
     */
    public void applyTo(PlayerEntityModel<?> m) {
        float bp = rot[AnimClip.BODY][0] * RAD, by = rot[AnimClip.BODY][1] * RAD, br = rot[AnimClip.BODY][2] * RAD;
        boolean bodyRot = bp != 0 || by != 0 || br != 0;
        if (!bodyRot) {
            put(AnimClip.HEAD, m.head);
            put(AnimClip.BODY, m.body);
            put(AnimClip.RIGHT_ARM, m.rightArm);
            put(AnimClip.LEFT_ARM, m.leftArm);
        } else {
            RB.rotationZYX(br, by, bp);
            // 몸통 피벗: 엉덩이 + R*(목 - 엉덩이) + 오프셋
            TMP.set(0, -HIP_Y, 0);
            RB.transform(TMP);
            float bx = TMP.x + pos[AnimClip.BODY][0];
            float byy = HIP_Y + TMP.y + pos[AnimClip.BODY][1];
            float bz = TMP.z + pos[AnimClip.BODY][2];
            m.body.pivotX = bx;
            m.body.pivotY = byy;
            m.body.pivotZ = bz;
            m.body.pitch = bp;
            m.body.yaw = by;
            m.body.roll = br;
            attachArm(m.rightArm, AnimClip.RIGHT_ARM, bx, byy, bz);
            attachArm(m.leftArm, AnimClip.LEFT_ARM, bx, byy, bz);
            // 머리: 위치만 몸통을 따라가고 시선 회전은 그대로
            TMP.set(DEFAULT_PIVOT[AnimClip.HEAD][0] + pos[AnimClip.HEAD][0], DEFAULT_PIVOT[AnimClip.HEAD][1] + pos[AnimClip.HEAD][1], DEFAULT_PIVOT[AnimClip.HEAD][2] + pos[AnimClip.HEAD][2]);
            RB.transform(TMP);
            m.head.pivotX = bx + TMP.x;
            m.head.pivotY = byy + TMP.y;
            m.head.pivotZ = bz + TMP.z;
            m.head.pitch = rot[AnimClip.HEAD][0] * RAD + bp * 0.35f;
            m.head.yaw = rot[AnimClip.HEAD][1] * RAD + by * 0.5f;
            m.head.roll = rot[AnimClip.HEAD][2] * RAD + br * 0.5f;
        }
        put(AnimClip.RIGHT_LEG, m.rightLeg);
        put(AnimClip.LEFT_LEG, m.leftLeg);
        m.hat.copyTransform(m.head);
        m.jacket.copyTransform(m.body);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
    }

    private void attachArm(ModelPart p, int b, float bx, float by, float bz) {
        TMP.set(DEFAULT_PIVOT[b][0] + pos[b][0], DEFAULT_PIVOT[b][1] + pos[b][1], DEFAULT_PIVOT[b][2] + pos[b][2]);
        RB.transform(TMP);
        p.pivotX = bx + TMP.x;
        p.pivotY = by + TMP.y;
        p.pivotZ = bz + TMP.z;
        RA.rotationZYX(rot[b][2] * RAD, rot[b][1] * RAD, rot[b][0] * RAD);
        RA.set(new org.joml.Matrix3f(RB).mul(RA));
        RA.getEulerAnglesZYX(EUL);
        p.pitch = EUL.x;
        p.yaw = EUL.y;
        p.roll = EUL.z;
    }

    private void put(int b, ModelPart p) {
        p.pitch = rot[b][0] * RAD;
        p.yaw = rot[b][1] * RAD;
        p.roll = rot[b][2] * RAD;
        p.pivotX = DEFAULT_PIVOT[b][0] + pos[b][0];
        p.pivotY = DEFAULT_PIVOT[b][1] + pos[b][1];
        p.pivotZ = DEFAULT_PIVOT[b][2] + pos[b][2];
    }

    /** this = lerp(this, o, w) for bone b. */
    public void blendBone(Pose o, int b, float w) {
        if (w <= 0) return;
        if (w >= 1) {
            System.arraycopy(o.rot[b], 0, rot[b], 0, 3);
            System.arraycopy(o.pos[b], 0, pos[b], 0, 3);
            return;
        }
        for (int c = 0; c < 3; c++) {
            rot[b][c] += (o.rot[b][c] - rot[b][c]) * w;
            pos[b][c] += (o.pos[b][c] - pos[b][c]) * w;
        }
    }
}
