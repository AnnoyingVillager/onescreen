package dev.onescreen.client.media;

import dev.onescreen.OneScreen;
import dev.onescreen.screen.ScreenSettings;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One client-side media load. All mutable state is accessed from Minecraft's client thread.
 */
final class MediaSession {
	private static final int MAX_MEDIA_LOG_LENGTH = 512;
	private static final int VIDEO_MAX_WIDTH = 1280;
	private static final int VIDEO_MAX_HEIGHT = 720;
	private static final long LIVE_RESYNC_DEBOUNCE_NANOS = 1_000_000_000L;
	private static final long LIVE_REFRESH_PENDING_TIMEOUT_NANOS = 10_000_000_000L;

	private final ScreenKey key;
	private final String url;
	private final int loadRevision;
	private final Identifier textureId;
	private final AtomicLong videoFrameSequence = new AtomicLong();
	private final AtomicBoolean hlsProbeStarted = new AtomicBoolean();
	private final AtomicReference<WaterMediaCompat.@Nullable HlsMetadata> hlsMetadata = new AtomicReference<>();
	private ScreenSettings settings;
	private double distanceGain = 1.0D;
	private WaterMediaCompat.@Nullable MediaResource mrl;
	private WaterMediaCompat.@Nullable MediaPlayerHandle player;
	private WaterMediaCompat.@Nullable MediaEngine graphicsEngine;
	private WaterMediaCompat.@Nullable MediaEngine imageEngine;
	private WaterMediaCompat.@Nullable MediaEngine videoEngine;
	private @Nullable DynamicTexture texture;
	private MediaState state = MediaState.RESOLVING;
	private @Nullable String error;
	private boolean videoOutput;
	private boolean textureRegistered;
	private long copiedFrameSequence = -1L;
	private int registeredMediaWidth;
	private int registeredMediaHeight;
	private int appliedVolumePercent = -1;
	private boolean appliedMuted;
	private @Nullable Boolean appliedLoop;
	private @Nullable Boolean appliedPaused;
	private boolean pauseRequested;
	private @Nullable Boolean manualPauseOverride;
	private boolean manuallyStopped;
	private boolean lastPausedState;
	private boolean hasPauseState;
	private boolean refreshPending;
	private long refreshPendingSinceNanos;
	private long lastLiveResyncNanos;
	private boolean firstFrameLogged;
	private boolean closed;
	private boolean failureLogged;
	private @Nullable String lastLoggedStatus;

	MediaSession(final ScreenKey key, final String url, final int loadRevision, final ScreenSettings settings, final double distanceGain) {
		this.key = Objects.requireNonNull(key, "key");
		this.url = Objects.requireNonNull(url, "url");
		this.loadRevision = loadRevision;
		this.settings = Objects.requireNonNull(settings, "settings");
		this.distanceGain = clampDistanceGain(distanceGain);
		this.textureId = textureId(key);
		try {
			this.mrl = WaterMediaCompat.mrl(url);
		} catch (RuntimeException exception) {
			this.fail("无法初始化 WATERMeDIA", exception);
		}
		this.startHlsProbeIfNeeded(false);
	}

	String url() {
		return this.url;
	}

	int loadRevision() {
		return this.loadRevision;
	}

	void tick(final ScreenSettings currentSettings, final double currentDistanceGain, final boolean paused) {
		ScreenSettings previousSettings = this.settings;
		boolean settingsChanged = !previousSettings.equals(currentSettings);
		boolean effectivePaused = this.manualPauseOverride != null ? this.manualPauseOverride : paused;
		boolean resumed = this.hasPauseState && this.lastPausedState && !effectivePaused;
		this.settings = Objects.requireNonNull(currentSettings, "currentSettings");
		this.distanceGain = clampDistanceGain(currentDistanceGain);
		this.pauseRequested = effectivePaused;
		if (!currentSettings.refreshOnResume()) {
			this.refreshPending = false;
		} else if (settingsChanged || resumed) {
			this.requestLiveRefresh();
		}
		this.hasPauseState = true;
		this.lastPausedState = effectivePaused;
		if (this.closed || this.state == MediaState.ERROR) {
			return;
		}

		if (this.player == null) {
			this.createPlayerIfReady();
		}

		if (this.player != null && this.refreshPending && !this.pauseRequested && !this.manuallyStopped) {
			this.tryRefreshLive();
		}
		if (this.player == null && !this.closed && this.state != MediaState.ERROR) {
			this.createPlayerIfReady();
		}

		if (this.player != null) {
			WaterMediaCompat.MediaPlayerHandle current = this.player;
			this.applyPlayerSettings();
			this.updatePlayer();
			if (this.player != null) {
				this.startHlsProbeIfNeeded(this.isLivePlayer(current));
			}
		}
	}

