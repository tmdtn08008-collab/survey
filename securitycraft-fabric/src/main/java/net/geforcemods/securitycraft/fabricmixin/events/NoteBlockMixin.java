package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fires SecurityCraft's NoteBlockEvent.Play stand-in on both sides when a note block plays its note, like NeoForge. A
 * canceled event prevents the note from being played.
 */
@Mixin(NoteBlock.class)
public abstract class NoteBlockMixin {
	//PORT-NOTE: NeoForge also plays a note and instrument changed by the event. SecurityCraft's handler does not change them, so they are not replaced here
	@Inject(method = "triggerEvent(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;II)Z", at = @At("HEAD"), cancellable = true)
	private void securitycraft$fireNoteBlockPlay(BlockState state, Level level, BlockPos pos, int id, int param, CallbackInfoReturnable<Boolean> cir) {
		if (EventHooks.onNoteBlockPlay(level, pos, state, state.getValue(NoteBlock.NOTE), state.getValue(NoteBlock.INSTRUMENT)).isCanceled())
			cir.setReturnValue(false);
	}
}
