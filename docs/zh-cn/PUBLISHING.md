# 发布流程

## 当前版本状态

- 最新公开版本：`0.2.0`。
- 当前源码版本：`0.2.0`，对应 GitHub Release 标签 `v0.2.0`。
- 发布标签格式：`v<version>`，例如 `v0.2.0`。
- 标签版本必须与 `gradle.properties` 中的 `version` 完全一致。

发布前同步修改或确认：

- `gradle.properties` 的 `version`。
- `CHANGELOG.md` 的版本条目。
- `README.md`、`README.en.md` 的公开版本和兼容性说明。
- 如有必要，增加 `.github/release-notes/v<version>.md`。
- `fabric.mod.json` 继续使用 Gradle 占位符 `"${version}"`，不要手填。

## 本地构建

Windows PowerShell：

```powershell
$env:JAVA_HOME='C:\Users\user\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon'
$env:GRADLE_USER_HOME='E:\waterframes\.gradle-user-home'
.\gradlew.bat build --rerun-tasks --offline --no-daemon --no-configuration-cache
```

使用系统 JDK 25 时可省略 `JAVA_HOME` 和 `GRADLE_USER_HOME`。发布构建建议加 `--rerun-tasks`，避免复用旧的打包结果。

验收产物：

```text
build/libs/onescreen-<version>.jar
build/libs/onescreen-<version>-sources.jar
build/libs/onescreen-<version>.sha256
```

最后一个文件由发布流水线生成；本地可使用 PowerShell 手动核对 JAR 和 sources JAR：

```powershell
Get-FileHash 'build\libs\onescreen-0.2.0.jar' -Algorithm SHA256
Get-FileHash 'build\libs\onescreen-0.2.0-sources.jar' -Algorithm SHA256
```

检查 JAR 内至少包含：

- `fabric.mod.json`
- `LICENSE`
- `assets/onescreen/lang/*.json`
- `src/main` 对应的服务端/公共类
- `src/client` 对应的客户端类
- `onescreen.client.mixins.json` 和三个 Vulkan Mixin

不要把以下内容打进发布 JAR 或提交到仓库：

- `run/`、`logs/`、crash report、世界存档。
- `.gradle/`、`build/`。
- 下载的 WATERMeDIA Binaries。
- 本地测试媒体、临时 `.codex-*` 脚本或私人 URL。
- 本机用户名、令牌、私钥和个人绝对路径。

## GitHub Actions

### `build.yml`

- 每次 `push`、`pull_request` 和手动触发时构建。
- 使用 Temurin Java 25。
- 上传 `build/libs/*.jar`（排除 `*-dev.jar`）作为 14 天测试产物。

### `release.yml`

- 仅在推送 `v*` 标签时运行。
- 强制 `build --rerun-tasks`。
- 校验标签版本与 `gradle.properties` 一致。
- 生成主 JAR、sources JAR 的 SHA-256。
- 使用 `GITHUB_TOKEN` 创建 GitHub Release，并附加三个文件。

工作流自身的 `run` 步骤在 Ubuntu 上可使用 shell 脚本；本地开发和新 Agent 操作仍必须使用 PowerShell。这是 CI 与本地执行环境的边界，不是允许在本地改用 bash 的先例。

## 创建发布标签

只有用户明确要求发布后才执行：

```powershell
git tag -a v0.2.0 -m 'OneScreen 0.2.0'
git push origin main
git push origin v0.2.0
```

推送标签会触发 `.github/workflows/release.yml`。如果标签与项目版本不一致，流水线必须失败，不创建 Release。

## 发布前检查

- [ ] `gradlew build` 通过，工作树没有意外临时文件。
- [ ] Java 25、Minecraft 26.2、Fabric API、WATERMeDIA 3.0.0 基线在 README 中一致。
- [ ] 0.2 OpenGL 1080p/4K 回归完成。
- [ ] 0.2 Vulkan 1080p/4K 回归完成。
- [ ] Vulkan 三个队列同步 Mixin 注入确认，无设备丢失。
- [ ] 暂停、停止、刷新、循环、VOD seek、HLS 进度完成回归。
- [ ] README、README.en、CHANGELOG、发布说明和人类开发文档同步。
- [ ] `agents/` 当前状态、测试结果和下一任务同步。
- [ ] JAR 内无私人 URL、日志、Binaries 或运行时世界。
- [ ] Release 同时包含主 JAR、sources JAR 和 SHA-256 文件。

## 密钥与权限

发布工作流只使用 GitHub 自动提供的 `GITHUB_TOKEN`。权限保持最小化：普通构建只有 `contents: read`，发布任务才声明 `contents: write`。仓库中不得保存 PAT、密码或私钥。