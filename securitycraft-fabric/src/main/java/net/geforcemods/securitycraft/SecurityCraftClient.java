package net.geforcemods.securitycraft;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.geforcemods.securitycraft.fabric.network.PayloadRegistrar;
import net.geforcemods.securitycraft.network.VersionCheckClient;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public class SecurityCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NeoForgeConfigRegistry.INSTANCE.register(SecurityCraft.MODID, ModConfig.Type.CLIENT, ConfigHandler.CLIENT_SPEC);
		ConfigScreenFactoryRegistry.INSTANCE.register(SecurityCraft.MODID, ConfigurationScreen::new);
		PayloadRegistrar.registerClientReceivers();
		VersionCheckClient.register();
		ClientHandler.init();
	}
}
