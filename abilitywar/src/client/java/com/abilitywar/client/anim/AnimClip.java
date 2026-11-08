package com.abilitywar.client.anim;

import org.jetbrains.annotations.Nullable;

/** 플레이어 애니메이션 클립 (3인칭 본 + 1인칭 팔/카메라). 시간 단위는 틱. */
public final class AnimClip {
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, ROOT = 6;
    public static final int BONES = 7;
    public static final String[] BONE_NAMES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "root"};
    public static final int FP_RIGHT = 0, FP_LEFT = 1, FP_CAMERA = 2;
    public static final String[] FP_NAMES = {"right_arm", "left_arm", "camera"};

    public final String id;
    public float length = 20;
    public boolean loop;
    public float blendIn = 2;
    public float blendOut = 3;
    public boolean headLock;
    public float rootPivot = 12f;
    public float stride = 9.42f;
    /** 1인칭에서 다른 팔/카메라를 숨길지. */
    public boolean fpHideMissing;

    public final AnimChannel[] rot = new AnimChannel[BONES];
    public final AnimChannel[] pos = new AnimChannel[BONES];
    public final AnimChannel[] fpRot = new AnimChannel[3];
    public final AnimChannel[] fpPos = new AnimChannel[3];
    @Nullable
    public AnimChannel fov;
    /** fp 팔 보이기 여부 채널 (x 값 >0.5 보임). */
    public final AnimChannel[] fpVis = new AnimChannel[2];

    public AnimClip(String id) {
        this.id = id;
    }

    public boolean hasBone(int b) {
        return rot[b] != null || pos[b] != null;
    }

    public boolean hasFp() {
        return fpRot[0] != null || fpPos[0] != null || fpRot[1] != null || fpPos[1] != null || fpRot[2] != null || fov != null;
    }

    public boolean hasFpArm(int arm) {
        return fpRot[arm] != null || fpPos[arm] != null;
    }

    /** 루프/비루프를 고려한 로컬 시간. */
    public float localTime(float t) {
        if (loop && length > 0) {
            t %= length;
            if (t < 0) t += length;
            return t;
        }
        return Math.max(0, Math.min(t, length));
    }
}
