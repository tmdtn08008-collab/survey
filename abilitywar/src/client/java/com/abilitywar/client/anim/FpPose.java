package com.abilitywar.client.anim;

/** 1인칭 포즈: 오른팔, 왼팔 [pos px: 오른쪽,위,앞 / rot 도: pitch(위+), yaw(왼쪽+), roll], 카메라 회전 + fov. */
public final class FpPose {
    public final float[][] rot = new float[3][3];
    public final float[][] pos = new float[3][3];
    public final boolean[] visible = {true, true};
    public float fov;

    public void set(FpPose o) {
        for (int b = 0; b < 3; b++) {
            System.arraycopy(o.rot[b], 0, rot[b], 0, 3);
            System.arraycopy(o.pos[b], 0, pos[b], 0, 3);
        }
        visible[0] = o.visible[0];
        visible[1] = o.visible[1];
        fov = o.fov;
    }

    public void blendBone(FpPose o, int b, float w) {
        if (w <= 0) return;
        if (w >= 1) {
            System.arraycopy(o.rot[b], 0, rot[b], 0, 3);
            System.arraycopy(o.pos[b], 0, pos[b], 0, 3);
            if (b < 2) visible[b] = o.visible[b];
            return;
        }
        for (int c = 0; c < 3; c++) {
            rot[b][c] += (o.rot[b][c] - rot[b][c]) * w;
            pos[b][c] += (o.pos[b][c] - pos[b][c]) * w;
        }
        if (b < 2 && w > 0.5f) visible[b] = o.visible[b];
    }

    /** 기본 가드 자세 (클립이 없을 때). */
    public void setDefaultGuard() {
        // 오른팔 (바닐라 1인칭 팔 배치를 기준으로 안쪽으로 모은 가드)
        pos[0][0] = 6.5f; pos[0][1] = -13.5f; pos[0][2] = 7.5f;
        rot[0][0] = 36f; rot[0][1] = 6f; rot[0][2] = 50f;
        // 왼팔
        pos[1][0] = -6.5f; pos[1][1] = -13.5f; pos[1][2] = 6.5f;
        rot[1][0] = 34f; rot[1][1] = -6f; rot[1][2] = -50f;
        rot[2][0] = rot[2][1] = rot[2][2] = 0;
        pos[2][0] = pos[2][1] = pos[2][2] = 0;
        visible[0] = visible[1] = true;
        fov = 0;
    }
}
