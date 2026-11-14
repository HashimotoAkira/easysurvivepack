package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import com.easysurvivalpack.systems.inffirework.InfiniteFireworkCraftingService;
import com.easysurvivalpack.systems.durability.DurabilityRepairService;
import com.easysurvivalpack.systems.infpear.InfinitePearCraftingService;
import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;

@Mixin(ItemEntity.class)
public abstract class TotemItemEntityMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void easySurvivalPack$processDroppedItem(CallbackInfo callback) {
		ItemEntity itemEntity = (ItemEntity) (Object) this;
		if (!(itemEntity.level() instanceof ServerLevel)) {
			return;
		}

		InfinitePearCraftingService.tryCraft(itemEntity);
		InfiniteFireworkCraftingService.tryCraft(itemEntity);
		InfiniteTridentCraftingService.tryCraft(itemEntity);
		DurabilityRepairService.tryUpgrade(itemEntity);
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void easySurvivalPack$recoverDropsBeforeLightningBurnsThem(CallbackInfo callback) {
		ItemEntity itemEntity = (ItemEntity) (Object) this;
		if (itemEntity.level() instanceof ServerLevel
				&& InfiniteTridentCraftingService.recoverDroppedItem(itemEntity)) {
			callback.cancel();
		}
	}
}
