package com.abilitywar.ability;

import com.abilitywar.data.AbilityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 능력 하나의 서버측 동작. 새 능력은 이 클래스를 상속해서 {@link Abilities#register}로 등록한다.
 */
public abstract class Ability {
    public final Identifier id;
    /** 애니메이션/아이콘/번역 키 접두어 (예: "void"). */
    public final String key;
    /** HUD/이펙트 대표 색 (ARGB). */
    public final int color;

    protected Ability(Identifier id, String key, int color) {
        this.id = id;
        this.key = key;
        this.color = color;
    }

    /** 스킬 사용 시도. 성공하면 true (쿨타임은 구현에서 {@link AbilityData#startCooldown} 으로 건다). */
    public abstract boolean useSkill(ServerPlayerEntity player, AbilityData data, SkillSlot slot, PacketByteBuf extra);

    /** 평타. target 은 null 일 수 있다(허공 휘두르기). */
    public abstract void basicAttack(ServerPlayerEntity player, AbilityData data, @Nullable LivingEntity target);

    /** 매 틱. */
    public void tick(ServerPlayerEntity player, AbilityData data) {
    }

    /**
     * 이 능력을 가진 플레이어가 피해를 받기 직전. false 를 반환하면 피해 무효.
     */
    public boolean onIncomingDamage(ServerPlayerEntity player, AbilityData data, DamageSource source, float amount) {
        return true;
    }

    /** 능력 해제/사망/로그아웃 등 상태 초기화. */
    public void reset(ServerPlayerEntity player, AbilityData data) {
    }

    /** 평타 간격 (틱). */
    public int attackInterval(int comboIndex) {
        return 8;
    }

    public String translationKey() {
        return "ability.abilitywar." + key;
    }
}
