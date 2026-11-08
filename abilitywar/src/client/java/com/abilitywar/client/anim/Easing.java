package com.abilitywar.client.anim;

import java.util.Locale;

/** 키프레임 이징 함수. 이름은 easings.net 표기 (inOutCubic, outBack ...). */
public enum Easing {
    LINEAR, STEP, SMOOTH,
    IN_SINE, OUT_SINE, IN_OUT_SINE,
    IN_QUAD, OUT_QUAD, IN_OUT_QUAD,
    IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC,
    IN_QUART, OUT_QUART, IN_OUT_QUART,
    IN_QUINT, OUT_QUINT, IN_OUT_QUINT,
    IN_EXPO, OUT_EXPO, IN_OUT_EXPO,
    IN_CIRC, OUT_CIRC, IN_OUT_CIRC,
    IN_BACK, OUT_BACK, IN_OUT_BACK,
    IN_ELASTIC, OUT_ELASTIC,
    IN_BOUNCE, OUT_BOUNCE;

    public static Easing parse(String s) {
        if (s == null) return LINEAR;
        String n = s.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (n.startsWith("ease")) n = n.substring(4);
        if (n.equals("catmullrom") || n.equals("catmull")) return SMOOTH;
        for (Easing e : values()) {
            if (e.name().toLowerCase(Locale.ROOT).replace("_", "").equals(n)) return e;
        }
        return LINEAR;
    }

    public float apply(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return this == STEP ? 1 : (this == OUT_ELASTIC || this == IN_ELASTIC ? 1 : 1);
        final double PI = Math.PI;
        double r = switch (this) {
            case LINEAR, SMOOTH -> x;
            case STEP -> 0;
            case IN_SINE -> 1 - Math.cos(x * PI / 2);
            case OUT_SINE -> Math.sin(x * PI / 2);
            case IN_OUT_SINE -> -(Math.cos(PI * x) - 1) / 2;
            case IN_QUAD -> x * x;
            case OUT_QUAD -> 1 - (1 - x) * (1 - x);
            case IN_OUT_QUAD -> x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
            case IN_CUBIC -> x * x * x;
            case OUT_CUBIC -> 1 - Math.pow(1 - x, 3);
            case IN_OUT_CUBIC -> x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
            case IN_QUART -> Math.pow(x, 4);
            case OUT_QUART -> 1 - Math.pow(1 - x, 4);
            case IN_OUT_QUART -> x < 0.5 ? 8 * Math.pow(x, 4) : 1 - Math.pow(-2 * x + 2, 4) / 2;
            case IN_QUINT -> Math.pow(x, 5);
            case OUT_QUINT -> 1 - Math.pow(1 - x, 5);
            case IN_OUT_QUINT -> x < 0.5 ? 16 * Math.pow(x, 5) : 1 - Math.pow(-2 * x + 2, 5) / 2;
            case IN_EXPO -> Math.pow(2, 10 * x - 10);
            case OUT_EXPO -> 1 - Math.pow(2, -10 * x);
            case IN_OUT_EXPO -> x < 0.5 ? Math.pow(2, 20 * x - 10) / 2 : (2 - Math.pow(2, -20 * x + 10)) / 2;
            case IN_CIRC -> 1 - Math.sqrt(1 - x * x);
            case OUT_CIRC -> Math.sqrt(1 - Math.pow(x - 1, 2));
            case IN_OUT_CIRC -> x < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * x, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * x + 2, 2)) + 1) / 2;
            case IN_BACK -> 2.70158 * x * x * x - 1.70158 * x * x;
            case OUT_BACK -> 1 + 2.70158 * Math.pow(x - 1, 3) + 1.70158 * Math.pow(x - 1, 2);
            case IN_OUT_BACK -> {
                double c2 = 1.70158 * 1.525;
                yield x < 0.5 ? (Math.pow(2 * x, 2) * ((c2 + 1) * 2 * x - c2)) / 2
                        : (Math.pow(2 * x - 2, 2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2;
            }
            case IN_ELASTIC -> -Math.pow(2, 10 * x - 10) * Math.sin((x * 10 - 10.75) * (2 * PI / 3));
            case OUT_ELASTIC -> Math.pow(2, -10 * x) * Math.sin((x * 10 - 0.75) * (2 * PI / 3)) + 1;
            case IN_BOUNCE -> 1 - outBounce(1 - x);
            case OUT_BOUNCE -> outBounce(x);
        };
        return (float) r;
    }

    private static double outBounce(double x) {
        double n1 = 7.5625, d1 = 2.75;
        if (x < 1 / d1) return n1 * x * x;
        if (x < 2 / d1) return n1 * (x -= 1.5 / d1) * x + 0.75;
        if (x < 2.5 / d1) return n1 * (x -= 2.25 / d1) * x + 0.9375;
        return n1 * (x -= 2.625 / d1) * x + 0.984375;
    }
}
