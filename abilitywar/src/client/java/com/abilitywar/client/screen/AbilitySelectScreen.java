package com.abilitywar.client.screen;

import com.abilitywar.AbilityWar;
import com.abilitywar.ability.Abilities;
import com.abilitywar.ability.Ability;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.fx.Tex;
import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.net.AWNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** 능력 선택 화면 (기본 키: B). */
public class AbilitySelectScreen extends Screen {
    private static final String[] SLOTS = {"skill1", "skill2", "skill3", "skill4", "ultimate", "passive"};
    private static final String[] KEYS = {"1", "2", "3", "4", "R", "T"};
    private final List<Ability> list = new ArrayList<>(Abilities.all());
    private int selected;
    /** 처음 접속: 직업을 고르기 전에는 닫을 수 없음. */
    private final boolean forced;
    private static boolean pendingForced;

    public AbilitySelectScreen() {
        this(false);
    }

    public AbilitySelectScreen(boolean forced) {
        super(Text.translatable(forced ? "abilitywar.screen.first_title" : "abilitywar.screen.title"));
        this.forced = forced;
        for (int i = 0; i < list.size(); i++) if (list.get(i).key.equals(ClientState.ability)) selected = i;
    }

    /** 서버가 요청: 접속 중 로딩 화면이 닫힌 뒤에 염. */
    public static void request(boolean forced) {
        if (forced) pendingForced = true;
        else net.minecraft.client.MinecraftClient.getInstance().setScreen(new AbilitySelectScreen(false));
    }

    public static void tickPending(net.minecraft.client.MinecraftClient mc) {
        if (!pendingForced || mc.player == null) return;
        if (!ClientState.ability.isEmpty()) {
            pendingForced = false;
            return;
        }
        if (mc.currentScreen == null) {
            pendingForced = false;
            mc.setScreen(new AbilitySelectScreen(true));
        }
    }

    @Override
    protected void init() {
        int bw = 120;
        if (forced) {
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("abilitywar.screen.first_choose"), b -> choose(list.get(selected).key))
                    .dimensions(this.width / 2 - bw / 2 - 20, this.height - 30, bw + 40, 20).build());
            return;
        }
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("abilitywar.screen.choose"), b -> choose(list.get(selected).key))
                .dimensions(this.width / 2 - bw - 4, this.height - 30, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("abilitywar.screen.clear"), b -> choose(""))
                .dimensions(this.width / 2 + 4, this.height - 30, bw, 20).build());
    }

    @Override
    public void tick() {
        // 명령어 등으로 직업이 정해지면 저절로 닫힘
        if (forced && !ClientState.ability.isEmpty()) this.client.setScreen(null);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !forced;
    }

    void choose(String key) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(key);
        ClientPlayNetworking.send(AWNet.C2S_SELECT, buf);
        this.client.setScreen(null);
    }

    /** 카드 크기: 직업이 많아도 한 줄에 들어가게. */
    int cardW() {
        int gap = 8;
        return Math.max(56, Math.min(110, (this.width - 20 + gap) / list.size() - gap));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cardW = cardW(), cardH = cardW * 72 / 110, gap = 8;
        int total = list.size() * (cardW + gap) - gap;
        int x0 = (this.width - total) / 2;
        for (int i = 0; i < list.size(); i++) {
            int x = x0 + i * (cardW + gap), y = 34;
            if (mx >= x && mx < x + cardW && my >= y && my < y + cardH) {
                selected = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        HudDraw.rect(ctx, 0, 0, this.width, this.height, 0x880A0414);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, forced ? 8 : 12, 0xE6CCFF);
        if (forced) ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("abilitywar.screen.first_hint"), this.width / 2, 20, 0x9A8DB0);
        int cardW = cardW(), cardH = cardW * 72 / 110, gap = 8;
        int total = list.size() * (cardW + gap) - gap;
        int x0 = (this.width - total) / 2;
        for (int i = 0; i < list.size(); i++) {
            Ability a = list.get(i);
            int x = x0 + i * (cardW + gap), y = 34;
            boolean sel = i == selected;
            boolean hover = mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH;
            ctx.drawTexture(AbilityWar.id("textures/gui/card_" + a.key + ".png"), x, y, cardW, cardH - 14, 0, 0, 256, 160, 256, 160);
            if (forced && i == selected) HudDraw.rect(ctx, x, y, x + cardW, y + cardH - 14, 0x22FFFFFF);
            HudDraw.rect(ctx, x, y + cardH - 14, x + cardW, y + cardH, 0xEE120A1E);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(a.translationKey()), x + cardW / 2, y + cardH - 11, 0xFFFFFF);
            int border = sel ? 0xFFB27BFF : hover ? 0xAA9B7BC0 : 0x663A2A50;
            HudDraw.rect(ctx, x - 1, y - 1, x + cardW + 1, y, border);
            HudDraw.rect(ctx, x - 1, y + cardH, x + cardW + 1, y + cardH + 1, border);
            HudDraw.rect(ctx, x - 1, y, x, y + cardH, border);
            HudDraw.rect(ctx, x + cardW, y, x + cardW + 1, y + cardH, border);
            if (a.key.equals(ClientState.ability)) {
                ctx.drawTextWithShadow(this.textRenderer, Text.translatable("abilitywar.screen.current").formatted(Formatting.GREEN), x + 3, y + 3, 0xFFFFFF);
            }
        }
        // 상세 설명
        Ability a = list.get(selected);
        int dx = Math.max(10, this.width / 2 - 170), dy = 34 + cardH + 12;
        int dw = Math.min(340, this.width - 20);
        HudDraw.rect(ctx, dx - 6, dy - 6, dx + dw + 6, this.height - 38, 0xCC0C0614);
        ctx.drawTextWithShadow(this.textRenderer, Text.translatable(a.translationKey()).formatted(Formatting.BOLD), dx, dy, 0xE6CCFF);
        ctx.drawTextWithShadow(this.textRenderer, Text.translatable(a.translationKey() + ".desc"), dx, dy + 12, 0xB8A8D0);
        int y = dy + 28;
        int maxY = this.height - 44;
        for (int i = 0; i < SLOTS.length && y < maxY; i++) {
            ctx.drawTexture(Tex.icon(a.key, SLOTS[i]), dx, y, 16, 16, 0, 0, 64, 64, 64, 64);
            Text nm = Text.literal("[" + KEYS[i] + "] ").formatted(Formatting.GRAY).append(Text.translatable("abilitywar.skill." + a.key + "." + SLOTS[i]).formatted(Formatting.WHITE));
            ctx.drawTextWithShadow(this.textRenderer, nm, dx + 20, y, 0xFFFFFF);
            List<net.minecraft.text.OrderedText> lines = this.textRenderer.wrapLines(Text.translatable("abilitywar.skill." + a.key + "." + SLOTS[i] + ".desc"), dw - 22);
            int ly = y + 10;
            for (var l : lines) {
                if (ly > maxY) break;
                ctx.drawTextWithShadow(this.textRenderer, l, dx + 20, ly, 0x9A8DB0);
                ly += 9;
            }
            y = ly + 3;
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
