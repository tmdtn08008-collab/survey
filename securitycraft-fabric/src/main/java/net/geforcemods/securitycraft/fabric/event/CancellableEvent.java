package net.geforcemods.securitycraft.fabric.event;

/**
 * Base class for SecurityCraft's Fabric stand-ins of cancellable NeoForge events (NeoForge's ICancellableEvent).
 */
public abstract class CancellableEvent {
	private boolean canceled;

	public boolean isCanceled() {
		return canceled;
	}

	public void setCanceled(boolean canceled) {
		this.canceled = canceled;
	}
}
