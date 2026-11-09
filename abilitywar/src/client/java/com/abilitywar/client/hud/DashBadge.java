package com.abilitywar.client.hud;

import com.abilitywar.AbilityWar;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.input.InputHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** 공통 대시(Q) 쿨타임 원형 배지. */
public final class DashBadge {
    private static final Identifier ICON = AbilityWar.id("textures/gui/dash.png");

    /** (cx, cy) 중심, 지름 size. */
    public static void render(DrawContext ctx, float td, float cx, float cy, float size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int i = SkillSlot.DASH.ordinal();
        float frac = ClientState.cooldownFraction(i, td);
        float half = size / 2f;
        mc.getTextureManager().getTexture(ICON).setFilter(true, false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        quad(m, ICON, cx - half, cy - half, cx + half, cy + half, frac > 0 ? 0.55f : 1f);
        if (frac > 0) {
            pie(m, cx, cy, half * 0.86f, 1 - frac, 1f, 0, 0, 0, 0.62f);
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            ring(m, cx, cy, half * 0.84f, half * 0.98f, 0, 1 - frac, 0.75f, 0.55f, 1f, 0.9f);
            RenderSystem.defaultBlendFunc();
        }
        float flash = Math.max(ClientState.readyFlash[i], ClientState.pressFlash[i] * 0.6f);
        if (flash > 0) {
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            pie(m, cx, cy, half * 0.95f, 0, 1, 0.85f, 0.7f, 1f, flash * 0.7f);
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableBlend();
        TextRenderer tr = mc.textRenderer;
        String label = InputHandler.DASH.getBoundKeyLocalizedText().getString();
        if (label.length() > 3) label = label.substring(0, 3);
        float sc = Math.max(0.5f, Math.min(0.8f, size / 24f));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx + half - tr.getWidth(label) * sc * 0.6f, cy + half - 6 * sc, 50);
        ctx.getMatrices().scale(sc, sc, 1);
        ctx.drawTextWithShadow(tr, label, 0, 0, 0xFFFFFF);
        ctx.getMatrices().pop();
    }

    private static void quad(Matrix4f m, Identifier tex, float x0, float y0, float x1, float y1, float bright) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(m, x0, y1, 0).texture(0, 1).color(bright, bright, bright, 1).next();
        bb.vertex(m, x1, y1, 0).texture(1, 1).color(bright, bright, bright, 1).next();
        bb.vertex(m, x1, y0, 0).texture(1, 0).color(bright, bright, bright, 1).next();
        bb.vertex(m, x0, y0, 0).texture(0, 0).color(bright, bright, bright, 1).next();
        Tessellator.getInstance().draw();
    }

    private static void pie(Matrix4f m, float cx, float cy, float r, float from, float to, float cr, float cg, float cb, float a) {
        if (to <= from) return;
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        bb.vertex(m, cx, cy, 0).color(cr, cg, cb, a).next();
        int steps = Math.max(2, (int) ((to - from) * 48));
        for (int k = 0; k <= steps; k++) {
            double ang = (from + (to - from) * k / steps) * Math.PI * 2;
            bb.vertex(m, cx + (float) Math.sin(ang) * r, cy - (float) Math.cos(ang) * r, 0).color(cr, cg, cb, a).next();
        }
        Tessellator.getInstance().draw();
        RenderSystem.enableCull();
    }

    private static void ring(Matrix4f m, float cx, float cy, float r0, float r1, float from, float to, float cr, float cg, float cb, float a) {
        if (to <= from) return;
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        int steps = Math.max(2, (int) ((to - from) * 48));
        for (int k = 0; k <= steps; k++) {
            double ang = (from + (to - from) * k / steps) * Math.PI * 2;
            float sx = (float) Math.sin(ang), sy = (float) -Math.cos(ang);
            bb.vertex(m, cx + sx * r0, cy + sy * r0, 0).color(cr, cg, cb, a).next();
            bb.vertex(m, cx + sx * r1, cy + sy * r1, 0).color(cr, cg, cb, a).next();
        }
        Tessellator.getInstance().draw();
        RenderSystem.enableCull();
    }

    private DashBadge() {
    }
}
