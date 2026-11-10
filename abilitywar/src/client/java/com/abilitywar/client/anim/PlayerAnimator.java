package com.abilitywar.client.anim;

import com.abilitywar.AbilityWar;
import com.abilitywar.client.input.InputHandler;
import com.abilitywar.data.AWPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 3인칭/1인칭 플레이어 애니메이션 적용.
 */
public final class PlayerAnimator {
    private static final Map<Integer, AnimState> STATES = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();
    public static boolean renderingFirstPersonArms;

    private static final Pose VANILLA = new Pose();
    private static final Pose OUT = new Pose();
    private static final Pose LOCO = new Pose();
    private static final Pose T1 = new Pose();
    private static final Pose T2 = new Pose();
    private static final Pose T3 = new Pose();
    private static final Pose T4 = new Pose();
    private static final float[] V = new float[3];

    public static AnimState state(Entity e) {
        AnimState st = STATES.computeIfAbsent(e.getId(), k -> new AnimState());
        st.lastSeenTick = ClientClock.ticks();
        return st;
    }

    public static AnimState peek(Entity e) {
        return STATES.get(e.getId());
    }

    public static void play(int entityId, String clipId, float speed) {
        AnimClip c = AnimRegistry.get(clipId);
        if (c == null && clipId.indexOf('.') > 0 && !clipId.startsWith("common.")) {
            // 능력 전용 동작이 없으면 공통 동작 (예: void.dash_left -> common.dash_left)
            c = AnimRegistry.get("common" + clipId.substring(clipId.indexOf('.')));
        }
        if (c == null) {
            if (MISSING.add(clipId)) AbilityWar.LOG.warn("Missing animation clip: {}", clipId);
            return;
        }
        AnimState st = STATES.computeIfAbsent(entityId, k -> new AnimState());
        st.lastSeenTick = ClientClock.ticks();
        st.play(c, ClientClock.now(), speed);
    }

    public static void stop(int entityId) {
        AnimState st = STATES.get(entityId);
        if (st != null) st.stopAll(ClientClock.now());
    }

    public static void clear() {
        STATES.clear();
    }

    public static void tick() {
        long now = ClientClock.ticks();
        STATES.values().removeIf(s -> now - s.lastSeenTick > 400 && s.actions.isEmpty());
    }

    public static String abilityKey(PlayerEntity p) {
        return ((AWPlayer) p).aw$getAbilityKey();
    }

    static boolean locomotionAllowed(PlayerEntity p) {
        return !p.isSleeping() && !p.isFallFlying() && !p.isInSwimmingPose() && !p.hasVehicle() && !p.isUsingRiptide()
                && !(p.isUsingItem() && !p.getActiveItem().isEmpty()) && !p.isSpectator();
    }

    // ------------------------------------------------------------------ weights

