package com.abilitywar.client.screen;

import com.abilitywar.client.hud.GoldHud;
import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.rpg.InstanceDungeon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 던전 안내인: 던전 7 (테마, 레벨 순) × 난이도 3 고르기. 줄마다 이름 · 보스 · 필요 레벨, 단추마다 입장료.
 * 야생 · 지역의 던전 입구 (gate != null) 에서는 그 던전 한 줄만.
 */
public class DungeonScreen extends Screen {
    final int npc;
    @org.jetbrains.annotations.Nullable
    final net.minecraft.util.math.BlockPos gate;
    final java.util.List<InstanceDungeon.Theme> rows;
    int px, py, pw = 400, ph = 252;
    static final int TOP = 62;
    int row = 28;

    public DungeonScreen(int npc) {
        super(GuildScreen.title(npc));
        this.npc = npc;
        this.gate = null;
        this.rows = InstanceDungeon.Theme.byLevel();
    }

    DungeonScreen(net.minecraft.util.math.BlockPos gate, InstanceDungeon.Theme theme) {
        super(Text.translatable("gate.abilitywar.title"));
        this.npc = -1;
        this.gate = gate;
        this.rows = java.util.List.of(theme);
    }

    /** 던전 입구 블록에서 연 창. */
    public static void openGate(net.minecraft.util.math.BlockPos pos, int theme) {
        MinecraftClient mc = MinecraftClient.getInstance();
        InstanceDungeon.Theme[] all = InstanceDungeon.Theme.values();
        if (theme < 0 || theme >= all.length) return;
        if (mc.currentScreen instanceof DungeonScreen s && pos.equals(s.gate)) return;
        if (mc.currentScreen != null) return;
        mc.setScreen(new DungeonScreen(pos, all[theme]));
    }

    public static void open(int npc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof DungeonScreen s && s.npc == npc) return;
        mc.setScreen(new DungeonScreen(npc));
    }

    static boolean creative() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.player.isCreative();
    }

    @Override
    protected void init() {
        pw = Math.min(400, width - 12);
        // 화면이 낮으면 (큰 GUI 배율) 줄 높이를 줄임
        row = Math.max(22, Math.min(28, (height - 8 - TOP - 34) / rows.size()));
        ph = TOP + rows.size() * row + 34;
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        int y = py + TOP;
        for (InstanceDungeon.Theme t : rows) {
            int bx = px + pw - 10 - 3 * 58 - 2 * 3;
            for (InstanceDungeon.Diff d : InstanceDungeon.Diff.values()) {
                Text label = Text.translatable("dungeon.abilitywar.diff." + d.key).formatted(d.color);
                ButtonWidget b = addDrawableChild(ButtonWidget.builder(label, btn -> {
                    if (gate != null) RpgClient.send(16, t.key + ":" + d.key + ":" + gate.getX() + ":" + gate.getY() + ":" + gate.getZ(), -1);
                    else RpgClient.send(15, t.key + ":" + d.key, npc);
                    close();
                }).dimensions(bx, y + 3, 58, 18).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                        Text.translatable("dungeon.abilitywar.btn_tip", InstanceDungeon.minLevel(t, d), GoldHud.fmt(InstanceDungeon.fee(t, d)), d.reward))).build());
                b.active = creative() || (RpgClient.level >= InstanceDungeon.minLevel(t, d) && RpgClient.gold >= InstanceDungeon.fee(t, d));
                bx += 61;
            }
            y += row;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.close"), b -> close()).dimensions(px + pw - 100, py + ph - 24, 90, 18).build());
    }

    @Override
    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (gate != null) {
            if (mc.player == null || mc.player.getBlockPos().getSquaredDistance(gate) > 10 * 10) close();
            return;
        }
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        if (e == null || mc.player == null || e.squaredDistanceTo(mc.player) > 12 * 12) close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        RpgUi.panel(ctx, px, py, px + pw, py + ph);
        RpgUi.header(ctx, textRenderer, Text.translatable(gate != null ? "gate.abilitywar.title" : "dungeon.abilitywar.title").formatted(Formatting.GOLD), px + pw / 2, py + 8);
        RpgUi.wrapped(ctx, textRenderer, Text.translatable(gate != null ? "gate.abilitywar.say" : "dungeon.abilitywar.say"), px + 12, py + 30, pw - 24, 0xB8AC90, py + TOP - 4);
        String money = GoldHud.fmt(RpgClient.gold) + " G";
        int y = py + TOP;
        for (InstanceDungeon.Theme t : rows) {
            boolean ok = creative() || RpgClient.level >= t.level;
            HudDraw.rect(ctx, px + 10, y, px + pw - 10, y + row - 3, ok ? 0x40201810 : 0x40100808);
            HudDraw.rect(ctx, px + 10, y, px + 13, y + row - 3, 0xFF000000 | t.color);
            ctx.drawTextWithShadow(textRenderer, Text.translatable("dungeon.abilitywar.name." + t.key).styled(s -> s.withColor(t.color).withBold(true)), px + 18, y + 4, 0xFFFFFF);
            Text sub = Text.translatable("dungeon.abilitywar.row_sub", t.level, Text.translatable("dungeon.abilitywar.boss." + t.key + ".boss"));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(px + 18, y + 15, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            ctx.drawTextWithShadow(textRenderer, sub, 0, 0, ok ? 0xC8BCA0 : 0x8A6A6A);
            ctx.getMatrices().pop();
            y += row;
        }
        int party = com.abilitywar.client.social.SocialClient.party.size();
        ctx.drawTextWithShadow(textRenderer, Text.translatable(party > 1 ? "dungeon.abilitywar.party" : "dungeon.abilitywar.solo", party), px + 12, py + ph - 20, 0x8FD3FF);
        GoldHud.coin(ctx, px + 12 + textRenderer.getWidth(Text.translatable(party > 1 ? "dungeon.abilitywar.party" : "dungeon.abilitywar.solo", party)) + 10, py + ph - 22, 11);
        ctx.drawTextWithShadow(textRenderer, money, px + 12 + textRenderer.getWidth(Text.translatable(party > 1 ? "dungeon.abilitywar.party" : "dungeon.abilitywar.solo", party)) + 24, py + ph - 20, 0xFFE9A8);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
