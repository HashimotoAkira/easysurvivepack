package com.easysurvivalpack.systems.frostwalker;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

final class TemporaryLavaData extends SavedData {
	private static final String DATA_NAME = "easy_survival_pack_temporary_lava";
	private static final Codec<TemporaryLavaData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
			.xmap(TemporaryLavaData::fromSerialized, TemporaryLavaData::toSerialized);
	private static final SavedDataType<TemporaryLavaData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("easy_survival_pack", DATA_NAME),
			TemporaryLavaData::new,
			CODEC,
			DataFixTypes.LEVEL
	);

	private final Map<BlockPos, Long> expiryTicks = new HashMap<>();

	static TemporaryLavaData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	Long getExpiryTick(BlockPos position) {
		return expiryTicks.get(position);
	}

	Set<BlockPos> positions() {
		return Set.copyOf(expiryTicks.keySet());
	}

	void setExpiryTick(BlockPos position, long expiryTick) {
		expiryTicks.put(position.immutable(), expiryTick);
		setDirty();
	}

	void remove(BlockPos position) {
		if (expiryTicks.remove(position) != null) {
			setDirty();
		}
	}

	private static TemporaryLavaData fromSerialized(Map<String, Long> serialized) {
		TemporaryLavaData data = new TemporaryLavaData();
		for (Map.Entry<String, Long> entry : serialized.entrySet()) {
			data.expiryTicks.put(BlockPos.of(Long.parseLong(entry.getKey())), entry.getValue());
		}
		return data;
	}

	private Map<String, Long> toSerialized() {
		Map<String, Long> serialized = new HashMap<>();
		for (Map.Entry<BlockPos, Long> entry : expiryTicks.entrySet()) {
			serialized.put(Long.toString(entry.getKey().asLong()), entry.getValue());
		}
		return serialized;
	}
}
