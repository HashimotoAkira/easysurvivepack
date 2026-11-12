package com.easysurvivalpack.systems.frostwalker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

public final class FrostWalkerService {
	private static final int TICKS_PER_SECOND = 20;
	private static final int LAVA_SECONDS_PER_LEVEL = 5;
	private static final int MAX_VERTICAL_WATER_DISTANCE = 2;
	private static final Map<UUID, PlayerLocation> PLAYER_LOCATIONS = new HashMap<>();

	private FrostWalkerService() {
	}

	public static void register() {
		ServerTickEvents.START_SERVER_TICK.register(FrostWalkerService::updatePlayers);
		ServerTickEvents.END_SERVER_TICK.register(FrostWalkerService::tickTemporaryLava);
	}

	private static void updatePlayers(MinecraftServer server) {
		Set<UUID> onlinePlayers = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			onlinePlayers.add(playerId);
			processPlayer(playerId, player);
		}
		PLAYER_LOCATIONS.keySet().removeIf(playerId -> !onlinePlayers.contains(playerId));
	}

	private static void tickTemporaryLava(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			TemporaryLavaData temporaryBlocks = TemporaryLavaData.get(level);
			for (BlockPos position : temporaryBlocks.positions()) {
				Long expiryTick = temporaryBlocks.getExpiryTick(position);
				if (expiryTick == null || level.getGameTime() < expiryTick || !level.hasChunkAt(position)) {
					continue;
				}

				if (level.getBlockState(position).is(Blocks.MAGMA_BLOCK)) {
					level.setBlock(position, Blocks.LAVA.defaultBlockState(), 3);
				}
				temporaryBlocks.remove(position);
			}
		}
	}

	private static void processPlayer(UUID playerId, ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		int enchantmentLevel = EnchantmentHelper.getItemEnchantmentLevel(
				level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FROST_WALKER),
				player.getItemBySlot(EquipmentSlot.FEET)
		);
		BlockPos position = player.blockPosition();
		PlayerLocation previous = PLAYER_LOCATIONS.get(playerId);
		PLAYER_LOCATIONS.put(
				playerId,
				new PlayerLocation(level, position, enchantmentLevel)
		);
		if (enchantmentLevel <= 0
				|| player.isPassenger()
				|| previous != null
						&& previous.level() == level
						&& previous.position().equals(position)
						&& previous.enchantmentLevel() == enchantmentLevel) {
			return;
		}

		int radius = Math.min(16, 3 + enchantmentLevel - 1);
		freezeNearbyWater(level, player, position, radius);
		convertNearbyLava(level, position, radius, enchantmentLevel);
	}

	public static boolean shouldKeepFrostedIce(ServerLevel level, BlockPos icePosition) {
		for (PlayerLocation location : PLAYER_LOCATIONS.values()) {
			if (location.level() != level
					|| location.enchantmentLevel() <= 0) {
				continue;
			}

			int verticalDistance = location.position().getY() - icePosition.getY();
			int offsetX = location.position().getX() - icePosition.getX();
			int offsetZ = location.position().getZ() - icePosition.getZ();
			int radius = Math.min(16, 3 + location.enchantmentLevel() - 1);
			if (verticalDistance >= 1
					&& verticalDistance <= MAX_VERTICAL_WATER_DISTANCE
					&& offsetX * offsetX + offsetZ * offsetZ <= radius * radius) {
				return true;
			}
		}
		return false;
	}

	private static void freezeNearbyWater(ServerLevel level, ServerPlayer player, BlockPos center, int radius) {
		int radiusSquared = radius * radius;
		for (int offsetX = -radius; offsetX <= radius; offsetX++) {
			for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
				if (offsetX * offsetX + offsetZ * offsetZ > radiusSquared) {
					continue;
				}

				for (int verticalDistance = 1; verticalDistance <= MAX_VERTICAL_WATER_DISTANCE; verticalDistance++) {
					BlockPos waterPosition = center.offset(offsetX, -verticalDistance, offsetZ);
					if (!level.hasChunkAt(waterPosition)
							|| !level.getBlockState(waterPosition).is(Blocks.WATER)
							|| !level.getFluidState(waterPosition).isSource()
							|| !level.isEmptyBlock(waterPosition.above())) {
						continue;
					}

					level.setBlock(waterPosition, Blocks.FROSTED_ICE.defaultBlockState(), 3);
				}
			}
		}
	}

	private static void convertNearbyLava(ServerLevel level, BlockPos center, int radius, int enchantmentLevel) {
		TemporaryLavaData temporaryBlocks = TemporaryLavaData.get(level);
		long expiryTick = level.getGameTime() + (long) LAVA_SECONDS_PER_LEVEL * enchantmentLevel * TICKS_PER_SECOND;
		int radiusSquared = radius * radius;
		for (int offsetX = -radius; offsetX <= radius; offsetX++) {
			for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
				if (offsetX * offsetX + offsetZ * offsetZ > radiusSquared) {
					continue;
				}

				BlockPos lavaPosition = center.offset(offsetX, -1, offsetZ);
				if (!level.hasChunkAt(lavaPosition)
						|| !level.getBlockState(lavaPosition).is(Blocks.LAVA)
						|| !level.getFluidState(lavaPosition).isSource()) {
					continue;
				}

				if (level.setBlock(lavaPosition, Blocks.MAGMA_BLOCK.defaultBlockState(), 3)) {
					temporaryBlocks.setExpiryTick(lavaPosition, expiryTick);
				}
			}
		}
	}

	private record PlayerLocation(
			ServerLevel level,
			BlockPos position,
			int enchantmentLevel
	) {
	}
}
