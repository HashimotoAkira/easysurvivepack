package com.easysurvivalpack.systems.inffirework;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class InfiniteFireworkCraftingService {
	private static final String MARKER = "easy_survival_pack:infinite_firework";
	private static final double MAX_RADIUS = 2.0;
	private static final double MAX_DISTANCE_SQUARED = MAX_RADIUS * MAX_RADIUS;
	private static final int MAX_DROP_AGE_DIFFERENCE_TICKS = 5 * 20;

	private InfiniteFireworkCraftingService() {
	}

	public static void tryCraft(ItemEntity rocketEntity) {
		if (rocketEntity.level().isClientSide()
				|| rocketEntity.isRemoved()
				|| !rocketEntity.getItem().is(Items.FIREWORK_ROCKET)
				|| isInfiniteFirework(rocketEntity.getItem())) {
			return;
		}

		if (!(rocketEntity.getOwner() instanceof ServerPlayer owner)) {
			return;
		}

		List<ItemEntity> nearbyItems = rocketEntity.level().getEntitiesOfClass(
				ItemEntity.class,
				rocketEntity.getBoundingBox().inflate(MAX_RADIUS),
				item -> !item.isRemoved()
						&& item.getOwner() != null
						&& item.getOwner().getUUID().equals(owner.getUUID())
						&& item.position().distanceToSqr(rocketEntity.position()) <= MAX_DISTANCE_SQUARED
						&& Math.abs(item.getAge() - rocketEntity.getAge()) <= MAX_DROP_AGE_DIFFERENCE_TICKS
		);
		ItemEntity crystalEntity = nearbyItems.stream()
				.filter(item -> item != rocketEntity && item.getItem().is(Items.END_CRYSTAL))
				.findFirst()
				.orElse(null);
		if (crystalEntity == null) {
			return;
		}

		ItemStack result = rocketEntity.getItem().copyWithCount(1);
		result.set(DataComponents.CUSTOM_NAME, Component.literal("Infinite Firework"));
		result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putBoolean(MARKER, true));

		ItemEntity resultEntity = new ItemEntity(
				rocketEntity.level(),
				rocketEntity.getX(),
				rocketEntity.getY(),
				rocketEntity.getZ(),
				result
		);
		if (!rocketEntity.level().addFreshEntity(resultEntity)) {
			return;
		}

		consumeOne(rocketEntity);
		consumeOne(crystalEntity);
	}

	public static boolean isInfiniteFirework(ItemStack stack) {
		if (!stack.is(Items.FIREWORK_ROCKET)) {
			return false;
		}
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getBooleanOr(MARKER, false);
	}

	private static void consumeOne(ItemEntity itemEntity) {
		ItemStack remaining = itemEntity.getItem().copy();
		remaining.shrink(1);
		if (remaining.isEmpty()) {
			itemEntity.discard();
		} else {
			itemEntity.setItem(remaining);
		}
	}
}
