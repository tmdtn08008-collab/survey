package com.abilitywar.client;

import com.abilitywar.client.anim.AnimRegistry;
import com.abilitywar.client.anim.ClientClock;
import com.abilitywar.client.anim.PlayerAnimator;
import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.HandTracker;
import com.abilitywar.client.fx.PostEffects;
import com.abilitywar.client.fx.VfxManager;
import com.abilitywar.client.hud.DamageNumbers;
import com.abilitywar.client.hud.SkillHud;
import com.abilitywar.client.input.InputHandler;
import com.abilitywar.client.net.ClientNet;
import com.abilitywar.client.render.EmptyRenderer;
import com.abilitywar.registry.AWEntities;
import com.abilitywar.AbilityWar;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class AbilityWarClient implements ClientModInitializer {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(com.abilitywar.ability.warlock.GrimoireScreenHandler.TYPE, com.abilitywar.client.screen.GrimoireScreen::new);
        InputHandler.init();
        com.abilitywar.client.gear.GearClient.init();
        com.abilitywar.gear.GearItem.CLIENT_LEVEL[0] = () -> com.abilitywar.client.rpg.RpgClient.level;
        EntityRendererRegistry.register(AWEntities.JOB_MASTER, com.abilitywar.client.render.JobMasterRenderer::new);
        EntityRendererRegistry.register(AWEntities.KING, com.abilitywar.client.render.JobMasterRenderer::king);
        com.abilitywar.client.dev.TestDriver.init();
        ClientNet.init();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new AnimRegistry());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new com.abilitywar.client.hud.HudLayouts());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return AbilityWar.id("post_effects");
            }

            @Override
            public void reload(ResourceManager manager) {
                PostEffects.reload();
            }
        });

        EntityRendererRegistry.register(AWEntities.VOID_SINGULARITY, EmptyRenderer::new);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer pr) {
                helper.register(new HandTracker.Feature(pr));
                helper.register(new com.abilitywar.client.fx.dragon.DragonParts.Feature(pr));
                helper.register(new com.abilitywar.client.fx.blade.BladeParts.Feature(pr));
                helper.register(new com.abilitywar.client.fx.blood.BloodParts.Feature(pr));
                helper.register(new com.abilitywar.client.fx.cantor.CantorParts.Feature(pr));
                helper.register(new com.abilitywar.client.fx.warlock.WarlockParts.Feature(pr));
                helper.register(new com.abilitywar.client.fx.angel.AngelParts.Feature(pr));
                helper.register(new com.abilitywar.client.fx.guardian.GuardianParts.Feature(pr));
                helper.register(new com.abilitywar.client.fx.paladin.PaladinParts.Feature(pr));
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(com.abilitywar.client.input.DashController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(com.abilitywar.client.screen.AbilitySelectScreen::tickPending);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null && !client.isPaused()) {
                ClientClock.tick();
                ClientState.tick();
                com.abilitywar.client.rpg.RpgClient.tick();
                CameraController.tick();
                DamageNumbers.tick();
                PlayerAnimator.tick();
            }
            InputHandler.tick();
            com.abilitywar.client.dev.TestDriver.tick(client);
            VfxManager.tick();
            PostEffects.tick();
        });

        HudRenderCallback.EVENT.register((ctx, td) -> {
            SkillHud.renderShadowHint(ctx, td);
            SkillHud.render(ctx, td);
            com.abilitywar.client.hud.RpgHud.render(ctx, td);
            DamageNumbers.renderHud(ctx, td);
        });
        WorldRenderEvents.AFTER_SETUP.register(VfxManager::beginFrame);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(VfxManager::renderWorld);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    static void reset() {
        ClientState.reset();
        com.abilitywar.client.rpg.RpgClient.reset();
        PlayerAnimator.clear();
        VfxManager.clear();
        DamageNumbers.clear();
        CameraController.reset();
        com.abilitywar.client.input.DashController.reset();
    }
}
