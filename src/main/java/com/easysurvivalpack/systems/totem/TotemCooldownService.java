package com.easysurvivalpack.systems.totem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.gameevent.GameEvent;

import com.easysurvivalpack.EasySurvivalPack;
import com.easysurvivalpack.display.StatusDisplayService;

public final class TotemCooldownService {
	private static final String MARKER = "easy_survival_pack:cooling_totem";
	private static final String REMAINING_MILLIS = "remaining_millis";
	private static final String UPDATED_AT = "updated_at";
	private static final String TIME_FACTOR = "time_factor";
	private static final String OWNER = "owner";
	private static final String INSTANCE_ID = "instance_id";
	private static final String INVENTORY_ONLINE_FACTOR = "inventory_online_factor";
	private static final String INVENTORY_OFFLINE_FACTOR = "inventory_offline_factor";
	private static final String CONTAINER_ONLINE_FACTOR = "container_online_factor";
	private static final String CONTAINER_OFFLINE_FACTOR = "container_offline_factor";
	private static final long BASE_COOLDOWN_MILLIS = 60 * 1000L;
	private static final int TICKS_PER_UPDATE = 20;
	private static final int CONTAINER_SCAN_TICKS = 100;
	private static long ticks;

	private enum CooldownRate {
		INVENTORY_ONLINE(INVENTORY_ONLINE_FACTOR, TotemState.DEFAULT_INVENTORY_ONLINE_FACTOR),
		INVENTORY_OFFLINE(INVENTORY_OFFLINE_FACTOR, TotemState.DEFAULT_INVENTORY_OFFLINE_FACTOR),
		CONTAINER_ONLINE(CONTAINER_ONLINE_FACTOR, TotemState.DEFAULT_CONTAINER_ONLINE_FACTOR),
		CONTAINER_OFFLINE(CONTAINER_OFFLINE_FACTOR, TotemState.DEFAULT_CONTAINER_OFFLINE_FACTOR);

		private final String dataKey;
		private final int defaultFactor;

		CooldownRate(String dataKey, int defaultFactor) {
			this.dataKey = dataKey;
			this.defaultFactor = defaultFactor;
		}

	}

	private static final AttachmentType<TotemState> STATE = AttachmentRegistry.create(
			EasySurvivalPack.id("totem_state"),
			builder -> builder
					.persistent(TotemState.CODEC)
					.initializer(TotemState::initial)
	);

