package com.abilitywar.client.anim;

/** 3차원 키프레임 채널 (회전 또는 위치). */
public final class AnimChannel {
    final float[] times;
    final float[][] values;
    final Easing[] easings;

    public AnimChannel(float[] times, float[][] values, Easing[] easings) {
        this.times = times;
        this.values = values;
        this.easings = easings;
    }

    public float length() {
        return times.length == 0 ? 0 : times[times.length - 1];
    }

    public void sample(float t, float[] out) {
        int n = times.length;
        if (n == 0) {
            out[0] = out[1] = out[2] = 0;
            return;
        }
        if (n == 1 || t <= times[0]) {
            copy(values[0], out);
            return;
        }
        if (t >= times[n - 1]) {
            copy(values[n - 1], out);
            return;
        }
        int i = 0;
        while (i < n - 2 && t >= times[i + 1]) i++;
        float t0 = times[i], t1 = times[i + 1];
        float u = t1 - t0 <= 1e-5f ? 1 : (t - t0) / (t1 - t0);
        Easing e = easings[i + 1];
        float k = e.apply(u);
        float[] a = values[i], b = values[i + 1];
        if (e == Easing.SMOOTH) {
            float[] p0 = values[Math.max(0, i - 1)], p3 = values[Math.min(n - 1, i + 2)];
            for (int c = 0; c < 3; c++) out[c] = catmull(p0[c], a[c], b[c], p3[c], k);
        } else {
            for (int c = 0; c < 3; c++) out[c] = a[c] + (b[c] - a[c]) * k;
        }
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private static void copy(float[] src, float[] out) {
        out[0] = src[0];
        out[1] = src[1];
        out[2] = src[2];
    }
}
