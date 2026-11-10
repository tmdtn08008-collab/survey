package com.abilitywar.server;

import com.abilitywar.data.AWPlayer;
import com.abilitywar.net.AWNet;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

public final class ServerEvents {
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(AbilityManager::tickServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            com.abilitywar.ability.warlock.Necromancy.shutdown(s);
            ServerActions.clear();
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(AbilityManager::allowDamage);
        // 서버 종료 등으로 고정이 풀리지 않은 엔티티 복구
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(com.abilitywar.combat.Combat.NOGRAV_TAG)) com.abilitywar.combat.Combat.hold(entity, false);
            com.abilitywar.ability.warlock.Necromancy.onLoad(entity);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity sp) AbilityManager.onDeathOrRespawn(sp);
            com.abilitywar.ability.warlock.Necromancy.onDeath(entity, source);
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            AWPlayer o = (AWPlayer) oldPlayer;
            AWPlayer n = (AWPlayer) newPlayer;
            n.aw$setAbilityKey(o.aw$getAbilityKey());
            n.aw$data().copyFrom(o.aw$data());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            AbilityManager.onDeathOrRespawn(newPlayer);
            AWNet.sendState(newPlayer);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            AbilityManager.data(p).dirty = true;
            server.execute(() -> AWNet.sendState(p));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            ServerActions.cancelFor(p);
            AbilityManager.onDeathOrRespawn(p);
        });

        // 기절/연출 중이거나 그림자 상태면 일반 공격 불가
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            var d = AbilityManager.data(sp);
            if (AWNet.stunned(sp) || d.isFrozen() || d.shadowForm) return ActionResult.FAIL;
            return ActionResult.PASS;
        });
    }

    private ServerEvents() {
    }
}
