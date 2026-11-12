package com.abilitywar.command;

import com.abilitywar.ability.Abilities;
import com.abilitywar.ability.Ability;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.server.AbilityManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /abilitywar (별칭 /aw, /능력)
 */
public final class AWCommands {
    private static final SuggestionProvider<ServerCommandSource> ABILITIES = (ctx, b) ->
            CommandSource.suggestMatching(Abilities.all().stream().map(a -> a.key), b);

    private static final SuggestionProvider<ServerCommandSource> GRADES = (ctx, b) ->
            CommandSource.suggestMatching(java.util.Arrays.stream(com.abilitywar.gear.GearGrade.values()).map(g -> g.key), b);

    /** /aw gear <등급> [weapon|armor|set|all] [개수]: 그 등급 장비를 무작위로 받음 (set = 갑옷 한 벌). */
    private static int gear(CommandContext<ServerCommandSource> ctx, String kind, int count) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        com.abilitywar.gear.GearGrade g = com.abilitywar.gear.GearGrade.byKey(StringArgumentType.getString(ctx, "grade"));
        java.util.Random r = new java.util.Random();
        int given = 0;
        for (int i = 0; i < count; i++) {
            List<net.minecraft.item.Item> give = new java.util.ArrayList<>();
            boolean weapon = kind.equals("weapon") || (kind.equals("all") && r.nextBoolean());
            if (weapon) {
                var l = com.abilitywar.gear.GearItems.WEAPONS.get(g);
                give.add(l.get(r.nextInt(l.size())));
            } else {
                var sets = com.abilitywar.gear.GearItems.SETS.get(g);
                var set = sets.get(r.nextInt(sets.size()));
                var items = com.abilitywar.gear.GearItems.setItems(set);
                if (kind.equals("set")) give.addAll(items);
                else give.add(items.get(r.nextInt(items.size())));
            }
            for (net.minecraft.item.Item it : give) {
                net.minecraft.item.ItemStack st = new net.minecraft.item.ItemStack(it);
                if (!p.getInventory().insertStack(st)) p.dropItem(st, false);
                given++;
            }
        }
        int n = given;
        ctx.getSource().sendFeedback(() -> Text.translatable("gear.abilitywar.ui.give", g.displayName(), n), false);
        return n;
    }

    /** /aw tower start [층]: 관리자는 아무 층부터, 아니면 체크포인트만. */
    static int towerStart(ServerCommandSource src, int floor) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        String err = com.abilitywar.rpg.AbyssTower.start(p, floor, src.hasPermissionLevel(2));
        if (err != null) src.sendError(Text.translatable(err, com.abilitywar.rpg.AbyssTower.MIN_LEVEL));
        return err == null ? 1 : 0;
    }

    /** /aw request accept|decline party|trade [보낸 사람] */
    static int request(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, String from) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean accept = StringArgumentType.getString(ctx, "answer").equals("accept");
        var type = StringArgumentType.getString(ctx, "type").equals("trade") ? com.abilitywar.social.Requests.Type.TRADE : com.abilitywar.social.Requests.Type.PARTY;
        com.abilitywar.social.Requests.respond(ctx.getSource().getPlayerOrThrow(), type, accept, from);
        return 1;
    }

    /** /aw gold add|set|take <양> [플레이어] */
    static int goldOp(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, java.util.Collection<ServerPlayerEntity> targets) {
        String op = StringArgumentType.getString(ctx, "op");
        long amount = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "amount");
        for (ServerPlayerEntity p : targets) {
            switch (op) {
                case "set" -> com.abilitywar.rpg.RpgManager.setGold(p, amount);
                case "take" -> com.abilitywar.rpg.RpgManager.setGold(p, Math.max(0, com.abilitywar.rpg.RpgManager.gold(p) - amount));
                default -> com.abilitywar.rpg.RpgManager.addGold(p, amount);
            }
            ctx.getSource().sendFeedback(() -> Text.literal(p.getEntityName() + ": ").append(Text.translatable("gold.abilitywar.have",
                    com.abilitywar.rpg.Shops.fmt(com.abilitywar.rpg.RpgManager.gold(p)))), true);
        }
        return targets.size();
    }

    /** /aw npc 로 놓을 수 있는 NPC. */
    static final java.util.Map<String, net.minecraft.entity.EntityType<?>> NPCS = java.util.Map.of(
            "apothecary", com.abilitywar.registry.AWEntities.APOTHECARY, "blacksmith", com.abilitywar.registry.AWEntities.BLACKSMITH,
            "guild_clerk", com.abilitywar.registry.AWEntities.GUILD_CLERK, "job_master", com.abilitywar.registry.AWEntities.JOB_MASTER,
            "king", com.abilitywar.registry.AWEntities.KING, "tower_keeper", com.abilitywar.registry.AWEntities.TOWER_KEEPER,
            "dungeon_guide", com.abilitywar.registry.AWEntities.DUNGEON_GUIDE);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> BOSSES = (ctx, b) ->
            net.minecraft.command.CommandSource.suggestMatching(java.util.stream.Stream.of("abilitywar:fallen_king", "abilitywar:ember_colossus", "abilitywar:frost_witch", "abilitywar:ancient_wyvern", "abilitywar:dragon_king",
                    "bosses_of_mass_destruction:lich", "bosses_of_mass_destruction:obsidilith", "bosses_of_mass_destruction:gauntlet", "bosses_of_mass_destruction:void_blossom",
                    "soulsweapons:accursed_lord_boss", "soulsweapons:draugr_boss", "soulsweapons:night_shade", "soulsweapons:returning_knight", "soulsweapons:chaos_monarch",
                    "soulsweapons:moonknight", "soulsweapons:day_stalker", "soulsweapons:night_prowler", "graveyard:lich",
                    "archon:tar", "archon:ayla", "archon:leven", "archon:inigo", "archon:null",
                    "adventurez:blackstone_golem", "adventurez:the_eye", "adventurez:void_shadow",
                    "dungeonnowloading:chaos_spawner", "dungeonnowloading:fairkeeper_boros", "dungeonnowloading:fairkeeper_ouros",
                    "aquamirae:captain_cornelia", "myths_of_the_sea:leviathan", "myths_of_the_sea:kraken", "mutantmonsters:mutant_zombie",
                    "mutantmonsters:mutant_skeleton", "mutantmonsters:mutant_creeper", "mutantmonsters:mutant_enderman", "saintsdragons:raevyx",
                    "saintsdragons:ignivorus", "saintsdragons:cindervane", "saintsdragons:nulljaw", "saintsdragons:atroxiia", "mobs_of_mythology:drake",
                    "mobs_of_mythology:automaton", "minecraft:ender_dragon", "minecraft:wither")
                    .filter(id -> net.minecraft.registry.Registries.ENTITY_TYPE.containsId(new net.minecraft.util.Identifier(id))).toList(), b);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> PLACES = (ctx, b) ->
            net.minecraft.command.CommandSource.suggestMatching(java.util.Arrays.stream(com.abilitywar.world.RpgStructures.Kind.values()).map(k -> k.name().toLowerCase()).toList(), b);

    private static int dungeonStart(ServerCommandSource src, String theme, String diff) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        var t = com.abilitywar.rpg.InstanceDungeon.Theme.byKey(theme);
        if (t == null) {
            src.sendError(Text.literal(theme + "?"));
            return 0;
        }
        var d = com.abilitywar.rpg.InstanceDungeon.Diff.byKey(diff);
        String err = com.abilitywar.rpg.InstanceDungeon.start(p, t, d);
        if (err != null) {
            src.sendError(Text.translatable(err, com.abilitywar.rpg.InstanceDungeon.minLevel(t, d), com.abilitywar.rpg.Shops.fmt(com.abilitywar.rpg.InstanceDungeon.fee(t, d))));
            return 0;
        }
        return 1;
    }

    /** /aw goto <장소>: 가장 가까운 RPG 구조물 앞으로 순간이동 (수도는 광장). */
    private static int gotoPlace(ServerCommandSource src, String kind) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        com.abilitywar.world.RpgStructures.Kind k;
        try {
            k = com.abilitywar.world.RpgStructures.Kind.valueOf(kind.toUpperCase());
        } catch (IllegalArgumentException e) {
            src.sendError(Text.literal(kind + "?"));
            return 0;
        }
        var ow = src.getServer().getOverworld();
        int x, z;
        if (k == com.abilitywar.world.RpgStructures.Kind.CAPITAL || k == com.abilitywar.world.RpgStructures.Kind.CAPITAL_LANDS) {
            if (com.abilitywar.world.RpgWorld.capital == null) {
                src.sendError(Text.translatable("rpg.abilitywar.no_capital"));
                return 0;
            }
            x = ow.getSpawnPos().getX();
            z = ow.getSpawnPos().getZ();
        } else {
            var reg = ow.getRegistryManager().get(net.minecraft.registry.RegistryKeys.STRUCTURE);
            var entry = reg.getEntry(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.STRUCTURE, com.abilitywar.AbilityWar.id(kind)));
            if (entry.isEmpty()) return 0;
            var from = p.getWorld() == ow ? p.getBlockPos() : ow.getSpawnPos();
            var res = ow.getChunkManager().getChunkGenerator().locateStructure(ow, net.minecraft.registry.entry.RegistryEntryList.of(entry.get()), from, 100, false);
            if (res == null) {
                src.sendError(Text.translatable("rpg.abilitywar.place_not_found"));
                return 0;
            }
            x = res.getFirst().getX() + 8;
            z = res.getFirst().getZ() + 8 + k.radius - 6;
        }
        ow.getChunk(x >> 4, z >> 4);
        int y = ow.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        p.teleport(ow, x + 0.5, y, z + 0.5, 180f, 10f);
        int fx = x, fz = z;
        src.sendFeedback(() -> Text.translatable("rpg.abilitywar.goto", Text.translatable(k == com.abilitywar.world.RpgStructures.Kind.TOWN ? "region.abilitywar.town.generic" : "region.abilitywar." + kind), fx, y, fz), false);
        return 1;
    }

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> GUILD_RANKS = (ctx, b) -> {
        java.util.List<String> l = new java.util.ArrayList<>(java.util.List.of(com.abilitywar.rpg.Guild.RANKS));
        l.add("none");
        return net.minecraft.command.CommandSource.suggestMatching(l, b);
    };

    private static int guildRank(CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> ps) {
        String r = StringArgumentType.getString(ctx, "rank").toUpperCase();
        int rank = r.equals("NONE") ? -1 : java.util.Arrays.asList(com.abilitywar.rpg.Guild.RANKS).indexOf(r);
        if (rank < -1 || (rank == -1 && !r.equals("NONE"))) {
            ctx.getSource().sendError(Text.literal(r + "?"));
            return 0;
        }
        return rpgEach(ps, p -> com.abilitywar.rpg.RpgManager.setGuildRank(p, rank));
    }

    private static int rpgEach(Collection<ServerPlayerEntity> ps, java.util.function.Consumer<ServerPlayerEntity> f) {
        for (ServerPlayerEntity p : ps) f.accept(p);
        return ps.size();
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> d) {
        var root = literal("abilitywar")
                .then(literal("list").executes(ctx -> {
                    for (Ability a : Abilities.all()) {
                        ctx.getSource().sendFeedback(() -> Text.literal(" - " + a.key + " : ").append(Text.translatable(a.translationKey())), false);
                    }
                    return Abilities.all().size();
                }))
                .then(literal("set")
                        .then(argument("ability", StringArgumentType.word()).suggests(ABILITIES)
                                .executes(ctx -> set(ctx, List.of(ctx.getSource().getPlayerOrThrow())))
                                .then(argument("targets", EntityArgumentType.players()).requires(s -> s.hasPermissionLevel(2))
                                        .executes(ctx -> set(ctx, EntityArgumentType.getPlayers(ctx, "targets"))))))
                .then(literal("clear")
                        .executes(ctx -> clear(List.of(ctx.getSource().getPlayerOrThrow())))
                        .then(argument("targets", EntityArgumentType.players()).requires(s -> s.hasPermissionLevel(2))
                                .executes(ctx -> clear(EntityArgumentType.getPlayers(ctx, "targets")))))
                .then(literal("level").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, com.abilitywar.rpg.RpgManager.MAX_LEVEL))
                                .executes(ctx -> rpgEach(List.of(ctx.getSource().getPlayerOrThrow()), p -> com.abilitywar.rpg.RpgManager.setLevel(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level"))))
                                .then(argument("targets", EntityArgumentType.players())
                                        .executes(ctx -> rpgEach(EntityArgumentType.getPlayers(ctx, "targets"), p -> com.abilitywar.rpg.RpgManager.setLevel(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level")))))))
                .then(literal("xp").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(ctx -> rpgEach(List.of(ctx.getSource().getPlayerOrThrow()), p -> com.abilitywar.rpg.RpgManager.addXp(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"))))
                                .then(argument("targets", EntityArgumentType.players())
                                        .executes(ctx -> rpgEach(EntityArgumentType.getPlayers(ctx, "targets"), p -> com.abilitywar.rpg.RpgManager.addXp(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(literal("stats").requires(s -> s.hasPermissionLevel(2))
                        .then(literal("reset")
                                .executes(ctx -> rpgEach(List.of(ctx.getSource().getPlayerOrThrow()), com.abilitywar.rpg.RpgManager::resetStats))
                                .then(argument("targets", EntityArgumentType.players())
                                        .executes(ctx -> rpgEach(EntityArgumentType.getPlayers(ctx, "targets"), com.abilitywar.rpg.RpgManager::resetStats)))))
                .then(literal("quest").requires(s -> s.hasPermissionLevel(2))
                        .then(literal("complete").executes(ctx -> rpgEach(List.of(ctx.getSource().getPlayerOrThrow()), com.abilitywar.rpg.RpgManager::forceProgress))
                                .then(argument("targets", EntityArgumentType.players()).executes(ctx -> rpgEach(EntityArgumentType.getPlayers(ctx, "targets"), com.abilitywar.rpg.RpgManager::forceProgress))))
                        .then(literal("abandon").executes(ctx -> rpgEach(List.of(ctx.getSource().getPlayerOrThrow()), com.abilitywar.rpg.RpgManager::abandon))
                                .then(argument("targets", EntityArgumentType.players()).executes(ctx -> rpgEach(EntityArgumentType.getPlayers(ctx, "targets"), com.abilitywar.rpg.RpgManager::abandon)))))
                .then(literal("grand").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(ctx -> rpgEach(List.of(ctx.getSource().getPlayerOrThrow()), p -> com.abilitywar.rpg.RpgManager.setGrand(p, com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on"))))
                                .then(argument("targets", EntityArgumentType.players())
                                        .executes(ctx -> rpgEach(EntityArgumentType.getPlayers(ctx, "targets"), p -> com.abilitywar.rpg.RpgManager.setGrand(p, com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on")))))))
                .then(literal("boss").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("boss", net.minecraft.command.argument.IdentifierArgumentType.identifier()).suggests(BOSSES)
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    var id = net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(ctx, "boss");
                                    var e = com.abilitywar.mob.BossSpawner.spawnNear(p.getServerWorld(), p, id);
                                    return e == null ? 0 : 1;
                                })))
                .then(literal("altar").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("boss", net.minecraft.command.argument.IdentifierArgumentType.identifier()).suggests(BOSSES)
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    var pos = p.getBlockPos().down();
                                    p.getServerWorld().setBlockState(pos, com.abilitywar.mob.BossAltar.BLOCK.getDefaultState());
                                    com.abilitywar.mob.BossAltar.setBoss(p.getServerWorld(), pos, net.minecraft.command.argument.IdentifierArgumentType.getIdentifier(ctx, "boss"));
                                    return 1;
                                })))
                .then(literal("guild").requires(s -> s.hasPermissionLevel(2))
                        .then(literal("rank").then(argument("rank", StringArgumentType.word()).suggests(GUILD_RANKS)
                                .executes(ctx -> guildRank(ctx, List.of(ctx.getSource().getPlayerOrThrow())))
                                .then(argument("targets", EntityArgumentType.players()).executes(ctx -> guildRank(ctx, EntityArgumentType.getPlayers(ctx, "targets"))))))
                        .then(literal("points").then(argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .executes(ctx -> rpgEach(List.of(ctx.getSource().getPlayerOrThrow()), p -> com.abilitywar.rpg.RpgManager.addGuildPoints(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"))))
                                .then(argument("targets", EntityArgumentType.players()).executes(ctx -> rpgEach(EntityArgumentType.getPlayers(ctx, "targets"),
                                        p -> com.abilitywar.rpg.RpgManager.addGuildPoints(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount")))))))
                        .then(literal("complete").executes(ctx -> rpgEach(List.of(ctx.getSource().getPlayerOrThrow()), com.abilitywar.rpg.RpgManager::guildForceProgress))
                                .then(argument("targets", EntityArgumentType.players()).executes(ctx -> rpgEach(EntityArgumentType.getPlayers(ctx, "targets"), com.abilitywar.rpg.RpgManager::guildForceProgress))))
                        .then(literal("clerk").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            var npc = com.abilitywar.registry.AWEntities.GUILD_CLERK.create(p.getServerWorld());
                            if (npc == null) return 0;
                            npc.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw() + 180, 0);
                            npc.setHeadYaw(p.getYaw() + 180);
                            npc.setBodyYaw(p.getYaw() + 180);
                            p.getServerWorld().spawnEntity(npc);
                            return 1;
                        })))
                .then(literal("party")
                        .then(literal("invite").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                            String err = com.abilitywar.social.Requests.send(ctx.getSource().getPlayerOrThrow(), EntityArgumentType.getPlayer(ctx, "player"),
                                    com.abilitywar.social.Requests.Type.PARTY);
                            if (err != null) ctx.getSource().sendError(Text.translatable(err));
                            return err == null ? 1 : 0;
                        })))
                        .then(literal("leave").executes(ctx -> {
                            com.abilitywar.social.Party.leave(ctx.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(literal("list").executes(ctx -> {
                            com.abilitywar.social.Party.list(ctx.getSource().getPlayerOrThrow());
                            return 1;
                        })))
                .then(literal("trade").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    String err = com.abilitywar.social.Requests.send(ctx.getSource().getPlayerOrThrow(), EntityArgumentType.getPlayer(ctx, "player"),
                            com.abilitywar.social.Requests.Type.TRADE);
                    if (err != null) ctx.getSource().sendError(Text.translatable(err));
                    return err == null ? 1 : 0;
                })))
                .then(literal("request").then(argument("answer", StringArgumentType.word()).suggests((c, sb) -> net.minecraft.command.CommandSource.suggestMatching(List.of("accept", "decline"), sb))
                        .then(argument("type", StringArgumentType.word()).suggests((c, sb) -> net.minecraft.command.CommandSource.suggestMatching(List.of("party", "trade"), sb))
                                .executes(ctx -> request(ctx, null))
                                .then(argument("from", StringArgumentType.word()).executes(ctx -> request(ctx, StringArgumentType.getString(ctx, "from")))))))
                .then(literal("npc").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("type", StringArgumentType.word()).suggests((c, sb) -> net.minecraft.command.CommandSource.suggestMatching(NPCS.keySet(), sb))
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    var type = NPCS.get(StringArgumentType.getString(ctx, "type"));
                                    if (type == null) return 0;
                                    var npc = type.create(p.getServerWorld());
                                    if (npc == null) return 0;
                                    npc.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw() + 180, 0);
                                    npc.setHeadYaw(p.getYaw() + 180);
                                    if (npc instanceof net.minecraft.entity.LivingEntity le) le.setBodyYaw(p.getYaw() + 180);
                                    p.getServerWorld().spawnEntity(npc);
                                    return 1;
                                })))
                .then(literal("gold").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            ctx.getSource().sendFeedback(() -> Text.translatable("gold.abilitywar.have", com.abilitywar.rpg.Shops.fmt(com.abilitywar.rpg.RpgManager.gold(p))), false);
                            return 1;
                        })
                        .then(argument("op", StringArgumentType.word()).requires(s -> s.hasPermissionLevel(2))
                                .suggests((c, sb) -> net.minecraft.command.CommandSource.suggestMatching(List.of("add", "set", "take"), sb))
                                .then(argument("amount", com.mojang.brigadier.arguments.LongArgumentType.longArg(0, 999_999_999L))
                                        .executes(ctx -> goldOp(ctx, List.of(ctx.getSource().getPlayerOrThrow())))
                                        .then(argument("targets", EntityArgumentType.players()).executes(ctx -> goldOp(ctx, EntityArgumentType.getPlayers(ctx, "targets")))))))
                .then(literal("enhance").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, com.abilitywar.gear.GearEnhance.BEYOND_MAX)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            var st = p.getMainHandStack();
                            if (!(st.getItem() instanceof com.abilitywar.gear.GearItem)) {
                                ctx.getSource().sendError(Text.translatable("smith.abilitywar.err.no_item"));
                                return 0;
                            }
                            com.abilitywar.gear.GearEnhance.set(st, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level"));
                            ctx.getSource().sendFeedback(() -> st.getName(), false);
                            return 1;
                        })))
                .then(literal("awaken").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("stars", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, com.abilitywar.gear.GearEnhance.AWAKEN_MAX)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            var st = p.getMainHandStack();
                            if (!(st.getItem() instanceof com.abilitywar.gear.GearItem)) {
                                ctx.getSource().sendError(Text.translatable("smith.abilitywar.err.no_item"));
                                return 0;
                            }
                            int a = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "stars");
                            if (a > 0) com.abilitywar.gear.GearEnhance.set(st, Math.max(com.abilitywar.gear.GearEnhance.level(st), com.abilitywar.gear.GearEnhance.MAX));
                            com.abilitywar.gear.GearEnhance.setAwaken(st, a);
                            ctx.getSource().sendFeedback(() -> st.getName(), false);
                            return 1;
                        })))
                // ? 장비 끝까지 시험용: /aw stage <0 기본 | 1 초월+ | 2 정제 | 3 초정제> (손에 든 ? 장비)
                .then(literal("stage").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("stage", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, com.abilitywar.gear.GearEnhance.SUPER_REFINE)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            var st = p.getMainHandStack();
                            if (com.abilitywar.gear.GearEnhance.gradeOf(st) != com.abilitywar.gear.GearGrade.UNKNOWN) {
                                ctx.getSource().sendError(Text.translatable("smith.abilitywar.err.stage_unknown"));
                                return 0;
                            }
                            com.abilitywar.gear.GearEnhance.setStage(st, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "stage"));
                            ctx.getSource().sendFeedback(() -> st.getName(), false);
                            return 1;
                        })))
                .then(literal("dungeon")
                        .then(literal("start").then(argument("theme", StringArgumentType.word())
                                .suggests((c, sb) -> CommandSource.suggestMatching(java.util.Arrays.stream(com.abilitywar.rpg.InstanceDungeon.Theme.values()).map(t -> t.key), sb))
                                .executes(ctx -> dungeonStart(ctx.getSource(), StringArgumentType.getString(ctx, "theme"), "normal"))
                                .then(argument("difficulty", StringArgumentType.word())
                                        .suggests((c, sb) -> CommandSource.suggestMatching(List.of("normal", "hard", "hell"), sb))
                                        .executes(ctx -> dungeonStart(ctx.getSource(), StringArgumentType.getString(ctx, "theme"), StringArgumentType.getString(ctx, "difficulty"))))))
                        .then(literal("leave").executes(ctx -> com.abilitywar.rpg.InstanceDungeon.leave(ctx.getSource().getPlayerOrThrow()) ? 1 : 0))
                        .then(literal("next").requires(s -> s.hasPermissionLevel(2))
                                .executes(ctx -> com.abilitywar.rpg.InstanceDungeon.debugNext(ctx.getSource().getPlayerOrThrow(), null) ? 1 : 0)
                                .then(argument("kind", StringArgumentType.word()).suggests((c, sb) -> CommandSource.suggestMatching(List.of("trap", "treasure", "mid", "boss", "start"), sb))
                                        .executes(ctx -> com.abilitywar.rpg.InstanceDungeon.debugNext(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "kind")) ? 1 : 0)))
                        .then(literal("list").executes(ctx -> {
                            for (var t : com.abilitywar.rpg.InstanceDungeon.Theme.values()) {
                                ctx.getSource().sendFeedback(() -> Text.literal("• ").append(Text.translatable("dungeon.abilitywar.name." + t.key).styled(st -> st.withColor(t.color)))
                                        .append(Text.literal("  " + t.key + "  ").formatted(Formatting.DARK_GRAY))
                                        .append(Text.translatable("dungeon.abilitywar.list_line", t.level, com.abilitywar.rpg.Shops.fmt(t.fee)).formatted(Formatting.GRAY)), false);
                            }
                            return 1;
                        })))
                .then(literal("tower")
                        .then(literal("start").executes(ctx -> towerStart(ctx.getSource(), 1))
                                .then(argument("floor", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(ctx ->
                                        towerStart(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "floor")))))
                        .then(literal("leave").executes(ctx -> com.abilitywar.rpg.AbyssTower.leave(ctx.getSource().getPlayerOrThrow()) ? 1 : 0))
                        .then(literal("top").executes(ctx -> {
                            var l = com.abilitywar.rpg.AbyssTower.top(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.translatable("tower.abilitywar.top").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
                            for (Text t : l) ctx.getSource().sendFeedback(() -> t, false);
                            return l.size();
                        }))
                        .then(literal("best").requires(s -> s.hasPermissionLevel(2))
                                .then(argument("floor", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(ctx -> rpgEach(List.of(ctx.getSource().getPlayerOrThrow()), p -> com.abilitywar.rpg.AbyssTower.setBest(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "floor"))))
                                        .then(argument("targets", EntityArgumentType.players()).executes(ctx -> rpgEach(EntityArgumentType.getPlayers(ctx, "targets"),
                                                p -> com.abilitywar.rpg.AbyssTower.setBest(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "floor"))))))))
                .then(literal("goto").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("place", StringArgumentType.word()).suggests(PLACES)
                                .executes(ctx -> gotoPlace(ctx.getSource(), StringArgumentType.getString(ctx, "place")))))
                .then(literal("capital").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    var cap = com.abilitywar.world.RpgWorld.capital;
                    if (cap == null) {
                        ctx.getSource().sendError(Text.translatable("rpg.abilitywar.no_capital"));
                        return 0;
                    }
                    var ow = ctx.getSource().getServer().getOverworld();
                    var sp = ow.getSpawnPos();
                    p.teleport(ow, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 180f, 0f);
                    ctx.getSource().sendFeedback(() -> Text.translatable("rpg.abilitywar.capital_tp", cap.getX(), cap.getZ()), false);
                    return 1;
                }))
                .then(literal("king").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    var npc = com.abilitywar.registry.AWEntities.KING.create(p.getServerWorld());
                    if (npc == null) return 0;
                    npc.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw() + 180, 0);
                    npc.setHeadYaw(p.getYaw() + 180);
                    npc.setBodyYaw(p.getYaw() + 180);
                    p.getServerWorld().spawnEntity(npc);
                    ctx.getSource().sendFeedback(() -> Text.translatable("rpg.abilitywar.npc_spawned"), false);
                    return 1;
                }))
                .then(literal("npc").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    var npc = com.abilitywar.registry.AWEntities.JOB_MASTER.create(p.getServerWorld());
                    if (npc == null) return 0;
                    npc.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw() + 180, 0);
                    npc.setHeadYaw(p.getYaw() + 180);
                    npc.setBodyYaw(p.getYaw() + 180);
                    p.getServerWorld().spawnEntity(npc);
                    ctx.getSource().sendFeedback(() -> Text.translatable("rpg.abilitywar.npc_spawned"), false);
                    return 1;
                }))
                .then(literal("gear").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("grade", StringArgumentType.word()).suggests(GRADES)
                                .executes(ctx -> gear(ctx, "all", 1))
                                .then(argument("kind", StringArgumentType.word()).suggests((c, sb) -> CommandSource.suggestMatching(List.of("weapon", "armor", "set", "all"), sb))
                                        .executes(ctx -> gear(ctx, StringArgumentType.getString(ctx, "kind"), 1))
                                        .then(argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 36))
                                                .executes(ctx -> gear(ctx, StringArgumentType.getString(ctx, "kind"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(literal("charge").requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> charge(List.of(ctx.getSource().getPlayerOrThrow()), 100))
                        .then(argument("targets", EntityArgumentType.players())
                                .executes(ctx -> charge(EntityArgumentType.getPlayers(ctx, "targets"), 100))
                                .then(argument("amount", FloatArgumentType.floatArg(0, 100))
                                        .executes(ctx -> charge(EntityArgumentType.getPlayers(ctx, "targets"), FloatArgumentType.getFloat(ctx, "amount"))))))
                .then(literal("reset").requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> reset(List.of(ctx.getSource().getPlayerOrThrow())))
                        .then(argument("targets", EntityArgumentType.players())
                                .executes(ctx -> reset(EntityArgumentType.getPlayers(ctx, "targets")))));
        var node = d.register(root);
        d.register(literal("aw").redirect(node));
        d.register(literal("능력").redirect(node));
    }

    private static int set(CommandContext<ServerCommandSource> ctx, Collection<ServerPlayerEntity> players) throws CommandSyntaxException {
        String key = StringArgumentType.getString(ctx, "ability");
        Ability a = Abilities.byString(key);
        if (a == null) {
            ctx.getSource().sendError(Text.translatable("abilitywar.msg.unknown", key));
            return 0;
        }
        for (ServerPlayerEntity p : players) AbilityManager.setAbility(p, a);
        return players.size();
    }

    private static int clear(Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) AbilityManager.setAbility(p, null);
        return players.size();
    }

    private static int charge(Collection<ServerPlayerEntity> players, float amount) {
        for (ServerPlayerEntity p : players) {
            AbilityData d = AbilityManager.data(p);
            d.ult = Math.min(AbilityData.ULT_MAX, amount);
            d.dirty = true;
            AWNet.sendState(p);
        }
        return players.size();
    }

    private static int reset(Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            AbilityData d = AbilityManager.data(p);
            d.clearCooldowns();
            AWNet.sendState(p);
        }
        return players.size();
    }

    private AWCommands() {
    }
}
