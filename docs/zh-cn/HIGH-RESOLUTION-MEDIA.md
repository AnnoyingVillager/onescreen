# 高分辨率视频与硬件解码评估

- 评估日期：2026-11-14
- 适用范围：WATERFrAMES `1.21.1-fabric` 参考实现、OneScreen 的 Minecraft `26.2` Fabric 目标
- 结论等级：代码审计，不是显卡实验室认证
> **2026-11-15 实施更新（优先于下文早期评估）**
>
> OneScreen 0.2 已移除固定 `1920x1080` 视频尺寸上限，并实现 OpenGL/Vulkan 的 WATERMeDIA GPU 纹理直通；CPU AWT 路径仍保留为回退。Vulkan 使用 Minecraft graphics queue，并由 `VulkanQueueSynchronization` 与三个 Mixin 串行化 WATERMeDIA 和 Minecraft 的提交、等待与呈现。
>
> 沙箱实测：1080p H.264 输出 `1920x1080` 且 `hardwareAccel=true`；4K AV1 Main 10-bit HLG 输出 `3840x2160`，WATERMeDIA 选择软解但 GPU 纹理路径稳定，修复前约 2 秒发生的 `VK_ERROR_DEVICE_LOST` 已不再复现，连续播放超过 1 分钟。
>
> 下文仍保留为技术背景与风险清单。其中“保留 1280x720 默认上限”等旧建议已被 0.2 当前实现取代；4K/8K 仍按设备与片源能力动态决定，不构成所有硬件上的性能承诺。

## 结论

WATERFrAMES 本身没有配置一个“最高只支持 1080p”的固定像素上限。旧 Fabric 分支把 WATERMeDIA 2 返回的媒体宽度和高度直接用于 OpenGL 纹理；WATERMeDIA 3 则提供显式的上传尺寸缩放（`scale`）和距离细节级别（`LodLevel`）。

硬件解码可以实现，但不能仅凭“码流是 4K/8K”或“WATERMeDIA 配置开启”判断最终显示一定流畅。必须同时满足：

1. GPU 和驱动支持该编码、Profile、色深及分辨率。
2. 操作系统和媒体后端能创建对应的硬件设备。
3. 帧不用经过代价过高的 GPU 到 CPU 回读。
4. 纹理上传、Minecraft 渲染和音频线程仍有足够预算。
5. 最终显示尺寸和观看距离值得使用该像素数量。

### 0.1.x 时期建议与后续结论

2026-11-14 的早期评估曾在 CPU 回读路径占主导时建议：

- 保留 `1280x720` 作为安全输出上限。
- 优先播放 720p 或 1080p 片源。
- 将 1080p 作为逐设备测试档位。
- 只有完成 GPU 纹理直通后才考虑把 4K 作为正式支持目标。

这些建议用于解释 0.1.x 的风险来源，已被 0.2 当前实现取代：

- OneScreen 已取消固定 1280x720/1920x1080 上限。
- `LOD=MAX` 使用 WATERMeDIA 实际输出尺寸。
- OpenGL/Vulkan GPU 纹理路径已接入，CPU AWT 路径仅作回退。
- 4K/8K 的硬解与流畅度仍由片源、WATERMeDIA、FFmpeg、GPU 和驱动决定，不能承诺所有设备都能稳定播放。
- 性能结论必须区分 `format=GPU/CPU` 与 `hardwareAccel=true/false`。

## WATERFrAMES 1.21.1 Fabric 的实际行为

审计对象为 `waterframes 2.1.22` 使用的 WATERMeDIA `2.1.34` 源码。

### 没有应用层像素上限

`client/display/Display.java` 的 `width()`、`height()` 和 `texture()` 直接取自：

- 图片：`ImageRenderer.width/height/texture()`。
- 视频：`VideoPlayer.width/height/texture()`。

`VideoPlayer.getBufferFormat(sourceWidth, sourceHeight)` 请求原尺寸 RGBA 缓冲区：

```java
this.width = sourceWidth;
this.height = sourceHeight;
this.size = sourceWidth * sourceHeight * 4;
return new BufferFormat(Chroma.RGBA, sourceWidth, sourceHeight);
```

每次新帧到达后，`VideoPlayer.preRender()` 调用 `RenderAPI.uploadBuffer(...)`，使用 `glTexImage2D` 或 `glTexSubImage2D` 把整帧上传到一张 OpenGL 纹理。该路径没有降低分辨率、距离 LOD 或总像素预算。

因此，4K/8K 是否成功主要受以下因素约束：

