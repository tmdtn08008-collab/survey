package com.abilitywar.client.camera;

import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.anim.FirstPersonRenderer;
import com.abilitywar.client.anim.FpPose;
import com.abilitywar.client.fx.PostEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/** 화면 흔들림, 히트스톱, 1인칭 카메라 애니메이션, 궁극기 시네마틱 카메라. */
public final class CameraController {
    private static float trauma;
    private static float traumaDecay = 0.05f;
    private static float fovPunch;

    // ------------------------------------------------------------- 흔들림
    public static void addShake(float intensity, int duration) {
        trauma = Math.min(1.6f, trauma + intensity);
        traumaDecay = Math.max(0.02f, Math.min(trauma / Math.max(1, duration), 0.2f));
    }

    public static void hitstop(float amount) {
        ClientClock.hitstop(amount);
        fovPunch = Math.min(1f, fovPunch + amount * 0.15f);
    }

    public static void tick() {
        trauma = Math.max(0, trauma - traumaDecay);
        fovPunch = Math.max(0, fovPunch - 0.12f);
        if (cin != null) {
            cin.age++;
            if (cin.age > cin.duration + 1) cin = null;
        }
    }

    static float noise(float t, float seed) {
        return (float) (Math.sin(t * 1.7 + seed) * 0.5 + Math.sin(t * 3.1 + seed * 2.3) * 0.3 + Math.sin(t * 5.3 + seed * 0.7) * 0.2);
    }

