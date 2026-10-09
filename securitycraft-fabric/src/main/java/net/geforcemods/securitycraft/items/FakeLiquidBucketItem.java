package net.geforcemods.securitycraft.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.material.Fluid;

public class FakeLiquidBucketItem extends BucketItem {
	public FakeLiquidBucketItem(Fluid fluid, Properties builder) {
		super(fluid, builder);

		DispenserBlock.registerBehavior(this, new DefaultDispenseItemBehavior() {
			private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

			@Override
			public ItemStack execute(BlockSource source, ItemStack stack) {
				DispensibleContainerItem bucket = (DispensibleContainerItem) stack.getItem();
				BlockPos dispenseAt = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
				Level level = source.level();

				//PORT-NOTE: NeoForge's overload additionally takes the bucket stack, only to check whether its fluid vaporizes through the fluid type. Fake water uses the water fluid type there
				//and is in the water fluid tag here, so vanilla's own ultra warm dimension check vaporizes it the same way
				if (bucket.emptyContents(null, level, dispenseAt, null)) {
					bucket.checkExtraContent(null, level, stack, dispenseAt);
					return consumeWithRemainder(source, stack, new ItemStack(Items.BUCKET));
				}
				else
					return defaultDispenseItemBehavior.dispense(source, stack);
			}
		});
	}
}
