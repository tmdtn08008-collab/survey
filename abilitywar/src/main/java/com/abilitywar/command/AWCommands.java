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
