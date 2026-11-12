package com.easysurvivalpack.systems.lowhpdefense;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LowHpDefenseState(int appliedTier, List<SuspendedResistance> suspendedEffects) {
	public static final Codec<LowHpDefenseState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("applied_tier", -1).forGetter(LowHpDefenseState::appliedTier),
			SuspendedResistance.CODEC.listOf()
					.optionalFieldOf("suspended_effects", List.of())
					.forGetter(LowHpDefenseState::suspendedEffects)
	).apply(instance, LowHpDefenseState::new));

	public LowHpDefenseState {
		suspendedEffects = List.copyOf(suspendedEffects);
	}

	public static LowHpDefenseState initial() {
		return new LowHpDefenseState(-1, List.of());
	}

	public LowHpDefenseState withAppliedTier(int appliedTier) {
		return new LowHpDefenseState(appliedTier, suspendedEffects);
	}

	public LowHpDefenseState withSuspendedEffects(List<SuspendedResistance> suspendedEffects) {
		return new LowHpDefenseState(appliedTier, suspendedEffects);
	}

	public record SuspendedResistance(
			int amplifier,
			int duration,
			boolean ambient,
			boolean visible,
			boolean showIcon
	) {
		public static final Codec<SuspendedResistance> CODEC = RecordCodecBuilder.create(instance ->
				instance.group(
						Codec.INT.fieldOf("amplifier").forGetter(SuspendedResistance::amplifier),
						Codec.INT.fieldOf("duration").forGetter(SuspendedResistance::duration),
						Codec.BOOL.fieldOf("ambient").forGetter(SuspendedResistance::ambient),
						Codec.BOOL.fieldOf("visible").forGetter(SuspendedResistance::visible),
						Codec.BOOL.fieldOf("show_icon").forGetter(SuspendedResistance::showIcon)
				).apply(instance, SuspendedResistance::new));
	}
}
