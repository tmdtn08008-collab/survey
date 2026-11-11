package com.abilitywar.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 회복 물약: 마시면 최대 체력의 일정 비율을 바로 회복. 회복 물약끼리 재사용 대기 시간을 같이 씀. */
public class HealthPotionItem extends Item {
    public static final int COOLDOWN = 20 * 4;
    final float fraction;

    public HealthPotionItem(float fraction, Settings settings) {
        super(settings);
        this.fraction = fraction;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 16;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) {
            user.heal(user.getMaxHealth() * fraction);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.35f, 1.8f);
            if (world instanceof ServerWorld sw)
                sw.spawnParticles(ParticleTypes.HEART, user.getX(), user.getY() + 1.2, user.getZ(), 4 + Math.round(fraction * 8), 0.4, 0.4, 0.4, 0.05);
        }
        if (user instanceof PlayerEntity p) {
            for (Item i : RpgItems.HEALTH_POTIONS) p.getItemCooldownManager().set(i, COOLDOWN);
            if (!p.getAbilities().creativeMode) stack.decrement(1);
        } else stack.decrement(1);
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.abilitywar.health_potion.desc", Math.round(fraction * 100)).formatted(Formatting.RED));
        tooltip.add(Text.translatable("item.abilitywar.health_potion.cooldown", COOLDOWN / 20).formatted(Formatting.DARK_GRAY));
    }
}
