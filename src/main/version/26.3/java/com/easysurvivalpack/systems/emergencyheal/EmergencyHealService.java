package com.easysurvivalpack.systems.emergencyheal;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.easysurvivalpack.EasySurvivalPack;
import com.easysurvivalpack.display.StatusDisplayService;

public final class EmergencyHealService {
	private static final int RESPAWN_GRACE_TICKS = 100;
	private static final float TRIGGER_HEALTH_RATIO = 0.25F;

	private static final AttachmentType<EmergencyHealState> STATE = AttachmentRegistry.create(
			EasySurvivalPack.id("emergency_heal_state"),
			builder -> builder
					.persistent(EmergencyHealState.CODEC)
					.initializer(EmergencyHealState::initial)
	);

	private EmergencyHealService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(EmergencyHealService::tick);
		ServerPlayerEvents.AFTER_RESPAWN.register(EmergencyHealService::afterRespawn);
		EmergencyHealCommands.register();
	}

	static void setHealPercent(ServerPlayer player, int healPercent) {
		player.setAttached(
				STATE,
				player.getAttachedOrCreate(STATE).withHealPercent(healPercent)
		);
	}

	static void setCooldownDurationTicks(ServerPlayer player, int cooldownDurationTicks) {
		player.setAttached(
				STATE,
				player.getAttachedOrCreate(STATE).withCooldownDurationTicks(cooldownDurationTicks)
		);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		EmergencyHealState state = player.getAttachedOrCreate(STATE);
		float currentHealth = player.getHealth();
		float maxHealth = player.getMaxHealth();

		if (!state.initialized()) {
			player.setAttached(STATE, state.withRuntime(true, currentHealth, 0, 0));
			return;
		}

		if (state.respawnGraceTicks() > 0) {
			player.setAttached(
					STATE,
					state.withRuntime(true, currentHealth, 0, state.respawnGraceTicks() - 1)
			);
			publishCooldown(player, 0);
			return;
		}

		if (state.healPercent() == 0) {
			player.setAttached(STATE, state.withRuntime(true, currentHealth, 0, 0));
			return;
		}

		int cooldownTicks = Math.max(0, state.cooldownTicks() - 1);
		boolean tookDamage = currentHealth < state.previousHealth();
		boolean belowTriggerThreshold = maxHealth > 0.0F
				&& currentHealth <= maxHealth * TRIGGER_HEALTH_RATIO;

		if (player.isAlive() && cooldownTicks == 0 && tookDamage && belowTriggerThreshold) {
			player.heal(maxHealth * (state.healPercent() / 100.0F));
			currentHealth = player.getHealth();
			cooldownTicks = state.cooldownDurationTicks();
		}

		player.setAttached(STATE, state.withRuntime(true, currentHealth, cooldownTicks, 0));
		publishCooldown(player, cooldownTicks);
	}

	private static void publishCooldown(ServerPlayer player, int cooldownTicks) {
		if (cooldownTicks <= 0) {
			return;
		}
		int remainingSeconds = (cooldownTicks + 19) / 20;
		StatusDisplayService.publish(
				player,
				"emergency_heal",
				Component.literal("EH: " + remainingSeconds + "s")
		);
	}

	private static void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		EmergencyHealState oldState = oldPlayer.getAttachedOrCreate(STATE);
		if (alive) {
			newPlayer.setAttached(STATE, oldState);
			return;
		}

		newPlayer.setAttached(
				STATE,
				oldState.withRuntime(true, newPlayer.getHealth(), 0, RESPAWN_GRACE_TICKS)
		);
	}
}
