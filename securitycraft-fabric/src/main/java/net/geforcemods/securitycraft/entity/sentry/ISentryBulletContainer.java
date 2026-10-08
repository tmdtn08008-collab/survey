package net.geforcemods.securitycraft.entity.sentry;

import net.geforcemods.securitycraft.fabric.items.IItemHandler;

public interface ISentryBulletContainer {
	public IItemHandler getHandlerForSentry(Sentry sentry);
}
