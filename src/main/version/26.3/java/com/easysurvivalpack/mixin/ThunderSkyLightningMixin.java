package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;

import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;

@Mixin(Entity.class)
public abstract class ThunderSkyLightningMixin {
	@Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true)
	private void easySurvivalPack$protectThunderSkyOwner(
			ServerLevel level,
			LightningBolt lightning,
			CallbackInfo callback
	) {
		Entity entity = (Entity) (Object) this;
		if (entity instanceof ItemEntity && InfiniteTridentCraftingService.isThunderSkyLightning(lightning)) {
			callback.cancel();
			return;
		}
		if (InfiniteTridentCraftingService.isThunderSkyLightning(lightning)
				&& lightning.getCause() instanceof ServerPlayer owner
				&& InfiniteTridentCraftingService.shouldBlockThunderSkyPvpDamage(owner, entity)) {
			callback.cancel();
			return;
		}
		if (!InfiniteTridentCraftingService.shouldProtectThunderSkyOwner(lightning, entity)) {
			return;
		}

		callback.cancel();
	}
}
