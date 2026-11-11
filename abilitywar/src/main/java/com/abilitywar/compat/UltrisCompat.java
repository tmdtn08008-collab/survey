package com.abilitywar.compat;

import com.abilitywar.rpg.RpgManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ultris: Boss Expansion (데이터팩 보스 모드, 따로 받아서 넣음) 호환.
 * <ul>
 *   <li>Ultris 보스는 바닐라 몹 + 꼬리표 (ultris_boss 등). 보통처럼 죽지 않고 연출 뒤 사라지면서 근처 플레이어의 점수판
 *       kills_&lt;보스&gt; 를 1 올림 → 그 점수가 오르면 "보스 처치" (경험치 · 전직 · 왕 · 길드 퀘스트).</li>
 *   <li>Ultris 가 플레이어 근처에 보스를 불러내는 이벤트 (타락한 엔더맨 · 환술사) 는 수도 · 마을 안이면 막음.</li>
 * </ul>
 * 모드가 없으면 점수판이 없어서 아무 일도 안 함.
 */
public final class UltrisCompat {
    /** 점수판 이름 → (보스 이름 번역 키, 경험치). */
    record Boss(String key, int xp) {
    }

    static final Map<String, Boss> BOSSES = Map.of(
            "kills_ender", new Boss("compat.abilitywar.ultris.ender", 450),
            "kills_blazeking", new Boss("compat.abilitywar.ultris.blazeking", 1000),
            "kills_giant", new Boss("compat.abilitywar.ultris.giant", 600),
            "kills_illusioner", new Boss("compat.abilitywar.ultris.illusioner", 500),
            "kills_keeper", new Boss("compat.abilitywar.ultris.keeper", 500),
            "kills_phantom", new Boss("compat.abilitywar.ultris.phantom", 400),
            "kills_shulker", new Boss("compat.abilitywar.ultris.shulker", 600),
            "kills_ultrawith", new Boss("compat.abilitywar.ultris.ultrawither", 800));

    /** Ultris 가 직접 처리하는 보스 몸 (보통 죽음 처리는 건너뜀: 점수판으로 셈). */
    public static final Set<String> BOSS_TAGS = Set.of("ultris_boss", "ultrawither", "enderboss", "blazeboss", "sanctumboss", "giantzombieboss", "phantomleader", "shulkerstone");

    /** 플레이어마다 마지막으로 본 점수. */
    static final Map<UUID, Map<String, Integer>> SEEN = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(UltrisCompat::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> SEEN.clear());
    }

    public static boolean isUltrisBoss(Entity e) {
        for (String t : e.getCommandTags()) if (BOSS_TAGS.contains(t)) return true;
        return false;
    }

    static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 11) return;
        var sb = server.getScoreboard();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Map<String, Integer> seen = SEEN.computeIfAbsent(p.getUuid(), u -> new HashMap<>());
            for (var en : BOSSES.entrySet()) {
                ScoreboardObjective obj = sb.getNullableObjective(en.getKey());
                if (obj == null) continue;
                String name = p.getEntityName();
                int now = sb.playerHasObjective(name, obj) ? sb.getPlayerScore(name, obj).getScore() : 0;
                Integer before = seen.put(en.getKey(), now);
                // 처음 보는 값 (접속 · 서버 시작) 은 기준으로만 씀
                if (before == null || now <= before) continue;
                int n = Math.min(now - before, 5);
                for (int i = 0; i < n; i++) {
                    RpgManager.creditBoss(p, en.getValue().xp());
                    p.sendMessage(Text.translatable("rpg.abilitywar.boss_down", Text.translatable(en.getValue().key())).formatted(Formatting.GOLD), false);
                }
            }
        }
        SEEN.keySet().removeIf(u -> server.getPlayerManager().getPlayer(u) == null);
    }

    /**
     * Ultris 의 "플레이어 근처에 보스 불러내기" 이벤트 중인지: 그 이벤트는 플레이어에게 ultris_player_spawn_* 꼬리표를 붙인 채
     * 보스 (꼬리표 creatures_nonvariant) 를 소환함. 수도 · 마을 안이면 그 소환을 막음 (ServerWorldMixin).
     */
    public static boolean blockSpawn(ServerWorld w, Entity e) {
        if (!e.getCommandTags().contains("creatures_nonvariant")) return false;
        var near = w.getEntitiesByClass(Entity.class, new Box(e.getBlockPos()).expand(48), o -> {
            for (String t : o.getCommandTags()) if (t.startsWith("ultris_player_spawn_")) return true;
            return false;
        });
        if (near.isEmpty()) return false;
        Entity who = near.get(0);
        BlockPos ground = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, who.getBlockPos());
        return com.abilitywar.world.RpgWorld.noPatrols(w, ground);
    }

    private UltrisCompat() {
    }
}
