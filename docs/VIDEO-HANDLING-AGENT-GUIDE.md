# OneScreen 视频处理修改交接指南

- 目标读者：另一个负责修改 OneScreen 的 AI 编程会话
- 项目：OneScreen 仓库根目录
- 目标版本：Minecraft `26.2` / Fabric / WATERMeDIA `3.0.0.23`（编译基线）/ `3.0.1`（运行兼容）
- 编写日期：2026-11-14
- 关联审计：[高分辨率视频与硬件解码评估](HIGH-RESOLUTION-MEDIA.md)
- 目的：在保留 OpenGL/Vulkan 后端兼容性的前提下，改善视频分辨率策略、CPU 拷贝开销和可测试性

## 先给接手智能体的硬性结论

当前改动应当继续使用 `AWTEngine -> BufferedImage -> NativeImage -> DynamicTexture` 路径，先完成“可控分辨率、按距离 LOD、低开销像素拷贝和验证”。

本次不要做以下事情：

- 不要把视频播放器改成原始 OpenGL `GL11` 纹理。
- 不要直接实现 Vulkan `VKContext` 桥接。
- 不要使用 Mixin、access widener 或 WATERMeDIA 私有字段。
- 不要通过 `System.setProperty` 或用户路径猜测 FFmpeg 参数。
- 不要宣称 `MediaPlayer` 已明确使用硬解；WATERMeDIA 3.0.x 没有通过当前公开 `MediaPlayer` 暴露稳定的“本次实际使用硬解”查询。
- 不要无条件把 `1280x720` 改成 4K/8K。
- 不要在每一帧、每一个 tick 或每次渲染重复记录媒体尺寸日志。
- 不要在渲染线程发起 URL 请求、MRL 解析或 `MediaPlayer` 创建。

硬解由 WATERMeDIA 3.0.x 自动管理，配置默认开启：

```text
<Minecraft 实例>/config/watermedia.toml
[codecs.ffmpeg]
hardwareAccel = true
```

当前 AWT 路径可以使用 WATERMeDIA 的硬件解码候选，但最终仍需 GPU 到 CPU 回读、Java 像素复制和纹理上传，不是零拷贝播放。需要真正的 4K/8K GPU 直通时另开高风险里程碑，不要混入本次修改。

## 当前代码状态

主要文件：

- `src/client/java/dev/onescreen/client/media/MediaSession.java`
- `src/client/java/dev/onescreen/client/media/MediaTextureUploader.java`
- `src/client/java/dev/onescreen/client/media/ScreenMediaManager.java`
- `src/client/java/dev/onescreen/client/media/MediaSnapshot.java`
- `src/client/java/dev/onescreen/client/render/ScreenRenderer.java`

当前行为：

1. VIDEO 使用 `PlayerAPI.awtEngine(callback)`。
2. 音频使用 `PlayerAPI.jsEngine()`。
3. 创建播放器后调用 `player.scale(1280, 720)`，然后 `start()`。
4. `onFrame` 回调只递增 `AtomicLong`，不直接触达 Minecraft 渲染。
5. 渲染线程发现帧序号变化后，从 AWT 的 `BufferedImage` 复制到 `NativeImage`。
6. `DynamicTexture.upload()` 每次上传一帧。
7. `ScreenMediaManager` 每客户端最多保留 8 个活动会话，超限延时。

这套路径已经通过 OpenGL 3.3 和 Vulkan 1.4.329 的基本视频验收。

## 本次修改的完成定义

完成后应具备：

- 明确的视频输出质量档位。
- 默认档位仍为 `1280x720`。
- 可选 `1920x1080` 测试档位，不依赖 GUI。
- 按观看距离选择 WATERMeDIA `LodLevel`，并有阈值迟滞。
- 仅在质量或 LOD 实际变化时修改播放器，不在每帧重复调用。
- 视频像素复制从逐像素、逐次边界检查的 `setPixel` 路径改为批量 `IntBuffer` 写入。
- 每帧严格只调用一次 `DynamicTexture.upload()`。
- 仍然遵守客户端主线程纹理规则。
- 编译、OpenGL、Vulkan、硬解和软解回退测试均有记录。
- 若 1080p 未达到验收门槛，默认发布仍保持 720p，并在风险文档中记录结果。

## 阶段 0：基线检查

在 OneScreen 仓库根目录执行：

