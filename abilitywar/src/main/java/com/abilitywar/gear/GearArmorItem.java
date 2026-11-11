package com.abilitywar.gear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** RPG 방어구 한 부위. 방어력·방어 강도·밀쳐내기 저항은 재질에서, 나머지 능력치는 따로 붙음. */
public class GearArmorItem extends ArmorItem implements GearItem {
    public final GearDefs.ArmorSet set;
    public final GearDefs.Piece piece;
    private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;

    public GearArmorItem(GearDefs.ArmorSet set, GearDefs.Piece piece, Type type, Settings settings) {
        super(new GearArmorMaterial(set), type, settings);
        this.set = set;
        this.piece = piece;
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(super.getAttributeModifiers(type.getEquipmentSlot()));
        String slot = type.getEquipmentSlot().getName();
        for (Map.Entry<GearStat, Double> e : piece.stats().entrySet()) {
            GearStat st = e.getKey();
            if (st == GearStat.ARMOR || st == GearStat.TOUGHNESS || st == GearStat.KNOCKBACK_RESISTANCE) continue;
            b.put(st.attribute(), new EntityAttributeModifier(GearUuids.of(slot, st), "Gear " + st.key, st.modifierValue(e.getValue()), st.operation()));
        }
        this.modifiers = b.build();
    }

    @Override
    public GearGrade grade() {
        return set.grade();
    }

    @Override
    public String gearId() {
        return piece.id();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == this.type.getEquipmentSlot() ? modifiers : ImmutableMultimap.of();
    }

    /** 강화된 갑옷: 방어력 · 체력이 더 붙음. */
    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(ItemStack stack, EquipmentSlot slot) {
        int lv = GearEnhance.level(stack);
        if (slot != this.type.getEquipmentSlot() || lv <= 0) return getAttributeModifiers(slot);
        return GearEnhance.armorModifiers(this, modifiers, lv);
    }

    @Override
    public Text getName(ItemStack stack) {
        return GearEnhance.name(stack, GearItem.name(set.grade(), getTranslationKey()));
    }

    @Override
    public Text getName() {
        return GearItem.name(set.grade(), getTranslationKey());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        GearTooltip.armor(this, stack, tooltip);
    }
}
