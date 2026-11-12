package com.easysurvivalpack;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysurvivalpack.systems.emergencyheal.EmergencyHealService;
import com.easysurvivalpack.systems.durability.DurabilityRepairService;
import com.easysurvivalpack.systems.frostwalker.FrostWalkerService;
import com.easysurvivalpack.systems.inftrident.InfiniteTridentCraftingService;
import com.easysurvivalpack.systems.lowhpdefense.LowHpDefenseService;
import com.easysurvivalpack.systems.outofcombatrecovery.OutOfCombatRecoveryService;
import com.easysurvivalpack.systems.spawn.SpawnSystemService;
import com.easysurvivalpack.systems.totem.TotemCooldownService;
import com.easysurvivalpack.display.StatusDisplayService;
import com.easysurvivalpack.systems.xploss.XpLossService;

public final class EasySurvivalPack implements ModInitializer {
	public static final String MOD_ID = "easy_survival_pack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		EmergencyHealService.register();
		OutOfCombatRecoveryService.register();
		SpawnSystemService.register();
		LowHpDefenseService.register();
		TotemCooldownService.register();
		DurabilityRepairService.register();
		FrostWalkerService.register();
		InfiniteTridentCraftingService.register();
		StatusDisplayService.register();
		XpLossService.register();
		LOGGER.info("Easy Survival Pack initialized for Minecraft 1.21.1.");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
