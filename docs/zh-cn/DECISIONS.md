# 架构决策记录

只记录会影响多个模块或以后难以撤销的决定。新决定追加在末尾。

## ADR-001：新建精简项目，不直接改造 WATERFrAMES

- 状态：Accepted
- 日期：2026-11-13

### 背景

WATERFrAMES 的 Fabric 分支包含 CreativeCore GUI、旧 WATERMeDIA 2 媒体接口、复杂方块和网络体系。目标只需要一块屏幕。

### 决定

新建最小 Fabric 26.2 项目，仅复用几何、数据模型和媒体生命周期思路。

### 影响

- 初期需要重写媒体接入。
- 不需要承担完整 WATERFrAMES 的历史结构。
- 更容易验证和维护。

## ADR-002：使用 Fabric split client source set

- 状态：Accepted
- 日期：2026-11-13

### 决定

服务端安全代码和方块数据放在 `src/main`，渲染与媒体放在 `src/client`。

### 影响

- 专用服务器不会加载客户端媒体类。
- 渲染器注册需要单独客户端初始化入口。
- AI 必须遵守 source-set 边界。

## ADR-003：第一版不使用 Mixin 或 access widener

- 状态：Superseded by ADR-022（仅允许 Vulkan 队列同步所需的窄范围 Mixin）
- 日期：2026-11-13

### 决定

使用公开事件、BlockEntityRenderer 和 WATERMeDIA 公共 API。

### 影响

- 避免内部字段变化。
- 如公开 API 不足，优先缩小功能而不是注入内部代码。

## ADR-004：复用 WATERMeDIA 3，不使用 Minecraft 私有音频引擎

- 状态：Accepted
- 日期：2026-11-13

### 决定

媒体解码、视频纹理和媒体音频由 WATERMeDIA 3 处理。第一版通过距离计算客户端音量。

### 影响

- 不实现复杂 Minecraft 声音衰减集成。
- 避免 `SoundEngine`、`Channel` 和 access widener。
- 后续如需原生衰减，可改为 WATERMeDIA 提供的稳定扩展点。

## ADR-005：URL 通过 BlockEntity 更新包同步

- 状态：Accepted
- 日期：2026-11-13

### 决定

第一版 URL 只由服务端命令修改，并通过 `getUpdateTag` / `getUpdatePacket` / `sendBlockUpdated` 同步。

### 影响

- 无需自定义客户端到服务器 payload。
- URL 仍是服务端权威数据。
- 后续加入 GUI 时再增加受验证的自定义 payload。

## ADR-006：单个方块作为锚点，视觉尺寸可超过方块

- 状态：Accepted
- 日期：2026-11-13

### 决定

屏幕的碰撞体和放置逻辑保持单方块，渲染几何按宽高扩展。

### 影响

- 第一版不需要多方块系统。
- 必须正确扩展渲染包围盒。
- 超宽屏幕可能与实体碰撞表现不一致，但属于可接受的首版折中。

## ADR-007：首版只允许 HTTP/HTTPS

- 状态：Accepted
- 日期：2026-11-13

### 决定

不接受 `file://`、`javascript:` 或其他协议。

### 影响

- 安全性更高。
- 本地文件支持推迟到专门处理 `water://local/` 路径边界的版本。

## ADR-008：将 Loom 固定为 1.19.4

- 状态：Accepted
- 日期：2026-11-13

### 背景

Fabric 官方 26.2 模板声明 `1.19-SNAPSHOT`。首次成功配置构建时，Gradle 实际解析为 `1.19.4`。

### 决定

项目暂时固定使用 `fabric-loom 1.19.4`，避免开发期间因 SNAPSHOT 漂移产生不可重复构建。

### 影响

- 当前构建可重复解析到同一 Loom 版本。
- 升级 Loom 时必须单独验证 Minecraft 26.2 项目配置和客户端/服务端启动。
- 发布前继续记录最终解析版本。

## ADR-009：26.2 使用全局 BlockEntity 渲染与扩展距离

- 状态：Accepted
- 日期：2026-11-13

### 背景

