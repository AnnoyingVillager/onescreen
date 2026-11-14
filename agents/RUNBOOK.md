# OneScreen Agent 执行手册

## 1. 工作区

- 代码仓库：`E:\waterframes\onescreen`
- 外部参考源码：`E:\waterframes\_mc26.2-sources`、`E:\waterframes\_watermedia-sources`
- Gradle 缓存：`E:\waterframes\.gradle-user-home`
- 客户端运行目录：`E:\waterframes\onescreen\run`

只操作工作区内的客户端。不要启动 PCL2、用户整合包或 `E:\.minecraft\versions\...`。

## 2. 强制 PowerShell

每次执行命令都必须明确指定 PowerShell。禁止 bash、cmd、Git Bash、WSL 或 POSIX 管道语法。

Windows 构建环境：

```powershell
$env:JAVA_HOME='C:\Users\user\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon'
$env:GRADLE_USER_HOME='E:\waterframes\.gradle-user-home'
.\gradlew.bat build --offline --no-daemon --no-configuration-cache
```

启动沙箱客户端：

```powershell
$env:JAVA_HOME='C:\Users\user\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon'
$env:GRADLE_USER_HOME='E:\waterframes\.gradle-user-home'
.\gradlew.bat runClient --offline --no-daemon --no-configuration-cache
```

如果有 `$env:`、管道或多条语句，仍然必须使用 PowerShell。

## 3. 开工前检查

```powershell
git status --short
git log -3 --oneline
Get-Content 'agents\README.md' -Raw
Get-Content 'agents\PROJECT-CONTEXT.md' -Raw
Get-Content 'agents\DEVELOPMENT-LOG.md' -Raw
```

确认：

- 当前版本和 `HEAD` 是否一致。
- 是否有未提交的用户改动。
- 最近一次构建是否通过。
- 是否有新的 crash report。

不要覆盖或回退不属于当前任务的未提交改动。

## 4. API 事实检查

修改陌生 API 前：

1. 在 `E:\waterframes\_mc26.2-sources` 或 `_watermedia-sources` 搜索真实源码。
2. 必要时用 `javap` 检查 Gradle 缓存中的编译类。
3. 确认包名、方法名、参数类型、返回值和线程约束。
4. 如果找不到签名，停止猜测并报告阻塞。
5. 不要为了消除编译错误而大面积重写。

WATERMeDIA 反射入口只允许出现在 `WaterMediaCompat` 及其直接协作者中。

## 5. 测试素材

本地媒体服务：

```text
http://localhost/1080p.mp4
http://localhost/4k.mp4
```

测试命令：

```text
/screen set 600 -31 600 http://localhost/1080p.mp4
/screen set 600 -31 600 http://localhost/4k.mp4
```

如果 `localhost` 不可用，先检查 HTTP 服务；不要把网络不可达误判为播放器 bug。

## 6. 日志与崩溃定位

关键路径：

- `run/logs/latest.log`
- `run/logs/debug.log`
- `run/crash-reports/`
- `crash-reports/`（如由启动器生成）
- `build/libs/`

先跑这些筛选：

```powershell
Select-String -Path 'run\logs\latest.log' -Pattern 'onescreen|watermedia|VK_ERROR|DEVICE_LOST|Retention wait|hardwareAccel|Media texture ready|FFMediaPlayer' -Context 2,4
Get-ChildItem 'run\crash-reports' | Sort-Object LastWriteTime -Descending | Select-Object -First 3
```

崩溃分析顺序：

1. 找最内层 `Caused by`，不要只看 Minecraft 的外层 `ReportedException`。
2. 确认是渲染线程、FFmpeg 线程还是生命周期线程。
3. 对照最新一次代码修改和日志时间。
4. 判断是网络、解码、纹理、队列同步、资源释放还是命令/数据问题。
5. 修复后必须重新运行同一复现场景。

## 7. Vulkan 专项

当前架构：

- WATERMeDIA 使用 Minecraft graphics queue。
- Minecraft 和 WATERMeDIA 共享同一把每队列锁。
- 3 个 Mixin 必须全部注入。

启动后先确认：

```powershell
Select-String -Path 'run\logs\debug.log' -Pattern 'VulkanQueueMixin|VulkanQueueSubmissionMixin|VulkanGpuSurfaceMixin|onescreen.client.mixin'
```

4K/1080p 日志最少检查：

```text
Video at ... uses WATERMeDIA GPU texture path (Vulkan)
Media texture ready ... (3840x2160, format=GPU, backend=Vulkan)
Video decode ... hardwareAccel=false/true
```

如果出现：

```text
Retention wait failed
VK_ERROR_DEVICE_LOST
GpuDeviceLossException
```

立即停止当前测试，保留日志和 crash report，不要继续反复启动同一客户端。优先检查：

- 是否仍指向 graphics queue。
- 队列锁是否覆盖 submit/wait/present。
- 三个 Mixin 是否成功注入。
- 是否误用了 compute/transfer queue。
- 是否有其他图形后端代码同时操作同一 device/queue。

## 8. 构建与实机验收

代码完成后至少：

```powershell
$env:JAVA_HOME='C:\Users\user\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon'
$env:GRADLE_USER_HOME='E:\waterframes\.gradle-user-home'
.\gradlew.bat build --offline --no-daemon --no-configuration-cache
```

然后按改动范围测试：

- 方块/模型：六向朝向、透明、debug、隐藏画框、点击区域。
- 图片：PNG/JPEG/WebP、错误 URL、换图、区块卸载。
- 视频：1080p/4K、暂停、停止、刷新、循环、声音。
- 播放器：VOD seek、直播进度、自动追帧。
- 图形后端：OpenGL 与 Vulkan 都测。
- 生命周期：退出世界、维度切换、断线、快速切换 URL。

记录结果时区分：

- 编译通过。
- 自动日志检查通过。
- 实机画面通过。
- 性能压力通过。

不要把未做的测试写成已完成。

## 9. 文档同步

每次修改至少检查：

- `agents/DEVELOPMENT-LOG.md`：是否需要追加本次结果。
- `agents/PROJECT-CONTEXT.md`：架构/版本/约束是否变化。
- `agents/README.md`：当前状态和下一步是否变化。
- `docs/`：人类可读的架构、API、测试、决策是否需要同步。

文档要求：

- Agent 文档只在 `agents/` 维护。
- README 和 `docs/` 面向人类，使用完整使用说明和实现原理。
- 不保留失效链接、重复交接文档或历史临时计划。
- 重要修复必须写清根因、影响、修复、验证和剩余风险。

## 10. 结束前检查

```powershell
git diff --check
git status --short
git diff --stat
```

确认：

- 没有 PowerShell/bash 混乱留下的临时文件。
- 没有 `run/`、`logs/`、crash report、Binaries 或私人 URL。
- JAR 构建成功。
- 相关实机测试完成或明确记录未完成。
- 文档链接有效。
- 没有擅自提交、推送或发布。

## 11. 交接给下一会话

交接内容至少包括：

- 当前目标与用户最新约束。
- 已修改文件。
- 已通过和未通过的测试。
- 最新日志/crash 路径。
- 当前 JAR 路径和哈希。
- Git 工作树状态。
- 下一个最小可执行任务。

不要只写“继续开发”；必须写清可复现的下一步。