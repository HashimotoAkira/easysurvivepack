package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;

@Mixin(TridentItem.class)
public abstract class InfiniteTridentItemMixin {
	@Redirect(
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;consumeAndReturn(ILnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"
			)
	)
	private ItemStack easySurvivalPack$keepInfiniteTridentInInventory(
			ItemStack stack,
			int amount,
			LivingEntity user
	) {
		if (InfiniteTridentCraftingService.isInfiniteTrident(stack)) {
			return InfiniteTridentCraftingService.recordThrow(stack, amount);
		}
		boolean thunderSky = InfiniteTridentCraftingService.isThunderSkyTrident(stack);
		if (thunderSky) {
			InfiniteTridentCraftingService.recordThrow(stack, amount);
		}
		return stack.consumeAndReturn(amount, user);
	}
}
