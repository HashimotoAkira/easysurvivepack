package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.item.ItemEntity;

import com.easysurvivalpack.systems.inffirework.InfiniteFireworkCraftingService;
import com.easysurvivalpack.systems.durability.DurabilityRepairService;
import com.easysurvivalpack.systems.infpear.InfinitePearCraftingService;
import com.easysurvivalpack.systems.totem.TotemCooldownService;

@Mixin(ItemEntity.class)
public abstract class TotemItemEntityMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void easySurvivalPack$updateDroppedTotemCooldown(CallbackInfo callback) {
		ItemEntity itemEntity = (ItemEntity) (Object) this;
		TotemCooldownService.updateDroppedItem(itemEntity.getItem(), itemEntity);
		InfinitePearCraftingService.tryCraft(itemEntity);
		InfiniteFireworkCraftingService.tryCraft(itemEntity);
		DurabilityRepairService.tryUpgrade(itemEntity);
	}
}
