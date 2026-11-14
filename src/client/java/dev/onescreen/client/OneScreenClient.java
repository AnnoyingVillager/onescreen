package dev.onescreen.client;

import dev.onescreen.OneScreen;
import dev.onescreen.client.debug.ClientScreenDebugState;
import dev.onescreen.client.editor.ScreenEditorScreen;
import dev.onescreen.client.media.ScreenMediaManager;
import dev.onescreen.client.media.WaterMediaCompat;
import dev.onescreen.client.render.ScreenRenderer;
import dev.onescreen.debug.ScreenDebugStatePayload;
import dev.onescreen.editor.ScreenEditorOpenPayload;
import dev.onescreen.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class OneScreenClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(ModBlockEntities.SCREEN, context -> new ScreenRenderer());
		ClientPlayNetworking.registerGlobalReceiver(ScreenDebugStatePayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientScreenDebugState.setEnabled(payload.enabled()));
		});
		ClientPlayNetworking.registerGlobalReceiver(ScreenEditorOpenPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> context.client().gui.setScreen(new ScreenEditorScreen(
				payload.pos(),
				payload.url(),
				payload.width(),
				payload.height(),
				payload.renderDistance(),
				payload.audioDistance(),
				payload.alignment(),
				payload.fitMode(),
				payload.rotationDegrees(),
				payload.hideFrame(),
				payload.loop(),
				payload.refreshOnResume(),
				payload.maxRenderDistance(),
				payload.maxAudioDistance()
			)));
		});

		ScreenMediaManager mediaManager = ScreenMediaManager.get();
		ClientChunkEvents.CHUNK_LOAD.register(mediaManager::trackChunk);
		ClientChunkEvents.CHUNK_UNLOAD.register(mediaManager::untrackChunk);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null) {
				mediaManager.tick(client.level, client.isPaused());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			ClientScreenDebugState.reset();
			mediaManager.clear();
		}));

		if (!FabricLoader.getInstance().isModLoaded("watermedia")) {
			OneScreen.LOGGER.warn("WATERMeDIA is not installed; screens will show the error placeholder");
		} else if (!WaterMediaCompat.isAvailable()) {
			OneScreen.LOGGER.warn("WATERMeDIA is installed, but its player API is unsupported; screens will show the error placeholder");
		} else {
			OneScreen.LOGGER.info("Using {}", WaterMediaCompat.apiDescription());
		}
		OneScreen.LOGGER.info("Initializing OneScreen client");
	}
}
