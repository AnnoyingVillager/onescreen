package dev.onescreen.screen;

import java.util.Locale;
import java.util.Optional;

/**
 * Nine-way placement of the media plane relative to the 1x1 anchor frame.
 * Positive horizontal means right; positive vertical means down.
 */
public enum ScreenAlignment {
	TOP_LEFT(-1, -1, "top_left", "↖"),
	UP(0, -1, "up", "↑"),
	TOP_RIGHT(1, -1, "top_right", "↗"),
	LEFT(-1, 0, "left", "←"),
	CENTER(0, 0, "center", "•"),
	RIGHT(1, 0, "right", "→"),
	BOTTOM_LEFT(-1, 1, "bottom_left", "↙"),
	DOWN(0, 1, "down", "↓"),
	BOTTOM_RIGHT(1, 1, "bottom_right", "↘");

	private final int horizontal;
	private final int vertical;
	private final String translationKey;
	private final String symbol;

	ScreenAlignment(final int horizontal, final int vertical, final String id, final String symbol) {
		this.horizontal = horizontal;
		this.vertical = vertical;
		this.translationKey = "onescreen.alignment." + id;
		this.symbol = symbol;
	}

	public int horizontal() {
		return this.horizontal;
	}

	public int vertical() {
		return this.vertical;
	}

	public String translationKey() {
		return this.translationKey;
	}

	public String symbol() {
		return this.symbol;
	}

	public static Optional<ScreenAlignment> parse(final String rawValue) {
		if (rawValue == null) {
			return Optional.empty();
		}
		String normalized = rawValue.trim().toLowerCase(Locale.ROOT).replace('-', '_');
		if (normalized.equals("top")) {
			normalized = "up";
		} else if (normalized.equals("bottom")) {
			normalized = "down";
		}
		for (ScreenAlignment alignment : values()) {
			String id = alignment.translationKey.substring("onescreen.alignment.".length());
			if (id.equals(normalized) || alignment.name().toLowerCase(Locale.ROOT).equals(normalized)) {
				return Optional.of(alignment);
			}
		}
		return Optional.empty();
	}

	public static ScreenAlignment byOrdinal(final int ordinal) {
		ScreenAlignment[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CENTER;
	}
}