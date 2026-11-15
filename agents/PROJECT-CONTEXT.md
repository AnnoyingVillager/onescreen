# OneScreen 项目上下文

## 产品定义

OneScreen 是 Minecraft Java Edition 26.2 / Fabric 的薄型屏幕模组。玩家把 1x1x1/16 的屏幕画框贴在任意方块的可点击表面（包括楼梯、栅栏等不完整方块），画框不依赖承托方块，原贴附方块被破坏后仍可悬空，通过右键编辑器或 `/screen` 命令配置媒体 URL、尺寸、距离、九宫格位置、比例模式、旋转、循环、直播追帧和画框可见性。

项目由生成式 AI 主导实现，人类用户提供需求、运行环境与实机反馈。对外文档必须明确这一点，不能把它描述为 Mojang、Fabric 或 WATERMeDIA 官方项目。

## 当前技术基线

| 项目 | 当前值 |
|---|---|
| Minecraft | `26.2` |
| Java | `25` |
| Fabric Loader | 构建/测试 `0.18.6`，最低声明 `0.18.0` |
| Fabric API | `0.152.0+26.2` |
| Gradle | `9.8.2` |
| Loom | `1.19.4` |
| OneScreen 源码版本 | `0.2.1`（未发布） |
| WATERMeDIA | `3.0.0` |
| WATERMeDIA Binaries | `3.0.0` |

`fabric.mod.json` 当前声明 WATERMeDIA `>=3.0.0.0 <3.1.0`，运行时再由 `WaterMediaVersion` 做特殊排序。

## WATERMeDIA 版本规则

WATERMeDIA 的版本语义与普通四段版本号不同：

- `3.0.0` 是正式基线。
- `3.0.0.x` 是低于 `3.0.0` 的测试版本。
- `3.0.x`（x > 0）是高于所有 `3.0.0.x` 的正式版本。
- 例：`3.0.0.23 < 3.0.0 < 3.0.1 < 3.0.1.1 < 3.0.2`。
- `3.1.0` 不在当前支持范围内。

当前正式目标是 `3.0.x`。`3.0.0.x` 兼容代码可以保留为 best-effort，但不应成为新的验收基线，也不要继续新增对旧包布局的直接依赖。

## 源码集与关键代码地图

### `src/main`

服务端和公共逻辑：

- `block/ScreenBlock.java`：六向薄画框、点击交互、blockstate；放置只读取点击面，不保存或校验承托方块。
- `block/ScreenBlockEntity.java`：屏幕权威配置、持久化、更新包、reload revision。
- `screen/ScreenSettings.java`：URL、尺寸、距离、对齐、适配、旋转、循环等纯数据。
- `screen/ScreenGeometry.java`、`ScreenFitGeometry.java`：六向平面几何、旋转与 UV 映射。
- `command/ScreenCommands.java`：`/screen` 命令树与权限。
- `editor/*Payload.java`：打开编辑器、保存设置、刷新等 C2S/S2C 数据。
- `config/OneScreenConfig.java`：服务端渲染/声音最大距离。

### `src/client`

客户端渲染和媒体：

- `render/ScreenRenderer.java`：26.2 render state + submit 渲染。
- `render/ScreenRenderState.java`：每帧只读快照。
- `editor/ScreenEditorScreen.java`：右键编辑界面、滑块、播放控制、旋转和隐藏画框。
- `media/ScreenMediaManager.java`：按屏幕 key 管理会话、容量、距离与生命周期。
- `media/MediaSession.java`：单个屏幕的 MRL、播放器、纹理、状态机和直播刷新。
- `media/MediaTextureUploader.java`：CPU 帧到 `NativeImage`/`DynamicTexture`。
- `media/ExternalMediaTexture.java`：把 WATERMeDIA 的 OpenGL/Vulkan 纹理句柄接入 Minecraft 纹理抽象。
- `media/WaterMediaCompat.java`：所有 WATERMeDIA 版本和反射边界。
- `media/WaterMediaVersion.java`：特殊版本排序与支持判断。
- `media/VideoLod.java`：按归一化距离选择 LOD，带迟滞。
- `media/WaterMediaVulkanContext.java`：把 Minecraft Vulkan device 适配为 WATERMeDIA `VKContext`。
- `media/VulkanQueueSynchronization.java`：按 `VkQueue` 句柄共享队列锁。
- `mixin/Vulkan*Mixin.java`：仅用于把 Minecraft 提交、等待和呈现纳入同一把队列锁。

