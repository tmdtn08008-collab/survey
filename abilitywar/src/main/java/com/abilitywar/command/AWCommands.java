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
