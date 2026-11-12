package com.abilitywar.client.fx.dragon;

import com.abilitywar.client.fx.AWRenderLayers;
import com.abilitywar.client.fx.Fx;
import com.abilitywar.client.fx.Tex;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 고룡 강림의 고룡. 바닐라 모델을 쓰지 않고 직접 만든 뼈대(머리·턱·뿔·목·몸통·다리·꼬리·날개)를
 * 상자와 날개막 다각형으로 그린다. 크기는 블록 단위 (몸길이 약 25칸, 날개 폭 약 26칸).
 *
 * <p>로컬 좌표: +Z = 앞(바라보는 방향), +Y = 위, +X = 왼쪽. 원점 = 땅 위의 중심.
 */
public final class ElderDragon {
    /** 고룡 그림 한 벌 (붉은 고룡 · 푸른 용왕 · 녹옥 비룡). */
    public record Skin(Identifier scales, Identifier glow, Identifier belly, Identifier horn, Identifier membrane, Identifier membraneGlow, float tr, float tg, float tb) {
        static Skin of(String suffix, float tr, float tg, float tb) {
            return new Skin(Tex.fx("elder_scales" + suffix), Tex.fx("elder_glow" + suffix), Tex.fx("elder_belly" + suffix), Tex.fx("elder_horn" + suffix),
                    Tex.fx("elder_membrane" + suffix), Tex.fx("elder_membrane_glow" + suffix), tr, tg, tb);
        }
    }

    public static final Skin RED = Skin.of("", 0.74f, 0.62f, 0.62f);
    public static final Skin AZURE = Skin.of("_azure", 0.9f, 0.92f, 1f);
    public static final Skin JADE = Skin.of("_jade", 0.85f, 0.9f, 0.85f);
    static Skin skin = RED;

    /** 자세 (모두 로컬 좌표). */
    public static final class Pose {
        public float rear;            // 0..1 앞몸을 일으킴
        public float wingSpread;      // 0 접음 .. 1 활짝
        public float wingFlap;        // -1 위로 .. 1 아래로
        public float jaw;             // 0..1 입 벌림
        public float throat;          // 0..1 목구멍 불빛 (브레스 준비)
        public float clawRaise;       // 0..1 앞발 들기
        public float tailSway = 1;
        public float breathe;         // 0..1 가슴 부풀기
        public float shake;           // 포효 떨림
        public float time;
        public Vec3d head = new Vec3d(0, 8.5, 6.5);
        public Vec3d look = new Vec3d(0, 8.2, 12);
    }

    // ------------------------------------------------------------------ 결과 (렌더 후 갱신)
    /** 마지막으로 그린 입 위치 (월드). */
    public static Vec3d lastMouth = Vec3d.ZERO;
    /** 몸 위의 점들 (월드) — 불티 파티클용. */
    public static final List<Vec3d> SAMPLES = new ArrayList<>();

    // ------------------------------------------------------------------ 조립용
    enum Mat { SCALES, BELLY, HORN, WHITE }

    record Part(Mat mat, Vec3d c, Vec3d ax, Vec3d ay, Vec3d az, float sx, float sy, float sz, float r, float g, float b, float uo, float tile) {
    }

    record Poly(Vec3d[] pts, Vec3d uAxis, Vec3d vAxis, Vec3d origin) {
    }

    private static final List<Part> PARTS = new ArrayList<>();
    private static final List<Part> EMISSIVE = new ArrayList<>();
    private static final List<Poly> MEMBRANES = new ArrayList<>();
    private static float seed;

    static final Vec3d UP = new Vec3d(0, 1, 0);

    /** a→b 를 잇는 상자 (단면 w x h). up 은 대략적인 위 방향. */
    static void seg(Mat m, Vec3d a, Vec3d b, float w, float h, Vec3d up, float r, float g, float bl) {
        Vec3d d = b.subtract(a);
        double len = d.length();
        if (len < 1e-4) return;
        Vec3d az = d.multiply(1 / len);
        Vec3d ax = up.crossProduct(az);
        if (ax.lengthSquared() < 1e-6) ax = new Vec3d(1, 0, 0).crossProduct(az);
        ax = ax.normalize();
        Vec3d ay = az.crossProduct(ax).normalize();
        seed += 0.37f;
        PARTS.add(new Part(m, a.add(b).multiply(0.5), ax, ay, az, w, h, (float) len, r, g, bl, seed % 1f, m == Mat.HORN || m == Mat.WHITE ? 1.2f : 1.6f));
    }

    static void seg(Mat m, Vec3d a, Vec3d b, float w, float h, Vec3d up) {
        seg(m, a, b, w, h, up, 1, 1, 1);
    }