Minecraft 26.2 的 `BlockEntityRenderer` 不再提供项目计划中假设的 `getRenderBoundingBox` 方法。

### 决定

屏幕渲染器使用 `shouldRenderOffScreen() = true` 进入全局 BlockEntity 渲染集合，并在 `shouldRender()` 中按屏幕宽高扩展可视距离。几何通过 `SubmitNodeCollector.submitCustomGeometry` 提交。

### 影响

- 避免大屏幕因锚点所在区块被剔除而突然消失。
- 远距离屏幕仍会按扩展距离剔除。
- 不依赖 OpenGL 专用纹理绑定，可由 26.2 渲染后端抽象处理。

## ADR-010：M5 使用 WATERMeDIA HeadlessGFXEngine 与 Minecraft DynamicTexture

- 状态：Accepted
- 日期：2026-11-13

### 背景

WATERMeDIA 的 GPU `texture()` 句柄只在其具体图形后端内有意义。Minecraft 26.2 的 `RenderType` 又通过 `Identifier` 从 `TextureManager` 解析纹理。直接把句柄包装成 OpenGL 纹理既依赖后端，也违反项目的后端无关约束。

### 决定

M5 的静态图片使用 WATERMeDIA 公共 `HeadlessGFXEngine`：

- 通过 `PlayerAPI.headlessEngine()` 创建引擎。
- 读取公开的 `uploadCount()`、`format()`、`width()`、`height()` 和 `lastUpload()`。
- 将单平面 8-bit `BGRA`/`RGBA`/`RGB`/`GRAY` 帧复制到 Minecraft 自有 `NativeImage`。
- 通过 `DynamicTexture` 和 `TextureManager` 注册稳定动态 `Identifier`。
- 使用 clamp-to-edge、线性过滤，并在播放器、区块或连接关闭时释放纹理和会话。
- 断线回调不在客户端线程执行，因此清理必须先通过 `Minecraft.execute(...)` 调度到客户端线程。

### 影响

- M5 不使用 Mixin、access widener、`GlTexture` 包装器或 `GL11.glGetTexImage`。
- 静态图片只做一次 CPU 像素复制，PNG、JPEG、WebP 均已实机通过。
- 该方案不适合逐帧视频的持续 CPU 拷贝；M6 必须单独实现按帧更新或公开后端纹理桥接，并根据性能测试选择方案。
- 每个屏幕仍保持一个稳定纹理 `Identifier`，播放器与会话生命周期独立关闭。

## ADR-011：M6 使用软件视频引擎与 Java Sound 实现后端无关播放

- 状态：Superseded in part by ADR-023（AWT 仍作为 CPU 回退）
- 日期：2026-11-13

### 背景

M5 的 `HeadlessGFXEngine` 只公开第一平面，无法完整表示 MP4 解码常见的多平面 YUV 帧。直接把 WATERMeDIA 的 OpenGL/Vulkan GPU 句柄交给 Minecraft `RenderType` 也会重新引入后端耦合。音频侧若直接创建 `ALEngine`，则必须保证 Minecraft OpenAL context 在调用线程上当前有效。

### 决定

M6 对视频使用公开 `PlayerAPI.awtEngine(...)`：

- `SWEngine` 会拒绝 YUV/RGB 等布局，由 WATERMeDIA 解码器先转换到 packed BGRA/RGBA。
- 每次 `onFrame` 回调递增帧序号；客户端渲染线程仅在序号变化时把 packed ARGB 写入 Minecraft `NativeImage` 并调用 `DynamicTexture.upload()`。
- 视频上传尺寸上限为 1280x720，避免首版在大分辨率媒体上产生无上限 CPU 拷贝。

音频使用公开 `PlayerAPI.jsEngine()`：

- Java Sound 不依赖当前 OpenAL context，也不需要 Minecraft 私有 `SoundEngine` / `Channel`。
- 屏幕设置中的音量、静音、循环和距离增益直接应用于 `MediaPlayer`。
- 单机暂停菜单打开时暂停播放器，关闭时恢复；多人游戏中由服务器继续推进媒体时钟。

### 影响