- 片源和硬件解码器的能力。
- RGBA 帧缓冲区占用。
- OpenGL 驱动的 `GL_MAX_TEXTURE_SIZE`。
- 每帧 RGBA 上传带宽。
- Minecraft/其他模组占用的显存和 CPU。

WATERFrAMES 的 `maxWidth`、`maxHeight` 配置限制的是屏幕在世界中的方块尺寸，不限制视频像素尺寸。

### Windows 默认启用硬解候选

WATERMeDIA 2.1.34 的 Windows LibVLC 参数包含：

```text
--avcodec-hw d3d11va,dxva2,any
--vout direct3d11,glwin32,gl,any
--avcodec-fast
--avcodec-skip-idct=4
```

所以 1.21.1 Fabric 分支在 Windows 上会要求 LibVLC 优先尝试 D3D11VA、DXVA2，再允许其他硬件后端。Linux 参数文件没有相同的 `--avcodec-hw` 项，不能据源码认定其默认同样开启硬解。

“使用硬解”不等于“全程驻留 GPU”：

- LibVLC 仍通过 callback video surface 向 Java 提供 RGBA 缓冲区。
- WATERFrAMES 随后把该缓冲区上传为 OpenGL 纹理。
- 这条链路可能发生 GPU 解码帧回读、像素格式转换和 CPU 到 GPU 二次上传。

因此旧分支是可行的硬解参考，但不是高分辨率的最佳架构。

### 4K/8K 的单帧最低字节数

RGBA/BGRA 每像素按 4 字节计算：

| 分辨率 | 单帧字节 | 单帧 MiB | 60 FPS 的原始 RGBA 带宽 |
|---|---:|---:|---:|
| 1280x720 | 3,686,400 | 3.52 | 约 211 MiB/s |
| 1920x1080 | 8,294,400 | 7.91 | 约 475 MiB/s |
| 3840x2160 | 33,177,600 | 31.64 | 约 1.85 GiB/s |
| 7680x4320 | 132,710,400 | 126.56 | 约 7.42 GiB/s |

这些是最低单份像素数据量，不包含：

- 解码器内部 YUV 帧。
- Java AWT/`BufferedImage` 副本。
- Minecraft `NativeImage` 副本。
- GPU 纹理。
- 多个同时播放屏幕。

只要路径中多一次完整 CPU 复制，实际内存带宽就会接近或超过表中数值。4K60、8K30/60 的瓶颈往往在上传和复制，而不是单纯解码。

## WATERMeDIA 3 的实际能力

### 硬件解码默认开启

本地审计的 WATERMeDIA `3.0.1` 中，`WaterMediaConfig.codecs.ffmpeg.hardwareAccel` 默认值为 `true`。

配置路径：

```text
<Minecraft 实例>/config/watermedia.toml
```

相关开关：

```toml
[codecs.ffmpeg]
hardwareAccel = true
```

这不是 OneScreen 自己的配置。用户或整合包可以关闭它，以规避部分驱动的卡顿、黑屏或回退异常。

### 自动选择硬件后端

`FFDecoder` 在播放器提供 Vulkan 设备时先尝试该设备；否则依次尝试：

1. CUDA（NVIDIA）
2. QSV（Intel）
3. AMF（AMD）
4. OHCODEC（平台专用硬件）
5. D3D11VA（Windows DirectX 11）
6. D3D12VA（Windows DirectX 12）
7. VideoToolbox（macOS）
8. VA-API / VDPAU / DRM（Linux）
9. MediaCodec（Android）
10. Vulkan（通用后备）

只有实际设备、FFmpeg 构建、编码器和驱动都接受时才会成功。日志中可观察：

```text
Hardware decoder initialized: ...
FFMediaPlayer started - video: true (hw: true...)
```

失败后会尝试软件解码。已建立的硬件路径若连续 30 帧传输失败，或在 120 帧窗口内 GPU 到 CPU 的平均传输时间超过帧预算的 75%，也会切换为软件解码。

### `scale` 与 LOD

`MediaPlayer.scale(width, height)` 为每个轴设置上传尺寸上限，不会放大片源：

```java
player.scale(1280, 720);
```

`LodLevel` 在 `scale` 之后再做百分比缩减：

| LOD | 保留尺寸 |
|---|---:|
| `MAX` | 100% |
| `CLOSE` | 75% |
| `NEAR` | 50% |
| `FAR` | 25% |
| `FAR_AWAY` | 10% |

