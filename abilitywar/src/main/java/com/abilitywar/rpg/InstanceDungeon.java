package com.abilitywar.rpg;

import com.abilitywar.AbilityWar;
import com.abilitywar.gear.GearGrade;
import com.abilitywar.gear.GearItems;
import com.abilitywar.item.RpgItems;
import com.abilitywar.net.AWNet;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
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
 * 인스턴스 던전 (2.10): 던전 안내인에게 테마 · 난이도를 골라 파티로 들어감. 들어갈 때마다 방 배치가 새로 만들어짐.
 * <pre>
 *  시작 방 → 전투 방 (문이 잠기고 적을 다 잡아야 열림) · 함정 방 (바닥에서 가시가 솟음) → 중간 보스 → 전투 · 함정 → 보스 방
 *  옆길: 보물 방 (상자). 보스를 잡으면 보상 + 30초 뒤 돌아감 (/aw dungeon leave 로 바로).
 * </pre>
 * 테마 5 (고블린 굴 · 해적 동굴 · 광신도 묘실 · 골렘 유적 · 용의 심연) × 난이도 3 (보통 · 어려움 · 지옥).
 * 따로 만든 빈 차원 (abilitywar:dungeon) 에서 진행, 안에서 쓰러져도 아이템은 그대로 (수도에서 다시 시작).
 */
