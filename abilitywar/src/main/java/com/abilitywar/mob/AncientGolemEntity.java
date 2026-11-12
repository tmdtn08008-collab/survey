package com.abilitywar.mob;

import com.abilitywar.combat.Combat;
import com.abilitywar.item.RpgItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** 고대 골렘: 정글 · 산 · 사막 유적의 이끼 낀 돌 골렘. 느리고 아주 아픔, 맞으면 하늘로 날아감. 가끔 땅을 굴러 주변을 흔듦. */
public class AncientGolemEntity extends AWMonster implements TrophyMob, GolemLike {
    int attackTicks, stompCd = 120;

    public AncientGolemEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 18;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 100).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 13)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22).add(EntityAttributes.GENERIC_ARMOR, 10)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24);
    }

    @Override
    protected double attackSpeed() {
        return 1.0;
    }

    @Override
    public int attackTicks() {
        return attackTicks;
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (attackTicks > 0) attackTicks--;
    }

    @Override
    public boolean tryAttack(Entity target) {
        attackTicks = 10;
        getWorld().sendEntityStatus(this, (byte) 4);
        boolean ok = super.tryAttack(target);
        if (ok) {
            target.setVelocity(target.getVelocity().add(0, 0.55, 0));
            target.velocityModified = true;
        }
        playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.7f);
        return ok;
    }

    @Override
    public void handleStatus(byte status) {
        if (status == 4) {
            attackTicks = 10;
            playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.7f);
        } else super.handleStatus(status);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        LivingEntity t = getTarget();
        if (t == null || --stompCd > 0 || distanceTo(t) > 4 || !isOnGround()) return;
        stompCd = 140 + random.nextInt(60);
        attackTicks = 10;
        getWorld().sendEntityStatus(this, (byte) 4);
        MobFx.slam(w, getPos(), 3.5f, 0x7A8A60);
        playSound(SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, 1.4f, 0.5f);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(getBlockPos()).expand(3.5), e -> e instanceof PlayerEntity p && !p.isCreative() && !p.isSpectator())) {
            Combat.damage(e, getDamageSources().mobAttack(this), 7, com.abilitywar.net.AWNet.DMG_SKILL);
            Vec3d d = e.getPos().subtract(getPos());
            Combat.setVelocity(e, new Vec3d(d.x, 0, d.z).normalize().multiply(0.6).add(0, 0.5, 0));
        }
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.MOSSY_COBBLESTONE, 1, 3);
        drop(Items.IRON_INGOT, 0, 2);
    }

    @Override
    protected float gearChance() {
        return 0.08f;
    }

    @Override
    public Item trophy() {
        return RpgItems.GOLEM_CORE;
    }

    @Override
    public int trophyChance() {
        return 25;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLOCK_STONE_HIT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        playSound(SoundEvents.ENTITY_IRON_GOLEM_STEP, 1f, 0.7f);
    }

    @Override
    public float getSoundPitch() {
        return 0.6f;
    }
}
