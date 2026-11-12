package com.easysurvivalpack.systems.outofcombatrecovery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RecoveryState(
		int combatTicks,
		int environmentTicks,
		int ticksUntilNextHeal,
		int combatDelayTicks,
		int environmentDelayTicks,
		float healPercentPerSecond
) {
	public static final Codec<RecoveryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("combat_ticks", 0).forGetter(RecoveryState::combatTicks),
			Codec.INT.optionalFieldOf("environment_ticks", 0).forGetter(RecoveryState::environmentTicks),
			Codec.INT.optionalFieldOf("ticks_until_next_heal", 20).forGetter(RecoveryState::ticksUntilNextHeal),
			Codec.INT.optionalFieldOf("combat_delay_ticks", 8 * 20).forGetter(RecoveryState::combatDelayTicks),
			Codec.INT.optionalFieldOf("environment_delay_ticks", 3 * 20)
					.forGetter(RecoveryState::environmentDelayTicks),
			Codec.FLOAT.optionalFieldOf("heal_percent_per_second", 5.0F)
					.forGetter(RecoveryState::healPercentPerSecond)
	).apply(instance, RecoveryState::new));

	public static RecoveryState initial() {
		return new RecoveryState(0, 0, 20, 8 * 20, 3 * 20, 5.0F);
	}

	public RecoveryState withTimers(int combatTicks, int environmentTicks, int ticksUntilNextHeal) {
		return new RecoveryState(
				combatTicks,
				environmentTicks,
				ticksUntilNextHeal,
				combatDelayTicks,
				environmentDelayTicks,
				healPercentPerSecond
		);
	}

	public RecoveryState withCombatDelayTicks(int combatDelayTicks) {
		return new RecoveryState(
				combatTicks,
				environmentTicks,
				ticksUntilNextHeal,
				combatDelayTicks,
				environmentDelayTicks,
				healPercentPerSecond
		);
	}

	public RecoveryState withEnvironmentDelayTicks(int environmentDelayTicks) {
		return new RecoveryState(
				combatTicks,
				environmentTicks,
				ticksUntilNextHeal,
				combatDelayTicks,
				environmentDelayTicks,
				healPercentPerSecond
		);
	}

	public RecoveryState withHealPercentPerSecond(float healPercentPerSecond) {
		return new RecoveryState(
				combatTicks,
				environmentTicks,
				ticksUntilNextHeal,
				combatDelayTicks,
				environmentDelayTicks,
				healPercentPerSecond
		);
	}
}
