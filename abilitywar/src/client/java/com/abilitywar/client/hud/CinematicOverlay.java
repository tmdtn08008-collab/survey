package com.abilitywar.client.hud;

import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.Tex;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

/** 궁극기 연출 화면: 레터박스, 비네트, 제목. */
public final class CinematicOverlay {
    /** 능력별 연출 테마. */
    record Theme(String key, float vr, float vg, float vb, int accent, int glow, int title, int sub, int caption, int flash,
                 float cut1, float cut2, float titleAt, float captionUntil) {
    }

    static final Theme VOID = new Theme("void", 0.08f, 0.0f, 0.16f, 0x9B5CFF, 0x9B5CFF, 0xF4EAFF, 0xD9C2FF, 0xD9C2FF, 0xF4EAFF, 24, 46, 46, 44);
    static final Theme BLADE = new Theme("blade", 0.12f, 0.02f, 0.18f, 0xB44CFF, 0x8A2BE2, 0xF5E8FF, 0xD8B8FF, 0xD8B8FF, 0xF0D8FF, 20, 46, 48, 40);
    static final Theme DRAGON = new Theme("dragon", 0.2f, 0.05f, 0.0f, 0xFF7A2E, 0xFF4A12, 0xFFF1DC, 0xFFD2A0, 0xFFC890, 0xFFE6C0, 26, 56, 58, 42);
    static final Theme CANTOR = new Theme("cantor", 0.2f, 0.16f, 0.05f, 0xFFD86A, 0xFFC040, 0xFFF6DC, 0xFFE6A0, 0xFFE6A0, 0xFFF6DC, 16, 48, 40, 36);
    static final Theme WARLOCK = new Theme("warlock", 0.02f, 0.1f, 0.06f, 0x48E8A0, 0x2AA870, 0xE0FFF0, 0xA8F0CC, 0xA8F0CC, 0xD8FFE8, 16, 26, 26, 24);
    static final Theme ANGEL = new Theme("angel", 0.22f, 0.2f, 0.12f, 0xFFF0C0, 0xFFD978, 0xFFFDF4, 0xFFF0C8, 0xFFF0C8, 0xFFFFFF, 14, 30, 34, 30);
    static final Theme GUARDIAN = new Theme("guardian", 0.16f, 0.13f, 0.05f, 0xF0C860, 0xD8A040, 0xFFF8E4, 0xF0E0B0, 0xF0E0B0, 0xFFF6DC, 12, 22, 30, 26);
    static final Theme PALADIN = new Theme("paladin", 0.22f, 0.19f, 0.1f, 0xFFE7A0, 0xFFD978, 0xFFFDF4, 0xFFF0C8, 0xFFF0C8, 0xFFFFFF, 12, 24, 30, 26);
    static final Theme BLOOD = new Theme("blood", 0.24f, 0.0f, 0.02f, 0xFF2A3A, 0xD0102A, 0xFFE4E4, 0xFFB4B4, 0xFFB0B0, 0xFFD8D8, 16, 58, 36, 34);

    static Theme theme() {
        String ty = CameraController.cinematicType();
        return ty.startsWith("dragon") ? DRAGON : ty.startsWith("blade") ? BLADE : ty.startsWith("blood") ? BLOOD : ty.startsWith("cantor") ? CANTOR : ty.startsWith("warlock") ? WARLOCK : ty.startsWith("angel") ? ANGEL : ty.startsWith("guardian") ? GUARDIAN : ty.startsWith("paladin") ? PALADIN : VOID;
    }

