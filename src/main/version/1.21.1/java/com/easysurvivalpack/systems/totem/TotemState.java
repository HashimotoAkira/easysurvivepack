package com.easysurvivalpack.systems.totem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TotemState(
		int cooldownSeconds,
		int inventoryOnlineFactor,
		int inventoryOfflineFactor,
		int containerOnlineFactor,
		int containerOfflineFactor
) {
	public static final int DEFAULT_INVENTORY_ONLINE_FACTOR = 1;
	public static final int DEFAULT_INVENTORY_OFFLINE_FACTOR = 5;
	public static final int DEFAULT_CONTAINER_ONLINE_FACTOR = 2;
	public static final int DEFAULT_CONTAINER_OFFLINE_FACTOR = 10;

	public static final Codec<TotemState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("cooldown_seconds", 60).forGetter(TotemState::cooldownSeconds),
			Codec.INT.optionalFieldOf(
					"inventory_online_factor",
					DEFAULT_INVENTORY_ONLINE_FACTOR
			).forGetter(TotemState::inventoryOnlineFactor),
			Codec.INT.optionalFieldOf(
					"inventory_offline_factor",
					DEFAULT_INVENTORY_OFFLINE_FACTOR
			).forGetter(TotemState::inventoryOfflineFactor),
			Codec.INT.optionalFieldOf(
					"container_online_factor",
					DEFAULT_CONTAINER_ONLINE_FACTOR
			).forGetter(TotemState::containerOnlineFactor),
			Codec.INT.optionalFieldOf(
					"container_offline_factor",
					DEFAULT_CONTAINER_OFFLINE_FACTOR
			).forGetter(TotemState::containerOfflineFactor)
	).apply(instance, TotemState::new));

	public static TotemState initial() {
		return new TotemState(
				60,
				DEFAULT_INVENTORY_ONLINE_FACTOR,
				DEFAULT_INVENTORY_OFFLINE_FACTOR,
				DEFAULT_CONTAINER_ONLINE_FACTOR,
				DEFAULT_CONTAINER_OFFLINE_FACTOR
		);
	}

	public TotemState withCooldownSeconds(int seconds) {
		return new TotemState(
				seconds,
				inventoryOnlineFactor,
				inventoryOfflineFactor,
				containerOnlineFactor,
				containerOfflineFactor
		);
	}

	public TotemState withFactors(
			int inventoryOnline,
			int inventoryOffline,
			int containerOnline,
			int containerOffline
	) {
		return new TotemState(cooldownSeconds, inventoryOnline, inventoryOffline, containerOnline, containerOffline);
	}
}
