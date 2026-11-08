package com.abilitywar.client.anim;

import java.util.ArrayList;
import java.util.List;

/** 엔티티 하나의 애니메이션 상태. */
public final class AnimState {
    public static final class Action {
        public final AnimClip clip;
        public final float start;
        public final float speed;
        public float fadeStart = -1;
        public float fadeLen = 1;

        Action(AnimClip clip, float start, float speed) {
            this.clip = clip;
            this.start = start;
            this.speed = speed;
        }

        public float time(float now) {
            return (now - start) * speed;
        }

        public boolean expired(float now) {
            if (fadeStart >= 0 && now - fadeStart >= fadeLen) return true;
            return !clip.loop && time(now) >= clip.length;
        }

        public float weight(float now) {
            float t = time(now);
            float w = 1f;
            if (clip.blendIn > 0) w = Math.min(w, t / clip.blendIn);
            if (!clip.loop && clip.blendOut > 0) w = Math.min(w, (clip.length - t) / clip.blendOut);
            if (fadeStart >= 0) w *= 1f - (now - fadeStart) / fadeLen;
            return Math.max(0, Math.min(1, w));
        }
    }

    public final List<Action> actions = new ArrayList<>(3);
    public float moveW, sprintW, sneakW, airW, blockW;
    public float lastUpdate = -1;
    public int airTicks;
    public long lastSeenTick;
    public int lastAirCheckTick;
    public float airSince = -1;

    // 루트 변환 캐시 (setupTransforms 에서 사용)
    public final float[] rootRot = new float[3];
    public final float[] rootPos = new float[3];
    public float rootPivot = 12f;

    public void play(AnimClip clip, float now, float speed) {
        for (Action a : actions) {
            if (a.fadeStart < 0) {
                a.fadeStart = now;
                a.fadeLen = Math.max(0.5f, clip.blendIn);
            }
        }
        actions.add(new Action(clip, now, speed));
        while (actions.size() > 3) actions.remove(0);
    }

    public void stopAll(float now) {
        for (Action a : actions) {
            if (a.fadeStart < 0) {
                a.fadeStart = now;
                a.fadeLen = 3;
            }
        }
    }

    public void prune(float now) {
        actions.removeIf(a -> a.expired(now));
    }

    public boolean isPlaying(String prefix, float now) {
        for (Action a : actions) {
            if (a.fadeStart < 0 && !a.expired(now) && a.clip.id.startsWith(prefix)) return true;
        }
        return false;
    }

    public Action current() {
        for (int i = actions.size() - 1; i >= 0; i--) {
            if (actions.get(i).fadeStart < 0) return actions.get(i);
        }
        return null;
    }
}
