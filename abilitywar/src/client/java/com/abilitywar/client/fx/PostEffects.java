package com.abilitywar.client.fx;

import com.abilitywar.AbilityWar;
import com.abilitywar.client.ClientState;
import com.abilitywar.client.camera.CameraController;
import com.abilitywar.registry.AWEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;

/** 뇌진탕(흐림/겹쳐보임/색수차) + 공허 색조 후처리. */
public final class PostEffects {
    private static final Identifier ID = AbilityWar.id("shaders/post/concussion.json");
    private static PostEffectProcessor processor;
    private static int w = -1, h = -1;
    private static boolean failed;
    private static float concussion, voidAmt;
    private static boolean hadConcussion;
    // 색조 (기본: 공허 보라)
    static final float[] VOID_DARK = {0.01f, 0f, 0.035f}, VOID_LIGHT = {0.42f, 0.22f, 0.78f};
    static final float[] FIRE_DARK = {0.05f, 0.012f, 0f}, FIRE_LIGHT = {1.0f, 0.52f, 0.2f};
    static final float[] BLADE_DARK = {0.03f, 0f, 0.045f}, BLADE_LIGHT = {0.8f, 0.45f, 1.0f};
    static final float[] CANTOR_DARK = {0.07f, 0.05f, 0.01f}, CANTOR_LIGHT = {1.0f, 0.93f, 0.7f};
    static final float[] BLOOD_DARK = {0.06f, 0f, 0.008f}, BLOOD_LIGHT = {1.0f, 0.26f, 0.24f};
    private static final float[] dark = VOID_DARK.clone(), light = VOID_LIGHT.clone();

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        float targetC = 0, targetV = 0;
        if (mc.player != null && mc.world != null) {
            StatusEffectInstance inst = mc.player.getStatusEffect(AWEffects.CONCUSSION);
            if (inst != null) targetC = Math.min(1f, inst.getDuration() / 25f);
            if (inst != null && !hadConcussion) {
                // 이명
                mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(com.abilitywar.registry.AWSounds.CONCUSSION_RING, 1.0f, 0.9f));
            }
            hadConcussion = inst != null;
            if (ClientState.shadowForm) targetV = 0.7f;
            targetV = Math.max(targetV, BlackHoleFx.proximity(mc.world, mc.gameRenderer.getCamera().getPos()) * 0.7f);
            for (Effects.UltCast u : VfxManager.find(Effects.UltCast.class)) targetV = Math.max(targetV, u.darkness(0) * 0.6f);
            boolean fire = false, blade = false, blood = false, holy = false;
            if (CameraController.isCinematic()) {
                fire = CameraController.cinematicType().startsWith("dragon");
                blade = CameraController.cinematicType().startsWith("blade");
                blood = CameraController.cinematicType().startsWith("blood");
                holy = CameraController.cinematicType().startsWith("cantor");
                targetV = Math.max(targetV, fire ? 0.32f : blade ? 0.3f : blood ? 0.42f : holy ? 0.22f : 0.45f);
            }
            float[] td = fire ? FIRE_DARK : blade ? BLADE_DARK : blood ? BLOOD_DARK : holy ? CANTOR_DARK : VOID_DARK, tl = fire ? FIRE_LIGHT : blade ? BLADE_LIGHT : blood ? BLOOD_LIGHT : holy ? CANTOR_LIGHT : VOID_LIGHT;
            for (int i = 0; i < 3; i++) {
                dark[i] += (td[i] - dark[i]) * 0.2f;
                light[i] += (tl[i] - light[i]) * 0.2f;
            }
        }
        concussion += (targetC - concussion) * (targetC > concussion ? 0.5f : 0.12f);
        voidAmt += (targetV - voidAmt) * 0.15f;
        if (concussion < 0.002f) concussion = 0;
        if (voidAmt < 0.002f) voidAmt = 0;
    }

    public static float concussion() {
        return concussion;
    }

    public static void render(float tickDelta) {
        if ((concussion <= 0 && voidAmt <= 0) || failed) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            if (processor == null) {
                processor = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), ID);
                w = h = -1;
            }
            int fw = mc.getFramebuffer().textureWidth, fh = mc.getFramebuffer().textureHeight;
            if (fw != w || fh != h) {
                processor.setupDimensions(fw, fh);
                w = fw;
                h = fh;
            }
            float time = (mc.world != null ? mc.world.getTime() : 0) + tickDelta;
            for (PostEffectPass pass : processor.passes) {
                var prog = pass.getProgram();
                prog.getUniformByNameOrDummy("Strength").set(concussion);
                prog.getUniformByNameOrDummy("VoidAmount").set(voidAmt);
                prog.getUniformByNameOrDummy("Time").set(time / 20f);
                prog.getUniformByNameOrDummy("TintDark").set(dark[0], dark[1], dark[2]);
                prog.getUniformByNameOrDummy("TintLight").set(light[0], light[1], light[2]);
            }
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            processor.render(tickDelta);
            mc.getFramebuffer().beginWrite(true);
        } catch (Exception e) {
            AbilityWar.LOG.error("Concussion shader failed; disabling", e);
            failed = true;
        }
    }

    public static void reload() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        failed = false;
    }

    private PostEffects() {
    }
}
