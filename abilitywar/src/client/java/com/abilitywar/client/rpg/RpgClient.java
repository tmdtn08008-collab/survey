package com.abilitywar.client.rpg;

import com.abilitywar.client.ClientState;
import com.abilitywar.rpg.ClassTree;
import com.abilitywar.rpg.JobQuests;
import com.abilitywar.rpg.RpgManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/** 로컬 플레이어의 RPG 상태 (RpgManager.sync 로 받음). */
public final class RpgClient {
    public static final String[] STATS = RpgManager.STAT_KEYS;
    public static boolean synced;
    public static int level = 1;
    public static long xp, need = 11;
    public static int points;
    public static final int[] stats = new int[5];
    public static String baseClass = "", quest = "";
    public static final int[] progress = new int[8];
    public static int progressN;
    public static int jobLevel = 30;
    public static final Set<String> completed = new HashSet<>();
    public static boolean selfSelect;

    /** HUD 연출용. */
    public static float xpShown;
    public static int xpFlash, pointsFlash;
    public static int bannerTicks, bannerLevel;
    private static int lastLevel = -1;

    public static void read(PacketByteBuf buf) {
        int lv = buf.readVarInt();
        long x = buf.readVarLong();
        need = Math.max(1, buf.readVarLong());
        int pts = buf.readVarInt();
        for (int i = 0; i < 5; i++) stats[i] = buf.readVarInt();
        baseClass = buf.readString();
        quest = buf.readString();
        progressN = buf.readVarInt();
        for (int i = 0; i < progressN; i++) {
            int v = buf.readVarInt();
            if (i < progress.length) progress[i] = v;
        }
        jobLevel = buf.readVarInt();
        int n = buf.readVarInt();
        completed.clear();
        for (int i = 0; i < n; i++) completed.add(buf.readString());
        selfSelect = buf.isReadable() && buf.readBoolean();
        if (synced && x > xp && lv == level) xpFlash = 14;
        if (synced && pts > points) pointsFlash = 40;
        if (lastLevel >= 0 && lv != level) xpShown = 0;
        xp = x;
        level = lv;
        points = pts;
        lastLevel = lv;
        synced = true;
    }

    public static void tick() {
        float target = (float) xp / need;
        xpShown += (target - xpShown) * 0.2f;
        if (Math.abs(target - xpShown) < 0.001f) xpShown = target;
        if (xpFlash > 0) xpFlash--;
        if (pointsFlash > 0) pointsFlash--;
        if (bannerTicks > 0) bannerTicks--;
    }

    public static void reset() {
        synced = false;
        level = 1;
        xp = 0;
        points = 0;
        java.util.Arrays.fill(stats, 0);
        baseClass = quest = "";
        progressN = 0;
        completed.clear();
        lastLevel = -1;
        bannerTicks = 0;
        xpShown = 0;
    }

    public static void levelUpFx(int lv) {
        if (lv < 0) return; // 전직은 서버가 제목으로 알림
        bannerLevel = lv;
        bannerTicks = 70;
    }

    // ================================================================ 보내기
    /** 0 = 스탯 +1, 4 = 스탯 +5, 1 = 퀘스트 수락, 2 = 완료, 3 = 포기. */
    public static void send(int action, String arg, int npc) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        buf.writeString(arg);
        buf.writeVarInt(npc);
        ClientPlayNetworking.send(RpgManager.C2S_RPG, buf);
    }

    // ================================================================ 퀘스트 상태
    public enum Status {AVAILABLE, LOCKED_LEVEL, ACTIVE, READY, COMPLETED, OTHER_ACTIVE, ADVANCED, WRONG_CLASS}

    public static Status status(JobQuests.Quest q) {
        if (completed.contains(q.id()) || ClientState.ability.equals(q.reward())) return Status.COMPLETED;
        if (q.id().equals(quest)) return done(q) ? Status.READY : Status.ACTIVE;
        if (!baseClass.equals(q.base())) return Status.WRONG_CLASS;
        if (ClassTree.tier(ClientState.ability) >= 2) return Status.ADVANCED;
        if (!quest.isEmpty()) return Status.OTHER_ACTIVE;
        if (level < jobLevel) return Status.LOCKED_LEVEL;
        return Status.AVAILABLE;
    }

    public static JobQuests.Quest active() {
        return quest.isEmpty() ? null : JobQuests.ALL.get(quest);
    }

    public static int count(int i) {
        return i < progressN ? progress[i] : 0;
    }

    public static boolean done(JobQuests.Quest q) {
        if (!q.id().equals(quest)) return false;
        for (int i = 0; i < q.objectives().size(); i++) if (count(i) < q.objectives().get(i).count()) return false;
        return true;
    }

    /** NPC 머리 위 표시: 0 없음, 1 = 받을 수 있음(!), 2 = 진행 중(?), 3 = 완료 가능(?). */
    public static int marker() {
        if (!synced) return 0;
        JobQuests.Quest a = active();
        if (a != null) return done(a) ? 3 : 2;
        if (ClassTree.tier(ClientState.ability) != 1 || level < jobLevel) return 0;
        for (JobQuests.Quest q : JobQuests.forBase(baseClass)) if (status(q) == Status.AVAILABLE) return 1;
        return 0;
    }

    public static MutableText questName(JobQuests.Quest q) {
        return Text.translatable("rpg.abilitywar.quest." + q.id());
    }

    public static MutableText objectiveText(JobQuests.Objective o) {
        return switch (o.type()) {
            case KILL -> Text.translatable("rpg.abilitywar.obj.kill", target(o.target()));
            case KILL_NIGHT -> Text.translatable("rpg.abilitywar.obj.kill_night", target(o.target()));
            case ITEM -> Text.translatable("rpg.abilitywar.obj.item", itemName(o.target()));
            case HEAL -> Text.translatable("rpg.abilitywar.obj.heal");
            case BUFF -> Text.translatable("rpg.abilitywar.obj.buff");
            case TANK -> Text.translatable("rpg.abilitywar.obj.tank");
        };
    }

    static Text target(String t) {
        if (t.startsWith("#")) return Text.translatable("rpg.abilitywar.target." + t.substring(1));
        EntityType<?> et = Registries.ENTITY_TYPE.get(new Identifier(t));
        return et.getName();
    }

    static Text itemName(String id) {
        Item it = Registries.ITEM.get(new Identifier(id));
        return it.getName();
    }

    public static Item item(String id) {
        return Registries.ITEM.get(new Identifier(id));
    }

    public static boolean canPickFreely() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return selfSelect || (mc.player != null && mc.player.hasPermissionLevel(2));
    }

    private RpgClient() {
    }
}
