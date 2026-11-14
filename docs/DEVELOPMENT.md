# 开发环境与验证

## 环境要求

- JDK 25
- Git
- 支持 Java 25 的 IDE，推荐 IntelliJ IDEA
- Minecraft 26.2 测试环境
- WATERMeDIA `3.0.1` 与 WATERMeDIA Binaries `3.0.1` 用于新版实机测试
- WATERMeDIA `3.0.0.23` 与 WATERMeDIA Binaries `3.0.0.6` 仅用于旧版兼容回归

Gradle Wrapper 固定为 `9.8.2`，不需要单独安装 Gradle。

## 获取与构建

```bash
git clone https://github.com/AnnoyingVillager/onescreen.git
cd onescreen
./gradlew build --no-configuration-cache
```

Windows：

```powershell
.\gradlew.bat build --no-configuration-cache
```

构建输出位于 `build/libs/`：

- `onescreen-0.1.2.jar`：正式模组文件。
- `onescreen-0.1.2-sources.jar`：源码包。

CI 只接受 JDK 25。使用 Java 21 会在 Fabric Loom 配置阶段失败。

## 本地运行

```bash
./gradlew runClient
```

仅测试专用服务端启动：

```bash
./gradlew runServer
```

默认构建使用 WATERMeDIA `3.0.0.23` + Binaries `3.0.0.6`。旧版 Binaries 不在 Modrinth Maven 中，因此由 CurseMaven 的 File ID `8519058` 提供。验证新版组合时显式覆盖两个属性：

```powershell
.\gradlew.bat runClient `
  -Pwatermedia_version=3.0.1 `
  -Pwatermedia_binaries_version=3.0.1
```

`run/`、`logs/`、`build/` 和 `.gradle/` 都是本地生成目录，不应提交。

## 源码集约束

- `src/main`：公共代码、方块、方块实体、命令、网络与配置。
- `src/client`：渲染、纹理、媒体播放器、音频和客户端生命周期。
- `src/main` 不得引用 `net.minecraft.client.*`。
- WATERMeDIA 版本差异必须集中在 `WaterMediaCompat`。
- 禁止 Mixin、access widener 和 Minecraft 私有音频/渲染类。

## 修改后验证

最低要求：

```bash
./gradlew compileJava compileClientJava
./gradlew build --no-configuration-cache
```

涉及媒体或渲染的修改还应完成：

1. WATERMeDIA `3.0.1` 的客户端实机播放。
2. OpenGL 与 Vulkan 两种图形后端检查。
3. 图片、VOD、HLS 元数据和播放控制回归。
4. 退出世界和关闭游戏时确认媒体会话已释放。
5. 只修改 `WaterMediaCompat` 之外的业务代码时，仍需运行双版本 API 冒烟测试。

测试矩阵和已知未覆盖项目见 [TESTING.md](TESTING.md)。

## 网络与隐私

- 文档、日志和测试数据不得包含个人访问令牌、密码、私钥、本机用户名或绝对路径。
- 不要提交 `run/`、`logs/`、崩溃报告、世界存档或下载的原生 Binaries。
- 远程媒体 URL 可能包含隐私参数，提交问题报告前应删去查询字符串和认证信息。
- 测试截图应移除聊天内容、服务器地址和其他个人信息。

## AI 协作

项目主要由生成式 AI 参与实现。提交大型改动前请阅读 [AI-WORKFLOW.md](AI-WORKFLOW.md)，并使用真实依赖源码或 `javap` 验证不熟悉的 API 签名。
