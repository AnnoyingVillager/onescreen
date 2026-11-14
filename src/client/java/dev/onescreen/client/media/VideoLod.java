package dev.onescreen.client.media;

import org.jspecify.annotations.Nullable;

/**
 * Distance tiers applied to WATERMeDIA's upload resolution.
 *
 * <p>The thresholds intentionally include a small hysteresis band so a player walking near a
 * boundary does not cause the decoder to change resolution every tick.</p>
 */
enum VideoLod {
	MAX,
	CLOSE,
	NEAR,
	FAR,
	FAR_AWAY;

	private static final double[] LOWER_BOUNDS = {0.0D, 0.40D, 0.60D, 0.78D, 0.92D};
	private static final double HYSTERESIS = 0.05D;

	static VideoLod select(final double distanceRatio, final @Nullable VideoLod current) {
		if (!Double.isFinite(distanceRatio) || distanceRatio < 0.0D) {
			return MAX;
		}

		VideoLod target = target(distanceRatio);
		if (current == null || target == current) {
			return target;
		}

		int currentIndex = current.ordinal();
		int targetIndex = target.ordinal();
		if (targetIndex > currentIndex) {
			double boundary = LOWER_BOUNDS[targetIndex];
			return distanceRatio >= boundary + HYSTERESIS ? target : current;
		}

		double boundary = LOWER_BOUNDS[currentIndex];
		return distanceRatio <= boundary - HYSTERESIS ? target : current;
	}

	private static VideoLod target(final double distanceRatio) {
		if (distanceRatio < LOWER_BOUNDS[1]) {
			return MAX;
		}
		if (distanceRatio < LOWER_BOUNDS[2]) {
			return CLOSE;
		}
		if (distanceRatio < LOWER_BOUNDS[3]) {
			return NEAR;
		}
		if (distanceRatio < LOWER_BOUNDS[4]) {
			return FAR;
		}
		return FAR_AWAY;
	}
}