    public static void render(DrawContext ctx, float td) {
        if (CameraController.isPrelude()) {
            prelude(ctx, td, theme());
            return;
        }
        Theme th = theme();
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float t = CameraController.cinematicTime(td);
        int dur = CameraController.cinematicDuration();
        float in = Math.min(1, t / 6f);
        float out = Math.min(1, Math.max(0, (dur - t) / 6f));
        float k = Math.min(in, out);
        k = k * k * (3 - 2 * k);
        // 혈월만개: 하늘과 땅을 핏빛으로 (곱하기 합성)
        if (th == BLOOD) {
            float red = k * Math.min(1, t / 14f);
            multiply(ctx, sw, sh, 1f, 1f - 0.62f * red, 1f - 0.58f * red);
        }
        // 천상의 심판: 하늘에서 쏟아지는 빛 (위쪽이 밝게)
        if (th == ANGEL || th == PALADIN) {
            float holy = k * Math.min(1, t / 16f);
            ctx.fillGradient(0, 0, sw, (int) (sh * 0.6f), HudDraw.argb(0.32f * holy, 0xFFF6DC), HudDraw.argb(0f, 0xFFF6DC));
        }
        // 비네트
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(th.vr(), th.vg(), th.vb(), 0.95f * k);
        ctx.drawTexture(Tex.VIGNETTE, 0, 0, sw, sh, 0, 0, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // 레터박스
        float bar = sh * 0.12f * k;
        HudDraw.rect(ctx, 0, 0, sw, bar, 0xFF000000);
        HudDraw.rect(ctx, 0, sh - bar, sw, sh, 0xFF000000);
        HudDraw.rect(ctx, 0, bar, sw, bar + 1, HudDraw.argb(0.5f * k, th.accent()));
        HudDraw.rect(ctx, 0, sh - bar - 1, sw, sh - bar, HudDraw.argb(0.5f * k, th.accent()));
        // 시전자
        Entity caster = CameraController.cinematicCaster();
        if (caster != null && t < th.captionUntil()) {
            float a = Math.min(1, t / 6f) * Math.min(1, (th.captionUntil() - t) / 6f);
            Text cap = Text.translatable("abilitywar.cinematic.caster", caster.getDisplayName());
            ctx.getMatrices().push();
            ctx.getMatrices().translate(16 + (1 - a) * -10, bar + 10, 0);
            ctx.drawTextWithShadow(tr, cap, 0, 0, ((int) (a * 255) << 24) | th.caption());
            ctx.getMatrices().pop();
        }
        // 컷 전환 플래시
        float flash = 0;
        float c1 = th.cut1(), c2 = th.cut2();
        if (t >= c1 && t < c1 + 4) flash = Math.max(flash, 1 - (t - c1) / 4f) * 0.35f;
        if (t >= c2 && t < c2 + 6) flash = Math.max(flash, (1 - (t - c2) / 6f) * (th == VOID ? 1f : 0.5f));
        if (th == DRAGON && t >= 66 && t < 72) flash = Math.max(flash, (1 - (t - 66) / 6f) * 0.7f);
        if (flash > 0) HudDraw.rect(ctx, 0, 0, sw, sh, HudDraw.argb(flash * 0.85f, th.flash()));
        // 제목
        if (t >= th.titleAt()) {
            float tt = t - th.titleAt();
            float a = Math.min(1, tt / 5f) * out;
            Text title = Text.translatable("abilitywar.ult." + th.key() + ".title");
            Text sub = Text.translatable("abilitywar.ult." + th.key() + ".subtitle");
            float scale = 3.2f + (1 - Math.min(1, tt / 8f)) * 1.2f;
            int tw = tr.getWidth(title);
            float cy = sh - bar - 46;
            int alpha = (int) (a * 255) << 24;
            net.minecraft.util.Identifier tex = com.abilitywar.AbilityWar.id("textures/gui/ult_title_" + th.key() + ".png");
            if (MinecraftClient.getInstance().getResourceManager().getResource(tex).isPresent()) {
                // 능력별 제목 그림 (용기사 = 한자, 공허 = 영어): 크게 들어왔다가 자리 잡음
                float[] sz = {1024, th == DRAGON || th == BLOOD || th == ANGEL || th == GUARDIAN || th == PALADIN ? 300 : th == BLADE || th == CANTOR ? 260 : 220}; // tools/gen_titles.py 의 그림 크기
                float w = Math.min(sw * 0.62f, 380f);
                float h = w * sz[1] / sz[0];
                float zoom = 1f + 0.35f * (float) Math.pow(1 - Math.min(1, tt / 7f), 3);
                MinecraftClient.getInstance().getTextureManager().getTexture(tex).setFilter(true, false);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(sw / 2f, cy - h * 0.18f, 0);
                ctx.getMatrices().scale(zoom, zoom, 1);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1, 1, 1, a);
                ctx.drawTexture(tex, (int) (-w / 2), (int) (-h / 2), (int) w, (int) h, 0, 0, (int) sz[0], (int) sz[1], (int) sz[0], (int) sz[1]);
                // 처음 들어올 때 번쩍임
                float bloom = Math.max(0, 1 - tt / 6f);
                if (bloom > 0) {
                    RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
                    RenderSystem.setShaderColor(1, 1, 1, bloom * 0.8f * out);
                    ctx.drawTexture(tex, (int) (-w / 2), (int) (-h / 2), (int) w, (int) h, 0, 0, (int) sz[0], (int) sz[1], (int) sz[0], (int) sz[1]);
                    RenderSystem.defaultBlendFunc();
                }
                RenderSystem.setShaderColor(1, 1, 1, 1);
                ctx.getMatrices().pop();
            } else {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sw / 2f, cy, 0);
            ctx.getMatrices().scale(scale, scale, 1);
            for (int i = 0; i < 4; i++) {
                float ox = (i % 2 == 0 ? 1 : -1) * 0.6f, oy = (i < 2 ? 1 : -1) * 0.6f;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(ox, oy, 0);
                ctx.drawText(tr, title, -tw / 2, -4, ((int) (a * 90) << 24) | th.glow(), false);
                ctx.getMatrices().pop();
            }
            ctx.drawText(tr, title, -tw / 2, -4, alpha | th.title(), false);
            ctx.getMatrices().pop();
            }
            // 3차 전직 ("대"): 제목 왼쪽에 붉은 바탕 금테 "大" 도장이 쾅 찍힘
            if (com.abilitywar.client.fx.GrandFx.isGrand(caster)) grandSeal(ctx, tr, sw / 2f - Math.min(sw * 0.62f, 380f) / 2f - 6, cy - 22, tt, out);
            float sa = Math.min(1, Math.max(0, (tt - 3) / 5f)) * out;
            int sw2 = tr.getWidth(sub);
            float lineW = (sw2 * 1.4f + 40) * Math.min(1, tt / 10f);
            HudDraw.rect(ctx, sw / 2f - lineW, cy + 20, sw / 2f + lineW, cy + 21, HudDraw.argb(sa * 0.8f, th == DRAGON ? 0xFF9A50 : th == BLADE ? 0xC870FF : th == BLOOD ? 0xFF3A4A : th == CANTOR ? 0xFFD86A : th == WARLOCK ? 0x48E8A0 : th == ANGEL ? 0xFFE8A0 : th == GUARDIAN ? 0xF0C860 : th == PALADIN ? 0xFFE7A0 : 0xB27BFF));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sw / 2f, cy + 27, 0);
            ctx.getMatrices().scale(1.4f, 1.4f, 1);
            ctx.drawTextWithShadow(tr, sub, -sw2 / 2, 0, ((int) (sa * 255) << 24) | th.sub());
            ctx.getMatrices().pop();
        }
    }

    /**
     * 궁극기 각성 (2초): 레터박스 · 집중선 · 궁극기 이름 컷인 띠 · 마지막 번쩍임.
     * 화면 색 (시간 정지 흑백) 은 PostEffects, 카메라는 CameraController, 몸 주위 효과는 Effects.UltAwaken.
     */
    static void prelude(DrawContext ctx, float td, Theme th) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float t = CameraController.cinematicTime(td);
        int dur = CameraController.cinematicDuration();
        float in = Math.min(1, t / 4f);
        // 비네트 (능력 색)
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(th.vr(), th.vg(), th.vb(), 0.9f * in);
        ctx.drawTexture(Tex.VIGNETTE, 0, 0, sw, sh, 0, 0, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // 집중선: 가운데로 모이는 흰 선, 마지막에 빨라지고 진해짐
        float lines = in * (t < 28 ? 0.45f : 0.45f + 0.5f * Math.min(1, (t - 28) / 6f)) * (t > dur - 3 ? Math.max(0, (dur - t) / 3f) : 1);
        if (lines > 0) {
            java.util.Random r = new java.util.Random((long) (t * (t < 28 ? 0.5f : 1.5f)) * 7919L);
            float cx = sw / 2f, cy = sh / 2f, maxR = (float) Math.hypot(sw, sh) * 0.55f;
            for (int i = 0; i < 64; i++) {
                float ang = r.nextFloat() * 360f;
                float r0 = maxR * (0.32f + r.nextFloat() * 0.3f);
                float w = 0.6f + r.nextFloat() * 1.8f;
                int col = r.nextInt(5) == 0 ? th.accent() : 0xFFFFFF;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(cx, cy, 0);
                ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(ang));
                HudDraw.rect(ctx, r0, -w / 2, maxR, w / 2, HudDraw.argb(lines * (0.25f + r.nextFloat() * 0.45f), col));
                ctx.getMatrices().pop();
            }
        }
        // 레터박스
        float bar = sh * 0.13f * in;
        HudDraw.rect(ctx, 0, 0, sw, bar, 0xFF000000);
        HudDraw.rect(ctx, 0, sh - bar, sw, sh, 0xFF000000);
        HudDraw.rect(ctx, 0, bar, sw, bar + 1, HudDraw.argb(0.7f * in, th.accent()));
        HudDraw.rect(ctx, 0, sh - bar - 1, sw, sh - bar, HudDraw.argb(0.7f * in, th.accent()));
        // 컷인 띠: 왼쪽에서 확 들어와 버티다 오른쪽으로 빠짐 (기울어진 띠 두 겹)
        if (t >= 6 && t < 35) {
            float tin = Math.min(1, (t - 6) / 5f), tout = t > 30 ? (t - 30) / 5f : 0;
            float ease = 1 - (1 - tin) * (1 - tin) * (1 - tin);
            float slide = (1 - ease) * -sw * 1.2f + tout * tout * sw * 1.3f;
            float by = sh * 0.64f, bh = 46;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(sw / 2f + slide, by, 0);
            ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-7));
            float half = sw * 0.75f;
            HudDraw.rect(ctx, -half, -bh / 2 - 9, half, -bh / 2 - 4, HudDraw.argb(0.9f, th.accent()));
            ctx.fillGradient((int) -half, (int) (-bh / 2), (int) half, (int) (bh / 2), HudDraw.argb(0.94f, 0x0A0612), HudDraw.argb(0.94f, 0x1A1028));
            HudDraw.rect(ctx, -half, -bh / 2, half, -bh / 2 + 1, HudDraw.argb(1f, th.accent()));
            HudDraw.rect(ctx, -half, bh / 2 - 1, half, bh / 2, HudDraw.argb(1f, th.accent()));
            HudDraw.rect(ctx, -half, bh / 2 + 3, half, bh / 2 + 6, HudDraw.argb(0.6f, th.glow()));
            // 흐르는 빗금
            float flow = (t * 9) % 24;
            for (float x = -half + flow; x < half; x += 24)
                HudDraw.rect(ctx, x, -bh / 2 + 2, x + 6, bh / 2 - 2, HudDraw.argb(0.06f, th.accent()));
            // "ULTIMATE" + 궁극기 이름 + 시전자
            String key = CameraController.cinematicType();
            key = key.substring(0, key.length() - 4);
            Text ult = Text.literal("U L T I M A T E");
            ctx.getMatrices().push();
            ctx.getMatrices().translate(-sw * 0.36f, -bh / 2 + 6, 0);
            ctx.drawText(tr, ult, 0, 0, 0xFF000000 | th.accent(), false);
            ctx.getMatrices().pop();
            Text title = Text.translatable("abilitywar.ult." + key + ".title");
            Text sub = Text.translatable("abilitywar.ult." + key + ".subtitle");
            float ts = 2.4f + 0.6f * (1 - ease);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(-sw * 0.36f, -4, 0);
            ctx.getMatrices().scale(ts, ts, 1);
            ctx.drawText(tr, title, 1, 1, 0xFF000000 | th.glow(), false);
            ctx.drawText(tr, title, 0, 0, 0xFF000000 | th.title(), false);
            ctx.getMatrices().pop();
            int tw = (int) (tr.getWidth(title) * ts);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(-sw * 0.36f + tw + 12, 3, 0);
            ctx.getMatrices().scale(1.2f, 1.2f, 1);
            ctx.drawTextWithShadow(tr, sub, 0, 0, 0xFF000000 | th.sub());
            ctx.getMatrices().pop();
            Entity caster = CameraController.cinematicCaster();
            if (caster != null) {
                Text cn = caster.getDisplayName();
                ctx.drawTextWithShadow(tr, cn, (int) (sw * 0.36f - tr.getWidth(cn)), (int) (bh / 2 - 13), 0xFFFFFFFF);
            }
            ctx.getMatrices().pop();
            // 띠가 들어오는 순간 번쩍
            if (t < 9) HudDraw.rect(ctx, 0, 0, sw, sh, HudDraw.argb((1 - (t - 6) / 3f) * 0.25f, 0xFFFFFF));
        }
        // 마지막: 하얗게 번쩍 (진짜 궁극기로 넘어감)
        float flash = 0;
        if (t >= 35) flash = Math.min(1, (t - 35) / 3f);
        if (t >= 38) flash = Math.max(0, 1 - (t - 38) / 2f) * 0.9f + 0.1f;
        if (flash > 0) HudDraw.rect(ctx, 0, 0, sw, sh, HudDraw.argb(flash * 0.9f, th.flash()));
    }

    static void grandSeal(DrawContext ctx, TextRenderer tr, float x, float y, float tt, float out) {
        float st = tt - 4;
        if (st < 0) return;
        float k = Math.min(1, st / 4f);
        float scale = 1f + 1.4f * (float) Math.pow(1 - k, 3);
        float a = Math.min(1, st / 2f) * out;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-8));
        int s = 18;
        HudDraw.rect(ctx, -s - 2, -s - 2, s + 2, s + 2, HudDraw.argb(a, 0xFFD86A));
        HudDraw.rect(ctx, -s, -s, s, s, HudDraw.argb(a, 0xA8141A));
        HudDraw.rect(ctx, -s + 3, -s + 3, s - 3, -s + 4, HudDraw.argb(a * 0.6f, 0xFFD86A));
        HudDraw.rect(ctx, -s + 3, s - 4, s - 3, s - 3, HudDraw.argb(a * 0.6f, 0xFFD86A));
        ctx.getMatrices().scale(2.6f, 2.6f, 1);
        String ch = "大";
        ctx.drawText(tr, ch, -tr.getWidth(ch) / 2, -4, ((int) (a * 255) << 24) | 0xFFF0C0, false);
        ctx.getMatrices().pop();
        // 찍히는 순간 번쩍임
        if (st < 3) HudDraw.rect(ctx, x - 40, y - 40, x + 40, y + 40, HudDraw.argb((1 - st / 3f) * 0.35f * out, 0xFFF6DC));
    }

    /** 화면 전체에 색을 곱함. */
    static void multiply(DrawContext ctx, float w, float h, float r, float g, float b) {
        org.joml.Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_DST_COLOR, org.lwjgl.opengl.GL11.GL_ZERO);
        RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);
        net.minecraft.client.render.BufferBuilder bb = net.minecraft.client.render.Tessellator.getInstance().getBuffer();
        bb.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS, net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        bb.vertex(m, 0, h, 0).color(r, g, b, 1).next();
        bb.vertex(m, w, h, 0).color(r, g, b, 1).next();
        bb.vertex(m, w, 0, 0).color(r, g, b, 1).next();
        bb.vertex(m, 0, 0, 0).color(r, g, b, 1).next();
        net.minecraft.client.render.Tessellator.getInstance().draw();
        RenderSystem.defaultBlendFunc();
    }

    private CinematicOverlay() {
    }
}
