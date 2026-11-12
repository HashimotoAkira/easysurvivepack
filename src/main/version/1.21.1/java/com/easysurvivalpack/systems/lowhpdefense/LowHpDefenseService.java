package com.easysurvivalpack.systems.lowhpdefense;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import com.easysurvivalpack.EasySurvivalPack;
import com.easysurvivalpack.display.StatusDisplayService;
import com.easysurvivalpack.systems.lowhpdefense.LowHpDefenseState.SuspendedResistance;
import com.easysurvivalpack.systems.spawn.SpawnSystemService;

public final class LowHpDefenseService {
	private static final int INFINITE_DURATION = MobEffectInstance.INFINITE_DURATION;
	private static final ThreadLocal<Boolean> INTERNAL_EFFECT_CHANGE = ThreadLocal.withInitial(() -> false);
	private static final AttachmentType<LowHpDefenseState> STATE = AttachmentRegistry.create(
			EasySurvivalPack.id("low_hp_defense_state"),
			builder -> builder
					.persistent(LowHpDefenseState.CODEC)
					.initializer(LowHpDefenseState::initial)
	);

	private LowHpDefenseService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LowHpDefenseService::tick);
	}

	public static boolean interceptExternalResistance(ServerPlayer player, MobEffectInstance incoming) {
		if (INTERNAL_EFFECT_CHANGE.get()) {
			return false;
		}

		LowHpDefenseState state = stateFor(player);
		MobEffectInstance active = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		boolean modOwnsActive = isManagedEffect(active, state.appliedTier());
		int requiredTier = getRequiredTier(player.getHealth(), player.getMaxHealth());

		if (requiredTier > 0 && incoming.getAmplifier() < requiredTier - 1) {
			state = state.withSuspendedEffects(addSuspendedEffect(
					state.suspendedEffects(),
					toSuspendedResistance(incoming)
			));
			player.setAttached(STATE, state);
			return true;
		}

		if (modOwnsActive) {
			removeEffectInternally(player);
			state = state.withAppliedTier(0);
			player.setAttached(STATE, state);
		}
		return false;
	}

	public static void onExternalResistanceRemoved(ServerPlayer player) {
		if (!INTERNAL_EFFECT_CHANGE.get()) {
			clearSuspendedEffects(player);
		}
	}

	public static void onExternalEffectsCleared(ServerPlayer player) {
		if (!INTERNAL_EFFECT_CHANGE.get()) {
			clearSuspendedEffects(player);
		}
	}

	private static void clearSuspendedEffects(ServerPlayer player) {
		LowHpDefenseState state = stateFor(player);
		if (!state.suspendedEffects().isEmpty()) {
			player.setAttached(STATE, state.withSuspendedEffects(List.of()));
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		LowHpDefenseState state = stateFor(player);
		LowHpDefenseState previousState = state;
		List<SuspendedResistance> suspendedEffects = tickSuspendedEffects(state.suspendedEffects());
		state = state.withSuspendedEffects(suspendedEffects);

		int requiredTier = SpawnSystemService.isProtectionActive(player)
				? 0
				: getRequiredTier(player.getHealth(), player.getMaxHealth());
		MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		boolean modOwnsActive = isManagedEffect(resistance, state.appliedTier());

		if (state.appliedTier() > 0 && !modOwnsActive) {
			state = state.withAppliedTier(0);
		}

		boolean suspendedEffectHasPriority = hasEligibleSuspendedEffect(
				state.suspendedEffects(),
				requiredTier
		);
		if (modOwnsActive && (state.appliedTier() != requiredTier || suspendedEffectHasPriority)) {
			removeEffectInternally(player);
			resistance = null;
			state = state.withAppliedTier(0);
			modOwnsActive = false;
		}

		if (requiredTier > 0) {
			if (!modOwnsActive && resistance != null && resistance.getAmplifier() < requiredTier - 1) {
				state = state.withSuspendedEffects(addSuspendedEffect(
						state.suspendedEffects(),
						toSuspendedResistance(resistance)
				));
				removeEffectInternally(player);
				resistance = null;
			}

			if (resistance == null && hasEligibleSuspendedEffect(state.suspendedEffects(), requiredTier)) {
				List<SuspendedResistance> remaining = restoreSuspendedEffects(
						player,
						state.suspendedEffects(),
						requiredTier
				);
				state = state.withSuspendedEffects(remaining);
				resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
			}

			if (resistance == null) {
				boolean applied = applyManagedEffect(player, requiredTier);
				state = state.withAppliedTier(applied ? requiredTier : 0);
			} else if (!modOwnsActive && resistance.getAmplifier() >= requiredTier - 1) {
				state = state.withAppliedTier(0);
			} else if (modOwnsActive && resistance.getDuration() != INFINITE_DURATION) {
				boolean applied = applyManagedEffect(player, requiredTier);
				state = state.withAppliedTier(applied ? requiredTier : 0);
			}
		} else {
			if (modOwnsActive) {
				removeEffectInternally(player);
				resistance = null;
				state = state.withAppliedTier(0);
			}

			if (resistance == null && !state.suspendedEffects().isEmpty()) {
				state = state.withSuspendedEffects(restoreSuspendedEffects(
						player,
						state.suspendedEffects(),
						0
				));
			}
		}

		if (!state.equals(previousState)) {
			player.setAttached(STATE, state);
		}
		publishResistance(player);
	}

	private static List<SuspendedResistance> tickSuspendedEffects(List<SuspendedResistance> effects) {
		List<SuspendedResistance> remaining = new ArrayList<>(effects.size());
		for (SuspendedResistance effect : effects) {
			if (effect.duration() == INFINITE_DURATION) {
				remaining.add(effect);
			} else if (effect.duration() > 1) {
				remaining.add(new SuspendedResistance(
						effect.amplifier(),
						effect.duration() - 1,
						effect.ambient(),
						effect.visible(),
						effect.showIcon()
				));
			}
		}
		return remaining;
	}

	private static List<SuspendedResistance> addSuspendedEffect(
			List<SuspendedResistance> current,
			SuspendedResistance incoming
	) {
		List<SuspendedResistance> updated = new ArrayList<>(current);
		for (int index = 0; index < updated.size(); index++) {
			SuspendedResistance existing = updated.get(index);
			if (existing.amplifier() == incoming.amplifier()) {
				if (durationAtLeast(existing.duration(), incoming.duration())) {
					return updated;
				}
				updated.set(index, incoming);
				return updated;
			}
		}
		updated.add(incoming);
		return updated;
	}

	private static boolean durationAtLeast(int first, int second) {
		return first == INFINITE_DURATION || (second != INFINITE_DURATION && first >= second);
	}

	private static SuspendedResistance toSuspendedResistance(MobEffectInstance effect) {
		return new SuspendedResistance(
				effect.getAmplifier(),
				effect.getDuration(),
				effect.isAmbient(),
				effect.isVisible(),
				effect.showIcon()
		);
	}

	private static boolean hasEligibleSuspendedEffect(
			List<SuspendedResistance> effects,
			int requiredTier
	) {
		return effects.stream().anyMatch(effect ->
				requiredTier == 0 || effect.amplifier() >= requiredTier - 1);
	}

	private static List<SuspendedResistance> restoreSuspendedEffects(
			ServerPlayer player,
			List<SuspendedResistance> effects,
			int requiredTier
	) {
		List<SuspendedResistance> eligible = new ArrayList<>();
		List<SuspendedResistance> remaining = new ArrayList<>();
		for (SuspendedResistance effect : effects) {
			if (requiredTier == 0 || effect.amplifier() >= requiredTier - 1) {
				eligible.add(effect);
			} else {
				remaining.add(effect);
			}
		}

		MobEffectInstance hidden = null;
		for (SuspendedResistance effect : eligible.stream()
				.sorted(Comparator.comparingInt(SuspendedResistance::amplifier))
				.toList()) {
			hidden = new MobEffectInstance(
					MobEffects.DAMAGE_RESISTANCE,
					effect.duration(),
					effect.amplifier(),
					effect.ambient(),
					effect.visible(),
					effect.showIcon(),
					hidden
			);
		}
		if (hidden != null) {
			MobEffectInstance restoredEffect = hidden;
			runInternalEffectChange(() -> player.addEffect(restoredEffect));
		}
		return remaining;
	}

	private static int getRequiredTier(float health, float maxHealth) {
		if (maxHealth <= 0.0F || health <= 0.0F) {
			return 0;
		}
		float healthRatio = health / maxHealth;
		if (healthRatio <= 0.125F) {
			return 3;
		}
		if (healthRatio <= 0.25F) {
			return 2;
		}
		if (healthRatio <= 0.5F) {
			return 1;
		}
		return 0;
	}

	private static boolean isManagedEffect(MobEffectInstance effect, int appliedTier) {
		return appliedTier > 0 && effect != null && effect.getAmplifier() == appliedTier - 1;
	}

	private static boolean applyManagedEffect(ServerPlayer player, int tier) {
		removeEffectInternally(player);
		return runInternalEffectChange(() -> player.addEffect(new MobEffectInstance(
				MobEffects.DAMAGE_RESISTANCE,
				INFINITE_DURATION,
				tier - 1,
				true,
				false,
				true
		)));
	}

	private static void removeEffectInternally(ServerPlayer player) {
		if (player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
			runInternalEffectChange(() -> player.removeEffect(MobEffects.DAMAGE_RESISTANCE));
		}
	}

	private static boolean runInternalEffectChange(java.util.function.Supplier<Boolean> action) {
		boolean previous = INTERNAL_EFFECT_CHANGE.get();
		INTERNAL_EFFECT_CHANGE.set(true);
		try {
			return action.get();
		} finally {
			INTERNAL_EFFECT_CHANGE.set(previous);
		}
	}

	private static void runInternalEffectChange(Runnable action) {
		boolean previous = INTERNAL_EFFECT_CHANGE.get();
		INTERNAL_EFFECT_CHANGE.set(true);
		try {
			action.run();
		} finally {
			INTERNAL_EFFECT_CHANGE.set(previous);
		}
	}

	private static LowHpDefenseState stateFor(ServerPlayer player) {
		return player.getAttachedOrCreate(STATE);
	}

	private static void publishResistance(ServerPlayer player) {
		MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		if (resistance == null) {
			return;
		}
		int resistancePercent = Math.min(100, (resistance.getAmplifier() + 1) * 20);
		StatusDisplayService.publish(
				player,
				"defense",
				Component.literal("DEF " + resistancePercent + "%")
		);
	}
}
