package com.abilitywar.client.screen;

import com.abilitywar.client.hud.GoldHud;
import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.hud.RpgHud;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.gear.GearArmorItem;
import com.abilitywar.gear.GearEnhance;
import com.abilitywar.gear.GearGrade;
import com.abilitywar.gear.GearItem;
import com.abilitywar.gear.GearStat;
import com.abilitywar.gear.GearWeaponItem;
import com.abilitywar.item.RpgItems;
import com.abilitywar.rpg.ShopNpcEntity;
import com.abilitywar.rpg.Shops;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 대장장이: 장비 강화 (+15 까지) · 승급 (다음 등급).
 * 왼쪽: 내 장비 (인벤토리 · 입은 갑옷), 오른쪽: 고른 장비의 다음 강화 능력치 · 성공 확률 · 재료 / 승급 조건.
 */
public class EnhanceScreen extends Screen {
    final int npc;
    int px, py, pw = 440, ph = 268;
    int selected = -1, scroll;
    ButtonWidget enhance, repeat, promote, shop, close;
    /** +15 장비: 강화 단추가 각성 단추로 바뀜. */
    boolean awakenMode;
    CheckboxWidget protect;
    // 결과 연출
    int resultCode = -1, resultLevel;
    float resultT;

    public EnhanceScreen(int npc) {
        super(GuildScreen.title(npc));
        this.npc = npc;
    }

