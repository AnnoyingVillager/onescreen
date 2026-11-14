# 发布流程

## 版本规则

项目使用语义化版本，当前发布版本为 `0.1.1`。

发布前需要同步修改：

- `gradle.properties` 中的 `version`。
- `CHANGELOG.md`。
- GitHub Release 标签，格式为 `v<version>`。
- 如有必要，更新 `.github/release-notes/v<version>.md`。

`fabric.mod.json` 的版本使用 Gradle 占位符，无需手工修改。

## 本地发布构建

```bash
./gradlew build --rerun-tasks --no-configuration-cache
```

确认以下文件存在并通过 ZIP/JAR 校验：

```text
build/libs/onescreen-<version>.jar
build/libs/onescreen-<version>-sources.jar
```

同时检查 JAR 内的 `fabric.mod.json`、`LICENSE`、语言文件和客户端类。

## GitHub Actions

- `.github/workflows/build.yml`：每次推送、拉取请求或手动触发时构建并上传测试产物。
- `.github/workflows/release.yml`：推送 `v*` 标签时强制重新构建、生成 SHA-256 文件并创建 GitHub Release。

创建工作流：

```bash
git tag -a v0.1.1 -m "OneScreen 0.1.1"
git push origin main
git push origin v0.1.1
```

标签版本必须与 `gradle.properties` 完全一致；不一致时发布工作流会失败。

## 发布检查

- [ ] 主分支 CI 通过。
- [x] OpenGL 与 Vulkan 的实机冒烟测试完成。
- [x] WATERMeDIA 双版本冒烟测试通过。
- [x] LICENSE、README、CHANGELOG 和发布说明已更新。
- [x] 仓库中没有本地日志、世界存档、令牌或绝对路径。
- [ ] Release 同时包含主 JAR、源码 JAR 和 SHA-256 文件。
- [x] 已知限制在 Release 页面明确列出。

## 密钥与权限

发布工作流只使用 GitHub 自动提供的 `GITHUB_TOKEN`，不需要在仓库中保存 PAT。工作流权限应保持最小化，只有发布任务声明 `contents: write`。
