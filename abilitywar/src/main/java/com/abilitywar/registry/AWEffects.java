package com.abilitywar.registry;

import com.abilitywar.AbilityWar;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class AWEffects {
    /** 뇌진탕: 화면이 흐려지고 흔들린다 (client visual is driven by this effect). */
    public static final StatusEffect CONCUSSION = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("concussion"),
            new AWStatusEffect(StatusEffectCategory.HARMFUL, 0x4B1C7A, false));
    /** 속박: 움직임/공격/스킬 사용 불가. */
    public static final StatusEffect STUN = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("stun"),
            new AWStatusEffect(StatusEffectCategory.HARMFUL, 0x22113A, true));

    /** 용혈 표식 (용기사): 다음 용조 마지막 타격 / 궁극기가 강화된다. */
    public static final StatusEffect DRAGON_MARK = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("dragon_mark"),
            new AWStatusEffect(StatusEffectCategory.HARMFUL, 0xFF5A1E, false));

    /** 마력 표식 (마검사): 무형검무 추가 참격, 파공참/천지개벽 추가 피해. */
    public static final StatusEffect MANA_MARK = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("mana_mark"),
            new AWStatusEffect(StatusEffectCategory.HARMFUL, 0xB44CFF, false));

    public static void init() {
    }

    public static boolean isStunned(LivingEntity e) {
        return e.hasStatusEffect(STUN);
    }

    public static class AWStatusEffect extends StatusEffect {
        private final boolean freezeMobs;

        public AWStatusEffect(StatusEffectCategory category, int color, boolean freezeMobs) {
            super(category, color);
            this.freezeMobs = freezeMobs;
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return freezeMobs;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            if (freezeMobs && entity instanceof MobEntity mob) {
                mob.getNavigation().stop();
                mob.setTarget(mob.getTarget());
            }
        }
    }

    private AWEffects() {
    }
}