## 屏幕数据与同步

服务端是 URL 和屏幕设置的唯一权威来源。修改流程：

1. `/screen` 或编辑器 payload 在逻辑服务端校验权限、坐标、方块目标、距离和 URL。
2. 修改 `ScreenBlockEntity` 中的 `ScreenSettings`。
3. `setChanged()` 和 block update 触发同步。
4. 客户端收到方块实体数据后，`ScreenMediaManager` 比较 URL 和 `loadRevision`。
5. 旧会话先关闭，再异步创建新会话。
6. 渲染器只读取 `MediaSnapshot`，不直接操作播放器。

URL 只允许 HTTP/HTTPS，长度上限为 4096。不要信任客户端 URL，不要引入同步网络请求到渲染线程。

## 渲染原理

- 屏幕方块使用六向 `FACING`，但没有 `canSurvive` 或承托邻居自毁逻辑；任意可点击方块表面均可放置，承托方块被破坏后画框继续悬空。
- 模型与动态媒体四边形位于同一薄面。
- `ScreenGeometry` 统一计算平面内旋转、媒体面基向量、九宫格偏移和声音中心。
- `ScreenFitMode` 支持 `fit/stretch/tile/fill/center`；平铺模式 UV 可超过 1。
- 无媒体且 debug 关闭时屏幕区域透明；debug 开启显示画框材质。
- 隐藏画框只隐藏静态模型，保留方块、媒体面和点击区域。
- 26.2 当前目标没有可覆盖的旧版 `getRenderBoundingBox`；距离剔除由 `ScreenRenderer.shouldRender` 和 renderer 契约负责，超大屏边缘仍需实机验证。

## 0.2 媒体路径

### 统一入口

所有版本差异经过 `WaterMediaCompat`。业务代码不能直接 import WATERMeDIA 玩家类。

### CPU 回退

```text
WATERMeDIA AWTEngine
  -> BufferedImage / packed ARGB
  -> NativeImage
  -> DynamicTexture.upload()
  -> Minecraft screen quad
```

CPU 路径的约束：

- 水印回调线程只递增帧序号，不直接调用 Minecraft。
- 渲染线程在帧序号变化时复制并上传。
- 纹理创建、更新、释放都在客户端线程。
- 尺寸变化要重建纹理。
- 该路径适用于 GPU 引擎创建失败或不支持的图形后端。

### OpenGL GPU 纹理路径

- 使用 WATERMeDIA `GLEngine`。
- `ExternalMediaTexture` 包装动态 GL texture id。
- 每帧绘制时读取当前句柄，避免把旧句柄固定死。

### Vulkan GPU 纹理路径

```text
Minecraft VulkanDevice
  -> WaterMediaVulkanContext (VKContext proxy)
  -> WATERMeDIA VKEngine
  -> VkImageView
  -> WaterMediaVulkanView
  -> Minecraft texture binding
```

必须保持的契约：

- WATERMeDIA `texture()` 会把图像屏障转换到 `FRAGMENT_SHADER / SHADER_READ`。
- WATERMeDIA 默认假定消费者也在同一 graphics queue 上采样。
- `queueLock()` 必须同时锁住 WATERMeDIA `vkQueueSubmit`、Minecraft `vkQueueSubmit2KHR`、`vkQueueWaitIdle` 和 `vkQueuePresentKHR`。
- 2026-11-15 的崩溃就是因为错误使用独立 compute/transfer queue 且没有跨队列 semaphore，最终触发 `VK_ERROR_DEVICE_LOST`。
- 三个 Mixin 仅为满足这个同步契约，不得删除或替换成私有队列方案。

## 分辨率与 LOD

- OneScreen 不再固定 max width/height 为 1920x1080。
- `LOD=MAX` 时允许 WATERMeDIA 使用源尺寸。
- `VideoLod` 根据屏幕渲染距离比例选择 `MAX/CLOSE/NEAR/FAR/FAR_AWAY`。
- LOD 变化只在跨档位且越过 0.05 迟滞带时应用。
- 4K/8K 是否硬解由 WATERMeDIA、FFmpeg、编解码器、GPU 和驱动决定，不由 OneScreen 承诺。
- 测试片 `4k.mp4` 是 AV1 Main 10-bit HLG；当前 WATERMeDIA 选择软解但仍可走 GPU 纹理上传。