public final class InstanceDungeon {
    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, AbilityWar.id("dungeon"));
    public static final String MOB_TAG = "aw_dungeon";
    static final int Y = 64, SPACING = 512, MAX_RUNS = 24, CELL = 26, HALF = 7, H = 6;
    static final int TIME_LIMIT = 20 * 60 * 20, END_WAIT = 20 * 30;
    static final UUID HP_ID = UUID.nameUUIDFromBytes("abilitywar_dungeon:hp".getBytes());
    static final UUID DMG_ID = UUID.nameUUIDFromBytes("abilitywar_dungeon:dmg".getBytes());
    static final UUID KB_ID = UUID.nameUUIDFromBytes("abilitywar_dungeon:kb".getBytes());

    // ================================================================ 테마 · 난이도

    /** 벽 · 벽2 · 바닥 · 바닥2 · 천장 · 불빛 · 기둥 · 문 (닫힐 때) · 장식. */
    record Palette(Block wall, Block wall2, Block floor, Block floor2, Block ceil, Block light, Block pillar, Block door, Block[] props) {
    }

    public enum Theme {
        GOBLIN("goblin_warren", 10, 2_500, 300, 0x7AA040,
                new String[]{"abilitywar:goblin", "abilitywar:goblin", "abilitywar:dire_wolf", "abilitywar:thief", "abilitywar:bandit"},
                new String[]{"abilitywar:goblin_lord"}, new String[]{"abilitywar:orc_lord"},
                new Palette(Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.COARSE_DIRT, Blocks.PACKED_MUD, Blocks.ROOTED_DIRT, Blocks.SHROOMLIGHT,
                        Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_FENCE, new Block[]{Blocks.HAY_BLOCK, Blocks.BARREL, Blocks.BONE_BLOCK, Blocks.MOSS_BLOCK})),
        PIRATE("pirate_grotto", 25, 6_000, 1_000, 0x40B0D0,
                new String[]{"abilitywar:pirate", "abilitywar:pirate", "abilitywar:bandit", "abilitywar:mermaid", "minecraft:drowned"},
                new String[]{"abilitywar:pirate"}, new String[]{"aquamirae:captain_cornelia", "abilitywar:pirate"},
                new Palette(Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.CUT_SANDSTONE, Blocks.SEA_LANTERN,
                        Blocks.DARK_OAK_LOG, Blocks.IRON_BARS, new Block[]{Blocks.BARREL, Blocks.SPRUCE_PLANKS, Blocks.RAW_GOLD_BLOCK, Blocks.HAY_BLOCK})),
        CULT("cult_crypt", 40, 11_000, 2_500, 0x9A3CFF,
                new String[]{"abilitywar:cultist", "abilitywar:cultist", "abilitywar:wraith", "abilitywar:skeleton_knight", "minecraft:wither_skeleton"},
                new String[]{"illagerinvasion:necromancer", "abilitywar:cultist"}, new String[]{"abilitywar:fallen_king"},
                new Palette(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE, Blocks.DEEPSLATE_BRICKS,
                        Blocks.PEARLESCENT_FROGLIGHT, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.IRON_BARS,
                        new Block[]{Blocks.BONE_BLOCK, Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.CRYING_OBSIDIAN})),
        GOLEM("golem_ruins", 55, 18_000, 5_000, 0xFFB040,
                new String[]{"abilitywar:ancient_golem", "abilitywar:skeleton_knight", "abilitywar:skeleton_knight", "abilitywar:wisp", "abilitywar:fairy"},
                new String[]{"abilitywar:enhanced_golem"}, new String[]{"abilitywar:ember_colossus"},
                new Palette(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS,
                        Blocks.GLOWSTONE, Blocks.QUARTZ_PILLAR, Blocks.IRON_BARS, new Block[]{Blocks.MOSSY_COBBLESTONE, Blocks.GOLD_BLOCK, Blocks.CRACKED_STONE_BRICKS})),
        DRAGON("dragon_abyss", 70, 30_000, 10_000, 0x5AC8FF,
                new String[]{"abilitywar:wraith", "minecraft:blaze", "minecraft:wither_skeleton", "abilitywar:cultist", "abilitywar:ancient_golem"},
                new String[]{"abilitywar:ancient_wyvern"}, new String[]{"abilitywar:dragon_king"},
                new Palette(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.NETHER_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.BLACKSTONE,
                        Blocks.OCHRE_FROGLIGHT, Blocks.POLISHED_BASALT, Blocks.IRON_BARS, new Block[]{Blocks.GOLD_BLOCK, Blocks.BONE_BLOCK, Blocks.OBSIDIAN})),
        // 2.11: 슬라임 숲 · 거미 숲의 던전 (순서 = 던전 입구 블록의 번호라 뒤에 붙임)
        SLIME("slime_hollow", 18, 4_000, 600, 0x7CE05A,
                new String[]{"minecraft:slime", "minecraft:slime", "minecraft:slime", "abilitywar:goblin", "minecraft:witch"},
                new String[]{"minecraft:slime"}, new String[]{"minecraft:slime"},
                new Palette(Blocks.MOSSY_COBBLESTONE, Blocks.MUD_BRICKS, Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.PACKED_MUD, Blocks.VERDANT_FROGLIGHT,
                        Blocks.MANGROVE_LOG, Blocks.MANGROVE_FENCE, new Block[]{Blocks.SLIME_BLOCK, Blocks.MOSS_BLOCK, Blocks.CLAY, Blocks.BROWN_MUSHROOM_BLOCK})),
        SPIDER("spider_nest", 32, 8_000, 1_500, 0xC8C8D8,
                new String[]{"minecraft:spider", "minecraft:cave_spider", "minecraft:spider", "minecraft:skeleton", "abilitywar:thief"},
                new String[]{"minecraft:spider"}, new String[]{"abilitywar:spider_queen"},
                new Palette(Blocks.COBBLED_DEEPSLATE, Blocks.TUFF, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.DEEPSLATE, Blocks.OCHRE_FROGLIGHT,
                        Blocks.STRIPPED_DARK_OAK_LOG, Blocks.IRON_BARS, new Block[]{Blocks.BONE_BLOCK, Blocks.COBWEB, Blocks.WHITE_WOOL, Blocks.MUSHROOM_STEM}));

        public final String key;
        public final int level, gold, fee, color;
        final String[] mobs, mid, boss;
        final Palette pal;

        Theme(String key, int level, int gold, int fee, int color, String[] mobs, String[] mid, String[] boss, Palette pal) {
            this.key = key;
            this.level = level;
            this.gold = gold;
            this.fee = fee;
            this.color = color;
            this.mobs = mobs;
            this.mid = mid;
            this.boss = boss;
            this.pal = pal;
        }

        /** 중간 보스 방 · 보스 방 반지름 · 높이 (용의 심연은 날아다니는 용이라 큼). */
        int midHalf() {
            return this == DRAGON ? 13 : 10;
        }

        int midH() {
            return this == DRAGON ? 16 : 9;
        }

        int bossHalf() {
            return this == DRAGON ? 21 : 13;
        }

        int bossH() {
            return this == DRAGON ? 26 : 12;
        }

        /** 보상 재료 (테마마다). */
        Item[] mats() {
            return switch (this) {
                case GOBLIN -> new Item[]{RpgItems.GOBLIN_EAR, RpgItems.DIRE_FANG, RpgItems.BANDIT_SEAL, RpgItems.LORD_EMBLEM};
                case PIRATE -> new Item[]{RpgItems.PIRATE_DOUBLOON, RpgItems.MERMAID_TEAR, RpgItems.BANDIT_SEAL};
                case CULT -> new Item[]{RpgItems.CULT_SIGIL, RpgItems.WRAITH_ESSENCE, RpgItems.KNIGHT_CREST, RpgItems.CROWN_SHARD};
                case GOLEM -> new Item[]{RpgItems.GOLEM_CORE, RpgItems.FAIRY_DUST, RpgItems.EMBER_CORE};
                case DRAGON -> new Item[]{RpgItems.DRAKE_SCALE, RpgItems.WYVERN_SCALE, RpgItems.DRAGON_KING_SCALE};
                case SLIME -> new Item[]{net.minecraft.item.Items.SLIME_BALL, RpgItems.FAIRY_DUST, RpgItems.GOBLIN_EAR, RpgItems.DIRE_FANG};
                case SPIDER -> new Item[]{net.minecraft.item.Items.SPIDER_EYE, RpgItems.BANDIT_SEAL, RpgItems.WRAITH_ESSENCE, RpgItems.DIRE_FANG};
            };
        }

        /** 보스 보상 장비 등급 (난이도별). */
        GearGrade grade(Diff d, Random r) {
            GearGrade[] g = switch (this) {
                case GOBLIN -> new GearGrade[]{GearGrade.RARE, GearGrade.HERO, GearGrade.LEGENDARY};
                case PIRATE -> new GearGrade[]{GearGrade.HERO, GearGrade.LEGENDARY, GearGrade.MYTHIC};
                case CULT -> new GearGrade[]{GearGrade.LEGENDARY, GearGrade.MYTHIC, GearGrade.SECRET};
                case GOLEM -> new GearGrade[]{GearGrade.MYTHIC, GearGrade.SECRET, GearGrade.SECRET};
                case DRAGON -> new GearGrade[]{GearGrade.SECRET, GearGrade.SECRET, r.nextInt(100) < 30 ? GearGrade.UNKNOWN : GearGrade.SECRET};
                case SLIME -> new GearGrade[]{GearGrade.RARE, GearGrade.HERO, GearGrade.LEGENDARY};
                case SPIDER -> new GearGrade[]{GearGrade.HERO, GearGrade.LEGENDARY, GearGrade.MYTHIC};
            };
            return g[d.ordinal()];
        }

        /** 난이도 단계 (몹 수 · 함정 피해): 필요 레벨로 셈 (고블린 0 … 용 4). */
        int tier() {
            return Math.max(0, (level - 10) / 15);
        }

        /** 화면에 보일 순서 (필요 레벨 순). */
        public static List<Theme> byLevel() {
            List<Theme> l = new ArrayList<>(List.of(values()));
            l.sort(java.util.Comparator.comparingInt(t -> t.level));
            return l;
        }

        public static Theme byKey(String k) {
            for (Theme t : values()) if (t.key.equals(k) || t.name().equalsIgnoreCase(k)) return t;
            return null;
        }
    }

    public enum Diff {
        NORMAL("normal", 0, 1.0, 1.0, 1, 1, Formatting.GREEN),
        HARD("hard", 10, 2.5, 1.5, 2, 2, Formatting.GOLD),
        HELL("hell", 20, 6.0, 2.2, 5, 4, Formatting.DARK_RED);

        public final String key;
        public final int levelAdd, reward, fee;
        final double hp, dmg;
        public final Formatting color;

        Diff(String key, int levelAdd, double hp, double dmg, int reward, int fee, Formatting color) {
            this.key = key;
            this.levelAdd = levelAdd;
            this.hp = hp;
            this.dmg = dmg;
            this.reward = reward;
            this.fee = fee;
            this.color = color;
        }

        public static Diff byKey(String k) {
            for (Diff d : values()) if (d.key.equals(k) || d.name().equalsIgnoreCase(k)) return d;
            return NORMAL;
        }
    }

    public static int minLevel(Theme t, Diff d) {
        return t.level + d.levelAdd;
    }

    public static long fee(Theme t, Diff d) {
        return (long) t.fee * d.fee;
    }

    // ================================================================ 방 · 진행 상태

    enum Kind {START, COMBAT, TRAP, TREASURE, MID, BOSS}

    static final class Room {
        final Kind kind;
        final BlockPos c;
        final int half, h;
        final List<Direction> doors = new ArrayList<>();
        final List<UUID> mobs = new ArrayList<>();
        /** 함정: 솟을 자리 {x, y, z, 솟는 시각, 피해}. */
        final List<double[]> pending = new ArrayList<>();
        boolean active, cleared, visited;
        int wave, waves = 1, total, timer;
        @Nullable
        UUID boss;

        Room(Kind kind, BlockPos c, int half, int h) {
            this.kind = kind;
            this.c = c;
            this.half = half;
            this.h = h;
            this.cleared = kind == Kind.START || kind == Kind.TREASURE || kind == Kind.TRAP;
        }

        boolean inside(Vec3d p, double shrink) {
            return Math.abs(p.x - (c.getX() + 0.5)) < half - shrink && Math.abs(p.z - (c.getZ() + 0.5)) < half - shrink
                    && p.y >= c.getY() - 1 && p.y < c.getY() + h + 2;
        }

        Box box(int pad) {
            return new Box(c.getX() - half - pad, c.getY() - 1, c.getZ() - half - pad, c.getX() + half + 1 + pad, c.getY() + h + 2, c.getZ() + half + 1 + pad);
        }

        boolean fights() {
            return kind == Kind.COMBAT || kind == Kind.MID || kind == Kind.BOSS;
        }
    }

    record Link(Room a, Room b, Direction dir) {
    }

    record Return(RegistryKey<World> world, Vec3d pos, float yaw) {
    }

    enum State {RUN, DONE}

    static final class Run {
        final int slot;
        final Theme theme;
        final Diff diff;
        final List<Room> rooms = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        final Set<UUID> players = new LinkedHashSet<>();
        final Map<UUID, Return> back = new HashMap<>();
        final ServerBossBar bar = new ServerBossBar(Text.empty(), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
        State state = State.RUN;
        int timer, endTimer, partySize;

        Run(int slot, Theme theme, Diff diff) {
            this.slot = slot;
            this.theme = theme;
            this.diff = diff;
        }

        Room start() {
            return rooms.get(0);
        }

        int fightsTotal() {
            return (int) rooms.stream().filter(Room::fights).count();
        }

        int fightsCleared() {
            return (int) rooms.stream().filter(r -> r.fights() && r.cleared).count();
        }
    }

    static final List<Run> RUNS = new ArrayList<>();
    static final Map<UUID, Run> BY_PLAYER = new HashMap<>();
    static int nextSlot;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(InstanceDungeon::tick);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> !(isDungeon(world) && !player.isCreative()));
        ServerPlayerEvents.COPY_FROM.register((oldP, newP, alive) -> {
            if (alive || !isDungeon(oldP.getWorld()) || oldP.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.KEEP_INVENTORY)) return;
            newP.getInventory().clone(oldP.getInventory());
            newP.experienceLevel = oldP.experienceLevel;
            newP.totalExperience = oldP.totalExperience;
            newP.experienceProgress = oldP.experienceProgress;
        });
        // 슬라임이 쪼개져 나온 작은 슬라임도 그 방의 적 (다 잡아야 방이 열림)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
            if (!isDungeon(w) || !(e instanceof net.minecraft.entity.mob.SlimeEntity) || e.getCommandTags().contains(MOB_TAG)) return;
            for (Run r : RUNS)
                for (Room rm : r.rooms)
                    if (rm.active && rm.inside(e.getPos(), 1)) {
                        e.addCommandTag(MOB_TAG);
                        rm.mobs.add(e.getUuid());
                        return;
                    }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.player;
            if (isDungeon(p.getWorld()) && !BY_PLAYER.containsKey(p.getUuid())) sendHome(p, null);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Run r = BY_PLAYER.remove(handler.player.getUuid());
            if (r != null) {
                r.players.remove(handler.player.getUuid());
                r.bar.removePlayer(handler.player);
            }
        });
    }

    public static boolean isDungeon(World w) {
        return w != null && w.getRegistryKey() == WORLD;
    }

    public static boolean keepsInventory(LivingEntity e) {
        return e instanceof ServerPlayerEntity && isDungeon(e.getWorld());
    }

    public static boolean inDungeon(ServerPlayerEntity p) {
        return BY_PLAYER.containsKey(p.getUuid());
    }

    // ================================================================ 시작 · 끝

    /** @return 오류 번역 키 (되면 null). 오류 글자의 인자: 필요한 레벨 · 입장료. */
    @Nullable
    public static String start(ServerPlayerEntity leader, Theme theme, Diff diff) {
        MinecraftServer server = leader.getServer();
        if (server == null) return "dungeon.abilitywar.err.closed";
        ServerWorld w = server.getWorld(WORLD);
        if (w == null) return "dungeon.abilitywar.err.closed";
        if (inDungeon(leader) || isDungeon(leader.getWorld()) || AbyssTower.inTower(leader) || AbyssTower.isAbyss(leader.getWorld()))
            return "dungeon.abilitywar.err.already";
        int need = minLevel(theme, diff);
        long fee = fee(theme, diff);
        if (RpgManager.rpg(leader).level < need && !leader.isCreative()) return "dungeon.abilitywar.err.level";
        if (RpgManager.gold(leader) < fee && !leader.isCreative()) return "dungeon.abilitywar.err.fee";
        if (RUNS.size() >= MAX_RUNS) return "dungeon.abilitywar.err.full";
        List<ServerPlayerEntity> group = new ArrayList<>();
        group.add(leader);
        Team t = com.abilitywar.social.Party.team(leader);
        if (t != null) {
            for (ServerPlayerEntity o : com.abilitywar.social.Party.online(server, t)) {
                if (o == leader || o.getWorld() != leader.getWorld() || o.squaredDistanceTo(leader) > 32 * 32 || inDungeon(o) || AbyssTower.inTower(o) || o.isSpectator())
                    continue;
                if (RpgManager.rpg(o).level < need && !o.isCreative()) {
                    o.sendMessage(Text.translatable("dungeon.abilitywar.err.level_member", need).formatted(Formatting.RED), false);
                    continue;
                }
                if (RpgManager.gold(o) < fee && !o.isCreative()) {
                    o.sendMessage(Text.translatable("dungeon.abilitywar.err.fee_member", Shops.fmt(fee)).formatted(Formatting.RED), false);
                    continue;
                }
                group.add(o);
            }
        }
        // 빈 자리 (월드 시간으로 매번 다른 곳, 같은 자리에 겹치지 않게)
        int slot = (int) ((server.getOverworld().getTime() / 20 + nextSlot++) % 40000);
        for (Run r : RUNS) if (r.slot == slot) slot = (slot + 1) % 40000;
        Run run = new Run(slot, theme, diff);
        run.partySize = group.size();
        BlockPos origin = new BlockPos(slot * SPACING + 64, Y, 64);
        layout(run, origin, w.getRandom());
        build(w, run);
        RUNS.add(run);
        Room s = run.start();
        int i = 0;
        for (ServerPlayerEntity p : group) {
            if (!p.isCreative()) RpgManager.takeGold(p, fee);
            run.players.add(p.getUuid());
            run.back.put(p.getUuid(), new Return(p.getWorld().getRegistryKey(), p.getPos(), p.getYaw()));
            BY_PLAYER.put(p.getUuid(), run);
            double a = Math.PI * 2 * i++ / group.size();
            p.teleport(w, s.c.getX() + 0.5 + Math.cos(a) * 2, s.c.getY() + 1, s.c.getZ() + 0.5 + Math.sin(a) * 2, p.getYaw(), 0);
            p.setHealth(p.getMaxHealth());
            run.bar.addPlayer(p);
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("dungeon.abilitywar.name." + theme.key).styled(st -> st.withColor(theme.color).withBold(true))));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("dungeon.abilitywar.diff." + diff.key).formatted(diff.color)
                    .append(Text.literal("  ·  ").formatted(Formatting.GRAY)).append(Text.translatable("dungeon.abilitywar.enter_sub").formatted(Formatting.GRAY))));
            p.sendMessage(Text.translatable("dungeon.abilitywar.enter", Text.translatable("dungeon.abilitywar.name." + theme.key), Text.translatable("dungeon.abilitywar.diff." + diff.key),
                    run.fightsTotal()).formatted(Formatting.LIGHT_PURPLE), false);
        }
        w.playSound(null, s.c, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.HOSTILE, 0.6f, 0.6f);
        return null;
    }

    /** 혼자 나가기 (/aw dungeon leave). */
    public static boolean leave(ServerPlayerEntity p) {
        Run r = BY_PLAYER.get(p.getUuid());
        if (r == null) {
            if (isDungeon(p.getWorld())) sendHome(p, null);
            return false;
        }
        drop(r, p, Text.translatable("dungeon.abilitywar.left", p.getDisplayName()));
        sendHome(p, r.back.get(p.getUuid()));
        return true;
    }

    /** 관리자 · 시험용: 다음 (안 깬) 전투 방, 또는 그 종류 (trap · treasure · mid · boss) 의 첫 방으로. */
    public static boolean debugNext(ServerPlayerEntity p, @Nullable String kind) {
        Run r = BY_PLAYER.get(p.getUuid());
        if (r == null) return false;
        Room to = null;
        for (Room rm : r.rooms) {
            if (kind == null ? rm.fights() && !rm.cleared : rm.kind.name().equalsIgnoreCase(kind)) {
                to = rm;
                break;
            }
        }
        if (to == null) return false;
        ServerWorld w = (ServerWorld) p.getWorld();
        p.teleport(w, to.c.getX() + 0.5, to.c.getY() + 1, to.c.getZ() + 0.5 + Math.min(3, to.half - 2), 180, 10);
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
        if (w == null || isDungeon(w)) {
            w = s.getOverworld();
            BlockPos sp = w.getSpawnPos();
            p.teleport(w, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYaw(), 0);
        } else p.teleport(w, back.pos().x, back.pos().y, back.pos().z, back.yaw(), 0);
        p.fallDistance = 0;
    }

    static void end(MinecraftServer server, Run r, @Nullable String why) {
        ServerWorld w = server.getWorld(WORLD);
        for (ServerPlayerEntity p : players(server, r)) {
            BY_PLAYER.remove(p.getUuid());
            if (why != null) p.sendMessage(Text.translatable(why).formatted(Formatting.RED), false);
            sendHome(p, r.back.get(p.getUuid()));
        }
        for (UUID u : r.players) BY_PLAYER.remove(u);
        r.players.clear();
        r.bar.clearPlayers();
        if (w != null) for (Room rm : r.rooms) for (UUID id : rm.mobs) {
            Entity e = w.getEntity(id);
            if (e != null) e.discard();
        }
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

    // ================================================================ 방 배치

    static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /**
     * 칸 (26 블록) 위에서 시작 방부터 이어지는 길 6 칸 (시작 · 전투 · 전투/함정 · 중간 보스 · 전투 · 전투/함정) 을 무작위로 놓고,
     * 마지막 방에서 안 겹치는 쪽으로 큰 보스 방을 붙임. 길 중간에서 빈 옆 칸으로 보물 방 1~2 개.
     */
    static void layout(Run run, BlockPos origin, Random rnd) {
        Theme t = run.theme;
        for (int attempt = 0; attempt < 200; attempt++) {
            run.rooms.clear();
            run.links.clear();
            List<int[]> cells = new ArrayList<>();
            cells.add(new int[]{0, 0});
            boolean ok = true;
            for (int i = 1; i < 6 && ok; i++) {
                int[] last = cells.get(cells.size() - 1);
                List<Direction> ds = new ArrayList<>(List.of(DIRS));
                java.util.Collections.shuffle(ds, new java.util.Random(rnd.nextLong()));
                ok = false;
                for (Direction d : ds) {
                    int nx = last[0] + d.getOffsetX(), nz = last[1] + d.getOffsetZ();
                    if (Math.abs(nx) > 3 || Math.abs(nz) > 3 || cells.stream().anyMatch(c -> c[0] == nx && c[1] == nz)) continue;
                    // 중간 보스 방 (큰 방) 은 옆 칸과 붙지 않게 다른 칸과 2 칸 이상 떨어져야 함 → 그냥 칸 크기로 충분
                    cells.add(new int[]{nx, nz});
                    ok = true;
                    break;
                }
            }
            if (!ok) continue;
            Kind[] kinds = {Kind.START, Kind.COMBAT, rnd.nextBoolean() ? Kind.TRAP : Kind.COMBAT, Kind.MID, Kind.COMBAT, rnd.nextBoolean() ? Kind.TRAP : Kind.COMBAT};
            if (kinds[2] != Kind.TRAP && kinds[5] != Kind.TRAP) kinds[rnd.nextBoolean() ? 2 : 5] = Kind.TRAP;
            for (int i = 0; i < 6; i++) {
                int[] c = cells.get(i);
                BlockPos p = origin.add(c[0] * CELL, 0, c[1] * CELL);
                Room rm = kinds[i] == Kind.MID ? new Room(Kind.MID, p, t.midHalf(), t.midH()) : new Room(kinds[i], p, kinds[i] == Kind.START ? 6 : HALF, H);
                run.rooms.add(rm);
                if (i > 0) run.links.add(new Link(run.rooms.get(i - 1), rm, dirBetween(cells.get(i - 1), c)));
            }
            // 보스 방: 마지막 방에서 이어지는 쪽 중 아무 방과도 안 겹치는 곳
            Room last = run.rooms.get(5);
            List<Direction> ds = new ArrayList<>(List.of(DIRS));
            java.util.Collections.shuffle(ds, new java.util.Random(rnd.nextLong()));
            Room boss = null;
            for (Direction d : ds) {
                int dist = last.half + 6 + t.bossHalf();
                BlockPos bc = last.c.offset(d, dist);
                Room cand = new Room(Kind.BOSS, bc, t.bossHalf(), t.bossH());
                Box bb = cand.box(3);
                if (run.rooms.stream().anyMatch(o -> o != last && o.box(1).intersects(bb))) continue;
                boss = cand;
                run.links.add(new Link(last, cand, d));
                break;
            }
            if (boss == null) continue;
            run.rooms.add(boss);
            // 보물 방: 길 위 방 (시작 · 중간 보스 빼고) 의 빈 옆 칸
            int treasures = 1 + rnd.nextInt(2);
            for (int k = 0; k < 12 && treasures > 0; k++) {
                int i = 1 + rnd.nextInt(5);
                if (kinds[i] == Kind.MID) continue;
                int[] c = cells.get(i);
                Direction d = DIRS[rnd.nextInt(4)];
                int nx = c[0] + d.getOffsetX(), nz = c[1] + d.getOffsetZ();
                if (cells.stream().anyMatch(cc -> cc[0] == nx && cc[1] == nz)) continue;
                Room tr = new Room(Kind.TREASURE, origin.add(nx * CELL, 0, nz * CELL), 5, 5);
                Box tb = tr.box(2);
                if (run.rooms.stream().anyMatch(o -> o.box(1).intersects(tb))) continue;
                cells.add(new int[]{nx, nz});
                run.rooms.add(tr);
                run.links.add(new Link(run.rooms.get(i), tr, d));
                treasures--;
            }
            for (Link l : run.links) {
                l.a().doors.add(l.dir());
                l.b().doors.add(l.dir().getOpposite());
            }
            // 난이도: 어려움 · 지옥은 전투 방 두 무리
            for (Room rm : run.rooms) if (rm.kind == Kind.COMBAT) rm.waves = run.diff == Diff.NORMAL ? 1 : 2;
            return;
        }
    }

    static Direction dirBetween(int[] a, int[] b) {
        int dx = b[0] - a[0], dz = b[1] - a[1];
        return dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ================================================================ 짓기

    static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    static void build(ServerWorld w, Run run) {
        // 청크 불러오기
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Room r : run.rooms) {
            x0 = Math.min(x0, r.c.getX() - r.half - 8);
            z0 = Math.min(z0, r.c.getZ() - r.half - 8);
            x1 = Math.max(x1, r.c.getX() + r.half + 8);
            z1 = Math.max(z1, r.c.getZ() + r.half + 8);
        }
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) w.getChunk(cx, cz);
        Palette p = run.theme.pal;
        for (Link l : run.links) corridor(w, p, l);
        for (Room r : run.rooms) room(w, p, run.theme, r);
        for (Room r : run.rooms) for (Direction d : r.doors) door(w, p, r, d, false);
    }

    static void set(ServerWorld w, BlockPos pos, BlockState s) {
        if (w.getBlockState(pos) != s) w.setBlockState(pos, s, FLAGS);
    }

    static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        return (h ^ (h >>> 13)) & 0x7FFFFFFF;
    }

    static void room(ServerWorld w, Palette p, Theme t, Room r) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        int cx = r.c.getX(), cy = r.c.getY(), cz = r.c.getZ(), hf = r.half;
        for (int dx = -hf; dx <= hf; dx++)
            for (int dz = -hf; dz <= hf; dz++)
                for (int dy = -1; dy <= r.h + 1; dy++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    boolean edge = Math.abs(dx) == hf || Math.abs(dz) == hf;
                    BlockState s;
                    if (dy == -1) s = p.wall2().getDefaultState();
                    else if (dy == 0) s = floor(p, r, dx, dz, x, z);
                    else if (dy == r.h + 1) s = (hash(x, y, z) % 9 == 0 && !edge && r.kind != Kind.BOSS) ? p.light().getDefaultState() : p.ceil().getDefaultState();
                    else if (edge) {
                        boolean corner = Math.abs(dx) == hf && Math.abs(dz) == hf;
                        int along = Math.abs(dx) == hf ? dz : dx;
                        if (!corner && dy == 3 && Math.floorMod(along, 4) == 2) s = p.light().getDefaultState();
                        else s = (hash(x, y, z) % 4 == 0 ? p.wall2() : p.wall()).getDefaultState();
                    } else s = Blocks.AIR.getDefaultState();
                    set(w, m.set(x, y, z), s);
                }
        // 기둥 (큰 방은 8 개)
        int pin = hf - 2;
        List<int[]> pillars = new ArrayList<>(List.of(new int[]{pin, pin}, new int[]{-pin, pin}, new int[]{pin, -pin}, new int[]{-pin, -pin}));
        if (hf >= 10) {
            pillars.addAll(List.of(new int[]{0, pin}, new int[]{0, -pin}, new int[]{pin, 0}, new int[]{-pin, 0}));
        }
        if (r.kind != Kind.TREASURE && r.kind != Kind.START)
            for (int[] q : pillars) {
                if (blocksDoor(r, q[0], q[1])) continue;
                for (int dy = 1; dy <= r.h; dy++)
                    set(w, m.set(cx + q[0], cy + dy, cz + q[1]), (dy == r.h ? p.light() : p.pillar()).getDefaultState());
            }
        Random rnd = w.getRandom();
        switch (r.kind) {
            case COMBAT -> {
                for (int i = 0; i < 5; i++) {
                    int dx = rnd.nextBoolean() ? hf - 1 : -(hf - 1), dz = rnd.nextInt(hf * 2 - 3) - (hf - 2);
                    if (rnd.nextBoolean()) {
                        int tmp = dx;
                        dx = dz;
                        dz = tmp;
                    }
                    if (blocksDoor(r, dx, dz)) continue;
                    set(w, m.set(cx + dx, cy + 1, cz + dz), p.props()[rnd.nextInt(p.props().length)].getDefaultState());
                }
            }
            case TRAP -> {
                // 함정 바닥: 금 간 무늬 + 가운데 경고 표시
                for (int dx = -hf + 1; dx < hf; dx++)
                    for (int dz = -hf + 1; dz < hf; dz++)
                        if (((dx + dz) & 3) == 0) set(w, m.set(cx + dx, cy, cz + dz), Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState());
            }
            case TREASURE -> {
                BlockPos chest = r.c.up();
                set(w, chest, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, r.doors.isEmpty() ? Direction.NORTH : r.doors.get(0)));
                if (w.getBlockEntity(chest) instanceof ChestBlockEntity be) com.abilitywar.world.Blueprint.Loot.fill(be, t.ordinal() >= 2 ? 2 : 1, rnd);
                for (Direction d : DIRS) {
                    BlockPos g = chest.offset(d, 2).down();
                    set(w, g, Blocks.GOLD_BLOCK.getDefaultState());
                    set(w, g.up(), Blocks.LANTERN.getDefaultState());
                }
            }
            case START -> {
                // 들어온 문 (차원문 테) — 나가기는 /aw dungeon leave
                for (int dy = 1; dy <= 4; dy++) {
                    set(w, m.set(cx - 2, cy + dy, cz - hf + 1), Blocks.CRYING_OBSIDIAN.getDefaultState());
                    set(w, m.set(cx + 2, cy + dy, cz - hf + 1), Blocks.CRYING_OBSIDIAN.getDefaultState());
                }
                for (int dx = -2; dx <= 2; dx++) set(w, m.set(cx + dx, cy + 5, cz - hf + 1), Blocks.OBSIDIAN.getDefaultState());
                for (int dx = -1; dx <= 1; dx++) for (int dy = 1; dy <= 4; dy++) set(w, m.set(cx + dx, cy + dy, cz - hf + 1), Blocks.PURPLE_STAINED_GLASS.getDefaultState());
            }
            case BOSS -> {
                // 바닥 고리 + 가운데 문양
                for (int dx = -hf + 1; dx < hf; dx++)
                    for (int dz = -hf + 1; dz < hf; dz++) {
                        double d = Math.sqrt(dx * dx + dz * dz);
                        if (Math.abs(d - (hf - 4)) < 0.6) set(w, m.set(cx + dx, cy, cz + dz), p.light().getDefaultState());
                        else if (d < 2.5) set(w, m.set(cx + dx, cy, cz + dz), p.floor2().getDefaultState());
                    }
            }
            default -> {
            }
        }
    }

    static BlockState floor(Palette p, Room r, int dx, int dz, int x, int z) {
        if (r.kind == Kind.START && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) return Blocks.AMETHYST_BLOCK.getDefaultState();
        int h = hash(x, 0, z);
        if (dx == 0 || dz == 0) return p.floor2().getDefaultState();
        return (h % 5 == 0 ? p.floor2() : p.floor()).getDefaultState();
    }

    /** 문 앞 (문 좌우 2 칸 · 안쪽 2 칸) 은 비워 둠. */
    static boolean blocksDoor(Room r, int dx, int dz) {
        for (Direction d : r.doors) {
            int ax = d.getOffsetX() * r.half, az = d.getOffsetZ() * r.half;
            if (d.getAxis() == Direction.Axis.X ? Math.abs(dz) <= 2 && Math.abs(dx - ax) <= 3 : Math.abs(dx) <= 2 && Math.abs(dz - az) <= 3) return true;
        }
        return false;
    }

    /** 방 사이 복도: 폭 3 · 높이 4. */
    static void corridor(ServerWorld w, Palette p, Link l) {
        Direction d = l.dir();
        BlockPos from = l.a().c.offset(d, l.a().half), to = l.b().c.offset(d.getOpposite(), l.b().half);
        int len = Math.abs(to.getX() - from.getX()) + Math.abs(to.getZ() - from.getZ());
        Direction side = d.rotateYClockwise();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int i = 0; i <= len; i++) {
            BlockPos base = from.offset(d, i);
            for (int s = -2; s <= 2; s++) {
                BlockPos col = base.offset(side, s);
                for (int dy = -1; dy <= 5; dy++) {
                    BlockState st;
                    if (dy == -1) st = p.wall2().getDefaultState();
                    else if (dy == 0) st = (s == 0 ? p.floor2() : p.floor()).getDefaultState();
                    else if (dy == 5) st = (s == 0 && i % 5 == 2 ? p.light() : p.ceil()).getDefaultState();
                    else if (Math.abs(s) == 2) st = (hash(col.getX(), dy, col.getZ()) % 4 == 0 ? p.wall2() : p.wall()).getDefaultState();
                    else st = Blocks.AIR.getDefaultState();
                    set(w, m.set(col.getX(), col.getY() + dy, col.getZ()), st);
                }
            }
        }
    }

    /** 방의 문 (폭 3 · 높이 4): 닫으면 테마의 문 블록 (울타리 · 철창), 열면 공기. */
    static void door(ServerWorld w, Palette p, Room r, Direction d, boolean closed) {
        BlockPos c = r.c.offset(d, r.half);
        Direction side = d.rotateYClockwise();
        BlockState st = closed ? p.door().getDefaultState() : Blocks.AIR.getDefaultState();
        for (int s = -1; s <= 1; s++)
            for (int dy = 1; dy <= 4; dy++) {
                BlockPos q = c.offset(side, s).up(dy);
                w.setBlockState(q, st, Block.NOTIFY_ALL);
            }
        if (closed) for (int s = -1; s <= 1; s++) w.spawnParticles(ParticleTypes.CLOUD, c.getX() + 0.5 + side.getOffsetX() * s, c.getY() + 2, c.getZ() + 0.5 + side.getOffsetZ() * s, 6, 0.2, 1, 0.2, 0.02);
    }

    // ================================================================ 진행

    static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) return;
        ServerWorld w = server.getWorld(WORLD);
        if (w == null) return;
        for (Run r : new ArrayList<>(RUNS)) {
            for (ServerPlayerEntity p : players(server, r)) {
                if (!p.isAlive() || p.getWorld() != w) {
                    drop(r, p, Text.translatable("dungeon.abilitywar.fell", p.getDisplayName()));
                    continue;
                }
                if (p.getY() < Y - 12) {
                    Room s = r.start();
                    p.teleport(w, s.c.getX() + 0.5, s.c.getY() + 1, s.c.getZ() + 0.5, p.getYaw(), p.getPitch());
                    p.fallDistance = 0;
                }
            }
            List<ServerPlayerEntity> ps = players(server, r);
            if (ps.isEmpty()) {
                end(server, r, null);
                continue;
            }
            r.timer++;
            if (r.state == State.DONE) {
                r.endTimer++;
                r.bar.setName(Text.translatable("dungeon.abilitywar.bar.done", (END_WAIT - r.endTimer) / 20 + 1));
                r.bar.setPercent(1f - r.endTimer / (float) END_WAIT);
                if (r.endTimer % 10 == 0) {
                    Room b = r.rooms.get(r.rooms.size() - 1);
                    w.spawnParticles(ParticleTypes.END_ROD, b.c.getX() + 0.5, b.c.getY() + 2, b.c.getZ() + 0.5, 10, 1.5, 1.5, 1.5, 0.05);
                }
                if (r.endTimer >= END_WAIT) end(server, r, null);
                continue;
            }
            if (r.timer >= TIME_LIMIT) {
                end(server, r, "dungeon.abilitywar.timeout");
                continue;
            }
            Room active = null;
            for (Room rm : r.rooms) {
                boolean inside = ps.stream().anyMatch(p -> rm.inside(p.getPos(), 1.5));
                if (inside && !rm.visited) {
                    rm.visited = true;
                    if (rm.kind == Kind.TREASURE) for (ServerPlayerEntity p : ps) p.sendMessage(Text.translatable("dungeon.abilitywar.treasure").formatted(Formatting.GOLD), true);
                    if (rm.kind == Kind.TRAP) for (ServerPlayerEntity p : ps) p.sendMessage(Text.translatable("dungeon.abilitywar.trap").formatted(Formatting.RED), true);
                }
                if (rm.fights() && !rm.cleared && !rm.active && inside) activate(w, r, rm, ps);
                if (rm.active) {
                    active = rm;
                    fight(server, w, r, rm, ps);
                }
                if (rm.kind == Kind.TRAP) {
                    if (inside) trap(w, r, rm, ps);
                    erupt(w, r, rm, ps);
                }
            }
            bar(w, r, active);
        }
    }

    static void bar(ServerWorld w, Run r, @Nullable Room active) {
        int left = Math.max(0, (TIME_LIMIT - r.timer) / 20);
        String time = String.format("%d:%02d", left / 60, left % 60);
        Text name = Text.translatable("dungeon.abilitywar.name." + r.theme.key);
        // 우두머리 체력: 자기 체력바가 없는 몹일 때만 (이 모드 보스 · 정예는 자기 체력바가 있음)
        if (active != null && active.boss != null && w.getEntity(active.boss) instanceof LivingEntity b && b.isAlive() && !(b instanceof com.abilitywar.mob.BossEntity)) {
            r.bar.setColor(BossBar.Color.RED);
            r.bar.setName(Text.translatable("dungeon.abilitywar.bar.boss", b.getDisplayName(), (int) Math.ceil(b.getHealth()), (int) Math.ceil(b.getMaxHealth()), time));
            r.bar.setPercent(b.getHealth() / b.getMaxHealth());
        } else if (active != null) {
            int alive = alive(w, active);
            r.bar.setColor(BossBar.Color.YELLOW);
            r.bar.setName(Text.translatable("dungeon.abilitywar.bar.fight", name, alive, active.total, time));
            r.bar.setPercent(active.total <= 0 ? 0 : alive / (float) active.total);
        } else {
            r.bar.setColor(BossBar.Color.PURPLE);
            r.bar.setName(Text.translatable("dungeon.abilitywar.bar.explore", name, Text.translatable("dungeon.abilitywar.diff." + r.diff.key), r.fightsCleared(), r.fightsTotal(), time));
            r.bar.setPercent(r.fightsTotal() == 0 ? 1 : r.fightsCleared() / (float) r.fightsTotal());
        }
    }

    /** 전투 방에 들어옴: 문을 잠그고, 밖에 있는 파티원을 안으로 불러들이고, 적을 내보냄. */
    static void activate(ServerWorld w, Run r, Room rm, List<ServerPlayerEntity> ps) {
        rm.active = true;
        rm.wave = 0;
        Vec3d in = Vec3d.ofBottomCenter(rm.c.up());
        for (ServerPlayerEntity p : ps)
            if (!rm.inside(p.getPos(), 1.5)) {
                Vec3d to = in.add(w.getRandom().nextGaussian() * 1.5, 0, w.getRandom().nextGaussian() * 1.5);
                p.teleport(w, to.x, to.y, to.z, p.getYaw(), p.getPitch());
            }
        for (Direction d : rm.doors) door(w, r.theme.pal, rm, d, true);
        w.playSound(null, rm.c, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.HOSTILE, 1.5f, 0.6f);
        if (rm.kind != Kind.COMBAT) {
            String key = rm.kind == Kind.BOSS ? "dungeon.abilitywar.boss_room" : "dungeon.abilitywar.mid_room";
            for (ServerPlayerEntity p : ps) {
                p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 10));
                p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable(key).formatted(rm.kind == Kind.BOSS ? Formatting.DARK_RED : Formatting.RED, Formatting.BOLD)));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("dungeon.abilitywar.boss." + r.theme.key + "." + (rm.kind == Kind.BOSS ? "boss" : "mid")).formatted(Formatting.GRAY)));
            }
            w.playSound(null, rm.c, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6f, rm.kind == Kind.BOSS ? 0.6f : 0.9f);
        }
        spawnWave(w, r, rm);
    }

    static int alive(ServerWorld w, Room rm) {
        int n = 0;
        for (UUID id : rm.mobs) if (w.getEntity(id) instanceof LivingEntity le && le.isAlive()) n++;
        return n;
    }

    static void fight(MinecraftServer server, ServerWorld w, Run r, Room rm, List<ServerPlayerEntity> ps) {
        rm.timer++;
        int n = 0;
        for (UUID id : rm.mobs) {
            if (!(w.getEntity(id) instanceof LivingEntity le) || !le.isAlive()) continue;
            n++;
            // 방 밖으로 나간 몹은 되돌림 · 대상이 없으면 가까운 사람
            if (!rm.inside(le.getPos(), -2) && rm.timer % 20 == 0) le.refreshPositionAndAngles(rm.c.getX() + 0.5, rm.c.getY() + 1, rm.c.getZ() + 0.5, le.getYaw(), 0);
            if (le instanceof MobEntity m && rm.timer % 20 == 0 && (m.getTarget() == null || !m.getTarget().isAlive())) {
                ServerPlayerEntity near = null;
                for (ServerPlayerEntity p : ps) if (!p.isCreative() && !p.isSpectator() && (near == null || p.squaredDistanceTo(m) < near.squaredDistanceTo(m))) near = p;
                if (near != null) m.setTarget(near);
            }
        }
        // 보스 방 (어려움 · 지옥): 25초마다 부하 둘 (최대 6)
        if (rm.kind == Kind.BOSS && r.diff != Diff.NORMAL && rm.timer % 500 == 0 && n < 7) {
            for (int i = 0; i < 2; i++) spawn(w, r, rm, pick(r.theme.mobs, w.getRandom()), 0);
            w.playSound(null, rm.c, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.5f, 0.8f);
        }
        if (n > 0) return;
        // 다음 무리 또는 정리
        if (rm.kind == Kind.COMBAT && rm.wave < rm.waves) {
            spawnWave(w, r, rm);
            for (ServerPlayerEntity p : ps) p.sendMessage(Text.translatable("dungeon.abilitywar.wave", rm.wave, rm.waves).formatted(Formatting.YELLOW), true);
            return;
        }
        rm.active = false;
        rm.cleared = true;
        rm.mobs.clear();
        for (Direction d : rm.doors) door(w, r.theme.pal, rm, d, false);
        w.playSound(null, rm.c, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.HOSTILE, 1.5f, 0.8f);
        if (rm.kind == Kind.BOSS) {
            victory(server, w, r, rm, ps);
            return;
        }
        long g = (long) r.theme.gold / 10 * r.diff.reward / (rm.kind == Kind.MID ? 1 : 2);
        for (ServerPlayerEntity p : ps) {
            RpgManager.addGold(p, g);
            p.heal(p.getMaxHealth() * 0.25f);
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(4, 26, 8));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable(rm.kind == Kind.MID ? "dungeon.abilitywar.mid_down" : "dungeon.abilitywar.room_clear", Shops.fmt(g))
                    .formatted(Formatting.GOLD)));
        }
        w.playSound(null, rm.c, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    static String pick(String[] ids, Random r) {
        return ids[r.nextInt(ids.length)];
    }

    /** 첫 번째로 설치된 id (없으면 마지막 = 이 모드 몹). */
    static String first(String[] ids) {
        for (String id : ids) {
            Identifier i = Identifier.tryParse(id);
            if (i != null && Registries.ENTITY_TYPE.containsId(i)) return id;
        }
        return ids[ids.length - 1];
    }

    static void spawnWave(ServerWorld w, Run r, Room rm) {
        rm.wave++;
        Random rnd = w.getRandom();
        int extra = Math.max(0, r.partySize - 1);
        switch (rm.kind) {
            case MID -> {
                rm.boss = spawn(w, r, rm, first(r.theme.mid), 1);
                for (int i = 0; i < 1 + r.diff.ordinal() + extra; i++) spawn(w, r, rm, pick(r.theme.mobs, rnd), 0);
            }
            case BOSS -> {
                rm.boss = spawn(w, r, rm, first(r.theme.boss), 2);
                for (int i = 0; i < 2 + r.diff.ordinal() + extra; i++) spawn(w, r, rm, pick(r.theme.mobs, rnd), 0);
            }
            default -> {
                int n = 3 + r.theme.tier() / 2 + r.diff.ordinal() + extra * 2;
                for (int i = 0; i < n; i++) spawn(w, r, rm, pick(r.theme.mobs, rnd), 0);
            }
        }
        rm.total = alive(w, rm);
        w.playSound(null, rm.c, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.2f, 0.7f);
    }

    /** rank 0 일반 · 1 중간 보스 · 2 보스. 보스가 아닌 몹을 보스로 세우면 이름 · 큰 체력 · 밀리지 않음. */
    @Nullable
    static UUID spawn(ServerWorld w, Run r, Room rm, String id, int rank) {
        Identifier i = Identifier.tryParse(id);
        if (i == null || !Registries.ENTITY_TYPE.containsId(i)) return null;
        EntityType<?> type = Registries.ENTITY_TYPE.get(i);
        Entity e = type.create(w);
        if (!(e instanceof LivingEntity le)) {
            if (e != null) e.discard();
            return null;
        }
        Random rnd = w.getRandom();
        int span = Math.max(2, rm.half - 3);
        double x = rm.c.getX() + 0.5 + (rank > 0 ? 0 : (rnd.nextDouble() * 2 - 1) * span);
        double z = rm.c.getZ() + 0.5 + (rank > 0 ? 0 : (rnd.nextDouble() * 2 - 1) * span);
        boolean flier = e instanceof com.abilitywar.mob.AncientWyvernEntity || type == EntityType.BLAZE;
        double y = rm.c.getY() + 1 + (flier && rank > 0 ? Math.min(6, rm.h - 4) : 0);
        e.refreshPositionAndAngles(x, y, z, rnd.nextFloat() * 360, 0);
        if (e instanceof MobEntity m) {
            m.initialize(w, w.getLocalDifficulty(e.getBlockPos()), SpawnReason.EVENT, null, null);
            m.setPersistent();
        }
        // 슬라임: 보스 = 슬라임 킹 (크기 8), 중간 보스 = 크기 4, 보통 1~3
        if (e instanceof net.minecraft.entity.mob.SlimeEntity sl) sl.setSize(rank == 2 ? 8 : rank == 1 ? 4 : 1 + rnd.nextInt(3), true);
        e.addCommandTag(MOB_TAG);
        double party = 1 + 0.35 * Math.max(0, r.partySize - 1);
        double hp = r.diff.hp * party, dmg = r.diff.dmg;
        // 우두머리 체력: 진짜 보스 (이 모드 보스 · 다른 모드 보스) 는 난이도 · 인원만, 정예 몹은 중간 ×1.5 · 보스 ×2.5,
        // 보통 몹을 우두머리로 세우면 중간 ×6 · 보스 ×12 + 피해 ×1.5 + 빛남 + 밀리지 않음
        boolean elite = e instanceof com.abilitywar.mob.EliteEntity;
        boolean realBoss = (e instanceof com.abilitywar.mob.BossEntity && !elite) || !(i.getNamespace().equals(AbilityWar.MOD_ID) || i.getNamespace().equals("minecraft"));
        if (rank > 0 && elite) hp *= rank == 2 ? 2.5 : 1.5;
        else if (rank > 0 && !realBoss) {
            hp *= rank == 2 ? 12 : 6;
            dmg *= 1.5;
            le.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.GLOWING, 20 * 60 * 30, 0, false, false));
            mod(le, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KB_ID, 0.8, EntityAttributeModifier.Operation.ADDITION);
        }
        if (rank > 0) e.setCustomName(Text.translatable("dungeon.abilitywar.boss." + r.theme.key + "." + (rank == 2 ? "boss" : "mid")).styled(s -> s.withColor(r.theme.color)));
        mod(le, EntityAttributes.GENERIC_MAX_HEALTH, HP_ID, hp - 1, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(le, EntityAttributes.GENERIC_ATTACK_DAMAGE, DMG_ID, dmg - 1, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        le.setHealth(le.getMaxHealth());
        w.spawnEntity(e);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 1, z, 20, 0.4, 0.8, 0.4, 0.03);
        rm.mobs.add(e.getUuid());
        return e.getUuid();
    }

    static void mod(LivingEntity e, EntityAttribute attr, UUID id, double v, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance ai = e.getAttributeInstance(attr);
        if (ai == null || v <= 0) return;
        ai.removeModifier(id);
        ai.addPersistentModifier(new EntityAttributeModifier(id, "abilitywar dungeon", v, op));
    }

    /** 함정 방: 1.5초마다 바닥 여러 곳 (한 곳은 사람 발밑) 에 경고 원 → 1초 뒤 가시가 솟음 (피해 + 띄움). */
    static void trap(ServerWorld w, Run r, Room rm, List<ServerPlayerEntity> ps) {
        if (r.timer % 30 != 0) return;
        Random rnd = w.getRandom();
        int n = 3 + r.diff.ordinal() * 2;
        List<Vec3d> spots = new ArrayList<>();
        for (int i = 0; i < n; i++)
            spots.add(new Vec3d(rm.c.getX() + 0.5 + (rnd.nextDouble() * 2 - 1) * (rm.half - 1.5), rm.c.getY() + 1, rm.c.getZ() + 0.5 + (rnd.nextDouble() * 2 - 1) * (rm.half - 1.5)));
        List<ServerPlayerEntity> in = ps.stream().filter(p -> rm.inside(p.getPos(), 0.5)).toList();
        if (!in.isEmpty()) spots.add(in.get(rnd.nextInt(in.size())).getPos());
        float dmg = 4 + 3 * r.diff.ordinal() + r.theme.tier() * 2;
        for (Vec3d s : spots) {
            NbtCompound n2 = new NbtCompound();
            n2.putFloat("r", 1.6f);
            n2.putInt("d", 20);
            n2.putInt("col", 0xFF3030);
            AWNet.fx(w, s, "boss_warn", n2);
            rm.pending.add(new double[]{s.x, s.y, s.z, r.timer + 20, dmg});
        }
    }

    static void erupt(ServerWorld w, Run r, Room rm, List<ServerPlayerEntity> ps) {
        boolean any = false;
        for (int i = rm.pending.size() - 1; i >= 0; i--) {
            double[] q = rm.pending.get(i);
            if (r.timer < q[3]) continue;
            rm.pending.remove(i);
            any = true;
            Vec3d s = new Vec3d(q[0], q[1], q[2]);
            NbtCompound n2 = new NbtCompound();
            n2.putFloat("r", 1.6f);
            n2.putInt("col", 0x9A9A9A);
            AWNet.fx(w, s, "boss_slam", n2);
            w.spawnParticles(ParticleTypes.CRIT, s.x, s.y + 0.5, s.z, 12, 0.5, 0.6, 0.5, 0.2);
            for (ServerPlayerEntity p : ps) {
                if (p.getPos().squaredDistanceTo(s) > 1.7 * 1.7 || p.isCreative()) continue;
                p.damage(w.getDamageSources().magic(), (float) q[4]);
                p.setVelocity(p.getVelocity().add(0, 0.6, 0));
                p.velocityModified = true;
            }
        }
        if (any) w.playSound(null, rm.c, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    // ================================================================ 보상

    static void victory(MinecraftServer server, ServerWorld w, Run r, Room rm, List<ServerPlayerEntity> ps) {
        r.state = State.DONE;
        r.endTimer = 0;
        Random rnd = w.getRandom();
        Theme t = r.theme;
        Diff d = r.diff;
        long g = (long) t.gold * d.reward;
        int xp = t.level * 50 * d.reward;
        int mins = r.timer / 1200, secs = (r.timer / 20) % 60;
        for (ServerPlayerEntity p : ps) {
            RpgManager.addGold(p, g);
            RpgManager.addXp(p, xp);
            Item[] mats = t.mats();
            for (int i = 0; i < 2 + d.ordinal() * 2; i++) Shops.give(p, new ItemStack(mats[rnd.nextInt(mats.length)], 1 + rnd.nextInt(2)));
            Shops.give(p, new ItemStack(RpgItems.ENHANCE_STONE, 3 + d.ordinal() * 3 + t.ordinal()));
            Shops.give(p, new ItemStack(RpgItems.BOSS_SOUL, 1 + d.ordinal()));
            if (d != Diff.NORMAL) Shops.give(p, new ItemStack(RpgItems.PROTECTION_SCROLL, d == Diff.HELL ? 3 : 1));
            if (d == Diff.HELL) Shops.give(p, new ItemStack(RpgItems.ABYSS_CRYSTAL, 2 + t.ordinal()));
            Item gear = gear(t.grade(d, rnd), rnd);
            if (gear != null) Shops.give(p, new ItemStack(gear));
            RpgManager.questAmount(p, JobQuests.Type.BOSS, 1);
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("dungeon.abilitywar.clear").formatted(Formatting.GOLD, Formatting.BOLD)));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("dungeon.abilitywar.clear_sub", Shops.fmt(g), xp).formatted(Formatting.YELLOW)));
            p.sendMessage(Text.translatable("dungeon.abilitywar.clear_chat", Text.translatable("dungeon.abilitywar.name." + t.key), Text.translatable("dungeon.abilitywar.diff." + d.key),
                    String.format("%d:%02d", mins, secs), Shops.fmt(g)).formatted(Formatting.GOLD), false);
        }
        // 보상 상자 (보스 방 가운데) + 빛
        BlockPos c = rm.c.up();
        w.setBlockState(c, Blocks.CHEST.getDefaultState(), Block.NOTIFY_ALL);
        if (w.getBlockEntity(c) instanceof ChestBlockEntity be) com.abilitywar.world.Blueprint.Loot.fill(be, 2, rnd);
        w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 120, 1.5, 1.5, 1.5, 0.5);
        w.playSound(null, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.2f, 1.0f);
        if (d == Diff.HELL && server != null)
            for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList())
                o.sendMessage(Text.literal("⚔ ").formatted(Formatting.DARK_RED).append(Text.translatable("dungeon.abilitywar.broadcast_hell", ps.get(0).getDisplayName(),
                        Text.translatable("dungeon.abilitywar.name." + t.key))).formatted(Formatting.RED), false);
    }

    @Nullable
    static Item gear(GearGrade g, Random r) {
        if (r.nextBoolean()) {
            var list = GearItems.WEAPONS.get(g);
            if (list != null && !list.isEmpty()) return list.get(r.nextInt(list.size()));
        }
        var sets = GearItems.SETS.get(g);
        if (sets == null || sets.isEmpty()) return null;
        var pieces = new ArrayList<>(sets.get(r.nextInt(sets.size())).pieces().values());
        return GearItems.BY_ID.get(pieces.get(r.nextInt(pieces.size())).id());
    }

    private InstanceDungeon() {
    }
}
