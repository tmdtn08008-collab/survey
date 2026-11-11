package com.abilitywar.client.screen;

import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.rpg.Guild;
import com.abilitywar.rpg.JobQuests;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * 모험가 길드 접수원: 가입 (F 등급) · 자기 등급 퀘스트 받기 / 끝내기 / 포기 · 승급 심사.
 * 왼쪽: 등급 배지 + 포인트 막대 + 다음 등급 조건, 가운데: 이 등급의 퀘스트, 오른쪽: 목표 · 보상.
 */
public class GuildScreen extends Screen {
    final int npc;
    int px, py, pw = 420, ph = 240;
    int selected;
    float typed;
    ButtonWidget join, accept, turnIn, abandon, promote, close;

    public GuildScreen(int npc) {
        super(title(npc));
        this.npc = npc;
    }

    static Text title(int npc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        return e != null ? e.getDisplayName() : Text.translatable("entity.abilitywar.guild_clerk");
    }

    public static void open(int npc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof GuildScreen s && s.npc == npc) {
            s.typed = 0;
            return;
        }
        mc.setScreen(new GuildScreen(npc));
    }

    List<Guild.GQuest> quests() {
        return RpgClient.guildRank < 0 ? List.of() : Guild.forRank(RpgClient.guildRank);
    }

    Guild.GQuest current() {
        Guild.GQuest a = RpgClient.guildActive();
        List<Guild.GQuest> l = quests();
        if (selected < l.size()) return l.get(selected);
        return a;
    }

    @Override
    protected void init() {
        pw = Math.min(420, width - 12);
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        // 진행 중인 퀘스트를 먼저 고름
        List<Guild.GQuest> l = quests();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id().equals(RpgClient.guildQuest)) selected = i;
        int by = py + ph - 24;
        join = addDrawableChild(ButtonWidget.builder(Text.translatable("guild.abilitywar.btn.join"), b -> RpgClient.send(5, "", npc))
                .dimensions(px + pw - 200, by, 96, 18).build());
        accept = addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.accept"), b -> {
            Guild.GQuest q = current();
            if (q != null) RpgClient.send(6, q.id(), npc);
        }).dimensions(px + pw - 200, by, 96, 18).build());
        turnIn = addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.turn_in"), b -> RpgClient.send(7, "", npc))
                .dimensions(px + pw - 200, by, 96, 18).build());
        abandon = addDrawableChild(ButtonWidget.builder(Text.translatable("guild.abilitywar.btn.abandon"), b -> RpgClient.send(8, "", npc))
                .dimensions(px + pw - 300, by, 96, 18).build());
        promote = addDrawableChild(ButtonWidget.builder(Text.translatable("guild.abilitywar.btn.promote"), b -> RpgClient.send(9, "", npc))
                .dimensions(px + 10, by, 112, 18).build());
        close = addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.close"), b -> close())
                .dimensions(px + pw - 100, by, 90, 18).build());
        tick();
    }

    @Override
    public void tick() {
        boolean member = RpgClient.guildRank >= 0;
        Guild.GQuest q = current();
        Guild.GQuest a = RpgClient.guildActive();
        join.visible = !member;
        boolean mine = q != null && a != null && q.id().equals(a.id());
        accept.visible = member && a == null && q != null;
        turnIn.visible = member && mine;
        turnIn.active = mine && RpgClient.guildDone(a);
        abandon.visible = member && a != null;
        promote.visible = member && RpgClient.guildRank < Guild.TOP;
        promote.active = Guild.checkPromote(RpgClient.guildRank, RpgClient.guildPoints, RpgClient.level) == null;
        typed += 1.6f;
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        if (e == null || mc.player == null || e.squaredDistanceTo(mc.player) > 12 * 12) close();
    }

    Text line() {
        if (RpgClient.guildRank < 0) return Text.translatable("guild.abilitywar.say.welcome");
        Guild.GQuest a = RpgClient.guildActive();
        if (a != null) return Text.translatable(RpgClient.guildDone(a) ? "guild.abilitywar.say.ready" : "guild.abilitywar.say.progress", RpgClient.guildQuestName(a));
        if (Guild.checkPromote(RpgClient.guildRank, RpgClient.guildPoints, RpgClient.level) == null)
            return Text.translatable("guild.abilitywar.say.promote", Guild.rankName(RpgClient.guildRank + 1));
        if (RpgClient.guildRank >= Guild.TOP) return Text.translatable("guild.abilitywar.say.top");
        return Text.translatable("guild.abilitywar.say.board", Guild.rankName(RpgClient.guildRank));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx0 = px + 132, y = py + 80;
        List<Guild.GQuest> l = quests();
        for (int i = 0; i < l.size(); i++) {
            if (RpgUi.in(mx, my, lx0 + 2, y, lx0 + 128, y + 22)) {
                selected = i;
                return true;
            }
            y += 24;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        RpgUi.panel(ctx, px, py, px + pw, py + ph);
        MinecraftClient mc = MinecraftClient.getInstance();
        float time = (mc.player == null ? 0 : mc.player.age) + delta;
        // ---- 접수원 얼굴 + 이름 + 대사
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        RpgUi.inset(ctx, px + 10, py + 10, px + 56, py + 70);
        if (e instanceof LivingEntity le) {
            ctx.enableScissor(px + 11, py + 11, px + 55, py + 69);
            InventoryScreen.drawEntity(ctx, px + 33, py + 110, 48, -14, 4, le);
            ctx.disableScissor();
        }
        ctx.drawTextWithShadow(textRenderer, title, px + 64, py + 12, 0xFFE9A8);
        String full = line().getString();
        int n = Math.min(full.length(), (int) (typed + delta * 1.6f));
        RpgUi.wrapped(ctx, textRenderer, Text.literal(full.substring(0, n)), px + 64, py + 26, pw - 74, 0xE8DCC0, py + 68);

        // ---- 등급 배지 + 포인트 + 다음 등급
        int bx0 = px + 10, by0 = py + 78, bx1 = px + 126, by1 = py + ph - 30;
        RpgUi.inset(ctx, bx0, by0, bx1, by1);
        int rank = RpgClient.guildRank;
        int col = Guild.color(rank);
        if (rank >= Guild.TOP) col = lerp(0xFFD040, 0xFFFFFF, 0.5f + 0.5f * MathHelper.sin(time * 0.15f));
        String letter = Guild.rankName(rank);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("guild.abilitywar.screen.rank"), bx0 + 6, by0 + 5, RpgUi.GOLD);
        int cxb = (bx0 + bx1) / 2, cyb = by0 + 36;
        HudDraw.rect(ctx, cxb - 22, cyb - 18, cxb + 22, cyb + 18, 0xC0100A04);
        com.abilitywar.client.hud.RpgHud.frame(ctx, cxb - 22, cyb - 18, cxb + 22, cyb + 18, col);
        float sc = letter.length() >= 3 ? 2.0f : letter.length() == 2 ? 2.6f : 3.2f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cxb, cyb, 0);
        ctx.getMatrices().scale(sc, sc, 1);
        ctx.drawText(textRenderer, letter, -textRenderer.getWidth(letter) / 2, -4, 0xFF000000 | col, true);
        ctx.getMatrices().pop();
        int ty = cyb + 24;
        if (rank < 0) {
            RpgUi.wrapped(ctx, textRenderer, Text.translatable("guild.abilitywar.screen.not_member"), bx0 + 6, ty, bx1 - bx0 - 12, RpgUi.DIM, by1 - 6);
        } else if (rank < Guild.TOP) {
            int need = Guild.PROMOTE_POINTS[rank];
            ctx.drawTextWithShadow(textRenderer, Text.translatable("guild.abilitywar.screen.points", RpgClient.guildPoints, need), bx0 + 6, ty, 0xD8CCB0);
            RpgUi.bar(ctx, bx0 + 6, ty + 10, bx1 - bx0 - 12, 3, (float) RpgClient.guildPoints / need, col);
            int nlv = Guild.MIN_LEVEL[rank + 1];
            boolean lvOk = RpgClient.level >= nlv;
            ctx.drawTextWithShadow(textRenderer, Text.translatable("guild.abilitywar.screen.next", Guild.rankName(rank + 1)), bx0 + 6, ty + 18, RpgUi.GOLD);
            ctx.drawTextWithShadow(textRenderer, Text.literal(lvOk ? "✔ " : "✖ ").formatted(lvOk ? Formatting.GREEN : Formatting.RED)
                    .append(Text.translatable("rpg.abilitywar.screen.req_level", nlv).formatted(Formatting.GRAY)), bx0 + 8, ty + 28, 0xFFFFFF);
            boolean ptOk = RpgClient.guildPoints >= need;
            ctx.drawTextWithShadow(textRenderer, Text.literal(ptOk ? "✔ " : "✖ ").formatted(ptOk ? Formatting.GREEN : Formatting.RED)
                    .append(Text.translatable("guild.abilitywar.screen.req_points", need).formatted(Formatting.GRAY)), bx0 + 8, ty + 38, 0xFFFFFF);
        } else {
            ctx.drawTextWithShadow(textRenderer, Text.translatable("guild.abilitywar.screen.top"), bx0 + 6, ty, 0xFFE9A8);
        }
        if (rank >= 0)
            ctx.drawTextWithShadow(textRenderer, Text.translatable("guild.abilitywar.screen.done", RpgClient.guildDone), bx0 + 6, by1 - 12, RpgUi.DIM);

        // ---- 퀘스트 목록 (이 등급만)
        int lx0 = px + 132, lx1 = px + 262, ly0 = py + 78, ly1 = py + ph - 30;
        RpgUi.inset(ctx, lx0, ly0, lx1, ly1);
        List<Guild.GQuest> l = quests();
        Guild.GQuest a = RpgClient.guildActive();
        int y = ly0 + 3 - 1 + 2;
        for (int i = 0; i < l.size(); i++) {
            Guild.GQuest q = l.get(i);
            boolean sel = i == selected, hover = RpgUi.in(mx, my, lx0 + 2, y, lx1 - 2, y + 22), mine = a != null && a.id().equals(q.id());
            HudDraw.rect(ctx, lx0 + 2, y, lx1 - 2, y + 22, sel ? 0x60584018 : hover ? 0x30FFE0A0 : 0x20000000);
            if (sel) HudDraw.rect(ctx, lx0 + 2, y, lx0 + 4, y + 22, 0xFF000000 | RpgUi.GOLD);
            String mark = mine ? (RpgClient.guildDone(q) ? "?" : "…") : "!";
            int mc2 = mine ? (RpgClient.guildDone(q) ? 0x7CE87C : 0x8AC8FF) : a == null ? 0xFFE070 : 0x7A7060;
            ctx.drawTextWithShadow(textRenderer, mark, lx0 + 8, y + 7, mc2);
            ctx.drawTextWithShadow(textRenderer, RpgClient.guildQuestName(q), lx0 + 18, y + 3, 0xFFFFFF);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(lx0 + 18, y + 13, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            ctx.drawTextWithShadow(textRenderer, Text.literal("[" + Guild.rankName(q.rank()) + "] ").styled(st -> st.withColor(Guild.color(q.rank())))
                    .append(Text.translatable("guild.abilitywar.screen.reward_points", q.points()).formatted(Formatting.GRAY)), 0, 0, 0xFFFFFF);
            ctx.getMatrices().pop();
            y += 24;
        }
        if (l.isEmpty())
            RpgUi.wrapped(ctx, textRenderer, Text.translatable(rank < 0 ? "guild.abilitywar.screen.join_first" : "rpg.abilitywar.screen.no_quests"), lx0 + 6, ly0 + 6, lx1 - lx0 - 12, RpgUi.DIM, ly1 - 6);
        else
            RpgUi.wrapped(ctx, textRenderer, Text.translatable("guild.abilitywar.screen.rank_only", Guild.rankName(rank)), lx0 + 6, ly1 - 22, lx1 - lx0 - 12, RpgUi.DIM, ly1 - 2);

        // ---- 상세: 목표 + 보상
        int dx0 = px + 268, dx1 = px + pw - 10;
        RpgUi.inset(ctx, dx0, ly0, dx1, ly1);
        Guild.GQuest q = rank < 0 ? null : current();
        if (q != null) {
            int x = dx0 + 7, w = dx1 - dx0 - 14;
            int dy = ly0 + 6;
            ctx.drawTextWithShadow(textRenderer, RpgClient.guildQuestName(q).formatted(Formatting.BOLD), x, dy, 0xFFE9A8);
            dy += 12;
            dy = RpgUi.wrapped(ctx, textRenderer, Text.translatable("guild.abilitywar.quest." + q.id() + ".desc"), x, dy, w, 0xB8AC90, ly1 - 70) + 3;
            ctx.drawTextWithShadow(textRenderer, Text.translatable("rpg.abilitywar.screen.objectives"), x, dy, RpgUi.GOLD);
            dy += 10;
            boolean mine = a != null && a.id().equals(q.id());
            for (int i = 0; i < q.objectives().size(); i++) {
                JobQuests.Objective o = q.objectives().get(i);
                int have = mine ? Math.min(o.count(), RpgClient.guildCount(i)) : 0;
                boolean ok = have >= o.count();
                if (o.type() == JobQuests.Type.ITEM) {
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(x + 2, dy - 1, 0);
                    ctx.getMatrices().scale(0.6f, 0.6f, 1);
                    ctx.drawItem(new ItemStack(RpgClient.item(o.target())), 0, 0);
                    ctx.getMatrices().pop();
                } else ctx.drawTextWithShadow(textRenderer, "·", x + 5, dy, 0x9A8D70);
                ctx.drawTextWithShadow(textRenderer, RpgClient.objectiveText(o), x + 14, dy, ok ? 0x7CE87C : 0xD8CCB0);
                String cnt = have + "/" + o.count();
                ctx.drawTextWithShadow(textRenderer, cnt, dx1 - 7 - textRenderer.getWidth(cnt), dy, ok ? 0x7CE87C : 0xFFE070);
                dy += 9;
                RpgUi.bar(ctx, x + 14, dy, w - 14, 2, (float) have / o.count(), ok ? 0x5AC85A : 0xC8A040);
                dy += 6;
            }
            dy += 2;
            ctx.drawTextWithShadow(textRenderer, Text.translatable("rpg.abilitywar.screen.rewards"), x, dy, RpgUi.GOLD);
            dy += 10;
            int gradeCol = q.gear().color;
            Text[] rewards = {
                    Text.translatable("guild.abilitywar.screen.reward_points", q.points()).formatted(Formatting.YELLOW),
                    Text.translatable("rpg.abilitywar.screen.reward_xp", q.xp()).formatted(Formatting.GRAY),
                    Text.translatable("guild.abilitywar.screen.reward_gold", com.abilitywar.client.hud.GoldHud.fmt(q.gold())).formatted(Formatting.GOLD),
                    Text.translatable("guild.abilitywar.screen.reward_crystal", new ItemStack(com.abilitywar.item.RpgItems.crystal(Guild.crystal(q.rank()))).getName()).formatted(Formatting.LIGHT_PURPLE),
                    Text.translatable("guild.abilitywar.screen.reward_gear", q.gear().displayName(), Math.round(q.gearChance() * 100)).styled(st -> st.withColor(gradeCol)),
                    Text.translatable("guild.abilitywar.screen.reward_stones", Guild.stones(q.rank())).formatted(Formatting.AQUA),
                    Guild.promotionStones(q.rank()) > 0 ? Text.translatable("guild.abilitywar.screen.reward_promotion", Guild.promotionStones(q.rank())).formatted(Formatting.LIGHT_PURPLE) : null};
            for (Text t : rewards) {
                if (t == null) continue;
                if (dy > ly1 - 10) break;
                ctx.drawTextWithShadow(textRenderer, Text.literal("◆ ").formatted(Formatting.GOLD).append(t), x + 4, dy, 0xFFFFFF);
                dy += 10;
            }
        }
        super.render(ctx, mx, my, delta);
    }

    static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