- 同一套实现已在 OpenGL 3.3 和 Vulkan 1.4.329 下验证。
- 不引用 `GL11`、`GlTexture`、Vulkan 原生句柄、Mixin 或 access widener。
- 视频路径包含解码输出、ARGB 转换和 `NativeImage` 写入，CPU 成本高于 GPU 直通；M8 需要用多屏幕和 30 分钟运行测试确认上限。
- Java Sound 是客户端本地立体声输出，不提供 OpenAL 的位置声像；首版只实现需求中的距离音量衰减。

## ADR-012：六向薄画框必须让模型与媒体四边形同面

- 状态：Accepted
- 日期：2026-11-14

### 背景

画框改为 1×1×1/16 的六向薄方块后，`facing=up/down` 的 blockstate 旋转曾写反，导致方块模型位于方块另一面，而动态媒体四边形仍位于正确的朝向表面。视觉上表现为“屏幕方块与屏幕不在同侧”。

### 决定

- 基础模型薄片位于北侧 `z=15..16`。两个宽面使用中心透明、外圈不透明的 `screen_frame` 贴图，四个窄面保留原有侧边贴图。
- blockstate 使用 `facing=up: x=270`、`facing=down: x=90`，四个水平方向继续使用 `y=0/90/180/270`。
- 方块 `VoxelShape`、模型旋转和媒体四边形必须使用同一朝向表面。
- 媒体平面中心使用 `0.5 - facing.step × 0.4365`，即位于朝向表面的内侧；不能改成朝向正方向偏移。

### 影响

- `facing=up/down` 的薄片必须分别落在 `VoxelShape` 的底面/顶面；上下旋转写反时即使碰撞形状正确，贴图也会出现在同方块内相反的一侧。
- 无媒体且 debug 关闭时只保留实体边框，显示区域中心透明；debug 开启后由渲染器提交占位材质。
- 以后修改模型薄片方向或 blockstate 旋转时，必须同时回归六个朝向，不能只验证四个水平方向。

## ADR-013：屏幕画框使用模组专属创意栏

- 状态：Accepted
- 日期：2026-11-14

### 背景

屏幕画框最初通过 `CreativeModeTabs.BUILDING_BLOCKS` 注入原版建筑方块栏。它属于 OneScreen 的媒体设备，不应混入原版建筑方块分类。

### 决定

- 使用 `FabricCreativeModeTab.builder()` 创建并注册 `onescreen:screen` 独立创意栏。
- 栏标题使用 `itemGroup.onescreen`，图标与唯一展示物品均为屏幕画框。
- 移除对 `CreativeModeTabs.BUILDING_BLOCKS` 的输出注入，避免同一物品出现在原版建筑方块栏。

### 影响

- 屏幕画框只从 OneScreen 专属栏取得，后续模组物品也应优先加入该栏。
- 新增物品时必须同时确认专用栏与搜索页中的可见性。

## ADR-014：视频默认 720p，分阶段验证 LOD、批量复制与硬解

- 历史说明：本节记录 0.1.x 设计，不再代表 0.2 的固定上限策略。
- 状态：Superseded by ADR-023
- 日期：2026-11-14

### 背景

WATERFrAMES 1.21.1 参考实现会把媒体源分辨率交给 OpenGL 纹理。WATERMeDIA 3 默认可尝试硬件解码，但当前 OneScreen 使用 AWT 软件输出，仍存在 GPU 到 CPU 回读、`BufferedImage` 和 `NativeImage` 拷贝。直接放开 4K/8K 会让显存、内存带宽和客户端主线程成本不可控。

### 决定

- 默认视频输出继续限制为 `1280x720`。
- 增加 `1920x1080` 仅用于逐设备测试，不先改默认。
- 按相对 `renderDistance` 应用 WATERMeDIA `LodLevel`，并在阈值上加入迟滞。
- `MediaPlayer.scale(...)` 在 `start()` 前调用，重复尺寸不重复设置。
- ARGB 视频帧写入 `NativeImage` 改为 native-order `IntBuffer` 批量路径。
- 硬解继续交给 WATERMeDIA 的 `hardwareAccel` 配置和自动软件回退，不在 OneScreen 中伪造硬解状态。
- GPU 原生纹理直通另立里程碑，不混入本次 AWT 路径优化。

