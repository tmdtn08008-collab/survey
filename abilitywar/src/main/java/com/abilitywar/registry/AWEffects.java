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

    /** 출혈 (혈귀): 단계(=증폭+1, 최대 5)마다 1초에 0.4 피해. */
    public static final StatusEffect BLEED = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("bleed"), new BleedEffect());

    // ---- 성가사(버퍼) 버프 / 디버프
    /** 용맹: 주는 피해 +15%/단계. */
    public static final StatusEffect VALOR = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("valor"),
            new AWStatusEffect(StatusEffectCategory.BENEFICIAL, 0xFF7A3A, false));
    /** 수호: 받는 피해 -15%/단계. */
    public static final StatusEffect AEGIS = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("aegis"),
            new AWStatusEffect(StatusEffectCategory.BENEFICIAL, 0x6AC8FF, false));
    /** 신속: 이동 속도 +15%/단계, 쿨타임 회복 +25%/단계 (AbilityManager). */
    public static final StatusEffect HASTE = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("haste"),
            new AWStatusEffect(StatusEffectCategory.BENEFICIAL, 0x7CFF8A, false)
                    .addAttributeModifier(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED,
                            "6f1c2a4e-3b7d-4c55-9a0e-5d1b8c2f7a31", 0.15, net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    /** 열의 (천사): 공격 속도 +20%/단계 (바닐라 공격 속도 + 능력 평타 간격). */
    public static final StatusEffect FERVOR = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("fervor"),
            new AWStatusEffect(StatusEffectCategory.BENEFICIAL, 0xFFE070, false)
                    .addAttributeModifier(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED,
                            "8b2e6c1d-4f3a-4e9b-a7d2-3c5f1e9b6a40", 0.2, net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL));

    /** 평타 간격에 열의를 적용. */
    public static int attackInterval(LivingEntity e, int base) {
        net.minecraft.entity.effect.StatusEffectInstance f = e.getStatusEffect(FERVOR);
        if (f == null) return base;
        return Math.max(3, Math.round(base / (1f + 0.2f * (f.getAmplifier() + 1))));
    }

    /** 은총: 1초마다 체력 0.5/단계 회복. */
    public static final StatusEffect GRACE = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("grace"), new GraceEffect());
    /** 침묵: 스킬 사용 불가 (평타·대시는 가능). */
    public static final StatusEffect SILENCE = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("silence"),
            new AWStatusEffect(StatusEffectCategory.HARMFUL, 0xC8C8FF, false));
    /** 불협화음: 주는 피해 -20%. */
    public static final StatusEffect DISSONANCE = Registry.register(Registries.STATUS_EFFECT, AbilityWar.id("dissonance"),
            new AWStatusEffect(StatusEffectCategory.HARMFUL, 0x8A7A5A, false));

    public static class GraceEffect extends StatusEffect {
        public GraceEffect() {
            super(StatusEffectCategory.BENEFICIAL, 0xFFE89A);
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            if (entity.getHealth() < entity.getMaxHealth()) entity.heal(0.5f * (amplifier + 1));
        }
    }

    public static class BleedEffect extends StatusEffect {
        public BleedEffect() {
            super(StatusEffectCategory.HARMFUL, 0xB0101C);
        }

        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            if (entity.getHealth() > 1f) entity.damage(entity.getDamageSources().magic(), 0.4f * (amplifier + 1));
        }
    }

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