缩放发生在上传结果尺寸，而不是阻止 FFmpeg 按源分辨率解码。对普通硬件路径，FFmpeg 仍可能先解码并回读较大的帧，再通过 `sws_scale` 缩小并上传。`scale` 主要降低纹理显存和上传量，不能单独保证 4K/8K 解码便宜。

尺寸变化会重新分配输出表面或纹理。不要每一帧根据轻微距离变化来回切换 LOD；应在距离跨过明确阈值时更新，并做迟滞处理。

### Vulkan 零拷贝条件

只有以下链路才可能把硬件解码帧留在 GPU：

1. 使用 `VKEngine`。
2. 向 WATERMeDIA 提供兼容的 `VKContext`。
3. GPU、驱动、FFmpeg 和编码格式支持 Vulkan Video，或者支持对应的 twin-device/外部内存路径。
4. libplacebo 具备所需特性时，继续在 GPU 上完成格式、色彩和色调映射转换。

这条路径的接入面包括 Vulkan 设备特性链、队列族、队列锁、外部内存或 twin device、图像生命周期和 `retire` 回调。它不属于新人首版应该同时实现的功能。

## OneScreen 当前 26.2 路径

当前 `MediaSession` 使用：

```java
AWTEngine engine = PlayerAPI.awtEngine(this.videoFrameSequence::incrementAndGet);
this.player = PlayerAPI.player(current, 0, () -> engine, PlayerAPI::jsEngine);
this.player.scale(VIDEO_MAX_WIDTH, VIDEO_MAX_HEIGHT);
```

其中：

```java
private static final int VIDEO_MAX_WIDTH = 1280;
private static final int VIDEO_MAX_HEIGHT = 720;
```

帧到达后的路径为：

```text
FFmpeg 解码
  -> 硬件或软件解码
  -> BGRA/ARGB 输出
  -> AWTEngine BufferedImage
  -> Minecraft NativeImage
  -> DynamicTexture.upload()
  -> 26.2 OpenGL/Vulkan RenderType
```

这条方案的价值是图形后端无关，并且已经在 OpenGL/Vulkan 上工作；代价是固定的软件回读和额外复制。它可以使用 WATERMeDIA 的硬件解码器，但最终显示不是零拷贝。

当前 720p 上限很适合作为安全档位。若直接移除上限：

- 4K 时每屏至少多份约 32 MiB 帧数据。
- 8 个活动会话可能迅速占用数百 MiB 的非堆/堆内存和显存。
- 4K60 的单向原始拷贝接近 GiB/s 量级。
- 低端核显、集成显卡和同时运行光影的客户端更容易卡顿。

## 历史方案（0.2 未采用）

以下路线来自 2026-11-14 的 CPU 纹理评估，仅用于解释设计演进；当前实现以 GPU 纹理直通和动态 LOD 为准。


### A. 首版保持软件纹理桥接

保留 `1280x720`，打开 WATERMeDIA 默认硬解，并把支持范围写为：

```text
720p：默认且稳定
1080p：实验档位，需实机测试
4K：特定 GPU/编码验证后可用
8K：不承诺
```

这条路线不需要改当前媒体架构，只增加清晰限制和测试说明。

### B. 增加有限质量档位

如果需要比 720p 更清楚，先增加三档而没有复杂 GUI：

```text
safe  = 1280x720
hd    = 1920x1080
native = 源分辨率，但受像素预算保护
```

伪代码：

```java
enum VideoQuality {
    SAFE(1280, 720),
    HD(1920, 1080),
    NATIVE(Integer.MAX_VALUE, Integer.MAX_VALUE)
}

void applyQuality(MediaPlayer player, VideoQuality quality) {
    player.scale(quality.width(), quality.height());
}
```

`native` 不应真的无条件使用源分辨率。还需限制：

```java
long pixels = (long) player.sourceWidth() * player.sourceHeight();
long rgbaBytes = pixels * 4L;
if (rgbaBytes > MAX_BYTES_PER_SCREEN) {
    rejectOrFallbackToSafeQuality();
}
```

源尺寸在播放器打开后才可靠，因此最初的解码分配无法完全由这个检查预先阻止。真正需要“解码前限像素”时，必须扩展 WATERMeDIA 或修改 FFmpeg 参数，不能只依赖 `scale`。

距离 LOD 伪代码：

```java
LodLevel selectLod(double distance) {
    if (distance < 12) return LodLevel.MAX;
    if (distance < 32) return LodLevel.CLOSE;
    if (distance < 64) return LodLevel.NEAR;
    return LodLevel.FAR;
}
```

