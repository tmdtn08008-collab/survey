package com.abilitywar.client.screen;

import com.abilitywar.client.hud.GoldHud;
import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.rpg.ShopNpcEntity;
import com.abilitywar.rpg.Shops;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 상점 화면 (물약 상인 · 대장장이 재료): 칸을 누르면 1 개, Shift + 누르면 5 개 삼. 값은 에메랄드.
 */
public class ShopScreen extends Screen {
    final int npc;
    final String kind;
    int px, py, pw = 420, ph = 264;
    float typed;
    int flash = -1;
    float flashT;
    ButtonWidget close, toSmith;

    public ShopScreen(int npc, String kind) {
        super(GuildScreen.title(npc));
        this.npc = npc;
        this.kind = kind;
    }

    public static void open(int npc, String kind) {
        MinecraftClient.getInstance().setScreen(new ShopScreen(npc, kind));
    }

    List<Shops.Offer> offers() {
        return Shops.offers(kind);
    }

    boolean smith() {
        return ShopNpcEntity.BLACKSMITH.equals(kind);
    }

    @Override
    protected void init() {
        pw = Math.min(420, width - 12);
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        int by = py + ph - 24;
        close = addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.close"), b -> close()).dimensions(px + pw - 100, by, 90, 18).build());
        toSmith = addDrawableChild(ButtonWidget.builder(Text.translatable("smith.abilitywar.btn.enhance_tab"), b -> EnhanceScreen.open(npc))
                .dimensions(px + pw - 220, by, 112, 18).build());
        toSmith.visible = smith();
    }

    @Override
    public void tick() {
        typed += 1.6f;
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        if (e == null || mc.player == null || e.squaredDistanceTo(mc.player) > 12 * 12) close();
    }

    int cellW() {
        return (pw - 26) / 2;
    }

    int[] cell(int i) {
        int x0 = px + 10 + (i % 2) * (cellW() + 6), y0 = py + 80 + (i / 2) * 22;
        return new int[]{x0, y0, x0 + cellW(), y0 + 20};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        List<Shops.Offer> l = offers();
        for (int i = 0; i < l.size(); i++) {
            int[] c = cell(i);
            if (RpgUi.in(mx, my, c[0], c[1], c[2], c[3])) {
                int n = hasShiftDown() ? 5 : 1;
                RpgClient.send(10, l.get(i).id() + ":" + n, npc);
                flash = i;
                flashT = 0;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        RpgUi.panel(ctx, px, py, px + pw, py + ph);
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        RpgUi.inset(ctx, px + 10, py + 10, px + 56, py + 70);
        if (e instanceof LivingEntity le) {
            ctx.enableScissor(px + 11, py + 11, px + 55, py + 69);
            InventoryScreen.drawEntity(ctx, px + 33, py + 110, 48, -14, 4, le);
            ctx.disableScissor();
        }
        ctx.drawTextWithShadow(textRenderer, title, px + 64, py + 12, 0xFFE9A8);
        String full = Text.translatable("shop.abilitywar.say." + kind).getString();
        int n = Math.min(full.length(), (int) (typed + delta * 1.6f));
        RpgUi.wrapped(ctx, textRenderer, Text.literal(full.substring(0, n)), px + 64, py + 26, pw - 74, 0xE8DCC0, py + 68);

        long gold = RpgClient.gold;
        List<Shops.Offer> l = offers();
        ItemStack hover = null;
        for (int i = 0; i < l.size(); i++) {
            Shops.Offer o = l.get(i);
            int[] c = cell(i);
            boolean h = RpgUi.in(mx, my, c[0], c[1], c[2], c[3]);
            boolean afford = o.sell() || gold >= o.price();
            float f = i == flash ? Math.max(0, 1 - flashT / 8f) : 0;
            HudDraw.rect(ctx, c[0], c[1], c[2], c[3], h ? 0x50584018 : 0x40000000);
            if (f > 0) HudDraw.rect(ctx, c[0], c[1], c[2], c[3], HudDraw.argb(0.45f * f, 0x7CFF8A));
            com.abilitywar.client.hud.RpgHud.frame(ctx, c[0], c[1], c[2], c[3], h ? 0x8A6A28 : 0x3A2C12);
            ItemStack st = o.stack().get();
            ctx.drawItem(st, c[0] + 2, c[1] + 2);
            Text name = o.sell() ? Text.translatable("shop.abilitywar.sell", st.getName()) : st.getName();
            String nm = textRenderer.trimToWidth(name.getString(), cellW() - 80);
            ctx.drawTextWithShadow(textRenderer, Text.literal(nm).setStyle(name.getStyle()), c[0] + 21, c[1] + 6, o.sell() ? 0xB8F0B8 : 0xFFFFFF);
            String price = (o.sell() ? "+" : "") + GoldHud.fmt(o.price()) + " G";
            int pxr = c[2] - 6 - textRenderer.getWidth(price);
            GoldHud.coin(ctx, pxr - 12, c[1] + 5, 10);
            ctx.drawTextWithShadow(textRenderer, price, pxr, c[1] + 6, o.sell() ? 0x7CFF8A : afford ? 0xFFE9A8 : 0xFF6A6A);
            if (h) hover = st;
        }
        if (flash >= 0) flashT += delta;
        int fy = py + ph - 22;
        GoldHud.coin(ctx, px + 12, fy + 1, 13);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("shop.abilitywar.have", GoldHud.fmt(gold)), px + 30, fy + 3, 0xFFE9A8);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("shop.abilitywar.hint").formatted(Formatting.GRAY), px + 30, fy + 13, 0x9A8D70);
        super.render(ctx, mx, my, delta);
        if (hover != null) ctx.drawItemTooltip(textRenderer, hover, mx, my);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