### 影响

- 720p 保持不变，优先级是先降低逐像素处理成本并验证 1080p。
- 玩家远离屏幕时降低输出和上传成本，但不得在边界反复重建纹理。
- 4K/8K 只能是实验能力；要发布支持承诺，必须完成硬解、软解、OpenGL、Vulkan、多屏和 30 分钟测试。
- 0.2 已用 GPU 纹理路径和动态 LOD 取代本 ADR 的固定 1280x720 策略；历史实施细节不再单独维护。

## ADR-015：同一 JAR 通过反射兼容 WATERMeDIA 3.0.0.23 与 3.0.1

- 状态：Superseded in part by ADR-024（兼容层保留，正式基线改为 3.0.0）
- 日期：2026-11-14

### 背景

WATERMeDIA `3.0.0.23` 的玩家 API 位于 `org.watermedia.api.media`，`3.0.1` 已迁移到 `org.watermedia.api.player`。如果业务代码直接链接任一版本的类，另一个版本会在类加载阶段抛出 `ClassNotFoundException` 或 `NoClassDefFoundError`。

### 决定

- 业务代码只引用 `WaterMediaCompat` 的中立包装类型。
- 启动时优先检测 `PlayerAPI`；未找到时回退到旧版 `MediaAPI`。
- MRL、图像/音频引擎、播放器工厂、播放控制和尺寸设置全部通过公开反射调用。
- 传给播放器的图形引擎 supplier 必须返回 WATERMeDIA 原生 `GFXEngine` 对象，不能返回 OneScreen 的包装对象。
- 旧 `3.0.0.x` 测试版只保留 best-effort 兼容；正式测试目标为 WATERMeDIA `3.0.0` 及后续 `3.0.x`。

### 影响

- 同一个 OneScreen JAR 可同时支持两套 WATERMeDIA 包布局。
- 反射调用缺少编译期签名检查，因此升级 WATERMeDIA 时必须运行双版本冒烟测试。
- 当前正式组合为 WATERMeDIA `3.0.0` + Binaries `3.0.0`；更早的 `3.0.0.x` + `3.0.0.6` 仅作 best-effort 兼容。OneScreen 不打包原生库。
- 已完成旧测试版 API 识别冒烟；0.2 的正式构建与沙箱运行基线为 WATERMeDIA `3.0.0`。

## ADR-016：媒体引擎首帧前的空格式是合法状态

- 状态：Accepted
- 日期：2026-11-14

### 背景

WATERMeDIA 3.0.1 在图片引擎尚未上传第一帧时，`GFXEngine.format()` 可以返回 `null`。兼容层曾把空值转换成空字符串并直接用于 `switch`，导致渲染线程抛出 `NullPointerException` 并触发 `Render Frame` 崩溃。日志同时显示 HTTPS 图片已经成功解析和加载，因此该问题与 HTTPS 无关。

### 决定

- `format()` 返回 `null` 时表示“帧数据尚未就绪”，不是错误，也不得在渲染提取阶段崩溃。
- 像素格式为空、上传计数为零或尺寸未就绪时，本帧直接跳过。
- 状态和媒体类型名称统一归一化；未知值映射到 `UNKNOWN` 或空像素格式。
- 渲染提取中的媒体读取保留异常隔离，异常转为单次媒体错误而不是 Minecraft 渲染崩溃。

### 影响

- 图片第一帧到达前可以安全等待。
- `3.0.0.23` 与 `3.0.1` 都通过 `format(null)` 回归冒烟测试。
- HLS/M3U8 不是此次崩溃原因；OneScreen 当前允许 HTTP/HTTPS M3U8，实际播放依赖 WATERMeDIA 的 HLS 路径。

## ADR-017：画面比例使用五种显式适配模式

- 状态：Accepted
- 日期：2026-11-14

### 背景

媒体宽高比与屏幕宽高比不一致时，单一“保持比例”行为无法覆盖用户对裁切、拉伸、重复和原始尺寸的需求。

