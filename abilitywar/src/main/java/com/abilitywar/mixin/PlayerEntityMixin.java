package com.abilitywar.mixin;

import com.abilitywar.data.AWPlayer;
import com.abilitywar.data.AbilityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements AWPlayer {
    @Unique
    private static final TrackedData<String> AW_ABILITY = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.STRING);
    @Unique
    private static final TrackedData<Byte> AW_FLAGS = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BYTE);

    @Unique
    private AbilityData aw$data;

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "createPlayerAttributes", at = @At("RETURN"))
    private static void aw$gearAttributes(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.entity.attribute.DefaultAttributeContainer.Builder> cir) {
        com.abilitywar.gear.GearAttributes.addTo(cir.getReturnValue());
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void aw$initTracker(CallbackInfo ci) {
        this.dataTracker.startTracking(AW_ABILITY, "");
        this.dataTracker.startTracking(AW_FLAGS, (byte) 0);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void aw$write(NbtCompound nbt, CallbackInfo ci) {
        aw$data().writeNbt(nbt);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void aw$read(NbtCompound nbt, CallbackInfo ci) {
        aw$data().readNbt(nbt);
    }

    @Override
    public String aw$getAbilityKey() {
        return this.dataTracker.get(AW_ABILITY);
    }

    @Override
    public void aw$setAbilityKey(String key) {
        this.dataTracker.set(AW_ABILITY, key == null ? "" : key);
    }

    @Override
    public byte aw$getFlags() {
        return this.dataTracker.get(AW_FLAGS);
    }

    @Override
    public void aw$setFlags(byte flags) {
        this.dataTracker.set(AW_FLAGS, flags);
    }

    @Override
    public AbilityData aw$data() {
        if (aw$data == null) aw$data = new AbilityData((PlayerEntity) (Object) this);
        return aw$data;
    }
}
