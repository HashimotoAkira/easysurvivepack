package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;

@Mixin(ThrownTrident.class)
public abstract class InfiniteThrownTridentMixin {
	@Unique
	private ItemStack easySurvivalPack$pickupStackBeforeImpact = ItemStack.EMPTY;

	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void easySurvivalPack$startThunderSkyOnEntityHit(
			EntityHitResult hit,
			CallbackInfo callback
	) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		this.easySurvivalPack$pickupStackBeforeImpact = trident.getPickupItemStackOrigin().copy();
		if (hit.getEntity() instanceof LivingEntity living) {
			InfiniteTridentCraftingService.startThunderSequence(trident, hit.getLocation(), living);
		} else {
			InfiniteTridentCraftingService.startThunderSequence(trident, hit.getLocation(), null);
		}
	}

	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void easySurvivalPack$recoverTridentAfterEntityHit(
			EntityHitResult hit,
			CallbackInfo callback
	) {
		InfiniteTridentCraftingService.handleTridentEntityImpact(
				(ThrownTrident) (Object) this,
				this.easySurvivalPack$pickupStackBeforeImpact
		);
		this.easySurvivalPack$pickupStackBeforeImpact = ItemStack.EMPTY;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void easySurvivalPack$disableInfiniteTridentPickup(CallbackInfo callback) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		InfiniteTridentCraftingService.prepareHeavensSpearProjectile(trident);
		InfiniteTridentCraftingService.applyHeavensSpearAimAssist(trident);
		if (InfiniteTridentCraftingService.isInfiniteTrident(trident.getPickupItemStackOrigin())) {
			trident.pickup = AbstractArrow.Pickup.DISALLOWED;
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void easySurvivalPack$expireInfiniteTrident(CallbackInfo callback) {
		InfiniteTridentCraftingService.handleTridentTick((ThrownTrident) (Object) this);
	}
}
