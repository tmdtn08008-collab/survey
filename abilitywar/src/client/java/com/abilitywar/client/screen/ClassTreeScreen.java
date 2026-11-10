package com.abilitywar.client.screen;

import com.abilitywar.AbilityWar;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.hud.HudDraw;
import com.abilitywar.client.hud.RpgHud;
import com.abilitywar.client.input.InputHandler;
import com.abilitywar.client.rpg.RpgClient;
import com.abilitywar.net.AWNet;
import com.abilitywar.rpg.ClassTree;
import com.abilitywar.rpg.JobQuests;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 직업 트리 (기본 키: B). 1차(뿌리) → 2차(가지) → 3차(잎, 준비 중).
 * 일반 플레이어는 보기만, 관리자/자유 선택 규칙이 켜져 있으면 눌러서 바로 바꿈.
 */
public class ClassTreeScreen extends Screen {
    record Node(String key, int tier, String parent, int x, int y) {
    }

    final List<Node> nodes = new ArrayList<>();
    int nw, nh;
    String hovered;

    public ClassTreeScreen() {
        super(Text.translatable("rpg.abilitywar.screen.tree"));
    }

    /** 왕의 특별 직업 (능력이 등록된 것만). */
    static List<String> specials() {
        List<String> out = new ArrayList<>();
        for (String k : ClassTree.SPECIAL) if (com.abilitywar.ability.Abilities.byString(k) != null) out.add(k);
        return out;
    }

    int specialX = -1;

