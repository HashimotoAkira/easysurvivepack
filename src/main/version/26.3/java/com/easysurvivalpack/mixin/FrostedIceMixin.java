package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.easysurvivalpack.systems.frostwalker.FrostWalkerService;

@Mixin(FrostedIceBlock.class)
public abstract class FrostedIceMixin {
	@Inject(
			method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void easySurvivalPack$keepFrostedIceInActiveArea(
			BlockState state,
			ServerLevel level,
			BlockPos position,
			RandomSource random,
			CallbackInfo callback
	) {
		if (FrostWalkerService.shouldKeepFrostedIce(level, position)) {
			level.scheduleTick(position, Blocks.FROSTED_ICE, 20);
			callback.cancel();
		}
	}

	@Inject(
			method = "slightlyMelt(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void easySurvivalPack$preventNeighborMeltInActiveArea(
			BlockState state,
			Level level,
			BlockPos position,
			CallbackInfoReturnable<Boolean> callback
	) {
		if (level instanceof ServerLevel serverLevel
				&& FrostWalkerService.shouldKeepFrostedIce(serverLevel, position)) {
			callback.setReturnValue(false);
		}
	}
}
