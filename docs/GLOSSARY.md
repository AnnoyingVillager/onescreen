# 术语表

## Block / BlockState

`ScreenBlock` 是世界中的屏幕方块；blockstate 保存六向 `FACING` 和隐藏画框状态。屏幕本体是 1x1x1/16 的薄画框，但媒体显示面积可超过一个方块。

## BlockEntity

附着于屏幕方块的权威数据容器。`ScreenBlockEntity` 保存 `ScreenSettings` 和 `loadRevision`，不保存客户端播放器、纹理句柄或解码线程。

## BlockEntityRenderer

26.2 的客户端方块实体渲染器。`ScreenRenderer` 通过 render-state extraction 和 `submit` 提交媒体或占位四边形。

## RenderState

某一帧渲染所需数据的只读快照。`ScreenRenderState` 包含朝向、尺寸、旋转、适配模式、纹理和媒体状态，不直接持有 `MediaPlayer`。

## SubmitNodeCollector

26.2 中提交几何、文字和特效的渲染队列接口。渲染器不能退回旧版 `MultiBufferSource` 即时绘制模式。

## ScreenGeometry

计算六向屏幕平面基向量、平面内旋转、九宫格偏移和声音中心的纯几何类。媒体四边形与声音位置必须共用这一组计算。

## ScreenFitMode

画面适配算法：`fit`、`stretch`、`tile`、`fill`、`center`。`tile` 会产生大于 1 的 UV，并依赖纹理重复采样。

## ScreenMediaManager

客户端媒体会话协调器。按维度与方块坐标管理 `MediaSession`，负责 URL/revision 去重、并发上限、距离参数和生命周期清理。

## MediaSession

一个屏幕在单个客户端上的媒体加载与播放生命周期。负责 MRL、引擎、播放器、纹理、状态机、VOD seek、HLS 片段进度和直播追帧。

## MRL

WATERMeDIA 的媒体资源解析入口，把 URL 解析为可交给图片、视频或音频引擎处理的资源。

## MediaPlayerHandle

OneScreen 对 WATERMeDIA 播放器反射 API 的包装层句柄。业务代码只调用包装接口，不直接 import WATERMeDIA 玩家类。

## WaterMediaCompat

所有 WATERMeDIA API 版本差异、反射调用和引擎/播放器创建的唯一边界。

## WaterMediaVersion

实现 WATERMeDIA 特殊排序的版本判断类。测试版 `3.0.0.x` 低于正式版 `3.0.0`，而 `3.0.x`（x > 0）属于后续正式版本。

## CPU Texture Fallback

AWT 引擎输出 `BufferedImage`，经 `NativeImage` 和 `DynamicTexture` 上传到 Minecraft 的回退路径。它包含 GPU 到 CPU 回读和 Java 像素复制，适合作为兼容路径而不是性能承诺。

## ExternalMediaTexture

把 WATERMeDIA 提供的 OpenGL texture id 或 Vulkan `VkImageView` 包装成 Minecraft 纹理抽象的适配器。OneScreen 只拥有包装对象，不拥有 WATERMeDIA 的底层图像资源。

## GPU Texture Path

媒体帧由 WATERMeDIA 的 `GLEngine` 或 `VKEngine` 直接发布为 GPU 纹理，再由 Minecraft 渲染。`format=GPU` 只说明纹理路径，不代表 `hardwareAccel=true`。

## hardwareAccel

WATERMeDIA 播放器报告的硬件解码状态。它描述解码器选择，不描述纹理上传路径；必须与 `format=GPU/CPU` 分开记录。

## VideoLod

按屏幕距离比例选择 `MAX/CLOSE/NEAR/FAR/FAR_AWAY` 的细节级别算法。档位带 `0.05` 迟滞，只在真正跨档时调用 WATERMeDIA。

## Vulkan Queue Contract

WATERMeDIA 的 Vulkan 纹理发布与 Minecraft 采样必须使用同一 graphics queue，且提交、等待和呈现由同一把按 `VkQueue` 分配的锁串行化。

## VulkanQueueSynchronization

按原生 `VkQueue` 句柄提供共享锁的同步工具。三个窄范围 Mixin 把 Minecraft 的 `vkQueueSubmit2KHR`、`vkQueueWaitIdle` 和 `vkQueuePresentKHR` 纳入该锁。

## HLS / M3U8

HTTP Live Streaming 播放列表。VOD 可定位；直播流不能拖动进度条，只显示当前片段的目标时长进度。多频道 IPTV 当前只读取第一个源。

## ValueInput / ValueOutput

26.x 方块实体读写接口，替代旧的 `CompoundTag + HolderLookup.Provider` 存档方法。

## Update Tag

玩家进入区块时用于同步 BlockEntity 完整数据的快照。

## Update Packet

BlockEntity 数据变化时发送给跟踪该区块客户端的定向更新包。

## Payload

自定义网络消息的数据对象。OneScreen 用其打开编辑器、提交设置、刷新和广播 debug 状态；服务端始终重新执行权限与数值校验。

## Server Authoritative

服务端决定 URL 和屏幕配置，客户端不能自行成为持久化数据源。

## Session Leak

已不需要的媒体会话没有关闭，导致解码线程、内存、GPU 纹理或音频持续占用。区块卸载、断线、维度切换、URL 变化和停止播放都必须清理。