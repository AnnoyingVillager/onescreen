package dev.onescreen.screen;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/**
 * Server-side URL policy for screen configuration.
 */
public final class ScreenUrlValidator {
	private ScreenUrlValidator() {
	}

	public static String validate(final String rawUrl) {
		Objects.requireNonNull(rawUrl, "rawUrl");
		if (rawUrl.isBlank()) {
			throw new IllegalArgumentException("empty");
		}
		if (rawUrl.length() > ScreenSettings.MAX_URL_LENGTH) {
			throw new IllegalArgumentException("too_long");
		}
		for (int index = 0; index < rawUrl.length(); index++) {
			char character = rawUrl.charAt(index);
			int type = Character.getType(character);
			if (Character.isISOControl(character) || Character.isWhitespace(character) || type == Character.FORMAT) {
				throw new IllegalArgumentException("invalid_character");
			}
		}

		final URI uri;
		try {
			uri = new URI(rawUrl);
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("syntax", exception);
		}

		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			throw new IllegalArgumentException("scheme");
		}
		if (uri.isOpaque() || uri.getHost() == null || uri.getHost().isBlank()) {
			throw new IllegalArgumentException("host");
		}
		return rawUrl;
	}

	public static String summary(final String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return "(未设置)";
		}
		try {
			URI uri = new URI(rawUrl);
			String scheme = uri.getScheme() == null ? "?" : uri.getScheme().toLowerCase(Locale.ROOT);
			String host = uri.getHost() == null ? "?" : uri.getHost();
			String path = uri.getRawPath() == null ? "" : uri.getRawPath();
			StringBuilder summary = new StringBuilder(scheme).append("://").append(host);
			if (uri.getPort() >= 0) {
				summary.append(':').append(uri.getPort());
			}
			summary.append(path);
			if (uri.getRawQuery() != null) {
				summary.append("?…");
			}
			return summary.toString();
		} catch (URISyntaxException exception) {
			return "(无效 URL)";
		}
	}
}
