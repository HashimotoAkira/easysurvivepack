package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LightningBolt;

import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;

@Mixin(LightningBolt.class)
public abstract class ThunderSkyLightningBoltMixin {
	@Inject(method = "spawnFire", at = @At("HEAD"), cancellable = true)
	private void easySurvivalPack$preventThunderSkyFireBlocks(int attempts, CallbackInfo callback) {
		if (InfiniteTridentCraftingService.isThunderSkyLightning((LightningBolt) (Object) this)) {
			callback.cancel();
		}
	}
}