### 决定

- `fit`：保持比例并完整显示，空白区域透明。
- `stretch`：铺满四边形，不保持比例。
- `tile`：保持比例，以媒体长边 1 格为基准重复采样。
- `fill`：保持比例并铺满屏幕，通过裁剪 UV 移除溢出部分。
- `center`：保持比例，以媒体长边 1 格为基准居中显示。
- 默认值固定为 `fit`，保证旧世界升级后的视觉行为不变。
- 比例计算集中在无客户端依赖的 `ScreenFitGeometry`，渲染器只负责提交几何和 UV。

### 影响

- 平铺依赖 `DynamicTexture` 默认的 repeat sampler；UV 可以大于 `1.0`。
- GUI、指令、方块实体持久化和编辑 payload 都需要携带 `ScreenFitMode`。
- 居中和平铺的“1 格基准”是稳定的 Minecraft 世界尺寸定义，不随视频解码输出分辨率变化。

## ADR-018：距离上限由服务端 JSON 配置控制

- 状态：Accepted
- 日期：2026-11-14

### 背景

固定 `256` 格渲染距离和 `128` 格声音距离无法满足单机大屏或服务器大型场景。客户端 GUI 和服务端校验又必须使用同一上限，否则配置只对指令生效。

### 决定

- 新增 `config/onescreen.json`，包含 `maxRenderDistance` 和 `maxAudioDistance`。
- 默认上限保持 `256`/`128`；硬上限为 `4096`/`1024`，防止异常配置破坏浮点范围和渲染器距离接口。
- 配置由集成服务端或专用服务端加载；打开编辑器时通过 `ScreenEditorOpenPayload` 把有效上限发送给客户端。
- 指令和编辑 payload 都以服务端配置为准进行校验。
- 已有屏幕值不因降低配置上限而被自动改写。

### 影响

- 客户端不需要手动维护服务端距离配置；多人游戏中的编辑界面会使用服务端上限。
- 渲染器的全局最大视距使用硬上限，具体屏幕仍由自身的 `renderDistance` 和 `shouldRender` 决定。
- 过远距离会增加性能压力，配置变更需要重启生效。

## ADR-019：进度控制、直播追帧与循环播放进入屏幕编辑器

- 状态：Accepted
- 日期：2026-11-14

### 背景

单机打开暂停菜单时，直播源仍在服务端继续推进，而 `MediaSession` 按游戏暂停状态冻结播放器时钟，恢复后音视频会落后实时边缘。用户还需要在右键菜单中直接调整已知时长媒体的播放位置和循环状态。

### 决定

- `ScreenSettings` 新增持久化布尔值 `refreshOnResume`，默认 `false`，并通过编辑 payload、BlockEntity NBT 和服务端校验链同步。
- 设置界面新增循环播放勾选项、播放进度滑块和直播追帧勾选项。
- VOD 进度使用 WATERMeDIA `time()`/`duration()`/`canSeek()`；拖动时 `seekQuick()`，松开时 `seek()`。
- 直播进度只读。HLS 片段时长优先从 WATERMeDIA 的公开播放列表工具异步读取，并以独立 HTTP 播放列表解析作为旧版或工具不可用时的后备；按播放器时间与目标片段时长计算近似片段进度，没有可靠片段元数据时不伪造百分比。
- 自动追帧仅对 `live()` 或旧版 `liveSource()` 为真的播放器生效。暂停恢复或屏幕设置变化会重建同一 URL 的播放器，使直播重新从实时边缘加载；1 秒内最多重连一次。
- 追帧开关关闭时不增加任何重连；VOD 和图片不会因该选项重开。

### 影响

- 直播追帧会产生一次短暂缓冲/黑屏和音频重新初始化，这是默认关闭的原因。
- HLS 片段进度是目标时长近似值，不暴露或承诺精确的 HLS 媒体序列号。
- 循环状态现在可从 GUI 保存，服务端 `/screen loop` 仍保持兼容。
- 新功能以 WATERMeDIA `3.0.0` 及后续 `3.0.x` 为主要验收目标；旧 `3.0.0.x` 只做 best-effort 回归。OpenGL 与 Vulkan 仍需分别验证。

