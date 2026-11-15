# OneScreen 开发日志

本文件记录对后续 Agent 仍有价值的开发历史。详细的人类发布说明见根目录 `CHANGELOG.md` 和 `.github/release-notes/`。

## 2026-11-13：项目骨架与首个可玩闭环

- 建立 Minecraft 26.2 / Fabric split source set，锁定 Java 25、Gradle 9.8.2、Loom 1.19.4。
- 完成屏幕方块、BlockEntity、数据持久化、26.2 render state + submit 渲染、图片和视频声音。
- 完成 `/screen` 命令、URL 校验、服务端同步、区块卸载清理。
- OpenGL 3.3 与 Vulkan 1.4.329 均完成基础 MP4 画面/声音回归。
- 活动会话上限设为 8，超限屏幕延迟加载。

## 2026-11-14：0.1.x 功能与发布

### 六向薄画框

- 将屏幕改为 1x1x1/16 画框，支持任意六个方块表面。
- 修复 `UP/DOWN` 模型与媒体面落在同一方块错误侧、UV 反向和缺失上下窄边贴图的问题。
- 空屏默认透明；`/screen config debug on` 后才显示占位/边框材质。
- 建立独立 `OneScreen` 创意栏。
- 修复贴图方向、透明、debug 和六向几何，在 OpenGL/Vulkan 均完成视觉回归。

### WATERMeDIA 兼容与首个崩溃修复

- 兼容 WATERMeDIA 旧 `MediaAPI` 与新版 `PlayerAPI` 两套包布局，差异集中在 `WaterMediaCompat`。
- 修复图片首帧前 `GFXEngine.format() == null` 导致的渲染线程崩溃。
- 确认原错误与 HTTPS 无关，图片实际已加载成功。
- 确认 M3U8/HLS 解析和 FFmpeg 直播/VOD 探测能力；多频道 IPTV 仍只播放第一个源。

### 编辑器与屏幕控制

- 增加画面适应、拉伸、平铺、填充、居中。
- 增加进度条、循环、直播刷新、播放/暂停/停止。
- 修复小窗口和 GUI 缩放时的越界、字段错位。
- 增加手动刷新、平面内任意角度旋转、隐藏画框及持久化命令。
- 修复暂停状态退出编辑器后失效、停止后画面残留和停止后无法重播。
- 放大刷新图标并重做 PCL2 可见的模组图标。

### 发布

- 0.1.0：首个 GitHub Release，含 Actions 构建、JAR、sources JAR 和 SHA-256。
- 0.1.1：手动刷新、旋转、隐藏画框。
- 0.1.2：暂停/停止/刷新/图标修正。
- 0.2.0 随后成为新的公共版本。

## 2026-11-15：0.2 高分辨率与 GPU 纹理

### 已完成

- 删除 OneScreen 固定 `1920x1080` 视频上限和播放器 `maxSize/scale` 调用。
- 新增 `VideoLod`，保留远距离分辨率降档和迟滞。
- 新增 `ExternalMediaTexture`，接入 WATERMeDIA OpenGL/Vulkan GPU 纹理。
- 新增 `WaterMediaVersion`，实现 WATERMeDIA 特殊版本排序。
- 新增 `WaterMediaVulkanContext`，将 Minecraft Vulkan device 暴露给 `VKEngine`。
- 更新 `fabric.mod.json` 到 WATERMeDIA `>=3.0.0.0 <3.1.0`，构建基线改为 `3.0.0` + Binaries `3.0.0`。

### Vulkan 崩溃与根因

首次 4K Vulkan 播放约 2 秒后出现：

```text
Retention wait failed (VkResult -4)
GpuDeviceLossException: VK_ERROR_DEVICE_LOST
```

根因不是 HTTPS，也不是纹理尺寸注册失败，而是 WATERMeDIA 与 Minecraft 使用了不同 Vulkan 队列：

- WATERMeDIA `VKEngine` 的纹理屏障假定媒体和消费者在同一 graphics queue。
- 旧桥接却选择了独立 compute/transfer queue。
- 没有跨队列 semaphore，也没有共享 Minecraft 提交锁。

### 修复

- WATERMeDIA 改用 Minecraft `graphicsQueue()`。
- 新增 `VulkanQueueSynchronization`，按原生 `VkQueue` 句柄分配统一锁。
- 新增 3 个窄范围 Mixin：
  - `VulkanQueueSubmissionMixin`：锁住 `vkQueueSubmit2KHR`
  - `VulkanQueueMixin`：锁住 `vkQueueWaitIdle`
  - `VulkanGpuSurfaceMixin`：锁住 `vkQueuePresentKHR`
- 3 个 Mixin 在 `run/logs/debug.log` 中确认全部注入。

### 验证

- 1080p：`1920x1080`、`format=GPU`、`hardwareAccel=true`。
- 4K：`3840x2160`、`format=GPU`、WATERMeDIA 选择软解。
- 修复后 4K 连续播放超过 1 分钟，无 `Retention wait failed`、无新 crash report。
- 构建通过，JAR 为 `build/libs/onescreen-0.2.0.jar`，166563 bytes，SHA-256 `F577D5A5926C2D9562CC173D1FAFAFE8ADC069C7EC70964830A8A34020A479DD`。

