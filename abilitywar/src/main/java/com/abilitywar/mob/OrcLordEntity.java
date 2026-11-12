package com.abilitywar.mob;

import com.abilitywar.item.RpgItems;
import com.abilitywar.net.AWNet;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * 오크 로드: 오크 부족장. 크고 단단함, 큰 도끼.
 * 전투의 함성 (주변 오크 힘 · 속도 + 플레이어 느려짐) · 대지 가르기 (경고 원 후 큰 충격파) · 돌진.
 */
public class OrcLordEntity extends EliteEntity {
    public static final int WARCRY = 1, SLAM = 2, CHARGE = 3;
    int count;
    Vec3d chargeDir = Vec3d.ZERO;
    final Set<Integer> hitOnce = new HashSet<>();

    public OrcLordEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.RED);
        this.experiencePoints = 60;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 260).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27).add(EntityAttributes.GENERIC_ARMOR, 10)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data, @Nullable NbtCompound nbt) {
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.05f);
        return super.initialize(world, difficulty, reason, data, nbt);
    }

    @Override
    protected void onEnrage() {
        attack = WARCRY;
        attackTick = 0;
        startAnim(WARCRY);
    }

    @Override
    protected int pickAttack(LivingEntity t, double dist) {
        count++;
        if (count % 5 == 1) return WARCRY;
        if (dist > 6 && dist < 18) return CHARGE;
        if (dist < 5) return SLAM;
        return 0;
    }

    @Override
    protected int restAfter(int id) {
        return enraged() ? 36 : 56;
    }

    @Override
    protected boolean runAttack(int id, int t, @Nullable LivingEntity target) {
        ServerWorld w = (ServerWorld) getWorld();
        switch (id) {
            case WARCRY -> {
                getNavigation().stop();
                if (t == 1) playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 3f, 0.7f);
                if (t == 10) {
                    MobFx.ring(w, this, 10, 0xD04020);
                    for (OrcEntity o : w.getEntitiesByClass(OrcEntity.class, getBoundingBox().expand(16), e -> e.isAlive())) {
                        o.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0));
                        o.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0));
                        if (target != null) o.setTarget(target);
                    }
                    addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 160, 0));
                    for (PlayerEntity p : w.getEntitiesByClass(PlayerEntity.class, getBoundingBox().expand(10), this::hits)) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0), this);
                        if (p instanceof net.minecraft.server.network.ServerPlayerEntity sp) AWNet.shake(sp, 0.6f, 12);
                    }
                }
                return t >= 22;
            }
            case SLAM -> {
                getNavigation().stop();
                if (t == 1) {
                    warn(getPos(), 5.5f, 18, 0xFF5030);
                    playSound(SoundEvents.ENTITY_RAVAGER_ATTACK, 2f, 0.6f);
                }
                if (t == 12) swingHand(Hand.MAIN_HAND);
                if (t == 18) {
                    MobFx.slam(w, getPos(), 5.5f, 0x9A7A50);
                    playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2f, 0.5f);
                    playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.4f, 0.8f);
                    AWNet.shakeAround(w, getPos(), 0.7f, 12, 16);
                    for (LivingEntity v : victims(getPos(), 5.5)) {
                        hurt(v, 15, false);
                        push(v, getPos(), 0.9, 0.7);
                    }
                }
                return t >= 28;
            }
            case CHARGE -> {
                if (t == 1) {
                    if (target == null) return true;
                    hitOnce.clear();
                    Vec3d d = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ());
                    chargeDir = d.lengthSquared() < 1e-3 ? forward() : d.normalize();
                    faceTarget(target);
                    playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 2f, 1.1f);
                }
                if (t < 10) {
                    getNavigation().stop();
                    return false;
                }
                if (t <= 26) {
                    setVelocity(chargeDir.x * 0.85, getVelocity().y, chargeDir.z * 0.85);
                    velocityModified = true;
                    if (t % 3 == 0) w.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 4, 0.5, 0.1, 0.5, 0.02);
                    for (LivingEntity v : victims(getPos().add(0, 1, 0), 2.2)) {
                        if (!hitOnce.add(v.getId())) continue;
                        hurt(v, 13, false);
                        push(v, getPos(), 1.3, 0.6);
                        playSound(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
                    }
                    return horizontalCollision && t > 12;
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    protected SoundEvent introSound() {
        return SoundEvents.ENTITY_RAVAGER_ROAR;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        dropStack(new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(4)));
        dropStack(new ItemStack(RpgItems.ORC_TUSK, 1 + random.nextInt(3)));
    }

    @Override
    public Item trophy() {
        return RpgItems.LORD_EMBLEM;
    }

    @Override
    public int trophyChance() {
        return 85;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_HOGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_HOGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_HOGLIN_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 0.6f;
    }
}
