package com.easysurvivalpack.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;

import com.easysurvivalpack.systems.totem.TotemCooldownService;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
	@Shadow
	@Final
	private Player player;

	@Inject(method = "slotsChanged", at = @At("TAIL"))
	private void easySurvivalPack$blockCoolingTotemCrafting(Container container, CallbackInfo callback) {
		if (!player.level().isClientSide()
				&& ((CraftingMenu) (Object) this).getInputGridSlots().stream()
						.map(Slot::getItem)
						.anyMatch(TotemCooldownService::containsCooldownTotem)) {
			((CraftingMenu) (Object) this).getResultSlot().set(net.minecraft.world.item.ItemStack.EMPTY);
		}
	}
}
