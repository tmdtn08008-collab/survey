package com.abilitywar.rpg;

import com.abilitywar.AbilityWar;
import com.abilitywar.item.RpgItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 심연의 탑 (후반 컨텐츠): 끝없이 올라가는 층. 레벨 40 부터, 파티 (최대 5명) 로 같이.
 * <ul>
 *   <li>탑지기 (수도 모험가 길드 옆) 에게 말을 걸거나 /aw tower start 로 들어감. 1층 또는 깬 적 있는 10층 단위 다음 층 (11, 21, …) 부터.</li>
 *   <li>층마다 몬스터 무리 — 층이 오를수록 체력 · 공격력이 세짐. 5층마다 보스 (이 모드 보스 + 같이 넣은 다른 모드 보스).</li>
 *   <li>층을 깰 때마다 G · 경험치 · 강화석, 보스 층은 심연의 결정 (각성 재료). 10층 단위 첫 돌파 보너스.</li>
 *   <li>탑 안에서 쓰러지면 아이템은 그대로 (떨어지지 않음), 수도에서 다시 시작. 떨어지면 가운데로 되돌아옴.</li>
 * </ul>
 * 층은 따로 만든 차원 (abilitywar:abyss, 빈 공간) 에 원형 투기장을 짓고 진행한다. 파티마다 자리 하나 (1024칸 간격).
 */
