package com.abilitywar.client.hud;

import com.abilitywar.client.ClientState;
import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.input.InputHandler;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.rpg.JobQuests;
import com.abilitywar.rpg.RpgManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/** 왼쪽 위: 레벨 · 직업 · 경험치 막대 · 스탯 포인트, 그 아래 퀘스트 추적. 가운데: 레벨 업 알림. */
public final class RpgHud {
    public static final int GOLD = 0xF0C860;
    public static final int GOLD_DARK = 0x8A6A28;

    public static void render(DrawContext ctx, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || !RpgClient.synced || CameraController.isCinematic() || mc.options.debugEnabled) return;
        TextRenderer tr = mc.textRenderer;
        float time = mc.player.age + td;
        int x = 6, y = 6;
        // ---- 레벨 배지
        int bs = 24;
        HudDraw.rect(ctx, x, y, x + bs, y + bs, 0xE0140E08);
        if (RpgClient.grandActive()) {
            // 3차: 배지 둘레가 금빛으로 일렁임
            float gp = 0.5f + 0.5f * MathHelper.sin(time * 0.15f);
            frame(ctx, x - 2, y - 2, x + bs + 2, y + bs + 2, lerpColor(0x8A6A28, 0xFFE9A8, gp));
            HudDraw.rect(ctx, x - 1, y - 1, x + bs + 1, y + bs + 1, HudDraw.argb(0.12f + 0.12f * gp, 0xFFD86A));
        }
        frame(ctx, x, y, x + bs, y + bs, GOLD);
        HudDraw.rect(ctx, x + 2, y + 2, x + bs - 2, y + 3, HudDraw.argb(0.35f, GOLD));
        String lv = String.valueOf(RpgClient.level);
        float sc = lv.length() >= 3 ? 1.0f : 1.35f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + bs / 2f, y + bs / 2f + 1, 0);
        ctx.getMatrices().scale(sc, sc, 1);
        ctx.drawText(tr, lv, -tr.getWidth(lv) / 2, -4, 0xFFF6DC, true);
        ctx.getMatrices().pop();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 2, y + 1.5f, 0);
        ctx.getMatrices().scale(0.5f, 0.5f, 1);
        ctx.drawText(tr, "LV", 0, 0, GOLD, false);
        ctx.getMatrices().pop();
        // ---- 직업 이름
        int tx = x + bs + 5;
        boolean grand = RpgClient.grandActive();
        Text cls = ClientState.ability.isEmpty() ? Text.translatable("rpg.abilitywar.hud.no_class").formatted(Formatting.GRAY)
                : RpgClient.className(ClientState.ability, grand);
        ctx.drawTextWithShadow(tr, cls, tx, y + 1, grand ? lerpColor(0xFFD86A, 0xFFF6DC, 0.5f + 0.5f * MathHelper.sin(time * 0.12f)) : 0xFFFFFF);
        int tierN = com.abilitywar.rpg.ClassTree.tier(ClientState.ability, grand);
        if (tierN > 0) {
            Text tier = Text.translatable("rpg.abilitywar.tier." + tierN);
            ctx.drawTextWithShadow(tr, tier, tx + tr.getWidth(cls) + 4, y + 1, 0x9A8D70);
        }
        // ---- 경험치 막대
        int bw = 110, bh = 5, by = y + 13;
        HudDraw.rect(ctx, tx, by, tx + bw, by + bh, 0xC0100A04);
        float f = MathHelper.clamp(RpgClient.xpShown, 0, 1);
        int fill = (int) (bw * f);
        boolean max = RpgClient.level >= RpgManager.MAX_LEVEL;
        if (max) fill = bw;
        int c1 = 0xFFD07A, c2 = 0xC89030;
        for (int i = 0; i < fill; i++) {
            float k = (float) i / bw;
            HudDraw.rect(ctx, tx + i, by, tx + i + 1, by + bh, 0xFF000000 | lerpColor(c2, c1, k));
        }
        HudDraw.rect(ctx, tx, by, tx + fill, by + 1, 0x60FFFFFF);
        // 반짝임
        if (fill > 4) {
            float sx = (time * 2.2f) % (bw + 40) - 20;
            for (int i = -6; i <= 6; i++) {
                int px = (int) (tx + sx + i);
                if (px < tx || px >= tx + fill) continue;
                HudDraw.rect(ctx, px, by, px + 1, by + bh, HudDraw.argb(0.28f * (1 - Math.abs(i) / 7f), 0xFFFFFF));
            }
        }
        if (RpgClient.xpFlash > 0) HudDraw.rect(ctx, tx, by, tx + bw, by + bh, HudDraw.argb(RpgClient.xpFlash / 14f * 0.5f, 0xFFF6DC));
        frame(ctx, tx - 1, by - 1, tx + bw + 1, by + bh + 1, GOLD_DARK);
        String xs = max ? "MAX" : RpgClient.xp + " / " + RpgClient.need;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(tx + bw + 4, by - 0.5f, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1);
        ctx.drawText(tr, xs, 0, 0, 0xD8CCB0, true);
        ctx.getMatrices().pop();
        int ny = y + bs + 4;
        // ---- 스탯 포인트
        if (RpgClient.points > 0) {
            float pulse = 0.65f + 0.35f * MathHelper.sin(time * 0.15f);
            if (RpgClient.pointsFlash > 0) pulse = 1;
            Text pt = Text.translatable("rpg.abilitywar.hud.points", RpgClient.points, InputHandler.STATS.getBoundKeyLocalizedText());
            int pw = tr.getWidth(pt) + 8;
            HudDraw.rect(ctx, x, ny, x + pw, ny + 12, HudDraw.argb(0.55f, 0x2A1E08));
            HudDraw.rect(ctx, x, ny, x + 1, ny + 12, HudDraw.argb(pulse, GOLD));
            ctx.drawTextWithShadow(tr, pt, x + 5, ny + 2, lerpColor(0xC8A050, 0xFFF0B0, pulse));
            ny += 15;
        }
        // ---- 퀘스트 추적
        JobQuests.Quest q = RpgClient.active();
        if (q != null) {
            boolean ready = RpgClient.done(q);
            int qw = 170;
            int lines = q.objectives().size() + (ready ? 1 : 0);
            int qh = 14 + lines * 10 + 3;
            HudDraw.rect(ctx, x, ny, x + qw, ny + qh, 0x9A0C0806);
            HudDraw.rect(ctx, x, ny, x + 2, ny + qh, 0xFF000000 | (ready ? 0x7CE87C : GOLD));
            ctx.drawTextWithShadow(tr, Text.literal("◆ ").formatted(Formatting.GOLD).append(RpgClient.questName(q).formatted(Formatting.WHITE)), x + 6, ny + 3, 0xFFFFFF);
            int oy = ny + 15;
            for (int i = 0; i < q.objectives().size(); i++) {
                JobQuests.Objective o = q.objectives().get(i);
                int have = Math.min(o.count(), RpgClient.count(i));
                boolean ok = have >= o.count();
                Text line = Text.literal(ok ? "✔ " : "· ").formatted(ok ? Formatting.GREEN : Formatting.GRAY)
                        .append(RpgClient.objectiveText(o).formatted(ok ? Formatting.DARK_GRAY : Formatting.GRAY))
                        .append(Text.literal("  " + have + "/" + o.count()).formatted(ok ? Formatting.GREEN : Formatting.YELLOW));
                ctx.drawTextWithShadow(tr, line, x + 8, oy, 0xFFFFFF);
                oy += 10;
            }
            if (ready) {
                float p = 0.6f + 0.4f * MathHelper.sin(time * 0.2f);
                ctx.drawTextWithShadow(tr, Text.translatable("rpg.abilitywar.hud.return"), x + 8, oy, lerpColor(0x5AA85A, 0xB8FFB0, p));
            }
        }
        renderBanner(ctx, td, tr);
    }

    static void renderBanner(DrawContext ctx, float td, TextRenderer tr) {
        if (RpgClient.bannerTicks <= 0) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float t = 70 - RpgClient.bannerTicks + td;
        float in = MathHelper.clamp(t / 6f, 0, 1), out = MathHelper.clamp((RpgClient.bannerTicks - td) / 12f, 0, 1);
        float a = Math.min(in, out);
        float cy = sh * 0.27f;
        // 가로 빛줄
        float lw = 150 * (1 - (float) Math.pow(1 - in, 3));
        for (int i = 0; i < 12; i++) {
            float k = 1 - i / 12f;
            HudDraw.rect(ctx, sw / 2f - lw * k, cy - 1 + i * 0.0f, sw / 2f + lw * k, cy, HudDraw.argb(a * 0.08f, GOLD));
        }
        HudDraw.rect(ctx, sw / 2f - lw, cy + 13, sw / 2f + lw, cy + 14, HudDraw.argb(a * 0.7f, GOLD));
        Text title = Text.translatable("rpg.abilitywar.hud.level_up");
        float scale = 2.2f + 0.8f * (float) Math.pow(1 - in, 2);
        int alpha = (int) (a * 255) << 24;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sw / 2f, cy - 8, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        int w = tr.getWidth(title);
        ctx.drawText(tr, title, -w / 2 + 1, -4 + 1, alpha | 0x3A2808, false);
        ctx.drawText(tr, title, -w / 2, -4, alpha | 0xFFE9A8, false);
        ctx.getMatrices().pop();
        Text sub = Text.translatable("rpg.abilitywar.hud.level_now", RpgClient.bannerLevel);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sw / 2f, cy + 18, 0);
        ctx.getMatrices().scale(1.2f, 1.2f, 1);
        ctx.drawTextWithShadow(tr, sub, -tr.getWidth(sub) / 2, 0, alpha | 0xFFFFFF);
        ctx.getMatrices().pop();
    }

    public static void frame(DrawContext ctx, float x0, float y0, float x1, float y1, int rgb) {
        int c = 0xFF000000 | rgb;
        HudDraw.rect(ctx, x0, y0, x1, y0 + 1, c);
        HudDraw.rect(ctx, x0, y1 - 1, x1, y1, c);
        HudDraw.rect(ctx, x0, y0 + 1, x0 + 1, y1 - 1, c);
        HudDraw.rect(ctx, x1 - 1, y0 + 1, x1, y1 - 1, c);
    }

    public static int lerpColor(int a, int b, float k) {
        k = MathHelper.clamp(k, 0, 1);
        int r = (int) MathHelper.lerp(k, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) MathHelper.lerp(k, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) MathHelper.lerp(k, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private RpgHud() {
    }
}