```powershell
Get-Content -Raw AGENTS.md
Get-Content -Raw docs\VIDEO-HANDLING-AGENT-GUIDE.md
Get-Content -Raw docs\HIGH-RESOLUTION-MEDIA.md
Get-Content -Raw docs\MEDIA-AUDIO.md
.\gradlew.bat compileJava
```

如果仓库有 Git：

```powershell
git status --short
```

先确认构建基线。不要在一个本身无法编译的工作树上叠加视频改动。

## 阶段 1：建立输出质量档位

建议新增客户端内部类型，不加入网络协议或复杂 GUI：

```java
package dev.onescreen.client.media;

enum VideoOutputPreset {
    SAFE(1280, 720),
    HD(1920, 1080);

    private final int maxWidth;
    private final int maxHeight;

    VideoOutputPreset(int maxWidth, int maxHeight) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    int maxWidth() {
        return this.maxWidth;
    }

    int maxHeight() {
        return this.maxHeight;
    }
}
```

第一轮固定使用 `SAFE`。如果希望方便测试，可以增加客户端静态常量或本地调试开关，但不要新增 `/screen` 网络字段、服务端配置或 GUI：

```java
private static final VideoOutputPreset DEFAULT_PRESET = VideoOutputPreset.SAFE;
```

在 `MediaSession` 中替换：

```java
private static final int VIDEO_MAX_WIDTH = 1280;
private static final int VIDEO_MAX_HEIGHT = 720;
```

建议改为：

```java
private VideoOutputPreset outputPreset = DEFAULT_PRESET;
private int appliedScaleWidth = -1;
private int appliedScaleHeight = -1;
private @Nullable MediaPlayer.LodLevel appliedLod;
```

创建视频播放器时必须在 `start()` 之前应用尺寸：

```java
if (this.videoOutput) {
    this.applyOutputSize(this.player);
}
this.applyPlayerSettings();
this.player.start();
```

实现：

```java
private void applyOutputSize(MediaPlayer player) {
    int width = this.outputPreset.maxWidth();
    int height = this.outputPreset.maxHeight();
    if (width == this.appliedScaleWidth && height == this.appliedScaleHeight) {
        return;
    }

    player.scale(width, height);
    this.appliedScaleWidth = width;
    this.appliedScaleHeight = height;
}
```

`scale` 是每轴上限，不会放大源视频，也不是在解码前限制源分辨率。它降低的是 WATERMeDIA 输出到 AWT/纹理路径的尺寸。

不要把源分辨率当作输出纹理尺寸。格式错误的竖屏、超宽屏和正方形视频都应交给 `scale` 处理，渲染器继续按媒体宽高比适配屏幕区域。

## 阶段 2：加入距离 LOD

### 数据流修改

当前 `tick` 只有距离音量增益，信息不足以选择画面 LOD。建议把“原始距离”单独传入：

```java
void tick(
    ScreenSettings currentSettings,
    double currentDistanceGain,
    double distance,
    boolean paused
)
```

`ScreenMediaManager.tick` 每帧计算一次距离，并将其同时用于音频增益和视频策略：

```java
double distance = distanceToMediaCenter(screen);
double gain = audioDistanceGain(distance, settings);
session.tick(settings, gain, distance, paused);
```

不要让音频距离上限影响视频 LOD。视频仍受屏幕的 `renderDistance` 控制。

### 按归一化距离选档

伪代码：

```java
private static MediaPlayer.LodLevel selectLod(double distance, double renderDistance) {
    double ratio = distance / Math.max(1.0D, renderDistance);
    if (ratio < 0.25D) {
        return MediaPlayer.LodLevel.MAX;
    }
    if (ratio < 0.50D) {
        return MediaPlayer.LodLevel.CLOSE;
    }
    if (ratio < 0.75D) {
        return MediaPlayer.LodLevel.NEAR;
    }
    return MediaPlayer.LodLevel.FAR;
}
```

推荐映射：

| 归一化距离 | LOD | 输出比例 |
|---|---|---:|
| `< 25%` | `MAX` | 100% |
| `25%–50%` | `CLOSE` | 75% |
| `50%–75%` | `NEAR` | 50% |
| `>= 75%` | `FAR` | 25% |

不要使用 `FAR_AWAY` 作为默认档位；屏幕仍可见时过度降采样很容易明显发糊。确实位于渲染距离边缘的屏幕，优先依靠渲染剔除。

### 阈值迟滞

只做单向阈值会在玩家站在边界时反复重建输出表面。建议给向外切换增加 4 格余量，这里的伪代码必须结合真实距离实现：

