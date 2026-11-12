package com.abilitywar.mob;

import com.abilitywar.item.RpgItems;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 고블린 로드: 고블린 무리의 우두머리. 금 왕관 · 황금 도끼.
 * 뿔나팔 (고블린 2~3 마리 부름, 주변 6 마리까지) · 도약 내려찍기 · 회전 베기.
 */
public class GoblinLordEntity extends EliteEntity {
    public static final int HORN = 1, LEAP = 2, SPIN = 3;
    int count;
    Vec3d leapTo = Vec3d.ZERO;

    public GoblinLordEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.GREEN);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 150).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3).add(EntityAttributes.GENERIC_ARMOR, 6)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data, @Nullable NbtCompound nbt) {
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.1f);
        return super.initialize(world, difficulty, reason, data, nbt);
    }

    @Override
    protected double chaseSpeed() {
        return 1.15;
    }

    @Override
    protected int pickAttack(LivingEntity t, double dist) {
        count++;
        long goblins = getWorld().getEntitiesByClass(GoblinEntity.class, getBoundingBox().expand(16), e -> e.isAlive()).size();
        if (count % 4 == 1 && goblins < 6) return HORN;
        if (dist > 5 && dist < 14) return LEAP;
        if (dist < 4) return SPIN;
        return 0;
    }

    @Override
    protected int restAfter(int id) {
        return enraged() ? 40 : 60;
    }

    @Override
    protected boolean runAttack(int id, int t, @Nullable LivingEntity target) {
        ServerWorld w = (ServerWorld) getWorld();
        switch (id) {
            case HORN -> {
                getNavigation().stop();
                if (t == 1) playSound(SoundEvents.EVENT_RAID_HORN.value(), 3f, 1.4f);
                if (t == 14) {
                    int n = 2 + random.nextInt(2) + (enraged() ? 1 : 0);
                    for (int i = 0; i < n; i++) {
                        GoblinEntity g = AWMobs.GOBLIN.create(w);
                        if (g == null) continue;
                        double a = random.nextDouble() * Math.PI * 2;
                        BlockPos p = BlockPos.ofFloored(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3);
                        p = w.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p);
                        g.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360, 0);
                        g.initialize(w, w.getLocalDifficulty(p), SpawnReason.REINFORCEMENT, null, null);
                        if (target != null) g.setTarget(target);
                        w.spawnEntityAndPassengers(g);
                        w.spawnParticles(net.minecraft.particle.ParticleTypes.POOF, g.getX(), g.getY() + 0.5, g.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
                    }
                }
                return t >= 24;
            }
            case LEAP -> {
                if (t == 1) {
                    if (target == null) return true;
                    leapTo = target.getPos();
                    warn(leapTo, 3.5f, 18, 0x8AD040);
                    Vec3d d = leapTo.subtract(getPos());
                    setVelocity(d.x * 0.16, 0.9, d.z * 0.16);
                    velocityModified = true;
                    playSound(SoundEvents.ENTITY_GOAT_LONG_JUMP, 2f, 0.8f);
                }
                if (t > 4 && (isOnGround() || t > 30)) {
                    MobFx.slam(w, getPos(), 3.5f, 0x7A9A40);
                    playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2f, 1.3f);
                    for (LivingEntity v : victims(getPos(), 3.5)) {
                        hurt(v, 10, false);
                        push(v, getPos(), 0.7, 0.5);
                    }
                    return true;
                }
                return false;
            }
            case SPIN -> {
                getNavigation().stop();
                setYaw(getYaw() + 40);
                setBodyYaw(getYaw());
                if (t % 5 == 0) {
                    swingHand(t % 10 == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND);
                    playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.8f);
                    w.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1, getZ(), 3, 1.2, 0.2, 1.2, 0);
                    for (LivingEntity v : victims(getPos().add(0, 1, 0), 3.2)) {
                        hurt(v, 4, false);
                        push(v, getPos(), 0.3, 0.15);
                    }
                }
                return t >= 30;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    protected SoundEvent introSound() {
        return SoundEvents.EVENT_RAID_HORN.value();
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.GOLD_INGOT, 1, 3);
        drop(Items.EMERALD, 0, 2);
    }

    void drop(Item item, int min, int max) {
        int n = min + random.nextInt(max - min + 1);
        if (n > 0) dropStack(new ItemStack(item, n));
    }

    @Override
    public Item trophy() {
        return RpgItems.LORD_EMBLEM;
    }

    @Override
    public int trophyChance() {
        return 60;
    }

    @Override
    public int trophyCount(Random r) {
        return 1;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_PIGLIN_BRUTE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PIGLIN_BRUTE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PIGLIN_BRUTE_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 1.25f;
    }
}