    public static void open(int npc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof EnhanceScreen s && s.npc == npc) return;
        mc.setScreen(new EnhanceScreen(npc));
    }

    /** 서버가 보낸 강화 · 승급 결과. */
    public static void onResult(int code, int level, int slot) {
        if (MinecraftClient.getInstance().currentScreen instanceof EnhanceScreen s) {
            s.resultCode = code;
            s.resultLevel = level;
            s.resultT = 0;
            s.selected = slot;
        }
    }

    static PlayerInventory inv() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : mc.player.getInventory();
    }

    /** 장비가 든 칸 (0~35 가방, 36~39 갑옷, 40 왼손). */
    List<Integer> gearSlots() {
        List<Integer> l = new ArrayList<>();
        PlayerInventory inv = inv();
        if (inv == null) return l;
        for (int i = 0; i < 41; i++) if (inv.getStack(i).getItem() instanceof GearItem) l.add(i);
        return l;
    }

    ItemStack sel() {
        PlayerInventory inv = inv();
        if (inv == null || selected < 0) return ItemStack.EMPTY;
        ItemStack s = inv.getStack(selected);
        return s.getItem() instanceof GearItem ? s : ItemStack.EMPTY;
    }

    @Override
    protected void init() {
        pw = Math.min(440, width - 12);
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        if (selected < 0) {
            PlayerInventory inv = inv();
            if (inv != null && inv.getMainHandStack().getItem() instanceof GearItem) selected = inv.selectedSlot;
            else if (!gearSlots().isEmpty()) selected = gearSlots().get(0);
        }
        int rx = px + 160;
        enhance = addDrawableChild(ButtonWidget.builder(Text.translatable("smith.abilitywar.btn.enhance"), b -> {
            if (sel().isEmpty()) return;
            if (awakenMode) RpgClient.send(13, String.valueOf(selected), npc);
            else RpgClient.send(11, selected + ":" + (protect.isChecked() ? 1 : 0), npc);
        }).dimensions(px + pw - 110, py + 150, 100, 18).build());
        repeat = addDrawableChild(ButtonWidget.builder(Text.translatable("smith.abilitywar.btn.repeat"), b -> {
            if (!sel().isEmpty() && !awakenMode) RpgClient.send(11, selected + ":0:20", npc);
        }).dimensions(px + pw - 214, py + 150, 100, 18).build());
        protect = addDrawableChild(new CheckboxWidget(rx + 2, py + 148, 20, 20, Text.empty(), false));
        promote = addDrawableChild(ButtonWidget.builder(Text.translatable("smith.abilitywar.btn.promote"), b -> {
            if (!sel().isEmpty()) RpgClient.send(12, String.valueOf(selected), npc);
        }).dimensions(px + pw - 214, py + ph - 24, 108, 18).build());
        shop = addDrawableChild(ButtonWidget.builder(Text.translatable("smith.abilitywar.btn.shop"), b -> ShopScreen.open(npc, ShopNpcEntity.BLACKSMITH))
                .dimensions(px + 10, py + ph - 24, 140, 18).build());
        close = addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.close"), b -> close()).dimensions(px + pw - 100, py + ph - 24, 90, 18).build());
        tick();
    }

    @Override
    public void tick() {
        ItemStack s = sel();
        PlayerInventory inv = inv();
        boolean has = !s.isEmpty() && inv != null;
        int lv = has ? GearEnhance.level(s) : 0;
        GearGrade g = has ? ((GearItem) s.getItem()).grade() : GearGrade.COMMON;
        boolean creative = MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.getAbilities().creativeMode;
        int max = has ? GearEnhance.maxLevel(s) : GearEnhance.MAX;
        awakenMode = has && lv >= max;
        enhance.setMessage(Text.translatable(awakenMode ? "smith.abilitywar.btn.awaken" : "smith.abilitywar.btn.enhance"));
        if (awakenMode) {
            int a = GearEnhance.awaken(s);
            boolean am = true;
            for (GearEnhance.Mat m : GearEnhance.awakenMats(a)) am &= Shops.count(inv, m.item()) >= m.count();
            enhance.active = a < GearEnhance.AWAKEN_MAX && (creative || (am && RpgClient.gold >= GearEnhance.awakenGold(a)));
        } else enhance.active = has && lv < max && (creative || (Shops.count(inv, RpgItems.ENHANCE_STONE) >= GearEnhance.stoneCost(s)
                && RpgClient.gold >= GearEnhance.goldCost(s)));
        protect.visible = has && GearEnhance.failDrops(s) && lv < max;
        repeat.visible = has && !awakenMode && !GearEnhance.failDrops(s) && lv < max && GearEnhance.chance(s) < 100;
        repeat.active = enhance.active;
        GearEnhance.Ascend asc = has ? GearEnhance.nextAscend(s) : null;
        boolean mats = true;
        if (asc != null) {
            for (GearEnhance.Mat m : GearEnhance.ascendMats(asc)) mats &= Shops.count(inv, m.item()) >= m.count();
            promote.setMessage(Text.translatable("smith.abilitywar.btn." + asc.key()));
            promote.active = lv >= GearEnhance.BEYOND_MAX && (creative || (mats && RpgClient.gold >= GearEnhance.ascendGold(asc)));
            promote.visible = true;
        } else {
            if (has && GearEnhance.canPromote(g)) for (GearEnhance.Mat m : GearEnhance.promoteMats(g)) mats &= Shops.count(inv, m.item()) >= m.count();
            promote.setMessage(Text.translatable("smith.abilitywar.btn.promote"));
            promote.active = has && GearEnhance.canPromote(g) && lv >= GearEnhance.promoteMin(g) && (creative || (mats && RpgClient.gold >= GearEnhance.promoteGold(g)));
            promote.visible = has && GearEnhance.canPromote(g);
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        if (e == null || mc.player == null || e.squaredDistanceTo(mc.player) > 12 * 12) close();
    }

    static final int ROW = 20;

    int listRows() {
        return (ph - 30 - 80) / ROW;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        List<Integer> l = gearSlots();
        int y = py + 82;
        for (int i = scroll; i < l.size() && i < scroll + listRows(); i++) {
            if (RpgUi.in(mx, my, px + 12, y, px + 148, y + ROW - 2)) {
                selected = l.get(i);
                resultCode = -1;
                return true;
            }
            y += ROW;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int max = Math.max(0, gearSlots().size() - listRows());
        scroll = MathHelper.clamp(scroll - (int) Math.signum(amount), 0, max);
        return true;
    }

    int mouseX, mouseY;

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        mouseX = mx;
        mouseY = my;
        renderBackground(ctx);
        RpgUi.panel(ctx, px, py, px + pw, py + ph);
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerInventory inv = inv();
        RpgUi.header(ctx, textRenderer, Text.translatable("smith.abilitywar.title"), px + pw / 2, py + 8);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.say"), px + 12, py + 32, 0xB8AC90);
        String money = GoldHud.fmt(RpgClient.gold) + " G";
        int mw = textRenderer.getWidth(money);
        GoldHud.coin(ctx, px + pw - 14 - mw - 13, py + 30, 11);
        ctx.drawTextWithShadow(textRenderer, money, px + pw - 12 - mw, py + 32, 0xFFE9A8);

        // ---- 내 장비
        int lx0 = px + 10, lx1 = px + 150, ly0 = py + 48, ly1 = py + ph - 30;
        RpgUi.inset(ctx, lx0, ly0, lx1, ly1);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.my_gear"), lx0 + 5, ly0 + 5, RpgUi.GOLD);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.scroll_hint").formatted(Formatting.DARK_GRAY), lx0 + 5, ly0 + 18, 0x6E6E78);
        List<Integer> l = gearSlots();
        ItemStack hover = null;
        int y = py + 82;
        for (int i = scroll; i < l.size() && i < scroll + listRows(); i++) {
            int slot = l.get(i);
            ItemStack st = inv.getStack(slot);
            boolean s = slot == selected, h = RpgUi.in(mx, my, lx0 + 2, y, lx1 - 2, y + ROW - 2);
            HudDraw.rect(ctx, lx0 + 2, y, lx1 - 2, y + ROW - 2, s ? 0x60584018 : h ? 0x30FFE0A0 : 0x20000000);
            if (s) HudDraw.rect(ctx, lx0 + 2, y, lx0 + 4, y + ROW - 2, 0xFF000000 | RpgUi.GOLD);
            ctx.drawItem(st, lx0 + 5, y + 1);
            String nm = textRenderer.trimToWidth(st.getName().getString(), lx1 - lx0 - 30);
            ctx.drawTextWithShadow(textRenderer, Text.literal(nm), lx0 + 24, y + 5, ((GearItem) st.getItem()).grade().color);
            if (slot >= 36) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(lx1 - 14, y + 1, 0);
                ctx.getMatrices().scale(0.6f, 0.6f, 1);
                ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.worn"), 0, 0, 0x8FD3FF);
                ctx.getMatrices().pop();
            }
            if (h) hover = st;
            y += ROW;
        }
        if (l.isEmpty()) RpgUi.wrapped(ctx, textRenderer, Text.translatable("smith.abilitywar.no_gear"), lx0 + 6, py + 84, lx1 - lx0 - 12, RpgUi.DIM, ly1 - 4);

        // ---- 강화
        int rx0 = px + 158, rx1 = px + pw - 10;
        ItemStack st = sel();
        RpgUi.inset(ctx, rx0, ly0, rx1, py + 172);
        if (!st.isEmpty()) {
            GearItem gi = (GearItem) st.getItem();
            GearGrade g = gi.grade();
            int lv = GearEnhance.level(st);
            // 큰 아이콘
            ctx.getMatrices().push();
            ctx.getMatrices().translate(rx0 + 6, ly0 + 5, 0);
            ctx.getMatrices().scale(2f, 2f, 1);
            ctx.drawItem(st, 0, 0);
            ctx.getMatrices().pop();
            ctx.drawTextWithShadow(textRenderer, st.getName(), rx0 + 44, ly0 + 7, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, Text.literal("◆ ").append(g.displayName()).styled(s -> s.withColor(g.color)), rx0 + 44, ly0 + 19, 0xFFFFFF);
            // 별 15 (? · 아르반티아: +99 막대 + 단계)
            int sx = rx0 + 44 + textRenderer.getWidth(Text.literal("◆ ").append(g.displayName())) + 8;
            if (GearEnhance.beyond(g)) {
                int stg = GearEnhance.stage(st), bc = GearEnhance.colorOf(st);
                if (stg > 0) {
                    Text sn = Text.translatable("gear.abilitywar.ui.stage." + stg);
                    ctx.drawTextWithShadow(textRenderer, sn, sx, ly0 + 19, bc);
                    sx += textRenderer.getWidth(sn) + 6;
                }
                int bw = Math.max(40, rx1 - 8 - sx - 30);
                HudDraw.rect(ctx, sx, ly0 + 20, sx + bw, ly0 + 26, 0xFF1A1A22);
                HudDraw.rect(ctx, sx, ly0 + 20, sx + (int) (bw * lv / (float) GearEnhance.BEYOND_MAX), ly0 + 26, 0xFF000000 | bc);
                sx += bw + 6;
            } else for (int i = 1; i <= GearEnhance.MAX; i++) {
                int col = i <= lv ? GearEnhance.color(i) : 0x3A3A44;
                ctx.drawTextWithShadow(textRenderer, i <= lv ? "★" : "☆", sx, ly0 + 19, col);
                sx += 8 + (i % 5 == 0 ? 3 : 0);
            }
            int aw = GearEnhance.awaken(st);
            for (int i = 1; i <= aw; i++) {
                ctx.drawTextWithShadow(textRenderer, "✦", sx, ly0 + 19, GearEnhance.AWAKEN_COLOR);
                sx += 8;
            }
            int ty = ly0 + 40;
            if (lv >= GearEnhance.maxLevel(st)) {
                hover = awakenPanel(ctx, st, rx0, rx1, ly0, ty, inv, hover);
            } else {
                Text arrow = GearEnhance.levelText(st, lv).append(Text.literal("  →  ").formatted(Formatting.GRAY)).append(GearEnhance.levelText(st, lv + 1));
                ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.next").append(" ").append(arrow), rx0 + 8, ty, 0xE8DCC0);
                ty += 12;
                // 능력치 미리보기
                for (Text t : preview(st, lv)) {
                    ctx.drawTextWithShadow(textRenderer, t, rx0 + 12, ty, 0xFFFFFF);
                    ty += 10;
                }
                int ch = GearEnhance.chance(st);
                int cc = ch >= 80 ? 0x7CE87C : ch >= 50 ? 0xFFE070 : 0xFF8A5A;
                ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.chance", ch), rx1 - 8 - textRenderer.getWidth(Text.translatable("smith.abilitywar.chance", ch)), ly0 + 40, cc);
                Text fail = GearEnhance.failDrops(st) ? Text.translatable("smith.abilitywar.fail_drop", lv - 1).formatted(Formatting.RED)
                        : Text.translatable(ch >= 100 ? "smith.abilitywar.fail_none" : "smith.abilitywar.fail_keep").formatted(Formatting.GRAY);
                ctx.drawTextWithShadow(textRenderer, fail, rx1 - 8 - textRenderer.getWidth(fail), ly0 + 52, 0xFFFFFF);
                // 재료
                int cy = py + 124;
                cost(ctx, rx0 + 8, cy, new ItemStack(RpgItems.ENHANCE_STONE), Shops.count(inv, RpgItems.ENHANCE_STONE), GearEnhance.stoneCost(st), true);
                goldCost(ctx, rx0 + 150, cy, GearEnhance.goldCost(st));
                if (protect.visible) {
                    int have = Shops.count(inv, RpgItems.PROTECTION_SCROLL);
                    ctx.drawItem(new ItemStack(RpgItems.PROTECTION_SCROLL), rx0 + 26, py + 149);
                    ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.use_scroll", have), rx0 + 46, py + 154, have > 0 ? 0xE8DCC0 : 0xFF6A6A);
                }
            }
        }

        // ---- 승급
        int pz0 = py + 178, pz1 = py + ph - 30;
        RpgUi.inset(ctx, rx0, pz0, rx1, pz1);
        if (!st.isEmpty()) {
            GearGrade g = ((GearItem) st.getItem()).grade();
            int lv = GearEnhance.level(st);
            GearEnhance.Ascend asc = GearEnhance.nextAscend(st);
            if (asc != null) {
                hover = ascendPanel(ctx, st, asc, rx0, rx1, pz0, inv, hover);
            } else if (!GearEnhance.canPromote(g)) {
                ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.promote_top").formatted(Formatting.GRAY), rx0 + 8, pz0 + 8, 0xFFFFFF);
            } else {
                GearGrade ng = GearEnhance.next(g);
                Text title = Text.translatable("smith.abilitywar.promote_to").append(" ").append(ng.displayName().copy().styled(s -> s.withColor(ng.color).withBold(true)));
                ctx.drawTextWithShadow(textRenderer, title, rx0 + 8, pz0 + 6, 0xFFE9A8);
                int need = GearEnhance.promoteMin(g);
                boolean lvOk = lv >= need;
                ctx.drawTextWithShadow(textRenderer, Text.literal(lvOk ? "✔ " : "✖ ").formatted(lvOk ? Formatting.GREEN : Formatting.RED)
                        .append(Text.translatable("smith.abilitywar.promote_req", need, lv).formatted(Formatting.GRAY)), rx0 + 16 + textRenderer.getWidth(title), pz0 + 6, 0xFFFFFF);
                // 재료: 광물 · 몬스터 · 보스 전리품 + 돈
                int mx0 = rx0 + 8;
                for (GearEnhance.Mat m : GearEnhance.promoteMats(g)) {
                    ItemStack icon = new ItemStack(m.item());
                    int have = Shops.count(inv, m.item());
                    ctx.drawItem(icon, mx0, pz0 + 18);
                    String t = have + "/" + m.count();
                    ctx.drawTextWithShadow(textRenderer, t, mx0 + 18, pz0 + 23, have >= m.count() ? 0x7CE87C : 0xFF6A6A);
                    if (RpgUi.in(mouseX, mouseY, mx0, pz0 + 18, mx0 + 16, pz0 + 34)) hover = icon;
                    mx0 += 18 + textRenderer.getWidth(t) + 10;
                }
                goldCost(ctx, mx0 + 2, pz0 + 22, GearEnhance.promoteGold(g));
                ctx.getMatrices().push();
                ctx.getMatrices().translate(rx0 + 8, pz0 + 40, 0);
                ctx.getMatrices().scale(0.75f, 0.75f, 1);
                ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.promote_note", lv / 2).formatted(Formatting.GRAY), 0, 0, 0xFFFFFF);
                ctx.getMatrices().pop();
            }
        }
        super.render(ctx, mx, my, delta);
        renderResult(ctx, delta);
        if (hover != null) ctx.drawItemTooltip(textRenderer, hover, mx, my);
    }

    /** 각성 (+15 장비): ★a → ★a+1, 능력치 미리보기, 확률, 재료 (심연의 결정 · 보스의 영혼 · 용의 심장) + 돈. */
    ItemStack awakenPanel(DrawContext ctx, ItemStack st, int rx0, int rx1, int ly0, int ty, PlayerInventory inv, ItemStack hover) {
        int a = GearEnhance.awaken(st);
        if (a >= GearEnhance.AWAKEN_MAX) {
            ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.awaken_maxed").styled(s -> s.withColor(GearEnhance.AWAKEN_COLOR).withBold(true)), rx0 + 8, ty, 0xFFFFFF);
            return hover;
        }
        Text arrow = Text.literal("★" + a).styled(s -> s.withColor(GearEnhance.AWAKEN_COLOR)).append(Text.literal("  →  ").formatted(Formatting.GRAY))
                .append(Text.literal("★" + (a + 1)).styled(s -> s.withColor(GearEnhance.AWAKEN_COLOR).withBold(true)));
        ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.awaken_next").append(" ").append(arrow), rx0 + 8, ty, 0xE8DCC0);
        ty += 12;
        int lv = GearEnhance.statLevel(st);
        for (Text t : preview(st, lv, lv + GearEnhance.AWAKEN_STEP)) {
            ctx.drawTextWithShadow(textRenderer, t, rx0 + 12, ty, 0xFFFFFF);
            ty += 10;
        }
        int ch = GearEnhance.awakenChance(a);
        Text ct = Text.translatable("smith.abilitywar.chance", ch);
        ctx.drawTextWithShadow(textRenderer, ct, rx1 - 8 - textRenderer.getWidth(ct), ly0 + 40, ch >= 60 ? 0x7CE87C : ch >= 40 ? 0xFFE070 : 0xFF8A5A);
        Text fail = Text.translatable("smith.abilitywar.awaken_fail").formatted(Formatting.GRAY);
        ctx.drawTextWithShadow(textRenderer, fail, rx1 - 8 - textRenderer.getWidth(fail), ly0 + 52, 0xFFFFFF);
        int mx0 = rx0 + 8, cy = py + 124;
        for (GearEnhance.Mat m : GearEnhance.awakenMats(a)) {
            ItemStack icon = new ItemStack(m.item());
            int have = Shops.count(inv, m.item());
            ctx.drawItem(icon, mx0, cy - 4);
            String t = have + "/" + m.count();
            ctx.drawTextWithShadow(textRenderer, t, mx0 + 18, cy + 1, have >= m.count() ? 0x7CE87C : 0xFF6A6A);
            if (RpgUi.in(mouseX, mouseY, mx0, cy - 4, mx0 + 16, cy + 12)) hover = icon;
            mx0 += 18 + textRenderer.getWidth(t) + 8;
        }
        goldCost(ctx, mx0 + 2, cy, GearEnhance.awakenGold(a));
        return hover;
    }

    /** ? 장비: 초월+ · 정제 · 아르반티아 인피니티 승급 (재료가 많아서 두 줄). */
    ItemStack ascendPanel(DrawContext ctx, ItemStack st, GearEnhance.Ascend asc, int rx0, int rx1, int pz0, PlayerInventory inv, ItemStack hover) {
        int lv = GearEnhance.level(st);
        int col = asc == GearEnhance.Ascend.TRANSCEND ? GearEnhance.TRANSCEND_COLOR : asc == GearEnhance.Ascend.REFINE ? GearEnhance.REFINE_COLOR : GearEnhance.INFINITY_COLOR;
        Text title = Text.translatable("smith.abilitywar.ascend." + asc.key()).styled(s -> s.withColor(col).withBold(true));
        ctx.drawTextWithShadow(textRenderer, title, rx0 + 8, pz0 + 5, 0xFFFFFF);
        boolean lvOk = lv >= GearEnhance.BEYOND_MAX;
        ctx.drawTextWithShadow(textRenderer, Text.literal(lvOk ? "✔ " : "✖ ").formatted(lvOk ? Formatting.GREEN : Formatting.RED)
                .append(Text.translatable("smith.abilitywar.ascend_req", GearEnhance.BEYOND_MAX, lv).formatted(Formatting.GRAY)), rx0 + 16 + textRenderer.getWidth(title), pz0 + 5, 0xFFFFFF);
        int mx0 = rx0 + 8, my0 = pz0 + 16, right = rx1 - 6;
        for (GearEnhance.Mat m : GearEnhance.ascendMats(asc)) {
            ItemStack icon = new ItemStack(m.item());
            int have = Shops.count(inv, m.item());
            String t = have + "/" + m.count();
            int w = 18 + textRenderer.getWidth(t) + 8;
            if (mx0 + w > right) {
                mx0 = rx0 + 8;
                my0 += 17;
            }
            ctx.drawItem(icon, mx0, my0);
            ctx.drawTextWithShadow(textRenderer, t, mx0 + 18, my0 + 5, have >= m.count() ? 0x7CE87C : 0xFF6A6A);
            if (RpgUi.in(mouseX, mouseY, mx0, my0, mx0 + 16, my0 + 16)) hover = icon;
            mx0 += w;
        }
        String gs = GoldHud.fmt(GearEnhance.ascendGold(asc)) + " G";
        if (mx0 + 16 + textRenderer.getWidth(gs) > right) {
            mx0 = rx0 + 8;
            my0 += 17;
        }
        goldCost(ctx, mx0 + 2, my0 + 4, GearEnhance.ascendGold(asc));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(rx0 + 8, my0 + 19, 0);
        ctx.getMatrices().scale(0.7f, 0.7f, 1);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("smith.abilitywar.ascend_note." + asc.key()).formatted(Formatting.GRAY), 0, 0, 0xFFFFFF);
        ctx.getMatrices().pop();
        return hover;
    }

    /** 동전 + "120 G" (가진 돈이 모자라면 빨강). */
    void goldCost(DrawContext ctx, int x, int y, long cost) {
        GoldHud.coin(ctx, x, y - 1, 11);
        ctx.drawTextWithShadow(textRenderer, GoldHud.fmt(cost) + " G", x + 14, y + 1, RpgClient.gold >= cost ? 0xFFE9A8 : 0xFF6A6A);
    }

    /** "아이콘 가진 수 / 필요 수" (모자라면 빨강). */
    void cost(DrawContext ctx, int x, int y, ItemStack icon, int have, int need, boolean label) {
        ctx.drawItem(icon, x, y - 4);
        Text t = Text.literal(have + " / " + need);
        ctx.drawTextWithShadow(textRenderer, t, x + 19, y + 1, have >= need ? 0x7CE87C : 0xFF6A6A);
        if (!label) return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 19, y + 10, 0);
        ctx.getMatrices().scale(0.7f, 0.7f, 1);
        ctx.drawTextWithShadow(textRenderer, icon.getName(), 0, 0, 0x9A8D70);
        ctx.getMatrices().pop();
    }

    /** 다음 강화의 능력치 변화 줄. */
    List<Text> preview(ItemStack st, int lv) {
        int sl = GearEnhance.statLevel(st);
        ItemStack next = st.copy();
        GearEnhance.set(next, lv + 1);
        return preview(st, sl, Math.max(sl + 1, GearEnhance.statLevel(next)));
    }

    /** 능력치 단계 lv → to 의 변화 줄. */
    List<Text> preview(ItemStack st, int lv, int to) {
        List<Text> out = new ArrayList<>();
        if (st.getItem() instanceof GearWeaponItem w) {
            Map<GearStat, Double> a = GearEnhance.weaponStats(w.def, lv), b = GearEnhance.weaponStats(w.def, to);
            for (GearStat s : new GearStat[]{GearStat.ATTACK_DAMAGE, GearStat.ABILITY_DAMAGE}) {
                if (!b.containsKey(s)) continue;
                out.add(Text.translatable("gear.abilitywar.stat." + s.key).formatted(Formatting.GRAY).append(" ")
                        .append(Text.literal(s.format(a.getOrDefault(s, 0.0))).formatted(Formatting.WHITE))
                        .append(Text.literal(" → ").formatted(Formatting.DARK_GRAY)).append(Text.literal(s.format(b.get(s))).formatted(Formatting.GREEN)));
            }
        } else if (st.getItem() instanceof GearArmorItem ar) {
            int base = ar.getProtection();
            out.add(Text.translatable("gear.abilitywar.stat.armor").formatted(Formatting.GRAY).append(" ")
                    .append(Text.literal("+" + fmt(GearEnhance.armorBonus(base, lv))).formatted(Formatting.WHITE))
                    .append(Text.literal(" → ").formatted(Formatting.DARK_GRAY)).append(Text.literal("+" + fmt(GearEnhance.armorBonus(base, to))).formatted(Formatting.GREEN)));
            out.add(Text.translatable("gear.abilitywar.stat.max_health").formatted(Formatting.GRAY).append(" ")
                    .append(Text.literal("+" + fmt(GearEnhance.healthBonus(lv))).formatted(Formatting.WHITE))
                    .append(Text.literal(" → ").formatted(Formatting.DARK_GRAY)).append(Text.literal("+" + fmt(GearEnhance.healthBonus(to))).formatted(Formatting.GREEN)));
        }
        return out;
    }

    static String fmt(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    /** 결과 연출: 가운데 큰 글자가 튀어나왔다가 사라짐. */
    void renderResult(DrawContext ctx, float delta) {
        if (resultCode < 0) return;
        resultT += delta;
        float t = resultT / 20f;
        if (t > 2.2f) {
            resultCode = -1;
            return;
        }
        float a = t < 1.6f ? 1 : 1 - (t - 1.6f) / 0.6f;
        float pop = t < 0.15f ? 0.6f + t / 0.15f * 0.7f : t < 0.3f ? 1.3f - (t - 0.15f) / 0.15f * 0.3f : 1f;
        Text msg;
        int col;
        switch (resultCode) {
            case 0 -> {
                msg = Text.translatable("smith.abilitywar.result.success", resultLevel);
                col = GearEnhance.color(resultLevel);
            }
            case 2 -> {
                msg = Text.translatable("smith.abilitywar.result.down", resultLevel);
                col = 0xFF5A5A;
            }
            case 3 -> {
                msg = Text.translatable("smith.abilitywar.result.promoted");
                col = 0xFFD040;
            }
            case 4 -> {
                msg = Text.translatable("smith.abilitywar.result.awakened", resultLevel);
                col = GearEnhance.AWAKEN_COLOR;
            }
            case 5 -> {
                msg = Text.translatable("smith.abilitywar.result.awaken_fail");
                col = 0x9A88B8;
            }
            case 6 -> {
                msg = Text.translatable("smith.abilitywar.result.repeat", resultLevel);
                col = 0xFFE070;
            }
            case 7 -> {
                msg = Text.translatable("smith.abilitywar.result.transcend");
                col = GearEnhance.TRANSCEND_COLOR;
            }
            case 8 -> {
                msg = Text.translatable("smith.abilitywar.result.refine");
                col = GearEnhance.REFINE_COLOR;
            }
            case 9 -> {
                msg = Text.translatable("smith.abilitywar.result.infinity");
                col = GearEnhance.INFINITY_COLOR;
            }
            default -> {
                msg = Text.translatable("smith.abilitywar.result.fail");
                col = 0xB8B8B8;
            }
        }
        int cx = px + pw / 2 + 80, cy = py + 100;
        int w = textRenderer.getWidth(msg);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);
        HudDraw.rect(ctx, cx - w - 30, cy - 18, cx + w + 30, cy + 18, HudDraw.argb(0.94f * a, 0x0C0904));
        RpgHud.frame(ctx, cx - w - 30, cy - 18, cx + w + 30, cy + 18, col);
        RpgHud.frame(ctx, cx - w - 28, cy - 16, cx + w + 28, cy + 16, 0x3A2C12);
        ctx.getMatrices().pop();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 410);
        ctx.getMatrices().scale(2f * pop, 2f * pop, 1);
        ctx.drawTextWithShadow(textRenderer, msg, -w / 2, -4, HudDraw.argb(a, col));
        ctx.getMatrices().pop();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
