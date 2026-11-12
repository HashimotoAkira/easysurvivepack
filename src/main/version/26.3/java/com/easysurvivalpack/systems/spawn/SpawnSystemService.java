package com.easysurvivalpack.systems.spawn;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.easysurvivalpack.EasySurvivalPack;
import com.easysurvivalpack.display.StatusDisplayService;

public final class SpawnSystemService {
	private static final AttachmentType<SpawnState> STATE = AttachmentRegistry.create(
			EasySurvivalPack.id("spawn_state"),
			builder -> builder
					.persistent(SpawnState.CODEC)
					.initializer(SpawnState::initial)
	);

	private SpawnSystemService() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				applyMaxHealth(handler.player, stateFor(handler.player).maxHealth()));
		ServerPlayerEvents.AFTER_RESPAWN.register(SpawnSystemService::afterRespawn);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpawnSystemService::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(SpawnSystemService::tick);
		SpawnSystemCommands.register();
	}

	static void setMaxHealth(ServerPlayer player, float maxHealth) {
		SpawnState state = stateFor(player).withMaxHealth(maxHealth);
		player.setAttached(STATE, state);
		applyMaxHealth(player, maxHealth);
	}

	static void setProtectionDurationTicks(ServerPlayer player, int ticks) {
		SpawnState state = stateFor(player);
		player.setAttached(
				STATE,
				state.withProtectionDurationTicks(ticks)
						.withProtectionTicks(Math.min(state.protectionTicks(), ticks))
		);
	}

	public static int getProtectionTicks(ServerPlayer player) {
		return stateFor(player).protectionTicks();
	}

	public static boolean isProtectionActive(ServerPlayer player) {
		return getProtectionTicks(player) > 0;
	}

	private static SpawnState stateFor(ServerPlayer player) {
		return player.getAttachedOrCreate(STATE);
	}

	private static void applyMaxHealth(ServerPlayer player, float maxHealth) {
		AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null) {
			EasySurvivalPack.LOGGER.error(
					"Could not find max health attribute for player {}.",
					player.getScoreboardName()
			);
			return;
		}

		attribute.setBaseValue(maxHealth);
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
	}

	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
			cancelProtection(attacker);
		}

		if (target instanceof ServerPlayer player && stateFor(player).protectionTicks() > 0) {
			return false;
		}

		return true;
	}

	private static void cancelProtection(ServerPlayer player) {
		SpawnState state = stateFor(player);
		if (state.protectionTicks() > 0) {
			player.setAttached(STATE, state.withProtectionTicks(0));
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			SpawnState state = stateFor(player);
			if (state.protectionTicks() > 0) {
				int remainingTicks = state.protectionTicks() - 1;
				if (remainingTicks > 0) {
					StatusDisplayService.publish(
							player,
							"spawn_protection",
							Component.literal(
									"SP: " + ((remainingTicks + 19) / 20) + "s (-100% damage)"
							)
					);
				}
				player.setAttached(STATE, state.withProtectionTicks(remainingTicks));
			}
		}
	}

	private static void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		SpawnState oldState = stateFor(oldPlayer);
		int protectionTicks = alive ? oldState.protectionTicks() : oldState.protectionDurationTicks();
		newPlayer.setAttached(STATE, oldState.withProtectionTicks(protectionTicks));
		applyMaxHealth(newPlayer, oldState.maxHealth());
		if (!alive) {
			newPlayer.setHealth(newPlayer.getMaxHealth());
		}
	}
}
