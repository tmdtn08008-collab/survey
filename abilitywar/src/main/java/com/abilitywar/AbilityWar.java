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
        com.abilitywar.mob.AWMobs.init();
        com.abilitywar.world.RpgStructures.init();
        AWGameRules.init();
        com.abilitywar.gear.GearAttributes.init();
        com.abilitywar.gear.GearItems.init();
        com.abilitywar.item.RpgItems.init();
        com.abilitywar.social.Social.init();
        com.abilitywar.rpg.AbyssTower.init();
        com.abilitywar.mob.FinalDragon.init();
        Abilities.init();
        com.abilitywar.ability.warlock.GrimoireScreenHandler.init();
        AWNet.initServer();
        com.abilitywar.rpg.RpgManager.initNet();
        AWCommands.init();
        ServerEvents.init();
        LOG.info("Ability War loaded: {} abilities", Abilities.all().size());
    }
}
