package com.abilitywar.client.screen;

import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.rpg.JobQuests;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** 퀘스트 목록 + 상세 (퀘스트 창과 전직 교관 대화에서 같이 씀). */
final class QuestView {
    List<JobQuests.Quest> quests = List.of();
    int selected;

    void set(List<JobQuests.Quest> list) {
        String keep = selected < quests.size() ? quests.get(selected).id() : RpgClient.quest;
        quests = list;
        selected = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(keep.isEmpty() ? RpgClient.quest : keep)) selected = i;
    }

    JobQuests.Quest current() {
        return selected < quests.size() ? quests.get(selected) : null;
    }

    static int statusColor(RpgClient.Status s) {
        return switch (s) {
            case AVAILABLE -> 0xFFE070;
            case ACTIVE -> 0x8AC8FF;
            case READY -> 0x7CE87C;
            case COMPLETED -> 0x6A7A60;
            default -> 0x7A7060;
        };
    }

    static String statusMark(RpgClient.Status s) {
        return switch (s) {
            case AVAILABLE -> "!";
            case ACTIVE -> "…";
            case READY -> "?";
            case COMPLETED -> "✔";
            default -> "✖";
        };
    }

    /** 왼쪽 목록. */
    void renderList(DrawContext ctx, TextRenderer tr, int x0, int y0, int x1, int y1, int mx, int my) {
        RpgUi.inset(ctx, x0, y0, x1, y1);
        int y = y0 + 3;
        for (int i = 0; i < quests.size(); i++) {
            JobQuests.Quest q = quests.get(i);
            RpgClient.Status s = RpgClient.status(q);
            boolean sel = i == selected, hover = RpgUi.in(mx, my, x0 + 2, y, x1 - 2, y + 22);
            HudDraw.rect(ctx, x0 + 2, y, x1 - 2, y + 22, sel ? 0x60584018 : hover ? 0x30FFE0A0 : 0x20000000);
            if (sel) HudDraw.rect(ctx, x0 + 2, y, x0 + 4, y + 22, 0xFF000000 | RpgUi.GOLD);
            int col = statusColor(s);
            ctx.drawTextWithShadow(tr, statusMark(s), x0 + 8, y + 7, col);
            ctx.drawTextWithShadow(tr, RpgClient.questName(q), x0 + 18, y + 3, s == RpgClient.Status.COMPLETED ? 0x8A8A7A : 0xFFFFFF);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x0 + 18, y + 13, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1);
            ctx.drawTextWithShadow(tr, Text.translatable("rpg.abilitywar.status." + s.name().toLowerCase(), RpgClient.requiredLevel(q)), 0, 0, col);
            ctx.getMatrices().pop();
            y += 24;
        }
        if (quests.isEmpty()) RpgUi.wrapped(ctx, tr, Text.translatable("rpg.abilitywar.screen.no_quests"), x0 + 6, y0 + 6, x1 - x0 - 12, RpgUi.DIM, y1 - 10);
    }

    boolean clickList(double mx, double my, int x0, int y0, int x1) {
        int y = y0 + 3;
        for (int i = 0; i < quests.size(); i++) {
            if (RpgUi.in(mx, my, x0 + 2, y, x1 - 2, y + 22)) {
                selected = i;
                return true;
            }
            y += 24;
        }
        return false;
    }

    /** 오른쪽 상세. */
    void renderDetail(DrawContext ctx, TextRenderer tr, int x0, int y0, int x1, int y1) {
        RpgUi.inset(ctx, x0, y0, x1, y1);
        JobQuests.Quest q = current();
        if (q == null) return;
        int x = x0 + 7, w = x1 - x0 - 14, y = y0 + 6;
        RpgClient.Status s = RpgClient.status(q);
        ctx.drawTextWithShadow(tr, RpgClient.questName(q).formatted(Formatting.BOLD), x, y, 0xFFE9A8);
        y += 12;
        y = RpgUi.wrapped(ctx, tr, Text.translatable("rpg.abilitywar.quest." + q.id() + ".desc"), x, y, w, 0xB8AC90, y1 - 70) + 4;
        // 조건
        ctx.drawTextWithShadow(tr, Text.translatable("rpg.abilitywar.screen.requires"), x, y, RpgUi.GOLD);
        y += 10;
        int need = RpgClient.requiredLevel(q);
        boolean lvOk = RpgClient.level >= need;
        ctx.drawTextWithShadow(tr, Text.literal(lvOk ? "✔ " : "✖ ").formatted(lvOk ? Formatting.GREEN : Formatting.RED)
                .append(Text.translatable("rpg.abilitywar.screen.req_level", need).formatted(Formatting.GRAY)), x + 4, y, 0xFFFFFF);
        y += 10;
        boolean clsOk;
        Text req;
        switch (q.kind()) {
            case GRAND -> {
                clsOk = com.abilitywar.client.ClientState.ability.equals(q.base());
                req = Text.translatable("rpg.abilitywar.screen.req_class2", Text.translatable("ability.abilitywar." + q.base()));
            }
            case KING -> {
                clsOk = q.allows(RpgClient.baseClass);
                Text names = Text.empty();
                String[] bs = q.base().split(",");
                for (int k = 0; k < bs.length; k++) {
                    if (k > 0) ((net.minecraft.text.MutableText) names).append("·");
                    ((net.minecraft.text.MutableText) names).append(Text.translatable("ability.abilitywar." + bs[k]));
                }
                req = Text.translatable("rpg.abilitywar.screen.req_class", names);
            }
            default -> {
                clsOk = RpgClient.baseClass.equals(q.base());
                req = Text.translatable("rpg.abilitywar.screen.req_class", Text.translatable("ability.abilitywar." + q.base()));
            }
        }
        ctx.drawTextWithShadow(tr, Text.literal(clsOk ? "✔ " : "✖ ").formatted(clsOk ? Formatting.GREEN : Formatting.RED)
                .append(req.copy().formatted(Formatting.GRAY)), x + 4, y, 0xFFFFFF);
        y += 13;
        // 목표
        ctx.drawTextWithShadow(tr, Text.translatable("rpg.abilitywar.screen.objectives"), x, y, RpgUi.GOLD);
        y += 10;
        boolean mine = q.id().equals(RpgClient.quest);
        for (int i = 0; i < q.objectives().size(); i++) {
            JobQuests.Objective o = q.objectives().get(i);
            int have = mine ? Math.min(o.count(), RpgClient.count(i)) : s == RpgClient.Status.COMPLETED ? o.count() : 0;
            boolean ok = have >= o.count();
            if (o.type() == JobQuests.Type.ITEM) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + 2, y - 1, 0);
                ctx.getMatrices().scale(0.6f, 0.6f, 1);
                ctx.drawItem(new ItemStack(RpgClient.item(o.target())), 0, 0);
                ctx.getMatrices().pop();
            } else ctx.drawTextWithShadow(tr, "·", x + 5, y, 0x9A8D70);
            ctx.drawTextWithShadow(tr, RpgClient.objectiveText(o), x + 14, y, ok ? 0x7CE87C : 0xD8CCB0);
            String n = have + "/" + o.count();
            ctx.drawTextWithShadow(tr, n, x1 - 7 - tr.getWidth(n), y, ok ? 0x7CE87C : 0xFFE070);
            y += 9;
            RpgUi.bar(ctx, x + 14, y, w - 14, 2, (float) have / o.count(), ok ? 0x5AC85A : 0xC8A040);
            y += 5;
        }
        y += 3;
        // 보상
        if (y < y1 - 30) {
            ctx.drawTextWithShadow(tr, Text.translatable("rpg.abilitywar.screen.rewards"), x, y, RpgUi.GOLD);
            y += 10;
            Text rc = q.kind() == JobQuests.Kind.GRAND ? Text.translatable("rpg.abilitywar.screen.reward_grand", RpgClient.className(q.reward(), true))
                    : Text.translatable(q.kind() == JobQuests.Kind.KING ? "rpg.abilitywar.screen.reward_special" : "rpg.abilitywar.screen.reward_class", Text.translatable("ability.abilitywar." + q.reward()));
            ctx.drawTextWithShadow(tr, Text.literal("◆ ").formatted(Formatting.GOLD).append(rc.copy().formatted(Formatting.WHITE)), x + 4, y, 0xFFFFFF);
            y += 10;
            ctx.drawTextWithShadow(tr, Text.literal("◆ ").formatted(Formatting.GOLD).append(Text.translatable("rpg.abilitywar.screen.reward_xp", q.xp()).formatted(Formatting.GRAY)), x + 4, y, 0xFFFFFF);
            y += 10;
            ctx.drawTextWithShadow(tr, Text.literal("◆ ").formatted(Formatting.GOLD).append(Text.translatable("rpg.abilitywar.screen.reward_gear_" + q.kind().name().toLowerCase()).formatted(q.kind() == JobQuests.Kind.GRAND ? Formatting.GOLD : q.kind() == JobQuests.Kind.KING ? Formatting.LIGHT_PURPLE : Formatting.BLUE)), x + 4, y, 0xFFFFFF);
        }
    }

    static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }
}
