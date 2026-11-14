package dev.onescreen.client.mixin;

import com.mojang.blaze3d.vulkan.VulkanGpuSurface;
import dev.onescreen.client.media.VulkanQueueSynchronization;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VulkanGpuSurface.class)
public abstract class VulkanGpuSurfaceMixin {
	@Redirect(
		method = "present",
		at = @At(
			value = "INVOKE",
			target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I"
		),
		remap = false
	)
	private static int onescreen$present(final VkQueue queue, final VkPresentInfoKHR presentInfo) {
		return VulkanQueueSynchronization.present(queue, presentInfo);
	}
}