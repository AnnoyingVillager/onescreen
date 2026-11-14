package dev.onescreen.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.onescreen.OneScreen;
import dev.onescreen.screen.ScreenSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side configuration shared by single-player integrated servers and dedicated servers.
 */
public final class OneScreenConfig {
	private static final String FILE_NAME = "onescreen.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile float maxRenderDistance = ScreenSettings.DEFAULT_MAX_RENDER_DISTANCE;
	private static volatile float maxAudioDistance = ScreenSettings.DEFAULT_MAX_AUDIO_DISTANCE;

	private OneScreenConfig() {
	}

	public static synchronized void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		Data data = new Data();
		if (Files.exists(path)) {
			try {
				Data loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
				if (loaded != null) {
					data = loaded;
				}
			} catch (IOException | RuntimeException exception) {
				OneScreen.LOGGER.warn("Failed to read {}; using default distance limits", path, exception);
			}
		}

		float originalRenderDistance = data.maxRenderDistance;
		float originalAudioDistance = data.maxAudioDistance;
		data.maxRenderDistance = sanitize(
			data.maxRenderDistance,
			ScreenSettings.DEFAULT_MAX_RENDER_DISTANCE,
			ScreenSettings.MIN_RENDER_DISTANCE,
			ScreenSettings.HARD_MAX_RENDER_DISTANCE
		);
		data.maxAudioDistance = sanitize(
			data.maxAudioDistance,
			ScreenSettings.DEFAULT_MAX_AUDIO_DISTANCE,
			ScreenSettings.MIN_AUDIO_DISTANCE,
			ScreenSettings.HARD_MAX_AUDIO_DISTANCE
		);
		maxRenderDistance = data.maxRenderDistance;
		maxAudioDistance = data.maxAudioDistance;

		if (!Files.exists(path) || originalRenderDistance != data.maxRenderDistance || originalAudioDistance != data.maxAudioDistance) {
			write(path, data);
		}
		OneScreen.LOGGER.info(
			"Distance limits: render={}, audio={} ({})",
			maxRenderDistance,
			maxAudioDistance,
			path
		);
	}

	public static float maxRenderDistance() {
		return maxRenderDistance;
	}

	public static float maxAudioDistance() {
		return maxAudioDistance;
	}

	private static void write(final Path path, final Data data) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(
				path,
				GSON.toJson(data) + System.lineSeparator(),
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING
			);
		} catch (IOException exception) {
			OneScreen.LOGGER.warn("Failed to write {}", path, exception);
		}
	}

	private static float sanitize(final float value, final float fallback, final float min, final float max) {
		if (!Float.isFinite(value)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, value));
	}

	private static final class Data {
		private float maxRenderDistance = ScreenSettings.DEFAULT_MAX_RENDER_DISTANCE;
		private float maxAudioDistance = ScreenSettings.DEFAULT_MAX_AUDIO_DISTANCE;
	}
}
