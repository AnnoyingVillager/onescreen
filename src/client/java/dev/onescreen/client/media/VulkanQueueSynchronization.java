package dev.onescreen.client.media;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo2;

/**
 * Serializes every Minecraft and WATERMeDIA operation that requires external synchronization on a
 * Vulkan queue. Minecraft and WATERMeDIA submit from different threads, so sharing the graphics
 * queue is only valid when both sides use the same per-queue monitor.
 */
public final class VulkanQueueSynchronization {
	private static final ConcurrentMap<Long, Object> QUEUE_LOCKS = new ConcurrentHashMap<>();

	private VulkanQueueSynchronization() {
	}

	public static Object lockFor(final VkQueue queue) {
		if (queue == null) {
			throw new IllegalArgumentException("Vulkan queue must not be null");
		}
		return lockFor(queue.address());
	}

	public static Object lockFor(final long queue) {
		if (queue == 0L) {
			throw new IllegalArgumentException("Vulkan queue handle must not be zero");
		}
		return QUEUE_LOCKS.computeIfAbsent(queue, ignored -> new Object());
	}

	public static int submit2(
		final VkQueue queue,
		final VkSubmitInfo2.Buffer submissions,
		final long fence
	) {
		synchronized (lockFor(queue)) {
			return KHRSynchronization2.vkQueueSubmit2KHR(queue, submissions, fence);
		}
	}

	public static int waitIdle(final VkQueue queue) {
		synchronized (lockFor(queue)) {
			return VK12.vkQueueWaitIdle(queue);
		}
	}

	public static int present(final VkQueue queue, final VkPresentInfoKHR presentInfo) {
		synchronized (lockFor(queue)) {
			return KHRSwapchain.vkQueuePresentKHR(queue, presentInfo);
		}
	}
}