    /** 기저(ax, ay, az) 안의 상자. c = 중심. */
    static void box(Mat m, Frame f, double x, double y, double z, float sx, float sy, float sz) {
        box(m, f, x, y, z, sx, sy, sz, 1, 1, 1);
    }

    static void box(Mat m, Frame f, double x, double y, double z, float sx, float sy, float sz, float r, float g, float b) {
        seed += 0.29f;
        PARTS.add(new Part(m, f.at(x, y, z), f.x, f.y, f.z, sx, sy, sz, r, g, b, seed % 1f, m == Mat.HORN || m == Mat.WHITE ? 1.2f : 1.6f));
    }

    static void glowBox(Frame f, double x, double y, double z, float sx, float sy, float sz, float r, float g, float b) {
        EMISSIVE.add(new Part(Mat.WHITE, f.at(x, y, z), f.x, f.y, f.z, sx, sy, sz, r, g, b, 0, 1));
    }

    /** 원점 + 세 축. */
    record Frame(Vec3d o, Vec3d x, Vec3d y, Vec3d z) {
        Vec3d at(double a, double b, double c) {
            return o.add(x.multiply(a)).add(y.multiply(b)).add(z.multiply(c));
        }

        Vec3d dir(double a, double b, double c) {
            return x.multiply(a).add(y.multiply(b)).add(z.multiply(c)).normalize();
        }

        /** pivot 기준으로 x 축 둘레 회전 (앞이 위로 = 양수). */
        Frame pitch(Vec3d pivot, double deg) {
            double t = Math.toRadians(deg);
            Vec3d ny = y.multiply(Math.cos(t)).subtract(z.multiply(Math.sin(t)));
            Vec3d nz = y.multiply(Math.sin(t)).add(z.multiply(Math.cos(t)));
            return new Frame(pivot, x, ny, nz);
        }

        static Frame look(Vec3d o, Vec3d fwd, Vec3d upHint) {
            Vec3d z = fwd.normalize();
            Vec3d x = upHint.crossProduct(z);
            if (x.lengthSquared() < 1e-6) x = new Vec3d(1, 0, 0);
            x = x.normalize();
            Vec3d y = z.crossProduct(x).normalize();
            return new Frame(o, x, y, z);
        }
    }

