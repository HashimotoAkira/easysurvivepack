package com.easysurvivalpack.systems.inffirework;

import java.util.ArrayList;
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
		List<Consumption> ingredients = new ArrayList<>();
		ingredients.add(new Consumption(rocketEntity, 1));
		if (!allocate(nearbyItems, Items.AMETHYST_SHARD, 5, rocketEntity, ingredients)
				|| !allocate(nearbyItems, Items.REDSTONE, 5, rocketEntity, ingredients)
				|| !allocate(nearbyItems, Items.GLOWSTONE_DUST, 5, rocketEntity, ingredients)) {
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

		for (Consumption ingredient : ingredients) {
			ItemStack stack = ingredient.entity().getItem().copy();
			stack.shrink(ingredient.count());
			if (stack.isEmpty()) {
				ingredient.entity().discard();
			} else {
				ingredient.entity().setItem(stack);
			}
		}
	}

	public static boolean isInfiniteFirework(ItemStack stack) {
		if (!stack.is(Items.FIREWORK_ROCKET)) {
			return false;
		}
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getBoolean(MARKER);
	}

	private static boolean allocate(
			List<ItemEntity> nearbyItems,
			net.minecraft.world.item.Item requiredItem,
			int requiredCount,
			ItemEntity rocketEntity,
			List<Consumption> ingredients
	) {
		int remaining = requiredCount;
		for (ItemEntity item : nearbyItems) {
			if (item == rocketEntity || !item.getItem().is(requiredItem)) {
				continue;
			}

			int consumed = Math.min(remaining, item.getItem().getCount());
			if (consumed > 0) {
				ingredients.add(new Consumption(item, consumed));
				remaining -= consumed;
			}
			if (remaining == 0) {
				return true;
			}
		}
		return false;
	}

	private record Consumption(ItemEntity entity, int count) {
	}
}
