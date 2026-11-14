package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

@Mixin(ItemStack.class)
public abstract class InfiniteEnderPearlMixin {
	private static final String MARKER = "easy_survival_pack:infinite_ender_pearl";

	@Inject(method = "consume", at = @At("HEAD"), cancellable = true)
	private void easySurvivalPack$preserveInfinitePearl(int amount, LivingEntity user, CallbackInfo callback) {
		ItemStack stack = (ItemStack) (Object) this;
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		if (!data.copyTag().getBooleanOr(MARKER, false)) {
			return;
		}
		callback.cancel();
	}
}