public final class AbyssTower {
    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, AbilityWar.id("abyss"));
    public static final int MIN_LEVEL = 40;
    public static final String MOB_TAG = "aw_tower";
    static final int R = 18, Y = 80, SPACING = 1024, MAX_RUNS = 32;
    static final int PREP = 100, BREAK = 160, GLOW_AT = 20 * 120, FAIL_AT = 20 * 60 * 8;
    static final UUID HP_ID = UUID.nameUUIDFromBytes("abilitywar_tower:hp".getBytes());
    static final UUID DMG_ID = UUID.nameUUIDFromBytes("abilitywar_tower:dmg".getBytes());

    enum State {PREP, FIGHT, BREAK}

    static final class Run {
        final int slot;
        final BlockPos center;
        final Set<UUID> players = new LinkedHashSet<>();
        final Map<UUID, Return> back = new HashMap<>();
        final List<UUID> mobs = new ArrayList<>();
        final ServerBossBar bar = new ServerBossBar(Text.empty(), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
        int floor, startFloor, total, timer, cleared;
        State state = State.PREP;

        Run(int slot, int floor) {
            this.slot = slot;
            this.center = new BlockPos(slot * SPACING + 8, Y, 8);
            this.floor = floor;
            this.startFloor = floor;
        }

        Vec3d spot(int i, int n) {
            double a = Math.PI * 2 * i / Math.max(1, n);
            return Vec3d.ofBottomCenter(center.up()).add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
        }
    }

    record Return(RegistryKey<World> world, Vec3d pos, float yaw) {
    }

    static final List<Run> RUNS = new ArrayList<>();
    static final Map<UUID, Run> BY_PLAYER = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(AbyssTower::tick);
        // 탑 안 블록은 못 부숨 (투기장 보호)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> !(isAbyss(world) && !player.isCreative()));
        // 탑에서 쓰러짐: 아이템 · 경험치를 그대로 (LivingEntityMixin 이 떨어뜨리는 것을 막음)
        ServerPlayerEvents.COPY_FROM.register((oldP, newP, alive) -> {
            if (alive || !isAbyss(oldP.getWorld()) || oldP.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.KEEP_INVENTORY)) return;
            newP.getInventory().clone(oldP.getInventory());
            newP.experienceLevel = oldP.experienceLevel;
            newP.totalExperience = oldP.totalExperience;
            newP.experienceProgress = oldP.experienceProgress;
        });
        // 탑 진행 중이 아닌데 탑 안에서 접속 (서버 재시작 등): 수도로
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.player;
            if (isAbyss(p.getWorld()) && !BY_PLAYER.containsKey(p.getUuid())) sendHome(p, null);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Run r = BY_PLAYER.remove(handler.player.getUuid());
            if (r != null) {
                r.players.remove(handler.player.getUuid());
                r.bar.removePlayer(handler.player);
            }
        });
    }

    public static boolean isAbyss(World w) {
        return w != null && w.getRegistryKey() == WORLD;
    }

    /** 탑에서 죽은 플레이어: 아이템을 떨어뜨리지 않음. */
    public static boolean keepsInventory(LivingEntity e) {
        return e instanceof ServerPlayerEntity && isAbyss(e.getWorld());
    }

    public static boolean inTower(ServerPlayerEntity p) {
        return BY_PLAYER.containsKey(p.getUuid());
    }

    /** 체크포인트: 1, 그리고 깬 10층 단위 다음 층 (최고 23층이면 1 · 11 · 21). */
    public static List<Integer> checkpoints(int best) {
        List<Integer> l = new ArrayList<>();
        for (int f = 1; f <= best + 1; f += 10) l.add(f);
        return l;
    }

    // ================================================================ 시작 · 끝

    /** @return 오류 번역 키 (되면 null). */
    @Nullable
    public static String start(ServerPlayerEntity leader, int startFloor) {
        return start(leader, startFloor, false);
    }

    /** any = 관리자 명령: 아무 층부터. */
    @Nullable
    public static String start(ServerPlayerEntity leader, int startFloor, boolean any) {
        MinecraftServer server = leader.getServer();
        if (server == null) return "tower.abilitywar.err.closed";
        ServerWorld abyss = server.getWorld(WORLD);
        if (abyss == null) return "tower.abilitywar.err.closed";
        if (inTower(leader) || isAbyss(leader.getWorld())) return "tower.abilitywar.err.already";
        if (RpgManager.rpg(leader).level < MIN_LEVEL && !leader.isCreative()) return "tower.abilitywar.err.level";
        int best = RpgManager.rpg(leader).towerBest;
        if (startFloor < 1 || (!any && !checkpoints(best).contains(startFloor))) startFloor = 1;
        if (RUNS.size() >= MAX_RUNS) return "tower.abilitywar.err.full";
        // 같이 들어갈 파티원: 같은 파티 · 32칸 안 · 레벨 40 이상 · 탑에 없음
        List<ServerPlayerEntity> group = new ArrayList<>();
        group.add(leader);
        Team t = com.abilitywar.social.Party.team(leader);
        if (t != null) {
            for (ServerPlayerEntity o : com.abilitywar.social.Party.online(server, t)) {
                if (o == leader || o.getWorld() != leader.getWorld() || o.squaredDistanceTo(leader) > 32 * 32 || inTower(o) || o.isSpectator()) continue;
                if (RpgManager.rpg(o).level < MIN_LEVEL && !o.isCreative()) {
                    o.sendMessage(Text.translatable("tower.abilitywar.err.level_member", MIN_LEVEL).formatted(Formatting.RED), false);
                    continue;
                }
                group.add(o);
            }
        }
        int slot = 0;
        while (true) {
            int s = slot;
            if (RUNS.stream().noneMatch(r -> r.slot == s)) break;
            slot++;
        }
        Run run = new Run(slot, startFloor);
        build(abyss, run.center, true);
        RUNS.add(run);
        int i = 0;
        for (ServerPlayerEntity p : group) {
            run.players.add(p.getUuid());
            run.back.put(p.getUuid(), new Return(p.getWorld().getRegistryKey(), p.getPos(), p.getYaw()));
            BY_PLAYER.put(p.getUuid(), run);
            Vec3d at = run.spot(i++, group.size());
            p.teleport(abyss, at.x, at.y, at.z, p.getYaw(), 0);
            p.setHealth(p.getMaxHealth());
            run.bar.addPlayer(p);
            p.sendMessage(Text.translatable("tower.abilitywar.enter", startFloor).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        }
        abyss.playSound(null, run.center, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.HOSTILE, 0.6f, 0.7f);
        run.state = State.PREP;
        run.timer = 0;
        return null;
    }

    /** 혼자 나가기 (/aw tower leave). */
    public static boolean leave(ServerPlayerEntity p) {
        Run r = BY_PLAYER.get(p.getUuid());
        if (r == null) {
            if (isAbyss(p.getWorld())) sendHome(p, null);
            return false;
        }
        drop(r, p, Text.translatable("tower.abilitywar.left", p.getDisplayName()));
        sendHome(p, r.back.get(p.getUuid()));
        p.sendMessage(Text.translatable("tower.abilitywar.summary", r.cleared, RpgManager.rpg(p).towerBest).formatted(Formatting.GOLD), false);
        return true;
    }

    static void drop(Run r, ServerPlayerEntity p, Text why) {
        r.players.remove(p.getUuid());
        BY_PLAYER.remove(p.getUuid());
        r.bar.removePlayer(p);
        for (ServerPlayerEntity o : players(p.getServer(), r)) o.sendMessage(why.copy().formatted(Formatting.GRAY), false);
    }

    static void sendHome(ServerPlayerEntity p, @Nullable Return back) {
        MinecraftServer s = p.getServer();
        if (s == null) return;
        ServerWorld w = back != null ? s.getWorld(back.world()) : null;
        if (w == null || isAbyss(w)) {
            w = s.getOverworld();
            BlockPos sp = w.getSpawnPos();
            p.teleport(w, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYaw(), 0);
        } else p.teleport(w, back.pos().x, back.pos().y, back.pos().z, back.yaw(), 0);
        p.fallDistance = 0;
    }

    static void end(MinecraftServer server, Run r, String why) {
        ServerWorld abyss = server.getWorld(WORLD);
        for (ServerPlayerEntity p : players(server, r)) {
            BY_PLAYER.remove(p.getUuid());
            p.sendMessage(Text.translatable(why, r.floor).formatted(Formatting.RED), false);
            p.sendMessage(Text.translatable("tower.abilitywar.summary", r.cleared, RpgManager.rpg(p).towerBest).formatted(Formatting.GOLD), false);
            sendHome(p, r.back.get(p.getUuid()));
        }
        for (UUID u : r.players) BY_PLAYER.remove(u);
        r.players.clear();
        r.bar.clearPlayers();
        if (abyss != null) for (UUID id : r.mobs) {
            Entity e = abyss.getEntity(id);
            if (e != null) e.discard();
        }
        r.mobs.clear();
        RUNS.remove(r);
    }

    static List<ServerPlayerEntity> players(@Nullable MinecraftServer s, Run r) {
        List<ServerPlayerEntity> l = new ArrayList<>();
        if (s == null) return l;
        for (UUID u : r.players) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(u);
            if (p != null) l.add(p);
        }
        return l;
    }

    // ================================================================ 진행

    static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) return;
        ServerWorld abyss = server.getWorld(WORLD);
        if (abyss == null) return;
        for (Run r : new ArrayList<>(RUNS)) {
            // 쓰러졌거나 나간 사람 정리
            for (ServerPlayerEntity p : players(server, r)) {
                if (!p.isAlive() || p.getWorld() != abyss) {
                    drop(r, p, Text.translatable("tower.abilitywar.fell", p.getDisplayName()));
                    p.sendMessage(Text.translatable("tower.abilitywar.summary", r.cleared, RpgManager.rpg(p).towerBest).formatted(Formatting.GOLD), false);
                    continue;
                }
                // 떨어지면 가운데로
                if (p.getY() < r.center.getY() - 10) {
                    p.teleport(abyss, r.center.getX() + 0.5, r.center.getY() + 1, r.center.getZ() + 0.5, p.getYaw(), p.getPitch());
                    p.fallDistance = 0;
                    p.damage(abyss.getDamageSources().outOfWorld(), 4f);
                }
            }
            List<UUID> alive = new ArrayList<>(r.players);
            alive.removeIf(u -> server.getPlayerManager().getPlayer(u) == null);
            if (alive.isEmpty()) {
                end(server, r, "tower.abilitywar.wiped");
                continue;
            }
            r.timer++;
            switch (r.state) {
                case PREP -> prep(server, abyss, r);
                case FIGHT -> fight(server, abyss, r);
                case BREAK -> {
                    int left = (BREAK - r.timer) / 20 + 1;
                    r.bar.setName(Text.translatable("tower.abilitywar.bar.break", r.floor + 1, left));
                    r.bar.setPercent(1f - r.timer / (float) BREAK);
                    if (r.timer >= BREAK) {
                        r.floor++;
                        r.state = State.PREP;
                        r.timer = 0;
                    }
                }
            }
        }
    }

    static boolean bossFloor(int f) {
        return f % 5 == 0;
    }

    static void prep(MinecraftServer server, ServerWorld w, Run r) {
        if (r.timer == 1) {
            build(w, r.center, false);
            Text title = Text.translatable("tower.abilitywar.floor", r.floor).formatted(bossFloor(r.floor) ? Formatting.RED : Formatting.LIGHT_PURPLE, Formatting.BOLD);
            Text sub = Text.translatable(bossFloor(r.floor) ? "tower.abilitywar.floor.boss" : "tower.abilitywar.floor.sub", mobCount(r.floor, r.players.size()));
            for (ServerPlayerEntity p : players(server, r)) {
                p.networkHandler.sendPacket(new TitleFadeS2CPacket(8, 50, 14));
                p.networkHandler.sendPacket(new TitleS2CPacket(title));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(sub));
            }
            w.playSound(null, r.center, bossFloor(r.floor) ? SoundEvents.ENTITY_WITHER_SPAWN : SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, bossFloor(r.floor) ? 0.5f : 1.2f, 0.8f);
        }
        r.bar.setName(Text.translatable("tower.abilitywar.bar.prep", r.floor, (PREP - r.timer) / 20 + 1));
        r.bar.setPercent(r.timer / (float) PREP);
        r.bar.setColor(bossFloor(r.floor) ? BossBar.Color.RED : BossBar.Color.PURPLE);
        if (r.timer >= PREP) {
            spawnFloor(w, r);
            r.state = State.FIGHT;
            r.timer = 0;
        }
    }

    static void fight(MinecraftServer server, ServerWorld w, Run r) {
        List<ServerPlayerEntity> ps = players(server, r);
        int left = 0;
        for (UUID id : r.mobs) {
            Entity e = w.getEntity(id);
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            // 투기장 밖으로 떨어지거나 너무 멀리 간 몹: 떨어지면 처치, 멀면 되돌림
            if (le.getY() < r.center.getY() - 16) {
                le.kill();
                continue;
            }
            left++;
            if (le.squaredDistanceTo(Vec3d.ofCenter(r.center)) > 30 * 30 && r.timer % 40 == 0)
                le.refreshPositionAndAngles(r.center.getX() + 0.5, r.center.getY() + 2, r.center.getZ() + 0.5, le.getYaw(), 0);
            if (r.timer == GLOW_AT) le.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60 * 10, 0, false, false));
            if (le instanceof MobEntity m && (m.getTarget() == null || !m.getTarget().isAlive()) && r.timer % 20 == 0 && !ps.isEmpty()) {
                ServerPlayerEntity near = ps.get(0);
                for (ServerPlayerEntity p : ps) if (p.squaredDistanceTo(m) < near.squaredDistanceTo(m)) near = p;
                if (!near.isCreative() && !near.isSpectator()) m.setTarget(near);
            }
        }
        r.bar.setName(Text.translatable(bossFloor(r.floor) ? "tower.abilitywar.bar.boss" : "tower.abilitywar.bar.fight", r.floor, left, r.total));
        r.bar.setPercent(r.total <= 0 ? 0 : left / (float) r.total);
        if (left == 0) {
            clear(server, w, r, ps);
            return;
        }
        if (r.timer >= FAIL_AT) end(server, r, "tower.abilitywar.timeout");
    }

    // ================================================================ 몹

    /** 일반 몹: 층이 오르면 더 센 무리가 섞임. [층 이상부터, 몹 id …] */
    static final String[][] TIERS = {
            {"1", "minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:husk", "abilitywar:goblin", "abilitywar:wisp"},
            {"6", "abilitywar:orc", "abilitywar:skeleton_knight", "minecraft:witch", "minecraft:stray", "minecraft:pillager", "minecraft:cave_spider",
                    "mobs_of_mythology:kobold_warrior", "mobs_of_mythology:sporeling"},
            {"11", "minecraft:wither_skeleton", "minecraft:blaze", "minecraft:vindicator", "minecraft:piglin_brute", "minecraft:enderman",
                    "mobs_of_mythology:chupacabra", "graveyard:revenant", "graveyard:wraith"},
            {"21", "minecraft:evoker", "minecraft:ravager", "mutantmonsters:mutant_zombie", "mutantmonsters:mutant_skeleton", "mobs_of_mythology:automaton",
                    "mutantmonsters:mutant_creeper"}};

    /** 보스 층 보스: 이 모드 보스 + 같이 넣은 다른 모드 보스 (설치된 것만). [층 이상부터, id] */
    static final String[][] BOSSES = {
            {"5", "abilitywar:fallen_king"}, {"5", "abilitywar:frost_witch"}, {"10", "abilitywar:ember_colossus"}, {"10", "abilitywar:ancient_wyvern"},
            {"10", "mutantmonsters:mutant_enderman"}, {"10", "graveyard:lich"}, {"15", "bosses_of_mass_destruction:lich"},
            {"15", "soulsweapons:draugr_boss"}, {"15", "soulsweapons:accursed_lord_boss"}, {"20", "soulsweapons:returning_knight"},
            {"20", "bosses_of_mass_destruction:gauntlet"}, {"20", "archon:tar"}, {"20", "aquamirae:captain_cornelia"}, {"25", "archon:inigo"},
            {"25", "archon:ayla"}, {"25", "adventurez:blackstone_golem"}, {"30", "soulsweapons:moonknight"}, {"30", "soulsweapons:chaos_monarch"},
            {"30", "bosses_of_mass_destruction:void_blossom"}, {"40", "bosses_of_mass_destruction:obsidilith"}, {"40", "archon:leven"},
            {"50", "minecraft:wither"}};

    static int mobCount(int floor, int players) {
        int n = Math.min(4 + floor / 3, 12) + Math.max(0, players - 1) * 2;
        return bossFloor(floor) ? Math.max(2, n / 2) + 1 : n;
    }

    @Nullable
    static EntityType<?> type(String id) {
        Identifier i = Identifier.tryParse(id);
        return i != null && Registries.ENTITY_TYPE.containsId(i) ? Registries.ENTITY_TYPE.get(i) : null;
    }

    static List<EntityType<?>> pool(int floor, boolean top) {
        List<EntityType<?>> l = new ArrayList<>();
        for (int t = TIERS.length - 1; t >= 0; t--) {
            if (floor < Integer.parseInt(TIERS[t][0])) continue;
            for (int k = 1; k < TIERS[t].length; k++) {
                EntityType<?> e = type(TIERS[t][k]);
                if (e != null) l.add(e);
            }
            if (top && !l.isEmpty()) return l;
        }
        return l;
    }

    static void spawnFloor(ServerWorld w, Run r) {
        Random rnd = w.getRandom();
        int f = r.floor;
        int n = mobCount(f, r.players.size());
        r.mobs.clear();
        if (bossFloor(f)) {
            List<EntityType<?>> bs = new ArrayList<>();
            for (String[] b : BOSSES) {
                if (f < Integer.parseInt(b[0])) continue;
                EntityType<?> e = type(b[1]);
                if (e != null) bs.add(e);
            }
            if (!bs.isEmpty()) {
                EntityType<?> boss = bs.get(rnd.nextInt(bs.size()));
                spawn(w, r, boss, true, 0, 1);
                n--;
            }
        }
        List<EntityType<?>> top = pool(f, true), all = pool(f, false);
        int elites = 0;
        for (int i = 0; i < n; i++) {
            List<EntityType<?>> from = rnd.nextInt(100) < 60 ? top : all;
            if (from.isEmpty()) from = all;
            if (from.isEmpty()) break;
            EntityType<?> t = from.get(rnd.nextInt(from.size()));
            // 돌연변이 같은 센 몹은 층마다 조금만
            if (Registries.ENTITY_TYPE.getId(t).getNamespace().equals("mutantmonsters") || t == EntityType.RAVAGER) {
                if (elites >= 1 + f / 20) {
                    t = all.get(rnd.nextInt(all.size()));
                    if (Registries.ENTITY_TYPE.getId(t).getNamespace().equals("mutantmonsters") || t == EntityType.RAVAGER) continue;
                } else elites++;
            }
            spawn(w, r, t, false, i, n);
        }
        r.total = r.mobs.size();
        if (r.total == 0) r.total = 1;
        w.playSound(null, r.center, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    static void spawn(ServerWorld w, Run r, EntityType<?> type, boolean boss, int i, int n) {
        Entity e = type.create(w);
        if (!(e instanceof LivingEntity le)) {
            if (e != null) e.discard();
            return;
        }
        double a = Math.PI * 2 * (i + 0.5) / Math.max(1, n) + w.getRandom().nextDouble() * 0.3;
        double d = boss ? 0 : 11 + w.getRandom().nextDouble() * 4;
        double x = r.center.getX() + 0.5 + Math.cos(a) * d, z = r.center.getZ() + 0.5 + Math.sin(a) * d;
        double y = r.center.getY() + 1 + (e instanceof com.abilitywar.mob.AncientWyvernEntity || type == EntityType.WITHER ? 6 : 0);
        e.refreshPositionAndAngles(x, y, z, w.getRandom().nextFloat() * 360, 0);
        if (e instanceof MobEntity m) {
            m.initialize(w, w.getLocalDifficulty(e.getBlockPos()), SpawnReason.EVENT, null, null);
            m.setPersistent();
        }
        e.addCommandTag(MOB_TAG);
        int f = r.floor;
        double hp = boss ? 0.08 * Math.max(0, f - 5) : 0.12 * (f - 1);
        double dmg = 0.05 * (f - 1);
        mod(le, EntityAttributes.GENERIC_MAX_HEALTH, HP_ID, hp);
        mod(le, EntityAttributes.GENERIC_ATTACK_DAMAGE, DMG_ID, dmg);
        le.setHealth(le.getMaxHealth());
        w.spawnEntity(e);
        w.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1, z, 30, 0.4, 0.8, 0.4, 0.05);
        r.mobs.add(e.getUuid());
    }

    static void mod(LivingEntity e, net.minecraft.entity.attribute.EntityAttribute attr, UUID id, double v) {
        EntityAttributeInstance ai = e.getAttributeInstance(attr);
        if (ai == null || v <= 0) return;
        ai.removeModifier(id);
        ai.addPersistentModifier(new EntityAttributeModifier(id, "abilitywar tower", v, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // ================================================================ 보상

    static void clear(MinecraftServer server, ServerWorld w, Run r, List<ServerPlayerEntity> ps) {
        int f = r.floor;
        boolean boss = bossFloor(f);
        r.cleared = f;
        r.mobs.clear();
        long g = (long) 60 * f * (boss ? 3 : 1);
        int xp = 25 * f * (boss ? 2 : 1);
        for (ServerPlayerEntity p : ps) {
            RpgData d = RpgManager.rpg(p);
            boolean first = f > d.towerBest;
            RpgManager.addGold(p, g);
            RpgManager.addXp(p, xp);
            Shops.give(p, new ItemStack(RpgItems.ENHANCE_STONE, 1 + f / 5));
            int crystals = boss ? 1 + f / 10 : (f >= 10 && p.getRandom().nextInt(100) < 30 ? 1 : 0);
            if (first && f % 10 == 0) {
                crystals += 3 + f / 10;
                RpgManager.addGold(p, 1500L * (f / 10));
                Shops.give(p, new ItemStack(RpgItems.PROTECTION_SCROLL));
                Shops.give(p, new ItemStack(RpgItems.BOSS_SOUL));
                p.sendMessage(Text.translatable("tower.abilitywar.milestone", f).formatted(Formatting.GOLD, Formatting.BOLD), false);
            }
            if (crystals > 0) Shops.give(p, new ItemStack(RpgItems.ABYSS_CRYSTAL, crystals));
            if (first) {
                d.towerBest = f;
                d.dirty = true;
                RpgManager.sync(p);
                if (f % 10 == 0 && server != null)
                    for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList())
                        o.sendMessage(Text.literal("🗼 ").formatted(Formatting.LIGHT_PURPLE).append(Text.translatable("tower.abilitywar.broadcast", p.getDisplayName(), f).formatted(Formatting.LIGHT_PURPLE)), false);
            }
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(4, 30, 10));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("tower.abilitywar.cleared", f).formatted(Formatting.GOLD, Formatting.BOLD)));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("tower.abilitywar.cleared.sub", Shops.fmt(g), crystals)));
            p.heal(p.getMaxHealth() * 0.3f);
        }
        w.playSound(null, r.center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        w.spawnParticles(ParticleTypes.END_ROD, r.center.getX() + 0.5, r.center.getY() + 2, r.center.getZ() + 0.5, 80, 3, 1.5, 3, 0.1);
        r.state = State.BREAK;
        r.timer = 0;
    }

    // ================================================================ 투기장

    /** 원형 투기장 (반지름 18): 바닥 · 테두리 · 기둥 8개 · 보이지 않는 벽. full = 위 공간도 비움 (처음). */
    static void build(ServerWorld w, BlockPos c, boolean full) {
        int cx = c.getX(), cy = c.getY(), cz = c.getZ();
        for (int x = (cx - R - 2) >> 4; x <= (cx + R + 2) >> 4; x++)
            for (int z = (cz - R - 2) >> 4; z <= (cz + R + 2) >> 4; z++) w.getChunk(x, z);
        BlockPos.Mutable m = new BlockPos.Mutable();
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        for (int dx = -R - 2; dx <= R + 2; dx++) {
            for (int dz = -R - 2; dz <= R + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 1.6) continue;
                if (d <= R + 0.5) {
                    set(w, m.set(cx + dx, cy, cz + dz), floor(dx, dz, d), flags);
                    // 아래: 거꾸로 선 뾰족한 바위 (멀리서 보이는 모양)
                    int depth = (int) Math.max(1, (R + 0.5 - d) * 0.6);
                    for (int k = 1; k <= depth; k++) set(w, m.set(cx + dx, cy - k, cz + dz), k == depth ? Blocks.BLACKSTONE.getDefaultState() : Blocks.POLISHED_BLACKSTONE.getDefaultState(), flags);
                    for (int k = 1; k <= (full ? 24 : 6); k++) {
                        if (pillar(dx, dz) && k <= 7) continue;
                        set(w, m.set(cx + dx, cy + k, cz + dz), Blocks.AIR.getDefaultState(), flags);
                    }
                } else {
                    // 보이지 않는 벽
                    for (int k = 0; k <= 4; k++) set(w, m.set(cx + dx, cy + k, cz + dz), Blocks.BARRIER.getDefaultState(), flags);
                }
            }
        }
        // 기둥 8개 (흑요석 + 우는 흑요석 + 엔드 막대 불빛)
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            int px = cx + MathHelper.floor(Math.cos(a) * 15.5 + 0.5), pz = cz + MathHelper.floor(Math.sin(a) * 15.5 + 0.5);
            for (int k = 1; k <= 5; k++) set(w, m.set(px, cy + k, pz), (k == 3 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState(), flags);
            set(w, m.set(px, cy + 6, pz), Blocks.AMETHYST_BLOCK.getDefaultState(), flags);
            set(w, m.set(px, cy + 7, pz), Blocks.END_ROD.getDefaultState(), flags);
        }
    }

    static boolean pillar(int dx, int dz) {
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            if (dx == MathHelper.floor(Math.cos(a) * 15.5 + 0.5) && dz == MathHelper.floor(Math.sin(a) * 15.5 + 0.5)) return true;
        }
        return false;
    }

    static BlockState floor(int dx, int dz, double d) {
        if (d > R - 0.7) return ((dx + dz) & 1) == 0 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.OBSIDIAN.getDefaultState();
        if (d <= 1.6) return Blocks.AMETHYST_BLOCK.getDefaultState();
        if (d <= 3.0) return Blocks.PURPUR_BLOCK.getDefaultState();
        if (d <= 3.7) return Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState();
        if (Math.abs(d - 10.5) < 0.5) {
            double ang = Math.toDegrees(Math.atan2(dz, dx)) + 360;
            return ang % 30 < 6 ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        }
        if (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) return Blocks.DEEPSLATE_TILES.getDefaultState();
        int h = (dx * 73856093) ^ (dz * 19349663);
        return (h & 7) == 0 ? Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState() : (h & 7) == 1 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState()
                : d < 10 ? Blocks.POLISHED_DEEPSLATE.getDefaultState() : Blocks.POLISHED_BLACKSTONE.getDefaultState();
    }

    static void set(ServerWorld w, BlockPos p, BlockState s, int flags) {
        if (w.getBlockState(p) != s) w.setBlockState(p, s, flags);
    }

    // ================================================================ 순위

    /** 접속한 사람 중 최고 층 상위 10명. */
    public static List<Text> top(MinecraftServer s) {
        List<ServerPlayerEntity> ps = new ArrayList<>(s.getPlayerManager().getPlayerList());
        ps.sort((a, b) -> Integer.compare(RpgManager.rpg(b).towerBest, RpgManager.rpg(a).towerBest));
        List<Text> out = new ArrayList<>();
        int i = 0;
        for (ServerPlayerEntity p : ps) {
            if (i >= 10) break;
            int best = RpgManager.rpg(p).towerBest;
            if (best <= 0) continue;
            out.add(Text.literal((++i) + ". ").formatted(Formatting.GRAY).append(p.getDisplayName()).append(Text.literal("  " + best).formatted(Formatting.LIGHT_PURPLE))
                    .append(Text.translatable("tower.abilitywar.floor_unit").formatted(Formatting.LIGHT_PURPLE)));
        }
        return out;
    }

    /** 관리자: 최고 층 바꾸기. */
    public static void setBest(ServerPlayerEntity p, int best) {
        RpgData d = RpgManager.rpg(p);
        d.towerBest = Math.max(0, best);
        d.dirty = true;
        RpgManager.sync(p);
    }

    private AbyssTower() {
    }
}
