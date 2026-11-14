package dev.onescreen.screen;

import java.util.Locale;
import java.util.Optional;

/**
 * How the media texture is mapped onto the configured screen plane.
 */
public enum ScreenFitMode {
	FIT("fit", "onescreen.fit.fit"),
	STRETCH("stretch", "onescreen.fit.stretch"),
	TILE("tile", "onescreen.fit.tile"),
	FILL("fill", "onescreen.fit.fill"),
	CENTER("center", "onescreen.fit.center");

	private final String id;
	private final String translationKey;

	ScreenFitMode(final String id, final String translationKey) {
		this.id = id;
		this.translationKey = translationKey;
	}

	public String id() {
		return this.id;
	}

	public String translationKey() {
		return this.translationKey;
	}

	public static Optional<ScreenFitMode> parse(final String rawValue) {
		if (rawValue == null) {
			return Optional.empty();
		}
		String normalized = rawValue.trim().toLowerCase(Locale.ROOT).replace('-', '_');
		for (ScreenFitMode mode : values()) {
			if (mode.id.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
				return Optional.of(mode);
			}
		}
		return switch (normalized) {
			case "contain", "适应" -> Optional.of(FIT);
			case "stretch", "拉伸" -> Optional.of(STRETCH);
			case "repeat", "平铺" -> Optional.of(TILE);
			case "cover", "填充" -> Optional.of(FILL);
			case "original", "none", "居中" -> Optional.of(CENTER);
			default -> Optional.empty();
		};
	}

	public static ScreenFitMode byOrdinal(final int ordinal) {
		ScreenFitMode[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FIT;
	}
}
