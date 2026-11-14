# 里程碑跟踪

状态值：`TODO`、`IN_PROGRESS`、`BLOCKED`、`DONE`。

| 里程碑 | 状态 | 交付物 | 验收 |
|---|---|---|---|
| M0 范围冻结 | DONE | SCOPE、AGENTS | 范围审查完成 |
| M1 26.2 空模组 | DONE | Gradle、入口、fabric.mod.json | client/server 启动 |
| M2 屏幕方块 | DONE | block、BE、模型和资源 | 放置/破坏/重启 |
| M3 屏幕配置 | DONE | settings、Value IO、同步 | 保存与区块加载 |
| M4 占位渲染 | DONE | RenderState、Renderer | 四向和尺寸正确 |
| M5 静态图片 | DONE | MediaSession、Manager、DynamicTexture | PNG/JPEG/WebP 与生命周期 |
| M6 视频声音 | DONE | 播放状态、距离音量 | 视频/音频同步 |
| M7 命令同步 | DONE | 命令树、URL 校验、reload 修订 | 命令与重进恢复 |
| M8 稳定性 | IN_PROGRESS | 会话上限、清理、日志 | 30 分钟测试待做 |
| M9 多人发布 | IN_PROGRESS | GitHub Release、发布包、说明、清单 | GitHub 0.1.0 已发布；CurseForge/Modrinth 与完整多人矩阵待完成 |

## M0 范围冻结

完成条件：

- 功能和非功能列表明确。
- API 版本锁定。
- 明确禁止 Mixin 和复杂 GUI。

## M1 26.2 空模组

完成条件：

- `compileJava` 成功。
- `runClient` 进入主菜单。
- `runServer` 无客户端类加载错误。

## M2 屏幕方块

完成条件：

- 方块注册、放置、破坏、掉落和模型正确。
- 世界重进后方块仍存在。

## M3 屏幕配置

完成条件：

- URL、尺寸、音量和循环持久化。
- 玩家进入区块时同步当前值。

## M4 占位渲染

完成条件：

- 使用 26.2 render state + submit。
- 四向朝向和尺寸通过视觉验证。
- 大屏幕包围盒正确。

## M5 静态图片

完成条件：

- 单图片可以显示。
- 修改 URL、离开区块、重进可正确关闭和重开。

验收记录：

- WATERMeDIA 通过公开 `HeadlessGFXEngine` 输出 BGRA 8-bit 帧，不读取 OpenGL 私有纹理句柄。
- PNG、JPEG、WebP 均显示成功，图片方向与 16:9 宽高比正确。
- URL 切换会关闭旧会话并替换纹理；404 URL 显示红色错误占位且只记录一次错误。
- 区块卸载、断线、重进均会关闭会话；返回区块和重新进入世界后各只重建一次会话。
- 断线清理已调度到 Minecraft 客户端线程，回归日志中不再出现 `Rendersystem called from wrong thread`。

## M6 视频声音

完成条件：

- 视频和声音同步。
- 距离音量、静音、暂停正确。

验收记录：

- 视频使用 WATERMeDIA 公开 `AWTEngine` 强制输出 packed BGRA/ARGB，持续更新 Minecraft `DynamicTexture`；不绑定 OpenGL 或 Vulkan 私有纹理句柄。
- 音频使用 WATERMeDIA 公开 `JSEngine`，不依赖 OpenGL 后端的当前 OpenAL context；媒体音轨由 `MediaPlayer` 统一驱动。
- 音量按玩家到屏幕中心距离在 2–48 格线性衰减，再乘屏幕音量和静音状态；配置变化只更新现有播放器，不重开 URL。
- 单机暂停菜单打开和关闭会分别进入 `PAUSED` 与 `PLAYING`，视频帧和声音同时暂停/恢复。
- MP4 循环、非循环结束、运行时静音、维度切换关闭与返回重载均已验证。
- OpenGL 3.3 与 Vulkan 1.4.329 均显示 MP4，WATERMeDIA 日志均为 `video: true, audio: true`，无后端专用纹理调用异常。

## M7 命令同步

完成条件：

- 权限和参数校验。
- 多客户端 URL 一致。
- 保存后重进恢复。

验收记录：

- `/screen set|size|volume|mute|loop|reload` 使用权限等级 2；`/screen info` 可公开查询。
- URL 只接受带有效主机名的 `http://` / `https://`，并拒绝空值、超长、空白/不可见字符、无效语法和 `file:` / `javascript:` 等协议。
- `reload` 使用持久化 `loadRevision`；客户端检测到修订变化后关闭并重新解析同一 URL。
- 命令即时更新方块实体并通过原版 BlockEntity 更新包同步客户端，不新增自定义 payload。
- Vulkan 客户端已验证全部命令、非法 URL、非屏幕目标、尺寸/音量/静音/循环即时生效，以及保存后重进恢复。

