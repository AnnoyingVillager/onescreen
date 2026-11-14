package dev.onescreen.client.media;

import dev.onescreen.OneScreen;
import dev.onescreen.block.ScreenBlock;
import dev.onescreen.block.ScreenBlockEntity;
import dev.onescreen.screen.ScreenGeometry;
import dev.onescreen.screen.ScreenSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tracks loaded screens and owns one media session per world position.
 */
public final class ScreenMediaManager {
	private static final ScreenMediaManager INSTANCE = new ScreenMediaManager();
	private static final int MAX_ACTIVE_SESSIONS = 8;

	private final Map<ScreenKey, ScreenBlockEntity> screens = new HashMap<>();
	private final Map<ScreenKey, MediaSession> sessions = new HashMap<>();
	private final Set<ScreenKey> deferred = new HashSet<>();
	private @Nullable ClientLevel level;
	private @Nullable BlockPos editingPos;

	private ScreenMediaManager() {
	}

	public static ScreenMediaManager get() {
		return INSTANCE;
	}

	public void track(final ScreenBlockEntity screen) {
		if (!(screen.getLevel() instanceof ClientLevel currentLevel)) {
			return;
		}
		this.ensureLevel(currentLevel);
		this.screens.put(key(currentLevel, screen), screen);
	}

