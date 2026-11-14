package com.easysurvivalpack.compat;

import java.util.function.Consumer;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class TrinketsSupport {
	private TrinketsSupport() {
	}

	public static void forEachEquipped(ServerPlayer player, EquippedStackConsumer consumer) {
		if (FabricLoader.getInstance().isModLoaded("trinkets")) {
			TrinketsApiSupport.forEachEquipped(player, consumer);
		}
	}

	@FunctionalInterface
	public interface EquippedStackConsumer {
		void accept(ItemStack stack, Consumer<ItemStack> setStack);
	}
}
