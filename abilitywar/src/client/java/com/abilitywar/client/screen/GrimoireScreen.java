package com.abilitywar.client.screen;

import com.abilitywar.ability.warlock.GrimoireScreenHandler;
import com.abilitywar.net.AWNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 흑마법서 (G): 왼쪽 = 해골 장비 + 해골 미리보기 + 내 인벤토리, 오른쪽 = 영혼 목록.
 * 영혼 줄: [아이콘] 이름 · 상태 · ★(소환 대상 선택) · 해제(소환 중일 때) · ✕(버리기)
 */
public class GrimoireScreen extends HandledScreen<GrimoireScreenHandler> {
    // ---- 서버에서 받은 영혼 목록
    public static final class Entry {
        public int id;
        public String type;
        public boolean captured;
        public int status; // 0 준비, 1 소환 중, 2 쿨타임
        public int cooldown;
    }

    public static final List<Entry> SOULS = new ArrayList<>();
    public static int selected = -1, maxSouls = 24, maxRaised = 6;
    public static boolean skeletonOut;
    static long receivedAt;

    public static void read(PacketByteBuf buf) {
        SOULS.clear();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            Entry e = new Entry();
            e.id = buf.readVarInt();
            e.type = buf.readString();
            e.captured = buf.readBoolean();
            e.status = buf.readByte();
            e.cooldown = buf.readVarInt();
            SOULS.add(e);
        }
        selected = buf.readVarInt();
        maxSouls = buf.readVarInt();
        maxRaised = buf.readVarInt();
        skeletonOut = buf.readBoolean();
        receivedAt = MinecraftClient.getInstance().world != null ? MinecraftClient.getInstance().world.getTime() : 0;
    }

    static final int LEFT_W = 176, PANEL_W = 176, ROW_H = 20, LIST_Y = 22, LIST_ROWS = 7;
    static final int BG = 0xF0140E1C, BG2 = 0xF01C1428, EDGE = 0xFF48E8A0, EDGE2 = 0xFF2A6A50, SLOT = 0xFF0A0810, SLOT_EDGE = 0xFF3A5A4A;
    private int scroll;
    private SkeletonEntity preview;

    public GrimoireScreen(GrimoireScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        backgroundWidth = LEFT_W + PANEL_W + 4;
        backgroundHeight = 196;
        playerInventoryTitleY = GrimoireScreenHandler.INV_Y - 11;
        titleY = 6;
    }

    @Override
    protected void init() {
        super.init();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) preview = EntityType.SKELETON.create(mc.world);
    }

    static void action(int action, int id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        buf.writeVarInt(id);
        ClientPlayNetworking.send(AWNet.C2S_SOUL_ACTION, buf);
    }

    // ------------------------------------------------------------------ 그리기
    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        // 왼쪽 판
        frame(ctx, x, y, LEFT_W, backgroundHeight);
        // 오른쪽 판 (영혼)
        frame(ctx, x + LEFT_W + 4, y, PANEL_W, backgroundHeight);
        // 칸 배경
        for (Slot s : handler.slots) {
            ctx.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, SLOT_EDGE);
            ctx.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT);
        }
        // 해골 미리보기 (지금 장비를 입힌 모습)
        int px0 = x + 28, py0 = y + 16, px1 = x + 78, py1 = y + 88;
        ctx.fill(px0, py0, px1, py1, 0xFF06040A);
        ctx.drawBorder(px0, py0, px1 - px0, py1 - py0, EDGE2);
        if (preview != null) {
            for (int i = 0; i < 6; i++) preview.equipStack(com.abilitywar.ability.warlock.Necromancy.GEAR[i], handler.slots.get(i).getStack().copy());
            InventoryScreen.drawEntity(ctx, (px0 + px1) / 2, py1 - 6, 30, (px0 + px1) / 2f - mouseX, py0 + 20 - mouseY, preview);
        }
        // 손 칸 이름
        ctx.drawText(textRenderer, Text.translatable("abilitywar.warlock.hands"), x + 80, y + 62, 0xFF9AD8B8, false);
        // 해골 상태
        Text st = Text.translatable(skeletonOut ? "abilitywar.warlock.skeleton_out" : "abilitywar.warlock.skeleton_rest");
        ctx.drawText(textRenderer, st, x + 82, y + 20, skeletonOut ? 0xFF7CFFB0 : 0xFF8A8A9A, false);
        ctx.drawText(textRenderer, Text.translatable("abilitywar.warlock.skeleton_hint"), x + 82, y + 32, 0xFF6A7A72, false);
    }

    void frame(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, BG);
        ctx.fillGradient(x, y, x + w, y + 14, 0xF02A1E3A, BG2);
        ctx.drawBorder(x, y, w, h, EDGE2);
        ctx.drawBorder(x + 1, y + 1, w - 2, h - 2, 0x6048E8A0);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(textRenderer, title, titleX, titleY, 0xFFB8FFD8, false);
        ctx.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFF9AA8A0, false);
        // 오른쪽 판 머리
        int ox = LEFT_W + 4;
        int raised = 0;
        for (Entry e : SOULS) if (e.status == 1) raised++;
        ctx.drawText(textRenderer, Text.translatable("abilitywar.warlock.souls", SOULS.size(), maxSouls), ox + 6, 6, 0xFFB8FFD8, false);
        Text r = Text.translatable("abilitywar.warlock.raised_count", raised, maxRaised);
        ctx.drawText(textRenderer, r, ox + PANEL_W - 6 - textRenderer.getWidth(r), 6, 0xFF9AD8B8, false);
        long now = client.world != null ? client.world.getTime() : 0;
        int mx = mouseX - x, my = mouseY - y;
        if (SOULS.isEmpty()) {
            ctx.drawText(textRenderer, Text.translatable("abilitywar.warlock.empty1"), ox + 8, LIST_Y + 6, 0xFF8A8A9A, false);
            ctx.drawText(textRenderer, Text.translatable("abilitywar.warlock.empty2"), ox + 8, LIST_Y + 18, 0xFF6A6A7A, false);
        }
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, SOULS.size() - LIST_ROWS));
        for (int i = 0; i < LIST_ROWS && i + scroll < SOULS.size(); i++) {
            Entry e = SOULS.get(i + scroll);
            int ry = LIST_Y + i * ROW_H;
            boolean sel = e.id == selected;
            boolean hover = mx >= ox + 4 && mx < ox + PANEL_W - 4 && my >= ry && my < ry + ROW_H - 1;
            ctx.fill(ox + 4, ry, ox + PANEL_W - 4, ry + ROW_H - 1, sel ? 0x6048E8A0 : hover ? 0x40FFFFFF : 0x30000000);
            if (sel) ctx.drawBorder(ox + 4, ry, PANEL_W - 8, ROW_H - 1, EDGE);
            ctx.drawItem(icon(e.type), ox + 6, ry + 1);
            EntityType<?> t = Registries.ENTITY_TYPE.get(new Identifier(e.type));
            Text name = t.getName();
            ctx.drawText(textRenderer, name, ox + 25, ry + 2, e.captured ? 0xFFFFD86A : 0xFFE8E8F0, false);
            Text status;
            int col;
            if (e.status == 1) {
                status = Text.translatable("abilitywar.warlock.st_out");
                col = 0xFF7CFFB0;
            } else if (e.status == 2) {
                long left = Math.max(0, e.cooldown - (now - receivedAt));
                status = Text.translatable("abilitywar.warlock.st_cd", left / 1200, (left / 20) % 60);
                col = 0xFFFF8A6A;
            } else {
                status = Text.translatable("abilitywar.warlock.st_ready");
                col = 0xFF9AA8A0;
            }
            Text kind = Text.translatable(e.captured ? "abilitywar.warlock.kind_captured" : "abilitywar.warlock.kind_killed");
            ctx.drawText(textRenderer, Text.empty().append(kind).append(" · ").append(status), ox + 25, ry + 11, col, false);
            // 버튼: ★ 선택 / 해제 / ✕
            button(ctx, ox + PANEL_W - 52, ry + 4, sel ? "★" : "☆", mx, my, sel ? 0xFFFFD86A : 0xFFB0B0B0);
            if (e.status == 1) button(ctx, ox + PANEL_W - 36, ry + 4, "⇩", mx, my, 0xFF7CFFB0);
            button(ctx, ox + PANEL_W - 20, ry + 4, "✕", mx, my, 0xFFFF6A6A);
        }
        // 스크롤 표시
        if (SOULS.size() > LIST_ROWS) {
            int h = LIST_ROWS * ROW_H;
            int bh = Math.max(10, h * LIST_ROWS / SOULS.size());
            int by = LIST_Y + (h - bh) * scroll / Math.max(1, SOULS.size() - LIST_ROWS);
            ctx.fill(ox + PANEL_W - 3, LIST_Y, ox + PANEL_W - 2, LIST_Y + h, 0x40FFFFFF);
            ctx.fill(ox + PANEL_W - 3, by, ox + PANEL_W - 2, by + bh, EDGE);
        }
        // 아래쪽 버튼
        int by = backgroundHeight - 22;
        wideButton(ctx, ox + 6, by, 80, Text.translatable("abilitywar.warlock.dismiss_all"), mx, my);
        wideButton(ctx, ox + 90, by, 80, Text.translatable("abilitywar.warlock.dismiss_skeleton"), mx, my);
        ctx.drawText(textRenderer, Text.translatable("abilitywar.warlock.help"), ox + 6, by - 12, 0xFF6A7A72, false);
    }

    static ItemStack icon(String type) {
        EntityType<?> t = Registries.ENTITY_TYPE.get(new Identifier(type));
        SpawnEggItem egg = SpawnEggItem.forEntity(t);
        return new ItemStack(egg != null ? egg : Items.SOUL_LANTERN);
    }

    void button(DrawContext ctx, int bx, int by, String label, int mx, int my, int color) {
        boolean h = mx >= bx && mx < bx + 13 && my >= by && my < by + 12;
        ctx.fill(bx, by, bx + 13, by + 12, h ? 0xC0405048 : 0xA0201820);
        ctx.drawBorder(bx, by, 13, 12, h ? EDGE : EDGE2);
        ctx.drawText(textRenderer, label, bx + 7 - textRenderer.getWidth(label) / 2, by + 2, color, false);
    }

    void wideButton(DrawContext ctx, int bx, int by, int w, Text label, int mx, int my) {
        boolean h = mx >= bx && mx < bx + w && my >= by && my < by + 16;
        ctx.fill(bx, by, bx + w, by + 16, h ? 0xC0405048 : 0xA0201820);
        ctx.drawBorder(bx, by, w, 16, h ? EDGE : EDGE2);
        ctx.drawText(textRenderer, label, bx + w / 2 - textRenderer.getWidth(label) / 2, by + 4, 0xFFE0F0E8, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - x, my = (int) mouseY - y;
        int ox = LEFT_W + 4;
        if (button == 0 && mx >= ox && mx < ox + PANEL_W) {
            for (int i = 0; i < LIST_ROWS && i + scroll < SOULS.size(); i++) {
                Entry e = SOULS.get(i + scroll);
                int ry = LIST_Y + i * ROW_H;
                if (my < ry + 4 || my >= ry + 16) continue;
                if (hit(mx, my, ox + PANEL_W - 52, ry + 4)) {
                    action(0, e.id);
                    click();
                    return true;
                }
                if (e.status == 1 && hit(mx, my, ox + PANEL_W - 36, ry + 4)) {
                    action(1, e.id);
                    click();
                    return true;
                }
                if (hit(mx, my, ox + PANEL_W - 20, ry + 4)) {
                    action(2, e.id);
                    click();
                    return true;
                }
            }
            int by = backgroundHeight - 22;
            if (my >= by && my < by + 16) {
                if (mx >= ox + 6 && mx < ox + 86) {
                    action(3, 0);
                    click();
                    return true;
                }
                if (mx >= ox + 90 && mx < ox + 170) {
                    action(4, 0);
                    click();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    static boolean hit(int mx, int my, int bx, int by) {
        return mx >= bx && mx < bx + 13 && my >= by && my < by + 12;
    }

    void click() {
        client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX - x > LEFT_W) {
            scroll = MathHelper.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, SOULS.size() - LIST_ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
