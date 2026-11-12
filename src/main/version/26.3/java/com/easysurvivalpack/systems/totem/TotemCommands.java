package com.easysurvivalpack.systems.totem;

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

public final class TotemCommands {
	private static final int MAX_COOLDOWN_SECONDS = Integer.MAX_VALUE;

	private TotemCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> {
			var cooldownSeconds = Commands.argument(
					"seconds",
					IntegerArgumentType.integer(0, MAX_COOLDOWN_SECONDS)
			).executes(TotemCommands::setCooldown);
			var cooldownTargets = Commands.argument("targets", EntityArgument.players())
					.then(cooldownSeconds);
			dispatcher.register(Commands.literal("totemcooldown")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(cooldownTargets));

			var containerOffline = Commands.argument(
					"container_offline",
					IntegerArgumentType.integer(1, 10)
			).executes(TotemCommands::setFactors);
			var containerOnline = Commands.argument(
					"container_online",
					IntegerArgumentType.integer(1, 10)
			).then(containerOffline);
			var inventoryOffline = Commands.argument(
					"inventory_offline",
					IntegerArgumentType.integer(1, 10)
			).then(containerOnline);
			var inventoryOnline = Commands.argument(
					"inventory_online",
					IntegerArgumentType.integer(1, 10)
			).then(inventoryOffline);
			var factorTargets = Commands.argument("targets", EntityArgument.players())
					.then(inventoryOnline);
			dispatcher.register(Commands.literal("totemfactors")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(factorTargets));
		});
	}

	private static int setCooldown(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		int seconds = IntegerArgumentType.getInteger(context, "seconds");
		for (ServerPlayer target : targets) {
			TotemCooldownService.setCooldownSeconds(target, seconds);
		}

		context.getSource().sendSuccess(
				() -> Component.literal(
						"Totem cooldown set to " + seconds + "s for " + targets.size() + " player(s)."
				),
				false
		);
		return targets.size();
	}

	private static int setFactors(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		var targets = EntityArgument.getPlayers(context, "targets");
		int inventoryOnline = IntegerArgumentType.getInteger(context, "inventory_online");
		int inventoryOffline = IntegerArgumentType.getInteger(context, "inventory_offline");
		int containerOnline = IntegerArgumentType.getInteger(context, "container_online");
		int containerOffline = IntegerArgumentType.getInteger(context, "container_offline");
		for (ServerPlayer target : targets) {
			TotemCooldownService.setFactors(
					target,
					inventoryOnline,
					inventoryOffline,
					containerOnline,
					containerOffline
			);
		}

		context.getSource().sendSuccess(
				() -> Component.literal(
						"Totem factors set to inventory " + inventoryOnline + "x/" + inventoryOffline
								+ "x, container " + containerOnline + "x/" + containerOffline
								+ "x for " + targets.size() + " player(s)."
				),
				false
		);
		return targets.size();
	}
}
