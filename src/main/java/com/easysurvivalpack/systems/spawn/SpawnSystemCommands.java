package com.easysurvivalpack.systems.spawn;

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

public final class SpawnSystemCommands {
	private static final int MAX_HEALTH_POINTS = 1024;
	private static final int MAX_HEARTS = MAX_HEALTH_POINTS / 2;
	private static final int MAX_PROTECTION_SECONDS = Integer.MAX_VALUE / 20;

	private SpawnSystemCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
			var command = Commands.literal("sethp")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
			var targets = Commands.argument("targets", EntityArgument.players());
			var amount = Commands.argument(
					"amount",
					IntegerArgumentType.integer(1, MAX_HEALTH_POINTS)
			);

			amount.then(Commands.literal("hp").executes(context -> setHealth(context, false)));
			amount.then(Commands.literal("heart").executes(context -> setHealth(context, true)));
			targets.then(amount);
			command.then(targets);
			dispatcher.register(command);

			dispatcher.register(Commands.literal("spawnprotection")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(Commands.argument("targets", EntityArgument.players())
							.then(Commands.argument(
									"seconds",
									IntegerArgumentType.integer(0, MAX_PROTECTION_SECONDS)
							).executes(SpawnSystemCommands::setProtectionDuration))));
		});
	}

	private static int setProtectionDuration(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		int seconds = IntegerArgumentType.getInteger(context, "seconds");
		for (ServerPlayer target : targets) {
			SpawnSystemService.setProtectionDurationTicks(target, seconds * 20);
		}

		context.getSource().sendSuccess(
				() -> Component.literal(
						"Respawn protection duration set to " + seconds
								+ "s for " + targets.size() + " player(s)."
				),
				false
		);
		return targets.size();
	}

	private static int setHealth(CommandContext<CommandSourceStack> context, boolean hearts)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		int amount = IntegerArgumentType.getInteger(context, "amount");
		int maxAmount = hearts ? MAX_HEARTS : MAX_HEALTH_POINTS;
		if (amount > maxAmount) {
			context.getSource().sendFailure(Component.literal(
					"Maximum allowed value is " + maxAmount + (hearts ? " hearts." : " HP.")
			));
			return 0;
		}

		float maxHealth = hearts ? amount * 2.0F : amount;
		for (ServerPlayer target : targets) {
			SpawnSystemService.setMaxHealth(target, maxHealth);
		}

		context.getSource().sendSuccess(
				() -> Component.literal(
						"Max health set to " + amount + (hearts ? " hearts" : " HP")
								+ " for " + targets.size() + " player(s)."
				),
				false
		);
		return targets.size();
	}
}
