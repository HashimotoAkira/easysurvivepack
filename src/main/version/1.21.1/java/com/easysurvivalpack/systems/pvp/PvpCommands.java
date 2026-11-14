package com.easysurvivalpack.systems.pvp;

import java.io.IOException;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import com.easysurvivalpack.EasySurvivalPack;

public final class PvpCommands {
	private PvpCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) ->
				dispatcher.register(Commands.literal("pvp")
						.requires(source -> source.hasPermission(2))
						.then(Commands.literal("on").executes(context -> setPvp(context, true)))
						.then(Commands.literal("off").executes(context -> setPvp(context, false)))
				)
		);
	}

	private static int setPvp(CommandContext<CommandSourceStack> context, boolean enabled) {
		try {
			PvpSettingsService.setPvpAllowed(context.getSource().getServer(), enabled);
		} catch (IOException exception) {
			EasySurvivalPack.LOGGER.error("Could not save the server PvP setting.", exception);
			context.getSource().sendFailure(Component.literal(
					"PvP was not changed because the setting could not be saved."
			));
			return 0;
		}

		context.getSource().sendSuccess(
				() -> Component.literal("PvP is now " + (enabled ? "on" : "off") + "."),
				false
		);
		return 1;
	}
}
