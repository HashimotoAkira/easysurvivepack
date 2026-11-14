package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;

@Mixin(TridentItem.class)
public abstract class InfiniteTridentItemMixin {
	@Redirect(
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Inventory;removeItem(Lnet/minecraft/world/item/ItemStack;)V"
			)
	)
	private void easySurvivalPack$keepInfiniteTridentInInventory(Inventory inventory, ItemStack stack) {
		if (InfiniteTridentCraftingService.isThunderSkyTrident(stack)) {
			InfiniteTridentCraftingService.recordThrow(stack, 1);
		}
		if (!InfiniteTridentCraftingService.isInfiniteTrident(stack)) {
			inventory.removeItem(stack);
		}
	}
}