```java
private @Nullable MediaPlayer.LodLevel appliedLod;

private MediaPlayer.LodLevel nextLod(
    MediaPlayer.LodLevel current,
    double distance,
    double renderDistance
) {
    double ratio = distance / Math.max(1.0D, renderDistance);
    double hysteresis = 4.0D / Math.max(8.0D, renderDistance);

    if (current == MediaPlayer.LodLevel.MAX) {
        return ratio > 0.25D + hysteresis ? MediaPlayer.LodLevel.CLOSE : current;
    }
    if (current == MediaPlayer.LodLevel.CLOSE) {
        if (ratio < 0.25D - hysteresis) return MediaPlayer.LodLevel.MAX;
        if (ratio > 0.50D + hysteresis) return MediaPlayer.LodLevel.NEAR;
        return current;
    }
    if (current == MediaPlayer.LodLevel.NEAR) {
        if (ratio < 0.50D - hysteresis) return MediaPlayer.LodLevel.CLOSE;
        if (ratio > 0.75D + hysteresis) return MediaPlayer.LodLevel.FAR;
        return current;
    }
    return ratio < 0.75D - hysteresis ? MediaPlayer.LodLevel.NEAR : current;
}
```

应用逻辑：

```java
private void applyVideoPolicy(MediaPlayer player, double distance) {
    this.applyOutputSize(player);

    MediaPlayer.LodLevel next = this.nextLod(
        this.appliedLod == null ? MediaPlayer.LodLevel.MAX : this.appliedLod,
        distance,
        this.settings.renderDistance()
    );

    if (next != this.appliedLod) {
        player.lod(next);
        this.appliedLod = next;
        OneScreen.LOGGER.info(
            "Video LOD at {} changed to {} (distance={}, renderDistance={})",
            this.key.pos(), next, distance, this.settings.renderDistance()
        );
    }
}
```

只记录 LOD 变化，不按帧记录。

## 阶段 3：优化 AWT 到 NativeImage 的像素复制

### 当前问题

`MediaTextureUploader.copyArgbPixels` 现在对每个像素执行 `target.setPixel(x, y, pixel)`。26.2 的 `setPixel` 会逐像素：

- 检查格式。
- 检查边界。
- 检查内存是否已分配。
- 计算地址。
- 调用 `MemoryUtil.memPutInt`。
- 调用 `ARGB.toABGR`。

720p 每帧约 921,600 次 Java 调用，在 60 FPS 时需要连续处理大量边界检查。

### 推荐实现

26.2 的 `NativeImage` 提供公开 `getPixelBytes()`，`DynamicTexture.getPixels()` 也公开其 `NativeImage`。使用 `IntBuffer` 批量写入：

```java
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import net.minecraft.util.ARGB;

private static void copyArgbPixels(
    BufferedImage source,
    int width,
    int height,
    NativeImage target
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
        throw new IllegalArgumentException("Video pixel buffer is too small");
    }

    ByteBuffer destination = target.getPixelBytes().order(ByteOrder.nativeOrder());
    IntBuffer destinationPixels = destination.asIntBuffer();
    for (int index = 0; index < required; index++) {
        destinationPixels.put(index, ARGB.toABGR(pixels[index]));
    }
}
```

注意：

- `NativeImage` 内部 RGBA 字节在 little-endian 平台由 ABGR 整数表示，因此保留 `ARGB.toABGR`。
- `ByteOrder.nativeOrder()` 必须显式设置。
- 不要每帧重新分配 `IntBuffer`；这里只创建轻量视图。
- `DynamicTexture.upload()` 仍每帧一次。
- 不要同时保留原来的 `setPixel` 循环。

### 可选的静态图片/BGRA 批量路径

`copyPixels` 目前也逐像素调用 `setPixel`。可以在同一改动中优化 8-bit `BGRA` 和 `RGBA`：

- `RGBA` 源字节顺序与目标 RGBA 内存一致，可直接整数复制。
- `BGRA` 源整数的数值布局等同 ARGB，仍需逐像素 `ARGB.toABGR`。
- `RGB`、`GRAY` 保留当前的慢速回退，因为不是视频主路径。
- 所有整数 Buffer 视图都必须显式使用 native order。
- 容量检查必须使用 `long`，避免大尺寸乘法溢出。

如果时间有限，优先只优化视频的 `copyArgbPixels`；不要为了顺带优化图片而扩大风险。

## 阶段 4：尺寸、LOD 和资源生命周期

### 尺寸变更

