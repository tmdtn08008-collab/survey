package com.abilitywar.gear;

import com.abilitywar.combat.Combat;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWDamage;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.server.AbilityManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** 장비 능력치가 전투에 미치는 효과 (서버). */
public final class GearEffects {
    static final Random R = new Random();
    /** 장비 효과로 생긴 피해 처리 중 (효과가 또 효과를 부르지 않게). */
    public static boolean inEffect;
    /** 이번 피해가 장비 치명타였는지 (피해 숫자 표시용). */
    public static boolean lastCrit;
    static final String[] CHAOS = {"ignite", "frost", "lightning", "bleed", "poison", "wither", "vampire", "chain", "explode", "void", "berserk"};

    static double attr(LivingEntity e, net.minecraft.entity.attribute.EntityAttribute a) {
        EntityAttributeInstance i = e.getAttributeInstance(a);
        return i == null ? 0 : i.getValue();
    }

    static GearDefs.Weapon weapon(LivingEntity e) {
        if (!(e.getMainHandStack().getItem() instanceof GearWeaponItem w)) return null;
        if (e instanceof PlayerEntity p && !levelOk(p, w)) return null;
        return w.def;
    }

    // ================================================================ 피해 계산 (AbilityManager.modifyDamage)

    /** 때리는 쪽: 능력 피해, 처형, 치명타. */
    public static float outgoing(PlayerEntity attacker, LivingEntity victim, DamageSource source, float amount) {
        lastCrit = false;
        if (inEffect) return amount;
        boolean vanillaMelee = source.isOf(DamageTypes.PLAYER_ATTACK);
        if (!vanillaMelee && source.isIn(AWDamage.TAG_ABILITY)) amount *= 1f + (float) attr(attacker, GearAttributes.ABILITY_DAMAGE);
        // 스탯: 마법 직업은 지능(마법 피해), 그 외는 힘(물리 피해)
        boolean magic = !vanillaMelee && com.abilitywar.rpg.ClassTree.isMagic(((com.abilitywar.data.AWPlayer) attacker).aw$getAbilityKey());
        amount *= 1f + (float) attr(attacker, magic ? GearAttributes.MAGIC_DAMAGE : GearAttributes.PHYSICAL_DAMAGE);
        GearDefs.Weapon w = weapon(attacker);
        if (w != null) {
            for (GearDefs.Effect e : w.effects()) {
                if (e.id().equals("execute") && victim.getHealth() < victim.getMaxHealth() * 0.3f) amount *= 1f + (float) e.power() / 100f;
            }
        }
        double cc = attr(attacker, GearAttributes.CRIT_CHANCE);
        if (cc > 0 && R.nextDouble() < cc) {
            amount *= 1f + (float) attr(attacker, GearAttributes.CRIT_DAMAGE);
            lastCrit = true;
        }
        return amount;
    }

    /** 맞는 쪽: 받는 피해 감소. */
    public static float incoming(LivingEntity victim, float amount) {
        double dr = attr(victim, GearAttributes.DAMAGE_REDUCTION);
        return dr > 0 ? amount * (float) (1 - dr) : amount;
    }

    // ================================================================ 맞힌 뒤 (AbilityManager.afterDamage)

    public static void afterHit(ServerPlayerEntity p, LivingEntity victim, DamageSource source, float lost) {
        if (inEffect || victim == p) return;
        double ls = attr(p, GearAttributes.LIFESTEAL);
        GearDefs.Weapon w = weapon(p);
        if (w != null) for (GearDefs.Effect e : w.effects()) if (e.id().equals("vampire")) ls += e.power() / 100.0;
        if (ls > 0 && p.getHealth() < p.getMaxHealth()) {
            float h = (float) (lost * ls);
            if (h > 0.05f) {
                p.heal(h);
                if (h >= 0.5f) AWNet.damageNumber(p, h, AWNet.DMG_HEAL, p);
            }
        }
        if (w == null) return;
        inEffect = true;
        try {
            for (GearDefs.Effect e : w.effects()) {
                if (e.id().equals("chaos")) {
                    if (R.nextDouble() < e.chance()) apply(CHAOS[R.nextInt(CHAOS.length)], p, victim, lost, 1.0, 5, 8);
                    continue;
                }
                if (e.id().equals("vampire") || e.id().equals("execute")) continue;
                if (R.nextDouble() < e.chance()) apply(e.id(), p, victim, lost, e.chance(), e.seconds(), e.power());
            }
        } finally {
            inEffect = false;
        }
    }

