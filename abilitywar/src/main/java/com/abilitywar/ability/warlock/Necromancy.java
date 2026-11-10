package com.abilitywar.ability.warlock;

import com.abilitywar.combat.Combat;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWSounds;
import com.abilitywar.server.AbilityManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 흑마법사의 강령술: 하수인(해골 / 되살린 몹)과 땅에 떨어진 영혼.
 * <ul>
 *   <li>하수인은 명령 태그로 주인을 기억한다 ({@value #TAG}, "aw_owner:UUID").</li>
 *   <li>주인 = 그 플레이어. 주인 / 같은 팀 / 같은 주인의 하수인끼리는 서로 공격하지 않는다.</li>
 *   <li>하수인은 주인이 마지막으로 때린 대상(없으면 주인을 때린 대상)을 노리고, 할 일이 없으면 주인을 따라다닌다.</li>
 *   <li>되살린 몹은 체력·피해가 절반, 해골은 흑마법서에 넣어둔 장비를 그대로 입는다.</li>
 *   <li>흑마법사 또는 같은 팀이 몹을 죽이면 영혼이 10초 동안 떨어진다 (플레이어는 영혼 없음). T 로 수집.</li>
 * </ul>
 */
public final class Necromancy {
    public static final String TAG = "aw_minion", SKEL_TAG = "aw_skel", RAISED_TAG = "aw_raised";
    static final String OWNER_PREFIX = "aw_owner:", SOUL_PREFIX = "aw_soul:";
    public static final int MAX_RAISED = 6;
    public static final double PICKUP_RANGE = 12;
    public static final int SOUL_LIFE = 200;
    static final UUID HALF_HP = UUID.fromString("2b9c1f4e-6d3a-4f71-8e2c-91a7b5d0c3e4");
    public static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    static final Map<Entity, UUID> OWNER_CACHE = new WeakHashMap<>();

    /** 지금 나와 있는 하수인. */
    static final class Minion {
        final MobEntity mob;
        final UUID owner;
        final int soulId; // -1 = 해골
        boolean dismissing;

        Minion(MobEntity mob, UUID owner, int soulId) {
            this.mob = mob;
            this.owner = owner;
            this.soulId = soulId;
        }
    }

    static final Map<UUID, Minion> LIVE = new HashMap<>();
    /** 주인 → [대상 엔티티 id, 때린 시각]. */
    static final Map<UUID, long[]> FOCUS = new HashMap<>();

    // ================================================================ 태그 / 편

    public static boolean isMinion(Entity e) {
        return e != null && e.getCommandTags().contains(TAG);
    }

    public static boolean isRaised(Entity e) {
        return e != null && e.getCommandTags().contains(RAISED_TAG);
    }

    public static boolean isSkeleton(Entity e) {
        return e != null && e.getCommandTags().contains(SKEL_TAG);
    }

    @Nullable
    public static UUID ownerOf(Entity e) {
        if (!isMinion(e)) return null;
        UUID c = OWNER_CACHE.get(e);
        if (c != null) return c;
        for (String t : e.getCommandTags()) {
            if (t.startsWith(OWNER_PREFIX)) {
                try {
                    c = UUID.fromString(t.substring(OWNER_PREFIX.length()));
                    OWNER_CACHE.put(e, c);
                    return c;
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    /** 플레이어면 자기 UUID, 하수인이면 주인 UUID, 아니면 null. */
    @Nullable
    static UUID sideOf(Entity e) {
        if (e instanceof PlayerEntity p) return p.getUuid();
        return ownerOf(e);
    }

    /** 같은 편인가 (같은 주인 / 주인과 하수인 / 같은 팀 플레이어와 그 하수인). */
    public static boolean allied(Entity a, Entity b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        UUID ua = sideOf(a), ub = sideOf(b);
        if (ua == null || ub == null) return false;
        if (ua.equals(ub)) return true;
        if (!isMinion(a) && !isMinion(b)) return false; // 플레이어끼리는 바닐라 팀 규칙에 맡김
        PlayerEntity pa = a.getWorld().getPlayerByUuid(ua), pb = a.getWorld().getPlayerByUuid(ub);
        return pa != null && pb != null && pa.isTeammate(pb);
    }

    public static boolean isBoss(Entity e) {
        return e instanceof EnderDragonEntity || e instanceof WitherEntity || e instanceof WardenEntity || e instanceof ElderGuardianEntity
                || e.getType().isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ENTITY_TYPE, new Identifier("c", "bosses")));
    }

    // ================================================================ 집중 대상 (주인이 때린 적)

    public static void onHit(Entity attacker, LivingEntity victim) {
        if (attacker == null || victim == null || allied(attacker, victim)) return;
        UUID side = sideOf(attacker);
        if (side == null) return;
        if (attacker instanceof PlayerEntity && !"warlock".equals(((AWPlayer) attacker).aw$getAbilityKey())) return;
        FOCUS.put(side, new long[]{victim.getId(), victim.getWorld().getTime()});
        // 해골 장비 닳기
        if (isSkeleton(attacker) && attacker instanceof MobEntity m) wear(m, EquipmentSlot.MAINHAND, 1);
    }

    public static void onHurt(LivingEntity victim, float amount) {
        if (!isSkeleton(victim) || !(victim instanceof MobEntity m)) return;
        int n = Math.max(1, (int) (amount / 4));
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) wear(m, s, n);
    }

    static void wear(MobEntity m, EquipmentSlot slot, int amount) {
        ItemStack st = m.getEquippedStack(slot);
        if (st.isEmpty() || !st.isDamageable()) return;
        st.damage(amount, m, e -> e.sendEquipmentBreakStatus(slot));
    }

    @Nullable
    static LivingEntity focus(ServerPlayerEntity owner) {
        long[] f = FOCUS.get(owner.getUuid());
        if (f == null || owner.getWorld().getTime() - f[1] > 300) return null;
        Entity e = owner.getServerWorld().getEntityById((int) f[0]);
        if (e instanceof LivingEntity le && le.isAlive() && !le.isRemoved() && !allied(owner, le)) return le;
        return null;
    }

    // ================================================================ 하수인 소환 / 해제

    static void mark(MobEntity mob, ServerPlayerEntity owner, String kind, int soulId) {
        for (String t : new ArrayList<>(mob.getCommandTags())) if (t.startsWith("aw_")) mob.removeScoreboardTag(t);
        mob.addCommandTag(TAG);
        mob.addCommandTag(kind);
        mob.addCommandTag(OWNER_PREFIX + owner.getUuid());
        if (soulId >= 0) mob.addCommandTag(SOUL_PREFIX + soulId);
        mob.setPersistent();
        for (EquipmentSlot s : EquipmentSlot.values()) mob.setEquipmentDropChance(s, 0f);
        OWNER_CACHE.put(mob, owner.getUuid());
    }

    /** 해골 소환 (흑마법서의 장비를 입힘). */
    @Nullable
    public static MobEntity summonSkeleton(ServerPlayerEntity owner, Grimoire g, Vec3d at) {
        ServerWorld w = owner.getServerWorld();
        var sk = EntityType.SKELETON.create(w);
        if (sk == null) return null;
        sk.refreshPositionAndAngles(at.x, at.y, at.z, owner.getYaw(), 0);
        sk.initialize(w, w.getLocalDifficulty(sk.getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
        for (EquipmentSlot s : GEAR) sk.equipStack(s, ItemStack.EMPTY);
        mark(sk, owner, SKEL_TAG, -1);
        applyGear(sk, g);
        sk.setCustomName(Text.translatable("abilitywar.warlock.skeleton_name", owner.getDisplayName()));
        sk.setCustomNameVisible(false);
        LIVE.put(sk.getUuid(), new Minion(sk, owner.getUuid(), -1));
        w.spawnEntityAndPassengers(sk);
        g.skeleton = sk.getUuid();
        return sk;
    }

    public static void applyGear(MobEntity skel, Grimoire g) {
        for (int i = 0; i < GEAR.length; i++) {
            skel.equipStack(GEAR[i], g.gear.getStack(i).copy());
            skel.setEquipmentDropChance(GEAR[i], 0f);
        }
        if (skel instanceof AbstractSkeletonEntity a) a.updateAttackType();
    }

    public static void readGear(MobEntity skel, Grimoire g) {
        for (int i = 0; i < GEAR.length; i++) g.gear.setStack(i, skel.getEquippedStack(GEAR[i]).copy());
    }

    @Nullable
    public static MobEntity skeleton(ServerPlayerEntity owner, Grimoire g) {
        if (g.skeleton == null) return null;
        Minion m = LIVE.get(g.skeleton);
        return m != null && !m.mob.isRemoved() ? m.mob : null;
    }

    /** 영혼을 되살림 (체력·피해 절반). */
    @Nullable
    public static MobEntity raise(ServerPlayerEntity owner, Grimoire.Soul s, Vec3d at) {
        ServerWorld w = owner.getServerWorld();
        NbtCompound n = s.data.copy();
        n.putString("id", s.type);
        Entity ent = EntityType.loadEntityWithPassengers(n, w, e -> {
            e.refreshPositionAndAngles(at.x, at.y, at.z, owner.getYaw(), 0);
            return e;
        });
        if (!(ent instanceof MobEntity mob)) return null;
        mob.removeAllPassengers();
        mark(mob, owner, RAISED_TAG, s.id);
        EntityAttributeInstance hp = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null && hp.getModifier(HALF_HP) == null) {
            hp.addPersistentModifier(new EntityAttributeModifier(HALF_HP, "aw_raised_half", -0.5, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        mob.setHealth(mob.getMaxHealth());
        mob.setTarget(null);
        mob.clearStatusEffects();
        mob.extinguish();
        LIVE.put(mob.getUuid(), new Minion(mob, owner.getUuid(), s.id));
        w.spawnEntityAndPassengers(mob);
        s.summoned = mob.getUuid();
        return mob;
    }

    /** 하수인을 흑마법서로 돌려보냄 (죽은 게 아님: 쿨타임 없음). */
    public static void dismiss(ServerPlayerEntity owner, Grimoire g, UUID entity) {
        Minion m = LIVE.remove(entity);
        if (m == null) return;
        m.dismissing = true;
        if (m.soulId < 0) {
            readGear(m.mob, g);
            g.skeleton = null;
        } else {
            Grimoire.Soul s = g.soul(m.soulId);
            if (s != null) s.summoned = null;
        }
        NbtCompound n = new NbtCompound();
        n.putInt("e", m.mob.getId());
        AWNet.fx((ServerWorld) m.mob.getWorld(), m.mob.getPos(), "warlock_unsummon", n);
        m.mob.discard();
    }

    public static void dismissAll(ServerPlayerEntity owner, Grimoire g) {
        for (Minion m : new ArrayList<>(LIVE.values())) if (m.owner.equals(owner.getUuid())) dismiss(owner, g, m.mob.getUuid());
        g.skeleton = null;
        for (Grimoire.Soul s : g.souls) s.summoned = null;
    }

    public static List<MobEntity> minionsOf(UUID owner) {
        List<MobEntity> l = new ArrayList<>();
        for (Minion m : LIVE.values()) if (m.owner.equals(owner) && !m.mob.isRemoved()) l.add(m.mob);
        return l;
    }

    /** 하수인이 죽음 (또는 크리퍼처럼 스스로 사라짐). */
    static void died(MinecraftServer server, Minion m) {
        LIVE.remove(m.mob.getUuid());
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(m.owner);
        if (owner == null) return;
        AbilityData d = AbilityManager.data(owner);
        Grimoire g = d.grimoire;
        if (m.soulId < 0) {
            readGear(m.mob, g);
            g.skeleton = null;
            WarlockAbility.onSkeletonDied(owner, d);
        } else {
            Grimoire.Soul s = g.soul(m.soulId);
            if (s != null) {
                s.summoned = null;
                if (s.captured) {
                    s.cooldownUntil = owner.getWorld().getTime() + Grimoire.CAPTURED_COOLDOWN;
                    owner.sendMessage(Text.translatable("abilitywar.warlock.captured_died", name(s.type)), true);
                } else {
                    g.souls.remove(s);
                    owner.sendMessage(Text.translatable("abilitywar.warlock.soul_lost", name(s.type)), true);
                }
            }
        }
        WarlockAbility.syncSouls(owner);
    }

    public static Text name(String type) {
        EntityType<?> t = Registries.ENTITY_TYPE.get(new Identifier(type));
        return t.getName();
    }

    // ================================================================ 땅에 떨어진 영혼

    static final class Orb {
        final int id;
        final String type;
        final NbtCompound data;
        final ServerWorld world;
        final Vec3d pos;
        final long expire;
        final Set<UUID> eligible;

        Orb(int id, String type, NbtCompound data, ServerWorld world, Vec3d pos, long expire, Set<UUID> eligible) {
            this.id = id;
            this.type = type;
            this.data = data;
            this.world = world;
            this.pos = pos;
            this.expire = expire;
            this.eligible = eligible;
        }
    }

    static final List<Orb> ORBS = new ArrayList<>();
    static int orbIds = 1;

    /** 몹이 죽었을 때 (서버 이벤트). */
    public static void onDeath(LivingEntity e, DamageSource src) {
        if (e.getWorld().isClient || !(e.getWorld() instanceof ServerWorld w)) return;
        MinecraftServer server = w.getServer();
        // 하수인이 죽음
        Minion m = LIVE.get(e.getUuid());
        if (m != null) {
            if (!m.dismissing) died(server, m);
            return;
        }
        if (e instanceof PlayerEntity || isMinion(e) || !(e instanceof MobEntity) || e instanceof EnderDragonEntity) return;
        Entity killer = src.getAttacker();
        if (killer != null && isMinion(killer)) {
            UUID o = ownerOf(killer);
            killer = o == null ? null : server.getPlayerManager().getPlayer(o);
        }
        if (!(killer instanceof ServerPlayerEntity kp)) return;
        Set<UUID> eligible = new HashSet<>();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (!"warlock".equals(((AWPlayer) p).aw$getAbilityKey()) || p.squaredDistanceTo(e) > 48 * 48) continue;
            if (p == kp || p.isTeammate(kp)) eligible.add(p.getUuid());
        }
        if (eligible.isEmpty()) return;
        Orb o = new Orb(orbIds++, EntityType.getId(e.getType()).toString(), soulData(e), w, e.getPos().add(0, Math.min(1.2, e.getHeight() * 0.6), 0),
                w.getTime() + SOUL_LIFE, eligible);
        ORBS.add(o);
        NbtCompound n = new NbtCompound();
        n.putInt("id", o.id);
        n.putInt("life", SOUL_LIFE);
        n.putFloat("s", Math.min(2.5f, 0.6f + e.getHeight() * 0.35f));
        AWNet.fx(w, o.pos, "warlock_soul", n);
    }

    /** 다시 만들 때 쓸 엔티티 데이터 (위치·체력·상태 등 제외). */
    public static NbtCompound soulData(LivingEntity e) {
        NbtCompound n = new NbtCompound();
        e.writeNbt(n);
        for (String k : new String[]{"Pos", "Motion", "Rotation", "UUID", "Health", "HurtTime", "HurtByTimestamp", "DeathTime", "FallDistance", "FallFlying",
                "Fire", "Air", "OnGround", "PortalCooldown", "Passengers", "Leash", "Brain", "ActiveEffects", "active_effects", "AbsorptionAmount",
                "Tags", "PersistenceRequired", "Attributes", "SleepingX", "SleepingY", "SleepingZ", "ArmorDropChances", "HandDropChances",
                "AngryAt", "AngerTime", "Invulnerable", "NoAI", "Silent"}) {
            n.remove(k);
        }
        return n;
    }

    /** T: 주변의 영혼 수집. @return 모은 수 (-1 = 흑마법서가 가득 참) */
    public static int collect(ServerPlayerEntity p, Grimoire g) {
        int got = 0;
        boolean full = false;
        for (Iterator<Orb> it = ORBS.iterator(); it.hasNext(); ) {
            Orb o = it.next();
            if (o.world != p.getWorld() || !o.eligible.contains(p.getUuid()) || o.pos.squaredDistanceTo(p.getPos().add(0, 1, 0)) > PICKUP_RANGE * PICKUP_RANGE) continue;
            if (g.souls.size() >= Grimoire.MAX_SOULS) {
                full = true;
                break;
            }
            g.add(o.type, o.data, false);
            NbtCompound n = new NbtCompound();
            n.putInt("id", o.id);
            n.putInt("c", p.getId());
            AWNet.fx(o.world, o.pos, "warlock_soul_take", n);
            it.remove();
            got++;
        }
        return got == 0 && full ? -1 : got;
    }

    public static boolean soulsNearby(ServerPlayerEntity p) {
        for (Orb o : ORBS) {
            if (o.world == p.getWorld() && o.eligible.contains(p.getUuid()) && o.pos.squaredDistanceTo(p.getPos().add(0, 1, 0)) <= PICKUP_RANGE * PICKUP_RANGE) return true;
        }
        return false;
    }

    // ================================================================ 매 틱

    public static void tick(MinecraftServer server) {
        // 영혼 사라짐
        for (Iterator<Orb> it = ORBS.iterator(); it.hasNext(); ) {
            Orb o = it.next();
            if (o.world.getTime() >= o.expire) {
                NbtCompound n = new NbtCompound();
                n.putInt("id", o.id);
                AWNet.fx(o.world, o.pos, "warlock_soul_fade", n);
                it.remove();
            }
        }
        // 하수인
        for (Minion m : new ArrayList<>(LIVE.values())) {
            if (m.mob.isRemoved()) {
                Entity.RemovalReason r = m.mob.getRemovalReason();
                if (m.dismissing) {
                    LIVE.remove(m.mob.getUuid());
                } else if (r != null && r.shouldDestroy()) {
                    died(server, m);
                } else {
                    // 청크가 내려가거나 차원 이동: 흑마법서로 돌아옴
                    LIVE.remove(m.mob.getUuid());
                    ServerPlayerEntity owner = server.getPlayerManager().getPlayer(m.owner);
                    if (owner != null) {
                        Grimoire g = AbilityManager.data(owner).grimoire;
                        if (m.soulId < 0) {
                            readGear(m.mob, g);
                            g.skeleton = null;
                        } else {
                            Grimoire.Soul s = g.soul(m.soulId);
                            if (s != null) s.summoned = null;
                        }
                    }
                }
                continue;
            }
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(m.owner);
            if (owner == null || !owner.isAlive()) {
                m.dismissing = true;
                LIVE.remove(m.mob.getUuid());
                m.mob.discard();
                continue;
            }
            control(m, owner);
        }
    }

    /** 하수인 조종: 주인이 때린 대상 → 주인을 때린 대상 → 주인 따라가기. */
    static void control(Minion m, ServerPlayerEntity owner) {
        MobEntity mob = m.mob;
        if (mob.getWorld() != owner.getWorld()) {
            Vec3d at = owner.getPos();
            mob.teleport(owner.getServerWorld(), at.x, at.y, at.z, java.util.Set.of(), mob.getYaw(), mob.getPitch());
            return;
        }
        LivingEntity want = focus(owner);
        if (want == null) {
            LivingEntity a = owner.getAttacker();
            if (a != null && a.isAlive() && !allied(owner, a)) want = a;
        }
        if (want != null && want.squaredDistanceTo(mob) > 40 * 40) want = null;
        LivingEntity cur = mob.getTarget();
        if (want != null) {
            if (cur != want) {
                mob.setTarget(want);
                if (mob.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET)) mob.getBrain().remember(MemoryModuleType.ATTACK_TARGET, want);
            }
        } else if (cur != null && (!cur.isAlive() || allied(mob, cur) || cur.squaredDistanceTo(owner) > 32 * 32)) {
            mob.setTarget(null);
            if (mob.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET)) mob.getBrain().forget(MemoryModuleType.ATTACK_TARGET);
        }
        if (mob.getTarget() == null && mob.age % 5 == 0) {
            double d2 = mob.squaredDistanceTo(owner);
            if (d2 > 26 * 26) {
                Vec3d near = owner.getPos().add(Combat.horizontal(owner.getRotationVec(1f)).multiply(-2));
                Vec3d safe = Combat.safeSpot(mob, near);
                if (safe != null) mob.refreshPositionAndAngles(safe.x, safe.y, safe.z, mob.getYaw(), mob.getPitch());
            } else if (d2 > 5 * 5) {
                mob.getNavigation().startMovingTo(owner, 1.2);
            }
        }
    }

    /** 서버를 껐다 켰을 때 남아 있던 하수인 정리. */
    public static void onLoad(Entity e) {
        if (isMinion(e) && !LIVE.containsKey(e.getUuid())) e.discard();
    }

    public static void shutdown(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) dismissAll(p, AbilityManager.data(p).grimoire);
        LIVE.clear();
        ORBS.clear();
        FOCUS.clear();
    }

    static void playSummon(ServerWorld w, Vec3d at) {
        w.playSound(null, at.x, at.y, at.z, AWSounds.WARLOCK_SUMMON, SoundCategory.PLAYERS, 1.2f, 0.9f);
    }

    private Necromancy() {
    }
}
