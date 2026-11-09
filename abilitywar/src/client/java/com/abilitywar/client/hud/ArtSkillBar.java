package com.abilitywar.client.hud;

import com.abilitywar.ability.SkillSlot;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.input.InputHandler;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.registry.AWEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 그림으로 된 능력 전용 스킬바. 쿨타임/궁극기 게이지는 슬롯 모양 마스크로 잘라서 그 위에 덮는다.
 */
public final class ArtSkillBar {
    private static final String[] NAMES = {"skill1", "skill2", "skill3", "skill4", "ultimate", "passive"};

    // 현재 프레임의 배치 (화면 좌표)
    private static float bx, by, bw, bh, sc;
    private static HudLayouts.Layout L;

    public static void render(DrawContext ctx, float td, HudLayouts.Layout layout) {
        MinecraftClient mc = MinecraftClient.getInstance();
        L = layout;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        // 단축바와 겹치지 않는 크기 (오른쪽 아래)
        float room = sw / 2f - 91 - 10;
        bw = Math.max(96, Math.min(176, room));
        sc = bw / layout.width;
        bh = layout.height * sc;
        bx = sw - bw - 3;
        by = sh - bh - 2;

        setFilter(mc, layout.texture);
        setFilter(mc, layout.mask);
        texQuad(ctx, layout.texture, bx, by, bx + bw, by + bh, 0, 0, 1, 1, 1, 1, 1, 1);

        boolean locked = mc.player.hasStatusEffect(AWEffects.STUN) || ((AWPlayer) mc.player).aw$hasFlag(AWPlayer.FLAG_CINEMATIC) || ClientState.busy > 0;
        float time = mc.player.age + td;
        KeyBinding[] keys = {InputHandler.SKILL1, InputHandler.SKILL2, InputHandler.SKILL3, InputHandler.SKILL4, InputHandler.ULTIMATE, InputHandler.PASSIVE};

        for (int i = 0; i < 4; i++) skill(i, td, locked);
        ultimate(td, time, locked);
        passive(td, time);
        isAdditive = false;
        RenderSystem.defaultBlendFunc();
        TextRenderer tr = mc.textRenderer;
        for (int i = 0; i < 6; i++) relabel(ctx, tr, NAMES[i], keys[i]);
        isAdditive = false;
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void skill(int i, float td, boolean locked) {
        float[] s = L.slots.get(NAMES[i]);
        if (s == null) return;
        float frac = ClientState.cooldownFraction(i, td);
        if (frac > 0) {
            maskPie(s, 1 - frac, 1f, 0, 0, 0, 0.66f);
            sweepLine(s, 1 - frac, 0.85f);
        }
        if (locked) maskRect(s, 0, 1, 0.12f, 0.1f, 0.14f, 0.45f);
        float press = ClientState.pressFlash[i];
        if (press > 0) maskRect(s, 0, 1, 1, 1, 1, press * 0.28f);
        float flash = ClientState.readyFlash[i];
        if (flash > 0) {
            additive(true);
            maskRect(s, 0, 1, 0.85f, 0.7f, 1f, flash * 0.8f);
            additive(false);
        }
    }

    private static void ultimate(float td, float time, boolean locked) {
        float[] s = L.slots.get("ultimate");
        if (s == null) return;
        float charge = Math.min(1f, ClientState.ultDisplay / 100f);
        boolean ready = ClientState.ult >= 99.99f;
        if (!ready) {
            // 아직 안 찬 부분(위쪽)을 어둡게, 경계선은 빛나게
            float level = 1 - charge;
            maskRect(s, 0, level, 0, 0, 0, 0.62f);
            float wave = (float) Math.sin(time * 0.25) * 0.012f;
            additive(true);
            maskRect(s, Math.max(0, level - 0.035f + wave), Math.min(1, level + 0.01f + wave), 0.75f, 0.55f, 1f, 0.9f);
            maskRect(s, level, 1, 0.45f, 0.2f, 0.85f, 0.12f);
            additive(false);
        } else {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.3);
            additive(true);
            maskRect(s, 0, 1, 0.7f, 0.45f, 1f, 0.12f + 0.22f * pulse);
            additive(false);
        }
        if (locked) maskRect(s, 0, 1, 0.12f, 0.1f, 0.14f, 0.4f);
        float flash = ClientState.readyFlash[SkillSlot.ULTIMATE.ordinal()];
        if (flash > 0) {
            additive(true);
            maskRect(s, 0, 1, 1, 0.9f, 1, flash);
            additive(false);
        }
    }

    private static void passive(float td, float time) {
        float[] s = L.slots.get("passive");
        if (s == null) return;
        int i = SkillSlot.PASSIVE.ordinal();
        if (ClientState.shadowForm) {
            float k = Math.max(0, Math.min(1, (ClientState.shadowRemaining - td) / 80f));
            // 그림자 속: 남은 시간만큼 밝은 부채꼴 + 바늘
            maskPie(s, k, 1f, 0, 0, 0, 0.55f);
            additive(true);
            maskPie(s, 0, k, 0.6f, 0.4f, 1f, 0.55f + 0.2f * (float) Math.sin(time * 0.5));
            additive(false);
            sweepLine(s, k, 1f);
            return;
        }
        float frac = ClientState.cooldownFraction(i, td);
        if (frac > 0) {
            maskPie(s, 1 - frac, 1f, 0, 0, 0, 0.66f);
            sweepLine(s, 1 - frac, 0.85f);
        }
        float flash = ClientState.readyFlash[i];
        if (flash > 0) {
            additive(true);
            maskRect(s, 0, 1, 0.85f, 0.7f, 1f, flash * 0.8f);
            additive(false);
        }
    }

