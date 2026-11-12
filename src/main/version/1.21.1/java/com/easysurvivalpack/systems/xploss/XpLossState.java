package com.easysurvivalpack.systems.xploss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record XpLossState(boolean initialized, int previousTotalPoints, int reductionPercent) {
	public static final Codec<XpLossState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("initialized", false).forGetter(XpLossState::initialized),
			Codec.INT.optionalFieldOf("previous_total_points", 0).forGetter(XpLossState::previousTotalPoints),
			Codec.INT.optionalFieldOf("reduction_percent", 80).forGetter(XpLossState::reductionPercent)
	).apply(instance, XpLossState::new));

	public static XpLossState initial() {
		return new XpLossState(false, 0, 80);
	}

	public XpLossState withBaseline(int totalPoints) {
		return new XpLossState(true, totalPoints, reductionPercent);
	}

	public XpLossState withReductionPercent(int reductionPercent) {
		return new XpLossState(initialized, previousTotalPoints, reductionPercent);
	}
}