	MediaPlaybackInfo playbackInfo() {
		WaterMediaCompat.MediaPlayerHandle current = this.player;
		if (this.closed || this.state == MediaState.ERROR || current == null) {
			return MediaPlaybackInfo.EMPTY;
		}
		try {
			long durationMs = current.durationMs();
			boolean live = this.isLivePlayer(current);
			WaterMediaCompat.HlsMetadata metadata = this.hlsMetadata.get();
			long segmentDurationMs = live && metadata != null ? metadata.segmentDurationMs() : 0L;
			boolean canSeek = !live && durationMs > 0L && current.canSeek();
			return new MediaPlaybackInfo(
				current.timeMs(),
				durationMs,
				segmentDurationMs,
				live,
				canSeek,
				true
			);
		} catch (RuntimeException exception) {
			return new MediaPlaybackInfo(0L, 0L, 0L, false, false, true);
		}
	}

	boolean seek(final long timeMs, final boolean precise) {
		WaterMediaCompat.MediaPlayerHandle current = this.player;
		if (this.closed || this.state == MediaState.ERROR || current == null) {
			return false;
		}
		try {
			long durationMs = current.durationMs();
			if (durationMs <= 0L || !current.canSeek()) {
				return false;
			}
			long target = Math.max(0L, Math.min(durationMs, timeMs));
			return precise ? current.seek(target) : current.seekQuick(target);
		} catch (RuntimeException exception) {
			OneScreen.LOGGER.debug("Failed to seek media at {}", this.key.pos(), exception);
			return false;
		}
	}

	boolean play() {
		WaterMediaCompat.MediaPlayerHandle current = this.player;
		if (this.closed || this.state == MediaState.ERROR || current == null) {
			return false;
		}
		try {
			String status = current.status();
			boolean restarting = false;
			switch (status) {
				case "STOPPED", "ENDED" -> {
					if (!current.start()) {
						return false;
					}
					restarting = true;
				}
				case "PAUSED", "WAITING", "LOADING", "BUFFERING" -> current.resume();
				case "ERROR" -> {
					return false;
				}
				default -> {
				}
			}
			this.manualPauseOverride = false;
			if (!restarting) {
				this.manuallyStopped = false;
			}
			this.pauseRequested = false;
			this.appliedPaused = false;
			return true;
		} catch (RuntimeException exception) {
			OneScreen.LOGGER.debug("Failed to play media at {}", this.key.pos(), exception);
			return false;
		}
	}

	boolean pause() {
		WaterMediaCompat.MediaPlayerHandle current = this.player;
		if (this.closed || this.state == MediaState.ERROR || current == null) {
			return false;
		}
		try {
			String status = current.status();
			if ("STOPPED".equals(status) || "ENDED".equals(status) || "ERROR".equals(status)) {
				return false;
			}
			this.manualPauseOverride = true;
			this.manuallyStopped = false;
			this.pauseRequested = true;
			this.appliedPaused = true;
			current.pause();
			return true;
		} catch (RuntimeException exception) {
			OneScreen.LOGGER.debug("Failed to pause media at {}", this.key.pos(), exception);
			return false;
		}
	}

	boolean stop() {
		WaterMediaCompat.MediaPlayerHandle current = this.player;
		if (this.closed || current == null) {
			return false;
		}
		try {
			this.manualPauseOverride = false;
			this.manuallyStopped = true;
			this.pauseRequested = false;
			this.appliedPaused = false;
			return current.stop();
		} catch (RuntimeException exception) {
			OneScreen.LOGGER.debug("Failed to stop media at {}", this.key.pos(), exception);
			return false;
		}
	}

	void clearManualPauseOverride() {
		this.manualPauseOverride = null;
	}
	MediaSnapshot snapshot() {
		if (!this.closed && this.state != MediaState.ERROR) {
			try {
				this.updateVideoTexture();
			} catch (RuntimeException exception) {
				this.fail("无法读取媒体画面", exception);
			}
		}

		Identifier availableTexture = this.textureRegistered ? this.textureId : null;
		if (!this.closed && this.player != null) {
			this.state = this.playbackState();
		}
		return new MediaSnapshot(
			this.state,
			availableTexture,
			this.registeredMediaWidth,
			this.registeredMediaHeight,
			this.error
		);
	}

