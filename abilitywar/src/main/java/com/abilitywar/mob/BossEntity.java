package com.abilitywar.mob;

import com.abilitywar.combat.Combat;
import com.abilitywar.gear.GearGrade;
import com.abilitywar.gear.GearItems;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWDamage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 보스 공통: 보스 체력바, 등장 연출, 패턴 (공격 하나씩 순서대로), 2페이즈 (체력 50% 이하), 집 (제단) 에 묶임,
 * 야생 보스는 아무도 없으면 사라짐, 죽으면 장비·경험치.
 *
 * <p>클라이언트 동작: ANIM (공격 번호) + ANIM_AT (시작한 나이) 를 보고 렌더러가 자세를 정함.
 */
public abstract class BossEntity extends HostileEntity {
    static final TrackedData<Integer> ANIM = DataTracker.registerData(BossEntity.class, TrackedDataHandlerRegistry.INTEGER);
    static final TrackedData<Integer> ANIM_AT = DataTracker.registerData(BossEntity.class, TrackedDataHandlerRegistry.INTEGER);
    static final TrackedData<Boolean> ENRAGED = DataTracker.registerData(BossEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    protected final ServerBossBar bar;
    /** 진행 중인 공격 (0 = 없음) 과 그 공격의 경과 틱. */
    protected int attack, attackTick, cooldown = 40;
    /** 제단에서 나온 보스면 그 자리 (멀리 가면 돌아감). */
    @Nullable
    protected BlockPos home;
    protected boolean wild;
    int idleTicks;
    boolean introDone;

    protected BossEntity(EntityType<? extends HostileEntity> type, World world, BossBar.Color color) {
        super(type, world);
        this.bar = new ServerBossBar(getDisplayName(), color, BossBar.Style.NOTCHED_10);
        this.experiencePoints = 200;
        setPersistent();
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(ANIM, 0);
        dataTracker.startTracking(ANIM_AT, 0);
        dataTracker.startTracking(ENRAGED, false);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 24f));
        goalSelector.add(9, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    // ================================================================ 상태 (클라이언트도 읽음)
    public int anim() {
        return dataTracker.get(ANIM);
    }

    /** 지금 동작이 시작된 뒤 지난 틱 (클라이언트 보간용). */
    public float animTime(float td) {
        return age + td - dataTracker.get(ANIM_AT);
    }

    public boolean enraged() {
        return dataTracker.get(ENRAGED);
    }

    protected void startAnim(int id) {
        dataTracker.set(ANIM, id);
        dataTracker.set(ANIM_AT, age);
    }

    // ================================================================ 설정
    public void setHome(BlockPos pos) {
        this.home = pos;
    }

    public void setWild(boolean wild) {
        this.wild = wild;
    }

    /** 2페이즈 시작할 때 (체력 50%). */
    protected void onEnrage() {
    }

    /** 공격 고르기: 다음 공격 번호를 돌려줌. */
    protected abstract int pickAttack(LivingEntity target, double dist);

    /** 공격 진행: true 면 끝. */
    protected abstract boolean runAttack(int id, int t, @Nullable LivingEntity target);

    /** 공격이 끝난 뒤 쉬는 틱. */
    protected int restAfter(int id) {
        return enraged() ? 18 : 30;
    }

    /** 공격 중이 아닐 때 움직임 (기본: 대상에게 걸어감). */
    protected void idleMove(LivingEntity target, double dist) {
        if (dist > 3.5) getNavigation().startMovingTo(target, 1.0);
        else getNavigation().stop();
    }

    protected abstract String introSubtitleKey();

    protected SoundEvent introSound() {
        return SoundEvents.ENTITY_WITHER_SPAWN;
    }

    // ================================================================ 틱
    @Override
    protected void mobTick() {
        super.mobTick();
        bar.setPercent(getHealth() / getMaxHealth());
        // 보스 막대 이름에 남은 HP 숫자 (바뀔 때만)
        int hpNow = (int) Math.ceil(getHealth());
        if (hpNow != barHp) {
            barHp = hpNow;
            bar.setName(barName());
        }
        if (!(getWorld() instanceof ServerWorld sw)) return;
        if (!introDone && age > 2) {
            introDone = true;
            intro(sw);
        }
        if (!enraged() && getHealth() <= getMaxHealth() * 0.5f) {
            dataTracker.set(ENRAGED, true);
            NbtCompound n = new NbtCompound();
            n.putInt("e", getId());
            AWNet.fx(sw, getPos(), "boss_enrage", n);
            playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 3f, 0.6f);
            onEnrage();
        }
        // 집에서 너무 멀어지면 돌아감 (제단 보스)
        if (home != null && squaredDistanceTo(Vec3d.ofCenter(home)) > 40 * 40 && attack == 0) {
            refreshPositionAndAngles(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5, getYaw(), 0);
            setTarget(null);
        }
        // 주변에 플레이어가 없으면: 체력 회복 / 야생 보스는 사라짐
        boolean anyone = !sw.getEntitiesByClass(ServerPlayerEntity.class, getBoundingBox().expand(48), p -> !p.isSpectator() && !p.isCreative()).isEmpty();
        if (anyone) idleTicks = 0;
        else if (++idleTicks > 20 * 30) {
            if (age % 20 == 0) heal(getMaxHealth() * 0.02f);
            if (wild && idleTicks > 20 * 60 * 5) {
                discard();
                return;
            }
        }
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || target.isSpectator() || (target instanceof PlayerEntity pl && pl.isCreative()))) {
            setTarget(null);
            target = null;
        }
        if (attack != 0) {
            attackTick++;
            if (runAttack(attack, attackTick, target)) {
                cooldown = restAfter(attack);
                attack = 0;
                startAnim(0);
            }
            return;
        }
        if (target == null) return;
        double dist = distanceTo(target);
        getLookControl().lookAt(target, 30, 30);
        if (cooldown > 0) {
            cooldown--;
            idleMove(target, dist);
            return;
        }
        int id = pickAttack(target, dist);
        if (id != 0) {
            attack = id;
            attackTick = 0;
            getNavigation().stop();
            startAnim(id);
        } else idleMove(target, dist);
    }

    void intro(ServerWorld sw) {
        playSound(introSound(), 4f, 1f);
        NbtCompound n = new NbtCompound();
        n.putInt("e", getId());
        n.putString("name", getType().getTranslationKey());
        n.putString("sub", introSubtitleKey());
        AWNet.fx(sw, getPos(), "boss_intro", n);
        AWNet.shakeAround(sw, getPos(), 0.5f, 12, 32);
        for (ServerPlayerEntity p : sw.getEntitiesByClass(ServerPlayerEntity.class, getBoundingBox().expand(64), p -> true))
            p.sendMessage(Text.translatable("boss.abilitywar.appeared", getDisplayName()).formatted(Formatting.RED, Formatting.BOLD), false);
    }

    // ================================================================ 공격 도우미
    /** 보스가 때릴 수 있는 것: 플레이어와 플레이어 편 (다른 몬스터는 안 때림). */
    public boolean hits(Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e == this) return false;
        if (e instanceof PlayerEntity p) return !p.isSpectator() && !p.isCreative();
        return !(e instanceof Monster) || com.abilitywar.ability.warlock.Necromancy.isMinion(e);
    }

    protected List<LivingEntity> victims(Vec3d at, double r) {
        return getWorld().getEntitiesByClass(LivingEntity.class, new Box(at, at).expand(r), e -> hits(e) && e.getBoundingBox().getCenter().squaredDistanceTo(at) <= r * r + e.getWidth());
    }

    protected void hurt(LivingEntity t, float amount, boolean ult) {
        float a = enraged() ? amount * 1.2f : amount;
        Combat.damage(t, AWDamage.source(getWorld(), ult ? AWDamage.ULTIMATE : AWDamage.SKILL, this), a, ult ? AWNet.DMG_ULT : AWNet.DMG_SKILL);
    }

    protected void push(LivingEntity t, Vec3d from, double h, double up) {
        Vec3d d = t.getPos().subtract(from);
        d = new Vec3d(d.x, 0, d.z);
        if (d.lengthSquared() < 1e-4) d = new Vec3d(0, 0, 1);
        Combat.setVelocity(t, d.normalize().multiply(h).add(0, up, 0));
    }

    /** 바닥 경고 원 (빨강) — 곧 이 자리에 공격이 옴. */
    protected void warn(Vec3d at, float r, int ticks, int color) {
        NbtCompound n = new NbtCompound();
        n.putFloat("r", r);
        n.putInt("d", ticks);
        n.putInt("col", color);
        AWNet.fx((ServerWorld) getWorld(), at, "boss_warn", n);
    }

    protected Vec3d forward() {
        float yaw = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    protected void faceTarget(LivingEntity t) {
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * 57.2957795) - 90f;
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    // ================================================================ 보스 체력바 / 피해 / 죽음
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bar.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Text name) {
        super.setCustomName(name);
        bar.setName(barName());
    }

    int barHp = -1;

    /** "이름  ❤ 340 / 400". */
    net.minecraft.text.Text barName() {
        return getDisplayName().copy().append(net.minecraft.text.Text.literal("   ❤ " + (int) Math.ceil(getHealth()) + " / " + (int) Math.ceil(getMaxHealth()))
                .formatted(net.minecraft.util.Formatting.WHITE));
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean addStatusEffect(StatusEffectInstance effect, @Nullable Entity source) {
        // 기절·속박 같은 강한 효과는 보스에게 짧게만
        if (!effect.getEffectType().isBeneficial() && effect.getDuration() > 40)
            effect = new StatusEffectInstance(effect.getEffectType(), 40, Math.min(1, effect.getAmplifier()), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
        return super.addStatusEffect(effect, source);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (getWorld() instanceof ServerWorld sw) {
            NbtCompound n = new NbtCompound();
            n.putInt("e", getId());
            n.putFloat("s", getWidth());
            AWNet.fx(sw, getPos().add(0, getHeight() * 0.5, 0), "boss_death", n);
            AWNet.shakeAround(sw, getPos(), 0.8f, 20, 40);
            BossSpawner.onBossDeath(this);
        }
    }

    /** 장비 보상: 영웅 50% · 전설 30% · 신화 15% · 시크릿 5%, 2개 (+ 2페이즈까지 간 보스면 1개 더). */
    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        int n = 2 + (enraged() ? 1 : 0);
        for (int i = 0; i < n; i++) {
            float r = random.nextFloat();
            GearGrade g = r < 0.05f ? GearGrade.SECRET : r < 0.2f ? GearGrade.MYTHIC : r < 0.5f ? GearGrade.LEGENDARY : GearGrade.HERO;
            net.minecraft.item.Item item = null;
            if (random.nextBoolean()) {
                var list = GearItems.WEAPONS.get(g);
                if (list != null && !list.isEmpty()) item = list.get(random.nextInt(list.size()));
            } else {
                var sets = GearItems.SETS.get(g);
                if (sets != null && !sets.isEmpty()) {
                    var pieces = new java.util.ArrayList<>(sets.get(random.nextInt(sets.size())).pieces().values());
                    item = GearItems.BY_ID.get(pieces.get(random.nextInt(pieces.size())).id());
                }
            }
            if (item == null) continue;
            ItemStack st = new ItemStack(item);
            ItemEntity ie = dropStack(st);
            if (ie != null) ie.setGlowing(true);
        }
    }

    // ================================================================ 저장
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (home != null) nbt.put("Home", NbtHelper.fromBlockPos(home));
        nbt.putBoolean("Wild", wild);
        nbt.putBoolean("Intro", introDone);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Home")) home = NbtHelper.toBlockPos(nbt.getCompound("Home"));
        wild = nbt.getBoolean("Wild");
        introDone = nbt.getBoolean("Intro");
        bar.setName(barName());
    }
}
