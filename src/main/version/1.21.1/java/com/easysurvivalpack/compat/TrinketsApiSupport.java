package com.easysurvivalpack.compat;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class TrinketsApiSupport {
	private TrinketsApiSupport() {
	}

	public static void forEachEquipped(
			ServerPlayer player,
			TrinketsSupport.EquippedStackConsumer consumer
	) {
		TrinketsApi.getTrinketComponent(player).ifPresent(component ->
				component.getAllEquipped().forEach(equipped -> {
					SlotReference slot = equipped.getA();
					consumer.accept(equipped.getB(), updated -> setStack(slot, updated));
				})
		);
	}

	private static void setStack(SlotReference slot, ItemStack stack) {
		slot.inventory().setItem(slot.index(), stack);
	}
}
