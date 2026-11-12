package com.abilitywar.mob;

import com.abilitywar.item.RpgItems;
import com.abilitywar.rpg.RpgManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * 도적: 빠르고 약함. 때리면 G 를 훔쳐서 (가진 돈의 3%, 최대 500 G) 연막을 치고 달아남.
 * 쫓아가서 쓰러뜨리면 훔친 돈을 돌려받음 (+ 덤). 멀리 (40칸) 달아나면 돈과 함께 사라짐.
 */
public class ThiefEntity extends AWMonster implements TrophyMob {
    long stolen;
    int fleeTicks;
    @Nullable
    LivingEntity victim;

    public ThiefEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 7;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 18).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.36).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            public boolean canStart() {
                return fleeTicks <= 0 && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return fleeTicks <= 0 && super.shouldContinue();
            }
        });
        goalSelector.add(7, new WanderAroundFarGoal(this, 1.0));
        goalSelector.add(8, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data, @Nullable NbtCompound nbt) {
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.03f);
        return super.initialize(world, difficulty, reason, data, nbt);
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean ok = super.tryAttack(target);
        if (ok && fleeTicks <= 0 && target instanceof ServerPlayerEntity p && getWorld() instanceof ServerWorld sw) {
            long have = RpgManager.gold(p);
            long take = Math.min(500, Math.max(Math.min(have, 10), have * 3 / 100));
            if (take > 0 && RpgManager.takeGold(p, take)) {
                stolen += take;
                p.sendMessage(Text.translatable("mob.abilitywar.thief.stole", take).formatted(Formatting.GOLD), false);
            }
            victim = p;
            fleeTicks = 200;
            setTarget(null);
            addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1, false, false));
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.6, 0.6, 0.6, 0.02);
            playSound(SoundEvents.ENTITY_WITCH_CELEBRATE, 1f, 1.6f);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0), this);
        }
        return ok;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (fleeTicks > 0) {
            fleeTicks--;
            if (victim != null && age % 10 == 0) {
                Vec3d away = getPos().subtract(victim.getPos());
                away = new Vec3d(away.x, 0, away.z);
                if (away.lengthSquared() < 1e-3) away = new Vec3d(1, 0, 0);
                Vec3d to = getPos().add(away.normalize().multiply(12));
                getNavigation().startMovingTo(to.x, to.y, to.z, 1.4);
            }
            if (fleeTicks % 20 == 0) ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 4, 0.2, 0.1, 0.2, 0.01);
            // 멀리 달아나면 돈과 함께 사라짐
            if (stolen > 0 && getWorld().getClosestPlayer(this, 40) == null) {
                ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                discard();
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean ok = super.damage(source, amount);
        if (ok && fleeTicks > 0 && random.nextInt(3) == 0) fleeTicks = Math.max(fleeTicks, 60);
        return ok;
    }

    @Override
    public float getPathfindingFavor(BlockPos pos, WorldView world) {
        return 0f;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.GOLD_NUGGET, 0, 3);
        if (random.nextInt(5) == 0) drop(Items.EMERALD, 1, 2);
    }

    @Override
    public Item trophy() {
        return RpgItems.BANDIT_SEAL;
    }

    @Override
    public int trophyChance() {
        return 25;
    }

    /** 처치하면 훔친 돈을 돌려받음. */
    @Override
    public long goldBonus() {
        return stolen + 10;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return stolen <= 0;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putLong("Stolen", stolen);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        stolen = nbt.getLong("Stolen");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PILLAGER_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 1.3f;
    }
}
