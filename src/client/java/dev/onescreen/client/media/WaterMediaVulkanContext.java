package dev.onescreen.client.media;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanQueue;
import dev.onescreen.OneScreen;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.lwjgl.vulkan.VkPhysicalDeviceMultiDrawFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceSynchronization2Features;
import org.lwjgl.vulkan.VkPhysicalDeviceDynamicRenderingFeatures;

/**
 * Adapts Minecraft's Vulkan device to the WATERMeDIA VKContext interface without linking against
 * either version-specific WATERMeDIA API package.
 */
final class WaterMediaVulkanContext implements InvocationHandler, AutoCloseable {
	private static final @Nullable Field GPU_BACKEND_FIELD = gpuBackendField();
	private final VulkanDevice device;
	private final VulkanQueue queue;
	private final Object queueLock;
	private final VkPhysicalDeviceMemoryProperties memoryProperties;
	private final @Nullable VkPhysicalDeviceFeatures2 features;
	private final List<Pointer> featureAllocations;
	private final List<String> instanceExtensions;
	private final List<String> deviceExtensions;
	private final int[] queueCounts;
	private final @Nullable Constructor<?> deviceInfoConstructor;
	private boolean closed;

	private WaterMediaVulkanContext(final VulkanDevice device, final Class<?> contextType) {
		this.device = device;
		this.queue = device.graphicsQueue();
		this.queueLock = VulkanQueueSynchronization.lockFor(this.queue.vkQueue());
		this.memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
		VK12.vkGetPhysicalDeviceMemoryProperties(device.vkDevice().getPhysicalDevice(), this.memoryProperties);
		FeatureSet featureSet = buildFeatures(device);
		this.features = featureSet.root();
		this.featureAllocations = featureSet.allocations();
		this.instanceExtensions = List.copyOf(device.instance().getEnabledExtensions());
		this.deviceExtensions = enabledDeviceExtensions(device);
		this.queueCounts = queueCounts(device);
		this.deviceInfoConstructor = deviceInfoConstructor(contextType);
	}

	static @Nullable VulkanDevice currentDevice() {
		GpuDevice device = RenderSystem.getDevice();
		if (!device.getDeviceInfo().backendName().equalsIgnoreCase("Vulkan") || GPU_BACKEND_FIELD == null) {
			return null;
		}
		try {
			Object backend = GPU_BACKEND_FIELD.get(device);
			return backend instanceof VulkanDevice vulkanDevice ? vulkanDevice : null;
		} catch (IllegalAccessException exception) {
			OneScreen.LOGGER.warn("Cannot access Minecraft's Vulkan backend", exception);
			return null;
		}
	}

	static Bridge create(final VulkanDevice device, final Class<?> contextType) {
		WaterMediaVulkanContext handler = new WaterMediaVulkanContext(device, contextType);
		Object proxy = Proxy.newProxyInstance(
			contextType.getClassLoader(),
			new Class<?>[] {contextType},
			handler
		);
		return new Bridge(proxy, handler);
	}

	@Override
	public Object invoke(final Object proxy, final Method method, final Object[] arguments) {
		if (this.closed) {
			throw new IllegalStateException("WATERMeDIA Vulkan context is closed");
		}
		return switch (method.getName()) {
			case "vkInstance" -> this.device.instance().vkInstance();
			case "physicalDevice" -> this.device.vkDevice().getPhysicalDevice();
			case "vkDevice" -> this.device.vkDevice();
			case "queue" -> this.queue.vkQueue();
			case "queueFamily" -> this.queue.queueFamilyIndex();
			case "queueLock" -> this.queueLock;
			case "memoryProperties" -> this.memoryProperties;
			case "hostImportSupported" -> this.deviceExtensions.contains("VK_EXT_external_memory_host");
			case "minImportedHostPointerAlignment" -> 0L;
			case "ycbcrSampler" -> false;
			case "deviceInfo" -> this.newDeviceInfo();
			case "retire" -> {
				scheduleRetire((Runnable)arguments[0]);
				yield null;
			}
			case "toString" -> "OneScreen WATERMeDIA Vulkan context";
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> proxy == Objects.requireNonNull(arguments)[0];
			default -> throw new IllegalStateException("Unsupported VKContext method: " + method);
		};
	}

	private @Nullable Object newDeviceInfo() {
		if (this.deviceInfoConstructor == null || this.features == null) {
			return null;
		}
		try {
			return this.deviceInfoConstructor.newInstance(
				this.features,
				this.instanceExtensions,
				this.deviceExtensions,
				this.queueCounts.clone()
			);
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
			throw new IllegalStateException("Cannot create WATERMeDIA Vulkan device info", exception);
		}
	}


