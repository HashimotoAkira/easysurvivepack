package com.easysurvivalpack.display;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class StatusDisplayService {
	private static final Map<UUID, Map<String, Component>> CURRENT_MESSAGES = new HashMap<>();
	private static final java.util.Set<UUID> DISPLAYED_PLAYERS = new HashSet<>();

	private StatusDisplayService() {
	}

	public static void register() {
		ServerTickEvents.START_SERVER_TICK.register(server -> CURRENT_MESSAGES.clear());
		ServerTickEvents.END_SERVER_TICK.register(StatusDisplayService::render);
	}

	public static void publish(ServerPlayer player, String key, Component message) {
		CURRENT_MESSAGES.computeIfAbsent(player.getUUID(), ignored -> new TreeMap<>())
				.put(key, message);
	}

	private static void render(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<String, Component> messages = CURRENT_MESSAGES.get(player.getUUID());
			if (messages == null || messages.isEmpty()) {
				if (DISPLAYED_PLAYERS.remove(player.getUUID())) {
					player.displayClientMessage(Component.empty(), true);
				}
				continue;
			}

			MutableComponent display = Component.empty();
			boolean first = true;
			for (Component message : messages.values()) {
				if (!first) {
					display.append(Component.literal(" | "));
				}
				display.append(message);
				first = false;
			}
			player.displayClientMessage(display, true);
			DISPLAYED_PLAYERS.add(player.getUUID());
		}
	}
}
