package com.abilitywar.client.screen;

import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.item.RpgItems;
import com.abilitywar.rpg.AbyssTower;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** 탑지기: 심연의 탑 설명 · 최고 기록 · 시작 층 고르기 (1층 또는 깬 10층 단위 다음 층). */
public class TowerScreen extends Screen {
    final int npc;
    int px, py, pw = 320, ph = 224;

    public TowerScreen(int npc) {
        super(GuildScreen.title(npc));
        this.npc = npc;
    }

    public static void open(int npc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof TowerScreen s && s.npc == npc) return;
        mc.setScreen(new TowerScreen(npc));
    }

    boolean levelOk() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return RpgClient.level >= AbyssTower.MIN_LEVEL || (mc.player != null && mc.player.isCreative());
    }

    @Override
    protected void init() {
        pw = Math.min(320, width - 12);
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        List<Integer> cps = AbyssTower.checkpoints(RpgClient.towerBest);
        // 가장 높은 체크포인트 4개만 (1층은 항상)
        int shown = Math.min(4, cps.size());
        int bw = (pw - 20 - (shown - 1) * 4) / shown;
        int bx = px + 10;
        for (int i = 0; i < shown; i++) {
            int f = i == 0 ? 1 : cps.get(cps.size() - shown + i);
            ButtonWidget b = addDrawableChild(ButtonWidget.builder(Text.translatable("tower.abilitywar.btn.start", f), btn -> {
                RpgClient.send(14, String.valueOf(f), npc);
                close();
            }).dimensions(bx, py + ph - 48, bw, 20).build());
            b.active = levelOk();
            bx += bw + 4;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.close"), b -> close()).dimensions(px + pw - 100, py + ph - 24, 90, 18).build());
    }

    @Override
    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        if (e == null || mc.player == null || e.squaredDistanceTo(mc.player) > 12 * 12) close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        RpgUi.panel(ctx, px, py, px + pw, py + ph);
        RpgUi.header(ctx, textRenderer, Text.translatable("tower.abilitywar.title").formatted(Formatting.LIGHT_PURPLE), px + pw / 2, py + 8);
        int y = RpgUi.wrapped(ctx, textRenderer, Text.translatable("tower.abilitywar.say"), px + 12, py + 32, pw - 24, 0xB8AC90, py + 80);
        y += 4;
        int x0 = px + 10, x1 = px + pw - 10;
        RpgUi.inset(ctx, x0, y, x1, py + ph - 54);
        int ty = y + 6;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("tower.abilitywar.best", RpgClient.towerBest), x0 + 6, ty, 0xD8A8FF);
        boolean ok = levelOk();
        Text lv = Text.literal(ok ? "✔ " : "✖ ").formatted(ok ? Formatting.GREEN : Formatting.RED)
                .append(Text.translatable("tower.abilitywar.req", AbyssTower.MIN_LEVEL, RpgClient.level).formatted(Formatting.GRAY));
        ctx.drawTextWithShadow(textRenderer, lv, x1 - 6 - textRenderer.getWidth(lv), ty, 0xFFFFFF);
        ty += 13;
        int party = com.abilitywar.client.social.SocialClient.party.size();
        ctx.drawTextWithShadow(textRenderer, Text.translatable(party > 1 ? "tower.abilitywar.party" : "tower.abilitywar.solo", party), x0 + 6, ty, 0x8FD3FF);
        ty += 13;
        for (String k : new String[]{"tower.abilitywar.rule1", "tower.abilitywar.rule2", "tower.abilitywar.rule3"}) {
            ty = RpgUi.wrapped(ctx, textRenderer, Text.literal("• ").append(Text.translatable(k)), x0 + 6, ty, x1 - x0 - 30, 0xE8DCC0, py + ph - 60) + 1;
        }
        // 보상 아이콘
        ctx.drawItem(new ItemStack(RpgItems.ABYSS_CRYSTAL), x1 - 22, y + 22);
        ctx.drawItem(new ItemStack(RpgItems.ENHANCE_STONE), x1 - 22, y + 40);
        super.render(ctx, mx, my, delta);
        if (RpgUi.in(mx, my, x1 - 22, y + 22, x1 - 6, y + 38)) ctx.drawItemTooltip(textRenderer, new ItemStack(RpgItems.ABYSS_CRYSTAL), mx, my);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