    static Vec3d rotX(Vec3d v, double deg) {
        double t = Math.toRadians(deg), c = Math.cos(t), s = Math.sin(t);
        return new Vec3d(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
    }

    static Vec3d rotY(Vec3d v, double deg) {
        double t = Math.toRadians(deg), c = Math.cos(t), s = Math.sin(t);
        return new Vec3d(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    static Vec3d rotZ(Vec3d v, double deg) {
        double t = Math.toRadians(deg), c = Math.cos(t), s = Math.sin(t);
        return new Vec3d(v.x * c - v.y * s, v.x * s + v.y * c, v.z);
    }

    static Vec3d bez(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double u = 1 - t;
        return p0.multiply(u * u * u).add(p1.multiply(3 * u * u * t)).add(p2.multiply(3 * u * t * t)).add(p3.multiply(t * t * t));
    }

    static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return a.add(b.subtract(a).multiply(t));
    }

    // ------------------------------------------------------------------ 뼈대
    static final Vec3d HIP = new Vec3d(0, 4.4, -3.2);

    /** 자세로 모든 부위를 만든다 (로컬 좌표). @return 입 위치 (로컬) */
    static Vec3d build(Pose p) {
        PARTS.clear();
        EMISSIVE.clear();
        MEMBRANES.clear();
        seed = 0;
        double rearDeg = 28 * p.rear;
        // 몸통 좌표계: 엉덩이 기준으로 앞몸을 일으킴
        Frame world = new Frame(Vec3d.ZERO, new Vec3d(1, 0, 0), UP, new Vec3d(0, 0, 1));
        Frame body = world.pitch(HIP, rearDeg);
        Vec3d shakeV = new Vec3d(Math.sin(p.time * 3.1) * 0.06, Math.sin(p.time * 2.3) * 0.05, 0).multiply(p.shake);
        body = new Frame(body.o().add(shakeV), body.x(), body.y(), body.z());
        float br = 1 + 0.05f * p.breathe;

        // ---- 몸통 (엉덩이 뒤 → 어깨)
        Vec3d[] spine = {body.at(0, 0.2, -1.8), body.at(0, 0.4, 0), body.at(0, 0.7, 2.0), body.at(0, 1.0, 3.8), body.at(0, 1.6, 5.4)};
        float[][] sec = {{1.9f, 1.9f}, {2.3f, 2.2f}, {2.6f * br, 2.5f * br}, {2.4f * br, 2.3f * br}};
        for (int i = 0; i < 4; i++) {
            Vec3d a = spine[i], b = spine[i + 1];
            seg(Mat.SCALES, a, b, sec[i][0], sec[i][1], body.y());
            // 배 판
            Vec3d down = body.y().multiply(-sec[i][1] * 0.3);
            seg(Mat.BELLY, a.add(down), b.add(down), sec[i][0] * 0.78f, sec[i][1] * 0.48f, body.y());
            // 등 가시
            Vec3d mid = a.add(b).multiply(0.5).add(body.y().multiply(sec[i][1] * 0.5));
            spike(mid, body.y(), body.z(), 0.95f - i * 0.05f, 0.22f);
        }
        // 어깨 덩어리 (날개 뿌리)
        box(Mat.SCALES, body, 0, 1.55, 4.4, 3.0f * br, 1.2f, 1.8f);

        // ---- 꼬리 (앞몸이 들려도 꼬리는 땅 쪽을 유지)
        Vec3d tp = spine[0];
        double pitch = 12; // 처음엔 살짝 아래로
        double yaw = 0;
        float tw = 1.7f;
        for (int i = 0; i < 15; i++) {
            yaw += Math.sin(p.time * 0.07 - i * 0.45) * 7 * p.tailSway;
            double pt = pitch + (i > 9 ? -(i - 9) * 4 : 0);
            Vec3d d = rotY(rotX(new Vec3d(0, 0, -1), -pt), yaw);
            // 땅을 뚫지 않게
            Vec3d np = tp.add(d.multiply(1.0));
            if (np.y < 0.35 + tw * 0.4) np = new Vec3d(np.x, 0.35 + tw * 0.4, np.z);
            seg(Mat.SCALES, tp, np, tw, tw * 0.9f, UP);
            if (i % 2 == 0) spike(tp.add(np).multiply(0.5).add(0, tw * 0.45, 0), UP, tp.subtract(np).normalize(), 0.7f * tw, 0.14f * tw + 0.05f);
            tp = np;
            pitch = Math.max(-8, pitch - 3.5);
            tw = Math.max(0.28f, tw * 0.88f);
        }
        // 꼬리 끝 칼날
        Frame tf = Frame.look(tp, tp.subtract(spine[0]).normalize(), UP);
        box(Mat.HORN, tf, 0, 0, 0.5, 0.15f, 1.1f, 1.4f, 0.9f, 0.82f, 0.7f);

        // ---- 뒷다리 (2관절, 발은 땅에)
        for (int s = -1; s <= 1; s += 2) {
            Vec3d hipJ = body.at(s * 1.35, -0.4, -0.3);
            Vec3d foot = new Vec3d(s * 1.7, 0.25, HIP.z + 0.6);
            Vec3d knee = ik(hipJ, foot, 2.5, 2.5, new Vec3d(0, 0, 1));
            seg(Mat.SCALES, hipJ, knee, 1.25f, 1.25f, new Vec3d(s, 0, 0).crossProduct(knee.subtract(hipJ)).normalize());
            seg(Mat.SCALES, knee, foot, 0.85f, 0.85f, UP);
            Frame ff = new Frame(foot, new Vec3d(1, 0, 0), UP, new Vec3d(0, 0, 1));
            box(Mat.SCALES, ff, 0, 0, 0.35, 1.1f, 0.45f, 1.4f);
            claws(ff.at(0, -0.05, 1.05), new Vec3d(0, -0.35, 1).normalize(), s, 0.75f);
        }

        // ---- 앞다리 (서 있을 때 / 들었을 때)
        float raise = MathHelper.clamp(Math.max(p.rear, p.clawRaise), 0, 1);
        for (int s = -1; s <= 1; s += 2) {
            Vec3d sh = body.at(s * 1.35, 0.3, 4.6);
            Vec3d upperDirStand = new Vec3d(s * 0.15, -1, -0.1).normalize();
            Vec3d upperDirRaise = body.dir(s * 0.3, 0.2, 1);
            Vec3d ud = lerp(upperDirStand, upperDirRaise, raise).normalize();
            Vec3d elbow = sh.add(ud.multiply(2.1));
            Vec3d foreStand = new Vec3d(0, -1, 0.15).normalize();
            Vec3d foreRaise = body.dir(-s * 0.1, -0.5, 1);
            Vec3d fd = lerp(foreStand, foreRaise, raise).normalize();
            Vec3d wrist = elbow.add(fd.multiply(2.0));
            if (raise < 0.5 && wrist.y < 0.3) wrist = new Vec3d(wrist.x, 0.3, wrist.z);
            seg(Mat.SCALES, sh, elbow, 0.95f, 0.95f, body.z());
            seg(Mat.SCALES, elbow, wrist, 0.72f, 0.72f, body.z());
            Vec3d hand = lerp(new Vec3d(0, 0, 1), body.dir(0, 0.3, 1), raise).normalize();
            Frame hf = Frame.look(wrist, hand, UP);
            box(Mat.SCALES, hf, 0, 0, 0.3, 0.95f, 0.4f, 0.9f);
            claws(hf.at(0, 0, 0.8), lerp(new Vec3d(0, -0.4, 1), body.dir(0, -0.6, 1), raise).normalize(), s, 0.7f);
        }

        // ---- 날개
        for (int s = -1; s <= 1; s += 2) wing(p, body, s);

        // ---- 목
        Vec3d n0 = body.at(0, 1.9, 5.9);
        Vec3d t0 = body.dir(0, 0.75, 0.66);
        Vec3d head = p.head.add(shakeV.multiply(2));
        Vec3d fwd = p.look.subtract(head).normalize();
        double L = head.subtract(n0).length();
        // 살짝 S자로 휜 목 (정면에서도 뻣뻣해 보이지 않게)
        double sway = 1.1 + 0.25 * Math.sin(p.time * 0.06);
        Vec3d c1 = n0.add(t0.multiply(L * 0.42)).add(sway, 0, 0);
        Vec3d c2 = head.subtract(fwd.multiply(L * 0.38)).subtract(0, L * 0.05, 0).add(-sway * 0.9, 0, 0);
        int N = 11;
        Vec3d prev = n0;
        for (int i = 1; i <= N; i++) {
            double t = (double) i / N;
            Vec3d cur = bez(n0, c1, c2, head, t);
            float w = (float) MathHelper.lerp(t, 1.75, 1.05);
            seg(Mat.SCALES, prev, cur, w, w * 1.08f, UP);
            Vec3d dd = cur.subtract(prev).normalize();
            Vec3d upv = dd.crossProduct(UP.crossProduct(dd)).normalize();
            if (upv.lengthSquared() < 1e-6) upv = UP;
            Vec3d down = upv.multiply(-w * 0.32);
            seg(Mat.BELLY, prev.add(down), cur.add(down), w * 0.72f, w * 0.5f, upv);
            if (i % 2 == 1) spike(prev.add(cur).multiply(0.5).add(upv.multiply(w * 0.5)), upv, dd, 0.75f - (float) t * 0.25f, 0.16f);
            prev = cur;
        }

        // ---- 머리
        return head(p, Frame.look(head, fwd, UP));
    }

    static Vec3d ik(Vec3d a, Vec3d c, double l1, double l2, Vec3d bendHint) {
        Vec3d d = c.subtract(a);
        double dist = Math.min(d.length(), l1 + l2 - 1e-3);
        Vec3d dn = d.normalize();
        double x = (l1 * l1 - l2 * l2 + dist * dist) / (2 * dist);
        double h = Math.sqrt(Math.max(0, l1 * l1 - x * x));
        Vec3d perp = bendHint.subtract(dn.multiply(bendHint.dotProduct(dn)));
        if (perp.lengthSquared() < 1e-6) perp = new Vec3d(0, 1, 0);
        return a.add(dn.multiply(x)).add(perp.normalize().multiply(h));
    }

    static void spike(Vec3d base, Vec3d up, Vec3d fwd, float len, float thick) {
        Vec3d dir = up.multiply(0.8).subtract(fwd.multiply(0.6)).normalize();
        seg(Mat.HORN, base, base.add(dir.multiply(len)), thick, thick * 1.6f, fwd, 0.55f, 0.32f, 0.22f);
        seg(Mat.HORN, base.add(dir.multiply(len)), base.add(dir.multiply(len * 1.45)), thick * 0.45f, thick * 0.7f, fwd, 0.75f, 0.6f, 0.45f);
    }

    static void claws(Vec3d base, Vec3d dir, int side, float len) {
        for (int k = -1; k <= 1; k++) {
            Vec3d off = new Vec3d(k * 0.32, 0, 0);
            Vec3d a = base.add(off);
            Vec3d d = dir.add(k * 0.18, 0, 0).normalize();
            Vec3d mid = a.add(d.multiply(len * 0.55));
            Vec3d tip = mid.add(d.add(0, -0.6, 0).normalize().multiply(len * 0.5));
            seg(Mat.WHITE, a, mid, 0.18f, 0.22f, UP, 0.82f, 0.76f, 0.66f);
            seg(Mat.WHITE, mid, tip, 0.1f, 0.13f, UP, 0.92f, 0.88f, 0.8f);
        }
    }

    static void wing(Pose p, Frame body, int s) {
        float sp = MathHelper.clamp(p.wingSpread, 0, 1);
        Vec3d root = body.at(s * 1.25, 2.0, 4.0);
        double flap = p.wingFlap * 32;
        // 접힘 ↔ 펼침 방향 (왼쪽 날개 기준, s 로 좌우 반전)
        Vec3d humF = new Vec3d(s * 0.35, 0.65, -0.65), humS = new Vec3d(s * 1, 0.42, -0.1);
        Vec3d forF = new Vec3d(s * 0.1, -0.35, -1), forS = new Vec3d(s * 1, 0.12, 0.2);
        Vec3d hum = rotZ(lerp(humF, humS, sp).normalize(), -s * flap);
        Vec3d elbow = root.add(hum.multiply(3.1));
        Vec3d fore = rotZ(lerp(forF, forS, sp).normalize(), -s * flap * 1.3);
        Vec3d wrist = elbow.add(fore.multiply(3.9));
        seg(Mat.SCALES, root, elbow, 0.7f, 0.7f, UP);
        seg(Mat.SCALES, elbow, wrist, 0.5f, 0.5f, UP);
        // 손목 갈고리
        seg(Mat.WHITE, wrist, wrist.add(new Vec3d(s * 0.2, 0.6, 0.6)), 0.15f, 0.2f, UP, 0.85f, 0.78f, 0.66f);
        Vec3d[][] fdirs = {
                {new Vec3d(s * 0.05, -0.55, -0.85), new Vec3d(s * 0.75, 0.6, -0.25)},
                {new Vec3d(s * 0.08, -0.62, -0.8), new Vec3d(s * 0.92, 0.05, -0.45)},
                {new Vec3d(s * 0.1, -0.7, -0.75), new Vec3d(s * 0.6, -0.38, -0.75)},
                {new Vec3d(s * 0.12, -0.78, -0.7), new Vec3d(s * 0.22, -0.52, -0.95)},
        };
        double[] flen = {6.6, 6.1, 5.3, 4.4};
        Vec3d[] tips = new Vec3d[4];
        for (int i = 0; i < 4; i++) {
            Vec3d d = rotZ(lerp(fdirs[i][0], fdirs[i][1], sp).normalize(), -s * flap * 1.6);
            // 손가락은 약간 휘어짐
            Vec3d mid = wrist.add(d.multiply(flen[i] * 0.55));
            Vec3d d2 = d.add(0, -0.12, -0.1).normalize();
            tips[i] = mid.add(d2.multiply(flen[i] * 0.45));
            seg(Mat.HORN, wrist, mid, 0.28f, 0.28f, UP, 0.5f, 0.2f, 0.14f);
            seg(Mat.HORN, mid, tips[i], 0.18f, 0.18f, UP, 0.55f, 0.24f, 0.16f);
        }
        Vec3d attach = body.at(s * 0.95, 0.9, -1.4);
        // 날개막: 앞쪽 (뿌리-팔꿈치-손목), 손가락 사이 (오목한 뒷전), 마지막 손가락-몸
        Vec3d uA = hum.add(fore).normalize(), vA = hum.crossProduct(fore).crossProduct(uA).normalize();
        if (vA.lengthSquared() < 1e-6) vA = UP;
        mem(new Vec3d[]{root, elbow, wrist, wrist}, uA, vA, root);
        mem(new Vec3d[]{root, wrist, tips[0], tips[0]}, uA, vA, root);
        for (int i = 0; i < 3; i++) {
            Vec3d m = lerp(lerp(tips[i], tips[i + 1], 0.5), wrist, 0.22);
            mem(new Vec3d[]{wrist, tips[i], m, tips[i + 1]}, uA, vA, root);
        }
        Vec3d m4 = lerp(lerp(tips[3], attach, 0.5), wrist, 0.18);
        mem(new Vec3d[]{wrist, tips[3], m4, attach}, uA, vA, root);
        mem(new Vec3d[]{root, wrist, attach, attach}, uA, vA, root);
    }

    static void mem(Vec3d[] pts, Vec3d u, Vec3d v, Vec3d o) {
        MEMBRANES.add(new Poly(pts, u, v, o));
    }

    static Vec3d head(Pose p, Frame h) {
        // 두개골 / 이마 / 주둥이
        box(Mat.SCALES, h, 0, 0, 0.6, 1.95f, 1.5f, 2.1f);
        box(Mat.SCALES, h, 0.62, 0.72, 1.25, 0.6f, 0.36f, 1.35f);
        box(Mat.SCALES, h, -0.62, 0.72, 1.25, 0.6f, 0.36f, 1.35f);
        box(Mat.SCALES, h, 0, 0.05, 2.55, 1.32f, 0.85f, 2.2f);
        box(Mat.SCALES, h, 0, 0.12, 3.8, 1.05f, 0.62f, 0.55f);
        box(Mat.SCALES, h, 0, -0.32, 2.4, 1.2f, 0.25f, 2.6f, 0.75f, 0.55f, 0.5f);
        // 윗니
        for (double z = 1.9; z <= 3.85; z += 0.39) {
            box(Mat.WHITE, h, 0.5, -0.55, z, 0.12f, 0.3f, 0.12f, 0.95f, 0.92f, 0.85f);
            box(Mat.WHITE, h, -0.5, -0.55, z, 0.12f, 0.3f, 0.12f, 0.95f, 0.92f, 0.85f);
        }
        // 콧구멍 / 눈 (빛)
        glowBox(h, 0.3, 0.42, 4.02, 0.14f, 0.1f, 0.06f, 1f, 0.45f, 0.1f);
        glowBox(h, -0.3, 0.42, 4.02, 0.14f, 0.1f, 0.06f, 1f, 0.45f, 0.1f);
        glowBox(h, 0.82, 0.45, 1.3, 0.14f, 0.2f, 0.46f, 1f, 0.85f, 0.25f);
        glowBox(h, -0.82, 0.45, 1.3, 0.14f, 0.2f, 0.46f, 1f, 0.85f, 0.25f);
        // 뿔 (뒤로 휘어 올라감)
        for (int s = -1; s <= 1; s += 2) {
            Vec3d a = h.at(s * 0.62, 0.62, 0.05);
            Vec3d d = h.dir(s * 0.32, 0.55, -0.78);
            float th = 0.44f;
            double[] lens = {0.85, 0.8, 0.72, 0.62, 0.5};
            for (int i = 0; i < 5; i++) {
                Vec3d b = a.add(d.multiply(lens[i]));
                seg(Mat.HORN, a, b, th, th, h.y());
                a = b;
                d = d.add(h.y().multiply(-0.2)).add(h.z().multiply(-0.05)).normalize();
                th *= 0.76f;
            }
            Vec3d a2 = h.at(s * 0.88, 0.15, 0.15);
            Vec3d d2 = h.dir(s * 0.65, 0.12, -0.75);
            float th2 = 0.28f;
            for (int i = 0; i < 3; i++) {
                Vec3d b = a2.add(d2.multiply(0.55));
                seg(Mat.HORN, a2, b, th2, th2, h.y());
                a2 = b;
                th2 *= 0.7f;
            }
            // 볼 가시
            seg(Mat.HORN, h.at(s * 0.95, -0.25, 0.5), h.at(s * 1.4, -0.4, -0.2), 0.16f, 0.2f, h.y());
        }
        // 머리 위 볏
        for (int i = 0; i < 3; i++) {
            Vec3d b = h.at(0, 0.75, 0.15 + i * 0.42);
            spike(b, h.y(), h.z(), 0.55f - i * 0.12f, 0.14f);
        }
        // 아래턱 (경첩에서 벌어짐)
        Frame jaw = h.pitch(h.at(0, -0.45, 0.45), -38 * p.jaw);
        box(Mat.SCALES, jaw, 0, -0.22, 1.25, 1.25f, 0.46f, 2.85f);
        box(Mat.SCALES, jaw, 0, -0.26, 2.75, 1.0f, 0.42f, 0.55f);
        for (double z = 0.8; z <= 2.7; z += 0.4) {
            box(Mat.WHITE, jaw, 0.46, 0.08, z, 0.11f, 0.26f, 0.11f, 0.95f, 0.92f, 0.85f);
            box(Mat.WHITE, jaw, -0.46, 0.08, z, 0.11f, 0.26f, 0.11f, 0.95f, 0.92f, 0.85f);
        }
        // 입 안 불빛
        float fire = 0.35f + 0.65f * p.throat;
        glowBox(jaw, 0, 0.02, 1.4, 0.85f, 0.08f, 2.2f, fire, 0.4f * fire, 0.08f * fire);
        glowBox(h, 0, -0.42, 1.8, 0.8f, 0.06f, 2.0f, fire * 0.8f, 0.3f * fire, 0.05f * fire);
        // 입 = 위턱과 아래턱 사이 앞쪽
        Vec3d upper = h.at(0, -0.4, 3.7);
        Vec3d lower = jaw.at(0, 0.0, 2.6);
        return upper.add(lower).multiply(0.5);
    }

    // ------------------------------------------------------------------ 그리기
    /**
     * @param pos   땅 위 중심 (월드)
     * @param yaw   바라보는 방향 (마인크래프트 기준)
     * @param alpha 전체 투명도
     * @param glow  비늘 틈 용암빛 세기
     */
    public static void render(Fx.Ctx c, Vec3d pos, float yaw, float scale, Pose p, float alpha, float glow, Skin sk) {
        skin = sk;
        try {
            render(c, pos, yaw, scale, p, alpha, glow);
        } finally {
            skin = RED;
        }
    }

    public static void render(Fx.Ctx c, Vec3d pos, float yaw, float scale, Pose p, float alpha, float glow) {
        Vec3d mouthLocal = build(p);
        // 로컬 → 월드
        double th = Math.toRadians(-yaw);
        double cs = Math.cos(th), sn = Math.sin(th);
        lastMouth = toWorld(mouthLocal, pos, cs, sn, scale);
        SAMPLES.clear();
        for (int i = 0; i < PARTS.size(); i += 3) SAMPLES.add(toWorld(PARTS.get(i).c(), pos, cs, sn, scale));
        if (alpha <= 0.01f) return;

        c.matrices.push();
        c.matrices.translate(pos.x - c.cam.x, pos.y - c.cam.y, pos.z - c.cam.z);
        c.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        c.matrices.scale(scale, scale, scale);
        Matrix4f m = c.matrices.peek().getPositionMatrix();
        Matrix3f n = c.matrices.peek().getNormalMatrix();
        int light = 0xF000F0;
        // 몸 (재질별로 한 번씩)
        emitParts(c.imm.getBuffer(RenderLayer.getEntityTranslucent(skin.scales())), m, n, Mat.SCALES, alpha, light);
        emitParts(c.imm.getBuffer(RenderLayer.getEntityTranslucent(skin.belly())), m, n, Mat.BELLY, alpha, light);
        emitParts(c.imm.getBuffer(RenderLayer.getEntityTranslucent(skin.horn())), m, n, Mat.HORN, alpha, light);
        emitParts(c.imm.getBuffer(RenderLayer.getEntityTranslucent(skin.horn())), m, n, Mat.WHITE, alpha, light);
        VertexConsumer mv = c.imm.getBuffer(RenderLayer.getEntityTranslucent(skin.membrane()));
        for (Poly poly : MEMBRANES) emitPoly(mv, m, n, poly, 1, 1, 1, alpha * 0.92f, light);
        c.imm.draw();
        // 빛 (가산)
        float g = glow * alpha;
        if (g > 0.01f) {
            VertexConsumer gv = c.imm.getBuffer(AWRenderLayers.additive(skin.glow()));
            for (Part pt : PARTS) if (pt.mat() == Mat.SCALES) emitBoxSimple(gv, m, pt, g, g, g, 1);
            VertexConsumer mg = c.imm.getBuffer(AWRenderLayers.additive(skin.membraneGlow()));
            for (Poly poly : MEMBRANES) emitPolySimple(mg, m, poly, g, g, g, 1);
            VertexConsumer ev = c.imm.getBuffer(AWRenderLayers.additive(Tex.GLOW));
            for (Part pt : EMISSIVE) emitBoxSimple(ev, m, pt, pt.r() * alpha, pt.g() * alpha, pt.b() * alpha, 1);
        }
        c.matrices.pop();
    }

    static Vec3d toWorld(Vec3d l, Vec3d pos, double cs, double sn, float scale) {
        double x = l.x * cs + l.z * sn, z = -l.x * sn + l.z * cs;
        return pos.add(x * scale, l.y * scale, z * scale);
    }

    /** 로컬 → 월드 (렌더하지 않고 계산만). */
    public static Vec3d localToWorld(Vec3d l, Vec3d pos, float yaw, float scale) {
        double th = Math.toRadians(-yaw);
        return toWorld(l, pos, Math.cos(th), Math.sin(th), scale);
    }

    interface FaceSink {
        void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, float u0, float v0, float u1, float v1, Vec3d nrm);
    }

    /** 상자의 6면 (텍스처 좌표는 면 크기에 비례해 반복). */
    static void faces(Part p, boolean fullUv, FaceSink sink) {
        Vec3d hx = p.ax().multiply(p.sx() / 2), hy = p.ay().multiply(p.sy() / 2), hz = p.az().multiply(p.sz() / 2);
        Object[][] f = {
                {p.c().add(hy), hx, hz.multiply(-1), p.ay(), p.sx(), p.sz()},
                {p.c().subtract(hy), hx, hz, p.ay().multiply(-1), p.sx(), p.sz()},
                {p.c().add(hz), hx.multiply(-1), hy, p.az(), p.sx(), p.sy()},
                {p.c().subtract(hz), hx, hy, p.az().multiply(-1), p.sx(), p.sy()},
                {p.c().add(hx), hz, hy, p.ax(), p.sz(), p.sy()},
                {p.c().subtract(hx), hz.multiply(-1), hy, p.ax().multiply(-1), p.sz(), p.sy()},
        };
        for (Object[] o : f) {
            Vec3d c = (Vec3d) o[0], ua = (Vec3d) o[1], va = (Vec3d) o[2], nrm = (Vec3d) o[3];
            float w = (float) o[4], h = (float) o[5];
            float u0 = p.uo(), v0 = p.uo() * 0.7f, u1 = u0 + w / p.tile(), v1 = v0 + h / p.tile();
            if (fullUv) {
                u0 = 0;
                v0 = 0;
                u1 = 1;
                v1 = 1;
            }
            sink.quad(c.subtract(ua).subtract(va), c.add(ua).subtract(va), c.add(ua).add(va), c.subtract(ua).add(va), u0, v0, u1, v1, nrm);
        }
    }

    static void emitParts(VertexConsumer vc, Matrix4f m, Matrix3f n, Mat mat, float alpha, int light) {
        // 재질 색조: 오래된 용이라 비늘은 어둡게
        float tr = mat == Mat.SCALES ? skin.tr() : 1f, tg = mat == Mat.SCALES ? skin.tg() : 1f, tb = mat == Mat.SCALES ? skin.tb() : 1f;
        for (Part p : PARTS) if (p.mat() == mat) emitBox(vc, m, n, p, alpha, light, tr, tg, tb);
    }

    static void emitBox(VertexConsumer vc, Matrix4f m, Matrix3f n, Part p, float alpha, int light, float tr, float tg, float tb) {
        float r = p.r() * tr, g = p.g() * tg, b = p.b() * tb;
        faces(p, false, (q0, q1, q2, q3, u0, v0, u1, v1, nrm) -> {
            ev(vc, m, n, q0, r, g, b, alpha, u0, v1, light, nrm);
            ev(vc, m, n, q1, r, g, b, alpha, u1, v1, light, nrm);
            ev(vc, m, n, q2, r, g, b, alpha, u1, v0, light, nrm);
            ev(vc, m, n, q3, r, g, b, alpha, u0, v0, light, nrm);
        });
    }

    static void ev(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3d p, float r, float g, float b, float a, float u, float v, int light, Vec3d nrm) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(n, (float) nrm.x, (float) nrm.y, (float) nrm.z).next();
    }

