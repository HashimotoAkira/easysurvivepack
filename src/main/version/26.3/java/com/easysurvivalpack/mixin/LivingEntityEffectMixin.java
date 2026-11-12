package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.easysurvivalpack.systems.lowhpdefense.LowHpDefenseService;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {
	@Inject(
			method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void easySurvivalPack$manageExternalResistance(
			MobEffectInstance effect,
			Entity source,
			CallbackInfoReturnable<Boolean> callback
	) {
		if ((Object) this instanceof ServerPlayer player
				&& effect.getEffect().is(MobEffects.RESISTANCE)
				&& LowHpDefenseService.interceptExternalResistance(player, effect)) {
			callback.setReturnValue(false);
		}
	}

	@Inject(
			method = "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void easySurvivalPack$manageForcedExternalResistance(
			MobEffectInstance effect,
			Entity source,
			CallbackInfo callback
	) {
		if ((Object) this instanceof ServerPlayer player
				&& effect.getEffect().is(MobEffects.RESISTANCE)
				&& LowHpDefenseService.interceptExternalResistance(player, effect)) {
			callback.cancel();
		}
	}

	@Inject(method = "removeEffect", at = @At("HEAD"))
	private void easySurvivalPack$clearSuspendedResistance(
			Holder<MobEffect> effect,
			CallbackInfoReturnable<Boolean> callback
	) {
		if ((Object) this instanceof ServerPlayer player && effect.is(MobEffects.RESISTANCE)) {
			LowHpDefenseService.onExternalResistanceRemoved(player);
		}
	}

	@Inject(method = "removeAllEffects", at = @At("HEAD"))
	private void easySurvivalPack$clearSuspendedEffects(CallbackInfoReturnable<Boolean> callback) {
		if ((Object) this instanceof ServerPlayer player) {
			LowHpDefenseService.onExternalEffectsCleared(player);
		}
	}
}
