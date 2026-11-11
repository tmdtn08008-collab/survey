package com.abilitywar.world;

import com.abilitywar.AbilityWar;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * RPG 월드 공통: 수도 위치 (서버가 켜질 때 계산), 처음 한 번은 월드 스폰을 수도 광장으로 옮김.
 * 수도 안 = 안전 지대 (야생 보스가 안 나옴, 몬스터도 안 생김 — 구조물 spawn_overrides).
 */
public final class RpgWorld {
    /** 수도 중심 (월드마다). null 이면 아직 모름. */
    public static BlockPos capital;
    public static final int SAFE_RADIUS = 220;

    public static boolean inSafeZone(ServerWorld w, BlockPos pos) {
        return capital != null && w.getRegistryKey() == World.OVERWORLD && capital.getSquaredDistance(pos.getX(), capital.getY(), pos.getZ()) < SAFE_RADIUS * SAFE_RADIUS;
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(RpgWorld::onStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            capital = null;
            REGION.clear();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(RpgWorld::tickRegions);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
            if (!(e instanceof net.minecraft.entity.passive.VillagerEntity v)) return;
            for (String t : v.getCommandTags()) {
                if (!t.startsWith(PROF_TAG)) continue;
                var prof = net.minecraft.registry.Registries.VILLAGER_PROFESSION.get(new net.minecraft.util.Identifier(t.substring(PROF_TAG.length())));
                v.removeScoreboardTag(t);
                // 레벨 2 + 경험치: 일터가 없어도 직업을 잃지 않음
                v.setVillagerData(v.getVillagerData().withProfession(prof).withLevel(2));
                v.setExperience(10);
                break;
            }
        });
    }

    public static final String PROF_TAG = "abilitywar_prof=";

    // ================================================================ 지역 이름 (들어갈 때 화면 가운데 제목)
    static final java.util.Map<java.util.UUID, String> REGION = new java.util.HashMap<>();
    static final int TOWN_NAMES = 16;

    /** 이 위치의 RPG 구조물 (없으면 null). 겹치면 왕령보다 수도·마을·던전이 먼저. */
    public static net.minecraft.structure.StructureStart structureAt(ServerWorld w, BlockPos pos) {
        var acc = w.getStructureAccessor();
        net.minecraft.structure.StructureStart best = null;
        int bestPri = -1;
        for (var st : acc.getStructureReferences(pos).keySet()) {
            if (!(st instanceof RpgStructures.RpgStructure rs)) continue;
            var start = acc.getStructureAt(pos, st);
            if (start == null || !start.hasChildren()) continue;
            int pri = rs.kind == RpgStructures.Kind.CAPITAL_LANDS ? 0 : 1;
            if (pri > bestPri) {
                best = start;
                bestPri = pri;
            }
        }
        return best;
    }

    public static RpgStructures.Kind kindAt(ServerWorld w, BlockPos pos) {
        var start = structureAt(w, pos);
        return start == null ? null : ((RpgStructures.RpgStructure) start.getStructure()).kind;
    }

    static void tickRegions(MinecraftServer server) {
        if (server.getTicks() % 20 != 7) return;
        for (var p : server.getPlayerManager().getPlayerList()) {
            ServerWorld w = p.getServerWorld();
            String key = "";
            net.minecraft.text.Text title = null, sub = null;
            var start = w.getRegistryKey() == World.OVERWORLD ? structureAt(w, p.getBlockPos()) : null;
            if (start != null) {
                var kind = ((RpgStructures.RpgStructure) start.getStructure()).kind;
                String k = kind.name().toLowerCase();
                key = k + "@" + start.getPos().toLong();
                if (kind == RpgStructures.Kind.TOWN) {
                    int n = Math.floorMod((int) (start.getPos().toLong() * 0x9E3779B97F4A7C15L >>> 40), TOWN_NAMES);
                    title = net.minecraft.text.Text.translatable("region.abilitywar.town", net.minecraft.text.Text.translatable("region.abilitywar.town_name." + n));
                } else title = net.minecraft.text.Text.translatable("region.abilitywar." + k);
                sub = net.minecraft.text.Text.translatable("region.abilitywar." + k + ".sub");
            }
            String prev = REGION.put(p.getUuid(), key);
            if (prev == null || prev.equals(key) || title == null) continue;
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, 50, 20));
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(sub.copy().formatted(net.minecraft.util.Formatting.GRAY)));
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(net.minecraft.text.Text.literal("◆ ").append(title).append(" ◆").formatted(net.minecraft.util.Formatting.GOLD)));
            p.playSound(net.minecraft.sound.SoundEvents.BLOCK_BELL_RESONATE, net.minecraft.sound.SoundCategory.AMBIENT, 0.5f, 1.2f);
        }
    }

    static void onStart(MinecraftServer server) {
        ServerWorld ow = server.getOverworld();
        try {
            ChunkPos c = RpgStructures.CapitalPlacement.locate(ow.getChunkManager().getStructurePlacementCalculator());
            capital = new BlockPos(c.getStartX() + 8, 64, c.getStartZ() + 8);
            State st = ow.getPersistentStateManager().getOrCreate(State::read, State::new, "abilitywar_capital");
            if (!st.spawnSet) {
                // 수도가 있는 청크를 만들고 광장 (분수 남쪽) 을 스폰으로
                var chunk = ow.getChunk(c.x, c.z);
                boolean has = chunk.getStructureStarts().keySet().stream().anyMatch(s -> s instanceof RpgStructures.RpgStructure rs && rs.kind == RpgStructures.Kind.CAPITAL);
                if (!has) {
                    // 이 모드 전에 만든 월드: 그 자리는 이미 생성됨 → 수도 없음
                    AbilityWar.LOG.info("[rpg] no capital in this world (chunk {} was generated before the mod); spawn unchanged", c);
                    capital = null;
                    st.spawnSet = true;
                    st.noCapital = true;
                    st.markDirty();
                    return;
                }
                int sx = capital.getX(), sz = capital.getZ() + CapitalPlan.SPAWN_DZ;
                ow.getChunk(sx >> 4, sz >> 4);
                int y = ow.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                ow.setSpawnPos(new BlockPos(sx, y, sz), 0);
                ow.getGameRules().get(GameRules.SPAWN_RADIUS).set(1, server);
                st.spawnSet = true;
                st.markDirty();
                AbilityWar.LOG.info("[rpg] capital at {}, world spawn moved to {} {} {}", capital, sx, y, sz);
            }
            if (st.noCapital) {
                capital = null;
                return;
            }
            capital = new BlockPos(capital.getX(), ow.getSpawnPos().getY(), capital.getZ());
        } catch (Exception e) {
            AbilityWar.LOG.error("[rpg] could not place the capital spawn", e);
        }
    }

    static final class State extends PersistentState {
        boolean spawnSet, noCapital;

        static State read(NbtCompound n) {
            State s = new State();
            s.spawnSet = n.getBoolean("SpawnSet");
            s.noCapital = n.getBoolean("NoCapital");
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n) {
            n.putBoolean("SpawnSet", spawnSet);
            n.putBoolean("NoCapital", noCapital);
            return n;
        }
    }

    private RpgWorld() {
    }
}
