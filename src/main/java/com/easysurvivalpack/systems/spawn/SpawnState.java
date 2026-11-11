package com.easysurvivalpack.systems.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SpawnState(float maxHealth, int protectionTicks, int protectionDurationTicks) {
	public static final Codec<SpawnState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.optionalFieldOf("max_health", 40.0F).forGetter(SpawnState::maxHealth),
			Codec.INT.optionalFieldOf("protection_ticks", 0).forGetter(SpawnState::protectionTicks),
			Codec.INT.optionalFieldOf("protection_duration_ticks", 5 * 20)
					.forGetter(SpawnState::protectionDurationTicks)
	).apply(instance, SpawnState::new));

	public static SpawnState initial() {
		return new SpawnState(40.0F, 0, 5 * 20);
	}

	public SpawnState withMaxHealth(float maxHealth) {
		return new SpawnState(maxHealth, protectionTicks, protectionDurationTicks);
	}

	public SpawnState withProtectionTicks(int protectionTicks) {
		return new SpawnState(maxHealth, protectionTicks, protectionDurationTicks);
	}

	public SpawnState withProtectionDurationTicks(int protectionDurationTicks) {
		return new SpawnState(maxHealth, protectionTicks, protectionDurationTicks);
	}
}
