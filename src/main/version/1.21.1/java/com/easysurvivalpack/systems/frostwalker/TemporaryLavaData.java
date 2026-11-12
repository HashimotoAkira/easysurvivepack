package com.easysurvivalpack.systems.frostwalker;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

final class TemporaryLavaData extends SavedData {
	private static final String DATA_NAME = "easy_survival_pack_temporary_lava";
	private static final SavedData.Factory<TemporaryLavaData> FACTORY = new SavedData.Factory<>(
			TemporaryLavaData::new,
			TemporaryLavaData::load,
			DataFixTypes.LEVEL
	);

	private final Map<BlockPos, Long> expiryTicks = new HashMap<>();

	static TemporaryLavaData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
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

	private static TemporaryLavaData load(CompoundTag serialized, HolderLookup.Provider registries) {
		TemporaryLavaData data = new TemporaryLavaData();
		for (String key : serialized.getAllKeys()) {
			data.expiryTicks.put(BlockPos.of(Long.parseLong(key)), serialized.getLong(key));
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag serialized, HolderLookup.Provider registries) {
		for (Map.Entry<BlockPos, Long> entry : expiryTicks.entrySet()) {
			serialized.putLong(Long.toString(entry.getKey().asLong()), entry.getValue());
		}
		return serialized;
	}
}
