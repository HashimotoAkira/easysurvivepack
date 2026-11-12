package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import com.easysurvivalpack.systems.inffirework.InfiniteFireworkCraftingService;

@Mixin(ProjectileWeaponItem.class)
public abstract class InfiniteFireworkCrossbowMixin {
	@Redirect(
			method = "useAmmo",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processAmmoUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)I"
			)
	)
	private static int easySurvivalPack$preserveInfiniteFireworkWhenLoaded(
			ServerLevel level,
			ItemStack weapon,
			ItemStack projectile,
			int amount
	) {
		if (InfiniteFireworkCraftingService.isInfiniteFirework(projectile)) {
			return 0;
		}
		return EnchantmentHelper.processAmmoUse(level, weapon, projectile, amount);
	}
}