    static void apply(String id, ServerPlayerEntity p, LivingEntity v, float lost, double chance, double secs, double power) {
        ServerWorld w = p.getServerWorld();
        int ticks = (int) Math.round(secs * 20);
        Vec3d c = v.getPos().add(0, v.getHeight() * 0.6, 0);
        switch (id) {
            case "ignite" -> {
                v.setOnFireFor(Math.max(1, (int) secs));
                particles(w, ParticleTypes.FLAME, c, 12, 0.3);
            }
            case "frost" -> {
                v.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 1), p);
                v.setFrozenTicks(Math.max(v.getFrozenTicks(), 160));
                particles(w, ParticleTypes.SNOWFLAKE, c, 14, 0.35);
                w.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6f, 1.6f);
            }
            case "lightning" -> {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(v.getX(), v.getY(), v.getZ());
                    bolt.setCosmetic(true);
                    w.spawnEntity(bolt);
                }
                Combat.damage(v, AWDamage.source(w, AWDamage.SKILL, p), (float) power, AWNet.DMG_SKILL);
            }
            case "bleed" -> v.addStatusEffect(new StatusEffectInstance(AWEffects.BLEED, ticks, 0), p);
            case "poison" -> {
                v.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, ticks, 1), p);
                particles(w, ParticleTypes.ITEM_SLIME, c, 8, 0.3);
            }
            case "wither" -> {
                v.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, ticks, 1), p);
                particles(w, ParticleTypes.SMOKE, c, 10, 0.3);
            }
            case "vampire" -> {
                float h = lost * 0.3f;
                p.heal(h);
                particles(w, ParticleTypes.DAMAGE_INDICATOR, c, 6, 0.3);
            }
            case "holy" -> {
                for (PlayerEntity o : w.getEntitiesByClass(PlayerEntity.class, new Box(p.getBlockPos()).expand(6), o -> o == p || o.isTeammate(p))) {
                    o.heal((float) power);
                    AWNet.damageNumber(o, (float) power, AWNet.DMG_HEAL, p);
                    particles(w, ParticleTypes.HAPPY_VILLAGER, o.getPos().add(0, 1, 0), 6, 0.4);
                }
            }
            case "chain" -> {
                LivingEntity next = null;
                double best = 36;
                for (LivingEntity o : w.getEntitiesByClass(LivingEntity.class, v.getBoundingBox().expand(5), o -> o != v && o != p && o.isAlive())) {
                    if (!Combat.canTarget(p, o)) continue;
                    double d = o.squaredDistanceTo(v);
                    if (d < best) {
                        best = d;
                        next = o;
                    }
                }
                if (next != null) {
                    line(w, c, next.getPos().add(0, next.getHeight() * 0.6, 0));
                    Combat.damage(next, AWDamage.source(w, AWDamage.SKILL, p), Math.max(1f, lost * (float) power / 100f), AWNet.DMG_SKILL);
                }
            }
            case "explode" -> {
                w.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                w.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 1.3f);
                for (LivingEntity o : w.getEntitiesByClass(LivingEntity.class, v.getBoundingBox().expand(3), o -> o != p && o.isAlive())) {
                    if (o != v && !Combat.canTarget(p, o)) continue;
                    Combat.damage(o, AWDamage.source(w, AWDamage.SKILL, p), (float) power, AWNet.DMG_SKILL);
                }
            }
            case "void" -> {
                Vec3d to = p.getPos().subtract(v.getPos());
                if (to.lengthSquared() > 1e-4) Combat.setVelocity(v, to.normalize().multiply(0.7).add(0, 0.15, 0));
                v.addStatusEffect(new StatusEffectInstance(v instanceof PlayerEntity ? StatusEffects.DARKNESS : StatusEffects.BLINDNESS, ticks, 0), p);
                particles(w, ParticleTypes.PORTAL, c, 20, 0.5);
            }
            case "haste" -> {
                AbilityData d = AbilityManager.data(p);
                long cut = Math.round(secs * 20);
                for (int i = 0; i < d.cdEnd.length; i++) d.cdEnd[i] = Math.max(d.now(), d.cdEnd[i] - cut);
                d.dirty = true;
                particles(w, ParticleTypes.ELECTRIC_SPARK, p.getPos().add(0, 1, 0), 10, 0.4);
            }
            case "berserk" -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, ticks, 0, false, false, true));
                particles(w, ParticleTypes.ANGRY_VILLAGER, p.getPos().add(0, 1.8, 0), 3, 0.3);
            }
            default -> {
            }
        }
    }

    static void particles(ServerWorld w, ParticleEffect type, Vec3d c, int n, double spread) {
        w.spawnParticles(type, c.x, c.y, c.z, n, spread, spread, spread, 0.02);
    }

    static void line(ServerWorld w, Vec3d a, Vec3d b) {
        Vec3d d = b.subtract(a);
        int n = (int) Math.max(4, d.length() * 4);
        for (int i = 0; i <= n; i++) {
            Vec3d q = a.add(d.multiply(i / (double) n));
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0);
        }
    }

    // ================================================================ 세트 효과 (10틱마다)
    static final Map<UUID, String> ACTIVE = new HashMap<>();
    static final Map<UUID, Long> LAST_PULSE = new HashMap<>();
    static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static GearDefs.ArmorSet fullSet(LivingEntity e) {
        GearDefs.ArmorSet s = null;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = e.getEquippedStack(slot);
            if (!(st.getItem() instanceof GearArmorItem a)) return null;
            if (s == null) s = a.set;
            else if (s != a.set) return null;
        }
        return s;
    }

    // ================================================================ 착용 레벨
    static final Map<UUID, String> LOCKED = new HashMap<>();

    public static boolean levelOk(PlayerEntity p, GearItem g) {
        if (!p.getWorld().getGameRules().getBoolean(com.abilitywar.registry.AWGameRules.GEAR_LEVEL)) return true;
        return com.abilitywar.rpg.RpgManager.rpg(p).level >= g.grade().requiredLevel();
    }

    /** 레벨이 모자란 무기는 능력치를 0 으로 (같은 크기의 반대 수정치), 갑옷은 벗김. */
    static void levelChecks(ServerPlayerEntity p, boolean armorToo) {
        ItemStack hand = p.getMainHandStack();
        String lock = hand.getItem() instanceof GearWeaponItem w && !levelOk(p, w) ? w.def.id() : null;
        String prev = LOCKED.get(p.getUuid());
        if (!java.util.Objects.equals(lock, prev)) {
            for (GearStat st : GearStat.values()) {
                EntityAttributeInstance inst = p.getAttributeInstance(st.attribute());
                if (inst != null) inst.removeModifier(GearUuids.of("lock", st));
            }
            if (lock != null) {
                GearWeaponItem w = (GearWeaponItem) hand.getItem();
                for (Map.Entry<GearStat, Double> e : w.def.stats().entrySet()) {
                    EntityAttributeInstance inst = p.getAttributeInstance(e.getKey().attribute());
                    if (inst != null) inst.addTemporaryModifier(new EntityAttributeModifier(GearUuids.of("lock", e.getKey()), "Gear level lock",
                            -e.getKey().modifierValue(e.getValue()), e.getKey().operation()));
                }
                p.sendMessage(net.minecraft.text.Text.translatable("gear.abilitywar.ui.level_locked", w.grade().requiredLevel()).formatted(net.minecraft.util.Formatting.RED), true);
                LOCKED.put(p.getUuid(), lock);
            } else LOCKED.remove(p.getUuid());
        }
        if (!armorToo) return;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = p.getEquippedStack(slot);
            if (st.getItem() instanceof GearArmorItem a && !levelOk(p, a)) {
                ItemStack copy = st.copy();
                p.equipStack(slot, ItemStack.EMPTY);
                if (!p.getInventory().insertStack(copy)) p.dropItem(copy, false);
                p.sendMessage(net.minecraft.text.Text.translatable("gear.abilitywar.ui.level_locked", a.grade().requiredLevel()).formatted(net.minecraft.util.Formatting.RED), true);
            }
        }
    }

    public static void tick(ServerPlayerEntity p) {
        long now = p.getServer().getTicks();
        levelChecks(p, (now + p.getId()) % 10 == 0);
        if ((now + p.getId()) % 10 != 0) return;
        GearDefs.ArmorSet set = fullSet(p);
        String id = set == null ? null : set.id();
        String prev = ACTIVE.get(p.getUuid());
        if (!java.util.Objects.equals(id, prev)) {
            // 세트 능력치 바꾸기
            for (GearStat st : GearStat.values()) {
                EntityAttributeInstance inst = p.getAttributeInstance(st.attribute());
                if (inst != null) inst.removeModifier(GearUuids.set(st));
            }
            if (set != null) {
                for (Map.Entry<GearStat, Double> e : set.setStats().entrySet()) {
                    EntityAttributeInstance inst = p.getAttributeInstance(e.getKey().attribute());
                    if (inst != null) inst.addTemporaryModifier(new EntityAttributeModifier(GearUuids.set(e.getKey()), "Gear set", e.getKey().modifierValue(e.getValue()), e.getKey().operation()));
                }
            }
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
            if (id == null) ACTIVE.remove(p.getUuid());
            else ACTIVE.put(p.getUuid(), id);
        }
        if (set == null) return;
        for (String fx : set.setEffects()) {
            switch (fx) {
                case "regen" -> keep(p, StatusEffects.REGENERATION, 0);
                case "fire" -> keep(p, StatusEffects.FIRE_RESISTANCE, 0);
                case "speed" -> keep(p, StatusEffects.SPEED, 0);
                case "night" -> keep(p, StatusEffects.NIGHT_VISION, 0, 260);
                case "water" -> {
                    keep(p, StatusEffects.WATER_BREATHING, 0);
                    if (p.isTouchingWater()) keep(p, StatusEffects.DOLPHINS_GRACE, 0);
                }
                case "jump" -> keep(p, StatusEffects.JUMP_BOOST, 1);
                case "resist" -> keep(p, StatusEffects.RESISTANCE, 0);
                case "guardian" -> {
                    long last = LAST_PULSE.getOrDefault(p.getUuid(), 0L);
                    if (now - last >= 600) {
                        LAST_PULSE.put(p.getUuid(), now);
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 1, true, false, true));
                    }
                }
                case "aura" -> {
                    if (now % 60 < 10) {
                        for (PlayerEntity o : p.getServerWorld().getEntitiesByClass(PlayerEntity.class, new Box(p.getBlockPos()).expand(8), o -> o.isTeammate(p) || o == p)) {
                            if (o.getHealth() < o.getMaxHealth()) {
                                o.heal(1f);
                                particles(p.getServerWorld(), ParticleTypes.HEART, o.getPos().add(0, 2, 0), 1, 0.2);
                            }
                        }
                    }
                }
                case "chaos" -> {
                    long last = LAST_PULSE.getOrDefault(p.getUuid(), 0L);
                    if (now - last >= 400) {
                        LAST_PULSE.put(p.getUuid(), now);
                        StatusEffect[] good = {StatusEffects.SPEED, StatusEffects.STRENGTH, StatusEffects.REGENERATION, StatusEffects.RESISTANCE, StatusEffects.HASTE, StatusEffects.JUMP_BOOST};
                        StatusEffect[] bad = {StatusEffects.SLOWNESS, StatusEffects.WEAKNESS, StatusEffects.GLOWING};
                        StatusEffect pick = R.nextDouble() < 0.8 ? good[R.nextInt(good.length)] : bad[R.nextInt(bad.length)];
                        p.addStatusEffect(new StatusEffectInstance(pick, 400, R.nextInt(2), true, false, true));
                        particles(p.getServerWorld(), ParticleTypes.WITCH, p.getPos().add(0, 1, 0), 20, 0.5);
                    }
                }
                default -> {
                }
            }
        }
    }

    static void keep(ServerPlayerEntity p, StatusEffect e, int amp) {
        keep(p, e, amp, 60);
    }

    static void keep(ServerPlayerEntity p, StatusEffect e, int amp, int ticks) {
        StatusEffectInstance cur = p.getStatusEffect(e);
        if (cur == null || cur.getDuration() < ticks - 20 || cur.getAmplifier() < amp)
            p.addStatusEffect(new StatusEffectInstance(e, ticks, amp, true, false, true));
    }

    /** 가시 세트: 근접 공격자에게 30% 반사. */
    public static void thorns(LivingEntity victim, DamageSource source, float lost) {
        if (inEffect || !(victim instanceof ServerPlayerEntity p)) return;
        GearDefs.ArmorSet set = fullSet(p);
        if (set == null || !set.setEffects().contains("thorns")) return;
        Entity src = source.getSource();
        if (!(source.getAttacker() instanceof LivingEntity att) || src != att || att.squaredDistanceTo(p) > 25) return;
        inEffect = true;
        try {
            att.damage(p.getDamageSources().thorns(p), Math.max(0.5f, lost * 0.3f));
        } finally {
            inEffect = false;
        }
    }

    public static void forget(ServerPlayerEntity p) {
        ACTIVE.remove(p.getUuid());
        LOCKED.remove(p.getUuid());
        LAST_PULSE.remove(p.getUuid());
    }

    private GearEffects() {
    }
}
