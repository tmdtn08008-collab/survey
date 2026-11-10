package com.abilitywar.ability;

import com.abilitywar.ability.voidab.VoidAbility;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Abilities {
    private static final Map<Identifier, Ability> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, Ability> BY_KEY = new LinkedHashMap<>();

    public static Ability VOID;
    public static Ability DRAGON;
    public static Ability BLADE;
    public static Ability BLOOD;
    public static Ability CANTOR;
    public static Ability WARLOCK;
    public static Ability ANGEL;
    public static Ability WARRIOR, MAGE, TANK, HEALER, GUARDIAN, PALADIN;

    public static void init() {
        VOID = register(new VoidAbility());
        DRAGON = register(new com.abilitywar.ability.dragon.DragonAbility());
        BLADE = register(new com.abilitywar.ability.blade.BladeAbility());
        BLOOD = register(new com.abilitywar.ability.blood.BloodAbility());
        CANTOR = register(new com.abilitywar.ability.cantor.CantorAbility());
        WARLOCK = register(new com.abilitywar.ability.warlock.WarlockAbility());
        ANGEL = register(new com.abilitywar.ability.angel.AngelAbility());
        // 1차 직업
        WARRIOR = register(new com.abilitywar.ability.basic.WarriorClass());
        MAGE = register(new com.abilitywar.ability.basic.MageClass());
        TANK = register(new com.abilitywar.ability.basic.TankClass());
        HEALER = register(new com.abilitywar.ability.basic.HealerClass());
        GUARDIAN = register(new com.abilitywar.ability.guardian.GuardianAbility());
        PALADIN = register(new com.abilitywar.ability.paladin.PaladinAbility());
    }

    public static <T extends Ability> T register(T ability) {
        REGISTRY.put(ability.id, ability);
        BY_KEY.put(ability.key, ability);
        return ability;
    }

    @Nullable
    public static Ability get(@Nullable Identifier id) {
        return id == null ? null : REGISTRY.get(id);
    }

    @Nullable
    public static Ability byString(@Nullable String s) {
        if (s == null || s.isEmpty()) return null;
        Ability a = BY_KEY.get(s);
        if (a != null) return a;
        Identifier id = Identifier.tryParse(s);
        return get(id);
    }

    public static Collection<Ability> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    private Abilities() {
    }
}
