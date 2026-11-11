package com.easysurvivalpack.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ExperienceCommand;
import net.minecraft.server.level.ServerPlayer;

import com.easysurvivalpack.systems.xploss.XpLossService;

@Mixin(ExperienceCommand.class)
public abstract class ExperienceCommandMixin {
	@Inject(method = "setExperience", at = @At("TAIL"))
	private static void easySurvivalPack$rebaseAfterAdminSet(
			CommandSourceStack source,
			Collection<? extends ServerPlayer> players,
			int amount,
			@Coerce Object type,
			CallbackInfoReturnable<Integer> callback
	) {
		if (callback.getReturnValue() > 0) {
			for (ServerPlayer player : players) {
				XpLossService.rebase(player);
			}
		}
	}
}
