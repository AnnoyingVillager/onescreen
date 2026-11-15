package dev.onescreen.client.media;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Bridges the two public WATERMeDIA API layouts used by 3.0.0.23 and 3.0.1.
 *
 * <p>3.0.0.23 exposes {@code org.watermedia.api.media.MediaAPI}; 3.0.1 moved the player API to
 * {@code org.watermedia.api.player.PlayerAPI}. Direct links against either layout make the mod fail
 * to load with the other version, so this class deliberately keeps all WATERMeDIA types reflective.</p>
 */
public final class WaterMediaCompat {
	private static final Detection DETECTION = detect();

	private WaterMediaCompat() {
	}

	public static boolean isAvailable() {
		return DETECTION.implementation() != null;
	}

	public static String apiDescription() {
		Reflective implementation = implementation();
		String version = WaterMediaVersion.installed();
		if (version == null) {
			return implementation.description();
		}
		String channel = WaterMediaVersion.isTestBuild(version)
			? "test build, ranked below 3.0.0"
			: "formal 3.0.x line";
		return implementation.description() + " version " + version + " (" + channel + ")";
	}

	static MediaResource mrl(final String url) {
		Reflective implementation = implementation();
		return new MediaResource(invoke(implementation.mrlFactory(), null, url)).reloadIfRegenerable();
	}

	static MediaEngine headlessEngine() {
		Reflective implementation = implementation();
		Object engine = implementation.legacy()
			? invoke(implementation.headlessFactory(), null, false)
			: invoke(implementation.headlessFactory(), null);
		return new MediaEngine(engine);
	}

	static MediaEngine awtEngine(final Runnable frameCallback) {
		Reflective implementation = implementation();
		return new MediaEngine(invoke(implementation.awtFactory(), null, frameCallback));
	}

	static MediaEngine glEngine(final Thread renderThread, final Executor renderExecutor) {
		Reflective implementation = implementation();
		return new MediaEngine(invoke(implementation.glEngineFactory(), null, renderThread, renderExecutor));
	}

	static MediaEngine vkEngine(final Object context) {
		Reflective implementation = implementation();
		return new MediaEngine(invoke(implementation.vkEngineFactory(), null, context));
	}

	static @Nullable MediaEngine gpuEngine(final Thread renderThread, final Executor renderExecutor) {
		Reflective implementation = implementation();
		String backend = RenderSystem.getDevice().getDeviceInfo().backendName();
		if (isOpenGl(backend)) {
			return new MediaEngine(invoke(implementation.glEngineFactory(), null, renderThread, renderExecutor));
		}
		if (isVulkan(backend)) {
			VulkanDevice device = WaterMediaVulkanContext.currentDevice();
			if (device == null) {
				return null;
			}
			WaterMediaVulkanContext.Bridge context = WaterMediaVulkanContext.create(device, implementation.vkContextClass());
			try {
				return new MediaEngine(invoke(implementation.vkEngineFactory(), null, context.proxy()), context);
			} catch (RuntimeException | Error exception) {
				context.close();
				throw exception;
			}
		}
		return null;
	}

	private static boolean isOpenGl(final String backend) {
		String normalized = backend.toLowerCase(java.util.Locale.ROOT);
		return normalized.contains("opengl") || normalized.equals("gl");
	}

	private static boolean isVulkan(final String backend) {
		return backend.toLowerCase(java.util.Locale.ROOT).contains("vulkan");
	}

	static MediaPlayerHandle createPlayer(final MediaResource resource, final Supplier<Object> graphicsSupplier) {
		Reflective implementation = implementation();
		Supplier<Object> audioSupplier = () -> invoke(implementation.jsEngineFactory(), null);
		Object player = invoke(
			implementation.playerFactory(),
			null,
			resource.handle(),
			0,
			graphicsSupplier,
			audioSupplier
		);
		return new MediaPlayerHandle(player);
	}