## ADR-020：紧凑编辑器布局与显式播放控制状态

- 状态：Accepted
- 日期：2026-11-14

### 背景

右键编辑器在 856×512 窗口和较高 GUI 缩放下曾出现底部控件裁切，数值字段的标签与输入框也存在垂直错位。新增播放、暂停、停止控件后，编辑器的暂停操作还必须能和单机暂停菜单、直播追帧及异常停止检测共存。

### 决定

- 编辑器面板固定为 `400×236`，窗口过小时整体限制在视口内；字段行使用 `15px` 控件高度，并让标签基线与输入框垂直居中。
- 播放、暂停、停止三个按钮放入编辑器底部绿色控制区；媒体尚未就绪时保持禁用。
- 编辑器打开期间，用户的播放/暂停操作通过 `MediaSession.manualPauseOverride` 覆盖游戏暂停状态；关闭编辑器后清除覆盖并重新跟随游戏暂停状态。
- 用户主动停止使用 `manuallyStopped` 标记，`STOPPED` 不再被误判为异常停止。从 `STOPPED/ENDED` 重新播放时，该标记保留到播放器离开终止状态，避免异步状态切换期间触发错误回收。
- 每 tick 更新播放器后重新检查会话是否仍持有播放器，再查询直播状态，禁止在更新路径已释放播放器后继续解引用。

### 影响

- 停止后立即重新播放可在 WATERMeDIA 3.0.1 下恢复为 `LOADING → PLAYING`。
- 编辑器的小窗口布局已在 856×512 下通过 OpenGL 与 Vulkan 实机视觉检查；字段标签和输入框对齐，底部保存/取消与全部播放控制均在视口内。
- 用户主动停止、暂停覆盖和异常停止现在是三个独立状态；后续新增播放器生命周期逻辑时必须保留该区分。

## ADR-021：编辑器新增手动刷新、平面旋转与隐藏画框材质

- 状态：Accepted
- 日期：2026-11-14

### 背景

播放器进入停止或异常状态后，仅依赖播放按钮无法可靠恢复媒体；建筑展示还要求屏幕画框不遮挡环境，并支持任意平面角度。设置必须继续由服务端权威保存，不能只在客户端隐藏或伪旋转。

### 决定

- 编辑器在播放组最左侧新增 `↻` 手动刷新按钮。该按钮不依赖播放器就绪状态，只要求屏幕已保存 URL。
- 客户端发送受验证的 `ScreenReloadPayload`；服务端重新检查权限、编辑距离、目标方块和 URL，再调用现有 `reload()` 递增 `loadRevision`，由既有媒体协调器关闭并重建会话。
- `ScreenSettings` 新增持久化 `rotationDegrees`（`-180..180`）与 `hideFrame`（默认 `false`）。
- 渲染和声音中心统一使用 `ScreenGeometry.right/up(facing, rotationDegrees)` 计算旋转后的平面基向量；九宫格偏移也沿旋转后的局部轴应用。
- `ScreenBlock` 新增 `hidden` 布尔 blockstate。开启时不渲染静态方块模型，但保留原 VoxelShape、BlockEntity、媒体渲染器和交互逻辑。
- 打开/更新 payload、NBT、`/screen info`、GUI 和命令同步新增字段；旧存档缺字段时回退到 `0°` 与 `false`。
- 新增 `/screen rotation <pos> <degrees>` 与 `/screen hideframe <pos> <true|false>`。

### 影响

- 手动刷新会短暂重建播放器，直播流会从当前实时边缘重新连接。
- 隐藏画框不会破坏方块或移除媒体；玩家仍可在原薄型点击区域右键打开编辑器。
- `-180..180` 已覆盖所有平面朝向。后续若允许无限角度输入，应在写入前规范化并表示当前等价角度。
- 新增设置必须继续同时更新服务端校验、GUI payload、BlockEntity 持久化和发布文档。

## ADR-022：Vulkan GPU 纹理共享 Minecraft graphics queue，并以 Mixin 串行化

