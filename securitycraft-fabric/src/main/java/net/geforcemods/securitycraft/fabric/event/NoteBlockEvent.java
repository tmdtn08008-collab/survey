package net.geforcemods.securitycraft.fabric.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

/**
 * Fabric stand-ins for NeoForge's NoteBlockEvent subclasses that SecurityCraft listens to.
 */
public abstract class NoteBlockEvent extends CancellableEvent {
	private final Level level;
	private final BlockPos pos;
	private final BlockState state;
	private final int noteId;

	protected NoteBlockEvent(Level level, BlockPos pos, BlockState state, int note) {
		this.level = level;
		this.pos = pos;
		this.state = state;
		this.noteId = note;
	}

	public LevelAccessor getLevel() {
		return level;
	}

	public BlockPos getPos() {
		return pos;
	}

	public BlockState getState() {
		return state;
	}

	public int getVanillaNoteId() {
		return noteId;
	}

	/**
	 * Fired on both sides when a note block plays its note (NoteBlock#triggerEvent). Canceling it prevents the note from
	 * being played.
	 */
	public static class Play extends NoteBlockEvent {
		private final NoteBlockInstrument instrument;

		public Play(Level level, BlockPos pos, BlockState state, int note, NoteBlockInstrument instrument) {
			super(level, pos, state, note);
			this.instrument = instrument;
		}

		public NoteBlockInstrument getInstrument() {
			return instrument;
		}
	}
}
