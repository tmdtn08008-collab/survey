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

    /** 평타 애니메이션 이름 (클라이언트 예측과 서버 모두 사용). */
    public String attackAnim(net.minecraft.entity.player.PlayerEntity player, int combo) {
        return key + ".attack" + (combo + 1);
    }

    /** T 를 길게 눌러야 하는 패시브면 필요한 틱 수 (0 = 바로 발동). */
    public int passiveHoldTicks(net.minecraft.entity.player.PlayerEntity player) {
        return 0;
    }

    /** 궁극기 사용 가능 여부. */
    public boolean canUseUltimate(ServerPlayerEntity player, AbilityData data) {
        return data.ultReady();
    }

    /** 시간에 따라 궁극기 게이지가 차는지. */
    /** 클라이언트 사전 판정 (최종 판정은 서버): 궁극기 키를 눌러도 되는가. */
    public boolean clientUltReady(net.minecraft.entity.player.PlayerEntity player, float ult) {
        return ult >= 99.99f;
    }

    /** 클라이언트 사전 판정: 패시브(T) 키를 눌러도 되는가. */
    public boolean clientPassiveReady(net.minecraft.entity.player.PlayerEntity player, float ult, boolean cooldownReady) {
        return cooldownReady;
    }

    /** 다른 스킬 사용 중(busy)에도 이 스킬을 받아줄지 (서버). */
    public boolean allowWhileBusy(ServerPlayerEntity player, AbilityData data, SkillSlot slot) {
        return false;
    }

    /** 클라이언트 사전 판정: busy 중에도 이 키를 서버로 보낼지. */
    public boolean clientAllowWhileBusy(net.minecraft.entity.player.PlayerEntity player, SkillSlot slot) {
        return false;
    }

    public boolean passiveGauge() {
        return true;
    }

    /** 이 능력의 플레이어가 피해를 줬을 때 (궁극기 피해 제외). */
    public void onDealtDamage(ServerPlayerEntity player, AbilityData data, float lost) {
        data.addUlt(lost * 4f);
    }

    /** 이 능력의 플레이어가 피해를 받았을 때. */
    public void onDamaged(ServerPlayerEntity player, AbilityData data, float lost) {
        data.addUlt(lost * 2.5f);
    }
}
