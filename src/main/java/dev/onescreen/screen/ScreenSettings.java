package dev.onescreen.screen;

import java.util.Objects;

public record ScreenSettings(
	String url,
	float width,
	float height,
	float renderDistance,
	float audioDistance,
	ScreenAlignment alignment,
	ScreenFitMode fitMode,
	float volume,
	boolean muted,
	boolean loop,
	boolean refreshOnResume
) {
	public static final int MAX_URL_LENGTH = 4096;
	public static final float MIN_SIZE = 0.25F;
	public static final float MAX_SIZE = 32.0F;
	public static final float MIN_RENDER_DISTANCE = 8.0F;
	public static final float DEFAULT_MAX_RENDER_DISTANCE = 256.0F;
	public static final float HARD_MAX_RENDER_DISTANCE = 4096.0F;
	public static final float MIN_AUDIO_DISTANCE = 2.0F;
	public static final float DEFAULT_MAX_AUDIO_DISTANCE = 128.0F;
	public static final float HARD_MAX_AUDIO_DISTANCE = 1024.0F;
	public static final float DEFAULT_WIDTH = 4.0F;
	public static final float DEFAULT_HEIGHT = 2.25F;
	public static final float DEFAULT_RENDER_DISTANCE = 96.0F;
	public static final float DEFAULT_AUDIO_DISTANCE = 48.0F;
	public static final float DEFAULT_VOLUME = 1.0F;
	public static final ScreenSettings DEFAULT = new ScreenSettings(
		"",
		DEFAULT_WIDTH,
		DEFAULT_HEIGHT,
		DEFAULT_RENDER_DISTANCE,
		DEFAULT_AUDIO_DISTANCE,
		ScreenAlignment.CENTER,
		ScreenFitMode.FIT,
		DEFAULT_VOLUME,
		false,
		true,
		false
	);

	public ScreenSettings {
		url = sanitizeLoadedUrl(url);
		width = clampSize(width, DEFAULT_WIDTH);
		height = clampSize(height, DEFAULT_HEIGHT);
		renderDistance = clampRenderDistance(renderDistance);
		audioDistance = clampAudioDistance(audioDistance);
		alignment = alignment == null ? ScreenAlignment.CENTER : alignment;
		fitMode = fitMode == null ? ScreenFitMode.FIT : fitMode;
		volume = clampVolume(volume);
	}

	public ScreenSettings withUrl(final String nextUrl) {
		return new ScreenSettings(nextUrl, this.width, this.height, this.renderDistance, this.audioDistance, this.alignment, this.fitMode, this.volume, this.muted, this.loop, this.refreshOnResume);
	}

	public ScreenSettings withSize(final float nextWidth, final float nextHeight) {
		return new ScreenSettings(this.url, nextWidth, nextHeight, this.renderDistance, this.audioDistance, this.alignment, this.fitMode, this.volume, this.muted, this.loop, this.refreshOnResume);
	}

	public ScreenSettings withRenderDistance(final float nextRenderDistance) {
		return new ScreenSettings(this.url, this.width, this.height, nextRenderDistance, this.audioDistance, this.alignment, this.fitMode, this.volume, this.muted, this.loop, this.refreshOnResume);
	}

	public ScreenSettings withAudioDistance(final float nextAudioDistance) {
		return new ScreenSettings(this.url, this.width, this.height, this.renderDistance, nextAudioDistance, this.alignment, this.fitMode, this.volume, this.muted, this.loop, this.refreshOnResume);
	}

	public ScreenSettings withAlignment(final ScreenAlignment nextAlignment) {
		return new ScreenSettings(this.url, this.width, this.height, this.renderDistance, this.audioDistance, nextAlignment, this.fitMode, this.volume, this.muted, this.loop, this.refreshOnResume);
	}

	public ScreenSettings withFitMode(final ScreenFitMode nextFitMode) {
		return new ScreenSettings(this.url, this.width, this.height, this.renderDistance, this.audioDistance, this.alignment, nextFitMode, this.volume, this.muted, this.loop, this.refreshOnResume);
	}

	public ScreenSettings withEditorSettings(
		final String nextUrl,
		final float nextWidth,
		final float nextHeight,
		final float nextRenderDistance,
		final float nextAudioDistance,
		final ScreenAlignment nextAlignment,
		final ScreenFitMode nextFitMode,
		final boolean nextLoop,
		final boolean nextRefreshOnResume
	) {
		return new ScreenSettings(
			nextUrl,
			nextWidth,
			nextHeight,
			nextRenderDistance,
			nextAudioDistance,
			nextAlignment,
			nextFitMode,
			this.volume,
			this.muted,
			nextLoop,
			nextRefreshOnResume
		);
	}

	public ScreenSettings withVolume(final float nextVolume) {
		return new ScreenSettings(this.url, this.width, this.height, this.renderDistance, this.audioDistance, this.alignment, this.fitMode, nextVolume, this.muted, this.loop, this.refreshOnResume);
	}

	public ScreenSettings withMuted(final boolean nextMuted) {
		return new ScreenSettings(this.url, this.width, this.height, this.renderDistance, this.audioDistance, this.alignment, this.fitMode, this.volume, nextMuted, this.loop, this.refreshOnResume);
	}

	public ScreenSettings withLoop(final boolean nextLoop) {
		return new ScreenSettings(this.url, this.width, this.height, this.renderDistance, this.audioDistance, this.alignment, this.fitMode, this.volume, this.muted, nextLoop, this.refreshOnResume);
	}

	public ScreenSettings withRefreshOnResume(final boolean nextRefreshOnResume) {
		return new ScreenSettings(this.url, this.width, this.height, this.renderDistance, this.audioDistance, this.alignment, this.fitMode, this.volume, this.muted, this.loop, nextRefreshOnResume);
	}

	public boolean hasUrl() {
		return !this.url.isBlank();
	}

	public static boolean isValidSize(final float value) {
		return isInRange(value, MIN_SIZE, MAX_SIZE);
	}

	public static boolean isValidRenderDistance(final float value) {
		return isValidRenderDistance(value, DEFAULT_MAX_RENDER_DISTANCE);
	}

	public static boolean isValidRenderDistance(final float value, final float maxDistance) {
		return isInRange(value, MIN_RENDER_DISTANCE, Math.min(maxDistance, HARD_MAX_RENDER_DISTANCE));
	}

	public static boolean isValidAudioDistance(final float value) {
		return isValidAudioDistance(value, DEFAULT_MAX_AUDIO_DISTANCE);
	}

	public static boolean isValidAudioDistance(final float value, final float maxDistance) {
		return isInRange(value, MIN_AUDIO_DISTANCE, Math.min(maxDistance, HARD_MAX_AUDIO_DISTANCE));
	}

	private static String sanitizeLoadedUrl(final String value) {
		if (value == null || value.length() > MAX_URL_LENGTH) {
			return "";
		}

		for (int i = 0; i < value.length(); i++) {
			if (Character.isISOControl(value.charAt(i))) {
				return "";
			}
		}

		return Objects.requireNonNull(value);
	}

	private static float clampSize(final float value, final float fallback) {
		return clampFinite(value, fallback, MIN_SIZE, MAX_SIZE);
	}

	private static float clampRenderDistance(final float value) {
		return clampFinite(value, DEFAULT_RENDER_DISTANCE, MIN_RENDER_DISTANCE, HARD_MAX_RENDER_DISTANCE);
	}

	private static float clampAudioDistance(final float value) {
		return clampFinite(value, DEFAULT_AUDIO_DISTANCE, MIN_AUDIO_DISTANCE, HARD_MAX_AUDIO_DISTANCE);
	}

	private static float clampVolume(final float value) {
		return clampFinite(value, DEFAULT_VOLUME, 0.0F, 1.0F);
	}

	private static boolean isInRange(final float value, final float min, final float max) {
		return Float.isFinite(value) && value >= min && value <= max;
	}

	private static float clampFinite(final float value, final float fallback, final float min, final float max) {
		if (!Float.isFinite(value)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, value));
	}
}
