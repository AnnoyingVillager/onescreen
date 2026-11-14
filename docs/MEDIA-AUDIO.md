# 媒体、GPU 纹理与音频

## 1. 依赖与目标

OneScreen 0.2 使用：

- WATERMeDIA `3.0.0` 及后续 `3.0.x` 正式版本
- 与 WATERMeDIA 发布线匹配的 WATERMeDIA Binaries
- 客户端必须安装两者；专用服务器不加载媒体原生库

高分辨率、编解码器和硬件能力的评估见 [高分辨率与硬件解码评估](HIGH-RESOLUTION-MEDIA.md)。

不使用：

- WATERMeDIA 2.x 的 `ImageAPI`、`ImageCache`、`ImageRenderer`
- 旧 VLC `VideoPlayer`
- Minecraft 私有 `SoundEngine`、`Channel`、`ChannelAccess`
- 业务代码中的 WATERMeDIA 直接玩家类 import

## 2. 统一入口

所有 WATERMeDIA 版本和 API 差异都集中在：

- `src/client/java/dev/onescreen/client/media/WaterMediaCompat.java`
- `src/client/java/dev/onescreen/client/media/WaterMediaVersion.java`

业务层只与包装对象交互：

- `MediaResource`
- `MediaEngine`
- `MediaPlayerHandle`
- `MediaSnapshot`

这样可以兼容不同 WATERMeDIA 包布局，同时避免在渲染或命令代码中散落反射逻辑。

## 3. 每条屏幕会话的结构

`ScreenMediaManager` 按维度与方块坐标管理 `MediaSession`。每个会话拥有：

- 一个 MRL。
- 一个视频或图片引擎。
- 一个播放器。
- 一个 Minecraft 纹理标识。
- 当前设置与距离参数。
- 播放状态、错误和帧序号。

单个客户端最多同时保留 8 个活动会话；超出后延迟重试，避免无限创建解码器。

## 4. 图片路径

图片使用 WATERMeDIA Headless 引擎输出首帧：

```text
HeadlessGFXEngine
  -> ByteBuffer / PixelFormat
  -> NativeImage
  -> DynamicTexture
  -> 屏幕四边形
```

注意：

- `format()` 在第一帧到达前可以为 `null`，这是合法状态。
- 只有 `uploadCount` 或帧序号变化时才复制和上传。
- 图片尺寸变化时必须重建 `DynamicTexture`。
- 纹理创建、更新和释放都在客户端线程。

## 5. 视频路径总览

`MediaSession.createVideoPlayer()` 先尝试 GPU 引擎；GPU 初始化或播放器创建失败时回退到 AWT CPU 引擎。

```text
优先：WATERMeDIA GLEngine / VKEngine
  -> ExternalMediaTexture
  -> GPU texture view

回退：WATERMeDIA AWTEngine
  -> BufferedImage packed ARGB
  -> NativeImage
  -> DynamicTexture
```

`format=GPU` 表示纹理路径，不代表解码器一定使用硬件加速。`hardwareAccel=true/false` 来自播放器运行状态，必须单独记录。

## 6. OpenGL GPU 纹理

OpenGL 路径：

- 使用 WATERMeDIA `GLEngine`。
- `ExternalMediaTexture.WaterMediaGlTexture` 重写 `glId()`，在绘制时读取当前 GL texture id。
- `WaterMediaGlView` 同样动态读取 id，避免 WATERMeDIA 轮换纹理后 Minecraft 仍绑定旧句柄。
- 该包装器不拥有 WATERMeDIA 的纹理对象，只管理 Minecraft 侧的小型 view/facade。

## 7. Vulkan GPU 纹理

### 对象关系

```text
Minecraft VulkanDevice
  -> WaterMediaVulkanContext
  -> WATERMeDIA VKContext proxy
  -> WATERMeDIA VKEngine
  -> VkImageView
  -> WaterMediaVulkanView
  -> Minecraft texture binding
```

### 队列契约

WATERMeDIA `VKEngine` 的纹理发布假定：

1. 媒体写入和 Minecraft 采样使用同一个 graphics queue。
2. 双方所有需要外部同步的队列操作由同一把 `queueLock()` 串行化。
3. 图像屏障把结果转换到 `FRAGMENT_SHADER / SHADER_READ` 后，消费者在后续 graphics queue 提交中采样。

当前实现：

- `WaterMediaVulkanContext.queue()` 返回 Minecraft `graphicsQueue()`。
- `VulkanQueueSynchronization` 按 `VkQueue` 原生地址提供统一锁。
- `VulkanQueueSubmissionMixin` 锁住 Minecraft `vkQueueSubmit2KHR`。
- `VulkanQueueMixin` 锁住 `vkQueueWaitIdle`。
- `VulkanGpuSurfaceMixin` 锁住 `vkQueuePresentKHR`。

### 历史故障

旧实现把 WATERMeDIA 指向独立 compute/transfer queue，却没有跨队列 semaphore，播放 4K 约 2 秒后出现：

```text
Retention wait failed (VkResult -4)
VK_ERROR_DEVICE_LOST
```

该问题不是 HTTPS、片源或纹理尺寸导致的，而是 Vulkan 队列与可见性契约错误。不要恢复独立队列方案。

### 所有权

