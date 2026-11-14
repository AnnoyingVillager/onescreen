# 开发环境与验证

## 环境要求

- JDK 25
- Git
- 支持 Java 25 的 IDE，推荐 IntelliJ IDEA
- Minecraft Java Edition 26.2
- Fabric Loader `0.18.6` 作为构建/测试基线
- Fabric API `0.152.0+26.2`
- WATERMeDIA `3.0.0`
- 与 WATERMeDIA 发布线匹配的 WATERMeDIA Binaries

Gradle Wrapper 固定为 `9.8.2`，不需要单独安装 Gradle。项目使用 Fabric Loom `1.19.4` 和 Java 25。

## 获取与构建

```powershell
git clone https://github.com/AnnoyingVillager/onescreen.git
Set-Location onescreen
.\gradlew.bat build --no-configuration-cache
```

Linux/macOS 可使用 `./gradlew build --no-configuration-cache`。

构建输出：

- `build/libs/onescreen-0.2.0.jar`：模组 JAR。
- `build/libs/onescreen-0.2.0-sources.jar`：源码包。

当前公开最新版本仍为 `0.1.2`；源码版本已进入 `0.2.0` 开发阶段。构建前先查看 `gradle.properties` 中的实际版本。

## 本地运行

```powershell
.\gradlew.bat runClient --no-configuration-cache
```

只启动专用服务端：

```powershell
.\gradlew.bat runServer --no-configuration-cache
```

离线沙箱或已经准备好依赖缓存时可使用：

```powershell
.\gradlew.bat build --offline --no-daemon --no-configuration-cache
.\gradlew.bat runClient --offline --no-daemon --no-configuration-cache
```

`run/`、`logs/`、`build/`、`.gradle/`、崩溃报告、世界存档和下载的 Binaries 都是本地生成内容，不应提交。

## 源码集约束

- `src/main`：方块、BlockEntity、设置、命令、网络 payload、配置和服务端校验。
- `src/client`：渲染器、编辑器、纹理、WATERMeDIA 播放器、CPU/GPU 媒体路径和客户端生命周期。
- `src/main` 不得引用 `net.minecraft.client.*`。
- WATERMeDIA 版本差异必须集中在 `WaterMediaCompat`。
- 不直接引用 WATERMeDIA 玩家包中的类。
- Mixin 仅允许用于当前 Vulkan 队列外部同步契约；不得添加无关 Mixin 或 access widener。

## GPU 媒体实现边界

- OpenGL：WATERMeDIA `GLEngine` + `ExternalMediaTexture`。
- Vulkan：WATERMeDIA `VKEngine` + Minecraft `VulkanDevice` + 共享 graphics queue。
- CPU 回退：WATERMeDIA `AWTEngine` + `NativeImage` + `DynamicTexture`。
- 详细实现和所有权说明见 [MEDIA-AUDIO.md](MEDIA-AUDIO.md)。

Vulkan 中 WATERMeDIA 与 Minecraft 必须共享同一队列锁。不要改用独立 compute/transfer queue，否则会重新引入设备丢失风险。

## 修改后验证

最低命令：

```powershell
.\gradlew.bat compileJava compileClientJava
.\gradlew.bat build --no-configuration-cache
```

媒体或渲染改动还应完成：

1. 沙箱或独立测试客户端进入世界。
2. 1080p 与 4K/高分辨率片源各播放一次。
3. OpenGL 与 Vulkan 两种图形后端检查。
4. 图片、VOD、HLS 元数据和播放控制回归。
5. 暂停、停止、刷新、循环、退出世界和断线清理。
6. 检查 `format=GPU/CPU` 与 `hardwareAccel=true/false` 日志。

完整测试矩阵见 [TESTING.md](TESTING.md)。

## API 事实检查

使用陌生 Minecraft 或 WATERMeDIA API 前：

1. 找到依赖源码或反编译类。
2. 检查包名、方法、参数、返回值和线程约束。
3. 不凭记忆补全签名。
4. 找不到时缩小任务或明确报告阻塞。

本地参考源码目录通常为：

```text
E:\waterframes\_mc26.2-sources
E:\waterframes\_watermedia-sources
```

## 网络与隐私

- 文档、日志和测试数据不得包含个人访问令牌、密码、私钥、本机用户名或绝对路径。
- 不要提交 `run/`、`logs/`、崩溃报告、世界存档或下载的原生 Binaries。
- 远程媒体 URL 可能包含隐私参数；提交问题报告前应删除查询字符串和认证信息。
- 测试截图应移除聊天内容、服务器地址和其他个人信息。

## Agent 维护入口

项目主要由生成式 AI 参与实现。维护者或新的自动化 Agent 若需要执行开发任务，应先阅读 [agents/README.md](../agents/README.md) 中的项目上下文、开发日志和执行手册；本文只保留人类可复现的环境与验证方法。