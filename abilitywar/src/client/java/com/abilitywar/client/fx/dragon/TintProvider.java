package com.abilitywar.client.fx.dragon;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

/**
 * 요청된 레이어를 무시하고 지정한 레이어 + 색으로 그리게 만드는 래퍼 (환영/잔상용).
 * 첫 번째 getBuffer 만 그리고 나머지(눈 등 추가 패스)는 버린다.
 */
public final class TintProvider implements VertexConsumerProvider {
    private final VertexConsumerProvider base;
    private final RenderLayer layer;
    private final float r, g, b, a;
    private int calls;

    public TintProvider(VertexConsumerProvider base, RenderLayer layer, float r, float g, float b, float a) {
        this.base = base;
        this.layer = layer;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer requested) {
        if (calls++ > 0) return NOOP;
        return new Tint(base.getBuffer(layer), r, g, b, a);
    }

    static final class Tint implements VertexConsumer {
        final VertexConsumer d;
        final float r, g, b, a;

        Tint(VertexConsumer d, float r, float g, float b, float a) {
            this.d = d;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            d.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            d.color((int) (red * r), (int) (green * g), (int) (blue * b), (int) (alpha * a));
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            d.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            d.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            d.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            d.normal(x, y, z);
            return this;
        }

        @Override
        public void next() {
            d.next();
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            d.vertex(x, y, z, red * r, green * g, blue * b, alpha * a, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
            d.fixedColor(red, green, blue, alpha);
        }

        @Override
        public void unfixColor() {
            d.unfixColor();
        }
    }

    static final VertexConsumer NOOP = new VertexConsumer() {
        public VertexConsumer vertex(double x, double y, double z) { return this; }
        public VertexConsumer color(int red, int green, int blue, int alpha) { return this; }
        public VertexConsumer texture(float u, float v) { return this; }
        public VertexConsumer overlay(int u, int v) { return this; }
        public VertexConsumer light(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void next() { }
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float nx, float ny, float nz) { }
        public void fixedColor(int red, int green, int blue, int alpha) { }
        public void unfixColor() { }
    };
}
