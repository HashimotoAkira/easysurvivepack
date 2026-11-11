package com.easysurvivalpack.systems.xploss;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class XpLossCommands {
	private XpLossCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) ->
				dispatcher.register(Commands.literal("xploss")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument(
										"percent",
										IntegerArgumentType.integer(0, 100)
								).executes(XpLossCommands::setReductionPercent)))));
	}

	private static int setReductionPercent(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		int percent = IntegerArgumentType.getInteger(context, "percent");
		for (ServerPlayer target : targets) {
			XpLossService.setReductionPercent(target, percent);
		}

		context.getSource().sendSuccess(
				() -> Component.literal(
						percent == 0
								? "XP loss reduction disabled for " + targets.size() + " player(s)."
								: "XP loss reduction set to " + percent
										+ "% for " + targets.size() + " player(s)."
				),
				false
		);
		return targets.size();
	}
}
