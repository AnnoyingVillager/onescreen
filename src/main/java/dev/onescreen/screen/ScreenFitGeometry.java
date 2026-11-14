package dev.onescreen.screen;

/**
 * Pure geometry for mapping a media texture inside a screen plane.
 *
 * <p>The natural scale used by {@link ScreenFitMode#TILE} and {@link ScreenFitMode#CENTER} is one
 * block along the media's longer edge. UV values outside 0..1 are intentional for tiling and rely
 * on Minecraft's repeat sampler.</p>
 */
public final class ScreenFitGeometry {
	private static final float NATURAL_SIZE = 1.0F;

	private ScreenFitGeometry() {
	}

	public record Mapping(
		float width,
		float height,
		float u0,
		float v0,
		float u1,
		float v1
	) {
		public static Mapping full(final float width, final float height) {
			return new Mapping(width, height, 0.0F, 0.0F, 1.0F, 1.0F);
		}
	}

	public static Mapping calculate(
		final ScreenFitMode mode,
		final float screenWidth,
		final float screenHeight,
		final int mediaWidth,
		final int mediaHeight
	) {
		if (mediaWidth <= 0 || mediaHeight <= 0 || screenWidth <= 0.0F || screenHeight <= 0.0F) {
			return Mapping.full(screenWidth, screenHeight);
		}

		ScreenFitMode effectiveMode = mode == null ? ScreenFitMode.FIT : mode;
		float screenAspect = screenWidth / screenHeight;
		float mediaAspect = (float)mediaWidth / mediaHeight;
		return switch (effectiveMode) {
			case FIT -> {
				float[] size = contain(screenWidth, screenHeight, mediaAspect);
				yield Mapping.full(size[0], size[1]);
			}
			case STRETCH -> Mapping.full(screenWidth, screenHeight);
			case TILE -> {
				float[] tile = natural(mediaAspect);
				yield new Mapping(
					screenWidth,
					screenHeight,
					0.0F,
					0.0F,
					screenWidth / tile[0],
					screenHeight / tile[1]
				);
			}
			case FILL -> {
				float[] uv = coverUv(screenAspect, mediaAspect);
				yield new Mapping(screenWidth, screenHeight, uv[0], uv[1], uv[2], uv[3]);
			}
			case CENTER -> {
				float[] size = natural(mediaAspect);
				yield Mapping.full(size[0], size[1]);
			}
		};
	}

	private static float[] contain(final float width, final float height, final float mediaAspect) {
		if (mediaAspect > width / height) {
			return new float[] { width, width / mediaAspect };
		}
		return new float[] { height * mediaAspect, height };
	}

	private static float[] natural(final float mediaAspect) {
		if (mediaAspect >= 1.0F) {
			return new float[] { NATURAL_SIZE, NATURAL_SIZE / mediaAspect };
		}
		return new float[] { NATURAL_SIZE * mediaAspect, NATURAL_SIZE };
	}

	private static float[] coverUv(final float screenAspect, final float mediaAspect) {
		if (mediaAspect > screenAspect) {
			float visible = screenAspect / mediaAspect;
			float inset = (1.0F - visible) * 0.5F;
			return new float[] { inset, 0.0F, 1.0F - inset, 1.0F };
		}

		float visible = mediaAspect / screenAspect;
		float inset = (1.0F - visible) * 0.5F;
		return new float[] { 0.0F, inset, 1.0F, 1.0F - inset };
	}
}
