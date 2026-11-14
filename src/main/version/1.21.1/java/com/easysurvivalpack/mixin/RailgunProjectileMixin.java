package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

import com.easysurvivalpack.systems.railgun.RailgunService;

@Mixin(Projectile.class)
public abstract class RailgunProjectileMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void easySurvivalPack$applyRailgunAimAssist(CallbackInfo callback) {
		if ((Object) this instanceof AbstractArrow arrow) {
			RailgunService.applyAimAssist(arrow);
		}
	}

	@Inject(method = "onHit", at = @At("HEAD"))
	private void easySurvivalPack$createRailgunImpact(HitResult hit, CallbackInfo callback) {
		if ((Object) this instanceof AbstractArrow arrow) {
			RailgunService.handleImpact(arrow, hit);
		}
	}

	@Inject(method = "onHit", at = @At("TAIL"))
	private void easySurvivalPack$restoreRailgunArrowPhysics(HitResult hit, CallbackInfo callback) {
		if ((Object) this instanceof AbstractArrow arrow) {
			RailgunService.restoreArrowPhysicsAfterImpact(arrow, hit);
		}
	}
}
