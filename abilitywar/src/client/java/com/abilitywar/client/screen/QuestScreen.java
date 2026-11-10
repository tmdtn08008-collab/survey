package com.abilitywar.client.screen;

import com.abilitywar.client.ClientState;
import com.abilitywar.client.input.InputHandler;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.rpg.ClassTree;
import com.abilitywar.rpg.JobQuests;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** 퀘스트 창 (기본 키: J): 내 1차 직업의 전직 퀘스트 목록과 진행 상황. */
public class QuestScreen extends Screen {
    final QuestView view = new QuestView();
    ButtonWidget abandon;
    boolean confirm;
    int px, py, pw = 360, ph = 222;

    public QuestScreen() {
        super(Text.translatable("rpg.abilitywar.screen.quests"));
    }

    /** 내 퀘스트: 진행 중 → 전직 교관 (3차·2차) → 왕. */
    static List<JobQuests.Quest> myQuests() {
        List<JobQuests.Quest> list = new ArrayList<>();
        JobQuests.Quest a = RpgClient.active();
        if (a != null) list.add(a);
        for (String g : new String[]{JobQuests.CLASS_MASTER, JobQuests.KING})
            for (JobQuests.Quest q : RpgClient.questsFor(g)) if (!list.contains(q)) list.add(q);
        return list;
    }

    @Override
    protected void init() {
        pw = Math.min(380, width - 16);
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        view.set(myQuests());
        abandon = addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.screen.abandon"), b -> {
            if (!confirm) {
                confirm = true;
                b.setMessage(Text.translatable("rpg.abilitywar.screen.abandon_confirm"));
                return;
            }
            RpgClient.send(3, "", -1);
            confirm = false;
            b.setMessage(Text.translatable("rpg.abilitywar.screen.abandon"));
        }).dimensions(px + pw - 98, py + ph - 26, 88, 18).build());
        tick();
    }

    @Override
    public void tick() {
        JobQuests.Quest q = view.current();
        abandon.visible = q != null && q.id().equals(RpgClient.quest);
        if (view.quests.size() != myQuests().size() || (RpgClient.active() != null && !view.quests.isEmpty() && view.quests.get(0) != RpgClient.active()))
            view.set(myQuests());
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (InputHandler.QUESTS.matchesKey(key, scan)) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (view.clickList(mx, my, px + 10, py + 50, px + 130)) {
            confirm = false;
            abandon.setMessage(Text.translatable("rpg.abilitywar.screen.abandon"));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        RpgUi.panel(ctx, px, py, px + pw, py + ph);
        RpgUi.header(ctx, textRenderer, title, px + pw / 2, py + 8);
        // 안내 한 줄
        Text hint;
        int tier = ClassTree.tier(ClientState.ability);
        if (tier == 0) hint = Text.translatable("rpg.abilitywar.guide.no_class");
        else if (tier >= 2 && RpgClient.grandActive() && RpgClient.active() == null) hint = Text.translatable("rpg.abilitywar.guide.grand_done");
        else if (tier >= 2 && RpgClient.active() == null) hint = RpgClient.level < RpgClient.grandLevel
                ? Text.translatable("rpg.abilitywar.guide.grand_level", RpgClient.grandLevel, RpgClient.level) : Text.translatable("rpg.abilitywar.guide.grand_visit");
        else if (RpgClient.active() != null) hint = Text.translatable(RpgClient.done(RpgClient.active()) ? "rpg.abilitywar.guide.ready" : "rpg.abilitywar.guide.progress");
        else if (RpgClient.level < RpgClient.jobLevel) hint = Text.translatable("rpg.abilitywar.guide.level", RpgClient.jobLevel, RpgClient.level);
        else hint = Text.translatable("rpg.abilitywar.guide.visit");
        ctx.drawCenteredTextWithShadow(textRenderer, hint, px + pw / 2, py + 34, 0xC8BC98);
        view.renderList(ctx, textRenderer, px + 10, py + 50, px + 130, py + ph - 10, mx, my);
        view.renderDetail(ctx, textRenderer, px + 136, py + 50, px + pw - 10, py + ph - (abandon.visible ? 32 : 10));
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
