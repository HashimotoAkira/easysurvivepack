package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.BlockHitResult;

import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;

@Mixin(AbstractArrow.class)
public abstract class InfiniteTridentBlockHitMixin {
	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void easySurvivalPack$startThunderSkyOnBlockHit(
			BlockHitResult hit,
			CallbackInfo callback
	) {
		if ((Object) this instanceof ThrownTrident trident) {
			InfiniteTridentCraftingService.startThunderSequence(trident, hit.getLocation(), null);
		}
	}

	@Inject(method = "onHitBlock", at = @At("TAIL"))
	private void easySurvivalPack$discardInfiniteTridentAfterBlockHit(
			BlockHitResult hit,
			CallbackInfo callback
	) {
		if ((Object) this instanceof ThrownTrident trident) {
			InfiniteTridentCraftingService.discardInfiniteTridentOnImpact(trident);
		}
	}
}
