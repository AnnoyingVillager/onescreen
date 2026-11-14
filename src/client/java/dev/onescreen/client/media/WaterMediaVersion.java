package dev.onescreen.client.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * WATERMeDIA version ordering used by OneScreen's compatibility layer.
 *
 * <p>WATERMeDIA treats {@code 3.0.0.x} builds as test builds. OneScreen therefore ranks every
 * non-release {@code 3.0.0.x} version below the formal {@code 3.0.0} baseline, even though a
 * generic four-component semantic comparator would normally do the opposite. Formal
 * {@code 3.0.x} releases continue to compare numerically.</p>
 */
public final class WaterMediaVersion {
	public static final String BASELINE = "3.0.0";
	public static final String MINIMUM_SUPPORTED = "3.0.0.0";

	private WaterMediaVersion() {
	}

	public static @Nullable String installed() {
		return FabricLoader.getInstance()
			.getModContainer("watermedia")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse(null);
	}

	public static boolean isSupported(final @Nullable String version) {
		if (version == null) {
			return false;
		}
		Parsed parsed = Parsed.parse(version);
		if (parsed.numbers().size() < 3) {
			return false;
		}
		int major = parsed.numbers().get(0);
		int minor = parsed.numbers().get(1);
		return major == 3 && minor == 0 && compare(version, MINIMUM_SUPPORTED) >= 0;
	}

	public static boolean isTestBuild(final @Nullable String version) {
		if (version == null) {
			return false;
		}
		Parsed parsed = Parsed.parse(version);
		return parsed.numbers().size() > 3
			&& parsed.numbers().get(0) == 3
			&& parsed.numbers().get(1) == 0
			&& parsed.numbers().get(2) == 0;
	}

	/**
	 * Compares two WATERMeDIA versions using OneScreen's release/test-build ordering.
	 */
	public static int compare(final String left, final String right) {
		Parsed lhs = Parsed.parse(left);
		Parsed rhs = Parsed.parse(right);
		List<Integer> leftNumbers = lhs.numbers();
		List<Integer> rightNumbers = rhs.numbers();

		int shared = Math.min(3, Math.min(leftNumbers.size(), rightNumbers.size()));
		for (int index = 0; index < shared; index++) {
			int result = Integer.compare(leftNumbers.get(index), rightNumbers.get(index));
			if (result != 0) {
				return result;
			}
		}

		boolean same300Core = leftNumbers.size() >= 3
			&& rightNumbers.size() >= 3
			&& leftNumbers.get(0) == 3
			&& leftNumbers.get(1) == 0
			&& leftNumbers.get(2) == 0
			&& rightNumbers.get(0) == 3
			&& rightNumbers.get(1) == 0
			&& rightNumbers.get(2) == 0;
		if (same300Core) {
			boolean leftBaseline = isFormalBaseline(lhs);
			boolean rightBaseline = isFormalBaseline(rhs);
			if (leftBaseline != rightBaseline) {
				return leftBaseline ? 1 : -1;
			}
		}

		int max = Math.max(leftNumbers.size(), rightNumbers.size());
		for (int index = 0; index < max; index++) {
			int leftValue = index < leftNumbers.size() ? leftNumbers.get(index) : 0;
			int rightValue = index < rightNumbers.size() ? rightNumbers.get(index) : 0;
			int result = Integer.compare(leftValue, rightValue);
			if (result != 0) {
				return result;
			}
		}

		if (lhs.suffix() != rhs.suffix()) {
			if (lhs.suffix().isEmpty()) {
				return 1;
			}
			if (rhs.suffix().isEmpty()) {
				return -1;
			}
			return lhs.suffix().compareToIgnoreCase(rhs.suffix());
		}
		return 0;
	}

	private static boolean isFormalBaseline(final Parsed parsed) {
		return parsed.numbers().size() == 3
			&& parsed.numbers().get(0) == 3
			&& parsed.numbers().get(1) == 0
			&& parsed.numbers().get(2) == 0
			&& parsed.suffix().isEmpty();
	}

	private record Parsed(List<Integer> numbers, String suffix) {
		private static Parsed parse(final String rawVersion) {
			String value = rawVersion.strip();
			if (!value.isEmpty() && (value.charAt(0) == 'v' || value.charAt(0) == 'V')) {
				value = value.substring(1);
			}
			int metadata = value.indexOf('+');
			if (metadata >= 0) {
				value = value.substring(0, metadata);
			}

			String[] pieces = value.split("[.\\-]", -1);
			List<Integer> numbers = new ArrayList<>(pieces.length);
			String suffix = "";
			for (String piece : pieces) {
				if (piece.isEmpty()) {
					continue;
				}
				int end = 0;
				while (end < piece.length() && Character.isDigit(piece.charAt(end))) {
					end++;
				}
				if (end == 0) {
					suffix = suffix.isEmpty() ? piece.toLowerCase(Locale.ROOT) : suffix + '-' + piece.toLowerCase(Locale.ROOT);
					continue;
				}
				try {
					numbers.add(Integer.parseInt(piece.substring(0, end)));
				} catch (NumberFormatException ignored) {
					numbers.add(Integer.MAX_VALUE);
				}
				if (end < piece.length()) {
					String tail = piece.substring(end).toLowerCase(Locale.ROOT);
					suffix = suffix.isEmpty() ? tail : suffix + '-' + tail;
				}
			}
			return new Parsed(List.copyOf(numbers), suffix);
		}
	}
}