package com.easysurvivalpack.systems.outofcombatrecovery;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class OutOfCombatRecoveryCommands {
	private static final int MIN_DELAY_SECONDS = 0;
	private static final int MAX_DELAY_SECONDS = Integer.MAX_VALUE / 20;
	private static final double MIN_HEAL_PERCENT = 0.5;
	private static final double MAX_HEAL_PERCENT = 10.0;

	private OutOfCombatRecoveryCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
			dispatcher.register(Commands.literal("recoverycombat")
					.requires(source -> source.hasPermission(2))
					.then(Commands.argument("targets", EntityArgument.players())
							.then(Commands.argument(
									"seconds",
									IntegerArgumentType.integer(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)
							).executes(OutOfCombatRecoveryCommands::setCombatDelay))));

			dispatcher.register(Commands.literal("recoveryenvironment")
					.requires(source -> source.hasPermission(2))
					.then(Commands.argument("targets", EntityArgument.players())
							.then(Commands.argument(
									"seconds",
									IntegerArgumentType.integer(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)
							).executes(OutOfCombatRecoveryCommands::setEnvironmentDelay))));

			dispatcher.register(Commands.literal("recoveryrate")
					.requires(source -> source.hasPermission(2))
					.then(Commands.argument("targets", EntityArgument.players())
							.then(Commands.argument(
									"percent",
									DoubleArgumentType.doubleArg(MIN_HEAL_PERCENT, MAX_HEAL_PERCENT)
							).executes(OutOfCombatRecoveryCommands::setHealRate))));
		});
	}

	private static int setCombatDelay(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		int seconds = IntegerArgumentType.getInteger(context, "seconds");
		for (ServerPlayer target : targets) {
			OutOfCombatRecoveryService.setCombatDelayTicks(target, seconds * 20);
		}
		return sendSuccess(context, "Combat recovery delay set to " + seconds + "s", targets.size());
	}

	private static int setEnvironmentDelay(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		int seconds = IntegerArgumentType.getInteger(context, "seconds");
		for (ServerPlayer target : targets) {
			OutOfCombatRecoveryService.setEnvironmentDelayTicks(target, seconds * 20);
		}
		return sendSuccess(context, "Environmental recovery delay set to " + seconds + "s", targets.size());
	}

	private static int setHealRate(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		float percent = (float) DoubleArgumentType.getDouble(context, "percent");
		for (ServerPlayer target : targets) {
			OutOfCombatRecoveryService.setHealPercentPerSecond(target, percent);
		}
		return sendSuccess(
				context,
				"Recovery rate set to " + percent + "% max HP per second",
				targets.size()
		);
	}

	private static int sendSuccess(
			CommandContext<CommandSourceStack> context,
			String message,
			int targetCount
	) {
		context.getSource().sendSuccess(
				() -> Component.literal(message + " for " + targetCount + " player(s)."),
				false
		);
		return targetCount;
	}
}
