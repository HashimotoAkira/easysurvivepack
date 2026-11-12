package com.easysurvivalpack.systems.emergencyheal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record EmergencyHealState(
		boolean initialized,
		float previousHealth,
		int cooldownTicks,
		int respawnGraceTicks,
		int healPercent,
		int cooldownDurationTicks
) {
	public static final Codec<EmergencyHealState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("initialized").forGetter(EmergencyHealState::initialized),
			Codec.FLOAT.fieldOf("previous_health").forGetter(EmergencyHealState::previousHealth),
			Codec.INT.fieldOf("cooldown_ticks").forGetter(EmergencyHealState::cooldownTicks),
			Codec.INT.fieldOf("respawn_grace_ticks").forGetter(EmergencyHealState::respawnGraceTicks),
			Codec.INT.optionalFieldOf("heal_percent", 25).forGetter(EmergencyHealState::healPercent),
			Codec.INT.optionalFieldOf("cooldown_duration_ticks", 30 * 20)
					.forGetter(EmergencyHealState::cooldownDurationTicks)
	).apply(instance, EmergencyHealState::new));

	public static EmergencyHealState initial() {
		return new EmergencyHealState(false, 0.0F, 0, 0, 25, 30 * 20);
	}

	public EmergencyHealState withRuntime(
			boolean initialized,
			float previousHealth,
			int cooldownTicks,
			int respawnGraceTicks
	) {
		return new EmergencyHealState(
				initialized,
				previousHealth,
				cooldownTicks,
				respawnGraceTicks,
				healPercent,
				cooldownDurationTicks
		);
	}

	public EmergencyHealState withHealPercent(int healPercent) {
		return new EmergencyHealState(
				initialized,
				previousHealth,
				healPercent == 0 ? 0 : cooldownTicks,
				respawnGraceTicks,
				healPercent,
				cooldownDurationTicks
		);
	}

	public EmergencyHealState withCooldownDurationTicks(int cooldownDurationTicks) {
		return new EmergencyHealState(
				initialized,
				previousHealth,
				Math.min(cooldownTicks, cooldownDurationTicks),
				respawnGraceTicks,
				healPercent,
				cooldownDurationTicks
		);
	}
}
