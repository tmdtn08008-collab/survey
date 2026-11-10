package com.abilitywar.client.screen;

import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.hud.RpgHud;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** RPG 화면 공통 그리기: 금테 패널, 머리글, 진행 막대, 줄바꿈 글. */
public final class RpgUi {
    public static final int GOLD = RpgHud.GOLD;
    public static final int TEXT = 0xE8DCC0;
    public static final int DIM = 0x9A8D70;

    public static void panel(DrawContext ctx, int x0, int y0, int x1, int y1) {
        ctx.fillGradient(x0, y0, x1, y1, 0xF0191208, 0xF00C0904);
        RpgHud.frame(ctx, x0, y0, x1, y1, 0x8A6A28);
        RpgHud.frame(ctx, x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0x3A2C12);
        // 모서리 장식
        int c = 0xFF000000 | GOLD;
        for (int[] k : new int[][]{{x0, y0, 1, 1}, {x1, y0, -1, 1}, {x0, y1, 1, -1}, {x1, y1, -1, -1}}) {
            int x = k[0], y = k[1], dx = k[2], dy = k[3];
            HudDraw.rect(ctx, Math.min(x, x + dx * 7), Math.min(y, y + dy), Math.max(x, x + dx * 7), Math.max(y, y + dy), c);
            HudDraw.rect(ctx, Math.min(x, x + dx), Math.min(y, y + dy * 7), Math.max(x, x + dx), Math.max(y, y + dy * 7), c);
        }
    }

    public static void inset(DrawContext ctx, int x0, int y0, int x1, int y1) {
        HudDraw.rect(ctx, x0, y0, x1, y1, 0x80000000);
        RpgHud.frame(ctx, x0, y0, x1, y1, 0x3A2C12);
    }

    public static void header(DrawContext ctx, TextRenderer tr, Text title, int cx, int y) {
        int w = tr.getWidth(title);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y, 0);
        ctx.getMatrices().scale(1.3f, 1.3f, 1);
        ctx.drawTextWithShadow(tr, title, -w / 2, 0, 0xFFE9A8);
        ctx.getMatrices().pop();
        int lw = (int) (w * 0.65f) + 24;
        HudDraw.rect(ctx, cx - lw, y + 14, cx + lw, y + 15, HudDraw.argb(0.6f, GOLD));
    }

    public static void bar(DrawContext ctx, int x, int y, int w, int h, float f, int color) {
        HudDraw.rect(ctx, x, y, x + w, y + h, 0xC0100A04);
        HudDraw.rect(ctx, x, y, x + (int) (w * Math.max(0, Math.min(1, f))), y + h, 0xFF000000 | color);
        HudDraw.rect(ctx, x, y, x + (int) (w * Math.max(0, Math.min(1, f))), y + 1, 0x50FFFFFF);
        RpgHud.frame(ctx, x - 1, y - 1, x + w + 1, y + h + 1, 0x5A4418);
    }

    /** 줄바꿈해서 그리고 다음 y 를 돌려줌. */
    public static int wrapped(DrawContext ctx, TextRenderer tr, Text t, int x, int y, int w, int color, int maxY) {
        List<OrderedText> lines = tr.wrapLines(t, w);
        for (OrderedText l : lines) {
            if (y > maxY) break;
            ctx.drawTextWithShadow(tr, l, x, y, color);
            y += 10;
        }
        return y;
    }

    public static boolean in(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    private RpgUi() {
    }
}