`scale()` 和 `LodLevel` 都可能让 `AWTEngine.width()/height()` 改变。现有逻辑在尺寸变化时：

1. 释放旧 `DynamicTexture`。
2. 用新尺寸创建 `NativeImage`。
3. 注册新纹理。

保持这个行为。不要在旧 `NativeImage` 上强行写不同尺寸；`MediaTextureUploader.updateArgb` 会拒绝尺寸不匹配，这是正确的保护。

### 切换限制

- `scale` 以当前档位为唯一依据；相同尺寸不得重复调用。
- `lod` 仅在档位变化时调用。
- 不在暂停、静音、音量变化时重建媒体播放器。
- URL 或 `loadRevision` 变化仍会关闭并重建媒体会话；区块卸载、移除、维度和断线清理也继续保留。质量档位与 LOD 变化不得触发会话重建。
- 会话关闭时仍释放播放器、纹理和动态图像。

### 回调线程

WATERMeDIA 的 `AWTEngine.onFrame` 在解码/上传线程运行。当前回调只递增 `AtomicLong` 是合适的最小设计：

```java
this.videoFrameSequence::incrementAndGet
```

继续遵守：

- 回调中不调用 `Minecraft.getInstance().execute(...)`。
- 回调中不创建 `DynamicTexture`。
- 回调中不访问 Minecraft RenderSystem。
- 渲染线程只读取“最新帧序号”，允许跳帧，不排队播放每一帧。

如果后续观察到画面撕裂、尺寸切换异常或 `BufferedImage` 并发读写问题，再单独实施双缓冲；不要在本阶段同时重写帧锁模型。

## 阶段 5：硬解和软解验证

### 不修改 WATERMeDIA 配置

OneScreen 不直接设置 FFmpeg 或硬件设备参数。测试时修改外部配置：

```toml
[codecs.ffmpeg]
hardwareAccel = true
```

先跑硬解默认路径，再临时设为 `false` 验证软件回退。

### 日志观察

WATERMeDIA 成功建立硬解时会记录类似：

```text
Hardware decoder initialized: ...
FFMediaPlayer started - video: true (hw: true...)
```

硬件路径异常时可能出现：

```text
GPU frame transfer failed ... falling back to software decoding
GPU frame transfer too slow ... falling back to software decoding
Software decoding fallback active
```

当前公开 `MediaPlayer` 没有可靠的“本次实际使用哪个硬解后端”字段。不要通过反射、私有字段或猜测日志字符串把它变成产品状态。第一版只记录测试结果。

### 硬解不等于高分辨率

- `hardwareAccel=true` 允许使用硬解。
- 当前 AWT 路径仍可能把帧从 GPU 回读到 CPU。
- `scale` 可能发生在回读之后。
- 因此 4K 片源的解码速度正常，也可能因为回读、缩放和纹理上传而卡顿。

## 测试矩阵

至少完成：

### 构建

```powershell
.\gradlew.bat compileJava
.\gradlew.bat build
```

### 功能

- 720p H.264 MP4 正常播放。
- 720p 视频有声音。
- 1080p H.264 MP4 可选测试。
- 至少一个 HEVC 或 AV1 样本；记录成功或失败原因。
- 暂停、继续、静音、循环、视频结束最后一帧不回归。
- URL 切换一次、区块卸载一次、维度切换一次、断线一次后无残留声音。

### 图形后端

- OpenGL 3.3：播放、尺寸、LOD、上传正确。
- Vulkan 1.4.329：播放、尺寸、LOD、上传正确。
- 两后端均确认无 `Rendersystem called from wrong thread`。

### 硬解/软解

- `hardwareAccel=true`：记录 WATERMeDIA 日志和是否有回退。
- `hardwareAccel=false`：视频仍播放。
- 硬解失败不崩溃，错误或回退可见。

### LOD

- 从 5 格拉远到 80 格，LOD 单向逐级变化。
- 在阈值附近来回移动，不出现每 tick 重建纹理。
- 视距变化后尺寸仍与视频宽高比匹配。
- 暂不要求手机端、4K 或 8K 成为发布门槛。

### 性能

比较改动前后的：

- 单屏 720p60 CPU 占用。
- 单屏 1080p30/60 CPU 占用。
- 8 个活动会话的进程内存。
- `NativeImage`/动态纹理数量是否稳定。
- 30 分钟内存曲线是否持续增长。

不要只提交“看起来更流畅”；至少保留一种可重复的观察数据。

### AI 不能替代的人工实机验收

负责编码的智能体必须明确区分：

