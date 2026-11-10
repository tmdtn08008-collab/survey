package com.abilitywar.client.screen;

import com.abilitywar.client.ClientState;
import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.input.InputHandler;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.rpg.ClassTree;
import com.abilitywar.rpg.RpgManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** 능력치 창 (기본 키: K): 레벨 업으로 받은 포인트를 힘·민첩·지능·체력·지혜에 찍음. */
public class StatScreen extends Screen {
    static final int[] COLORS = {0xFF8A5A, 0x7CE8A0, 0x8AB4FF, 0xFF6A7A, 0xD8A8FF};
    static final String[] ABBR = {"STR", "DEX", "INT", "VIT", "WIS"};
    final List<ButtonWidget> plus = new ArrayList<>();
    int px, py, pw = 330, ph = 214;

    public StatScreen() {
        super(Text.translatable("rpg.abilitywar.screen.stats"));
    }

    @Override
    protected void init() {
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        plus.clear();
        for (int i = 0; i < 5; i++) {
            final int st = i;
            int ry = rowY(i);
            ButtonWidget b1 = ButtonWidget.builder(Text.literal("+1"), b -> RpgClient.send(0, String.valueOf(st), -1))
                    .dimensions(px + pw - 58, ry + 3, 24, 16).build();
            ButtonWidget b5 = ButtonWidget.builder(Text.literal("+5"), b -> RpgClient.send(4, String.valueOf(st), -1))
                    .dimensions(px + pw - 32, ry + 3, 24, 16).build();
            plus.add(addDrawableChild(b1));
            plus.add(addDrawableChild(b5));
        }
        updateButtons();
    }

    int rowY(int i) {
        return py + 44 + i * 30;
    }

    void updateButtons() {
        for (ButtonWidget b : plus) b.active = RpgClient.points > 0;
    }

