package com.easysurvivalpack.systems.durability;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import com.easysurvivalpack.systems.outofcombatrecovery.OutOfCombatRecoveryService;

public final class DurabilityRepairService {
	private static final String LEVEL = "easy_survival_pack:durability_repair_level";
	private static final String LAST_TOOL_USE = "easy_survival_pack:last_repairable_tool_use";
	private static final String LORE_PREFIX = "Repair level: ";
	private static final int MAX_LEVEL = 5;
	private static final long TOOL_USE_DELAY_MILLIS = 5_000L;
	private static final int TICKS_PER_UPDATE = 20;
	private static final double MAX_RADIUS = 2.0;
	private static final double MAX_DISTANCE_SQUARED = MAX_RADIUS * MAX_RADIUS;
	private static final int MAX_DROP_AGE_DIFFERENCE_TICKS = 5 * 20;
	private static long ticks;

	private DurabilityRepairService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DurabilityRepairService::tick);
	}

	public static void tryUpgrade(ItemEntity itemEntity) {
		if (itemEntity.level().isClientSide()
				|| itemEntity.isRemoved()
				|| !itemEntity.getItem().isDamageableItem()
				|| getLevel(itemEntity.getItem()) >= MAX_LEVEL) {
			return;
		}

		if (!(itemEntity.getOwner() instanceof ServerPlayer owner)) {
			return;
		}

		List<ItemEntity> nearbyItems = itemEntity.level().getEntitiesOfClass(
				ItemEntity.class,
				itemEntity.getBoundingBox().inflate(MAX_RADIUS),
				item -> !item.isRemoved()
						&& item.getOwner() != null
						&& item.getOwner().getUUID().equals(owner.getUUID())
						&& item.position().distanceToSqr(itemEntity.position()) <= MAX_DISTANCE_SQUARED
						&& Math.abs(item.getAge() - itemEntity.getAge()) <= MAX_DROP_AGE_DIFFERENCE_TICKS
		);
		int levelsAvailable = MAX_LEVEL - getLevel(itemEntity.getItem());
		List<Consumption> ingredients = new ArrayList<>();
		int levelsToAdd = 0;
		for (ItemEntity nearbyItem : nearbyItems) {
			if (nearbyItem == itemEntity || !nearbyItem.getItem().is(Items.DRAGON_BREATH)) {
				continue;
			}

			int consumed = Math.min(levelsAvailable - levelsToAdd, nearbyItem.getItem().getCount());
			if (consumed > 0) {
				ingredients.add(new Consumption(nearbyItem, consumed));
				levelsToAdd += consumed;
			}
			if (levelsToAdd == levelsAvailable) {
				break;
			}
		}
		if (levelsToAdd == 0) {
			return;
		}

		ItemStack result = itemEntity.getItem().copyWithCount(1);
		setLevel(result, getLevel(result) + levelsToAdd);
		updateLore(result);
		result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

		ItemEntity resultEntity = new ItemEntity(
				itemEntity.level(),
				itemEntity.getX(),
				itemEntity.getY(),
				itemEntity.getZ(),
				result
		);
		resultEntity.setThrower(owner);
		if (!itemEntity.level().addFreshEntity(resultEntity)) {
			return;
		}

		consumeOne(itemEntity);
		for (Consumption ingredient : ingredients) {
			consume(ingredient.entity(), ingredient.count());
		}
	}

	private static void tick(MinecraftServer server) {
		if (++ticks % TICKS_PER_UPDATE != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			repairPlayerItems(player);
		}
	}

	private static void repairPlayerItems(ServerPlayer player) {
		if (!player.isAlive() || OutOfCombatRecoveryService.isInCombatRecoveryDelay(player)) {
			return;
		}

		boolean fallFlying = player.isFallFlying();
		long now = System.currentTimeMillis();
		NonNullList<ItemStack> inventoryItems = player.getInventory().getNonEquipmentItems();
		boolean inventoryChanged = false;
		for (int slot = 0; slot < inventoryItems.size(); slot++) {
			StackUpdate update = repairStack(inventoryItems.get(slot), fallFlying, now);
			if (update.changed()) {
				inventoryItems.set(slot, update.stack());
				inventoryChanged = true;
			}
		}
		if (inventoryChanged) {
			player.getInventory().setChanged();
		}

		repairEquipmentSlot(player, EquipmentSlot.HEAD, fallFlying, now);
		repairEquipmentSlot(player, EquipmentSlot.CHEST, fallFlying, now);
		repairEquipmentSlot(player, EquipmentSlot.LEGS, fallFlying, now);
		repairEquipmentSlot(player, EquipmentSlot.FEET, fallFlying, now);
		repairEquipmentSlot(player, EquipmentSlot.OFFHAND, fallFlying, now);
		repairContainer(player.getEnderChestInventory(), fallFlying, now);
	}

	private static void repairEquipmentSlot(ServerPlayer player, EquipmentSlot slot, boolean fallFlying, long now) {
		ItemStack current = player.getItemBySlot(slot);
		StackUpdate update = repairStack(current, fallFlying, now);
		if (update.changed()) {
			player.setItemSlot(slot, update.stack());
		}
	}

	private static void repairContainer(Container container, boolean fallFlying, long now) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			StackUpdate update = repairStack(container.getItem(slot), fallFlying, now);
			if (update.changed()) {
				container.setItem(slot, update.stack());
			}
		}
	}

	private static StackUpdate repairStack(ItemStack stack, boolean fallFlying, long now) {
		if (stack.isEmpty()) {
			return new StackUpdate(stack, false);
		}

		if (stack.isDamageableItem()) {
			int level = getLevel(stack);
			if (level <= 0 || !stack.isDamaged() || isToolRepairDelayed(stack, now)) {
				return new StackUpdate(stack, false);
			}

			int repairAmount = stack.is(Items.ELYTRA) && !fallFlying
					? (level * 3 + 1) / 2
					: level;
			stack.setDamageValue(Math.max(0, stack.getDamageValue() - repairAmount));
			return new StackUpdate(stack, true);
		}

		if (!isShulkerBox(stack)) {
			return new StackUpdate(stack, false);
		}

		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null) {
			return new StackUpdate(stack, false);
		}

		List<ItemStack> nestedItems = new ArrayList<>(contents.itemCopies().toList());
		boolean changed = false;
		for (int slot = 0; slot < nestedItems.size(); slot++) {
			StackUpdate update = repairStack(nestedItems.get(slot), fallFlying, now);
			if (update.changed()) {
				nestedItems.set(slot, update.stack());
				changed = true;
			}
		}
		if (!changed) {
			return new StackUpdate(stack, false);
		}

		ItemStack updatedShulker = stack.copy();
		updatedShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nestedItems));
		return new StackUpdate(updatedShulker, true);
	}

	private static void setLevel(ItemStack stack, int level) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(LEVEL, level));
	}

	private static int getLevel(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return Math.max(0, Math.min(MAX_LEVEL, data.copyTag().getIntOr(LEVEL, 0)));
	}

	public static void recordToolUse(ItemStack stack) {
		if (isRepairDelayedTool(stack)) {
			CustomData.update(
					DataComponents.CUSTOM_DATA,
					stack,
					tag -> tag.putLong(LAST_TOOL_USE, System.currentTimeMillis())
			);
		}
	}

	private static boolean isToolRepairDelayed(ItemStack stack, long now) {
		if (!isRepairDelayedTool(stack)) {
			return false;
		}

		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		long lastUse = data.copyTag().getLongOr(LAST_TOOL_USE, 0L);
		return lastUse > 0L && now - lastUse < TOOL_USE_DELAY_MILLIS;
	}

	private static boolean isRepairDelayedTool(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES)
				|| stack.is(ItemTags.AXES)
				|| stack.is(ItemTags.SHOVELS)
				|| stack.is(ItemTags.HOES);
	}

	private static void updateLore(ItemStack stack) {
		ItemLore currentLore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		List<Component> lines = new ArrayList<>(currentLore.lines());
		lines.removeIf(line -> line.getString().startsWith(LORE_PREFIX));
		int level = getLevel(stack);
		int elytraGroundedRate = (level * 3 + 1) / 2;
		String rate = stack.is(Items.ELYTRA)
				? level + "/s flying, " + elytraGroundedRate + "/s not flying"
				: level + "/s after recovery delay";
		lines.add(Component.literal(LORE_PREFIX + level + " (" + rate + ")"));
		stack.set(DataComponents.LORE, new ItemLore(lines));
	}

	private static boolean isShulkerBox(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ShulkerBoxBlock;
	}

	private static void consumeOne(ItemEntity itemEntity) {
		consume(itemEntity, 1);
	}

	private static void consume(ItemEntity itemEntity, int count) {
		ItemStack remaining = itemEntity.getItem().copy();
		remaining.shrink(count);
		if (remaining.isEmpty()) {
			itemEntity.discard();
		} else {
			itemEntity.setItem(remaining);
		}
	}

	private record Consumption(ItemEntity entity, int count) {
	}

	private record StackUpdate(ItemStack stack, boolean changed) {
	}
}
