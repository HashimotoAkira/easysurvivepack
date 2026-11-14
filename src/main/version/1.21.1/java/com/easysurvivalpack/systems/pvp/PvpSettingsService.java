package com.easysurvivalpack.systems.pvp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import com.easysurvivalpack.EasySurvivalPack;

public final class PvpSettingsService {
	private static final Path SETTINGS_FILE = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("easy_survival_pack_pvp.properties");

	private PvpSettingsService() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(PvpSettingsService::load);
		PvpCommands.register();
	}

	public static void setPvpAllowed(MinecraftServer server, boolean enabled) throws IOException {
		save(enabled);
		server.setPvpAllowed(enabled);
	}

	private static void load(MinecraftServer server) {
		boolean enabled = server.isPvpAllowed();
		if (Files.exists(SETTINGS_FILE)) {
			Properties settings = new Properties();
			try (var reader = Files.newBufferedReader(SETTINGS_FILE, StandardCharsets.UTF_8)) {
				settings.load(reader);
			} catch (IOException exception) {
				EasySurvivalPack.LOGGER.error("Could not read the PvP settings file {}.", SETTINGS_FILE, exception);
				return;
			}

			String value = settings.getProperty("pvp");
			if ("on".equalsIgnoreCase(value)) {
				enabled = true;
			} else if ("off".equalsIgnoreCase(value)) {
				enabled = false;
			} else {
				EasySurvivalPack.LOGGER.error(
						"Invalid or missing 'pvp' setting in {}. Keeping the server default.",
						SETTINGS_FILE
				);
				return;
			}
		} else {
			try {
				save(enabled);
			} catch (IOException exception) {
				EasySurvivalPack.LOGGER.error("Could not create the PvP settings file {}.", SETTINGS_FILE, exception);
			}
		}

		server.setPvpAllowed(enabled);
		EasySurvivalPack.LOGGER.info("Server PvP is {}.", enabled ? "on" : "off");
	}

	private static void save(boolean enabled) throws IOException {
		Files.createDirectories(SETTINGS_FILE.getParent());
		Properties settings = new Properties();
		settings.setProperty("pvp", enabled ? "on" : "off");
		try (var writer = Files.newBufferedWriter(SETTINGS_FILE, StandardCharsets.UTF_8)) {
			settings.store(writer, "Easy Survival Pack server PvP setting");
		}
	}
}