- 状态：Accepted
- 日期：2026-11-15

### 背景

WATERMeDIA `VKEngine` 的纹理发布假定媒体写入和消费者采样发生在同一 Vulkan graphics queue。0.2 初版桥接使用了独立 compute/transfer queue，却没有跨队列 semaphore，导致 4K 播放约 2 秒后出现 `VK_ERROR_DEVICE_LOST`。

### 决定

- `WaterMediaVulkanContext.queue()` 返回 Minecraft `graphicsQueue()`。
- `VulkanQueueSynchronization` 按原生 `VkQueue` 句柄提供统一锁。
- `VulkanQueueSubmissionMixin`、`VulkanQueueMixin`、`VulkanGpuSurfaceMixin` 分别锁住 Minecraft 提交、等待和呈现。
- 不删除这些 Mixin，也不改回独立队列，除非提供完整的跨队列同步实现并通过设备丢失压力测试。

### 影响

- WATERMeDIA GPU 纹理与 Minecraft 使用同一队列和同一锁，满足外部同步要求。
- 这是项目当前唯一允许的 Mixin 区域。
- Minecraft 或 WATERMeDIA 升级后必须重新确认注入目标和 4K 连续播放。

## ADR-023：取消固定视频分辨率上限，保留距离 LOD

- 状态：Accepted
- 日期：2026-11-15

### 背景

0.1.x 将视频限制为 1280x720/1080p 实验档位，避免 CPU 拷贝失控。0.2 接入 GPU 纹理后，继续在 OneScreen 侧固定 max size 会阻断 WATERMeDIA 根据片源和设备选择更高分辨率。

### 决定

- 删除 `VIDEO_MAX_WIDTH`、`VIDEO_MAX_HEIGHT` 和播放器 `maxSize(...)` 限制。
- 保留 `VideoLod`，按屏幕距离比例选择上传档位，并带迟滞。
- 近距离 `LOD=MAX` 允许源尺寸。
- 硬解/软解策略交给 WATERMeDIA、FFmpeg、GPU、驱动和片源决定。
- GPU 纹理路径失败时回退 AWT CPU 路径。

### 影响

- 4K 纹理可以按 `3840x2160` 输出。
- `format=GPU` 不等于硬解；`hardwareAccel=false` 时仍可能稳定使用 GPU 纹理上传。
- 4K/8K 高帧率性能仍需按设备压测，不能作无条件承诺。

## ADR-024：WATERMeDIA 以 3.0.0 为正式基线

- 状态：Accepted
- 日期：2026-11-15

### 决定

- 正式基线为 WATERMeDIA `3.0.0`，Binaries `3.0.0`。
- `3.0.0.x` 是低于 `3.0.0` 的测试版本。
- `3.0.x`（x > 0）是高于所有 `3.0.0.x` 的正式版本。
- 加载器声明 `>=3.0.0.0 <3.1.0`，运行时由 `WaterMediaVersion` 做特殊排序。
- 旧 `3.0.0.x` 包布局只保留 best-effort 兼容，不再作为新功能的主要目标。

### 影响

- 不能直接使用普通四段版本比较器。
- 所有 WATERMeDIA 玩家 API 差异仍集中在 `WaterMediaCompat`。
- 升级 WATERMeDIA 时必须检查实际包布局、Binaries 配套和沙箱运行日志。

## ADR-025：Agent 文档与人类文档分离

- 状态：Accepted
- 日期：2026-11-15

### 决定

- `agents/` 保存 Agent 入口、项目上下文、开发日志和执行手册。
- 根 `README.md`、`README.en.md` 和 `docs/` 面向人类用户与二次开发者，说明安装、使用、架构、渲染、媒体、网络和测试原理。
- 根 `AGENTS.md` 只保留自动发现所需的最小导航，不复制详细规则。

### 影响

- 新 Agent 按 `agents/README.md` 的固定顺序阅读即可上手。
- 人类文档不反复维护“给下一个 AI 的交接步骤”。
- 文档链接变化后必须全仓搜索旧文件名，避免保留失效路径。
