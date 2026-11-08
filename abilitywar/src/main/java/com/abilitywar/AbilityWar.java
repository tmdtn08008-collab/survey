package com.abilitywar;

import com.abilitywar.ability.Abilities;
import com.abilitywar.command.AWCommands;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.registry.AWEntities;
import com.abilitywar.registry.AWGameRules;
import com.abilitywar.registry.AWSounds;
import com.abilitywar.server.ServerEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ability War (능력대전쟁) - main entrypoint.
 */
public class AbilityWar implements ModInitializer {
    public static final String MOD_ID = "abilitywar";
    public static final Logger LOG = LoggerFactory.getLogger("AbilityWar");

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        AWSounds.init();
        AWEffects.init();
        AWEntities.init();
        AWGameRules.init();
        Abilities.init();
        AWNet.initServer();
        AWCommands.init();
        ServerEvents.init();
        LOG.info("Ability War loaded: {} abilities", Abilities.all().size());
    }
}