## M8 稳定性

完成条件：

- 活跃会话有上限。
- 快速操作无泄漏。
- 日志有限流。
- 连续运行 30 分钟观察通过。

进度：

- 单客户端活动会话上限已实现为 8；超限屏幕进入延迟集合，容量释放后自动重试。
- 错误日志仍保持每会话一次；URL 变更继续严格关闭旧会话。
- 9 屏上限实测和连续 30 分钟内存/线程观察尚未完成。
- 视频处理交接已完成文档化，见 `docs/VIDEO-HANDLING-AGENT-GUIDE.md`。
- 输出质量档位、距离 LOD、批量像素写入、硬解/软解和 1080p 压测仍待实施与验收。

## M9 多人发布

完成条件：

- 完整测试矩阵通过。
- 发布说明和已知限制完整。
- 客户端与服务端依赖说明准确。

## 当前验证备注

- 2026-11-13：`compileJava` 通过，客户端入口与渲染启动通过。
- `runServer` 已完成世界生成、输出 `Done`，并正常保存区块后关闭。
- 2026-11-13：M2 由客户端截图确认创造物品栏、放置、四向方块与挖掘掉落；M3 已验证配置即时读写、越界收敛和关服重载恢复。
- 2026-11-13：M4 已确认 4×2.25 数据与画面、四向朝向、8×5 超出单方块显示；约 93 格仍可见，约 110 格已剔除。
- 2026-11-13：M5 通过本地 HTTP 图片矩阵、换图、404、区块卸载/重载、断线/重进和西向屏幕视觉验收。
- 2026-11-13：M6 通过 MP4 画面/音轨、循环与结束、运行时静音/音量、单机暂停/继续、维度清理/重载；OpenGL 3.3 与 Vulkan 1.4.329 均完成实机回归。
- 2026-11-13：M7 通过 `/screen` 六类修改命令、info、URL 白名单、非法目标、reload 修订和保存后重进恢复；Vulkan 客户端完成验收。
- 2026-11-14：六向 1×1×1/16 薄画框通过 Vulkan 1.4.329 实机回归；将 `facing=up/down` 的 blockstate 旋转交换为 `x=270/90`，修复贴图落在同方块内相反一侧的问题。宽面使用中心透明的边框贴图，四窄边保留原贴图；空屏 debug 关闭时透明，debug 开启显示占位材质。
- 2026-11-14：同一模型修复在 OpenGL 3.3 完成边框、媒体方向和透明/调试状态回归。

- 2026-11-14：新增 `onescreen:screen` 专属创意栏并以屏幕画框作为栏图标；移除建筑方块栏注入。OpenGL 3.3 实机确认 `OneScreen` 栏目和内嵌物品显示，`gradlew build` 通过。
- 2026-11-14：WATERMeDIA 双布局兼容层完成。`3.0.0.23` 基线通过 `compileJava`、`compileClientJava` 和 `build`；两版 API 识别冒烟测试分别输出 `WATERMeDIA 3.0.0.x MediaAPI` 与 `WATERMeDIA 3.0.1+ PlayerAPI`。Binaries 3.0.0 官方文件的 SHA-512 已校验。匹配组合的游戏内图片、视频、声音及 OpenGL/Vulkan 回归仍待执行。`3.0.0.23` 仅为初版兼容，未来可能移除。
- 2026-11-14：修复 WATERMeDIA 图片首帧前 `GFXEngine.format() == null` 导致的 `Render Frame` 崩溃。错误报告中的 HTTPS 图片已经成功加载，崩溃与 HTTPS 无关；修复后 `compileJava`、`compileClientJava`、`build` 及两版 API 的 `format(null)` 冒烟测试均通过。另确认 OneScreen 接受 HTTP/HTTPS M3U8，WATERMeDIA 3.0.1 支持 HLS Master/Media Playlist、直播/VOD 探测和 FFmpeg HLS 解复用；IPTV 多频道列表当前只播放第一个源，实机回归待做。
- 2026-11-14：紧凑编辑器在 OpenGL 3.3 与 Vulkan 1.4.329、856×512 窗口下完成实机回归；字段标签与输入框对齐，底部保存/取消和 ▶/⏸/⏹ 控件完整可见。播放、暂停、停止及停止后重新播放均进入预期状态；Vulkan 回归中发现并修复了停止后重播时的播放器空指针，关闭编辑器后游戏暂停会重新正确冻结媒体。
- 2026-11-14：公开 GitHub 仓库、MIT 许可证、脱敏开发文档、CI 构建与标签自动发布工作流已配置；0.1.0 Release 由 Actions 成功生成主 JAR、源码 JAR 和 SHA-256 文件，下载后的双 WATERMeDIA API 冒烟测试通过。