## 编辑器与播放控制

- 进度条：VOD 可拖动；直播只显示当前片段近似进度。
- 自动直播刷新：默认关闭；暂停恢复或配置更新后可重建直播播放器。
- 停止：释放媒体纹理，屏幕不再显示残留帧。
- 暂停：手动暂停覆盖状态在关闭编辑器后继续生效。
- 刷新：服务端递增 `loadRevision`，客户端重建会话。
- WATERMeDIA 会按 URL 缓存 MRL。`WaterMediaCompat.mrl()` 必须对 `ERROR/BLOCKED/EXPIRED/FORGOTTEN` 资源调用 `reloadIfRegenerable()`；否则“服务先离线、后恢复”时，右键刷新仍会复用同一个失败对象。
- 旋转：`-180..180`，画面、九宫格和声音中心共用几何。
- 隐藏画框：默认关闭，不影响点击区域。

## 实机环境

用户提供的本地媒体：

- `http://localhost/1080p.mp4`：H.264 Main、1920x1080、30fps、BT.709。
- `http://localhost/4k.mp4`：AV1 Main 10-bit、3840x2160、60fps、BT.2020/HLG。

已验证 Vulkan 环境：

- Vulkan `1.4.329`
- NVIDIA GeForce RTX 5070 Ti
- 驱动 `596.49`
- 1080p：`hardwareAccel=true`、`format=GPU`
- 4K：`hardwareAccel=false`、`format=GPU`
- 修复后 4K 连续播放超过 1 分钟无设备丢失

## 已知风险与陷阱

- 不要用 `format=GPU` 推断硬解，也不要因为 `hardwareAccel=false` 判断播放器失效。
- 不要把 WATERMeDIA 指向 compute/transfer queue；这会破坏同队列可见性契约。
- 不要在没有共享锁的情况下让 Minecraft 和 WATERMeDIA 同时提交 graphics queue。
- 不要在 `ExternalMediaTexture` 中销毁 WATERMeDIA 拥有的 view。
- 不要让 `MediaSession` 在渲染线程创建播放器或执行网络请求。
- 不要把远程 URL 不加校验同步给客户端。
- 不要逐帧记录尺寸、LOD 或错误日志。
- 不要假设新建 `MediaSession` 就能绕过 WATERMeDIA 的 URL 级 MRL 缓存；失败资源必须通过兼容层刷新。
- 不要依赖已删除的 Agent 文档路径；长期 Agent 文档只在 `agents/`。

## 人类文档索引

人类文档按语言分为 `docs/zh-cn/` 与 `docs/en/`，两边保持相同文件名。修改任一功能时，应同步对应语言的同主题文档。

- `README.md` / `README.en.md`：安装、前置、公开版本、使用和已知限制。
- `docs/README.md`：文档语言入口。
- `docs/zh-cn/README.md` / `docs/en/README.md`：各自语言的完整文档索引。
- `COMMAND-REFERENCE.md`：命令语法、权限和值域。
- `CONFIGURATION.md`：`config/onescreen.json` 字段和生效方式。
- `SCOPE.md`：功能范围、非目标和验收门槛。
- `ARCHITECTURE.md`：source set、核心对象、网络和媒体数据流。
- `RENDERING.md`：六向几何、UV、画面适配、debug 和距离剔除。
- `MEDIA-AUDIO.md`：CPU/OpenGL/Vulkan 路径、LOD、播放控制和音频。
- `NETWORK-SYNC.md`：方块实体同步、编辑器 payload、命令和 URL 校验。
- `API-VERSIONS.md`：Minecraft、Fabric、WATERMeDIA 和反射边界。
- `HIGH-RESOLUTION-MEDIA.md`：高分辨率与硬件解码的技术评估和历史背景。
- `TESTING.md`：完整测试矩阵、已通过项和待办项。
- `DECISIONS.md`：ADR 和已取代决策。
- `RISKS.md`：当前风险和缓解措施。
- `GLOSSARY.md`：渲染、媒体、Vulkan 和网络术语。
- `DEVELOPMENT.md`：人类开发环境和基础验证流程。
- `PUBLISHING.md`：版本、构建、GitHub Actions 和发布门槛。
- `CHANGELOG.md` / `CHANGELOG.en.md`：中英文发布历史。