仅在跨越阈值时调用，并加入约 4 格迟滞，避免玩家站在边界时反复重建纹理。

### C. 高分辨率可选 GPU 原生路径

只有当 4K 是明确产品需求时才做：

- OpenGL：接入 `PlayerAPI.glEngine(...)`，把公开帧纹理交给受控的 26.2 自定义渲染路径。
- Vulkan：实现 `VKContext` 桥接和水印生命周期，验证设备与队列不会和原版渲染冲突。
- 保留 AWT 作为兼容回退。
- 由能力检测而不是 Minecraft 版本名称决定后端。
- 先在单屏 1080p、4K30、4K60 上做 30 分钟测试，再扩展多屏。

这是高级阶段工作。它会打破目前“只使用 Minecraft `DynamicTexture`”的简单结构，应作为独立里程碑，不要混入首版方块和命令开发。

## 测试矩阵

至少记录以下信息：

| 项目 | 建议值 |
|---|---|
| 图形后端 | OpenGL 与 Vulkan 分别测试 |
| GPU | NVIDIA、AMD、Intel 各至少一种，或记录已知不支持设备 |
| 编码 | H.264、HEVC、AV1；优先验证本地直链 MP4 |
| 分辨率/帧率 | 720p60、1080p60、4K30、4K60、8K30 实验 |
| 色深 | 8-bit SDR 必测，10-bit/HDR 作为后续项 |
| 网络 | 本地服务和远程 HTTP Range 服务各一组 |
| 活动屏幕 | 1、4、8 个 |
| 时长 | 连续 30 分钟 |

每组至少记录：

```text
backend=OPENGL|VULKAN
gpu=...
codec=H264|HEVC|AV1
source=3840x2160@30
output=1280x720
hardwareAccel=true|false
logsShowHardware=true|false
avgFps=...
worstFrameMs=...
processMemoryStart=...
processMemoryEnd=...
screenCount=...
visualResult=...
```

验收建议：

- 视频、声音和循环保持同步。
- 30 分钟内会话数、线程数、动态纹理数和进程内存没有持续无界增长。
- 8 个会话达到上限时不会继续创建解码器。
- 硬解失败会显示可用画面或明确错误，不崩溃。
- 切换 URL、离开区块、维度切换后无残留回读线程和声音。
- 4K 档位若未通过，产品只发布 720p/1080p 支持说明。

## 证据索引

本地审计源码：

- WATERMeDIA 2.1.34：`_watermedia-2-src/org/watermedia/api/player/videolan/VideoPlayer.java`
- WATERMeDIA 2.1.34 Windows 参数：`_watermedia-2-src/videolan/arguments.json`
- WATERMeDIA 3.0.1 配置：`_watermedia-sources/watermedia-3.0.1/org/watermedia/WaterMediaConfig.java`
- WATERMeDIA 3.0.1 解码器：`_watermedia-sources/watermedia-3.0.1/org/watermedia/api/codecs/decoders/FFDecoder.java`
- WATERMeDIA 3.0.1 播放器：`_watermedia-sources/watermedia-3.0.1/org/watermedia/api/player/FFMediaPlayer.java`
- WATERMeDIA 3.0.1 缩放 API：`_watermedia-sources/watermedia-3.0.1/org/watermedia/api/player/MediaPlayer.java`
- WATERMeDIA 3.0.1 AWT 输出：`_watermedia-sources/watermedia-3.0.1/org/watermedia/api/player/AWTEngine.java`
- 当前 OneScreen 视频路径：[`src/client/java/dev/onescreen/client/media/MediaSession.java`](../../src/client/java/dev/onescreen/client/media/MediaSession.java)
- 人类可读的 GPU/CPU 媒体实现：[MEDIA-AUDIO.md](MEDIA-AUDIO.md)

外部参考资料：

- [WATERMeDIA Modrinth](https://modrinth.com/mod/watermedia)
- [WATERMeDIA v3 源码](https://github.com/WaterMediaTeam/watermedia-v3)
- [FFmpeg hardware acceleration](https://trac.ffmpeg.org/wiki/HWAccelIntro)

## 最终可行性判断

OneScreen 0.2 已接入 OpenGL/Vulkan GPU 纹理，并移除固定 1080p 输出上限。1080p/4K 是否硬解仍由 WATERMeDIA、FFmpeg、编解码器、GPU 和驱动决定；4K/8K 高帧率不应在没有逐设备压力测试时承诺稳定性能。GPU 纹理直通解决的是显示和回读路径，不等于保证每个片源都使用硬件解码。