	/**
	 * Reads HLS target-duration metadata through WATERMeDIA's matching public playlist tool.
	 * This may block on network I/O and must only be called from a background thread.
	 */
	static @Nullable HlsMetadata hlsMetadata(final String url) {
		try {
			String toolName = classExists("org.watermedia.tools.M3UTool")
				? "org.watermedia.tools.M3UTool"
				: "org.watermedia.tools.MPEGTool";
			if (!classExists(toolName)) {
				return null;
			}
			Class<?> tool = Class.forName(toolName, false, WaterMediaCompat.class.getClassLoader());
			Method fetch = tool.getMethod("fetch", URI.class);
			Object playlist = invoke(fetch, null, URI.create(url));

			for (int depth = 0; depth < 2 && playlist != null; depth++) {
				Method variantsMethod = optionalMethod(playlist.getClass(), "variants");
				if (variantsMethod != null) {
					Object value = invoke(variantsMethod, playlist);
					if (value instanceof List<?> variants && !variants.isEmpty()) {
						Object variant = variants.get(0);
						Method uriMethod = optionalMethod(variant.getClass(), "uri");
						if (uriMethod != null && invoke(uriMethod, variant) instanceof URI variantUri) {
							playlist = invoke(fetch, null, variantUri);
							continue;
						}
					}
				}

				Method targetDurationMethod = optionalMethod(playlist.getClass(), "targetDuration");
				if (targetDurationMethod == null) {
					return null;
				}
				long targetDurationMs = Math.round(((Number) invoke(targetDurationMethod, playlist)).doubleValue() * 1000.0D);
				if (targetDurationMs <= 0L) {
					targetDurationMs = averageSegmentDurationMs(playlist);
				}
				if (targetDurationMs <= 0L) {
					return null;
				}
				boolean live = booleanMethod(playlist, "live");
				return new HlsMetadata(targetDurationMs, live);
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
			// Progress metadata is optional. Playback and automatic live refresh remain functional without it.
		}
		return null;
	}

	record HlsMetadata(long segmentDurationMs, boolean live) {
	}

	enum MediaKind {
		IMAGE,
		VIDEO,
		AUDIO,
		SUBTITLES,
		UNKNOWN;

		static MediaKind from(@Nullable final Object value) {
			String name = enumName(value);
			if (name == null) {
				return UNKNOWN;
			}
			return switch (name) {
				case "IMAGE" -> IMAGE;
				case "VIDEO" -> VIDEO;
				case "AUDIO" -> AUDIO;
				case "SUBTITLES" -> SUBTITLES;
				default -> UNKNOWN;
			};
		}
	}

	enum PixelFormat {
		GRAY,
		RGB,
		RGBA,
		BGRA;

		static @Nullable PixelFormat from(@Nullable final Object value) {
			String name = enumName(value);
			if (name == null) {
				return null;
			}
			return switch (name) {
				case "GRAY" -> GRAY;
				case "RGB" -> RGB;
				case "RGBA" -> RGBA;
				case "BGRA" -> BGRA;
				default -> null;
			};
		}
	}

	static final class MediaResource {
		private Object handle;
		private final Method statusMethod;
		private final @Nullable Method exceptionMethod;
		private final Method sourceMethod;
		private final @Nullable Method reloadMethod;

		private MediaResource(final Object handle) {
			this.handle = handle;
			this.statusMethod = method(handle.getClass(), "status");
			this.exceptionMethod = optionalMethod(handle.getClass(), "exception");
			this.sourceMethod = method(handle.getClass(), "source", int.class);
			this.reloadMethod = optionalMethod(handle.getClass(), "reload");
		}

		Object handle() {
			return this.handle;
		}

		MediaResource reloadIfRegenerable() {
			if (this.reloadMethod == null) {
				return this;
			}
			switch (this.status()) {
				case "ERROR", "BLOCKED", "EXPIRED", "FORGOTTEN" -> {
					Object refreshed = invoke(this.reloadMethod, this.handle);
					if (refreshed != null) {
						this.handle = refreshed;
					}
				}
				default -> {
				}
			}
			return this;
		}

		String status() {
			return enumName(invoke(this.statusMethod, this.handle));
		}

		@Nullable Throwable exception() {
			if (this.exceptionMethod == null) {
				return null;
			}
			Object value = invoke(this.exceptionMethod, this.handle);
			return value instanceof Throwable throwable ? throwable : null;
		}

		@Nullable Source source(final int index) {
			Object value = invoke(this.sourceMethod, this.handle, index);
			return value == null ? null : new Source(value);
		}
	}

	static final class Source {
		private final Object handle;
		private final Method typeMethod;

		private Source(final Object handle) {
			this.handle = handle;
			this.typeMethod = method(handle.getClass(), "type");
		}

		MediaKind kind() {
			return MediaKind.from(invoke(this.typeMethod, this.handle));
		}
	}

	static final class MediaEngine {
		private final Object handle;
		private final @Nullable AutoCloseable companion;
		private final Method releaseMethod;
		private final Method widthMethod;
		private final Method heightMethod;
		private final Method textureMethod;
		private final @Nullable Method formatMethod;
		private final @Nullable Method uploadCountMethod;
		private final @Nullable Method lastUploadMethod;
		private final @Nullable Method imageMethod;

		private MediaEngine(final Object handle) {
			this(handle, null);
		}

		private MediaEngine(final Object handle, final @Nullable AutoCloseable companion) {
			this.handle = handle;
			this.companion = companion;
			Class<?> type = handle.getClass();
			this.releaseMethod = method(type, "release");
			this.widthMethod = method(type, "width");
			this.heightMethod = method(type, "height");
			this.textureMethod = method(type, "texture");
			this.formatMethod = optionalMethod(type, "format");
			this.uploadCountMethod = optionalMethod(type, "uploadCount");
			this.lastUploadMethod = optionalMethod(type, "lastUpload");
			this.imageMethod = optionalMethod(type, "image");
		}

		Object handle() {
			return this.handle;
		}

		int width() {
			return ((Number) invoke(this.widthMethod, this.handle)).intValue();
		}

		int height() {
			return ((Number) invoke(this.heightMethod, this.handle)).intValue();
		}

		long texture() {
			return ((Number) invoke(this.textureMethod, this.handle)).longValue();
		}

		void release() {
			invoke(this.releaseMethod, this.handle);
			if (this.companion != null) {
				try {
					this.companion.close();
				} catch (Exception exception) {
					throw new IllegalStateException("Failed to close WATERMeDIA engine bridge", exception);
				}
			}
		}

		@Nullable PixelFormat format() {
			return this.formatMethod == null ? null : PixelFormat.from(invoke(this.formatMethod, this.handle));
		}

		long uploadCount() {
			if (this.uploadCountMethod == null) {
				return 0L;
			}
			return ((Number) invoke(this.uploadCountMethod, this.handle)).longValue();
		}

		@Nullable ByteBuffer lastUpload() {
			if (this.lastUploadMethod == null) {
				return null;
			}
			Object value = invoke(this.lastUploadMethod, this.handle);
			return value instanceof ByteBuffer buffer ? buffer : null;
		}

		@Nullable BufferedImage image() {
			if (this.imageMethod == null) {
				return null;
			}
			Object value = invoke(this.imageMethod, this.handle);
			return value instanceof BufferedImage image ? image : null;
		}
	}

	static final class MediaPlayerHandle {
		private final Object handle;
		private final Method statusMethod;
		private final Method volumeMethod;
		private final Method muteMethod;
		private final Method repeatMethod;
		private final Method pauseMethod;
		private final Method resumeMethod;
		private final Method startMethod;
		private final Method stopMethod;
		private final Method releaseMethod;
		private final Method timeMethod;
		private final Method durationMethod;
		private final Method canSeekMethod;
		private final Method seekMethod;
		private final Method seekQuickMethod;
		private final @Nullable Method lodMethod;
		private final @Nullable Method liveMethod;
		private final @Nullable Method liveSourceMethod;
		private final @Nullable Method hardwareAccelerationMethod;

		private MediaPlayerHandle(final Object handle) {
			this.handle = handle;
			Class<?> type = handle.getClass();
			this.statusMethod = method(type, "status");
			this.volumeMethod = method(type, "volume", int.class);
			this.muteMethod = method(type, "mute", boolean.class);
			this.repeatMethod = method(type, "repeat", boolean.class);
			this.pauseMethod = method(type, "pause");
			this.resumeMethod = method(type, "resume");
			this.startMethod = method(type, "start");
			this.stopMethod = method(type, "stop");
			this.releaseMethod = method(type, "release");
			this.timeMethod = method(type, "time");
			this.durationMethod = method(type, "duration");
			this.canSeekMethod = method(type, "canSeek");
			this.seekMethod = method(type, "seek", long.class);
			this.seekQuickMethod = method(type, "seekQuick", long.class);
			this.lodMethod = optionalMethod(type, "lod", lodLevelClass(type));
			this.liveMethod = optionalMethod(type, "live");
			this.liveSourceMethod = optionalMethod(type, "liveSource");
			Method hardwareAcceleration = optionalMethod(type, "hwAccel");
			if (hardwareAcceleration == null) {
				hardwareAcceleration = optionalMethod(type, "isHwAccel");
			}
			this.hardwareAccelerationMethod = hardwareAcceleration;
		}

		String status() {
			return enumName(invoke(this.statusMethod, this.handle));
		}

		void volume(final int percent) {
			invoke(this.volumeMethod, this.handle, percent);
		}

		void mute(final boolean muted) {
			invoke(this.muteMethod, this.handle, muted);
		}

		void repeat(final boolean repeat) {
			invoke(this.repeatMethod, this.handle, repeat);
		}

		boolean pause() {
			return (Boolean) invoke(this.pauseMethod, this.handle);
		}

		boolean resume() {
			return (Boolean) invoke(this.resumeMethod, this.handle);
		}

		boolean start() {
			return (Boolean) invoke(this.startMethod, this.handle);
		}

		boolean stop() {
			return (Boolean) invoke(this.stopMethod, this.handle);
		}

		void release() {
			invoke(this.releaseMethod, this.handle);
		}

		long timeMs() {
			return ((Number) invoke(this.timeMethod, this.handle)).longValue();
		}

		long durationMs() {
			return ((Number) invoke(this.durationMethod, this.handle)).longValue();
		}

		boolean live() {
			if (this.liveMethod != null) {
				return (Boolean) invoke(this.liveMethod, this.handle);
			}
			if (this.liveSourceMethod != null) {
				return (Boolean) invoke(this.liveSourceMethod, this.handle);
			}
			return false;
		}

		boolean canSeek() {
			return (Boolean) invoke(this.canSeekMethod, this.handle);
		}

		boolean seek(final long timeMs) {
			return (Boolean) invoke(this.seekMethod, this.handle, timeMs);
		}

		boolean seekQuick(final long timeMs) {
			return (Boolean) invoke(this.seekQuickMethod, this.handle, timeMs);
		}

		void lod(final String level) {
			Method target = this.lodMethod;
			if (target == null) {
				return;
			}
			@SuppressWarnings({"rawtypes", "unchecked"})
			Object value = Enum.valueOf((Class<? extends Enum>)target.getParameterTypes()[0], level);
			invoke(target, this.handle, value);
		}

		boolean hardwareAcceleration() {
			return this.hardwareAccelerationMethod != null
				&& (Boolean) invoke(this.hardwareAccelerationMethod, this.handle);
		}
	}

	private record Detection(@Nullable Reflective implementation, @Nullable Throwable error) {
	}

	private static Detection detect() {
		try {
			String waterMediaVersion = WaterMediaVersion.installed();
			if (waterMediaVersion != null && !WaterMediaVersion.isSupported(waterMediaVersion)) {
				return new Detection(
					null,
					new IllegalStateException("Unsupported WATERMeDIA version: " + waterMediaVersion)
				);
			}
			if (classExists("org.watermedia.api.player.PlayerAPI")) {
				return new Detection(new Reflective(false), null);
			}
			if (classExists("org.watermedia.api.media.MediaAPI")) {
				return new Detection(new Reflective(true), null);
			}
			return new Detection(null, null);
		} catch (ReflectiveOperationException exception) {
			return new Detection(null, exception);
		}
	}

	private static Reflective implementation() {
		Reflective implementation = DETECTION.implementation();
		if (implementation == null) {
			Throwable error = DETECTION.error();
			throw new IllegalStateException(
				error == null ? "WATERMeDIA player API is unavailable" : "Unsupported WATERMeDIA player API",
				error
			);
		}
		return implementation;
	}

	private static boolean classExists(final String name) {
		try {
			Class.forName(name, false, WaterMediaCompat.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	private static Method method(final Class<?> owner, final String name, final Class<?>... parameterTypes) {
		try {
			return owner.getMethod(name, parameterTypes);
		} catch (NoSuchMethodException exception) {
			throw new IllegalStateException("Missing WATERMeDIA method " + owner.getName() + "#" + name, exception);
		}
	}

	private static @Nullable Method optionalMethod(final Class<?> owner, final String name, final Class<?>... parameterTypes) {
		try {
			return owner.getMethod(name, parameterTypes);
		} catch (NoSuchMethodException ignored) {
			return null;
		}
	}

	private static Class<?> lodLevelClass(final Class<?> playerType) {
		for (Class<?> nested : playerType.getClasses()) {
			if ("LodLevel".equals(nested.getSimpleName())) {
				return nested;
			}
		}
		for (String name : List.of(
			"org.watermedia.api.player.MediaPlayer$LodLevel",
			"org.watermedia.api.media.players.MediaPlayer$LodLevel"
		)) {
			try {
				return Class.forName(name, false, WaterMediaCompat.class.getClassLoader());
			} catch (ClassNotFoundException ignored) {
			}
		}
		return Void.class;
	}

	private static boolean booleanMethod(final Object target, final String name) {
		Method targetMethod = optionalMethod(target.getClass(), name);
		return targetMethod != null && (Boolean) invoke(targetMethod, target);
	}

	private static long averageSegmentDurationMs(final Object playlist) {
		Method segmentsMethod = optionalMethod(playlist.getClass(), "segments");
		if (segmentsMethod == null || !(invoke(segmentsMethod, playlist) instanceof List<?> segments) || segments.isEmpty()) {
			return 0L;
		}
		double totalSeconds = 0.0D;
		for (Object segment : segments) {
			Method durationMethod = optionalMethod(segment.getClass(), "duration");
			if (durationMethod != null) {
				totalSeconds += ((Number) invoke(durationMethod, segment)).doubleValue();
			}
		}
		return Math.round(totalSeconds / segments.size() * 1000.0D);
	}

	private static Object invoke(final Method method, final @Nullable Object target, final Object... arguments) {
		try {
			return method.invoke(target, arguments);
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("WATERMeDIA call failed: " + method, cause);
		} catch (IllegalAccessException exception) {
			throw new IllegalStateException("Cannot access WATERMeDIA method: " + method, exception);
		}
	}

	private static String enumName(@Nullable final Object value) {
		if (value == null) {
			return "UNKNOWN";
		}
		if (value instanceof Enum<?> enumValue) {
			return enumValue.name();
		}
		return value.toString();
	}

	private static final class Reflective {
		private final boolean legacy;
		private final String description;
		private final Method mrlFactory;
		private final Method headlessFactory;
		private final Method awtFactory;
		private final Method glEngineFactory;
		private final Method vkEngineFactory;
		private final Class<?> vkContextClass;
		private final Method jsEngineFactory;
		private final Method playerFactory;

		private Reflective(final boolean legacy) throws ReflectiveOperationException {
			this.legacy = legacy;
			String apiClassName = legacy
				? "org.watermedia.api.media.MediaAPI"
				: "org.watermedia.api.player.PlayerAPI";
			String resourceClassName = legacy
				? "org.watermedia.api.media.MRL"
				: "org.watermedia.api.player.MRL";
			Class<?> apiClass = Class.forName(apiClassName, false, WaterMediaCompat.class.getClassLoader());
			Class<?> resourceClass = Class.forName(resourceClassName, false, WaterMediaCompat.class.getClassLoader());
			this.description = legacy ? "WATERMeDIA 3.0.0.x MediaAPI" : "WATERMeDIA 3.0.1+ PlayerAPI";
			this.mrlFactory = apiClass.getMethod("mrl", String.class);
			this.awtFactory = apiClass.getMethod("awtEngine", Runnable.class);
			this.glEngineFactory = apiClass.getMethod("glEngine", Thread.class, Executor.class);
			this.vkContextClass = Class.forName(
				legacy ? "org.watermedia.api.media.engines.vk.VKContext" : "org.watermedia.api.player.VKContext",
				false,
				WaterMediaCompat.class.getClassLoader()
			);
			this.vkEngineFactory = apiClass.getMethod("vkEngine", this.vkContextClass);
			this.headlessFactory = legacy
				? apiClass.getMethod("headlessEngine", boolean.class)
				: apiClass.getMethod("headlessEngine");
			this.jsEngineFactory = apiClass.getMethod("jsEngine");
			this.playerFactory = legacy
				? apiClass.getMethod("createPlayer", resourceClass, int.class, Supplier.class, Supplier.class)
				: apiClass.getMethod("player", resourceClass, int.class, Supplier.class, Supplier.class);
		}

		boolean legacy() {
			return this.legacy;
		}

		String description() {
			return this.description;
		}

		Method mrlFactory() {
			return this.mrlFactory;
		}

		Method headlessFactory() {
			return this.headlessFactory;
		}

		Method awtFactory() {
			return this.awtFactory;
		}

		Method glEngineFactory() {
			return this.glEngineFactory;
		}

		Method vkEngineFactory() {
			return this.vkEngineFactory;
		}

		Class<?> vkContextClass() {
			return this.vkContextClass;
		}

		Method jsEngineFactory() {
			return this.jsEngineFactory;
		}

		Method playerFactory() {
			return this.playerFactory;
		}
	}
}
