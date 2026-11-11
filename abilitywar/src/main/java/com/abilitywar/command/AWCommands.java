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

    /** /aw npc 로 놓을 수 있는 NPC. */
    static final java.util.Map<String, net.minecraft.entity.EntityType<?>> NPCS = java.util.Map.of(
            "apothecary", com.abilitywar.registry.AWEntities.APOTHECARY, "blacksmith", com.abilitywar.registry.AWEntities.BLACKSMITH,
            "guild_clerk", com.abilitywar.registry.AWEntities.GUILD_CLERK, "job_master", com.abilitywar.registry.AWEntities.JOB_MASTER,
            "king", com.abilitywar.registry.AWEntities.KING);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> BOSSES = (ctx, b) ->
            net.minecraft.command.CommandSource.suggestMatching(java.util.stream.Stream.of("abilitywar:fallen_king", "abilitywar:ember_colossus", "abilitywar:frost_witch", "abilitywar:ancient_wyvern",
                    "bosses_of_mass_destruction:lich", "bosses_of_mass_destruction:obsidilith", "bosses_of_mass_destruction:gauntlet", "bosses_of_mass_destruction:void_blossom",
                    "soulsweapons:accursed_lord_boss", "soulsweapons:draugr_boss", "soulsweapons:night_shade", "soulsweapons:returning_knight", "soulsweapons:chaos_monarch",
                    "soulsweapons:moonknight", "soulsweapons:day_stalker", "soulsweapons:night_prowler", "graveyard:lich",
                    "archon:tar", "archon:ayla", "archon:leven", "archon:inigo", "archon:null")
                    .filter(id -> net.minecraft.registry.Registries.ENTITY_TYPE.containsId(new net.minecraft.util.Identifier(id))).toList(), b);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> PLACES = (ctx, b) ->
            net.minecraft.command.CommandSource.suggestMatching(java.util.Arrays.stream(com.abilitywar.world.RpgStructures.Kind.values()).map(k -> k.name().toLowerCase()).toList(), b);

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
                .then(literal("enhance").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, com.abilitywar.gear.GearEnhance.MAX)).executes(ctx -> {
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
