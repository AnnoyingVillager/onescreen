package dev.onescreen.editor;

import dev.onescreen.block.ScreenBlockEntity;
import dev.onescreen.config.OneScreenConfig;
import dev.onescreen.screen.ScreenSettings;
import dev.onescreen.screen.ScreenUrlValidator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ScreenEditorNetworking {
	private static final double MAX_EDIT_DISTANCE_SQUARED = 64.0D;

	private ScreenEditorNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(ScreenSettingsUpdatePayload.TYPE, ScreenSettingsUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScreenEditorOpenPayload.TYPE, ScreenEditorOpenPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ScreenSettingsUpdatePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> applyUpdate(player, payload));
		});
	}

	public static void tryOpen(final Player player, final BlockPos pos) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (!canEdit(serverPlayer)) {
			serverPlayer.sendSystemMessage(Component.translatable("onescreen.editor.error.no_permission"));
			return;
		}
		if (!(serverPlayer.level().getBlockEntity(pos) instanceof ScreenBlockEntity screen)) {
			serverPlayer.sendSystemMessage(Component.translatable("onescreen.editor.error.not_screen"));
			return;
		}
		if (!ServerPlayNetworking.canSend(serverPlayer, ScreenEditorOpenPayload.TYPE)) {
			serverPlayer.sendSystemMessage(Component.translatable("onescreen.editor.error.client_support"));
			return;
		}
		ServerPlayNetworking.send(serverPlayer, ScreenEditorOpenPayload.fromScreen(
			pos,
			screen.getSettings(),
			OneScreenConfig.maxRenderDistance(),
			OneScreenConfig.maxAudioDistance()
		));
	}

	private static void applyUpdate(final ServerPlayer player, final ScreenSettingsUpdatePayload payload) {
		if (!canEdit(player)) {
			player.sendSystemMessage(Component.translatable("onescreen.editor.error.no_permission"));
			return;
		}
		if (!(player.level().getBlockEntity(payload.pos()) instanceof ScreenBlockEntity screen)) {
			player.sendSystemMessage(Component.translatable("onescreen.editor.error.not_screen"));
			return;
		}
		if (player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_EDIT_DISTANCE_SQUARED) {
			player.sendSystemMessage(Component.translatable("onescreen.editor.error.too_far"));
			return;
		}
		if (!ScreenSettings.isValidSize(payload.width())
			|| !ScreenSettings.isValidSize(payload.height())
			|| !ScreenSettings.isValidRenderDistance(payload.renderDistance(), OneScreenConfig.maxRenderDistance())
			|| !ScreenSettings.isValidAudioDistance(payload.audioDistance(), OneScreenConfig.maxAudioDistance())) {
			player.sendSystemMessage(Component.translatable("onescreen.editor.error.invalid_value"));
			return;
		}

		String url = payload.url();
		if (!url.isBlank()) {
			try {
				url = ScreenUrlValidator.validate(url);
			} catch (IllegalArgumentException exception) {
				player.sendSystemMessage(Component.translatable(
					"onescreen.command.error.invalid_url." + exception.getMessage()
				));
				return;
			}
		}

		ScreenSettings current = screen.getSettings();
		screen.setSettings(current.withEditorSettings(
			url,
			payload.width(),
			payload.height(),
			payload.renderDistance(),
			payload.audioDistance(),
			payload.alignment(),
			payload.fitMode(),
			payload.loop(),
			payload.refreshOnResume()
		));
		player.sendSystemMessage(Component.translatable(
			"onescreen.editor.success.saved",
			screen.getBlockPos().toShortString()
		));
	}

	private static boolean canEdit(final ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}
