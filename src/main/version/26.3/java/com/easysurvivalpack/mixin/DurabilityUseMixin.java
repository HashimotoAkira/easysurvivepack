package com.easysurvivalpack.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.easysurvivalpack.systems.durability.DurabilityRepairService;

@Mixin(ItemStack.class)
public abstract class DurabilityUseMixin {
	@Inject(
			method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
			at = @At("HEAD")
	)
	private void easySurvivalPack$recordServerToolUse(
			int damage,
			ServerLevel level,
			ServerPlayer player,
			Consumer<ItemStack> onBreak,
			CallbackInfo callback
	) {
		if (player != null) {
			DurabilityRepairService.recordToolUse((ItemStack) (Object) this);
		}
	}

	@Inject(
			method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;)V",
			at = @At("HEAD")
	)
	private void easySurvivalPack$recordHandToolUse(
			int damage,
			LivingEntity entity,
			InteractionHand hand,
			CallbackInfo callback
	) {
		if (entity instanceof ServerPlayer) {
			DurabilityRepairService.recordToolUse((ItemStack) (Object) this);
		}
	}

	@Inject(
			method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
			at = @At("HEAD")
	)
	private void easySurvivalPack$recordEquipmentToolUse(
			int damage,
			LivingEntity entity,
			EquipmentSlot slot,
			CallbackInfo callback
	) {
		if (entity instanceof ServerPlayer) {
			DurabilityRepairService.recordToolUse((ItemStack) (Object) this);
		}
	}
}