    static void emitBoxSimple(VertexConsumer vc, Matrix4f m, Part p, float r, float g, float b, float a) {
        faces(p, p.mat() == Mat.WHITE, (q0, q1, q2, q3, u0, v0, u1, v1, nrm) -> {
            sv(vc, m, q0, r, g, b, a, u0, v1);
            sv(vc, m, q1, r, g, b, a, u1, v1);
            sv(vc, m, q2, r, g, b, a, u1, v0);
            sv(vc, m, q3, r, g, b, a, u0, v0);
        });
    }

    static void sv(VertexConsumer vc, Matrix4f m, Vec3d p, float r, float g, float b, float a, float u, float v) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).texture(u, v).next();
    }

    static void emitPoly(VertexConsumer vc, Matrix4f m, Matrix3f n, Poly poly, float r, float g, float b, float a, int light) {
        Vec3d[] q = poly.pts();
        Vec3d nrm = q[1].subtract(q[0]).crossProduct(q[2].subtract(q[0]));
        if (nrm.lengthSquared() < 1e-8) nrm = UP;
        nrm = nrm.normalize();
        for (Vec3d p : q) {
            Vec3d d = p.subtract(poly.origin());
            float u = (float) (d.dotProduct(poly.uAxis()) / 3.0), v = (float) (d.dotProduct(poly.vAxis()) / 3.0);
            ev(vc, m, n, p, r, g, b, a, u, v, light, nrm);
        }
    }

    static void emitPolySimple(VertexConsumer vc, Matrix4f m, Poly poly, float r, float g, float b, float a) {
        for (Vec3d p : poly.pts()) {
            Vec3d d = p.subtract(poly.origin());
            float u = (float) (d.dotProduct(poly.uAxis()) / 3.0), v = (float) (d.dotProduct(poly.vAxis()) / 3.0);
            sv(vc, m, p, r, g, b, a, u, v);
        }
    }

    public static void clear() {
        SAMPLES.clear();
    }

    private ElderDragon() {
    }
}
