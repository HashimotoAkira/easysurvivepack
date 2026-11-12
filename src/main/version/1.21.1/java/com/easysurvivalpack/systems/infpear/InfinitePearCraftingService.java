package com.easysurvivalpack.systems.infpear;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class InfinitePearCraftingService {
	private static final String MARKER = "easy_survival_pack:infinite_ender_pearl";
	private static final double MAX_RADIUS = 2.0;
	private static final double MAX_DISTANCE_SQUARED = MAX_RADIUS * MAX_RADIUS;
	private static final int MAX_DROP_AGE_DIFFERENCE_TICKS = 5 * 20;

	private InfinitePearCraftingService() {
	}

	public static void tryCraft(ItemEntity pearlEntity) {
		if (pearlEntity.level().isClientSide()
				|| pearlEntity.isRemoved()
				|| !pearlEntity.getItem().is(Items.ENDER_PEARL)
				|| isInfinitePearl(pearlEntity.getItem())) {
			return;
		}

		if (!(pearlEntity.getOwner() instanceof ServerPlayer owner)) {
			return;
		}

		List<ItemEntity> nearbyItems = pearlEntity.level().getEntitiesOfClass(
				ItemEntity.class,
				pearlEntity.getBoundingBox().inflate(MAX_RADIUS),
				item -> isOwnedBy(item, owner)
						&& item.position().distanceToSqr(pearlEntity.position()) <= MAX_DISTANCE_SQUARED
						&& Math.abs(item.getAge() - pearlEntity.getAge()) <= MAX_DROP_AGE_DIFFERENCE_TICKS
		);
		if (!nearbyItems.contains(pearlEntity)) {
			return;
		}

		List<Consumption> ingredients = new ArrayList<>();
		ingredients.add(new Consumption(pearlEntity, 1));
		if (!allocate(nearbyItems, Items.AMETHYST_SHARD, 5, pearlEntity, ingredients)
				|| !allocate(nearbyItems, Items.REDSTONE, 5, pearlEntity, ingredients)
				|| !allocate(nearbyItems, Items.GLOWSTONE_DUST, 5, pearlEntity, ingredients)) {
			return;
		}

		ItemStack result = createInfinitePearl();
		ItemEntity resultEntity = new ItemEntity(
				pearlEntity.level(),
				pearlEntity.getX(),
				pearlEntity.getY(),
				pearlEntity.getZ(),
				result
		);
		if (!pearlEntity.level().addFreshEntity(resultEntity)) {
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

	private static boolean isOwnedBy(ItemEntity item, ServerPlayer owner) {
		return !item.isRemoved()
				&& item.getOwner() != null
				&& item.getOwner().getUUID().equals(owner.getUUID())
				&& !isInfinitePearl(item.getItem());
	}

	private static boolean allocate(
			List<ItemEntity> nearbyItems,
			net.minecraft.world.item.Item requiredItem,
			int requiredCount,
			ItemEntity pearlEntity,
			List<Consumption> ingredients
	) {
		int remaining = requiredCount;
		for (ItemEntity item : nearbyItems) {
			if (item == pearlEntity || !item.getItem().is(requiredItem)) {
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

	private static ItemStack createInfinitePearl() {
		ItemStack result = Items.ENDER_PEARL.getDefaultInstance();
		result.set(DataComponents.CUSTOM_NAME, Component.literal("Infinite Ender Pearl"));
		result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putBoolean(MARKER, true));
		return result;
	}

	private static boolean isInfinitePearl(ItemStack stack) {
		if (!stack.is(Items.ENDER_PEARL)) {
			return false;
		}
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getBoolean(MARKER);
	}

	private record Consumption(ItemEntity entity, int count) {
	}
}
