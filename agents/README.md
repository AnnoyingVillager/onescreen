# OneScreen Agent 入口

本目录是新会话中软件工程 Agent 的唯一上手入口。所有长期维护的 Agent 规则、项目背景、开发日志和执行手册都集中在这里；`docs/` 与根目录 README 面向人类用户和二次开发者。

## 必读顺序

1. 本文件：了解硬性约束、当前状态和下一步。
2. [PROJECT-CONTEXT.md](PROJECT-CONTEXT.md)：了解产品、架构、代码地图和必须知道的技术背景。
3. [DEVELOPMENT-LOG.md](DEVELOPMENT-LOG.md)：了解已经做过什么、哪些结论已验证、哪些只是历史记录。
4. [RUNBOOK.md](RUNBOOK.md)：按其中的 PowerShell、沙箱和测试流程执行工作。
5. 按任务阅读 `docs/zh-cn/` 或 `docs/en/` 中的人类开发文档。开始修改前先检查 `git status --short`；不要回退不属于当前任务的未提交改动。

## 当前状态（2026-11-15）

- 公共最新发布：`0.2.0`。
- 当前源码版本：`0.2.0`，发布分支为 `main`，GitHub Release 标签为 `v0.2.0`。
- 0.2 已实现 FHD/4K 源尺寸支持、GPU 纹理直通、OpenGL/Vulkan 后端、CPU 回退和 Vulkan 共享队列同步。
- 0.2 源码、Agent 文档和人类文档应保留在 `main`；本地临时文件仍不得提交。
- Agent 文档整理已完成：长期内容集中在 `agents/`，旧计划/交接文档已合并删除；README 和 `docs/` 只保留人类使用、开发原理与项目记录。
- 最近一次沙箱构建通过；1080p Vulkan 使用硬件解码，4K AV1 10-bit HLG 由 WATERMeDIA 选择软解但 GPU 纹理上传正常。
- 4K Vulkan 修复后的连续播放测试超过 1 分钟，无新 crash report；仍需 OpenGL、多屏和长时间压力回归。

## 不可违反的规则

### 执行环境

- 所有工具调用都必须显式使用 PowerShell；禁止 bash、cmd 或 POSIX 终端语法。
- 不得启动沙箱外的 Minecraft，不得使用 PCL2，不得使用用户自己的整合包实例。
- 只允许运行工作区 `E:\waterframes\onescreen` 内的沙箱客户端。
- 如沙箱无法完成 GUI 或图形测试，应明确报告阻塞，不得擅自改用宿主机客户端。

### 代码边界

- `src/main` 不得引用 `net.minecraft.client.*`、纹理句柄、播放器或渲染器。
- `src/client` 才能包含渲染、媒体、音频和客户端生命周期代码。
- WATERMeDIA 玩家 API 的版本差异必须收敛在 `WaterMediaCompat`。
- 不直接链接 `org.watermedia.api.media.*` 或 `org.watermedia.api.player.*` 到业务代码。
- 不为未知 API 猜测方法签名；先检查依赖源码、反编译结果或 `javap`。

### 媒体与渲染

- OneScreen 不再设置固定 1920x1080 视频上限；不要重新引入 `maxSize(1920,1080)` 一类限制。
- 近距离使用 `LOD=MAX`，远距离才由 `VideoLod` 降档；阈值带迟滞，不得逐帧改 LOD。
- GPU 纹理路径失败时必须能回到 AWT CPU 纹理路径。
- Vulkan 必须使用 Minecraft graphics queue，并保持 WATERMeDIA 与 Minecraft 的提交、等待、呈现由同一队列锁串行化。
- 当前三个 Vulkan Mixin 是经过验证的窄范围例外；不得借机添加无关 Mixin、access widener 或私有字段访问。
- `hardwareAccel=true` 不等于 GPU 纹理路径；`format=GPU` 也不等于硬解。必须分别记录。

### 工作方法

- 先读代码和真实依赖源码，再修改；一次只处理一个子系统。
- 每次代码修改后至少运行：
  `.\gradlew.bat build --offline --no-daemon --no-configuration-cache`
- 涉及渲染或媒体时，必须在沙箱客户端实机验证；编译通过不能代替画面、声音和生命周期验证。
- 修复、设计变更、测试结果必须同步到本目录，并在需要时同步人类文档。
- 未获用户明确要求，不要提交、推送、打标签或发布。
- 不要把临时脚本、`run/`、`logs/`、`crash-reports/`、`config/` 或下载的 Binaries 加入提交。

## 当前优先任务

1. 回归 OpenGL：1080p、4K、暂停、停止、刷新和循环。
2. Vulkan 4K 至少 30 分钟压力测试，并测试停止后立即重播。
3. 多屏同时播放、快速切换 URL、区块卸载、维度切换和断线清理。
4. 验证 1080p/4K 下 `format=GPU/CPU` 与 `hardwareAccel=true/false` 的独立日志含义。
5. 清理 `.codex-*` 临时文件，确认 `config/`、`run/`、日志和 Binaries 不进入提交。
6. 发布前再次执行 Markdown 链接检查、`git diff --check` 和离线构建；未完成测试不得写成已通过。

## 文档职责

- `agents/`：Agent 规则、上下文、日志、运行与交接手册。
- `README.md` / `README.en.md`：用户安装、使用、兼容版本与下载。
- `docs/zh-cn/`、`docs/en/`：中英文人类使用和二次开发文档；两种语言保持同构文件名与交叉链接。
- `docs/zh-cn/TESTING.md`、`docs/en/TESTING.md`：人类可执行测试矩阵。
- `docs/zh-cn/DECISIONS.md`、`docs/en/DECISIONS.md`：面向开发者的架构决策记录。
- `docs/zh-cn/API-VERSIONS.md`、`docs/en/API-VERSIONS.md`：依赖版本和 API 兼容边界。