	void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		OneScreen.LOGGER.info("Closing media session at {}", this.key.pos());
		this.unregisterTexture();
		this.releasePlayer();
	}

	private void createPlayerIfReady() {
		WaterMediaCompat.MediaResource current = this.mrl;
		if (current == null) {
			return;
		}

		String resolution = current.status();
		if ("FETCHING".equals(resolution)) {
			this.state = MediaState.RESOLVING;
			return;
		}
		if (!"LOADED".equals(resolution)) {
			this.fail("无法解析媒体地址；状态为 " + resolution, current.exception());
			return;
		}

		WaterMediaCompat.Source source = current.source(0);
		if (source == null) {
			this.fail("媒体没有可播放源", null);
			return;
		}
		if (source.kind() == WaterMediaCompat.MediaKind.SUBTITLES) {
			this.fail("不支持单独的字幕媒体", null);
			return;
		}

		this.state = MediaState.LOADING;
		boolean image = source.kind() == WaterMediaCompat.MediaKind.IMAGE;
		this.videoOutput = source.kind() == WaterMediaCompat.MediaKind.VIDEO
			|| source.kind() == WaterMediaCompat.MediaKind.UNKNOWN;
		try {
			Supplier<Object> graphicsSupplier;
			if (image) {
				WaterMediaCompat.MediaEngine engine = WaterMediaCompat.headlessEngine();
				this.graphicsEngine = engine;
				this.imageEngine = engine;
				graphicsSupplier = engine::handle;
			} else if (this.videoOutput) {
				WaterMediaCompat.MediaEngine engine = WaterMediaCompat.awtEngine(this.videoFrameSequence::incrementAndGet);
				this.graphicsEngine = engine;
				this.videoEngine = engine;
				graphicsSupplier = engine::handle;
			} else {
				graphicsSupplier = () -> null;
			}

			this.player = WaterMediaCompat.createPlayer(current, graphicsSupplier);
		} catch (RuntimeException exception) {
			this.fail("无法创建媒体播放器", exception);
			return;
		}

		if (this.player == null) {
			this.fail("WATERMeDIA 未创建播放器", null);
			return;
		}
		if (this.videoOutput) {
			this.player.maxSize(VIDEO_MAX_WIDTH, VIDEO_MAX_HEIGHT);
		}
		this.applyPlayerSettings();
		this.player.start();
		this.manuallyStopped = false;
		this.appliedPaused = null;
		this.applyPauseState(this.player);
	}

	private void updatePlayer() {
		WaterMediaCompat.MediaPlayerHandle current = this.player;
		if (current == null) {
			return;
		}

		String status = current.status();
		if (!status.equals(this.lastLoggedStatus)) {
			this.lastLoggedStatus = status;
			OneScreen.LOGGER.info("Media player status at {}: {}", this.key.pos(), status);
		}
		if ("ERROR".equals(status)) {
			this.fail("媒体解码失败", null);
		} else if ("STOPPED".equals(status) && !this.closed && !this.manuallyStopped) {
			this.fail("媒体播放器意外停止", null);
		} else if (this.manuallyStopped && !"STOPPED".equals(status) && !"ENDED".equals(status)) {
			this.manuallyStopped = false;
		}
	}

	private void applyPlayerSettings() {
		WaterMediaCompat.MediaPlayerHandle current = this.player;
		if (current == null) {
			return;
		}

		float masterVolume = this.settings.volume();
		int volumePercent = Math.round(masterVolume * (float) this.distanceGain * 100.0F);
		volumePercent = Math.max(0, Math.min(100, volumePercent));
		if (volumePercent != this.appliedVolumePercent) {
			current.volume(volumePercent);
			this.appliedVolumePercent = volumePercent;
		}
		if (this.settings.muted() != this.appliedMuted) {
			current.mute(this.settings.muted());
			this.appliedMuted = this.settings.muted();
		}
		if (this.appliedLoop == null || this.settings.loop() != this.appliedLoop) {
			current.repeat(this.settings.loop());
			this.appliedLoop = this.settings.loop();
		}
		this.applyPauseState(current);
	}

	private void applyPauseState(final WaterMediaCompat.MediaPlayerHandle current) {
		if (this.appliedPaused != null && this.appliedPaused == this.pauseRequested) {
			return;
		}
		if (this.pauseRequested) {
			current.pause();
		} else if ("PAUSED".equals(current.status())) {
			current.resume();
		}
		this.appliedPaused = this.pauseRequested;
	}

	private void requestLiveRefresh() {
		if (!this.refreshPending) {
			this.refreshPendingSinceNanos = System.nanoTime();
		}
		this.refreshPending = true;
	}

	private void tryRefreshLive() {
		WaterMediaCompat.MediaPlayerHandle current = this.player;
		if (current == null || this.pauseRequested) {
			return;
		}

		boolean live;
		try {
			live = this.isLivePlayer(current);
		} catch (RuntimeException exception) {
			this.refreshPending = false;
			return;
		}
		long now = System.nanoTime();
		if (!live) {
			try {
				if (current.durationMs() > 0L || current.canSeek()) {
					this.refreshPending = false;
				}
			} catch (RuntimeException exception) {
				this.refreshPending = false;
			}
			if (now - this.refreshPendingSinceNanos >= LIVE_REFRESH_PENDING_TIMEOUT_NANOS) {
				this.refreshPending = false;
			}
			return;
		}
		if (this.lastLiveResyncNanos != 0L && now - this.lastLiveResyncNanos < LIVE_RESYNC_DEBOUNCE_NANOS) {
			return;
		}

		this.refreshPending = false;
		this.lastLiveResyncNanos = now;
		OneScreen.LOGGER.info("Refreshing live media at {} after pause or configuration update", this.key.pos());
		this.releasePlayer();
		this.state = MediaState.LOADING;
		this.createPlayerIfReady();
	}

	private boolean isLivePlayer(final WaterMediaCompat.MediaPlayerHandle current) {
		WaterMediaCompat.HlsMetadata metadata = this.hlsMetadata.get();
		return current.live() || metadata != null && metadata.live();
	}

	private void startHlsProbeIfNeeded(final boolean detectedLive) {
		if (this.closed || this.state == MediaState.ERROR
			|| (!detectedLive && !looksLikeHls(this.url))
			|| !this.hlsProbeStarted.compareAndSet(false, true)) {
			return;
		}
		Thread probe = new Thread(() -> {
			WaterMediaCompat.HlsMetadata metadata = WaterMediaCompat.hlsMetadata(this.url);
			if (metadata == null) {
				metadata = HlsPlaylistResolver.resolve(this.url);
			}
			if (metadata == null || this.closed) {
				return;
			}
			this.hlsMetadata.set(metadata);
			OneScreen.LOGGER.debug(
				"HLS progress metadata at {}: segment={}ms, live={}",
				this.key.pos(),
				metadata.segmentDurationMs(),
				metadata.live()
			);
		}, "onescreen-hls-progress-" + this.key.pos().toShortString());
		probe.setDaemon(true);
		probe.start();
	}

	private static boolean looksLikeHls(final String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		return lower.contains(".m3u8") || lower.contains(".m3u");
	}

	private void updateVideoTexture() {
		if (!this.videoOutput || this.videoEngine == null) {
			this.updateImageTexture();
			return;
		}

		WaterMediaCompat.MediaEngine engine = this.videoEngine;
		long sequence = this.videoFrameSequence.get();
		int width = engine.width();
		int height = engine.height();
		BufferedImage frame = engine.image();
		if (sequence <= 0L || frame == null || width <= 0 || height <= 0 || sequence == this.copiedFrameSequence) {
			return;
		}

		try {
			if (!this.textureRegistered || this.registeredMediaWidth != width || this.registeredMediaHeight != height) {
				this.unregisterTexture();
				DynamicTexture created = MediaTextureUploader.createArgb(
					frame,
					width,
					height,
					"onescreen video " + this.key.pos().toShortString()
				);
				Minecraft.getInstance().getTextureManager().register(this.textureId, created);
				this.texture = created;
				this.textureRegistered = true;
				this.registeredMediaWidth = width;
				this.registeredMediaHeight = height;
				this.logFirstFrame(width, height, "ARGB");
			} else {
				DynamicTexture currentTexture = this.texture;
				if (currentTexture == null) {
					return;
				}
				MediaTextureUploader.updateArgb(currentTexture, frame, width, height);
			}
			this.copiedFrameSequence = sequence;
		} catch (RuntimeException exception) {
			this.fail("无法复制视频帧", exception);
		}
	}

	private void updateImageTexture() {
		WaterMediaCompat.MediaEngine engine = this.imageEngine;
		if (engine == null) {
			return;
		}

		long uploadCount = engine.uploadCount();
		WaterMediaCompat.PixelFormat format = engine.format();
		int width = engine.width();
		int height = engine.height();
		if (uploadCount <= 0L || format == null || width <= 0 || height <= 0 || uploadCount == this.copiedFrameSequence) {
			return;
		}

		ByteBuffer pixels = engine.lastUpload();
		if (pixels == null) {
			return;
		}

		try {
			if (!this.textureRegistered || this.registeredMediaWidth != width || this.registeredMediaHeight != height) {
				this.unregisterTexture();
				DynamicTexture created = MediaTextureUploader.create(
					pixels,
					format,
					width,
					height,
					"onescreen image " + this.key.pos().toShortString()
				);
				Minecraft.getInstance().getTextureManager().register(this.textureId, created);
				this.texture = created;
				this.textureRegistered = true;
				this.registeredMediaWidth = width;
				this.registeredMediaHeight = height;
				this.logFirstFrame(width, height, format.name());
			} else {
				DynamicTexture currentTexture = this.texture;
				if (currentTexture == null) {
					return;
				}
				MediaTextureUploader.update(currentTexture, pixels, format, width, height);
			}
			this.copiedFrameSequence = uploadCount;
		} catch (RuntimeException exception) {
			this.fail("无法复制媒体像素", exception);
		}
	}

	private MediaState playbackState() {
		if (this.closed) {
			return MediaState.IDLE;
		}
		if (this.state == MediaState.ERROR) {
			return MediaState.ERROR;
		}
		if (!this.videoOutput) {
			return this.textureRegistered ? MediaState.READY : this.state;
		}

		WaterMediaCompat.MediaPlayerHandle current = this.player;
		if (current == null) {
			return this.state;
		}
		return switch (current.status()) {
			case "PLAYING" -> this.textureRegistered ? MediaState.PLAYING : MediaState.LOADING;
			case "PAUSED" -> this.textureRegistered ? MediaState.PAUSED : MediaState.LOADING;
			case "ENDED" -> this.textureRegistered ? MediaState.ENDED : MediaState.LOADING;
			case "ERROR" -> MediaState.ERROR;
			case "WAITING", "LOADING", "BUFFERING" -> MediaState.LOADING;
			case "STOPPED" -> MediaState.ENDED;
			default -> MediaState.LOADING;
		};
	}

	private void logFirstFrame(final int width, final int height, final String format) {
		if (this.firstFrameLogged) {
			return;
		}
		this.firstFrameLogged = true;
		OneScreen.LOGGER.info("Media texture ready at {} ({}x{}, format={})", this.key.pos(), width, height, format);
	}

	private void unregisterTexture() {
		this.textureRegistered = false;
		this.copiedFrameSequence = -1L;
		this.texture = null;
		try {
			Minecraft.getInstance().getTextureManager().release(this.textureId);
		} catch (RuntimeException exception) {
			OneScreen.LOGGER.warn("Failed to release media texture for {}", this.key.pos(), exception);
		}
	}

	private void releasePlayer() {
		WaterMediaCompat.MediaPlayerHandle current = this.player;
		this.player = null;
		this.graphicsEngine = null;
		this.imageEngine = null;
		this.videoEngine = null;
		if (current == null) {
			return;
		}
		try {
			current.release();
		} catch (RuntimeException exception) {
			OneScreen.LOGGER.warn("Failed to release media player for {}", this.key.pos(), exception);
		}
	}

	private void fail(final String message, final @Nullable Throwable cause) {
		if (this.closed) {
			return;
		}
		this.state = MediaState.ERROR;
		this.error = trimError(message);
		this.unregisterTexture();
		this.releasePlayer();
		if (!this.failureLogged) {
			this.failureLogged = true;
			OneScreen.LOGGER.warn("Media error at {}: {}{}", this.key.pos(), this.error, cause == null ? "" : " (" + cause.getClass().getSimpleName() + ")");
		}
	}

	private static double clampDistanceGain(final double gain) {
		if (!Double.isFinite(gain)) {
			return 0.0D;
		}
		return Math.max(0.0D, Math.min(1.0D, gain));
	}

	private static Identifier textureId(final ScreenKey key) {
		String dimension = Integer.toUnsignedString(key.dimension().identifier().toString().hashCode(), 16);
		String position = Long.toUnsignedString(key.pos().asLong(), 16);
		return OneScreen.id("dynamic/" + dimension + "_" + position);
	}

	private static String trimError(final String message) {
		if (message.length() <= MAX_MEDIA_LOG_LENGTH) {
			return message;
		}
		return message.substring(0, MAX_MEDIA_LOG_LENGTH);
	}
}