- `compileJava`、`build` 和日志抓取是自动可以完成的。
- OpenGL/Vulkan 画面正确、声音同步、远景清晰度、玩家主观流畅度必须由人实际进入世界确认。
- 如果当前会话无法操作 Minecraft 客户端，不得把实机项目标记为通过；应给出测试步骤、测试片源和待人工勾选的清单。

人工验收时至少截图或记录：

- 屏幕在 5、25、50、75、95 格距离的画面。
- `hardwareAccel=true` 时 WATERMeDIA 硬解或回退日志。
- 单屏与 8 屏时的 F3 帧率。
- 暂停、继续与声音同步。
- 切到另一图形后端后的结果。

## 推荐提交拆分

如果仓库允许提交，拆成最多三个提交：

1. `Add bounded video output quality and LOD policy`
2. `Speed up ARGB video texture updates`
3. `Document high-resolution and hardware decoding limits`

每个提交都应独立编译。若第 3 步只是文档，可以先合入；代码提交不能把未验证的 1080p 默认值标为完成。

## 评审清单

代码审查时逐项确认：

- [ ] 默认输出仍为 1280x720。
- [ ] `player.scale(...)` 在 `player.start()` 前调用。
- [ ] 相同尺寸不重复调用 `scale`。
- [ ] LOD 只在档位变化时调用。
- [ ] LOD 有阈值迟滞。
- [ ] 音频距离不再冒充视频距离。
- [ ] `onFrame` 仍不调用 Minecraft 渲染 API。
- [ ] 纹理创建、上传、释放仍在客户端线程。
- [ ] `NativeImage` 尺寸变化会重建 `DynamicTexture`。
- [ ] ARGB 到 NativeImage 使用批量 `IntBuffer`，容量检查使用 `long` 或安全整数。
- [ ] 无逐帧日志。
- [ ] OpenGL 与 Vulkan 都通过。
- [ ] 硬解和软解两种配置都通过。
- [ ] 没有 Mixin、access widener、私有字段或原始 GL/VK 句柄。
- [ ] 文档没有把“可尝试硬解”误写成“保证硬解”。

## 给另一会话的直接指令

可以将下面这段作为该会话的起始任务：

```text
请只修改 OneScreen 仓库中的视频处理，不要重构整个模组。

先阅读：
- `AGENTS.md`
- `docs/VIDEO-HANDLING-AGENT-GUIDE.md`
- `docs/HIGH-RESOLUTION-MEDIA.md`
- `docs/MEDIA-AUDIO.md`

本次目标是：
1. 保留 WATERMeDIA AWTEngine、Minecraft NativeImage 和 DynamicTexture 架构；
2. 把硬编码 1280x720 改为默认 SAFE=1280x720、可选 HD=1920x1080 的输出档位；
3. 把真实世界距离传入 MediaSession，按 renderDistance 归一化并应用 LodLevel，带阈值迟滞，只在变化时调用；
4. 优化 MediaTextureUploader.copyArgbPixels，使用 getPixelBytes()、nativeOrder IntBuffer 和 ARGB.toABGR 批量写入，避免每像素 setPixel；
5. 保持每帧一次 DynamicTexture.upload()，保持主线程纹理规则和现有生命周期清理；
6. 不接入原始 OpenGL/Vulkan 纹理，不使用 Mixin/access widener/私有字段，不无条件把默认改成 1080p/4K。

完成标准：
- .\gradlew.bat compileJava 和 .\gradlew.bat build 通过；
- OpenGL 和 Vulkan 至少各播放一次 MP4；
- hardwareAccel=true 和 false 各验证一次；
- 720p 默认档位、1080p 测试档位、LOD 阈值迟滞、暂停、静音、循环、URL 切换、区块卸载均验证；
- 记录实际修改文件、测试结果、未完成项和任何硬解回退日志。
```

## 后续高风险工作：GPU 原生路径

只有当以下需求明确成立时才另开任务：

- 必须播放 4K60 或以上。
- 允许牺牲 OpenGL/Vulkan 双后端统一性。
- 能测试 NVIDIA、AMD、Intel 和至少一种集成显卡。
- 接受更复杂的纹理生命周期、颜色空间、队列同步和崩溃排查。

届时优先评估：

1. OpenGL 专用 `GLEngine` + 26.2 自定义提交。
2. Vulkan `VKContext` + GPU 解码帧直通。
3. AWT 兼容回退。

这三条路径不能为了追求“硬解标签”同时塞进首版。