    /** 키를 바꿨으면 그림에 박힌 글자 위에 새 글자를 덮어 그린다. */
    private static void relabel(DrawContext ctx, TextRenderer tr, String name, KeyBinding key) {
        if (key == null || key.isDefault()) return;
        float[] b = L.labels.get(name);
        if (b == null) return;
        float x0 = bx + b[0] * sc, y0 = by + b[1] * sc, x1 = bx + b[2] * sc, y1 = by + b[3] * sc;
        HudDraw.rect(ctx, x0, y0, x1, y1, 0xF0120A1E);
        String label = key.getBoundKeyLocalizedText().getString();
        if (label.length() > 3) label = label.substring(0, 3);
        float scale = Math.min(1f, (y1 - y0) / 9f);
        ctx.getMatrices().push();
        ctx.getMatrices().translate((x0 + x1) / 2f - tr.getWidth(label) * scale / 2f, (y0 + y1) / 2f - 4 * scale, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawTextWithShadow(tr, label, 0, 0, 0xFFFFFF);
        ctx.getMatrices().pop();
    }

    // ------------------------------------------------------------------ 그리기 도우미

    private static Matrix4f M = new Matrix4f();
    private static boolean isAdditive;

    private static void setFilter(MinecraftClient mc, Identifier id) {
        mc.getTextureManager().getTexture(id).setFilter(true, false);
    }

    private static void additive(boolean on) {
        isAdditive = on;
    }

    private static void applyBlend() {
        RenderSystem.enableBlend();
        if (isAdditive) RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        else RenderSystem.defaultBlendFunc();
    }

    private static void texQuad(DrawContext ctx, Identifier tex, float x0, float y0, float x1, float y1,
                                float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        M = new Matrix4f(ctx.getMatrices().peek().getPositionMatrix());
        isAdditive = false;
        applyBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(M, x0, y1, 0).texture(u0, v1).color(r, g, b, a).next();
        bb.vertex(M, x1, y1, 0).texture(u1, v1).color(r, g, b, a).next();
        bb.vertex(M, x1, y0, 0).texture(u1, v0).color(r, g, b, a).next();
        bb.vertex(M, x0, y0, 0).texture(u0, v0).color(r, g, b, a).next();
        Tessellator.getInstance().draw();
    }

    /** 슬롯 안쪽 영역 중 세로 [t0,t1] 부분을 마스크 모양으로 칠한다. */
    private static void maskRect(float[] s, float t0, float t1, float r, float g, float b, float a) {
        if (t1 <= t0 || a <= 0) return;
        float sy0 = s[1] + (s[3] - s[1]) * t0, sy1 = s[1] + (s[3] - s[1]) * t1;
        float x0 = bx + s[0] * sc, x1 = bx + s[2] * sc, y0 = by + sy0 * sc, y1 = by + sy1 * sc;
        applyBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, L.mask);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(M, x0, y1, 0).texture(s[0] / L.width, sy1 / L.height).color(r, g, b, a).next();
        bb.vertex(M, x1, y1, 0).texture(s[2] / L.width, sy1 / L.height).color(r, g, b, a).next();
        bb.vertex(M, x1, y0, 0).texture(s[2] / L.width, sy0 / L.height).color(r, g, b, a).next();
        bb.vertex(M, x0, y0, 0).texture(s[0] / L.width, sy0 / L.height).color(r, g, b, a).next();
        Tessellator.getInstance().draw();
    }

    /** 12시부터 시계방향 [from,to] 부채꼴을 마스크 모양으로 칠한다. */
    private static void maskPie(float[] s, float from, float to, float r, float g, float b, float a) {
        if (to <= from || a <= 0) return;
        float cxo = (s[0] + s[2]) / 2f, cyo = (s[1] + s[3]) / 2f;
        float hw = (s[2] - s[0]) / 2f, hh = (s[3] - s[1]) / 2f;
        applyBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, L.mask);
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        RenderSystem.disableCull();
        bb.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(M, bx + cxo * sc, by + cyo * sc, 0).texture(cxo / L.width, cyo / L.height).color(r, g, b, a).next();
        int steps = Math.max(2, (int) ((to - from) * 72));
        for (int k = 0; k <= steps; k++) {
            float f = from + (to - from) * k / steps;
            double ang = f * Math.PI * 2;
            float dx = (float) Math.sin(ang), dy = (float) -Math.cos(ang);
            float kk = Math.min(hw / Math.max(1e-4f, Math.abs(dx)), hh / Math.max(1e-4f, Math.abs(dy)));
            float ox = cxo + dx * kk, oy = cyo + dy * kk;
            bb.vertex(M, bx + ox * sc, by + oy * sc, 0).texture(ox / L.width, oy / L.height).color(r, g, b, a).next();
        }
        Tessellator.getInstance().draw();
        RenderSystem.enableCull();
    }

    /** 부채꼴 경계의 밝은 선 (시계 바늘). */
    private static void sweepLine(float[] s, float f, float a) {
        additive(true);
        maskPie(s, Math.max(0, f - 0.012f), Math.min(1, f + 0.004f), 0.8f, 0.6f, 1f, a);
        additive(false);
    }

    private ArtSkillBar() {
    }
}
