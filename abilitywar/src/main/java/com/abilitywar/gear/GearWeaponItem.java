package com.abilitywar.gear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RPG 무기: 손에 들면 능력치가 붙고, 능력 평타·스킬에도 적용된다. */
public class GearWeaponItem extends Item implements GearItem {
    public final GearDefs.Weapon def;
    private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;

    public GearWeaponItem(GearDefs.Weapon def, Settings settings) {
        super(settings);
        this.def = def;
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> b = ImmutableMultimap.builder();
        for (Map.Entry<GearStat, Double> e : def.stats().entrySet()) {
            GearStat st = e.getKey();
            UUID id = st == GearStat.ATTACK_DAMAGE ? ATTACK_DAMAGE_MODIFIER_ID : st == GearStat.ATTACK_SPEED ? ATTACK_SPEED_MODIFIER_ID : GearUuids.of("mainhand", st);
            b.put(st.attribute(), new EntityAttributeModifier(id, "Gear " + st.key, st.modifierValue(e.getValue()), st.operation()));
        }
        this.modifiers = b.build();
    }

    @Override
    public GearGrade grade() {
        return def.grade();
    }

    @Override
    public String gearId() {
        return def.id();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot);
    }

    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        return !miner.isCreative();
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public Text getName(ItemStack stack) {
        return GearItem.name(def.grade(), getTranslationKey());
    }

    @Override
    public Text getName() {
        return GearItem.name(def.grade(), getTranslationKey());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        GearTooltip.weapon(this, tooltip);
    }
}
