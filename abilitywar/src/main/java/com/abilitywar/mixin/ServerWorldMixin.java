package com.abilitywar.mixin;

import com.abilitywar.world.RpgWorld;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 수도 · 마을 안에서 생기려던 순찰대 (MobEntityMixin 이 꼬리표를 붙임) · Ultris 보스 불러내기 이벤트는 월드에 넣지 않음. */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void abilitywar$dropPatrolInTown(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity.getCommandTags().contains(RpgWorld.NO_SPAWN_TAG)) cir.setReturnValue(false);
        else if (com.abilitywar.compat.UltrisCompat.blockSpawn((ServerWorld) (Object) this, entity)) cir.setReturnValue(false);
    }
}
