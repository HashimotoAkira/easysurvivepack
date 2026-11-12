package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;

import com.easysurvivalpack.systems.inffirework.InfiniteFireworkCraftingService;

@Mixin(FireworkRocketItem.class)
public abstract class InfiniteFireworkItemMixin {
	@Redirect(
			method = "useOn",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"
			)
	)
	private void easySurvivalPack$preserveInfiniteFireworkOnBlockUse(ItemStack stack, int amount) {
		if (!InfiniteFireworkCraftingService.isInfiniteFirework(stack)) {
			stack.shrink(amount);
		}
	}

	@Redirect(
			method = "use",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"
			)
	)
	private void easySurvivalPack$preserveInfiniteFireworkOnUse(
			ItemStack stack,
			int amount,
			LivingEntity user
	) {
		if (!InfiniteFireworkCraftingService.isInfiniteFirework(stack)) {
			stack.consume(amount, user);
		}
	}
}
