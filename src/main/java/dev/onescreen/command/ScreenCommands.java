package dev.onescreen.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.onescreen.block.ScreenBlockEntity;
import dev.onescreen.config.OneScreenConfig;
import dev.onescreen.debug.ScreenDebugNetworking;
import dev.onescreen.debug.ScreenDebugSettings;
import dev.onescreen.screen.ScreenAlignment;
import dev.onescreen.screen.ScreenFitMode;
import dev.onescreen.screen.ScreenSettings;
import dev.onescreen.screen.ScreenUrlValidator;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Administrator command surface for configured screen blocks.
 */
public final class ScreenCommands {
	private static final SimpleCommandExceptionType ERROR_NOT_SCREEN = new SimpleCommandExceptionType(
		Component.translatable("onescreen.command.error.not_screen")
	);
	private static final SimpleCommandExceptionType ERROR_NO_URL = new SimpleCommandExceptionType(
		Component.translatable("onescreen.command.error.no_url")
	);

	private ScreenCommands() {
	}

	public static void register(
		final CommandDispatcher<CommandSourceStack> dispatcher,
		final CommandBuildContext context,
		final Commands.CommandSelection selection
	) {
		dispatcher.register(
			Commands.literal("screen")
				.then(
					Commands.literal("config")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.literal("debug")
								.executes(ScreenCommands::queryDebug)
								.then(Commands.literal("on").executes(commandContext -> setDebug(commandContext, true)))
								.then(Commands.literal("off").executes(commandContext -> setDebug(commandContext, false)))
							)
					)
				.then(
					Commands.literal("set")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("url", StringArgumentType.greedyString())
										.executes(ScreenCommands::setUrl)
								)
						)
				)
				.then(
					Commands.literal("size")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("width", FloatArgumentType.floatArg())
										.then(
											Commands.argument("height", FloatArgumentType.floatArg())
												.executes(ScreenCommands::setSize)
										)
								)
						)
				)
				.then(
					Commands.literal("renderdistance")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("value", FloatArgumentType.floatArg())
										.executes(ScreenCommands::setRenderDistance)
								)
						)
				)
				.then(
					Commands.literal("audiodistance")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("value", FloatArgumentType.floatArg())
										.executes(ScreenCommands::setAudioDistance)
								)
						)
				)
				.then(
					Commands.literal("align")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("position", StringArgumentType.word())
										.executes(ScreenCommands::setAlignment)
								)
						)
				)
				.then(
					Commands.literal("fitmode")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("mode", StringArgumentType.word())
										.executes(ScreenCommands::setFitMode)
								)
						)
				)
				.then(
					Commands.literal("rotation")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("degrees", FloatArgumentType.floatArg())
										.executes(ScreenCommands::setRotation)
								)
						)
				)
				.then(
					Commands.literal("hideframe")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("hidden", BoolArgumentType.bool())
										.executes(ScreenCommands::setHideFrame)
								)
						)
				)
				.then(
					Commands.literal("volume")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("value", FloatArgumentType.floatArg())
										.executes(ScreenCommands::setVolume)
								)
						)
				)
				.then(
					Commands.literal("mute")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("muted", BoolArgumentType.bool())
										.executes(ScreenCommands::setMuted)
								)
						)
				)
				.then(
					Commands.literal("loop")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.then(
									Commands.argument("loop", BoolArgumentType.bool())
										.executes(ScreenCommands::setLoop)
								)
						)
				)
				.then(
					Commands.literal("reload")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.executes(ScreenCommands::reload)
						)
				)
				.then(
					Commands.literal("info")
						.then(
							Commands.argument("pos", BlockPosArgument.blockPos())
								.executes(ScreenCommands::info)
						)
				)
		);
	}

	private static int queryDebug(final CommandContext<CommandSourceStack> context) {
		boolean enabled = ScreenDebugSettings.get(context.getSource().getServer()).isEnabled();
		sendDebugStatus(context, enabled, false);
		return 1;
	}

	private static int setDebug(final CommandContext<CommandSourceStack> context, final boolean enabled) {
		ScreenDebugSettings settings = ScreenDebugSettings.get(context.getSource().getServer());
		settings.setEnabled(enabled);
		ScreenDebugNetworking.broadcast(context.getSource().getServer(), enabled);
		sendDebugStatus(context, enabled, true);
		return 1;
	}

	private static void sendDebugStatus(
		final CommandContext<CommandSourceStack> context,
		final boolean enabled,
		final boolean broadcast
	) {
		Component state = Component.translatable(
			enabled ? "onescreen.command.debug.on" : "onescreen.command.debug.off"
		);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				broadcast ? "onescreen.command.success.debug" : "onescreen.command.info.debug",
				state
			),
			broadcast
		);
	}
	private static int setUrl(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		String rawUrl = StringArgumentType.getString(context, "url");
		final String url;
		try {
			url = ScreenUrlValidator.validate(rawUrl);
		} catch (IllegalArgumentException exception) {
			context.getSource().sendFailure(Component.translatable(
				"onescreen.command.error.invalid_url." + exception.getMessage()
			));
			return 0;
		}
		screen.setUrl(url);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.url",
				screen.getBlockPos().toShortString(),
				ScreenUrlValidator.summary(url)
			),
			true
		);
		return 1;
	}

	private static int setSize(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		float width = FloatArgumentType.getFloat(context, "width");
		float height = FloatArgumentType.getFloat(context, "height");
		if (!ScreenSettings.isValidSize(width) || !ScreenSettings.isValidSize(height)) {
			context.getSource().sendFailure(Component.translatable(
				"onescreen.command.error.invalid_size",
				ScreenSettings.MIN_SIZE,
				ScreenSettings.MAX_SIZE
			));
			return 0;
		}
		screen.setSize(width, height);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.size",
				screen.getBlockPos().toShortString(),
				width,
				height
			),
			true
		);
		return 1;
	}

	private static int setRenderDistance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		float value = FloatArgumentType.getFloat(context, "value");
		if (!ScreenSettings.isValidRenderDistance(value, OneScreenConfig.maxRenderDistance())) {
			context.getSource().sendFailure(Component.translatable(
				"onescreen.command.error.invalid_render_distance",
				ScreenSettings.MIN_RENDER_DISTANCE,
				OneScreenConfig.maxRenderDistance()
			));
			return 0;
		}
		screen.setRenderDistance(value);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.render_distance",
				screen.getBlockPos().toShortString(),
				value
			),
			true
		);
		return 1;
	}

	private static int setAudioDistance(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		float value = FloatArgumentType.getFloat(context, "value");
		if (!ScreenSettings.isValidAudioDistance(value, OneScreenConfig.maxAudioDistance())) {
			context.getSource().sendFailure(Component.translatable(
				"onescreen.command.error.invalid_audio_distance",
				ScreenSettings.MIN_AUDIO_DISTANCE,
				OneScreenConfig.maxAudioDistance()
			));
			return 0;
		}
		screen.setAudioDistance(value);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.audio_distance",
				screen.getBlockPos().toShortString(),
				value
			),
			true
		);
		return 1;
	}

	private static int setAlignment(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		String rawAlignment = StringArgumentType.getString(context, "position");
		ScreenAlignment alignment = ScreenAlignment.parse(rawAlignment).orElse(null);
		if (alignment == null) {
			context.getSource().sendFailure(Component.translatable("onescreen.command.error.invalid_alignment"));
			return 0;
		}
		screen.setAlignment(alignment);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.alignment",
				screen.getBlockPos().toShortString(),
				Component.translatable(alignment.translationKey())
			),
			true
		);
		return 1;
	}

	private static int setFitMode(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		String rawMode = StringArgumentType.getString(context, "mode");
		ScreenFitMode fitMode = ScreenFitMode.parse(rawMode).orElse(null);
		if (fitMode == null) {
			context.getSource().sendFailure(Component.translatable("onescreen.command.error.invalid_fit_mode"));
			return 0;
		}
		screen.setFitMode(fitMode);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.fit_mode",
				screen.getBlockPos().toShortString(),
				Component.translatable(fitMode.translationKey())
			),
			true
		);
		return 1;
	}

	private static int setRotation(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		float rotationDegrees = FloatArgumentType.getFloat(context, "degrees");
		if (!ScreenSettings.isValidRotation(rotationDegrees)) {
			context.getSource().sendFailure(Component.translatable(
				"onescreen.command.error.invalid_rotation",
				ScreenSettings.MIN_ROTATION,
				ScreenSettings.MAX_ROTATION
			));
			return 0;
		}
		screen.setRotationDegrees(rotationDegrees);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.rotation",
				screen.getBlockPos().toShortString(),
				rotationDegrees
			),
			true
		);
		return 1;
	}

	private static int setHideFrame(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		boolean hidden = BoolArgumentType.getBool(context, "hidden");
		screen.setHideFrame(hidden);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.hideframe",
				screen.getBlockPos().toShortString(),
				hidden
			),
			true
		);
		return 1;
	}

	private static int setVolume(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		float volume = FloatArgumentType.getFloat(context, "value");
		if (!Float.isFinite(volume) || volume < 0.0F || volume > 1.0F) {
			context.getSource().sendFailure(Component.translatable("onescreen.command.error.invalid_volume"));
			return 0;
		}
		screen.setVolume(volume);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.volume",
				screen.getBlockPos().toShortString(),
				volume
			),
			true
		);
		return 1;
	}

	private static int setMuted(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		boolean muted = BoolArgumentType.getBool(context, "muted");
		screen.setMuted(muted);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.muted",
				screen.getBlockPos().toShortString(),
				muted
			),
			true
		);
		return 1;
	}

	private static int setLoop(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		boolean loop = BoolArgumentType.getBool(context, "loop");
		screen.setLoop(loop);
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.loop",
				screen.getBlockPos().toShortString(),
				loop
			),
			true
		);
		return 1;
	}

	private static int reload(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		if (!screen.getSettings().hasUrl()) {
			throw ERROR_NO_URL.create();
		}
		int revision = screen.reload();
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.success.reload",
				screen.getBlockPos().toShortString(),
				revision
			),
			true
		);
		return 1;
	}

	private static int info(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ScreenBlockEntity screen = screen(context);
		ScreenSettings settings = screen.getSettings();
		context.getSource().sendSuccess(
			() -> Component.translatable(
				"onescreen.command.info",
				screen.getBlockPos().toShortString(),
				ScreenUrlValidator.summary(settings.url()),
				settings.width(),
				settings.height(),
				settings.renderDistance(),
				settings.audioDistance(),
				Component.translatable(settings.alignment().translationKey()),
				Component.translatable(settings.fitMode().translationKey()),
				settings.rotationDegrees(),
				settings.hideFrame(),
				settings.volume(),
				settings.muted(),
				settings.loop(),
				settings.refreshOnResume(),
				screen.getLoadRevision()
			),
			false
		);
		return 1;
	}

	private static ScreenBlockEntity screen(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerLevel level = context.getSource().getLevel();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
		if (level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) {
			return screen;
		}
		throw ERROR_NOT_SCREEN.create();
	}
}
