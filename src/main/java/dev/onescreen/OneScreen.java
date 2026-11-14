package dev.onescreen;

import dev.onescreen.command.ScreenCommands;
import dev.onescreen.config.OneScreenConfig;
import dev.onescreen.debug.ScreenDebugNetworking;
import dev.onescreen.editor.ScreenEditorNetworking;
import dev.onescreen.registry.ModBlockEntities;
import dev.onescreen.registry.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OneScreen implements ModInitializer {
	public static final String MOD_ID = "onescreen";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		OneScreenConfig.load();
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ScreenEditorNetworking.register();
		ScreenDebugNetworking.register();
		CommandRegistrationCallback.EVENT.register(ScreenCommands::register);
		LOGGER.info("Initializing OneScreen for Minecraft 26.2");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
