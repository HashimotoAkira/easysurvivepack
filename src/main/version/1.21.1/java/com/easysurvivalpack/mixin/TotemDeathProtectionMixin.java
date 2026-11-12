package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import com.easysurvivalpack.systems.totem.TotemCooldownService;

@Mixin(LivingEntity.class)
public abstract class TotemDeathProtectionMixin {
	@Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
	private void easySurvivalPack$activateInventoryTotem(
			DamageSource source,
			CallbackInfoReturnable<Boolean> callback
	) {
		if ((Object) this instanceof ServerPlayer player) {
			Boolean activated = TotemCooldownService.activateOnDeath(player, source);
			if (activated) {
				callback.setReturnValue(true);
			}
		}
	}
}
