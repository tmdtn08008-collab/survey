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
        if (isCinematic()) {
            Shot sh = currentShot(tickDelta);
            if (sh != null) roll += sh.roll;
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
            f += com.abilitywar.client.input.DashController.fov(tickDelta);
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
        Vec3d start;
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
        c.start = caster.getPos();
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

    public static String cinematicType() {
        return cin == null ? "" : cin.type;
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
        public float yaw, pitch, roll;
        public double fov;
    }

    /** 궁극기 각성 연출 ("void_pre" 같은 "_pre" 시네마틱) 인가. */
    public static boolean isPrelude() {
        return isCinematic() && cin.type.endsWith("_pre");
    }

    private static final Shot SHOT = new Shot();

    static float smooth(float x) {
        x = MathHelper.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    // 개발용 고정 카메라 (플레이어 기준 궤도)
    public static float[] debugOrbit;
    /** 개발용: 궁극기 연출 중에도 고정 카메라 사용. */
    public static boolean debugOverCinematic;

    @Nullable
    public static Shot currentShot(float td) {
        if (debugOrbit != null && (!isCinematic() || debugOverCinematic)) {
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
            SHOT.roll = 0;
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
        float roll = 0;
        if (cin.type.endsWith("_pre")) {
            // 궁극기 각성: 얼굴 클로즈업 → 낮은 각도로 돌며 올려다봄 → 확 빠지며 위로 → 넓게 멈춤
            Vec3d eye = C.add(0, caster.getStandingEyeHeight() - 0.05, 0);
            if (t < 14) {
                float k = smooth(t / 14f);
                cam = eye.add(F.multiply(1.35 - 0.25 * k)).add(R.multiply(0.32)).add(0, -0.08, 0);
                look = eye.add(0, -0.05, 0);
                fov = 40 - 6 * k;
                roll = 7 - 5 * k;
            } else if (t < 28) {
                float k = smooth((t - 14) / 14f);
                double th = Math.toRadians(40 - 85 * k);
                double rad = 2.6 + 0.4 * k;
                cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.25, 0);
                look = C.add(0, 1.35 + 0.2 * k, 0);
                fov = 64 + 4 * k;
                roll = (float) (-9 * Math.sin(k * Math.PI));
            } else if (t < 36) {
                float k = (t - 28) / 8f;
                k = 1 - (1 - k) * (1 - k) * (1 - k);
                double th = Math.toRadians(-45);
                double rad = 3.0 + 7 * k;
                cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.3 + 6 * k, 0);
                look = C.add(0, 1.2, 0);
                fov = 68 + 14 * k;
            } else {
                float k = smooth((t - 36) / 4f);
                double th = Math.toRadians(-45);
                cam = C.add(F.multiply(Math.cos(th) * 10)).add(R.multiply(Math.sin(th) * 10)).add(0, 6.3 + 0.3 * k, 0);
                look = C.add(0, 1.2, 0);
                fov = 82 + 2 * k;
            }
        } else if (cin.type.equals("blade_ult")) {
            Vec3d S = cin.start;
            if (t < 20) {
                // 검을 하늘로 드는 모습: 낮은 앵글에서 올려다보며 돌아감
                float k = smooth(t / 20f);
                double th = Math.toRadians(40 - 30 * k);
                double rad = 3.2 + 1.5 * k;
                cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.3, 0);
                look = C.add(0, 1.6 + 2.5 * k, 0);
                fov = 62 + 10 * k;
            } else if (t < 32) {
                // 거대해지는 검과 마력 균열: 와이드
                float k = smooth((t - 20) / 12f);
                cam = S.add(F.multiply(8 + 2 * k)).add(R.multiply(5)).add(0, 1.2, 0);
                look = S.add(0, 5.5 + 1.5 * k, 0);
                fov = 80;
            } else if (t < 46) {
                // 도약: 아래에서 올려다봄
                float k = smooth((t - 32) / 14f);
                cam = S.subtract(F.multiply(3.5)).add(R.multiply(3.5)).add(0, 0.6, 0);
                look = C.add(0, 2.0 + 3 * k, 0);
                fov = 82;
            } else {
                // 초대형 참격: 길을 옆에서 넓게
                float k = smooth((t - 46) / 26f);
                cam = S.add(F.multiply(9 + 3 * k)).add(R.multiply(17)).add(0, 4.5 - k, 0);
                look = S.add(F.multiply(11 + 4 * k)).add(0, 2.0, 0);
                fov = 80 - 3 * k;
            }
        } else if (cin.type.equals("warlock_ult")) {
            // T = 붙잡힌 대상 위치
            Vec3d toT = new Vec3d(T.x - C.x, 0, T.z - C.z);
            double dist = toT.length();
            Vec3d dirT = dist > 1e-3 ? toT.normalize() : F;
            Vec3d sideT = new Vec3d(-dirT.z, 0, dirT.x);
            if (t < 14) {
                // 손을 뻗는 흑마법사: 어깨 너머로 대상을 봄
                float k = smooth(t / 14f);
                cam = C.subtract(dirT.multiply(2.6 - 0.6 * k)).add(sideT.multiply(1.3)).add(0, 1.9, 0);
                look = T.add(0, 1.0 + 0.6 * k, 0);
                fov = 62 - 4 * k;
            } else if (t < 30) {
                // 어둠의 손이 대상을 움켜쥐어 들어 올림: 아래에서 올려다봄
                float k = smooth((t - 14) / 16f);
                cam = T.subtract(dirT.multiply(4.5)).add(sideT.multiply(3.5)).add(0, 0.4, 0);
                look = T.add(0, 1.6 + 1.2 * k, 0);
                fov = 74;
            } else {
                // 영혼을 뜯어냄: 옆에서 둘을 함께
                float k = smooth((t - 30) / 20f);
                Vec3d M = C.lerp(T, 0.5);
                cam = M.add(sideT.multiply(dist * 0.6 + 4.5)).add(0, 2.2 - 0.4 * k, 0);
                look = M.add(0, 1.4, 0);
                fov = 72 - 3 * k;
            }
        } else if (cin.type.equals("guardian_ult")) {
            // 거대한 방패: 시전자 뒤 2.2칸에 22틱째 박힘 (ClassFx.UltShield)
            Vec3d S = cin.start;
            Vec3d B = S.subtract(F.multiply(2.2));
            if (t < 12) {
                // 정면 로우앵글: 방패를 들어 올리는 수호기사
                float k = smooth(t / 12f);
                double th = Math.toRadians(28 - 12 * k);
                double rad = 3.4 - 0.6 * k;
                cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.4, 0);
                look = C.add(0, 1.5 + 0.3 * k, 0);
                fov = 60 + 4 * k;
            } else if (t < 22) {
                // 하늘에서 내려오는 거대한 방패: 앞에서 올려다봄
                float k = smooth((t - 12) / 10f);
                cam = S.add(F.multiply(7.5)).add(R.multiply(2.5)).add(0, 0.5, 0);
                look = B.add(0, 9 - 5 * k, 0);
                fov = 84;
            } else if (t < 40) {
                // 박히는 순간 + 결계: 옆에서 넓게 (살짝 돎)
                float k = smooth((t - 22) / 18f);
                double th = Math.toRadians(70 + 25 * k);
                double rad = 13 + 2 * k;
                cam = S.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 4.5 + 1.5 * k, 0);
                look = S.add(0, 2.2, 0);
                fov = 78;
            } else {
                // 어깨 너머: 방패를 등지고 끌려오는 적을 봄
                float k = smooth((t - 40) / 20f);
                cam = C.subtract(F.multiply(3.0 + 0.8 * k)).add(R.multiply(1.4)).add(0, 2.2 + 0.3 * k, 0);
                look = C.add(F.multiply(8)).add(0, 0.8, 0);
                fov = 72;
            }
        } else if (cin.type.equals("paladin_ult")) {
            // T = 거대한 성검이 박히는 자리 (24틱째)
            Vec3d toT = new Vec3d(T.x - C.x, 0, T.z - C.z);
            Vec3d dT = toT.lengthSquared() > 1e-3 ? toT.normalize() : F;
            Vec3d sT = new Vec3d(-dT.z, 0, dT.x);
            if (t < 12) {
                // 정면 로우앵글: 성검을 하늘로 드는 성기사
                float k = smooth(t / 12f);
                double th = Math.toRadians(26 - 10 * k);
                double rad = 3.2 + 0.6 * k;
                cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.3, 0);
                look = C.add(0, 1.7 + 1.0 * k, 0);
                fov = 62 + 8 * k;
            } else if (t < 24) {
                // 하늘에서 떨어지는 거대한 성검: 박힐 자리 옆에서 올려다봄
                float k = smooth((t - 12) / 12f);
                cam = T.add(sT.multiply(9)).subtract(dT.multiply(4)).add(0, 0.8, 0);
                look = T.add(0, 18 - 12 * k, 0);
                fov = 86;
            } else if (t < 40) {
                // 박히는 순간: 멀리 비스듬히 위에서 넓게
                float k = smooth((t - 24) / 16f);
                double th = Math.toRadians(50 + 30 * k);
                cam = T.add(dT.multiply(-Math.cos(th) * 15)).add(sT.multiply(Math.sin(th) * 15)).add(0, 6.5 - k, 0);
                look = T.add(0, 4, 0);
                fov = 80;
            } else {
                // 성기사 어깨 너머로 박힌 성검을 봄
                float k = smooth((t - 40) / 20f);
                cam = C.subtract(dT.multiply(3.2 + k)).add(sT.multiply(1.6)).add(0, 2.0 + 0.4 * k, 0);
                look = T.add(0, 5 + 2 * k, 0);
                fov = 74;
            }
        } else if (cin.type.equals("angel_ult")) {
            // 마법진: 중앙 = S + 15, 둘레 6개 = 반지름 9, 높이 13~15 (AngelAbility.circle)
            Vec3d S = cin.start;
            if (t < 14) {
                // 정면 로우앵글: 날개를 펼치며 하늘로 손을 드는 천사
                float k = smooth(t / 14f);
                double th = Math.toRadians(24 - 10 * k);
                double rad = 3.0 + 1.2 * k;
                cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.5, 0);
                look = C.add(0, 1.6 + 0.8 * k, 0);
                fov = 62 + 8 * k;
            } else if (t < 30) {
                // 하늘에 그려지는 마법진들: 발밑에서 올려다봄 (천사가 떠오름)
                float k = smooth((t - 14) / 16f);
                cam = S.add(F.multiply(3.5)).add(R.multiply(2.0)).add(0, 0.6, 0);
                look = S.add(F.multiply(-1.0)).add(0, 6 + 7 * k, 0);
                fov = 88;
            } else if (t < 50) {
                // 쏟아지는 빛줄기: 멀리 옆에서 넓게
                float k = smooth((t - 30) / 20f);
                cam = S.add(R.multiply(16 - 2 * k)).add(F.multiply(9)).add(0, 4.5 + k, 0);
                look = S.add(F.multiply(2)).add(0, 5.5, 0);
                fov = 82;
            } else {
                // 마무리: 떠 있는 천사 뒤에서 땅을 내려다봄
                float k = smooth((t - 50) / 20f);
                cam = C.subtract(F.multiply(4.5 + 1.5 * k)).add(R.multiply(1.8)).add(0, 2.6 + 0.6 * k, 0);
                look = C.add(F.multiply(8)).add(0, -0.5, 0);
                fov = 78;
            }
        } else if (cin.type.equals("cantor_ult")) {
            Vec3d S = cin.start;
            if (t < 16) {
                // 정면 로우앵글: 성가집을 펼치는 성가사 → 하늘의 빛을 올려다봄
                float k = smooth(t / 16f);
                double th = Math.toRadians(30 - 14 * k);
                double rad = 2.8 + 2.0 * k;
                cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.4, 0);
                look = C.add(0, 1.5 + 1.6 * k, 0);
                fov = 60 + 12 * k;
            } else if (t < 34) {
                // 빛의 날개로 떠오름: 옆 아래에서 넓게 (합창의 빛기둥이 솟음)
                float k = smooth((t - 16) / 18f);
                cam = S.add(R.multiply(8.5 - 1.5 * k)).add(F.multiply(5.5)).add(0, 0.8 + 1.2 * k, 0);
                look = C.add(0, 1.6, 0);
                fov = 82;
            } else if (t < 48) {
                // 거대한 광륜: 정면 아래에서 올려다봄
                float k = smooth((t - 34) / 14f);
                cam = S.add(F.multiply(6.5 - 1 * k)).add(R.multiply(1.5)).add(0, 0.5, 0);
                look = C.add(0, 1.9, 0);
                fov = 74 - 4 * k;
            } else {
                // GLORIA: 높은 부감으로 퍼지는 빛
                float k = smooth((t - 48) / 28f);
                cam = S.add(F.multiply(10 + 2 * k)).add(R.multiply(7)).add(0, 9 + 2 * k, 0);
                look = S.add(0, 1.0, 0);
                fov = 80;
            }
        } else if (cin.type.equals("blood_ult")) {
            // 핏빛 달은 시전자 뒤쪽 하늘 (BloodFx.Ult: start - F*46 + 30)
            Vec3d S = cin.start;
            Vec3d moon = S.subtract(F.multiply(46)).add(0, 30, 0);
            if (t < 16) {
                // 정면 로우앵글: 고개를 드는 혈귀 → 뒤로 떠오르는 달
                float k = smooth(t / 16f);
                double th = Math.toRadians(28 - 16 * k);
                double rad = 2.6 + 2.4 * k;
                cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.35, 0);
                look = C.add(0, 1.5, 0).lerp(moon, 0.06 + 0.1 * k);
                fov = 60 + 14 * k;
            } else if (t < 34) {
                // 사슬이 뻗고 적이 끌려옴: 높은 측면 와이드
                float k = smooth((t - 16) / 18f);
                cam = S.add(R.multiply(10 - 1.5 * k)).add(F.multiply(7 - 2 * k)).add(0, 7 - 1.5 * k, 0);
                look = S.add(0, 1.8 + 1.2 * k, 0);
                fov = 84 - 4 * k;
            } else if (t < 58) {
                // 흡혈: 공중의 혈귀를 아래에서 올려다보며 천천히 돎 (뒤로 달)
                float k = smooth((t - 34) / 24f);
                double th = Math.toRadians(-25 + 50 * k);
                double rad = 6.5;
                cam = S.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.6, 0);
                look = C.add(0, 1.0, 0);
                fov = 78;
            } else {
                // 피의 폭발: 높은 부감
                float k = smooth((t - 58) / 20f);
                cam = S.add(F.multiply(9 + 3 * k)).add(R.multiply(6)).add(0, 9 + 3 * k, 0);
                look = S.add(0, 1.0, 0);
                fov = 80;
            }
        } else if (cin.type.equals("dragon_ult")) {
            // 고룡은 용기사 뒤 10칸에 있고 머리는 높이 9~13칸
            Vec3d S = cin.start;
            Vec3d head = S.subtract(F.multiply(1.5)).add(0, 11.5, 0);
            if (t < 22) {
                // 웅크려 힘을 모으는 클로즈업 → 뒤로 빠지며 올려다봐 고룡을 드러냄
                float k = smooth(t / 22f);
                double th = Math.toRadians(34 - 18 * k);
                double rad = 2.5 + 6.5 * k;
                cam = C.add(F.multiply(Math.cos(th) * rad)).add(R.multiply(Math.sin(th) * rad)).add(0, 0.45 + 0.6 * k, 0);
                look = C.add(0, 1.0, 0).lerp(head.subtract(F.multiply(3)), 0.55 * k);
                fov = 58 + 20 * k;
            } else if (t < 42) {
                // 포효: 정면 로우앵글 와이드 (용기사 + 고룡 전체)
                float k = smooth((t - 22) / 20f);
                cam = S.add(F.multiply(8.5 + 1.5 * k)).add(R.multiply(4.5 - 2 * k)).add(0, 0.5, 0);
                look = S.subtract(F.multiply(4)).add(0, 7.8 + 0.6 * k, 0);
                fov = 84 + 3 * k;
            } else if (t < 56) {
                // 도약: 지면에서 올려다보며 용기사를 따라감 (뒤에 고룡)
                float k = smooth((t - 42) / 14f);
                cam = S.add(R.multiply(7)).add(F.multiply(6 + 2 * k)).add(0, 0.4, 0);
                look = C.add(0, 1.0, 0).lerp(head, 0.3 + 0.2 * k);
                fov = 82;
            } else {
                // 브레스 → 폭발: 측면 와이드 (고룡 머리 → 목표)
                float k = smooth((t - 56) / 18f);
                Vec3d mouth = S.add(0, 9.5, 0);
                Vec3d M = mouth.lerp(T, 0.5);
                double dist = new Vec3d(T.x - S.x, 0, T.z - S.z).length();
                cam = M.add(R.multiply((dist * 0.6 + 14) * (1 - 0.06 * k))).add(0, 1.5 + 1.0 * k, 0).subtract(F.multiply(4));
                look = mouth.lerp(T, 0.55 + 0.25 * k).subtract(F.multiply(1.5));
                fov = 82 - 4 * k;
            }
        } else if (t < 24) {
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
        SHOT.roll = roll;
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
