package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.easysurvivalpack.systems.railgun.RailgunService;

@Mixin(ArrowItem.class)
public abstract class RailgunArrowItemMixin {
	@Inject(method = "createArrow", at = @At("TAIL"))
	private void easySurvivalPack$markRailgunArrow(
			Level level,
			ItemStack arrowStack,
			LivingEntity shooter,
			ItemStack weapon,
			CallbackInfoReturnable<AbstractArrow> callback
	) {
		RailgunService.markProjectile(callback.getReturnValue(), weapon, shooter);
	}
}
