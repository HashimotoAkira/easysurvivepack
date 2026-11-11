package com.easysurvivalpack.systems.outofcombatrecovery;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;

import com.easysurvivalpack.EasySurvivalPack;
import com.easysurvivalpack.display.StatusDisplayService;

public final class OutOfCombatRecoveryService {
	private static final int TICKS_PER_SECOND = 20;
	private static final float VANILLA_HEAL_PER_SECOND = 1.0F;
	private static final AttachmentType<RecoveryState> STATE = AttachmentRegistry.create(
			EasySurvivalPack.id("out_of_combat_recovery"),
			builder -> builder
					.persistent(RecoveryState.CODEC)
					.initializer(RecoveryState::initial)
	);

	private OutOfCombatRecoveryService() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(OutOfCombatRecoveryService::afterDamage);
		ServerPlayerEvents.AFTER_RESPAWN.register(OutOfCombatRecoveryService::afterRespawn);
		ServerTickEvents.END_SERVER_TICK.register(OutOfCombatRecoveryService::tick);
		OutOfCombatRecoveryCommands.register();
	}

	static void setCombatDelayTicks(ServerPlayer player, int ticks) {
		RecoveryState state = stateFor(player);
		player.setAttached(
				STATE,
				state.withCombatDelayTicks(ticks)
						.withTimers(Math.min(state.combatTicks(), ticks), state.environmentTicks(),
								state.ticksUntilNextHeal())
		);
	}

	static void setEnvironmentDelayTicks(ServerPlayer player, int ticks) {
		RecoveryState state = stateFor(player);
		player.setAttached(
				STATE,
				state.withEnvironmentDelayTicks(ticks)
						.withTimers(state.combatTicks(), Math.min(state.environmentTicks(), ticks),
								state.ticksUntilNextHeal())
		);
	}

	static void setHealPercentPerSecond(ServerPlayer player, float percent) {
		player.setAttached(STATE, stateFor(player).withHealPercentPerSecond(percent));
	}

	private static RecoveryState stateFor(ServerPlayer player) {
		return player.getAttachedOrCreate(STATE);
	}

	public static boolean isInCombatRecoveryDelay(ServerPlayer player) {
		return stateFor(player).combatTicks() > 0;
	}

	private static void afterDamage(
			LivingEntity entity,
			DamageSource damageSource,
			float originalDamage,
			float finalDamage,
			boolean blocked
	) {
		if (finalDamage <= 0.0F) {
			return;
		}

		if (entity instanceof ServerPlayer victim) {
			if (damageSource.getEntity() != null && damageSource.getEntity() != victim) {
				resetCombatTimer(victim);
			} else {
				resetEnvironmentTimer(victim);
			}
		}

		if (damageSource.getEntity() instanceof ServerPlayer attacker && attacker != entity) {
			resetCombatTimer(attacker);
		}
	}

	private static void resetCombatTimer(ServerPlayer player) {
		RecoveryState state = stateFor(player);
		player.setAttached(
				STATE,
				state.withTimers(state.combatDelayTicks(), 0, TICKS_PER_SECOND)
		);
	}

	private static void resetEnvironmentTimer(ServerPlayer player) {
		RecoveryState state = stateFor(player);
		player.setAttached(
				STATE,
				state.withTimers(state.combatTicks(), state.environmentDelayTicks(), TICKS_PER_SECOND)
		);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		RecoveryState state = stateFor(player);
		boolean combatWasActive = state.combatTicks() > 0;
		boolean recoveryWasBlocked = combatWasActive
				|| (!combatWasActive && state.environmentTicks() > 0);
		int combatTicks = Math.max(0, state.combatTicks() - 1);
		int environmentTicks = combatWasActive
				? 0
				: Math.max(0, state.environmentTicks() - 1);
		int ticksUntilNextHeal = state.ticksUntilNextHeal();

		if (player.isAlive() && combatTicks == 0) {
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(20.0F);
		}

		if (recoveryWasBlocked || combatTicks > 0 || environmentTicks > 0) {
			ticksUntilNextHeal = TICKS_PER_SECOND;
		} else if (player.isAlive()) {
			if (player.getHealth() < player.getMaxHealth()) {
				ticksUntilNextHeal--;
				if (ticksUntilNextHeal <= 0) {
					float configuredHealPerSecond = (float) Math.ceil(
							player.getMaxHealth() * state.healPercentPerSecond() / 100.0F
					);
					float vanillaHealPerSecond = hasVanillaNaturalRegeneration(player)
							? VANILLA_HEAL_PER_SECOND
							: 0.0F;
					float additionalHeal = Math.max(0.0F, configuredHealPerSecond - vanillaHealPerSecond);
					player.heal(additionalHeal);
					ticksUntilNextHeal = TICKS_PER_SECOND;
				}
			} else {
				ticksUntilNextHeal = TICKS_PER_SECOND;
			}
		} else {
			ticksUntilNextHeal = TICKS_PER_SECOND;
		}

		player.setAttached(
				STATE,
				state.withTimers(combatTicks, environmentTicks, ticksUntilNextHeal)
		);
		publishStatus(player, combatTicks, environmentTicks);
	}

	private static void publishStatus(ServerPlayer player, int combatTicks, int environmentTicks) {
		if (combatTicks > 0) {
			StatusDisplayService.publish(
					player,
					"recovery",
					Component.literal("Combat: " + ((combatTicks + 19) / 20) + "s")
			);
			return;
		}
		if (environmentTicks > 0) {
			StatusDisplayService.publish(
					player,
					"recovery",
					Component.literal("Env: " + ((environmentTicks + 19) / 20) + "s")
			);
			return;
		}
		if (player.isAlive()
				&& (player.getHealth() < player.getMaxHealth()
						|| player.getFoodData().getFoodLevel() < 20
						|| player.getFoodData().getSaturationLevel() < 20.0F)) {
			StatusDisplayService.publish(player, "recovery", Component.literal("Recovery"));
		}
	}

	private static boolean hasVanillaNaturalRegeneration(ServerPlayer player) {
		return player.level().getDifficulty() == Difficulty.PEACEFUL
				|| player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
	}

	private static void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		RecoveryState oldState = stateFor(oldPlayer);
		newPlayer.setAttached(STATE, oldState.withTimers(0, 0, TICKS_PER_SECOND));
	}
}
