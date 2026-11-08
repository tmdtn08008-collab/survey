package com.abilitywar.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/** HUD 도형 (부채꼴 쿨타임, 테두리 진행도). */
public final class HudDraw {
    /** 정사각형 안을 12시부터 시계방향으로 fraction 만큼 남기는 어두운 부채꼴. */
    public static void squarePie(DrawContext ctx, float x, float y, float size, float fromFrac, float toFrac, int argb) {
        if (toFrac <= fromFrac) return;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        float cx = x + size / 2, cy = y + size / 2, half = size / 2;
        float a = ((argb >>> 24) & 255) / 255f, r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        bb.vertex(m, cx, cy, 0).color(r, g, b, a).next();
        int steps = Math.max(2, (int) ((toFrac - fromFrac) * 64));
        for (int i = 0; i <= steps; i++) {
            float f = fromFrac + (toFrac - fromFrac) * i / steps;
            double ang = f * Math.PI * 2;
            float dx = (float) Math.sin(ang), dy = (float) -Math.cos(ang);
            float k = half / Math.max(Math.abs(dx), Math.abs(dy));
            bb.vertex(m, cx + dx * k, cy + dy * k, 0).color(r, g, b, a).next();
        }
        Tessellator.getInstance().draw();
        RenderSystem.disableBlend();
    }

    /** 사각형 테두리를 12시부터 시계방향으로 fraction 만큼 그린다. */
    public static void borderProgress(DrawContext ctx, float x, float y, float size, float frac, float th, int argb) {
        if (frac <= 0) return;
        float per = size * 4;
        float len = per * Math.min(1, frac);
        float cx = x + size / 2;
        // 경로: 위쪽 중앙 -> 오른쪽 위 -> 오른쪽 아래 -> 왼쪽 아래 -> 왼쪽 위 -> 위쪽 중앙
        float[][] pts = {{cx, y}, {x + size, y}, {x + size, y + size}, {x, y + size}, {x, y}, {cx, y}};
        float remaining = len;
        for (int i = 0; i < pts.length - 1 && remaining > 0; i++) {
            float sx = pts[i][0], sy = pts[i][1], ex = pts[i + 1][0], ey = pts[i + 1][1];
            float segLen = Math.abs(ex - sx) + Math.abs(ey - sy);
            float use = Math.min(segLen, remaining);
            float k = segLen <= 0 ? 0 : use / segLen;
            float fx = sx + (ex - sx) * k, fy = sy + (ey - sy) * k;
            fillF(ctx, Math.min(sx, fx) - (sy == ey ? 0 : th / 2), Math.min(sy, fy) - (sx == ex ? 0 : th / 2),
                    Math.max(sx, fx) + (sy == ey ? 0 : th / 2), Math.max(sy, fy) + (sx == ex ? 0 : th / 2), argb, th, sx == ex);
            remaining -= use;
        }
    }

    private static void fillF(DrawContext ctx, float x0, float y0, float x1, float y1, int argb, float th, boolean vertical) {
        if (vertical) {
            x0 = x0;
            x1 = x0 + th;
        } else {
            y1 = y0 + th;
        }
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        float a = ((argb >>> 24) & 255) / 255f, r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bb.vertex(m, x0, y1, 0).color(r, g, b, a).next();
        bb.vertex(m, x1, y1, 0).color(r, g, b, a).next();
        bb.vertex(m, x1, y0, 0).color(r, g, b, a).next();
        bb.vertex(m, x0, y0, 0).color(r, g, b, a).next();
        Tessellator.getInstance().draw();
        RenderSystem.disableBlend();
    }

    public static void rect(DrawContext ctx, float x0, float y0, float x1, float y1, int argb) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        float a = ((argb >>> 24) & 255) / 255f, r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bb.vertex(m, x0, y1, 0).color(r, g, b, a).next();
        bb.vertex(m, x1, y1, 0).color(r, g, b, a).next();
        bb.vertex(m, x1, y0, 0).color(r, g, b, a).next();
        bb.vertex(m, x0, y0, 0).color(r, g, b, a).next();
        Tessellator.getInstance().draw();
        RenderSystem.disableBlend();
    }

    public static int argb(float a, int rgb) {
        return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private HudDraw() {
    }
}
