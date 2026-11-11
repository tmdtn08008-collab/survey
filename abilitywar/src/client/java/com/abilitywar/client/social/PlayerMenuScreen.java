package com.abilitywar.client.social;

import com.abilitywar.client.screen.RpgUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.text.Text;

/** 다른 플레이어를 Shift + 우클릭: 거래 요청 · 파티 초대 (같은 파티면 파티 나가기). */
public class PlayerMenuScreen extends Screen {
    final int target;
    int px, py, pw = 200, ph = 132;

    public PlayerMenuScreen(int target) {
        super(Text.translatable("social.abilitywar.menu"));
        this.target = target;
    }

    PlayerEntity player() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(target);
        return e instanceof PlayerEntity p ? p : null;
    }

    boolean sameParty() {
        PlayerEntity t = player(), me = MinecraftClient.getInstance().player;
        if (t == null || me == null) return false;
        AbstractTeam a = me.getScoreboardTeam();
        return a != null && a.getName().startsWith(com.abilitywar.social.Party.PREFIX) && t.isTeammate(me);
    }

    @Override
    protected void init() {
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int bx = px + 74, bw = pw - 84;
        addDrawableChild(ButtonWidget.builder(Text.translatable("social.abilitywar.btn.trade"), b -> {
            SocialClient.send(0, target, 0);
            close();
        }).dimensions(bx, py + 30, bw, 20).build());
        if (sameParty()) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("social.abilitywar.btn.leave"), b -> {
                SocialClient.send(3, 0, 0);
                close();
            }).dimensions(bx, py + 56, bw, 20).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.translatable("social.abilitywar.btn.party"), b -> {
                SocialClient.send(1, target, 0);
                close();
            }).dimensions(bx, py + 56, bw, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.close"), b -> close()).dimensions(bx, py + 98, bw, 20).build());
    }

    @Override
    public void tick() {
        PlayerEntity t = player();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (t == null || mc.player == null || t.squaredDistanceTo(mc.player) > 12 * 12) close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        RpgUi.panel(ctx, px, py, px + pw, py + ph);
        PlayerEntity t = player();
        RpgUi.inset(ctx, px + 10, py + 10, px + 64, py + ph - 10);
        if (t != null) {
            ctx.enableScissor(px + 11, py + 11, px + 63, py + ph - 11);
            InventoryScreen.drawEntity(ctx, px + 37, py + ph - 22, 40, -20, -5, t);
            ctx.disableScissor();
            ctx.drawTextWithShadow(textRenderer, t.getDisplayName(), px + 74, py + 12, 0xFFE9A8);
            String hp = "❤ " + com.abilitywar.client.hud.HealthBars.fmt(t.getHealth()) + " / " + com.abilitywar.client.hud.HealthBars.fmt(t.getMaxHealth());
            ctx.drawTextWithShadow(textRenderer, hp, px + 74, py + 82, 0xFF8A8A);
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
