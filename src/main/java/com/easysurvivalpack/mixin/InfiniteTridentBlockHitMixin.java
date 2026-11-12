package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.BlockHitResult;

import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;

@Mixin(AbstractArrow.class)
public abstract class InfiniteTridentBlockHitMixin {
	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void easySurvivalPack$strikeLightningOnFirstBlockHit(
			BlockHitResult hit,
			CallbackInfo callback
	) {
		if ((Object) this instanceof ThrownTrident trident) {
			InfiniteTridentCraftingService.applyFirstChannelingStrike(trident, hit.getLocation());
		}
	}
}
