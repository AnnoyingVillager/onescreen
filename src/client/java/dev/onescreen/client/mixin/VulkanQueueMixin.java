package dev.onescreen.client.mixin;

import com.mojang.blaze3d.vulkan.VulkanQueue;
import dev.onescreen.client.media.VulkanQueueSynchronization;
import org.lwjgl.vulkan.VkQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VulkanQueue.class)
public abstract class VulkanQueueMixin {
	@Redirect(
		method = "waitIdle",
		at = @At(
			value = "INVOKE",
			target = "Lorg/lwjgl/vulkan/VK12;vkQueueWaitIdle(Lorg/lwjgl/vulkan/VkQueue;)I"
		),
		remap = false
	)
	private static int onescreen$waitIdle(final VkQueue queue) {
		return VulkanQueueSynchronization.waitIdle(queue);
	}
}