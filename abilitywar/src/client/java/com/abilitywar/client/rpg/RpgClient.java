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
    /** 3차 전직 ("대") 을 했는지 (2차 직업일 때 효과). */
    public static boolean grand;
    public static int grandLevel = 60;
    /** 모험가 길드 (-1 = 가입 안 함). */
    public static int guildRank = -1, guildPoints, guildDone;
    public static String guildQuest = "";
    public static final int[] guildProgress = new int[4];
    public static int guildProgressN;

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
        grand = buf.isReadable() && buf.readBoolean();
        if (buf.isReadable()) grandLevel = buf.readVarInt();
        if (buf.isReadable()) {
            guildRank = buf.readVarInt();
            guildPoints = buf.readVarInt();
            guildDone = buf.readVarInt();
            guildQuest = buf.readString();
            guildProgressN = buf.readVarInt();
            for (int i = 0; i < guildProgressN; i++) {
                int v = buf.readVarInt();
                if (i < guildProgress.length) guildProgress[i] = v;
            }
        }
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
        grand = false;
        guildRank = -1;
        guildPoints = guildDone = guildProgressN = 0;
        guildQuest = "";
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
        String cur = ClientState.ability;
        if (JobQuests.finished(q, cur, grand, completed)) return Status.COMPLETED;
        if (q.id().equals(quest)) return done(q) ? Status.READY : Status.ACTIVE;
        String err = JobQuests.check(q, cur, baseClass, level, grand, quest, jobLevel, grandLevel);
        if (err == null) return Status.AVAILABLE;
        return switch (err) {
            case "rpg.abilitywar.err.wrong_class" -> Status.WRONG_CLASS;
            case "rpg.abilitywar.err.already_advanced", "rpg.abilitywar.err.already_grand" -> Status.ADVANCED;
            case "rpg.abilitywar.err.has_quest" -> Status.OTHER_ACTIVE;
            default -> Status.LOCKED_LEVEL;
        };
    }

    public static int requiredLevel(JobQuests.Quest q) {
        return JobQuests.requiredLevel(q, jobLevel, grandLevel);
    }

    /** 3차 전직 효과가 켜져 있는지 (지금 2차 직업). */
    public static boolean grandActive() {
        return grand && ClassTree.tier(ClientState.ability) == 2;
    }

    /** 직업 이름 (3차면 "대"). */
    public static MutableText className(String key, boolean grandName) {
        if (key == null || key.isEmpty()) return Text.translatable("rpg.abilitywar.hud.no_class");
        return grandName && ClassTree.tier(key) == 2 ? Text.translatable("ability.abilitywar." + key + ".grand") : Text.translatable("ability.abilitywar." + key);
    }

    /** 이 NPC 가 보여줄 퀘스트 (다른 직업 것은 뺌, 진행 중인 것 먼저). */
    public static java.util.List<JobQuests.Quest> questsFor(String giver) {
        java.util.List<JobQuests.Quest> out = new java.util.ArrayList<>();
        String base = baseClass.isEmpty() ? ClassTree.baseOf(ClientState.ability) : baseClass;
        for (JobQuests.Quest q : JobQuests.forGiver(giver, ClientState.ability, base, quest))
            if (q.id().equals(quest) || status(q) != Status.WRONG_CLASS || q.kind() == JobQuests.Kind.JOB) out.add(q);
        return out;
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

    // ================================================================ 모험가 길드
    public static com.abilitywar.rpg.Guild.GQuest guildActive() {
        return guildQuest.isEmpty() ? null : com.abilitywar.rpg.Guild.ALL.get(guildQuest);
    }

    public static int guildCount(int i) {
        return i < guildProgressN ? guildProgress[i] : 0;
    }

    public static boolean guildDone(com.abilitywar.rpg.Guild.GQuest q) {
        if (!q.id().equals(guildQuest)) return false;
        for (int i = 0; i < q.objectives().size(); i++) if (guildCount(i) < q.objectives().get(i).count()) return false;
        return true;
    }

    public static MutableText guildQuestName(com.abilitywar.rpg.Guild.GQuest q) {
        return Text.translatable("guild.abilitywar.quest." + q.id());
    }

    /** NPC 머리 위 표시: 0 없음, 1 = 받을 수 있음(!), 2 = 진행 중(?), 3 = 완료 가능(?). */
    public static int marker(String giver) {
        if (!synced) return 0;
        if (com.abilitywar.rpg.Guild.GIVER.equals(giver)) {
            if (guildRank < 0) return 1;
            com.abilitywar.rpg.Guild.GQuest g = guildActive();
            if (g != null) return guildDone(g) ? 3 : 2;
            return com.abilitywar.rpg.Guild.checkPromote(guildRank, guildPoints, level) == null ? 3 : 1;
        }
        JobQuests.Quest a = active();
        if (a != null) return a.giver().equals(giver) ? (done(a) ? 3 : 2) : 0;
        for (JobQuests.Quest q : questsFor(giver)) if (status(q) == Status.AVAILABLE) return 1;
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
            case BOSS -> Text.translatable("rpg.abilitywar.obj.boss");
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