    public static void applyViewTransform(MatrixStack matrices, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        float t = mc.player.age + tickDelta;
        float pitch = 0, yaw = 0, roll = 0;
        float s = trauma * trauma;
        if (s > 0) {
            float amp = s * 4.5f;
            pitch += amp * noise(t * 2.2f, 1);
            yaw += amp * noise(t * 2.2f, 7);
            roll += amp * 1.4f * noise(t * 2.2f, 13);
        }
        if (mc.options.getPerspective().isFirstPerson() && !isCinematic()) {
            FpPose p = FirstPersonRenderer.lastPose;
            if (p != null && FirstPersonRenderer.shouldTakeOver(mc.player)) {
                pitch += p.rot[2][0];
                yaw += p.rot[2][1];
                roll += p.rot[2][2];
            }
        }
        float c = PostEffects.concussion();
        if (c > 0) {
            roll += c * 3.5f * (float) Math.sin(t * 0.09);
            pitch += c * 1.5f * (float) Math.sin(t * 0.13 + 1);
        }
        if (roll != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        if (pitch != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        if (yaw != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
    }

    public static double modifyFov(double fov, float tickDelta, boolean world) {
        if (cin != null || debugOrbit != null) {
            Shot s = currentShot(tickDelta);
            if (s != null) return s.fov;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        double f = fov;
        if (world) {
            FpPose p = FirstPersonRenderer.lastPose;
            if (p != null && mc.player != null && mc.options.getPerspective().isFirstPerson() && FirstPersonRenderer.shouldTakeOver(mc.player)) f += p.fov;
            f -= fovPunch * 4;
        }
        return f;
    }

    // ------------------------------------------------------------- 시네마틱
    static final class Cinematic {
        int casterId;
        String type;
        int duration;
        int age;
        Vec3d target;
        Vec3d fwd;
    }

    @Nullable
    private static Cinematic cin;

    public static void startCinematic(int casterId, String type, int duration, Vec3d target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Entity caster = mc.world.getEntityById(casterId);
        if (caster == null) return;
        Cinematic c = new Cinematic();
        c.casterId = casterId;
        c.type = type;
        c.duration = duration;
        c.target = target;
        Vec3d d = target.subtract(caster.getPos());
        d = new Vec3d(d.x, 0, d.z);
        if (d.lengthSquared() < 1) {
            float yaw = caster.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            d = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        c.fwd = d.normalize();
        cin = c;
    }

    public static boolean isCameraOverridden() {
        return isCinematic() || debugOrbit != null;
    }

    public static boolean isCinematic() {
        return cin != null && cin.age <= cin.duration;
    }

    public static float cinematicTime(float td) {
        return cin == null ? -1 : cin.age + td;
    }

    public static int cinematicDuration() {
        return cin == null ? 1 : cin.duration;
    }

    @Nullable
    public static Entity cinematicCaster() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return cin == null || mc.world == null ? null : mc.world.getEntityById(cin.casterId);
    }

    public static final class Shot {
        public Vec3d pos;
        public float yaw, pitch;
        public double fov;
    }

    private static final Shot SHOT = new Shot();

    static float smooth(float x) {
        x = MathHelper.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    // 개발용 고정 카메라 (플레이어 기준 궤도)
    public static float[] debugOrbit;

    @Nullable
    public static Shot currentShot(float td) {
        if (debugOrbit != null && !isCinematic()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return null;
            Vec3d C = mc.player.getLerpedPos(td);
            float by = MathHelper.lerpAngleDegrees(td, mc.player.prevBodyYaw, mc.player.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
            Vec3d F = new Vec3d(-Math.sin(by), 0, Math.cos(by));
            Vec3d Rv = new Vec3d(-F.z, 0, F.x);
            double ang = Math.toRadians(debugOrbit[0]);
            Vec3d cam = C.add(F.multiply(Math.cos(ang) * debugOrbit[1])).add(Rv.multiply(Math.sin(ang) * debugOrbit[1])).add(0, debugOrbit[2], 0);
            Vec3d look = C.add(0, debugOrbit.length > 3 ? debugOrbit[3] : 1.1, 0);
            Vec3d d = look.subtract(cam);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            SHOT.pos = cam;
            SHOT.yaw = (float) (MathHelper.atan2(d.z, d.x) * 57.2957795) - 90f;
            SHOT.pitch = (float) -(MathHelper.atan2(d.y, h) * 57.2957795);
            SHOT.fov = 50;
            return SHOT;
        }
        if (!isCinematic()) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity caster = mc.world.getEntityById(cin.casterId);
        if (caster == null) {
            cin = null;
            return null;
        }
        float t = cin.age + td;
        Vec3d C = caster.getLerpedPos(td);
        Vec3d F = cin.fwd;
        Vec3d R = new Vec3d(-F.z, 0, F.x);
        Vec3d T = cin.target;
        Vec3d cam, look;
        double fov;
        if (t < 24) {
            float k = smooth(t / 24f);
            double th = Math.toRadians(42 - 30 * k);
            double rad = 3.4 - 0.7 * k;
            cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.45 + 0.15 * k, 0);
            look = C.add(0, 1.55, 0);
            fov = 60 - 4 * k;
        } else if (t < 46) {
            float k = smooth((t - 24) / 22f);
            cam = C.subtract(F.multiply(3.4 - 0.3 * k)).subtract(R.multiply(1.6)).add(0, 0.8 + 0.6 * k, 0);
            look = C.add(0, 3.0, 0).add(F.multiply(0.4));
            fov = 66 + 4 * k;
        } else {
            float k = smooth((t - 46) / 24f);
            Vec3d M = C.lerp(T, 0.5);
            double dist = new Vec3d(T.x - C.x, 0, T.z - C.z).length();
            cam = M.add(R.multiply((dist * 0.55 + 5.5) * (1 - 0.1 * k))).add(0, 2.4 - 0.4 * k, 0).subtract(F.multiply(1.2));
            look = C.lerp(T, 0.62).add(0, 1.0, 0);
            fov = 74 - 4 * k;
        }
        cam = collide(mc, look, cam);
        Vec3d d = look.subtract(cam);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        SHOT.pos = cam;
        SHOT.yaw = (float) (MathHelper.atan2(d.z, d.x) * 57.2957795) - 90f;
        SHOT.pitch = (float) -(MathHelper.atan2(d.y, h) * 57.2957795);
        float s = trauma * trauma;
        if (s > 0) {
            SHOT.yaw += s * 3 * noise(t * 2.2f, 3);
            SHOT.pitch += s * 3 * noise(t * 2.2f, 9);
        }
        SHOT.fov = fov;
        return SHOT;
    }

    static Vec3d collide(MinecraftClient mc, Vec3d from, Vec3d to) {
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3d dir = to.subtract(from).normalize();
            return hit.getPos().subtract(dir.multiply(0.3));
        }
        return to;
    }

    public static void reset() {
        cin = null;
        trauma = 0;
    }

    private CameraController() {
    }
}
