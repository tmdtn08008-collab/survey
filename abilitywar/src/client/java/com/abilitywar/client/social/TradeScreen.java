package com.abilitywar.client.social;

import com.abilitywar.client.hud.GoldHud;
import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.hud.RpgHud;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.client.screen.RpgUi;
import com.abilitywar.social.TradeScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * 거래 창: 왼쪽 내 물건 9 칸 + 올린 G, 오른쪽 상대 물건 + G, 가운데 상태 글,
 * 그 아래 G 입력 · [올리기], [준비] [거래 확정] [취소], 맨 아래 내 인벤토리.
 */
public class TradeScreen extends HandledScreen<TradeScreenHandler> {
    ButtonWidget ready, confirm, cancel, setGold;
    TextFieldWidget gold;

    public TradeScreen(TradeScreenHandler h, PlayerInventory inv, Text title) {
        super(h, inv, title);
        backgroundWidth = 300;
        backgroundHeight = 238;
    }

    @Override
    protected void init() {
        super.init();
        gold = new TextFieldWidget(textRenderer, x + 14, y + 104, 60, 14, Text.empty());
        gold.setTextPredicate(s -> s.matches("\\d{0,9}"));
        gold.setMaxLength(9);
        gold.setPlaceholder(Text.literal("0"));
        addDrawableChild(gold);
        setGold = addDrawableChild(ButtonWidget.builder(Text.translatable("trade.abilitywar.set_gold"), b -> {
            long v = gold.getText().isEmpty() ? 0 : Long.parseLong(gold.getText());
            SocialClient.send(4, 0, v);
        }).dimensions(x + 78, y + 102, 50, 18).build());
        ready = addDrawableChild(ButtonWidget.builder(Text.translatable("trade.abilitywar.ready"), b -> click(0)).dimensions(x + 14, y + 124, 86, 20).build());
        confirm = addDrawableChild(ButtonWidget.builder(Text.translatable("trade.abilitywar.confirm"), b -> click(1)).dimensions(x + 107, y + 124, 86, 20).build());
        cancel = addDrawableChild(ButtonWidget.builder(Text.translatable("trade.abilitywar.cancel"), b -> click(2)).dimensions(x + 200, y + 124, 86, 20).build());
    }

    void click(int id) {
        if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id);
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        gold.tick();
        ready.setMessage(Text.translatable(handler.myReady() ? "trade.abilitywar.unready" : "trade.abilitywar.ready"));
        confirm.active = handler.myReady() && handler.theirReady() && !handler.myConfirm();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // 글을 쓰는 동안 E (인벤토리 키) 로 창이 닫히지 않게
        if (gold.isFocused() && key != 256) return gold.keyPressed(key, scan, mods) || true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        RpgUi.panel(ctx, x, y, x + backgroundWidth, y + backgroundHeight);
        for (Slot s : handler.slots) {
            int sx = x + s.x - 1, sy = y + s.y - 1;
            boolean theirs = s.inventory == handler.theirs;
            HudDraw.rect(ctx, sx, sy, sx + 18, sy + 18, theirs ? 0xFF1E1810 : 0xFF2C2216);
            RpgHud.frame(ctx, sx, sy, sx + 18, sy + 18, theirs ? 0x3A2C12 : 0x5A4418);
        }
        // 준비 (초록) · 확정 (밝은 초록) 테두리
        RpgHud.frame(ctx, x + 10, y + 26, x + 72, y + 98, state(handler.myReady(), handler.myConfirm()));
        RpgHud.frame(ctx, x + 228, y + 26, x + 290, y + 98, state(handler.theirReady(), handler.theirConfirm()));
    }

    static int state(boolean ready, boolean confirm) {
        return confirm ? 0x9CFFA8 : ready ? 0x4AB84A : 0x3A2C12;
    }

    Text mark(boolean ready, boolean confirm) {
        return Text.translatable(confirm ? "trade.abilitywar.mark.confirmed" : ready ? "trade.abilitywar.mark.ready" : "trade.abilitywar.mark.not_ready");
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mx, int my) {
        Text head = Text.translatable("trade.abilitywar.header");
        ctx.drawText(textRenderer, head, 150 - textRenderer.getWidth(head) / 2, 6, 0xFFE9A8, true);
        ctx.drawText(textRenderer, Text.translatable("trade.abilitywar.me"), 14, 17, 0xFFE9A8, true);
        Text p = Text.literal(handler.partner);
        ctx.drawText(textRenderer, p, 286 - textRenderer.getWidth(p), 17, 0x8FD3FF, true);
        // 가운데 상태
        String st = handler.myConfirm() && !handler.theirConfirm() ? "trade.abilitywar.state.wait_confirm"
                : handler.myReady() && handler.theirReady() ? "trade.abilitywar.state.both_ready"
                : handler.myReady() ? "trade.abilitywar.state.wait_ready" : "trade.abilitywar.state.place";
        int cy = 32;
        for (var line : textRenderer.wrapLines(Text.translatable(st), 136)) {
            ctx.drawText(textRenderer, line, 150 - textRenderer.getWidth(line) / 2, cy, 0xD8CCB0, true);
            cy += 10;
        }
        Text mm = mark(handler.myReady(), handler.myConfirm()), tm = mark(handler.theirReady(), handler.theirConfirm());
        ctx.drawText(textRenderer, mm, 82, 74, handler.myReady() ? 0x7CE87C : 0x9A8D70, true);
        ctx.drawText(textRenderer, tm, 218 - textRenderer.getWidth(tm), 74, handler.theirReady() ? 0x7CE87C : 0x9A8D70, true);
        // 올린 G
        String myG = GoldHud.fmt(handler.myGold()) + " G";
        GoldHud.coin(ctx, 14, 87, 9);
        ctx.drawText(textRenderer, myG, 25, 88, 0xFFE9A8, true);
        String th = GoldHud.fmt(handler.theirGold()) + " G";
        GoldHud.coin(ctx, 286 - textRenderer.getWidth(th) - 11, 87, 9);
        ctx.drawText(textRenderer, th, 286 - textRenderer.getWidth(th), 88, 0xFFE9A8, true);
        ctx.drawText(textRenderer, Text.translatable("trade.abilitywar.have", GoldHud.fmt(RpgClient.gold)), 134, 108, 0x9A8D70, false);
        ctx.drawText(textRenderer, Text.translatable("container.inventory"), 69, 144, 0x9A8D70, false);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        super.render(ctx, mx, my, delta);
        drawMouseoverTooltip(ctx, mx, my);
    }
}
