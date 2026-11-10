package com.abilitywar.rpg;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** 전직 교관: 오른쪽 클릭하면 전직 퀘스트 화면. 움직이지 않고, 다치지 않고, 사라지지 않음. */
public class JobMasterEntity extends PathAwareEntity {
    public JobMasterEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
        setInvulnerable(true);
        setCustomName(Text.translatable(nameKey()));
        setCustomNameVisible(true);
    }

    /** 이 NPC 가 주는 퀘스트 (JobQuests.CLASS_MASTER / KING). */
    public String giver() {
        return JobQuests.CLASS_MASTER;
    }

    protected String nameKey() {
        return "entity.abilitywar.job_master";
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 100).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 10f, 1f));
        goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp) RpgManager.openNpc(sp, getId());
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // /kill 과 공허는 통과 (관리자가 지울 수 있게)
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL) || source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD))
            return super.damage(source, amount);
        // 창조 모드 플레이어가 웅크리고 때리면 제거
        if (source.getAttacker() instanceof PlayerEntity p && p.isCreative() && p.isSneaking()) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(net.minecraft.entity.Entity entity) {
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
    }
}