- WATERMeDIA 拥有 `VkImage`、`VkDeviceMemory`、`VkImageView` 和退休队列。
- OneScreen 只包装当前 view 句柄，不在 `ExternalMediaTexture.close()` 中销毁 WATERMeDIA 的 view。
- WATERMeDIA 的资源退休回调通过 Minecraft render-thread fenced task 延迟执行。

## 8. 分辨率与距离 LOD

0.2 不再设置固定的 `1920x1080` 视频上限：

- 近距离 `LOD=MAX` 时允许源尺寸，例如 3840x2160。
- 远距离由 `VideoLod` 选择 `MAX/CLOSE/NEAR/FAR/FAR_AWAY`。
- 档位阈值使用归一化距离比例，并带 `0.05` 迟滞，避免边界抖动。
- 只有档位实际变化时才调用 WATERMeDIA 的 `lod(...)`。
- 最终使用硬解还是软解由 WATERMeDIA、FFmpeg、GPU、驱动和片源决定。

测试片 `4k.mp4` 为 AV1 Main 10-bit HLG。在当前 RTX 5070 Ti / FFmpeg / WATERMeDIA 组合中，日志显示 `hardwareAccel=false`，但 `format=GPU`，说明它仍通过 GPU 纹理路径上传和显示。

## 9. CPU 纹理回退

AWT 路径使用 packed ARGB/BGRA `BufferedImage`。`MediaTextureUploader` 使用 native-order `IntBuffer` 批量写入 `NativeImage`，避免逐像素 `setPixel`。

约束：

- 水印回调线程只递增帧序号。
- 渲染线程只处理最新帧。
- 每帧最多调用一次 `DynamicTexture.upload()`。
- 不允许从渲染线程创建播放器或发起网络请求。
- CPU 路径仍涉及 GPU->CPU 回读、Java 像素复制和 CPU->GPU 上传，因此不应对所有 4K/8K 设备承诺性能。

## 10. 播放状态与控制

主要状态：

```text
RESOLVING -> LOADING -> PLAYING
                    \-> PAUSED -> PLAYING
                    \-> ENDED
任意状态 -> ERROR / IDLE
```

编辑器控制：

- 播放：在停止或结束后重新创建/恢复播放。
- 暂停：手动暂停覆盖状态在关闭编辑器后仍保持。
- 停止：释放媒体纹理并清除屏幕画面；播放器不再继续显示帧。
- 刷新：服务端递增 `loadRevision`，客户端关闭旧会话并重建同一 URL。
- 循环：通过播放器 `repeat` 设置即时更新。
- 进度条：VOD 可拖动；直播只读。
- 自动直播刷新：默认关闭，只对直播流生效。

## 11. VOD 进度与直播追帧

VOD：

- 使用 `time()` / `duration()`。
- 拖动时调用 `seekQuick()` 预览，松开时用 `seek()` 精确落点。
- 已知时长媒体不会因为直播刷新设置而重开。

直播：

- 通过 `live()`（兼容旧层 `liveSource()`）识别。
- 进度条不可拖动，只显示 HLS 当前片段的近似进度。
- HLS 片段时长优先从 WATERMeDIA 公开播放列表工具读取。
- 若工具不可用，使用 `HlsPlaylistResolver` 直接解析播放列表。
- 自动追帧在暂停恢复或配置更新后重连，带 1 秒防抖，避免连续配置导致重连风暴。

## 12. 音频与距离

音频通过 WATERMeDIA 的 JS 引擎播放，不依赖当前 OpenGL/Vulkan context。

输出音量：

```text
finalVolume = clamp(screenVolume * distanceGain, 0, 1)
distanceGain = 1 at <= minDistance
distanceGain = 0 at >= maxDistance
```

- 每个客户端独立计算距离音量。
- 一个玩家静音不影响其他玩家。
- 视频超出声音距离时仍可继续显示，声音归零。
- 服务端配置可提高渲染和声音上限，但实际仍受客户端区块加载和视距限制。

## 13. 生命周期

以下事件必须关闭会话：

- BlockEntity 卸载。
- 玩家离开服务器。
- 客户端关闭。
- 维度切换。
- URL 改变。
- 方块移除。
- GPU 引擎初始化失败后切换到 CPU 引擎。
- 编辑器中停止播放。
- 手动刷新替换旧会话。

关闭顺序：

1. 从纹理管理器注销 Minecraft 纹理。
2. 释放播放器。
3. 释放 CPU/GPU 引擎。
4. 让 WATERMeDIA 延迟回收 GPU view。
5. 从活动会话表移除。

## 14. 常见排错

| 日志/现象 | 优先检查 |
|---|---|
| `format=GPU` 但 `hardwareAccel=false` | 片源/编解码器/FFmpeg 是否选择软解，不代表纹理路径失败 |
| `VK_ERROR_DEVICE_LOST` | graphics queue、队列锁、三个 Vulkan Mixin 是否正确注入 |
| 停止后黑屏或重启失败 | 媒体纹理是否注销、播放器和引擎是否按顺序释放 |
| 只有第一帧或画面不更新 | 帧序号、引擎 `texture()` 句柄是否变化、纹理 view 是否动态读取 |
| HLS 进度不可用 | 播放列表解析失败时安全降级，不应影响播放器本身 |
| GPU 路径创建失败 | 应自动回退 AWT；检查 `MediaSession.createVideoPlayer()` 日志 |

更多测试步骤见 [测试计划](TESTING.md)。