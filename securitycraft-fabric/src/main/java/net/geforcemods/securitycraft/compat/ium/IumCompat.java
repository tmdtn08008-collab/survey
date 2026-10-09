package net.geforcemods.securitycraft.compat.ium;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;

public class IumCompat {
	private static final IumMod NONE = new IumMod() {
		@Override
		public void onChunkStatusAdded(ClientLevel level, int x, int z) {}

		@Override
		public void onChunkStatusRemoved(ClientLevel level, int x, int z) {}
	};
	private static IumMod activeIumMod;

	private IumCompat() {}

	public static IumMod get() {
		if (activeIumMod == null)
			activeIumMod = getInstalledIumMod();

		return activeIumMod;
	}

	public static boolean isActive() {
		return get() != NONE;
	}

	// PORT-NOTE: Embeddium only exists for (Neo)Forge, so its branch and compat class are gone. Sodium 0.6 for Fabric has the same
	// ChunkTrackerHolder API (same package) as the NeoForge build, so the Sodium compat is unchanged.
	private static IumMod getInstalledIumMod() {
		if (FabricLoader.getInstance().isModLoaded("sodium"))
			return new Sodium();
		else
			return NONE;
	}

	public interface IumMod {
		int FLAG_HAS_BLOCK_DATA = 1;

		void onChunkStatusAdded(ClientLevel level, int x, int z);

		void onChunkStatusRemoved(ClientLevel level, int x, int z);
	}
}