    @Override
    public void tick() {
        updateButtons();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (InputHandler.STATS.matchesKey(key, scan)) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    static Text effect(int stat, int n) {
        return switch (stat) {
            case 0 -> Text.translatable("rpg.abilitywar.stat.str.eff", fmt(1.5 * n), fmt(0.1 * n));
            case 1 -> Text.translatable("rpg.abilitywar.stat.dex.eff", fmt(0.4 * n), fmt(0.2 * n));
            case 2 -> Text.translatable("rpg.abilitywar.stat.int.eff", fmt(1.5 * n));
            case 3 -> Text.translatable("rpg.abilitywar.stat.vit.eff", fmt(1.0 * n));
            default -> Text.translatable("rpg.abilitywar.stat.wis.eff", fmt(0.3 * n), fmt(1.5 * n));
        };
    }

    static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    /** 이 직업이 주로 쓰는 스탯 (추천 표시). */
    static boolean recommended(int stat) {
        String base = ClassTree.baseOf(ClientState.ability);
        return switch (base) {
            case "warrior" -> stat == 0 || stat == 1;
            case "mage" -> stat == 2 || stat == 4;
            case "tank" -> stat == 3 || stat == 0;
            case "healer" -> stat == 4 || stat == 3;
            default -> false;
        };
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        RpgUi.panel(ctx, px, py, px + pw, py + ph);
        RpgUi.header(ctx, textRenderer, title, px + pw / 2, py + 8);
        // 왼쪽: 캐릭터
        int lx = px + 10, lw = 96;
        RpgUi.inset(ctx, lx, py + 32, lx + lw, py + ph - 10);
        if (client.player != null) InventoryScreen.drawEntity(ctx, lx + lw / 2, py + 132, 38, lx + lw / 2f - mx, py + 80 - my, client.player);
        Text cls = ClientState.ability.isEmpty() ? Text.translatable("rpg.abilitywar.hud.no_class") : Text.translatable("ability.abilitywar." + ClientState.ability);
        ctx.drawCenteredTextWithShadow(textRenderer, cls, lx + lw / 2, py + 140, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("rpg.abilitywar.hud.level_now", RpgClient.level), lx + lw / 2, py + 152, RpgUi.GOLD);
        RpgUi.bar(ctx, lx + 8, py + 166, lw - 16, 4, (float) RpgClient.xp / RpgClient.need, 0xE0B050);
        String xs = RpgClient.level >= RpgManager.MAX_LEVEL ? "MAX" : RpgClient.xp + " / " + RpgClient.need;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(lx + lw / 2f, py + 174, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1);
        ctx.drawCenteredTextWithShadow(textRenderer, xs, 0, 0, RpgUi.DIM);
        ctx.getMatrices().pop();
        int spent = 0;
        for (int v : RpgClient.stats) spent += v;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(lx + lw / 2f, py + 186, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("rpg.abilitywar.screen.spent", spent), 0, 0, RpgUi.DIM);
        ctx.getMatrices().pop();
        // 오른쪽 위: 남은 포인트
        int rx = lx + lw + 10, rw = px + pw - 10 - rx;
        Text pts = Text.translatable("rpg.abilitywar.screen.points", RpgClient.points);
        ctx.drawTextWithShadow(textRenderer, pts, rx, py + 32, RpgClient.points > 0 ? 0xFFE9A8 : RpgUi.DIM);
        // 스탯 줄
        for (int i = 0; i < 5; i++) {
            int ry = rowY(i);
            boolean hover = RpgUi.in(mx, my, rx, ry, rx + rw, ry + 26);
            HudDraw.rect(ctx, rx, ry, rx + rw, ry + 26, hover ? 0x30FFE0A0 : 0x40000000);
            HudDraw.rect(ctx, rx, ry, rx + 2, ry + 26, 0xFF000000 | COLORS[i]);
            // 약자 배지
            HudDraw.rect(ctx, rx + 5, ry + 4, rx + 29, ry + 22, 0xA0000000);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(rx + 17, ry + 9, 0);
            ctx.getMatrices().scale(0.8f, 0.8f, 1);
            ctx.drawCenteredTextWithShadow(textRenderer, ABBR[i], 0, 0, COLORS[i]);
            ctx.getMatrices().pop();
            Text nm = Text.translatable("rpg.abilitywar.stat." + RpgClient.STATS[i]);
            if (recommended(i)) nm = nm.copy().append(Text.literal(" ★").formatted(Formatting.GOLD));
            ctx.drawTextWithShadow(textRenderer, nm, rx + 34, ry + 3, 0xFFFFFF);
            String val = String.valueOf(RpgClient.stats[i]);
            ctx.drawTextWithShadow(textRenderer, val, px + pw - 64 - textRenderer.getWidth(val), ry + 3, COLORS[i]);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(rx + 34, ry + 15, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            ctx.drawTextWithShadow(textRenderer, effect(i, RpgClient.stats[i]), 0, 0, RpgUi.DIM);
            ctx.getMatrices().pop();
        }
        super.render(ctx, mx, my, delta);
        // 툴팁: 포인트 하나당 효과
        for (int i = 0; i < 5; i++) {
            int ry = rowY(i);
            if (RpgUi.in(mx, my, rx, ry, px + pw - 62, ry + 26)) {
                List<Text> tip = new ArrayList<>();
                tip.add(Text.translatable("rpg.abilitywar.stat." + RpgClient.STATS[i]).formatted(Formatting.GOLD));
                tip.add(Text.translatable("rpg.abilitywar.stat." + RpgClient.STATS[i] + ".desc").formatted(Formatting.GRAY));
                tip.add(Text.translatable("rpg.abilitywar.screen.per_point").append(effect(i, 1)).formatted(Formatting.DARK_AQUA));
                if (recommended(i)) tip.add(Text.translatable("rpg.abilitywar.screen.recommended").formatted(Formatting.YELLOW));
                ctx.drawTooltip(textRenderer, tip, mx, my);
            }
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("rpg.abilitywar.screen.stats_hint", InputHandler.STATS.getBoundKeyLocalizedText()), px + pw / 2, py + ph + 4, 0x807860);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
