package dev.onescreen.debug;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ScreenDebugNetworking {
	private ScreenDebugNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ScreenDebugStatePayload.TYPE, ScreenDebugStatePayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ScreenDebugSettings settings = ScreenDebugSettings.get(server);
			send(handler.player, settings.isEnabled());
		});
	}

	public static void broadcast(final MinecraftServer server, final boolean enabled) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player, enabled);
		}
	}

	private static void send(final ServerPlayer player, final boolean enabled) {
		if (ServerPlayNetworking.canSend(player, ScreenDebugStatePayload.TYPE)) {
			ServerPlayNetworking.send(player, new ScreenDebugStatePayload(enabled));
		}
	}
}