    static void updateWeights(AnimState st, PlayerEntity p, float limbDistance, float now) {
        float dt = st.lastUpdate < 0 ? 1 : Math.max(0, Math.min(5, now - st.lastUpdate));
        st.lastUpdate = now;
        float move = Math.min(1f, limbDistance * 1.7f);
        boolean onGround = p.isOnGround() || p.isClimbing() || p.isTouchingWater();
        if (!onGround) {
            if (st.airSince < 0) st.airSince = now;
        } else st.airSince = -1;
        boolean air = st.airSince >= 0 && now - st.airSince > 2.5f;
        boolean blocking = ((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_BLOCKING)
                || (p == MinecraftClient.getInstance().player && InputHandler.isBlockHeld());
        st.moveW = approach(st.moveW, move, dt, 0.45f);
        st.sprintW = approach(st.sprintW, p.isSprinting() ? 1 : 0, dt, 0.3f);
        st.sneakW = approach(st.sneakW, p.isInSneakingPose() ? 1 : 0, dt, 0.5f);
        st.airW = approach(st.airW, air ? 1 : 0, dt, air ? 0.25f : 0.5f);
        st.blockW = approach(st.blockW, blocking ? 1 : 0, dt, 0.55f);
        // 비행 모드 (천사): 공중에 있을 때 전용 비행 자세
        boolean fly = ((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_FLYING) && !p.isOnGround() && !p.isTouchingWater();
        st.flyW = approach(st.flyW, fly ? 1 : 0, dt, fly ? 0.3f : 0.4f);
    }

    static float approach(float cur, float target, float dt, float rate) {
        float k = 1f - (float) Math.exp(-dt * rate * 3f);
        return cur + (target - cur) * k;
    }

    // ------------------------------------------------------------------ eval

    /** 클립을 시간 t 에서 평가 (지정 안 된 본은 기본 자세 + 머리 시선). */
    static void eval(AnimClip c, float t, Pose out, float headYaw, float headPitch) {
        for (int b = 0; b < AnimClip.BONES; b++) {
            if (c.rot[b] != null) {
                c.rot[b].sample(t, V);
                out.rot[b][0] = V[0];
                out.rot[b][1] = V[1];
                out.rot[b][2] = V[2];
            } else {
                out.rot[b][0] = out.rot[b][1] = out.rot[b][2] = 0;
            }
            if (c.pos[b] != null) {
                c.pos[b].sample(t, V);
                out.pos[b][0] = V[0];
                out.pos[b][1] = V[1];
                out.pos[b][2] = V[2];
            } else {
                out.pos[b][0] = out.pos[b][1] = out.pos[b][2] = 0;
            }
        }
        if (!c.headLock) {
            out.rot[AnimClip.HEAD][0] += headPitch;
            out.rot[AnimClip.HEAD][1] += headYaw;
        }
    }

    static AnimClip clip(String key, String name) {
        return AnimRegistry.get(key + "." + name);
    }

    static float strideTime(AnimClip c, float limbAngle) {
        return c.localTime(limbAngle / Math.max(0.01f, c.stride) * c.length);
    }

    /** 이동 애니메이션 혼합. @return 사용 가능 여부 */
    static boolean evalLocomotion(String key, AnimState st, float now, float limbAngle, float headYaw, float headPitch, Pose out) {
        AnimClip idle = clip(key, "idle");
        if (idle == null) return false;
        AnimClip walk = clip(key, "walk");
        AnimClip run = clip(key, "run");
        AnimClip sneak = clip(key, "sneak");
        AnimClip sneakWalk = clip(key, "sneak_walk");
        AnimClip air = clip(key, "air");
        if (run == null) run = walk;
        if (sneak == null) sneak = idle;
        if (sneakWalk == null) sneakWalk = sneak;

        eval(idle, idle.localTime(now), out, headYaw, headPitch);
        if (walk != null && st.moveW > 0.01f) {
            eval(walk, strideTime(walk, limbAngle), T1, headYaw, headPitch);
            if (run != null && run != walk && st.sprintW > 0.01f) {
                eval(run, strideTime(run, limbAngle), T2, headYaw, headPitch);
                blendAll(T1, T2, st.sprintW);
            }
            blendAll(out, T1, st.moveW);
        }
        if (st.sneakW > 0.01f) {
            eval(sneak, sneak.localTime(now), T3, headYaw, headPitch);
            if (st.moveW > 0.01f && sneakWalk != sneak) {
                eval(sneakWalk, strideTime(sneakWalk, limbAngle), T4, headYaw, headPitch);
                blendAll(T3, T4, st.moveW);
            }
            blendAll(out, T3, st.sneakW);
        }
        if (air != null && st.airW > 0.01f) {
            eval(air, air.localTime(now), T1, headYaw, headPitch);
            blendAll(out, T1, st.airW);
        }
        AnimClip fly = clip(key, "fly");
        if (fly != null && st.flyW > 0.01f) {
            eval(fly, fly.localTime(now), T1, headYaw, headPitch);
            AnimClip flyMove = clip(key, "fly_move");
            if (flyMove != null && st.moveW > 0.01f) {
                eval(flyMove, flyMove.localTime(now), T2, headYaw, headPitch);
                blendAll(T1, T2, st.moveW);
            }
            blendAll(out, T1, st.flyW);
        }
        return true;
    }

    static void blendAll(Pose a, Pose b, float w) {
        for (int i = 0; i < AnimClip.BONES; i++) a.blendBone(b, i, w);
    }

    // ------------------------------------------------------------------ apply (3인칭)

    /** 마지막으로 포즈를 계산한 플레이어의 무기(item 본) 회전/위치 — 무기 피처 렌더러가 읽음. */
    public static final float[] ITEM_ROT = new float[3], ITEM_POS = new float[3];
    public static int itemOwner = -1;

    public static void apply(PlayerEntityModel<?> model, AbstractClientPlayerEntity p, float limbAngle, float limbDistance, float headYaw, float headPitch) {
        if (renderingFirstPersonArms) return;
        itemOwner = p.getId();
        ITEM_ROT[0] = ITEM_ROT[1] = ITEM_ROT[2] = 0;
        ITEM_POS[0] = ITEM_POS[1] = ITEM_POS[2] = 0;
        AnimState st = state(p);
        float now = ClientClock.now();
        st.prune(now);
        String key = abilityKey(p);
        updateWeights(st, p, limbDistance, now);
        boolean hasActions = !st.actions.isEmpty();
        boolean loco = !key.isEmpty() && locomotionAllowed(p);
        if (!hasActions && !loco) return;

        VANILLA.capture(model);
        OUT.set(VANILLA);
        if (loco && evalLocomotion(key, st, now, limbAngle, headYaw, headPitch, LOCO)) {
            boolean armsFree = p.getMainHandStack().isEmpty();
            for (int b = 0; b < 6; b++) {
                if (!armsFree && (b == AnimClip.RIGHT_ARM || b == AnimClip.LEFT_ARM)) continue;
                OUT.blendBone(LOCO, b, 1f);
            }
            OUT.blendBone(LOCO, AnimClip.ITEM, 1f);
            if (st.blockW > 0.01f) {
                AnimClip block = clip(key, "block");
                if (block != null) {
                    eval(block, block.localTime(now), T1, headYaw, headPitch);
                    for (int b = 0; b < 6; b++) if (block.hasBone(b)) OUT.blendBone(T1, b, st.blockW);
                    if (block.hasBone(AnimClip.ITEM)) OUT.blendBone(T1, AnimClip.ITEM, st.blockW);
                }
            }
        }
        for (AnimState.Action a : st.actions) {
            float w = a.weight(now);
            if (w <= 0) continue;
            eval(a.clip, a.clip.localTime(a.time(now)), T2, headYaw, headPitch);
            for (int b = 0; b < 6; b++) if (a.clip.hasBone(b)) OUT.blendBone(T2, b, w);
            if (a.clip.hasBone(AnimClip.ITEM)) OUT.blendBone(T2, AnimClip.ITEM, w);
        }
        OUT.applyTo(model);
        System.arraycopy(OUT.rot[AnimClip.ITEM], 0, ITEM_ROT, 0, 3);
        System.arraycopy(OUT.pos[AnimClip.ITEM], 0, ITEM_POS, 0, 3);
        itemOwner = p.getId();
    }

    // ------------------------------------------------------------------ root (setupTransforms)

    public static void applyRoot(AbstractClientPlayerEntity p, MatrixStack matrices, float tickDelta) {
        AnimState st = state(p);
        float now = ClientClock.now();
        String key = abilityKey(p);
        updateWeights(st, p, p.limbAnimator.getSpeed(tickDelta), now);
        float[] rr = st.rootRot, rp = st.rootPos;
        rr[0] = rr[1] = rr[2] = rp[0] = rp[1] = rp[2] = 0;
        float pivot = 12f;
        boolean any = false;
        if (!key.isEmpty() && locomotionAllowed(p)) {
            if (evalLocomotion(key, st, now, p.limbAnimator.getPos(tickDelta), 0, 0, LOCO)) {
                System.arraycopy(LOCO.rot[AnimClip.ROOT], 0, rr, 0, 3);
                System.arraycopy(LOCO.pos[AnimClip.ROOT], 0, rp, 0, 3);
                any = true;
            }
        }
        for (AnimState.Action a : st.actions) {
            float w = a.weight(now);
            if (w <= 0 || !a.clip.hasBone(AnimClip.ROOT)) continue;
            eval(a.clip, a.clip.localTime(a.time(now)), T2, 0, 0);
            for (int c = 0; c < 3; c++) {
                rr[c] += (T2.rot[AnimClip.ROOT][c] - rr[c]) * w;
                rp[c] += (T2.pos[AnimClip.ROOT][c] - rp[c]) * w;
            }
            pivot += (a.clip.rootPivot - pivot) * w;
            any = true;
        }
        if (!any) return;
        if (rr[0] == 0 && rr[1] == 0 && rr[2] == 0 && rp[0] == 0 && rp[1] == 0 && rp[2] == 0) return;
        float py = pivot / 16f;
        matrices.translate(rp[0] / 16f, rp[1] / 16f, -rp[2] / 16f);
        matrices.translate(0, py, 0);
        if (rr[1] != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rr[1]));
        if (rr[0] != 0) matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(rr[0]));
        if (rr[2] != 0) matrices.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(rr[2]));
        matrices.translate(0, -py, 0);
    }

    // ------------------------------------------------------------------ 1인칭

    private static final FpPose FP_OUT = new FpPose();
    private static final FpPose FP_T1 = new FpPose();
    private static final FpPose FP_T2 = new FpPose();
    private static final FpPose FP_T3 = new FpPose();

    static void evalFp(AnimClip c, float t, FpPose out) {
        out.setDefaultGuard();
        for (int b = 0; b < AnimClip.FP_BONES; b++) {
            if (c.fpRot[b] != null) {
                c.fpRot[b].sample(t, V);
                System.arraycopy(V, 0, out.rot[b], 0, 3);
            }
            if (c.fpPos[b] != null) {
                c.fpPos[b].sample(t, V);
                System.arraycopy(V, 0, out.pos[b], 0, 3);
            }
        }
        for (int b = 0; b < 2; b++) {
            if (c.fpVis[b] != null) {
                c.fpVis[b].sample(t, V);
                out.visible[b] = V[0] > 0.5f;
            } else if (c.fpHideMissing && !c.hasFpArm(b)) {
                out.visible[b] = false;
            }
        }
        if (c.fov != null) {
            c.fov.sample(t, V);
            out.fov = V[0];
        }
    }

    static void blendFpAll(FpPose a, FpPose b, float w) {
        for (int i = 0; i < AnimClip.FP_BONES; i++) a.blendBone(b, i, w);
        a.fov += (b.fov - a.fov) * w;
    }

    /** 로컬 플레이어의 1인칭 포즈 계산. */
    public static FpPose computeFirstPerson(AbstractClientPlayerEntity p, float tickDelta) {
        AnimState st = state(p);
        float now = ClientClock.now();
        st.prune(now);
        String key = abilityKey(p);
        float limbAngle = p.limbAnimator.getPos(tickDelta);
        updateWeights(st, p, p.limbAnimator.getSpeed(tickDelta), now);
        FP_OUT.setDefaultGuard();
        AnimClip idle = clip(key, "idle");
        if (idle != null && idle.hasFp()) {
            evalFp(idle, idle.localTime(now), FP_OUT);
            AnimClip walk = clip(key, "walk");
            AnimClip run = clip(key, "run");
            if (walk != null && walk.hasFp() && st.moveW > 0.01f) {
                evalFp(walk, strideTime(walk, limbAngle), FP_T1);
                if (run != null && run.hasFp() && st.sprintW > 0.01f) {
                    evalFp(run, strideTime(run, limbAngle), FP_T2);
                    blendFpAll(FP_T1, FP_T2, st.sprintW);
                }
                blendFpAll(FP_OUT, FP_T1, st.moveW);
            }
            AnimClip sneak = clip(key, "sneak");
            if (sneak != null && sneak.hasFp() && st.sneakW > 0.01f) {
                evalFp(sneak, sneak.localTime(now), FP_T3);
                AnimClip sw = clip(key, "sneak_walk");
                if (sw != null && sw.hasFp() && st.moveW > 0.01f) {
                    evalFp(sw, strideTime(sw, limbAngle), FP_T1);
                    blendFpAll(FP_T3, FP_T1, st.moveW);
                }
                blendFpAll(FP_OUT, FP_T3, st.sneakW);
            }
            AnimClip air = clip(key, "air");
            if (air != null && air.hasFp() && st.airW > 0.01f) {
                evalFp(air, air.localTime(now), FP_T1);
                blendFpAll(FP_OUT, FP_T1, st.airW);
            }
            AnimClip fly = clip(key, "fly");
            if (fly != null && fly.hasFp() && st.flyW > 0.01f) {
                evalFp(fly, fly.localTime(now), FP_T1);
                blendFpAll(FP_OUT, FP_T1, st.flyW);
            }
            AnimClip block = clip(key, "block");
            if (block != null && block.hasFp() && st.blockW > 0.01f) {
                evalFp(block, block.localTime(now), FP_T1);
                blendFpAll(FP_OUT, FP_T1, st.blockW);
            }
        }
        for (AnimState.Action a : st.actions) {
            float w = a.weight(now);
            if (w <= 0 || !a.clip.hasFp()) continue;
            evalFp(a.clip, a.clip.localTime(a.time(now)), FP_T2);
            for (int b = 0; b < 2; b++) {
                if (a.clip.hasFpArm(b) || a.clip.fpHideMissing || a.clip.fpVis[b] != null) FP_OUT.blendBone(FP_T2, b, w);
            }
            if (a.clip.fpRot[2] != null) FP_OUT.blendBone(FP_T2, 2, w);
            if (a.clip.fpRot[AnimClip.FP_ITEM] != null || a.clip.fpPos[AnimClip.FP_ITEM] != null) FP_OUT.blendBone(FP_T2, AnimClip.FP_ITEM, w);
            if (a.clip.fov != null) FP_OUT.fov += (FP_T2.fov - FP_OUT.fov) * w;
        }
        return FP_OUT;
    }

    private PlayerAnimator() {
    }
}
