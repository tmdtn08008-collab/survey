package net.geforcemods.securitycraft.fabric.event;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.geforcemods.securitycraft.SCContent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Replacement for SecurityCraft's NeoForge RegisterBrewingRecipesEvent listener, which added two brewing recipes that
 * Fabric's brewing API cannot express, because their input (the item in the bottom slots) is a bucket instead of a potion:
 * <ul>
 * <li>water bucket + potion of harming or strong harming (normal, splash or lingering) -> fake water bucket</li>
 * <li>lava bucket + potion of healing or strong healing (normal, splash or lingering) -> fake lava bucket</li>
 * </ul>
 * These are consulted by SecurityCraft's PotionBrewing, BrewingStandMenu$PotionSlot and BrewingStandBlockEntity mixins at
 * the places where NeoForge consults its BrewingRecipeRegistry, so ordinary brewing is unchanged.
 */
public final class SCBrewingRecipes {
	private static final List<Recipe> RECIPES = List.of(
			new Recipe(stack -> stack.is(Items.WATER_BUCKET), potionIngredient(Potions.HARMING, Potions.STRONG_HARMING), () -> new ItemStack(SCContent.FAKE_WATER_BUCKET.get())),
			new Recipe(stack -> stack.is(Items.LAVA_BUCKET), potionIngredient(Potions.HEALING, Potions.STRONG_HEALING), () -> new ItemStack(SCContent.FAKE_LAVA_BUCKET.get())));

	private SCBrewingRecipes() {}

	/**
	 * @return true if the stack is the input of a SecurityCraft brewing recipe, meaning it can be put into the bottom slots of
	 *         a brewing stand (NeoForge's PotionBrewing#isInput)
	 */
	public static boolean isInput(ItemStack stack) {
		for (Recipe recipe : RECIPES) {
			if (recipe.input.test(stack))
				return true;
		}

		return false;
	}

	/**
	 * @return true if the stack is the ingredient of a SecurityCraft brewing recipe, meaning it can be put into the top slot
	 *         of a brewing stand
	 */
	public static boolean isIngredient(ItemStack stack) {
		if (stack.isEmpty())
			return false;

		for (Recipe recipe : RECIPES) {
			if (recipe.ingredient.test(stack))
				return true;
		}

		return false;
	}

	/**
	 * @param input The stack in one of the bottom slots
	 * @param ingredient The stack in the top slot
	 * @return The result of brewing the input with the ingredient, or an empty stack if no SecurityCraft recipe matches
	 */
	public static ItemStack getOutput(ItemStack input, ItemStack ingredient) {
		if (input.isEmpty() || input.getCount() != 1 || ingredient.isEmpty())
			return ItemStack.EMPTY;

		for (Recipe recipe : RECIPES) {
			if (recipe.input.test(input) && recipe.ingredient.test(ingredient))
				return recipe.output.get();
		}

		return ItemStack.EMPTY;
	}

	public static boolean hasOutput(ItemStack input, ItemStack ingredient) {
		return !getOutput(input, ingredient).isEmpty();
	}

	/**
	 * Matches a normal, splash or lingering potion whose potion contents are exactly one of the given potions without custom
	 * color or effects, like the non-strict DataComponentIngredient SecurityCraft used on NeoForge.
	 */
	@SafeVarargs
	private static Predicate<ItemStack> potionIngredient(Holder<Potion>... potions) {
		return stack -> {
			if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION))
				return false;

			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);

			for (Holder<Potion> potion : potions) {
				if (Objects.equals(contents, new PotionContents(potion)))
					return true;
			}

			return false;
		};
	}

	private record Recipe(Predicate<ItemStack> input, Predicate<ItemStack> ingredient, Supplier<ItemStack> output) {}
}
