package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

@Mixin(EnderpearlItem.class)
public abstract class InfiniteEnderPearlUseMixin {
	private static final String MARKER = "easy_survival_pack:infinite_ender_pearl";

	@Redirect(
			method = "use",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"
			)
	)
	private void easySurvivalPack$preserveInfinitePearl(ItemStack stack, int amount, LivingEntity user) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		if (!data.copyTag().getBoolean(MARKER)) {
			stack.consume(amount, user);
		}
	}
}