	public void trackChunk(final ClientLevel level, final LevelChunk chunk) {
		this.ensureLevel(level);
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (blockEntity instanceof ScreenBlockEntity screen) {
				this.screens.put(key(level, screen), screen);
			}
		}
	}

	public void untrackChunk(final ClientLevel level, final LevelChunk chunk) {
		this.ensureLevel(level);
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (blockEntity instanceof ScreenBlockEntity screen) {
				this.remove(key(level, screen));
			}
		}
	}

	public void tick(final ClientLevel currentLevel, final boolean paused) {
		this.ensureLevel(currentLevel);

		for (ScreenKey key : new ArrayList<>(this.screens.keySet())) {
			ScreenBlockEntity screen = this.screens.get(key);
			if (screen == null || screen.isRemoved() || screen.getLevel() != currentLevel
				|| currentLevel.getBlockEntity(screen.getBlockPos()) != screen) {
				this.remove(key);
				continue;
			}
			this.reconcile(key, screen.getSettings());
		}

		for (Map.Entry<ScreenKey, MediaSession> entry : new ArrayList<>(this.sessions.entrySet())) {
			ScreenBlockEntity screen = this.screens.get(entry.getKey());
			if (screen == null) {
				this.remove(entry.getKey());
				continue;
			}
			entry.getValue().tick(screen.getSettings(), audioDistanceGain(screen), paused);
		}
	}

	public MediaSnapshot snapshot(final ScreenBlockEntity screen) {
		if (!(screen.getLevel() instanceof ClientLevel currentLevel) || this.level != currentLevel) {
			return MediaSnapshot.EMPTY;
		}
		MediaSession session = this.sessions.get(key(currentLevel, screen));
		return session == null ? MediaSnapshot.EMPTY : session.snapshot();
	}

	public MediaPlaybackInfo playbackInfo(final BlockPos pos) {
		ClientLevel currentLevel = this.level;
		if (currentLevel == null) {
			return MediaPlaybackInfo.EMPTY;
		}
		MediaSession session = this.sessions.get(new ScreenKey(currentLevel.dimension(), pos.immutable()));
		return session == null ? MediaPlaybackInfo.EMPTY : session.playbackInfo();
	}

	public void beginEditing(final BlockPos pos) {
		this.editingPos = pos.immutable();
	}

	public void endEditing(final BlockPos pos) {
		if (this.editingPos != null && this.editingPos.equals(pos)) {
			ClientLevel currentLevel = this.level;
			if (currentLevel != null) {
				MediaSession session = this.sessions.get(new ScreenKey(currentLevel.dimension(), pos.immutable()));
				if (session != null) {
					session.clearManualPauseOverride();
				}
			}
			this.editingPos = null;
		}
	}

	public boolean play(final BlockPos pos) {
		MediaSession session = this.session(pos);
		return session != null && session.play();
	}

	public boolean pause(final BlockPos pos) {
		MediaSession session = this.session(pos);
		return session != null && session.pause();
	}

	public boolean stop(final BlockPos pos) {
		MediaSession session = this.session(pos);
		return session != null && session.stop();
	}
	public boolean seek(final BlockPos pos, final long timeMs, final boolean precise) {
		ClientLevel currentLevel = this.level;
		if (currentLevel == null) {
			return false;
		}
		MediaSession session = this.sessions.get(new ScreenKey(currentLevel.dimension(), pos.immutable()));
		return session != null && session.seek(timeMs, precise);
	}

	public void clear() {
		for (MediaSession session : this.sessions.values()) {
			session.close();
		}
		this.sessions.clear();
		this.deferred.clear();
		this.screens.clear();
		this.editingPos = null;
		this.level = null;
	}

	private @Nullable MediaSession session(final BlockPos pos) {
		ClientLevel currentLevel = this.level;
		if (currentLevel == null) {
			return null;
		}
		return this.sessions.get(new ScreenKey(currentLevel.dimension(), pos.immutable()));
	}

	private void ensureLevel(final ClientLevel currentLevel) {
		if (this.level == currentLevel) {
			return;
		}
		this.clear();
		this.level = currentLevel;
	}

	private void reconcile(final ScreenKey key, final ScreenSettings settings) {
		ScreenBlockEntity screen = this.screens.get(key);
		if (screen == null) {
			return;
		}
		int revision = screen.getLoadRevision();
		MediaSession session = this.sessions.get(key);
		if (session != null && session.url().equals(settings.url()) && session.loadRevision() == revision) {
			this.deferred.remove(key);
			return;
		}
		if (session != null) {
			session.close();
			this.sessions.remove(key);
		}
		if (!settings.hasUrl()) {
			this.deferred.remove(key);
			return;
		}
		if (this.sessions.size() >= MAX_ACTIVE_SESSIONS) {
			if (this.deferred.add(key)) {
				OneScreen.LOGGER.info("Media session limit {} reached; deferring {}", MAX_ACTIVE_SESSIONS, key.pos());
			}
			return;
		}

		double distanceGain = audioDistanceGain(screen);
		this.sessions.put(key, new MediaSession(key, settings.url(), revision, settings, distanceGain));
		this.deferred.remove(key);
	}

	private void remove(final ScreenKey key) {
		this.screens.remove(key);
		this.deferred.remove(key);
		MediaSession session = this.sessions.remove(key);
		if (session != null) {
			session.close();
		}
	}

	private static double audioDistanceGain(final ScreenBlockEntity screen) {
		var player = Minecraft.getInstance().player;
		if (player == null || !(screen.getLevel() instanceof ClientLevel level)) {
			return 0.0D;
		}
		if (player.level() != level) {
			return 0.0D;
		}
		Vec3 center = ScreenGeometry.mediaCenter(
			screen.getBlockPos(),
			screen.getBlockState().getValue(ScreenBlock.FACING),
			screen.getSettings()
		);
		double distance = player.position().distanceTo(center);
		double minDistance = ScreenSettings.MIN_AUDIO_DISTANCE;
		double maxDistance = screen.getSettings().audioDistance();
		if (distance <= minDistance) {
			return 1.0D;
		}
		if (distance >= maxDistance) {
			return 0.0D;
		}
		return 1.0D - (distance - minDistance) / (maxDistance - minDistance);
	}

	private static ScreenKey key(final ClientLevel level, final ScreenBlockEntity screen) {
		return new ScreenKey(level.dimension(), screen.getBlockPos());
	}
}
