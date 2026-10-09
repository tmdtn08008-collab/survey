package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;

@Mixin(PlayerRenderer.class)
public interface PlayerRendererInvoker {
	@Invoker("getArmPose")
	static HumanoidModel.ArmPose securitycraft$getArmPose(AbstractClientPlayer player, InteractionHand hand) {
		throw new AssertionError();
	}
}
