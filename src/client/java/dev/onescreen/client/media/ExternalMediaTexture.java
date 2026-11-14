package dev.onescreen.client.media;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import java.util.function.LongSupplier;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * A Minecraft texture facade around a WATERMeDIA-owned GPU texture.
 *
 * <p>WATERMeDIA may rotate its texture handle as frames are uploaded, so both backend views read
 * the current handle at draw time. Resource ownership stays with WATERMeDIA; this class only owns
 * the small backend view used by Minecraft.</p>
 */
final class ExternalMediaTexture extends AbstractTexture {
	private final boolean ownsTexture;
	private boolean closed;

	private ExternalMediaTexture(
		final GpuTexture texture,
		final GpuTextureView textureView,
		final boolean ownsTexture
	) {
		this.texture = texture;
		this.textureView = textureView;
		this.ownsTexture = ownsTexture;
		this.sampler = RenderSystem.getSamplerCache().getSampler(
			AddressMode.CLAMP_TO_EDGE,
			AddressMode.CLAMP_TO_EDGE,
			FilterMode.LINEAR,
			FilterMode.LINEAR,
			false
		);
	}

	static ExternalMediaTexture create(
		final LongSupplier textureHandle,
		final int width,
		final int height,
		final String label
	) {
		GpuDevice device = RenderSystem.getDevice();
		String backend = device.getDeviceInfo().backendName();
		if (isOpenGl(backend)) {
			WaterMediaGlTexture texture = new WaterMediaGlTexture(
				textureHandle,
				label,
				width,
				height
			);
			return new ExternalMediaTexture(texture, new WaterMediaGlView(texture, textureHandle), false);
		}
		if (isVulkan(backend)) {
			VulkanDevice vulkanDevice = WaterMediaVulkanContext.currentDevice();
			if (vulkanDevice == null) {
				throw new IllegalStateException("Minecraft Vulkan backend is not accessible");
			}
			VulkanGpuTexture dummy = (VulkanGpuTexture)device.createTexture(
				label,
				GpuTexture.USAGE_TEXTURE_BINDING,
				GpuFormat.RGBA8_UNORM,
				width,
				height,
				1,
				1
			);
			return new ExternalMediaTexture(
				dummy,
				new WaterMediaVulkanView(vulkanDevice, dummy, textureHandle),
				true
			);
		}
		throw new IllegalStateException("Unsupported Minecraft GPU backend: " + backend);
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		GpuTextureView currentView = this.textureView;
		GpuTexture currentTexture = this.texture;
		this.textureView = null;
		this.texture = null;
		if (currentView != null) {
			currentView.close();
		}
		if (this.ownsTexture && currentTexture != null) {
			currentTexture.close();
		}
	}

	private static boolean isOpenGl(final String backend) {
		String normalized = backend.toLowerCase(java.util.Locale.ROOT);
		return normalized.contains("opengl") || normalized.contains("gl");
	}

	private static boolean isVulkan(final String backend) {
		return backend.toLowerCase(java.util.Locale.ROOT).contains("vulkan");
	}

	private static final class WaterMediaGlTexture extends GlTexture {
		private final LongSupplier textureHandle;
		private boolean closed;

		private WaterMediaGlTexture(
			final LongSupplier textureHandle,
			final String label,
			final int width,
			final int height
		) {
			super(
				GpuTexture.USAGE_TEXTURE_BINDING,
				label,
				GpuFormat.RGBA8_UNORM,
				width,
				height,
				1,
				1,
				(int)textureHandle.getAsLong(),
				null
			);
			this.textureHandle = textureHandle;
		}

		@Override
		public int glId() {
			return (int)this.textureHandle.getAsLong();
		}

		@Override
		public void close() {
			this.closed = true;
		}

		@Override
		public boolean isClosed() {
			return this.closed;
		}
	}

	private static final class WaterMediaGlView extends GlTextureView {
		private final LongSupplier textureHandle;
		private boolean closed;

		private WaterMediaGlView(final WaterMediaGlTexture texture, final LongSupplier textureHandle) {
			super(texture, 0, 1, null);
			this.textureHandle = textureHandle;
		}

		@Override
		public int glId() {
			return (int)this.textureHandle.getAsLong();
		}

		@Override
		public void close() {
			if (!this.closed) {
				this.closed = true;
				this.texture().removeViews();
			}
		}

		@Override
		public boolean isClosed() {
			return this.closed;
		}
	}

	private static final class WaterMediaVulkanView extends VulkanGpuTextureView {
		private final LongSupplier textureHandle;

		private WaterMediaVulkanView(
			final VulkanDevice device,
			final VulkanGpuTexture texture,
			final LongSupplier textureHandle
		) {
			super(device, texture, 0, 1);
			this.textureHandle = textureHandle;
		}

		@Override
		public long vkImageView() {
			long current = this.textureHandle.getAsLong();
			return current == 0L ? super.vkImageView() : current;
		}
	}
}