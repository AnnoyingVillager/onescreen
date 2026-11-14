package dev.onescreen.client.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Fallback HLS metadata reader for cases where WATERMeDIA's optional playlist tool is unavailable.
 */
final class HlsPlaylistResolver {
	private static final int MAX_PLAYLIST_BYTES = 2 * 1024 * 1024;
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(5L))
		.build();

	private HlsPlaylistResolver() {
	}

	static WaterMediaCompat.@Nullable HlsMetadata resolve(final String url) {
		try {
			URI current = URI.create(url);
			for (int depth = 0; depth < 2; depth++) {
				ParsedPlaylist playlist = parse(fetch(current), current);
				if (playlist.variant() != null) {
					current = playlist.variant();
					continue;
				}
				return playlist.segmentDurationMs() > 0L
					? new WaterMediaCompat.HlsMetadata(playlist.segmentDurationMs(), playlist.live())
					: null;
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (IOException | RuntimeException ignored) {
			// HLS progress metadata is optional and must never interfere with playback.
		}
		return null;
	}

	private static String fetch(final URI uri) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(8L))
			.header("Accept", "application/vnd.apple.mpegurl, application/x-mpegURL, application/mpegurl, */*")
			.GET()
			.build();
		HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream body = response.body()) {
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return "";
			}
			byte[] bytes = body.readNBytes(MAX_PLAYLIST_BYTES + 1);
			if (bytes.length > MAX_PLAYLIST_BYTES) {
				return "";
			}
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	private static ParsedPlaylist parse(final String playlist, final URI base) {
		if (playlist.isBlank()) {
			return ParsedPlaylist.EMPTY;
		}
		String[] lines = playlist.split("\\R");
		for (int index = 0; index < lines.length; index++) {
			if (!lines[index].startsWith("#EXT-X-STREAM-INF:")) {
				continue;
			}
			for (int candidate = index + 1; candidate < lines.length; candidate++) {
				String line = lines[candidate].trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				try {
					return new ParsedPlaylist(base.resolve(line), 0L, false);
				} catch (IllegalArgumentException ignored) {
					return ParsedPlaylist.EMPTY;
				}
			}
		}

		double targetDurationSeconds = 0.0D;
		double totalSegmentSeconds = 0.0D;
		int segmentCount = 0;
		boolean vod = false;
		for (String rawLine : lines) {
			String line = rawLine.trim();
			if (line.startsWith("#EXT-X-TARGETDURATION:")) {
				targetDurationSeconds = parseDouble(line.substring("#EXT-X-TARGETDURATION:".length()));
			} else if (line.startsWith("#EXTINF:")) {
				String value = line.substring("#EXTINF:".length());
				int comma = value.indexOf(',');
				if (comma >= 0) {
					value = value.substring(0, comma);
				}
				double duration = parseDouble(value);
				if (duration > 0.0D) {
					totalSegmentSeconds += duration;
					segmentCount++;
				}
			} else if (line.toUpperCase(Locale.ROOT).startsWith("#EXT-X-PLAYLIST-TYPE:VOD")) {
				vod = true;
			}
		}
		if (targetDurationSeconds <= 0.0D && segmentCount > 0) {
			targetDurationSeconds = totalSegmentSeconds / segmentCount;
		}
		long segmentDurationMs = Math.round(targetDurationSeconds * 1000.0D);
		boolean live = segmentCount > 0 && !vod && !playlist.contains("#EXT-X-ENDLIST");
		return new ParsedPlaylist(null, segmentDurationMs, live);
	}

	private static double parseDouble(final String value) {
		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException ignored) {
			return 0.0D;
		}
	}

	private record ParsedPlaylist(@Nullable URI variant, long segmentDurationMs, boolean live) {
		private static final ParsedPlaylist EMPTY = new ParsedPlaylist(null, 0L, false);
	}
}
