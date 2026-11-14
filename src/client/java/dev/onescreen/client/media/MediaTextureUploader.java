package dev.onescreen.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ARGB;

/**
 * Copies WATERMeDIA CPU frames into a Minecraft-owned dynamic texture.
 * Keeping the texture in Minecraft's abstraction makes the video path independent of the active
 * OpenGL or Vulkan renderer.
 */
final class MediaTextureUploader {
	private MediaTextureUploader() {
	}

	static DynamicTexture create(
		final ByteBuffer source,
		final WaterMediaCompat.PixelFormat format,
		final int width,
		final int height,
		final String label
	) {
		validateDimensions(width, height);
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
		try {
			copyPixels(source, format, width, height, image);
			return new LinearDynamicTexture(label, image);
		} catch (RuntimeException exception) {
			image.close();
			throw exception;
		}
	}

	static DynamicTexture createArgb(
		final BufferedImage source,
		final int width,
		final int height,
		final String label
	) {
		validateDimensions(width, height);
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
		try {
			copyArgbPixels(source, width, height, image);
			return new LinearDynamicTexture(label, image);
		} catch (RuntimeException exception) {
			image.close();
			throw exception;
		}
	}

	static void update(
		final DynamicTexture texture,
		final ByteBuffer source,
		final WaterMediaCompat.PixelFormat format,
		final int width,
		final int height
	) {
		copyPixels(source, format, width, height, texture.getPixels());
		texture.upload();
	}

	static void updateArgb(
		final DynamicTexture texture,
		final BufferedImage source,
		final int width,
		final int height
	) {
		copyArgbPixels(source, width, height, texture.getPixels());
		texture.upload();
	}

	private static void validateDimensions(final int width, final int height) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Invalid frame dimensions: " + width + "x" + height);
		}
	}

	private static void copyPixels(
		final ByteBuffer source,
		final WaterMediaCompat.PixelFormat format,
		final int width,
		final int height,
		final NativeImage target
	) {
		validateTarget(target, width, height);
		int bytesPerPixel = switch (format) {
			case GRAY, RGB -> format == WaterMediaCompat.PixelFormat.GRAY ? 1 : 3;
			case RGBA, BGRA -> 4;
			default -> throw new IllegalArgumentException("Unsupported image pixel format: " + format);
		};
		ByteBuffer pixels = source.duplicate();
		int base = pixels.position();
		long required = (long) width * height * bytesPerPixel;
		if (pixels.remaining() < required) {
			throw new IllegalArgumentException("Frame buffer is too small: " + pixels.remaining() + " < " + required);
		}
		IntBuffer targetPixels = target.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
		for (int index = 0; index < width * height; index++) {
			int offset = base + index * bytesPerPixel;
			int alpha = 0xFF;
			int red;
			int green;
			int blue;
			switch (format) {
				case GRAY -> red = green = blue = pixels.get(offset) & 0xFF;
				case RGB -> {
					red = pixels.get(offset) & 0xFF;
					green = pixels.get(offset + 1) & 0xFF;
					blue = pixels.get(offset + 2) & 0xFF;
				}
				case RGBA -> {
					red = pixels.get(offset) & 0xFF;
					green = pixels.get(offset + 1) & 0xFF;
					blue = pixels.get(offset + 2) & 0xFF;
					alpha = pixels.get(offset + 3) & 0xFF;
				}
				case BGRA -> {
					blue = pixels.get(offset) & 0xFF;
					green = pixels.get(offset + 1) & 0xFF;
					red = pixels.get(offset + 2) & 0xFF;
					alpha = pixels.get(offset + 3) & 0xFF;
				}
				default -> throw new IllegalArgumentException("Unsupported image pixel format: " + format);
			}
			int argb = alpha << 24 | red << 16 | green << 8 | blue;
			targetPixels.put(index, ARGB.toABGR(argb));
		}
	}

	private static void copyArgbPixels(
		final BufferedImage source,
		final int width,
		final int height,
		final NativeImage target
	) {
		validateTarget(target, width, height);
		if (source == null || source.getWidth() != width || source.getHeight() != height) {
			throw new IllegalArgumentException("Video frame is unavailable or has an unexpected size");
		}
		if (!(source.getRaster().getDataBuffer() instanceof DataBufferInt data)) {
			throw new IllegalArgumentException("Video frame is not backed by packed ARGB pixels");
		}

		int[] pixels = data.getData();
		int required = width * height;
		if (pixels.length < required) {
			throw new IllegalArgumentException("Video pixel buffer is too small: " + pixels.length + " < " + required);
		}
		IntBuffer targetPixels = target.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
		for (int index = 0; index < required; index++) {
			targetPixels.put(index, ARGB.toABGR(pixels[index]));
		}
	}

	private static void validateTarget(final NativeImage target, final int width, final int height) {
		if (target.getWidth() != width || target.getHeight() != height) {
			throw new IllegalArgumentException(
				"Texture size changed: " + target.getWidth() + "x" + target.getHeight() + " != " + width + "x" + height
			);
		}
	}

	private static final class LinearDynamicTexture extends DynamicTexture {
		private LinearDynamicTexture(final String label, final NativeImage image) {
			super(() -> label, image);
			this.sampler = RenderSystem.getSamplerCache().getSampler(
				AddressMode.CLAMP_TO_EDGE,
				AddressMode.CLAMP_TO_EDGE,
				FilterMode.LINEAR,
				FilterMode.LINEAR,
				false
			);
		}
	}
}
