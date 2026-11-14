package dev.onescreen.client.media;

/**
 * Thread-safe snapshot of the local player clock used by the editor progress control.
 */
public record MediaPlaybackInfo(
	long timeMs,
	long durationMs,
	long segmentDurationMs,
	boolean live,
	boolean canSeek,
	boolean ready
) {
	public static final MediaPlaybackInfo EMPTY =
		new MediaPlaybackInfo(0L, 0L, 0L, false, false, false);

	public MediaPlaybackInfo {
		timeMs = Math.max(0L, timeMs);
		durationMs = Math.max(0L, durationMs);
		segmentDurationMs = Math.max(0L, segmentDurationMs);
	}

	public long progressTimeMs() {
		if (!this.live) {
			return Math.min(this.timeMs, this.durationMs);
		}
		return this.segmentDurationMs <= 0L ? 0L : Math.floorMod(this.timeMs, this.segmentDurationMs);
	}

	public long progressDurationMs() {
		return this.live ? this.segmentDurationMs : this.durationMs;
	}

	public double progressFraction() {
		long total = this.progressDurationMs();
		if (total <= 0L) {
			return 0.0D;
		}
		return Math.max(0.0D, Math.min(1.0D, (double) this.progressTimeMs() / (double) total));
	}

	public long seekTimeMs(final double fraction) {
		if (!this.canSeek || this.durationMs <= 0L) {
			return 0L;
		}
		double clamped = Math.max(0.0D, Math.min(1.0D, fraction));
		return Math.min(this.durationMs, Math.round(clamped * this.durationMs));
	}
}
