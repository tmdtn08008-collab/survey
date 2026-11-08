package com.abilitywar.client.fx;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 카메라 기준 좌표(이미 -camPos)로 그리는 도형 도우미. 색은 0..1. */
public final class Draw {
    private static final Vector3f A = new Vector3f(), B = new Vector3f(), C = new Vector3f(), D = new Vector3f();

    public static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a, float u, float v) {
        vc.vertex(m, x, y, z).color(r, g, b, a).texture(u, v).next();
    }

    public static void vc(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).next();
    }

    /** 카메라를 향한 사각형. */
    public static void billboard(VertexConsumer vc, Matrix4f m, Quaternionf camRot, double x, double y, double z, float size, float rot,
                                 float r, float g, float b, float a) {
        float cs = (float) Math.cos(rot) * size, sn = (float) Math.sin(rot) * size;
        A.set(-cs + sn, -sn - cs, 0).rotate(camRot);
        B.set(-cs - sn, -sn + cs, 0).rotate(camRot);
        C.set(cs - sn, sn + cs, 0).rotate(camRot);
        D.set(cs + sn, sn - cs, 0).rotate(camRot);
        float fx = (float) x, fy = (float) y, fz = (float) z;
        v(vc, m, fx + A.x, fy + A.y, fz + A.z, r, g, b, a, 0, 1);
        v(vc, m, fx + B.x, fy + B.y, fz + B.z, r, g, b, a, 0, 0);
        v(vc, m, fx + C.x, fy + C.y, fz + C.z, r, g, b, a, 1, 0);
        v(vc, m, fx + D.x, fy + D.y, fz + D.z, r, g, b, a, 1, 1);
    }

    /** 임의 방향 사각형: 중심 c, 반축 ax, ay. */
    public static void quad(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d ax, Vec3d ay, float r, float g, float b, float a) {
        quadUV(vc, m, c, ax, ay, r, g, b, a, 0, 0, 1, 1);
    }

    public static void quadUV(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d ax, Vec3d ay, float r, float g, float b, float a, float u0, float v0, float u1, float v1) {
        v(vc, m, (float) (c.x - ax.x - ay.x), (float) (c.y - ax.y - ay.y), (float) (c.z - ax.z - ay.z), r, g, b, a, u0, v1);
        v(vc, m, (float) (c.x - ax.x + ay.x), (float) (c.y - ax.y + ay.y), (float) (c.z - ax.z + ay.z), r, g, b, a, u0, v0);
        v(vc, m, (float) (c.x + ax.x + ay.x), (float) (c.y + ax.y + ay.y), (float) (c.z + ax.z + ay.z), r, g, b, a, u1, v0);
        v(vc, m, (float) (c.x + ax.x - ay.x), (float) (c.y + ax.y - ay.y), (float) (c.z + ax.z - ay.z), r, g, b, a, u1, v1);
    }

    /** 수평 바닥 사각형 (회전 rot). */
    public static void flat(VertexConsumer vc, Matrix4f m, double x, double y, double z, float size, float rot, float r, float g, float b, float a) {
        double cs = Math.cos(rot) * size, sn = Math.sin(rot) * size;
        quad(vc, m, new Vec3d(x, y, z), new Vec3d(cs, 0, sn), new Vec3d(-sn, 0, cs), r, g, b, a);
    }

    /** 속도 방향으로 늘어난 줄기 (카메라 향함). */
    public static void streak(VertexConsumer vc, Matrix4f m, Vec3d p, Vec3d dir, float len, float width, float r, float g, float b, float a) {
        Vec3d d = dir.lengthSquared() < 1e-8 ? new Vec3d(0, 1, 0) : dir.normalize();
        Vec3d toCam = p.multiply(-1);
        Vec3d side = d.crossProduct(toCam);
        if (side.lengthSquared() < 1e-8) side = d.crossProduct(new Vec3d(0, 1, 0.01));
        side = side.normalize().multiply(width);
        Vec3d tail = p.subtract(d.multiply(len));
        v(vc, m, (float) (tail.x - side.x), (float) (tail.y - side.y), (float) (tail.z - side.z), r, g, b, 0, 0, 1);
        v(vc, m, (float) (tail.x + side.x), (float) (tail.y + side.y), (float) (tail.z + side.z), r, g, b, 0, 0, 0);
        v(vc, m, (float) (p.x + side.x), (float) (p.y + side.y), (float) (p.z + side.z), r, g, b, a, 1, 0);
        v(vc, m, (float) (p.x - side.x), (float) (p.y - side.y), (float) (p.z - side.z), r, g, b, a, 1, 1);
    }

    /** 점들을 잇는 리본 (카메라 향함). */
    public static void ribbon(VertexConsumer vc, Matrix4f m, Vec3d[] pts, int count, float[] widths, float r, float g, float b, float[] alphas, float uScale, float uOffset) {
        if (count < 2) return;
        Vec3d prevL = null, prevR = null;
        float prevU = 0, prevA = 0;
        float u = uOffset;
        for (int i = 0; i < count; i++) {
            Vec3d p = pts[i];
            Vec3d d = i < count - 1 ? pts[i + 1].subtract(p) : p.subtract(pts[i - 1]);
            if (d.lengthSquared() < 1e-10) d = new Vec3d(0, 0.001, 0);
            Vec3d side = d.crossProduct(p.multiply(-1));
            if (side.lengthSquared() < 1e-10) side = d.crossProduct(new Vec3d(0, 1, 0.01));
            side = side.normalize().multiply(widths[i]);
            Vec3d L = p.add(side), R = p.subtract(side);
            if (i > 0) u += (float) (pts[i].distanceTo(pts[i - 1]) * uScale);
            if (prevL != null) {
                v(vc, m, (float) prevL.x, (float) prevL.y, (float) prevL.z, r, g, b, prevA, prevU, 0);
                v(vc, m, (float) prevR.x, (float) prevR.y, (float) prevR.z, r, g, b, prevA, prevU, 1);
                v(vc, m, (float) R.x, (float) R.y, (float) R.z, r, g, b, alphas[i], u, 1);
                v(vc, m, (float) L.x, (float) L.y, (float) L.z, r, g, b, alphas[i], u, 0);
            }
            prevL = L;
            prevR = R;
            prevU = u;
            prevA = alphas[i];
        }
    }

    /** 고리(원반). 기준축 ax, ay (단위벡터), u=각도, v=반지름. */
    public static void annulus(VertexConsumer vc, Matrix4f m, Vec3d c, Vec3d ax, Vec3d ay, float r0, float r1, int seg, float uRot, float uRepeat,
                               float r, float g, float b, float aIn, float aOut) {
        for (int i = 0; i < seg; i++) {
            double t0 = (double) i / seg, t1 = (double) (i + 1) / seg;
            double a0 = t0 * Math.PI * 2, a1 = t1 * Math.PI * 2;
            Vec3d d0 = ax.multiply(Math.cos(a0)).add(ay.multiply(Math.sin(a0)));
            Vec3d d1 = ax.multiply(Math.cos(a1)).add(ay.multiply(Math.sin(a1)));
            float u0 = (float) (t0 * uRepeat + uRot), u1 = (float) (t1 * uRepeat + uRot);
            Vec3d p00 = c.add(d0.multiply(r0)), p01 = c.add(d0.multiply(r1)), p11 = c.add(d1.multiply(r1)), p10 = c.add(d1.multiply(r0));
            v(vc, m, (float) p00.x, (float) p00.y, (float) p00.z, r, g, b, aIn, u0, 0);
            v(vc, m, (float) p01.x, (float) p01.y, (float) p01.z, r, g, b, aOut, u0, 1);
            v(vc, m, (float) p11.x, (float) p11.y, (float) p11.z, r, g, b, aOut, u1, 1);
            v(vc, m, (float) p10.x, (float) p10.y, (float) p10.z, r, g, b, aIn, u1, 0);
        }
    }

    /** 단색 구 (POSITION_COLOR). */
    public static void sphereColor(VertexConsumer vc, Matrix4f m, Vec3d c, float radius, int lat, int lon, float r, float g, float b, float a) {
        for (int i = 0; i < lat; i++) {
            double th0 = Math.PI * i / lat - Math.PI / 2, th1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double ph0 = 2 * Math.PI * j / lon, ph1 = 2 * Math.PI * (j + 1) / lon;
                sp(vc, m, c, radius, th0, ph0, r, g, b, a);
                sp(vc, m, c, radius, th1, ph0, r, g, b, a);
                sp(vc, m, c, radius, th1, ph1, r, g, b, a);
                sp(vc, m, c, radius, th0, ph1, r, g, b, a);
            }
        }
    }

    private static void sp(VertexConsumer vc, Matrix4f m, Vec3d c, float rad, double th, double ph, float r, float g, float b, float a) {
        float x = (float) (c.x + Math.cos(th) * Math.cos(ph) * rad);
        float y = (float) (c.y + Math.sin(th) * rad);
        float z = (float) (c.z + Math.cos(th) * Math.sin(ph) * rad);
        vc.vertex(m, x, y, z).color(r, g, b, a).next();
    }

    /** 위치만 있는 구 (엔드 포털 레이어). */
    public static void spherePos(VertexConsumer vc, Matrix4f m, Vec3d c, float radius, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            double th0 = Math.PI * i / lat - Math.PI / 2, th1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double ph0 = 2 * Math.PI * j / lon, ph1 = 2 * Math.PI * (j + 1) / lon;
                pp(vc, m, c, radius, th0, ph0);
                pp(vc, m, c, radius, th1, ph0);
                pp(vc, m, c, radius, th1, ph1);
                pp(vc, m, c, radius, th0, ph1);
            }
        }
    }

    private static void pp(VertexConsumer vc, Matrix4f m, Vec3d c, float rad, double th, double ph) {
        vc.vertex(m, (float) (c.x + Math.cos(th) * Math.cos(ph) * rad), (float) (c.y + Math.sin(th) * rad), (float) (c.z + Math.cos(th) * Math.sin(ph) * rad)).next();
    }

    /** 지그재그 번개 (가산 단색). */
    public static void lightning(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, long seed, float width, int segs, float jitter, float r, float g, float bl, float alpha) {
        java.util.Random rnd = new java.util.Random(seed);
        Vec3d prev = a;
        Vec3d dir = b.subtract(a);
        for (int i = 1; i <= segs; i++) {
            double t = (double) i / segs;
            Vec3d p = a.add(dir.multiply(t));
            if (i < segs) p = p.add((rnd.nextDouble() - 0.5) * jitter, (rnd.nextDouble() - 0.5) * jitter, (rnd.nextDouble() - 0.5) * jitter);
            Vec3d d = p.subtract(prev);
            Vec3d side = d.crossProduct(prev.multiply(-1));
            if (side.lengthSquared() < 1e-10) side = new Vec3d(0, 1, 0);
            side = side.normalize().multiply(width);
            vc(vc, m, (float) (prev.x - side.x), (float) (prev.y - side.y), (float) (prev.z - side.z), r, g, bl, alpha);
            vc(vc, m, (float) (prev.x + side.x), (float) (prev.y + side.y), (float) (prev.z + side.z), r, g, bl, alpha);
            vc(vc, m, (float) (p.x + side.x), (float) (p.y + side.y), (float) (p.z + side.z), r, g, bl, alpha);
            vc(vc, m, (float) (p.x - side.x), (float) (p.y - side.y), (float) (p.z - side.z), r, g, bl, alpha);
            prev = p;
        }
    }

    private Draw() {
    }
}