	private TotemCooldownService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TotemCooldownService::tick);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				newPlayer.setAttached(STATE, stateFor(oldPlayer)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			processPlayerInventory(handler.player, CooldownRate.INVENTORY_ONLINE);
			processEnderChest(handler.player, CooldownRate.CONTAINER_ONLINE, System.currentTimeMillis());
			processOpenContainer(handler.player, server, System.currentTimeMillis(), false, null);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			long now = System.currentTimeMillis();
			processPlayerInventory(handler.player, CooldownRate.INVENTORY_OFFLINE, now);
			processEnderChest(handler.player, CooldownRate.CONTAINER_OFFLINE, now);
			processOpenContainer(handler.player, server, now, false, handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			long now = System.currentTimeMillis();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				processPlayerInventory(player, CooldownRate.INVENTORY_OFFLINE, now);
				processEnderChest(player, CooldownRate.CONTAINER_OFFLINE, now);
				processOpenContainer(player, server, now, true, null);
			}
			processLoadedContainers(server, now, true);
			processDroppedItems(server, now, true);
		});
		TotemCommands.register();
	}

	static void setCooldownSeconds(ServerPlayer player, int seconds) {
		player.setAttached(STATE, stateFor(player).withCooldownSeconds(seconds));
		shortenOwnedCooldowns(player, seconds);
	}

	private static void shortenOwnedCooldowns(ServerPlayer player, int seconds) {
		long now = System.currentTimeMillis();
		long maximumRemainingMillis = seconds * 1000L;
		UUID owner = player.getUUID();
		MinecraftServer server = player.level().getServer();
		for (ServerPlayer holder : server.getPlayerList().getPlayers()) {
			shortenPlayerCooldowns(holder, owner, maximumRemainingMillis, now);
		}

		for (ServerLevel level : server.getAllLevels()) {
			level.getChunkSource().chunkMap.forEachBlockTickingChunk(chunk ->
					chunk.getBlockEntities().values().forEach(blockEntity -> {
						if (blockEntity instanceof Container container) {
							shortenContainerCooldowns(container, owner, maximumRemainingMillis, now);
						}
					}));
			for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity itemEntity) {
					ItemStack current = itemEntity.getItem();
					ItemStack updated = shortenOwnedCooldowns(
							current,
							owner,
							maximumRemainingMillis,
							now
					);
					if (updated != current) {
						itemEntity.setItem(updated);
					}
				}
			}
		}
	}

	private static void shortenPlayerCooldowns(
			ServerPlayer holder,
			UUID owner,
			long maximumRemainingMillis,
			long now
	) {
		Inventory inventory = holder.getInventory();
		NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
		boolean inventoryChanged = false;
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack current = items.get(slot);
			ItemStack updated = shortenOwnedCooldowns(current, owner, maximumRemainingMillis, now);
			if (updated != current) {
				items.set(slot, updated);
				inventoryChanged = true;
			}
		}
		ItemStack offhand = holder.getItemBySlot(EquipmentSlot.OFFHAND);
		ItemStack updatedOffhand = shortenOwnedCooldowns(offhand, owner, maximumRemainingMillis, now);
		if (updatedOffhand != offhand) {
			holder.setItemSlot(EquipmentSlot.OFFHAND, updatedOffhand);
		}
		if (inventoryChanged) {
			inventory.setChanged();
		}

		shortenContainerCooldowns(holder.getEnderChestInventory(), owner, maximumRemainingMillis, now);
		for (Slot slot : holder.containerMenu.slots) {
			if (slot.container == inventory) {
				continue;
			}
			ItemStack current = slot.getItem();
			ItemStack updated = shortenOwnedCooldowns(current, owner, maximumRemainingMillis, now);
			if (updated != current) {
				slot.set(updated);
			}
		}
	}

	private static void shortenContainerCooldowns(
			Container container,
			UUID owner,
			long maximumRemainingMillis,
			long now
	) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack current = container.getItem(slot);
			ItemStack updated = shortenOwnedCooldowns(current, owner, maximumRemainingMillis, now);
			if (updated != current) {
				container.setItem(slot, updated);
			}
		}
	}

	private static ItemStack shortenOwnedCooldowns(
			ItemStack stack,
			UUID owner,
			long maximumRemainingMillis,
			long now
	) {
		if (isCoolingPlaceholder(stack) && owner.equals(readOwner(stack))) {
			long remainingMillis = getRemainingMillis(stack, now);
			if (maximumRemainingMillis == 0 || remainingMillis <= 0) {
				return Items.TOTEM_OF_UNDYING.getDefaultInstance();
			}
			long cappedRemainingMillis = Math.min(remainingMillis, maximumRemainingMillis);
			ItemStack updated = stack.copy();
			CustomData.update(DataComponents.CUSTOM_DATA, updated, tag -> {
				tag.putLong(REMAINING_MILLIS, cappedRemainingMillis);
				tag.putLong(UPDATED_AT, now);
			});
			updated.set(
					DataComponents.LORE,
					cooldownLore(cappedRemainingMillis * readTimeFactor(stack))
			);
			return updated;
		}

		if (!isShulkerBox(stack)) {
			return stack;
		}
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null) {
			return stack;
		}

		List<ItemStack> nestedItems = new ArrayList<>(contents.itemCopies().toList());
		boolean changed = false;
		for (int index = 0; index < nestedItems.size(); index++) {
			ItemStack nested = nestedItems.get(index);
			ItemStack updated = shortenOwnedCooldowns(nested, owner, maximumRemainingMillis, now);
			if (updated != nested) {
				nestedItems.set(index, updated);
				changed = true;
			}
		}
		if (!changed) {
			return stack;
		}

		ItemStack updatedShulker = stack.copy();
		updatedShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nestedItems));
		return updatedShulker;
	}

	static void setFactors(
			ServerPlayer player,
			int inventoryOnline,
			int inventoryOffline,
			int containerOnline,
			int containerOffline
	) {
		player.setAttached(
				STATE,
				stateFor(player).withFactors(inventoryOnline, inventoryOffline, containerOnline, containerOffline)
		);
	}

	private static TotemState stateFor(ServerPlayer player) {
		return player.getAttachedOrCreate(STATE);
	}

	public static boolean activateOnDeath(ServerPlayer player, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}

		ItemStack usedTotem = replaceReadyTotem(player);
		if (usedTotem == null) {
			return false;
		}

		DeathProtection deathProtection = usedTotem.get(DataComponents.DEATH_PROTECTION);
		if (deathProtection == null) {
			EasySurvivalPack.LOGGER.error("Totem of Undying is missing its death protection component.");
			return false;
		}

		player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
		CriteriaTriggers.USED_TOTEM.trigger(player, usedTotem);
		usedTotem.causeUseVibration(player, GameEvent.ITEM_INTERACT_FINISH);
		player.setHealth(1.0F);
		deathProtection.applyEffects(usedTotem, player);
		player.level().broadcastEntityEvent(player, (byte) 35);
		return true;
	}

	public static void updateDroppedItem(ItemStack stack, net.minecraft.world.entity.item.ItemEntity itemEntity) {
		if (itemEntity.tickCount % TICKS_PER_UPDATE != 0 || !isCoolingPlaceholder(stack)
				|| !(itemEntity.level() instanceof ServerLevel level)) {
			return;
		}

		updateDroppedItem(stack, itemEntity, level.getServer(), System.currentTimeMillis(), false);
	}

	private static void processDroppedItems(MinecraftServer server, long now, boolean allOwnersOffline) {
		for (ServerLevel level : server.getAllLevels()) {
			for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity itemEntity) {
					updateDroppedItem(itemEntity.getItem(), itemEntity, server, now, allOwnersOffline);
				}
			}
		}
	}

	private static void updateDroppedItem(
			ItemStack stack,
			ItemEntity itemEntity,
			MinecraftServer server,
			long now,
			boolean allOwnersOffline
	) {
		if (!isCoolingPlaceholder(stack)) {
			return;
		}

		UUID owner = readOwner(stack);
		ServerPlayer ownerPlayer = owner == null ? null : server.getPlayerList().getPlayer(owner);
		boolean ownerOnline = ownerPlayer != null && !allOwnersOffline;
		CooldownRate rate = ownerOnline ? CooldownRate.INVENTORY_ONLINE : CooldownRate.INVENTORY_OFFLINE;
		ItemStack updated = updateStack(stack, rate, owner, now);
		if (ownerOnline) {
			updated = shortenOwnedCooldowns(
					updated,
					owner,
					stateFor(ownerPlayer).cooldownSeconds() * 1000L,
					now
			);
		}
		if (updated != stack) {
			itemEntity.setItem(updated);
		}
	}

	private static void tick(MinecraftServer server) {
		boolean updateCooldowns = ++ticks % TICKS_PER_UPDATE == 0;
		long now = System.currentTimeMillis();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (updateCooldowns) {
				processPlayerInventory(player, CooldownRate.INVENTORY_ONLINE, now);
				processEnderChest(player, CooldownRate.CONTAINER_ONLINE, now);
				processOpenContainer(player, server, now, false, null);
			}
			publishHeldCooldown(player, now);
		}
		if (updateCooldowns && ticks % CONTAINER_SCAN_TICKS == 0) {
			processLoadedContainers(server, now, false);
		}
	}

	private static void processLoadedContainers(MinecraftServer server, long now, boolean allOwnersOffline) {
		for (ServerLevel level : server.getAllLevels()) {
			level.getChunkSource().chunkMap.forEachBlockTickingChunk(chunk ->
					chunk.getBlockEntities().values().forEach(blockEntity -> {
						if (blockEntity instanceof Container container) {
							processContainer(container, server, now, allOwnersOffline);
						}
					}));
		}
	}

	private static void processContainer(
			Container container,
			MinecraftServer server,
			long now,
			boolean allOwnersOffline
	) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack current = container.getItem(slot);
			if (!containsCooldownTotem(current)) {
				continue;
			}
			ItemStack updated = updateContainerStack(current, server, now, allOwnersOffline, null);
			if (updated != current) {
				container.setItem(slot, updated);
			}
		}
	}

	private static void processEnderChest(ServerPlayer player, CooldownRate rate, long now) {
		Container container = player.getEnderChestInventory();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack current = container.getItem(slot);
			if (!containsCooldownTotem(current)) {
				continue;
			}
			ItemStack updated = updateStack(current, rate, player.getUUID(), now);
			if (updated != current) {
				container.setItem(slot, updated);
			}
		}
	}

	private static void processPlayerInventory(ServerPlayer player, CooldownRate rate) {
		processPlayerInventory(player, rate, System.currentTimeMillis());
	}

	private static void processPlayerInventory(ServerPlayer player, CooldownRate rate, long now) {
		Inventory inventory = player.getInventory();
		NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
		boolean changed = false;
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack current = items.get(slot);
			ItemStack updated = updateStack(current, rate, player.getUUID(), now);
			if (updated != current) {
				items.set(slot, updated);
				changed = true;
			}
		}

		ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
		ItemStack updatedOffhand = updateStack(offhand, rate, player.getUUID(), now);
		if (updatedOffhand != offhand) {
			player.setItemSlot(EquipmentSlot.OFFHAND, updatedOffhand);
		}

		if (changed) {
			inventory.setChanged();
		}
	}

	private static void processOpenContainer(
			ServerPlayer player,
			MinecraftServer server,
			long now,
			boolean allOwnersOffline,
			UUID ownerGoingOffline
	) {
		for (Slot slot : player.containerMenu.slots) {
			if (slot.container == player.getInventory()) {
				continue;
			}

			ItemStack current = slot.getItem();
			if (!containsCooldownTotem(current)) {
				continue;
			}
			ItemStack updated = updateContainerStack(
					current,
					server,
					now,
					allOwnersOffline,
					ownerGoingOffline
			);
			if (updated != current) {
				slot.set(updated);
			}
		}
	}

	private static ItemStack updateStack(ItemStack stack, CooldownRate rate, UUID owner, long now) {
		if (stack.isEmpty()) {
			return stack;
		}

		ItemStack updated = stack;
		if (isCoolingPlaceholder(stack)) {
			long remaining = getRemainingMillis(stack, now);
			if (remaining <= 0) {
				return Items.TOTEM_OF_UNDYING.getDefaultInstance();
			}

			UUID effectiveOwner = owner == null ? readOwner(stack) : owner;
			int timeFactor = readConfiguredFactor(stack, rate);
			ItemStack updatedCooldownStack = stack.copy();
			CustomData.update(DataComponents.CUSTOM_DATA, updatedCooldownStack, tag -> {
				tag.putLong(REMAINING_MILLIS, remaining);
				tag.putLong(UPDATED_AT, now);
				tag.putInt(TIME_FACTOR, timeFactor);
				if (effectiveOwner != null) {
					tag.putString(OWNER, effectiveOwner.toString());
				}
			});
			updatedCooldownStack.set(DataComponents.LORE, cooldownLore(remaining * timeFactor));
			updated = updatedCooldownStack;
		}

		if (isShulkerBox(stack)) {
			ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
			if (contents != null) {
				List<ItemStack> nestedItems = new ArrayList<>(contents.itemCopies().toList());
				boolean nestedChanged = false;
				for (int index = 0; index < nestedItems.size(); index++) {
					ItemStack nested = nestedItems.get(index);
					UUID nestedOwner = readOwner(nested);
					ItemStack next = updateStack(
							nested,
							rate,
							nestedOwner == null ? owner : nestedOwner,
							now
					);
					if (next != nested) {
						nestedItems.set(index, next);
						nestedChanged = true;
					}
				}
				if (nestedChanged) {
					ItemStack updatedShulker = stack.copy();
					updatedShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nestedItems));
					updated = updatedShulker;
				}
			}
		}
		return updated;
	}

	private static ItemStack updateContainerStack(
			ItemStack stack,
			MinecraftServer server,
			long now,
			boolean allOwnersOffline,
			UUID ownerGoingOffline
	) {
		if (isCoolingPlaceholder(stack)) {
			UUID owner = readOwner(stack);
			ServerPlayer ownerPlayer = owner == null ? null : server.getPlayerList().getPlayer(owner);
			boolean ownerOnline = ownerPlayer != null && !allOwnersOffline
					&& !owner.equals(ownerGoingOffline);
			CooldownRate rate = ownerOnline
					? CooldownRate.CONTAINER_ONLINE
					: CooldownRate.CONTAINER_OFFLINE;
			ItemStack updated = updateStack(stack, rate, owner, now);
			if (ownerOnline) {
				int cooldownSeconds = stateFor(ownerPlayer).cooldownSeconds();
				return shortenOwnedCooldowns(updated, owner, cooldownSeconds * 1000L, now);
			}
			return updated;
		}

		if (!isShulkerBox(stack)) {
			return stack;
		}
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null) {
			return stack;
		}

		List<ItemStack> nestedItems = new ArrayList<>(contents.itemCopies().toList());
		boolean nestedChanged = false;
		for (int index = 0; index < nestedItems.size(); index++) {
			ItemStack nested = nestedItems.get(index);
			ItemStack next = updateContainerStack(nested, server, now, allOwnersOffline, ownerGoingOffline);
			if (next != nested) {
				nestedItems.set(index, next);
				nestedChanged = true;
			}
		}
		if (!nestedChanged) {
			return stack;
		}

		ItemStack updatedShulker = stack.copy();
		updatedShulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nestedItems));
		return updatedShulker;
	}

	private static long getRemainingMillis(ItemStack stack, long now) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		long remaining = data.copyTag().getLongOr(REMAINING_MILLIS, BASE_COOLDOWN_MILLIS);
		long updatedAt = data.copyTag().getLongOr(UPDATED_AT, now);
		int factor = Math.max(1, readTimeFactor(stack));
		long elapsed = Math.max(0, now - updatedAt);
		return Math.max(0, remaining - elapsed / factor);
	}

	private static int readTimeFactor(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return Math.max(1, data.copyTag().getIntOr(TIME_FACTOR, CooldownRate.INVENTORY_ONLINE.defaultFactor));
	}

	private static int readConfiguredFactor(ItemStack stack, CooldownRate rate) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		int factor = data.copyTag().getIntOr(rate.dataKey, rate.defaultFactor);
		return Math.max(1, Math.min(10, factor));
	}

	private static UUID readOwner(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		String value = data.copyTag().getStringOr(OWNER, "");
		if (value.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			EasySurvivalPack.LOGGER.warn("Ignoring invalid owner UUID on a cooling Totem: {}", value);
			return null;
		}
	}

	public static boolean containsCooldownTotem(ItemStack stack) {
		if (isCoolingPlaceholder(stack)) {
			return true;
		}
		if (!isShulkerBox(stack)) {
			return false;
		}
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		return contents != null && contents.nonEmptyItemCopyStream().anyMatch(TotemCooldownService::containsCooldownTotem);
	}

	private static boolean isCoolingPlaceholder(ItemStack stack) {
		if (!stack.is(Items.NETHER_STAR)) {
			return false;
		}
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getBooleanOr(MARKER, false);
	}

	private static boolean isShulkerBox(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ShulkerBoxBlock;
	}

	private static ItemStack replaceReadyTotem(ServerPlayer player) {
		long now = System.currentTimeMillis();
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.get(DataComponents.DEATH_PROTECTION) != null) {
				if (!held.is(Items.TOTEM_OF_UNDYING)) {
					return null;
				}
				ItemStack used = held.copy();
				player.setItemInHand(hand, createCooldownPlaceholder(player, now));
				return used;
			}
		}

		Inventory inventory = player.getInventory();
		NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (stack.is(Items.TOTEM_OF_UNDYING)) {
				ItemStack used = stack.copy();
				items.set(slot, createCooldownPlaceholder(player, now));
				inventory.setChanged();
				return used;
			}

			if (isShulkerBox(stack)) {
				ItemStack used = replaceInShulker(stack, player, now);
				if (used != null) {
					inventory.setChanged();
					return used;
				}
			}
		}

		ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
		if (offhand.is(Items.TOTEM_OF_UNDYING)) {
			ItemStack used = offhand.copy();
			player.setItemSlot(
					EquipmentSlot.OFFHAND,
					createCooldownPlaceholder(player, now)
			);
			return used;
		}
		return null;
	}

	private static ItemStack replaceInShulker(ItemStack shulker, ServerPlayer player, long now) {
		ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
		if (contents == null) {
			return null;
		}

		List<ItemStack> nestedItems = new ArrayList<>(contents.itemCopies().toList());
		for (int slot = 0; slot < nestedItems.size(); slot++) {
			ItemStack nested = nestedItems.get(slot);
			if (nested.is(Items.TOTEM_OF_UNDYING)) {
				ItemStack used = nested.copy();
				nestedItems.set(slot, createCooldownPlaceholder(player, now));
				shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nestedItems));
				return used;
			}
		}
		return null;
	}

	private static ItemStack createCooldownPlaceholder(ServerPlayer player, long now) {
		TotemState state = stateFor(player);
		int cooldownSeconds = state.cooldownSeconds();
		if (cooldownSeconds == 0) {
			return Items.TOTEM_OF_UNDYING.getDefaultInstance();
		}

		ItemStack placeholder = Items.NETHER_STAR.getDefaultInstance();
		placeholder.set(DataComponents.CUSTOM_NAME, Component.literal("Totem (recharging)"));
		placeholder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		placeholder.set(
				DataComponents.LORE,
				cooldownLore(cooldownSeconds * 1000L * state.inventoryOnlineFactor())
		);
		CustomData.update(DataComponents.CUSTOM_DATA, placeholder, tag -> {
			tag.putBoolean(MARKER, true);
			tag.putString(INSTANCE_ID, UUID.randomUUID().toString());
			tag.putLong(REMAINING_MILLIS, cooldownSeconds * 1000L);
			tag.putLong(UPDATED_AT, now);
			tag.putInt(TIME_FACTOR, state.inventoryOnlineFactor());
			tag.putString(OWNER, player.getUUID().toString());
			tag.putInt(INVENTORY_ONLINE_FACTOR, state.inventoryOnlineFactor());
			tag.putInt(INVENTORY_OFFLINE_FACTOR, state.inventoryOfflineFactor());
			tag.putInt(CONTAINER_ONLINE_FACTOR, state.containerOnlineFactor());
			tag.putInt(CONTAINER_OFFLINE_FACTOR, state.containerOfflineFactor());
		});
		return placeholder;
	}

	private static ItemLore cooldownLore(long remainingMillis) {
		long seconds = (Math.max(0, remainingMillis) + 999) / 1000;
		return new ItemLore(List.of(Component.literal(
				"Cooldown: " + (seconds / 60) + ":" + String.format("%02d", seconds % 60)
		)));
	}

	private static void publishHeldCooldown(ServerPlayer player, long now) {
		long shortestRemaining = Long.MAX_VALUE;
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (isCoolingPlaceholder(held)) {
				long remainingMillis = getRemainingMillis(held, now) * readTimeFactor(held);
				shortestRemaining = Math.min(shortestRemaining, remainingMillis);
			}
		}

		if (shortestRemaining != Long.MAX_VALUE) {
			long seconds = (shortestRemaining + 999) / 1000;
			StatusDisplayService.publish(
					player,
					"totem",
					Component.literal("Totem: " + (seconds / 60) + ":" + String.format("%02d", seconds % 60))
			);
		}
	}
}