## 2026-11-15：Agent 与人类文档重构

- 新建稳定入口 `agents/README.md`、`agents/PROJECT-CONTEXT.md`、`agents/DEVELOPMENT-LOG.md`、`agents/RUNBOOK.md`。
- 合并并删除旧 `docs/AI-WORKFLOW.md`、`docs/HANDOFF-2026-11-15-0.2.md`、`docs/VIDEO-HANDLING-AGENT-GUIDE.md`、`docs/PROJECT_PLAN.md`、`docs/MILESTONES.md`、`docs/OPEN-QUESTIONS.md`。
- 根 `AGENTS.md` 保留为最小启动导航，避免新 Agent 读取多份重复或过期流程。
- README 与 `docs/` 重新定位为人类使用说明和二次开发实现原理；补充 GPU/CPU 路径、Vulkan 队列契约、当前命令树、配置、测试和发布流程。
- 清理旧类名、失效文件名、错误的 `debug sessions` 命令、不存在的 `getRenderBoundingBox` 签名说明和旧固定分辨率建议。
- 所有本地命令示例统一使用 PowerShell；Agent 文档明确只允许工作区内沙箱客户端，禁止 PCL2 和宿主机客户端。
- 人类文档进一步拆分为 `docs/zh-cn/` 与 `docs/en/`；英文 README、英文 Changelog 和全部英文开发文档已补齐，内部链接按语言树分别维护。

## 2026-11-15：画框无承托放置

- `ScreenBlock.getStateForPlacement()` 改为只使用 `BlockPlaceContext.getClickedFace()`；不再通过 `isFaceSturdy` 限制完整方块。
- 删除 `canSurvive` 和读取承托邻居的 `updateShape` 自毁逻辑。画框不保存承托方块坐标，原贴附方块被破坏后保持悬空。
- `.\gradlew.bat build --offline --no-daemon --no-configuration-cache` 通过。
- Vulkan 1.4.329 沙箱实机验证：可在橡木楼梯、橡木栅栏的可点击表面放置 `onescreen:screen`。
- 实机验证：移除原本贴附的楼梯或栅栏后，F3 仍识别同一 `onescreen:screen` 方块，画框不自动掉落，`FACING` 保持不变。
- 本次测试没有新增 crash report；日志中的媒体连接失败来自测试世界中此前保存但本地 HTTP 服务未启动的 URL，与本方块改动无关。

## 2026-11-15：WATERMeDIA 失败 MRL 缓存恢复

- 稳定复现：关闭 IIS 本地站点后进入游戏，在同一屏幕输入 `http://localhost/4k.mp4`；媒体进入 `ERROR`。随后启动站点并点击右键编辑器 `↻`，旧实现仍无法恢复。
- 根因：WATERMeDIA 按 URL 缓存 MRL。HTTP 离线阶段生成的失败对象会一直留在缓存中；OneScreen 虽然递增 `loadRevision` 并重建 `MediaSession`，再次调用 `mrl(url)` 仍得到同一个 `ERROR` 对象。
- 修复：`WaterMediaCompat.mrl()` 构造 `MediaResource` 后调用 `reloadIfRegenerable()`；对 `ERROR/BLOCKED/EXPIRED/FORGOTTEN` 反射调用 WATERMeDIA `reload()` 并替换 handle，`FETCHING` 保持不打断。
- 反射仍全部限定在 `WaterMediaCompat`，没有扩散到业务层或渲染层。
- 构建：`gradlew.bat build --offline --no-daemon --no-configuration-cache` 通过。
- 首次恢复回归（2026-11-15 17:26:21）：HTTP 恢复后，同一 `619, -32, 602` 屏幕经右键 `↻` 与关闭编辑器触发新会话，依次进入 `WAITING -> LOADING -> PLAYING`。
- 单独按钮回归（2026-11-15 17:33）：在视频播放中点击右键菜单 `↻`，随后点击 `Cancel`（不保存设置）；客户端在关闭编辑器后关闭旧会话并重建，于 17:33:57 再次进入 `PLAYING`，证明恢复不依赖设置保存。
- 实机纹理日志：`Media texture ready ... (3840x2160, format=GPU, backend=Vulkan)`；画面恢复正常，证明失败缓存已被刷新。
- `Media source exceeds cache limit` 对约 1.93 GB 的测试文件是预期缓存旁路，之后 FFmpeg 可直接流式打开，不是本次失败根因。

## 当前 Git 状态

- 最新公开 Release 为 `v0.2.1`；历史基线 `v0.1.2` 为 `c0c2660`。
- `v0.2.1` 标签指向发布提交 `a74cfca`，已由 GitHub Actions 自动构建并发布主 JAR、sources JAR 和 SHA-256。
- 本地仍可能存在 `.codex-*` 临时文件、`config/` 和运行目录；这些内容不进入提交。
- 未获用户明确要求，不要自行提交、推送、打标签或发布。

## 仍在进行

- OpenGL 下 1080p/4K 回归。
- Vulkan 4K 30 分钟压力测试。
- 多屏、高并发、快速 URL 切换和维度切换。
- 停止后立即重播、暂停、刷新、循环的组合回归。
