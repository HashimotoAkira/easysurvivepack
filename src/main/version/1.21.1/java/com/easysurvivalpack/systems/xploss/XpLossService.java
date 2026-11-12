package com.easysurvivalpack.systems.xploss;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.easysurvivalpack.EasySurvivalPack;

public final class XpLossService {
	private static final int PERCENT_SCALE = 100;
	private static final AttachmentType<XpLossState> STATE = AttachmentRegistry.create(
			EasySurvivalPack.id("xp_loss_state"),
			builder -> builder
					.persistent(XpLossState.CODEC)
					.initializer(XpLossState::initial)
	);

	private XpLossService() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				rebase(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> rebase(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(XpLossService::tick);
		XpLossCommands.register();
	}

	static void setReductionPercent(ServerPlayer player, int percent) {
		XpLossState state = stateFor(player)
				.withReductionPercent(percent)
				.withBaseline(getTotalExperiencePoints(player));
		player.setAttached(STATE, state);
	}

	public static void rebase(ServerPlayer player) {
		XpLossState state = stateFor(player);
		player.setAttached(STATE, state.withBaseline(getTotalExperiencePoints(player)));
	}

	private static XpLossState stateFor(ServerPlayer player) {
		return player.getAttachedOrCreate(STATE);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive()) {
				tickPlayer(player);
			}
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		XpLossState state = stateFor(player);
		int currentTotal = getTotalExperiencePoints(player);
		if (!state.initialized()) {
			player.setAttached(STATE, state.withBaseline(currentTotal));
			return;
		}

		if (currentTotal < state.previousTotalPoints() && state.reductionPercent() > 0) {
			long lostPoints = (long) state.previousTotalPoints() - currentTotal;
			long refund = (lostPoints * state.reductionPercent() + PERCENT_SCALE - 1) / PERCENT_SCALE;
			if (refund > 0) {
				player.giveExperiencePoints((int) Math.min(refund, Integer.MAX_VALUE));
				currentTotal = getTotalExperiencePoints(player);
			}
		}

		player.setAttached(STATE, state.withBaseline(currentTotal));
	}

	private static int getTotalExperiencePoints(ServerPlayer player) {
		int level = Math.max(0, player.experienceLevel);
		if (level > 30_000) {
			return Integer.MAX_VALUE;
		}

		long pointsAtLevel;
		if (level <= 16) {
			pointsAtLevel = (long) level * level + 6L * level;
		} else if (level <= 31) {
			pointsAtLevel = (5L * level * level - 81L * level + 720L) / 2L;
		} else {
			pointsAtLevel = (9L * level * level - 325L * level + 4440L) / 2L;
		}

		long pointsToNextLevel;
		if (level <= 15) {
			pointsToNextLevel = 2L * level + 7L;
		} else if (level <= 30) {
			pointsToNextLevel = 5L * level - 38L;
		} else {
			pointsToNextLevel = 9L * level - 158L;
		}

		float progress = Math.max(0.0F, Math.min(1.0F, player.experienceProgress));
		long pointsIntoLevel = (long) Math.floor(progress * pointsToNextLevel);
		return (int) Math.min(Integer.MAX_VALUE, pointsAtLevel + pointsIntoLevel);
	}
}