	private static FeatureSet buildFeatures(final VulkanDevice device) {
		VkPhysicalDeviceFeatures2 core = VkPhysicalDeviceFeatures2.calloc().sType$Default();
		VkPhysicalDeviceVulkan11Features vulkan11 = VkPhysicalDeviceVulkan11Features.calloc().sType$Default();
		VkPhysicalDeviceVulkan12Features vulkan12 = VkPhysicalDeviceVulkan12Features.calloc().sType$Default();
		VkPhysicalDeviceSynchronization2Features synchronization2 = VkPhysicalDeviceSynchronization2Features.calloc().sType$Default();
		VkPhysicalDeviceDynamicRenderingFeatures dynamicRendering = VkPhysicalDeviceDynamicRenderingFeatures.calloc().sType$Default();
		VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT vertexDivisor =
			VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT.calloc().sType$Default();

		core.features()
			.multiDrawIndirect(true)
			.fillModeNonSolid(true)
			.samplerAnisotropy(true);
		vulkan11.shaderDrawParameters(true);
		vulkan12.timelineSemaphore(true).hostQueryReset(true);
		synchronization2.synchronization2(true);
		dynamicRendering.dynamicRendering(true);
		vertexDivisor.vertexAttributeInstanceRateDivisor(true);

		core.pNext(vulkan11.address());
		vulkan11.pNext(vulkan12.address());
		vulkan12.pNext(synchronization2.address());
		synchronization2.pNext(dynamicRendering.address());
		dynamicRendering.pNext(vertexDivisor.address());

		List<Pointer> allocations = new ArrayList<>(7);
		allocations.add(core);
		allocations.add(vulkan11);
		allocations.add(vulkan12);
		allocations.add(synchronization2);
		allocations.add(dynamicRendering);
		allocations.add(vertexDivisor);

		if (device.getDeviceInfo().underlyingExtensions().contains("VK_EXT_multi_draw (D)")) {
			VkPhysicalDeviceMultiDrawFeaturesEXT multiDraw = VkPhysicalDeviceMultiDrawFeaturesEXT.calloc().sType$Default();
			multiDraw.multiDraw(true);
			vertexDivisor.pNext(multiDraw.address());
			allocations.add(multiDraw);
		}
		return new FeatureSet(core, List.copyOf(allocations));
	}

	private static List<String> enabledDeviceExtensions(final VulkanDevice device) {
		List<String> extensions = new ArrayList<>();
		for (String extension : device.getDeviceInfo().underlyingExtensions()) {
			if (extension.endsWith(" (D)")) {
				extensions.add(extension.substring(0, extension.length() - 4));
			}
		}
		return List.copyOf(extensions);
	}

	private static int[] queueCounts(final VulkanDevice device) {
		Map<VulkanQueue, Boolean> queues = new IdentityHashMap<>();
		queues.put(device.graphicsQueue(), Boolean.TRUE);
		queues.put(device.computeQueue(), Boolean.TRUE);
		queues.put(device.transferQueue(), Boolean.TRUE);
		Map<Integer, Integer> countsByFamily = new HashMap<>();
		int maxFamily = 0;
		for (VulkanQueue queue : queues.keySet()) {
			int family = queue.queueFamilyIndex();
			maxFamily = Math.max(maxFamily, family);
			countsByFamily.merge(family, 1, Integer::sum);
		}
		int[] counts = new int[maxFamily + 1];
		for (Map.Entry<Integer, Integer> entry : countsByFamily.entrySet()) {
			counts[entry.getKey()] = entry.getValue();
		}
		return counts;
	}

	private static @Nullable Constructor<?> deviceInfoConstructor(final Class<?> contextType) {
		try {
			Method method = contextType.getMethod("deviceInfo");
			Class<?> type = method.getReturnType();
			return type.getDeclaredConstructor(
				VkPhysicalDeviceFeatures2.class,
				List.class,
				List.class,
				int[].class
			);
		} catch (NoSuchMethodException | RuntimeException ignored) {
			return null;
		}
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			this.memoryProperties.free();
			for (int index = this.featureAllocations.size() - 1; index >= 0; index--) {
				MemoryUtil.nmemFree(this.featureAllocations.get(index).address());
			}
		}
	}

	private static @Nullable Field gpuBackendField() {
		try {
			Field field = GpuDevice.class.getDeclaredField("backend");
			return field.trySetAccessible() ? field : null;
		} catch (NoSuchFieldException | RuntimeException exception) {
			OneScreen.LOGGER.warn("Minecraft GpuDevice backend is unavailable for WATERMeDIA Vulkan integration", exception);
			return null;
		}
	}

	record Bridge(Object proxy, WaterMediaVulkanContext handler) implements AutoCloseable {
		@Override
		public void close() {
			this.handler.close();
		}
	}

	private record FeatureSet(VkPhysicalDeviceFeatures2 root, List<Pointer> allocations) {
	}

	private static void scheduleRetire(final Runnable action) {
		Runnable enqueue = () -> {
			try {
				RenderSystem.queueFencedTask(action);
			} catch (RuntimeException exception) {
				OneScreen.LOGGER.warn("Failed to queue WATERMeDIA Vulkan resource retirement", exception);
			}
		};
		if (RenderSystem.isOnRenderThread()) {
			enqueue.run();
		} else {
			Minecraft.getInstance().execute(enqueue);
		}
	}
}