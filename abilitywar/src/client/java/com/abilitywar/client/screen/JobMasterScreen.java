package com.abilitywar.client.screen;

import com.abilitywar.client.ClientState;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.rpg.ClassTree;
import com.abilitywar.rpg.JobQuests;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;

/** 전직 교관 대화: 1차 직업에 이어지는 2차 전직 퀘스트를 받고, 끝내면 전직. */
public class JobMasterScreen extends Screen {
    final int npc;
    final QuestView view = new QuestView();
    ButtonWidget accept, turnIn, close;
    int px, py, pw = 380, ph = 230;
    float typed;

    public JobMasterScreen(int npc) {
        super(Text.translatable("entity.abilitywar.job_master"));
        this.npc = npc;
    }

    public static void open(int npc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof JobMasterScreen s && s.npc == npc) {
            s.view.set(QuestScreen.myQuests());
            s.typed = 0;
            return;
        }
        mc.setScreen(new JobMasterScreen(npc));
    }

    @Override
    protected void init() {
        pw = Math.min(400, width - 16);
        px = (width - pw) / 2;
        py = Math.max(4, (height - ph) / 2);
        view.set(QuestScreen.myQuests());
        int by = py + ph - 26;
        accept = addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.accept"), b -> {
            JobQuests.Quest q = view.current();
            if (q != null) RpgClient.send(1, q.id(), npc);
        }).dimensions(px + pw - 196, by, 90, 18).build());
        turnIn = addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.turn_in"), b -> RpgClient.send(2, "", npc))
                .dimensions(px + pw - 196, by, 90, 18).build());
        close = addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.npc.close"), b -> close())
                .dimensions(px + pw - 100, by, 90, 18).build());
        tick();
    }

    @Override
    public void tick() {
        JobQuests.Quest q = view.current();
        RpgClient.Status s = q == null ? null : RpgClient.status(q);
        accept.visible = s == RpgClient.Status.AVAILABLE;
        turnIn.visible = s == RpgClient.Status.READY;
        typed += 1.6f;
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        if (e == null || mc.player == null || e.squaredDistanceTo(mc.player) > 12 * 12) close();
    }

    Text line() {
        int tier = ClassTree.tier(ClientState.ability);
        JobQuests.Quest a = RpgClient.active();
        if (tier == 0) return Text.translatable("rpg.abilitywar.npc.say.no_class");
        if (tier >= 2) return Text.translatable("rpg.abilitywar.npc.say.advanced", Text.translatable("ability.abilitywar." + ClientState.ability));
        if (a != null) return Text.translatable(RpgClient.done(a) ? "rpg.abilitywar.npc.say.ready" : "rpg.abilitywar.npc.say.progress", RpgClient.questName(a));
        if (RpgClient.level < RpgClient.jobLevel) return Text.translatable("rpg.abilitywar.npc.say.level", RpgClient.jobLevel);
        return Text.translatable("rpg.abilitywar.npc.say.choose." + RpgClient.baseClass);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (view.clickList(mx, my, px + 10, py + 78, px + 130)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        RpgUi.panel(ctx, px, py, px + pw, py + ph);
        // NPC 얼굴 + 이름 + 대사
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity e = mc.world == null ? null : mc.world.getEntityById(npc);
        RpgUi.inset(ctx, px + 10, py + 10, px + 56, py + 70);
        if (e instanceof LivingEntity le) {
            // 얼굴과 가슴만 (이름표·머리 위 표시는 화면이 열려 있으면 JobMasterRenderer 가 숨김)
            ctx.enableScissor(px + 11, py + 11, px + 55, py + 69);
            InventoryScreen.drawEntity(ctx, px + 33, py + 110, 48, -14, 4, le);
            ctx.disableScissor();
        }
        ctx.drawTextWithShadow(textRenderer, title, px + 64, py + 12, 0xFFE9A8);
        Text say = line();
        String full = say.getString();
        int n = Math.min(full.length(), (int) (typed + delta * 1.6f));
        RpgUi.wrapped(ctx, textRenderer, Text.literal(full.substring(0, n)), px + 64, py + 26, pw - 74, 0xE8DCC0, py + 68);
        view.renderList(ctx, textRenderer, px + 10, py + 78, px + 130, py + ph - 10, mx, my);
        view.renderDetail(ctx, textRenderer, px + 136, py + 78, px + pw - 10, py + ph - 32);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
