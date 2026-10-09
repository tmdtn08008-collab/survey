package net.geforcemods.securitycraft.fabric.event;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.geforcemods.securitycraft.SCEventHandler;
import net.geforcemods.securitycraft.fabric.event.LivingChangeTargetEvent.LivingTargetType;
import net.geforcemods.securitycraft.fabric.event.PlayerInteractEvent.LeftClickBlock;
import net.geforcemods.securitycraft.fabric.event.PlayerInteractEvent.RightClickBlock;
import net.geforcemods.securitycraft.fabric.event.UseItemOnBlockEvent.UsePhase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Creates SecurityCraft's Fabric stand-ins for the NeoForge events SecurityCraft listens to and hands them to
 * {@link SCEventHandler}. These methods are called from SecurityCraft's mixins at the vanilla sites that NeoForge patched
 * (package net.geforcemods.securitycraft.fabricmixin.events) and from the Fabric API callbacks registered in
 * {@link SCEventHandler#init()}. Like NeoForge's event bus, a canceled event is not passed on to handlers with a lower
 * priority.
 */
public final class EventHooks {
	private EventHooks() {}

	/**
	 * Fired on both sides where NeoForge fires PlayerInteractEvent.RightClickBlock: in ServerPlayerGameMode#useItemOn after
	 * the feature flag check, and at the start of MultiPlayerGameMode#performUseItemOn.
	 */
	public static RightClickBlock onRightClickBlock(Player player, InteractionHand hand, BlockPos pos, BlockHitResult hitResult) {
		RightClickBlock event = new RightClickBlock(player, hand, pos, hitResult);

		SCEventHandler.highestPriorityOnRightClickBlock(event);

		if (!event.isCanceled())
			SCEventHandler.onRightClickBlock(event);

		return event;
	}

	/**
	 * Replacement for NeoForge's ItemStack#onItemUseFirst. Calls {@link ItemUseFirstHook#onItemUseFirst} for items
	 * implementing it, unless the player is in adventure mode and the item cannot be used on the clicked block. Awards the
	 * "item used" statistic if the item was used.
	 */
	public static InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
		if (!(stack.getItem() instanceof ItemUseFirstHook hook))
			return InteractionResult.PASS;

		Player player = context.getPlayer();

		if (player != null && !player.getAbilities().mayBuild && !stack.canPlaceOnBlockInAdventureMode(new BlockInWorld(context.getLevel(), context.getClickedPos(), false)))
			return InteractionResult.PASS;

		Item item = stack.getItem();
		InteractionResult result = hook.onItemUseFirst(stack, context);

		if (player != null && result.indicateItemUse())
			player.awardStat(Stats.ITEM_USED.get(item));

		return result;
	}

	/**
	 * Fired on the server at the start of ServerPlayerGameMode#handleBlockBreakAction (for every action, like NeoForge), and
	 * on the client from Fabric's AttackBlockCallback.
	 */
	public static LeftClickBlock onLeftClickBlock(Player player, BlockPos pos, Direction face, LeftClickBlock.Action action) {
		LeftClickBlock event = new LeftClickBlock(player, pos, face, action);

		SCEventHandler.onLeftClickBlock(event);
		return event;
	}

	/**
	 * Fired on the server from Fabric's PlayerBlockBreakEvents.BEFORE.
	 *
	 * @return true if the block may be broken, false if the event was canceled
	 */
	public static boolean onBlockBreak(Level level, Player player, BlockPos pos, BlockState state) {
		BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);

		SCEventHandler.onBlockEventBreak(event);
		return !event.isCanceled();
	}

	/**
	 * Fired on the server after an item was successfully used on a block and changed at least one block, with the first
	 * changed position, like NeoForge's EntityPlaceEvent (and EntityMultiPlaceEvent, which SecurityCraft receives as an
	 * EntityPlaceEvent of the first changed block).
	 */
	public static void onBlockPlace(@Nullable Entity entity, Level level, BlockPos pos, Direction clickedFace) {
		BlockState placedAgainst = level.getBlockState(pos.relative(clickedFace.getOpposite()));

		SCEventHandler.onBlockEventPlace(new BlockEvent.EntityPlaceEvent(level, pos, level.getBlockState(pos), placedAgainst, entity));
	}

	/**
	 * Calls the given item use action (Item#useOn on the server) while recording which block positions the level changes,
	 * like NeoForge's CommonHooks#onPlaceItemIntoWorld, and fires {@link BlockEvent.EntityPlaceEvent} for the first changed
	 * position if the item was used.
	 */
	//PORT-NOTE: Unlike NeoForge, block updates are not delayed while recording, and the placement cannot be undone by canceling the event (SecurityCraft never cancels it)
	public static InteractionResult useOnRecordingPlacements(Item item, UseOnContext context, Supplier<InteractionResult> useOn) {
		Level level = context.getLevel();

		if (level.isClientSide || item instanceof BucketItem || !(level instanceof BlockPlacementCapture capture))
			return useOn.get();

		List<BlockPos> previous = capture.securitycraft$startCapturingPlacements();
		InteractionResult result;
		List<BlockPos> placed;

		try {
			result = useOn.get();
		}
		finally {
			placed = capture.securitycraft$stopCapturingPlacements(previous);
		}

		if (result.consumesAction() && !placed.isEmpty())
			onBlockPlace(context.getPlayer(), level, placed.get(0), context.getClickedFace());

		return result;
	}

	/**
	 * Fired on both sides at the start of ItemStack#useOn, like NeoForge's UseItemOnBlockEvent with
	 * {@link UsePhase#ITEM_AFTER_BLOCK}.
	 */
	public static UseItemOnBlockEvent onUseItemOnBlock(UseOnContext context) {
		UseItemOnBlockEvent event = new UseItemOnBlockEvent(context, UsePhase.ITEM_AFTER_BLOCK);

		SCEventHandler.onUseItemOnBlock(event);
		return event;
	}

	/**
	 * Replacement for NeoForge's PlayerEvent.HarvestCheck as fired by EventHooks#doPlayerHarvestCheck, and for NeoForge's
	 * Player#hasCorrectToolForDrops(BlockState, Level, BlockPos) overload.
	 *
	 * @return Whether the player can harvest the given block
	 */
	public static boolean doPlayerHarvestCheck(Player player, BlockState state, BlockGetter level, BlockPos pos) {
		return doPlayerHarvestCheck(player, state, level, pos, player.hasCorrectToolForDrops(state), false, null);
	}

	/**
	 * Fires {@link PlayerEvent.HarvestCheck} with an already computed vanilla result.
	 *
	 * @param vanillaValue The result of Player#hasCorrectToolForDrops(BlockState)
	 * @param blockEntityKnown true if the block entity of the block has been captured before the block was removed, in which
	 *            case blockEntity is used instead of the block entity currently in the level
	 * @param blockEntity The captured block entity, if blockEntityKnown is true
	 * @return Whether the player can harvest the given block
	 */
	public static boolean doPlayerHarvestCheck(Player player, BlockState state, BlockGetter level, BlockPos pos, boolean vanillaValue, boolean blockEntityKnown, @Nullable BlockEntity blockEntity) {
		PlayerEvent.HarvestCheck event = new PlayerEvent.HarvestCheck(player, state, level, pos, vanillaValue, blockEntityKnown, blockEntity);

		SCEventHandler.onPlayerHarvestCheck(event);
		return event.canHarvest();
	}

	/**
	 * Fired on both sides in Entity#removeVehicle, like NeoForge's EventHooks#canMountEntity.
	 *
	 * @return true if the entity may (dis)mount, false if the event was canceled
	 */
	public static boolean canMountEntity(Entity entityMounting, Entity entityBeingMounted, boolean isMounting) {
		EntityMountEvent event = new EntityMountEvent(entityMounting, entityBeingMounted, entityMounting.level(), isMounting);

		SCEventHandler.onDismount(event);

		if (event.isCanceled()) {
			//like NeoForge, reset the rotation of the entity to its previous rotation
			entityMounting.absMoveTo(entityMounting.getX(), entityMounting.getY(), entityMounting.getZ(), entityMounting.yRotO, entityMounting.xRotO);
			return false;
		}

		return true;
	}

	/**
	 * Fired in Mob#setTarget and in the StartAttacking brain behavior, like NeoForge's CommonHooks#onLivingChangeTarget.
	 */
	public static LivingChangeTargetEvent onLivingChangeTarget(LivingEntity entity, @Nullable LivingEntity originalTarget, LivingTargetType targetType) {
		LivingChangeTargetEvent event = new LivingChangeTargetEvent(entity, originalTarget, targetType);

		SCEventHandler.onLivingSetAttackTarget(event);
		return event;
	}

	/**
	 * Fired where the wither destroys blocks around itself.
	 *
	 * @return true if the block may be destroyed, false if the event was canceled
	 */
	public static boolean onEntityDestroyBlock(LivingEntity entity, BlockPos pos, BlockState state) {
		LivingDestroyBlockEvent event = new LivingDestroyBlockEvent(entity, pos, state);

		SCEventHandler.onLivingDestroyEvent(event);
		return !event.isCanceled();
	}

	/**
	 * Fired on both sides at the start of NoteBlock#triggerEvent.
	 */
	public static NoteBlockEvent.Play onNoteBlockPlay(Level level, BlockPos pos, BlockState state, int note, NoteBlockInstrument instrument) {
		NoteBlockEvent.Play event = new NoteBlockEvent.Play(level, pos, state, note, instrument);

		SCEventHandler.onNoteBlockPlayed(event);
		return event;
	}

	/**
	 * Fired on the server at the end of LivingEntity#actuallyHurt and Player#actuallyHurt when the entity is not invulnerable
	 * to the damage source.
	 */
	public static void onLivingDamagePost(LivingEntity entity, DamageSource source) {
		SCEventHandler.onDamageTaken(new LivingDamageEvent.Post(entity, source));
	}

	/**
	 * Fired on the server from Fabric's ServerLivingEntityEvents.ALLOW_DAMAGE, which is called at the same place in
	 * LivingEntity#hurt as NeoForge's LivingIncomingDamageEvent.
	 *
	 * @return true if the entity may be damaged, false if the event was canceled
	 */
	public static boolean onEntityIncomingDamage(LivingEntity entity, DamageSource source, float amount) {
		LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(entity, source, amount);

		SCEventHandler.onLivingAttacked(event);
		return !event.isCanceled();
	}

	/**
	 * Fired on both sides when a player uses an item in the air, from Fabric's UseItemCallback.
	 */
	public static PlayerInteractEvent.RightClickItem onItemRightClick(Player player, InteractionHand hand) {
		PlayerInteractEvent.RightClickItem event = new PlayerInteractEvent.RightClickItem(player, hand);

		SCEventHandler.onRightClickItem(event);
		return event;
	}

	/**
	 * Fired on the server before an entity eating a chorus fruit teleports.
	 */
	public static EntityTeleportEvent.ChorusFruit onChorusFruitTeleport(LivingEntity entity, double targetX, double targetY, double targetZ) {
		EntityTeleportEvent.ChorusFruit event = new EntityTeleportEvent.ChorusFruit(entity, targetX, targetY, targetZ);

		SCEventHandler.onEntityTeleport(event);
		return event;
	}

	/**
	 * Fired on the server before a player is teleported by their ender pearl.
	 */
	public static EntityTeleportEvent.EnderPearl onEnderPearlLand(ServerPlayer player, double targetX, double targetY, double targetZ, ThrownEnderpearl pearl, float attackDamage, HitResult hitResult) {
		EntityTeleportEvent.EnderPearl event = new EntityTeleportEvent.EnderPearl(player, targetX, targetY, targetZ, pearl, attackDamage, hitResult);

		SCEventHandler.onEntityTeleport(event);
		return event;
	}

	/**
	 * Fired on the server before an enderman or a shulker teleports.
	 */
	//PORT-NOTE: NeoForge also fires EntityTeleportEvent.TeleportCommand and SpreadPlayersCommand. SecurityCraft maps them to no teleportation type, so the rift stabilizer never blocks them, and they are not fired on Fabric
	public static EntityTeleportEvent.EnderEntity onEnderTeleport(LivingEntity entity, double targetX, double targetY, double targetZ) {
		EntityTeleportEvent.EnderEntity event = new EntityTeleportEvent.EnderEntity(entity, targetX, targetY, targetZ);

		SCEventHandler.onEntityTeleport(event);
		return event;
	}
}
