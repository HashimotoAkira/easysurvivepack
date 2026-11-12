package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;

import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;

@Mixin(ThrownTrident.class)
public abstract class InfiniteThrownTridentMixin {
	private static final int MAX_PROJECTILE_LIFETIME_TICKS = 5 * 20;

	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void easySurvivalPack$startThunderSkyOnEntityHit(
			EntityHitResult hit,
			CallbackInfo callback
	) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		if (hit.getEntity() instanceof LivingEntity living) {
			InfiniteTridentCraftingService.startThunderSequence(trident, hit.getLocation(), living);
		} else {
			InfiniteTridentCraftingService.startThunderSequence(trident, hit.getLocation(), null);
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void easySurvivalPack$disableInfiniteTridentPickup(CallbackInfo callback) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		if (InfiniteTridentCraftingService.isInfiniteTrident(trident.getPickupItemStackOrigin())) {
			trident.pickup = AbstractArrow.Pickup.DISALLOWED;
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void easySurvivalPack$expireInfiniteTrident(CallbackInfo callback) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		if (!(trident.level() instanceof ServerLevel)
				|| !InfiniteTridentCraftingService.isInfiniteTrident(trident.getPickupItemStackOrigin())) {
			return;
		}

		if (trident.clientSideReturnTridentTickCount > 0
				|| trident.tickCount >= MAX_PROJECTILE_LIFETIME_TICKS) {
			trident.discard();
		}
	}
}
