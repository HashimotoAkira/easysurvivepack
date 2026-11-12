package com.easysurvivalpack.systems.inftrident;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class InfiniteTridentCraftingService {
	private static final String MARKER = "easy_survival_pack:infinite_trident";
	private static final String CHANNELING_USED = "easy_survival_pack:channeling_used";
	private static final String RECOVERED_OVERFLOW_TAG = "easy_survival_pack:lightning_drop_overflow";
	private static final double MAX_RADIUS = 2.0;
	private static final double MAX_DISTANCE_SQUARED = MAX_RADIUS * MAX_RADIUS;
	private static final int MAX_DROP_AGE_DIFFERENCE_TICKS = 5 * 20;
	private static final long DROP_RECOVERY_DURATION_TICKS = 5 * 20L;
	private static final Map<ServerLevel, List<LightningDropArea>> ACTIVE_DROP_RECOVERY_AREAS = new HashMap<>();

	private InfiniteTridentCraftingService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				List<LightningDropArea> areas = ACTIVE_DROP_RECOVERY_AREAS.get(level);
				if (areas == null) {
					continue;
				}

				Iterator<LightningDropArea> iterator = areas.iterator();
				while (iterator.hasNext()) {
					LightningDropArea area = iterator.next();
					if (area.expiryTick() < level.getGameTime()) {
						iterator.remove();
						continue;
					}

					for (ItemEntity itemEntity : level.getEntitiesOfClass(
							ItemEntity.class,
							area.fireArea(),
							item -> !item.isRemoved() && !item.entityTags().contains(RECOVERED_OVERFLOW_TAG)
					)) {
						recoverItemIntoInventoryOrAtFeet(itemEntity, area.owner());
					}
				}
				if (areas.isEmpty()) {
					ACTIVE_DROP_RECOVERY_AREAS.remove(level);
				}
			}
		});
	}

	public static void tryCraft(ItemEntity tridentEntity) {
		if (tridentEntity.level().isClientSide()
				|| tridentEntity.isRemoved()
				|| !tridentEntity.getItem().is(Items.TRIDENT)
				|| isInfiniteTrident(tridentEntity.getItem())
				|| !(tridentEntity.getOwner() instanceof ServerPlayer owner)) {
			return;
		}

		List<ItemEntity> nearbyItems = tridentEntity.level().getEntitiesOfClass(
				ItemEntity.class,
				tridentEntity.getBoundingBox().inflate(MAX_RADIUS),
				item -> !item.isRemoved()
						&& item.getOwner() != null
						&& item.getOwner().getUUID().equals(owner.getUUID())
						&& item.position().distanceToSqr(tridentEntity.position()) <= MAX_DISTANCE_SQUARED
						&& Math.abs(item.getAge() - tridentEntity.getAge()) <= MAX_DROP_AGE_DIFFERENCE_TICKS
		);
		ItemEntity starEntity = nearbyItems.stream()
				.filter(item -> item != tridentEntity && item.getItem().is(Items.NETHER_STAR))
				.findFirst()
				.orElse(null);
		if (starEntity == null) {
			return;
		}

		ItemStack result = tridentEntity.getItem().copyWithCount(1);
		result.set(DataComponents.CUSTOM_NAME, Component.literal("Infinite Trident"));
		result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putBoolean(MARKER, true));

		ItemEntity resultEntity = new ItemEntity(
				tridentEntity.level(),
				tridentEntity.getX(),
				tridentEntity.getY(),
				tridentEntity.getZ(),
				result
		);
		if (!tridentEntity.level().addFreshEntity(resultEntity)) {
			return;
		}

		consumeOne(tridentEntity);
		consumeOne(starEntity);
	}

	public static boolean isInfiniteTrident(ItemStack stack) {
		if (!stack.is(Items.TRIDENT)) {
			return false;
		}
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getBooleanOr(MARKER, false);
	}

	public static void applyFirstChannelingStrike(
			ThrownTrident trident,
			Vec3 hitPosition
	) {
		if (!(trident.level() instanceof ServerLevel level)) {
			return;
		}

		ItemStack projectileStack = trident.getPickupItemStackOrigin();
		if (!isInfiniteTrident(projectileStack)) {
			return;
		}
		CustomData data = projectileStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		if (data.copyTag().getBooleanOr(CHANNELING_USED, false)) {
			return;
		}

		var channeling = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.CHANNELING);
		if (EnchantmentHelper.getItemEnchantmentLevel(channeling, projectileStack) <= 0) {
			return;
		}

		LightningBolt lightning = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (lightning == null) {
			return;
		}
		lightning.setPos(hitPosition.x, hitPosition.y, hitPosition.z);
		if (trident.getOwner() instanceof ServerPlayer owner) {
			lightning.setCause(owner);
		}
		if (!level.addFreshEntity(lightning)) {
			return;
		}
		if (trident.getOwner() instanceof ServerPlayer owner) {
			registerDropRecoveryArea(level, owner, BlockPos.containing(
					lightning.getX(),
					lightning.getY() - 1.0E-6,
					lightning.getZ()
			));
		}

		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
				projectileStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
		);
		enchantments.set(channeling, 0);
		projectileStack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
		CustomData.update(
				DataComponents.CUSTOM_DATA,
				projectileStack,
				tag -> tag.putBoolean(CHANNELING_USED, true)
		);
	}

	public static boolean recoverDroppedItem(ItemEntity itemEntity) {
		if (!(itemEntity.level() instanceof ServerLevel level)
				|| itemEntity.isRemoved()
				|| itemEntity.entityTags().contains(RECOVERED_OVERFLOW_TAG)) {
			return false;
		}

		List<LightningDropArea> areas = ACTIVE_DROP_RECOVERY_AREAS.get(level);
		if (areas == null) {
			return false;
		}
		for (LightningDropArea area : areas) {
			if (area.expiryTick() >= level.getGameTime() && area.fireArea().contains(itemEntity.position())) {
				recoverItemIntoInventoryOrAtFeet(itemEntity, area.owner());
				return true;
			}
		}
		return false;
	}

	private static void registerDropRecoveryArea(
			ServerLevel level,
			ServerPlayer owner,
			BlockPos strikePosition
	) {
		AABB fireArea = new AABB(
				strikePosition.getX() - 1.5,
				strikePosition.getY() - 1.5,
				strikePosition.getZ() - 1.5,
				strikePosition.getX() + 2.5,
				strikePosition.getY() + 2.5,
				strikePosition.getZ() + 2.5
		);
		ACTIVE_DROP_RECOVERY_AREAS.computeIfAbsent(level, ignored -> new ArrayList<>())
				.add(new LightningDropArea(owner, fireArea, level.getGameTime() + DROP_RECOVERY_DURATION_TICKS));
		for (ItemEntity itemEntity : level.getEntitiesOfClass(
				ItemEntity.class,
				fireArea,
				item -> !item.isRemoved() && !item.entityTags().contains(RECOVERED_OVERFLOW_TAG)
		)) {
			recoverItemIntoInventoryOrAtFeet(itemEntity, owner);
		}
	}

	private static void recoverItemIntoInventoryOrAtFeet(ItemEntity itemEntity, ServerPlayer owner) {
		ItemStack recovered = cookRawMeat(itemEntity.getItem());
		itemEntity.setItem(recovered);
		ItemStack remainder = recovered.copy();
		owner.getInventory().add(remainder);
		if (!remainder.isEmpty()) {
			ItemEntity overflow = owner.drop(remainder, false, Prediction.SERVER_ONLY);
			if (overflow == null) {
				itemEntity.setItem(remainder);
				return;
			}
			overflow.addTag(RECOVERED_OVERFLOW_TAG);
		}
		itemEntity.discard();
	}

	private static ItemStack cookRawMeat(ItemStack rawStack) {
		Item cookedItem = switch (rawStack.getItem()) {
			case Item item when item == Items.BEEF -> Items.COOKED_BEEF;
			case Item item when item == Items.PORKCHOP -> Items.COOKED_PORKCHOP;
			case Item item when item == Items.CHICKEN -> Items.COOKED_CHICKEN;
			case Item item when item == Items.MUTTON -> Items.COOKED_MUTTON;
			case Item item when item == Items.RABBIT -> Items.COOKED_RABBIT;
			case Item item when item == Items.COD -> Items.COOKED_COD;
			case Item item when item == Items.SALMON -> Items.COOKED_SALMON;
			default -> null;
		};
		return cookedItem == null ? rawStack.copy() : new ItemStack(cookedItem, rawStack.getCount());
	}

	private record LightningDropArea(ServerPlayer owner, AABB fireArea, long expiryTick) {
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
