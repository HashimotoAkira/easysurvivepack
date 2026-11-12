package com.easysurvivalpack.systems.emergencyheal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class EmergencyHealCommands {
	private static final int MIN_HEAL_PERCENT = 0;
	private static final int MAX_HEAL_PERCENT = 100;
	private static final int MIN_COOLDOWN_SECONDS = 0;
	private static final int MAX_COOLDOWN_SECONDS = Integer.MAX_VALUE / 20;

	private EmergencyHealCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
			dispatcher.register(Commands.literal("emergencyhealhp")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(Commands.argument("targets", EntityArgument.players())
							.then(Commands.argument(
									"percent",
									IntegerArgumentType.integer(MIN_HEAL_PERCENT, MAX_HEAL_PERCENT)
							).executes(EmergencyHealCommands::setHealPercent))));

			dispatcher.register(Commands.literal("emergencyhealcd")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(Commands.argument("targets", EntityArgument.players())
							.then(Commands.argument(
									"seconds",
									IntegerArgumentType.integer(
											MIN_COOLDOWN_SECONDS,
											MAX_COOLDOWN_SECONDS
									)
							).executes(EmergencyHealCommands::setCooldownSeconds))));
		});
	}

	private static int setHealPercent(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		int percent = IntegerArgumentType.getInteger(context, "percent");
		for (ServerPlayer target : targets) {
			EmergencyHealService.setHealPercent(target, percent);
		}
		context.getSource().sendSuccess(
				() -> Component.literal(
						percent == 0
								? "Emergency Heal disabled for " + targets.size() + " player(s)."
								: "Emergency Heal: healing set to " + percent
										+ "% max HP for " + targets.size() + " player(s)."
				),
				false
		);
		return targets.size();
	}

	private static int setCooldownSeconds(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		int seconds = IntegerArgumentType.getInteger(context, "seconds");
		int cooldownTicks = seconds * 20;
		for (ServerPlayer target : targets) {
			EmergencyHealService.setCooldownDurationTicks(target, cooldownTicks);
		}
		context.getSource().sendSuccess(
				() -> Component.literal(
						"Emergency Heal: cooldown set to " + seconds
								+ "s for " + targets.size() + " player(s)."
				),
				false
		);
		return targets.size();
	}
}
