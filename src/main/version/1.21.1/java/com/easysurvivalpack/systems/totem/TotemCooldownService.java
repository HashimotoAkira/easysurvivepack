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
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.gameevent.GameEvent;

import com.easysurvivalpack.compat.TrinketsSupport;
import com.easysurvivalpack.EasySurvivalPack;
import com.easysurvivalpack.display.StatusDisplayService;

public final class TotemCooldownService {
	private static final String MARKER = "easy_survival_pack:cooling_totem";
	private static final String REMAINING_MILLIS = "remaining_millis";
	private static final String UPDATED_AT = "updated_at";
	private static final String TIME_FACTOR = "time_factor";
	private static final String RATE_CONTEXT = "rate_context";
	private static final String PAUSED = "paused";
	private static final String OWNER = "owner";
	private static final String INSTANCE_ID = "instance_id";
	private static final String INVENTORY_ONLINE_FACTOR = "inventory_online_factor";
	private static final String INVENTORY_OFFLINE_FACTOR = "inventory_offline_factor";
	private static final String CONTAINER_ONLINE_FACTOR = "container_online_factor";
	private static final String CONTAINER_OFFLINE_FACTOR = "container_offline_factor";
	private static final long BASE_COOLDOWN_MILLIS = 60 * 1000L;
	private static final int TICKS_PER_UPDATE = 20;
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
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			long now = System.currentTimeMillis();
			processPlayerInventory(handler.player, CooldownRate.INVENTORY_OFFLINE, now);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			long now = System.currentTimeMillis();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				processPlayerInventory(player, CooldownRate.INVENTORY_OFFLINE, now);
			}
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

	}

	private static void shortenPlayerCooldowns(
			ServerPlayer holder,
			UUID owner,
			long maximumRemainingMillis,
			long now
	) {
		Inventory inventory = holder.getInventory();
		NonNullList<ItemStack> items = inventory.items;
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
		TrinketsSupport.forEachEquipped(holder, (stack, setStack) -> {
			ItemStack updated = shortenOwnedCooldowns(stack, owner, maximumRemainingMillis, now);
			if (updated != stack) {
				setStack.accept(updated);
			}
		});
		if (inventoryChanged) {
			inventory.setChanged();
		}

	}

	private static ItemStack shortenOwnedCooldowns(
			ItemStack stack,
			UUID owner,
			long maximumRemainingMillis,
			long now
	) {
		if (isCoolingPlaceholder(stack) && owner.equals(readOwner(stack))) {
			long remainingMillis = getRemainingMillis(stack, now, TICKS_PER_UPDATE * 50L);
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

		List<ItemStack> nestedItems = new ArrayList<>(contents.stream().map(ItemStack::copy).toList());
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

		player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
		CriteriaTriggers.USED_TOTEM.trigger(player, usedTotem);
		player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
		player.setHealth(1.0F);
		player.removeAllEffects();
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.REGENERATION,
				900,
				1
		));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.ABSORPTION,
				100,
				1
		));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
				800
		));
		player.level().broadcastEntityEvent(player, (byte) 35);
		return true;
	}

	private static void tick(MinecraftServer server) {
		boolean updateCooldowns = ++ticks % TICKS_PER_UPDATE == 0;
		long now = System.currentTimeMillis();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (updateCooldowns) {
				processPlayerInventory(player, CooldownRate.INVENTORY_ONLINE, now);
			}
			publishHeldCooldown(player, now);
		}
	}

	private static void processPlayerInventory(ServerPlayer player, CooldownRate rate) {
		processPlayerInventory(player, rate, System.currentTimeMillis());
	}

	private static void processPlayerInventory(ServerPlayer player, CooldownRate rate, long now) {
		Inventory inventory = player.getInventory();
		NonNullList<ItemStack> items = inventory.items;
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
		TrinketsSupport.forEachEquipped(player, (stack, setStack) -> {
			ItemStack updated = updateStack(stack, rate, player.getUUID(), now);
			if (updated != stack) {
				setStack.accept(updated);
			}
		});

		if (changed) {
			inventory.setChanged();
		}
	}

	private static ItemStack updateStack(ItemStack stack, CooldownRate rate, UUID owner, long now) {
		if (stack.isEmpty()) {
			return stack;
		}

		ItemStack updated = stack;
		if (isCoolingPlaceholder(stack)) {
			int timeFactor = readConfiguredFactor(stack, rate);
			long maxElapsed = isMatchingOfflineContext(stack, rate)
					? Long.MAX_VALUE
					: TICKS_PER_UPDATE * 50L;
			long remaining = getRemainingMillis(stack, now, maxElapsed);
			if (remaining <= 0) {
				return Items.TOTEM_OF_UNDYING.getDefaultInstance();
			}

			UUID effectiveOwner = owner == null ? readOwner(stack) : owner;
			ItemStack updatedCooldownStack = stack.copy();
			CustomData.update(DataComponents.CUSTOM_DATA, updatedCooldownStack, tag -> {
				tag.putLong(REMAINING_MILLIS, remaining);
				tag.putLong(UPDATED_AT, now);
				tag.putInt(TIME_FACTOR, timeFactor);
				tag.putString(RATE_CONTEXT, rate.name());
				tag.putBoolean(PAUSED, false);
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
				List<ItemStack> nestedItems = new ArrayList<>(contents.stream().map(ItemStack::copy).toList());
				boolean nestedChanged = false;
				for (int index = 0; index < nestedItems.size(); index++) {
					ItemStack nested = nestedItems.get(index);
					UUID nestedOwner = readOwner(nested);
					ItemStack next = updateStack(
							nested,
							nestedRate(rate),
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

	private static CooldownRate nestedRate(CooldownRate rate) {
		return switch (rate) {
			case INVENTORY_ONLINE, CONTAINER_ONLINE -> CooldownRate.CONTAINER_ONLINE;
			case INVENTORY_OFFLINE, CONTAINER_OFFLINE -> CooldownRate.CONTAINER_OFFLINE;
		};
	}

	private static long getRemainingMillis(ItemStack stack, long now) {
		return getRemainingMillis(stack, now, TICKS_PER_UPDATE * 50L);
	}

	private static boolean isMatchingOfflineContext(ItemStack stack, CooldownRate rate) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		String previousRate = data.copyTag().getString(RATE_CONTEXT);
		return switch (rate) {
			case INVENTORY_ONLINE -> previousRate.equals(CooldownRate.INVENTORY_OFFLINE.name());
			case CONTAINER_ONLINE -> previousRate.equals(CooldownRate.CONTAINER_OFFLINE.name());
			case INVENTORY_OFFLINE, CONTAINER_OFFLINE -> false;
		};
	}

	private static long getRemainingMillis(ItemStack stack, long now, long maxElapsed) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		long remaining = data.copyTag().contains(REMAINING_MILLIS)
				? data.copyTag().getLong(REMAINING_MILLIS)
				: BASE_COOLDOWN_MILLIS;
		if (data.copyTag().getBoolean(PAUSED)) {
			return Math.max(0, remaining);
		}
		long updatedAt = data.copyTag().contains(UPDATED_AT) ? data.copyTag().getLong(UPDATED_AT) : now;
		int factor = Math.max(1, readTimeFactor(stack));
		long elapsed = Math.min(maxElapsed, Math.max(0, now - updatedAt));
		return Math.max(0, remaining - elapsed / factor);
	}

	private static int readTimeFactor(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return Math.max(
				1,
				data.copyTag().contains(TIME_FACTOR)
						? data.copyTag().getInt(TIME_FACTOR)
						: CooldownRate.INVENTORY_ONLINE.defaultFactor
		);
	}

	private static int readConfiguredFactor(ItemStack stack, CooldownRate rate) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		int factor = data.copyTag().contains(rate.dataKey)
				? data.copyTag().getInt(rate.dataKey)
				: rate.defaultFactor;
		return Math.max(1, Math.min(10, factor));
	}

	private static UUID readOwner(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		String value = data.copyTag().getString(OWNER);
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
		return contents != null && contents.nonEmptyStream().anyMatch(TotemCooldownService::containsCooldownTotem);
	}

	private static boolean isCoolingPlaceholder(ItemStack stack) {
		if (!stack.is(Items.NETHER_STAR)) {
			return false;
		}
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getBoolean(MARKER);
	}

	private static boolean isShulkerBox(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof ShulkerBoxBlock;
	}

	private static ItemStack replaceReadyTotem(ServerPlayer player) {
		long now = System.currentTimeMillis();
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(Items.TOTEM_OF_UNDYING)) {
				ItemStack used = held.copy();
				player.setItemInHand(hand, createCooldownPlaceholder(player, now, CooldownRate.INVENTORY_ONLINE));
				return used;
			}
		}

		Inventory inventory = player.getInventory();
		NonNullList<ItemStack> items = inventory.items;
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (stack.is(Items.TOTEM_OF_UNDYING)) {
				ItemStack used = stack.copy();
				items.set(slot, createCooldownPlaceholder(player, now, CooldownRate.INVENTORY_ONLINE));
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
					createCooldownPlaceholder(player, now, CooldownRate.INVENTORY_ONLINE)
			);
			return used;
		}

		ItemStack[] usedTrinketTotem = {null};
		TrinketsSupport.forEachEquipped(player, (stack, setStack) -> {
			if (usedTrinketTotem[0] != null) {
				return;
			}
			if (stack.is(Items.TOTEM_OF_UNDYING)) {
				usedTrinketTotem[0] = stack.copy();
				setStack.accept(createCooldownPlaceholder(player, now, CooldownRate.INVENTORY_ONLINE));
			} else if (isShulkerBox(stack)) {
				usedTrinketTotem[0] = replaceInShulker(stack, player, now);
			}
		});
		return usedTrinketTotem[0];
	}

	private static ItemStack replaceInShulker(ItemStack shulker, ServerPlayer player, long now) {
		ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
		if (contents == null) {
			return null;
		}

		List<ItemStack> nestedItems = new ArrayList<>(contents.stream().map(ItemStack::copy).toList());
		for (int slot = 0; slot < nestedItems.size(); slot++) {
			ItemStack nested = nestedItems.get(slot);
			if (nested.is(Items.TOTEM_OF_UNDYING)) {
				ItemStack used = nested.copy();
				nestedItems.set(slot, createCooldownPlaceholder(player, now, CooldownRate.CONTAINER_ONLINE));
				shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(nestedItems));
				return used;
			}
		}
		return null;
	}

	private static ItemStack createCooldownPlaceholder(ServerPlayer player, long now, CooldownRate rate) {
		TotemState state = stateFor(player);
		int cooldownSeconds = state.cooldownSeconds();
		if (cooldownSeconds == 0) {
			return Items.TOTEM_OF_UNDYING.getDefaultInstance();
		}
		int timeFactor = switch (rate) {
			case INVENTORY_ONLINE -> state.inventoryOnlineFactor();
			case INVENTORY_OFFLINE -> state.inventoryOfflineFactor();
			case CONTAINER_ONLINE -> state.containerOnlineFactor();
			case CONTAINER_OFFLINE -> state.containerOfflineFactor();
		};

		ItemStack placeholder = Items.NETHER_STAR.getDefaultInstance();
		placeholder.set(DataComponents.CUSTOM_NAME, Component.literal("Totem (recharging)"));
		placeholder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		placeholder.set(
				DataComponents.LORE,
				cooldownLore(cooldownSeconds * 1000L * timeFactor)
		);
		CustomData.update(DataComponents.CUSTOM_DATA, placeholder, tag -> {
			tag.putBoolean(MARKER, true);
			tag.putString(INSTANCE_ID, UUID.randomUUID().toString());
			tag.putLong(REMAINING_MILLIS, cooldownSeconds * 1000L);
			tag.putLong(UPDATED_AT, now);
			tag.putInt(TIME_FACTOR, timeFactor);
			tag.putString(RATE_CONTEXT, rate.name());
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