    @Override
    protected void init() {
        nodes.clear();
        List<String> sp = specials();
        int leaves = sp.size();
        for (String b : ClassTree.BASE) leaves += ClassTree.ADVANCE.get(b).size();
        int gap = 8, extra = sp.isEmpty() ? 0 : 14;
        nw = MathHelper.clamp((width - 24 - extra - gap * (leaves - 1)) / leaves, 40, 74);
        nh = nw * 40 / 64 + 12;
        int total = leaves * nw + (leaves - 1) * gap + extra;
        int x = (width - total) / 2;
        int y1 = 52, y2 = y1 + nh + 34, y3 = y2 + nh + 30;
        if (y3 + nh > height - 30) {
            int sh = (y3 + nh) - (height - 30);
            y2 -= sh / 2;
            y3 -= sh;
        }
        for (String b : ClassTree.BASE) {
            List<String> kids = ClassTree.ADVANCE.get(b);
            int bx0 = x;
            for (String k : kids) {
                nodes.add(new Node(k, 2, b, x, y2));
                nodes.add(new Node("+" + k, 3, k, x, y3));
                x += nw + gap;
            }
            int span = x - gap - bx0;
            nodes.add(new Node(b, 1, "", bx0 + span / 2 - nw / 2, y1));
        }
        // 왕의 직업: 1차와 잇지 않고 따로 (왕관 표시)
        specialX = sp.isEmpty() ? -1 : x + extra - gap / 2;
        x += extra;
        for (String k : sp) {
            nodes.add(new Node(k, 2, "", x, y2));
            nodes.add(new Node("+" + k, 3, k, x, y3));
            x += nw + gap;
        }
        if (RpgClient.canPickFreely()) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("rpg.abilitywar.tree.all"), b -> client.setScreen(new AbilitySelectScreen(false)))
                    .dimensions(width - 108, height - 24, 100, 18).build());
        }
    }

    Node find(String key) {
        for (Node n : nodes) if (n.key.equals(key)) return n;
        return null;
    }

    /** 내 길: 현재 직업과 그 부모. */
    boolean onPath(String key) {
        String cur = ClientState.ability;
        if (key.startsWith("+")) return RpgClient.grandActive() && key.substring(1).equals(cur);
        return key.equals(cur) || (!ClassTree.isSpecial(cur) && key.equals(ClassTree.baseOf(cur))) || (ClassTree.isSpecial(cur) && key.equals(RpgClient.baseClass));
    }

    boolean current(String key) {
        String cur = ClientState.ability;
        return key.startsWith("+") ? RpgClient.grandActive() && key.substring(1).equals(cur) : key.equals(cur) && !RpgClient.grandActive();
    }

    int w(Node n) {
        return nw;
    }

    int h(Node n) {
        return nh;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (InputHandler.SELECT.matchesKey(key, scan)) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (RpgClient.canPickFreely()) for (Node n : nodes) {
            if (RpgUi.in(mx, my, n.x, n.y, n.x + w(n), n.y + h(n))) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(n.key.startsWith("+") ? n.key.substring(1) : n.key);
                ClientPlayNetworking.send(AWNet.C2S_SELECT, buf);
                close();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        HudDraw.rect(ctx, 0, 0, width, height, 0x900A0804);
        float time = (client.player != null ? client.player.age : 0) + delta;
        RpgUi.header(ctx, textRenderer, title, width / 2, 10);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("rpg.abilitywar.tree.sub"), width / 2, 32, RpgUi.DIM);
        // 단계 이름
        Node anyB = null, any2 = null, any3 = null;
        for (Node n : nodes) {
            if (n.tier == 1 && anyB == null) anyB = n;
            if (n.tier == 2 && any2 == null) any2 = n;
            if (n.tier == 3 && any3 == null) any3 = n;
        }
        // 잇는 선
        for (Node n : nodes) {
            if (n.parent.isEmpty()) continue;
            Node p = find(n.parent);
            if (p == null) continue;
            boolean lit = onPath(n.key) && onPath(n.parent);
            int col = lit ? 0xFF000000 | RpgHud.GOLD : n.tier == 3 ? 0x70806030 : 0x90806A40;
            int ax = p.x + w(p) / 2, ay = p.y + h(p), bx = n.x + w(n) / 2, by = n.y;
            int mid = (ay + by) / 2;
            HudDraw.rect(ctx, ax, ay, ax + 1, mid + 1, col);
            HudDraw.rect(ctx, Math.min(ax, bx), mid, Math.max(ax, bx) + 1, mid + 1, col);
            HudDraw.rect(ctx, bx, mid, bx + 1, by, col);
            if (lit) {
                float k = (time * 0.04f) % 1f;
                int sy = (int) MathHelper.lerp(k, ay, by);
                HudDraw.rect(ctx, (sy < mid ? ax : bx) - 1, sy - 1, (sy < mid ? ax : bx) + 2, sy + 2, 0xFFFFF6DC);
            }
        }
        hovered = null;
        for (Node n : nodes) {
            int x = n.x, y = n.y, ww = w(n), hh = h(n);
            boolean hover = RpgUi.in(mx, my, x, y, x + ww, y + hh);
            if (hover) hovered = n.key;
            boolean grandNode = n.key.startsWith("+");
            String ak = grandNode ? n.key.substring(1) : n.key;
            boolean cur = current(n.key), path = onPath(n.key);
            boolean done = grandNode ? RpgClient.completed.contains("grand_" + ak) || (RpgClient.grand && ak.equals(ClientState.ability))
                    : n.tier == 2 && (ak.equals(ClientState.ability) || RpgClient.completed.contains("job_" + ak) || RpgClient.completed.contains("king_" + ak));
            int ih = hh - 12;
            ctx.drawTexture(AbilityWar.id("textures/gui/card_" + ak + ".png"), x, y, ww, ih, 0, 0, 256, 160, 256, 160);
            if (grandNode) {
                // 3차: 금빛을 덧칠하고 오른쪽 위에 "大" 도장
                float gp = 0.5f + 0.5f * MathHelper.sin(time * 0.1f + n.x * 0.05f);
                HudDraw.rect(ctx, x, y, x + ww, y + ih, HudDraw.argb(0.16f + 0.08f * gp, 0xFFD86A));
                int sx = x + ww - 12, sy = y + 2;
                HudDraw.rect(ctx, sx - 1, sy - 1, sx + 11, sy + 11, 0xFFFFD86A);
                HudDraw.rect(ctx, sx, sy, sx + 10, sy + 10, 0xFFA8141A);
                ctx.drawText(textRenderer, "大", sx + 1, sy + 1, 0xFFFFF0C0, false);
            }
            if (!path && !hover) HudDraw.rect(ctx, x, y, x + ww, y + ih, 0x70000000);
            HudDraw.rect(ctx, x, y + ih, x + ww, y + hh, path ? 0xF0342610 : grandNode ? 0xF0201606 : 0xF0141008);
            Text nm = RpgClient.className(ak, grandNode);
            float sc = Math.min(1f, (ww - 4) / (float) textRenderer.getWidth(nm));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + ww / 2f, y + ih + 2.5f, 0);
            ctx.getMatrices().scale(sc, sc, 1);
            ctx.drawCenteredTextWithShadow(textRenderer, nm, 0, 0, path ? 0xFFE9A8 : grandNode ? 0xE8C878 : 0xD0C8B8);
            ctx.getMatrices().pop();
            int border = cur ? lerp(0xC89030, 0xFFF0B0, 0.5f + 0.5f * MathHelper.sin(time * 0.2f)) : path ? RpgHud.GOLD : hover ? 0xA89060 : 0x4A3C20;
            RpgHud.frame(ctx, x - 1, y - 1, x + ww + 1, y + hh + 1, border);
            if (cur) RpgHud.frame(ctx, x - 2, y - 2, x + ww + 2, y + hh + 2, 0x6A5020);
            if (done && !cur) ctx.drawTextWithShadow(textRenderer, Text.literal("✔").formatted(Formatting.GREEN), x + 2, y + 2, 0xFFFFFF);
        }
        if (specialX >= 0) {
            // 왕의 직업 구역: 세로 금선 + 왕관 표시
            HudDraw.rect(ctx, specialX, anyB != null ? anyB.y : 50, specialX + 1, height - 30, 0x60FFD86A);
            Text kt = Text.translatable("rpg.abilitywar.tree.king");
            ctx.getMatrices().push();
            ctx.getMatrices().translate(specialX + 7 + nw / 2f, (anyB != null ? anyB.y + nh / 2f : 60) - 4, 0);
            ctx.getMatrices().scale(0.85f, 0.85f, 1);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("♛ ").append(kt), 0, 0, 0xFFD86A);
            ctx.getMatrices().pop();
        }
        // 단계 표시 (왼쪽)
        if (anyB != null) tierLabel(ctx, 1, anyB.y + h(anyB) / 2);
        if (any2 != null) tierLabel(ctx, 2, any2.y + h(any2) / 2);
        if (any3 != null) tierLabel(ctx, 3, any3.y + h(any3) / 2);
        String hint = RpgClient.canPickFreely() ? "rpg.abilitywar.tree.hint_admin" : "rpg.abilitywar.tree.hint";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable(hint, InputHandler.SELECT.getBoundKeyLocalizedText()), width / 2, height - 18, 0x807860);
        super.render(ctx, mx, my, delta);
        if (hovered != null) ctx.drawTooltip(textRenderer, tooltip(hovered), mx, my);
    }

    void tierLabel(DrawContext ctx, int tier, int cy) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(6, cy - 4, 0);
        ctx.getMatrices().scale(0.8f, 0.8f, 1);
        ctx.drawTextWithShadow(textRenderer, Text.translatable("rpg.abilitywar.tier." + tier), 0, 0, 0x8A7A58);
        ctx.getMatrices().pop();
    }

    List<Text> tooltip(String key) {
        List<Text> t = new ArrayList<>();
        if (key.startsWith("+")) {
            String k = key.substring(1);
            t.add(RpgClient.className(k, true).formatted(Formatting.GOLD));
            t.add(Text.translatable("rpg.abilitywar.tree.grand_desc").formatted(Formatting.GRAY));
            JobQuests.Quest q = JobQuests.ALL.get("grand_" + k);
            if (q != null) {
                t.add(Text.translatable(q.giver().equals(JobQuests.KING) ? "rpg.abilitywar.tree.how_king" : "rpg.abilitywar.tree.how_grand", RpgClient.grandLevel).formatted(Formatting.DARK_AQUA));
                t.add(Text.literal("  ").append(RpgClient.questName(q)).formatted(Formatting.YELLOW));
            }
            if (current(key)) t.add(Text.translatable("abilitywar.screen.current").formatted(Formatting.GREEN));
            return t;
        }
        t.add(Text.translatable("ability.abilitywar." + key).formatted(Formatting.GOLD));
        t.add(Text.translatable("ability.abilitywar." + key + ".desc").formatted(Formatting.GRAY));
        if (ClassTree.tier(key) == 1) {
            t.add(Text.translatable("rpg.abilitywar.tree.base_info").formatted(Formatting.DARK_AQUA));
        } else {
            JobQuests.Quest q = JobQuests.ALL.get("job_" + key);
            if (q == null) q = JobQuests.ALL.get("king_" + key);
            if (q != null && q.kind() == JobQuests.Kind.KING) {
                t.add(Text.translatable("rpg.abilitywar.tree.how_special", q.minLevel()).formatted(Formatting.DARK_AQUA));
                t.add(Text.literal("  ").append(RpgClient.questName(q)).formatted(Formatting.YELLOW));
            } else if (q != null) {
                t.add(Text.translatable("rpg.abilitywar.tree.how", RpgClient.jobLevel, Text.translatable("ability.abilitywar." + q.base())).formatted(Formatting.DARK_AQUA));
                t.add(Text.literal("  ").append(RpgClient.questName(q)).formatted(Formatting.YELLOW));
            }
        }
        if (current(key)) t.add(Text.translatable("abilitywar.screen.current").formatted(Formatting.GREEN));
        else if (RpgClient.canPickFreely()) t.add(Text.translatable("rpg.abilitywar.tree.click").formatted(Formatting.LIGHT_PURPLE));
        return t;
    }

    static int lerp(int a, int b, float k) {
        return RpgHud.lerpColor(a, b, k);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
