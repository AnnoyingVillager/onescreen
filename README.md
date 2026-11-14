# OneScreen

[English](README.en.md) | **简体中文**

OneScreen 是一个面向 **Minecraft Java Edition 26.2 / Fabric** 的屏幕模组，可在世界中放置薄型画框并播放图片、视频和声音。

> [!IMPORTANT]
> **本项目是一个由生成式 AI 主导完成的项目。** 它不是 Mojang、Fabric、WATERMeDIA 或其作者的官方项目，也不是由成熟商业团队维护的产品。代码、文档、构建配置和排错记录主要由生成式 AI 生成与整理，人类用户提供需求、运行环境和实机反馈。使用前请备份世界；建议先在测试世界中验证。

> 当前状态：`0.1.1` 公开测试版本。核心屏幕、编辑界面、图片/视频/声音、播放进度与直播追帧、六向朝向、命令、网络同步、专属创意栏及 OpenGL/Vulkan 渲染路径已实现；后续版本将继续补充直播流、压力和多人测试。

> 下载：[GitHub Releases](https://github.com/AnnoyingVillager/onescreen/releases) · 源码与问题反馈：[GitHub 仓库](https://github.com/AnnoyingVillager/onescreen)

## 功能概览

- 1×1×1/16 的薄型屏幕画框，可贴附在任意方块的六个表面。
- 点击画框打开设置界面，可配置 URL、画面尺寸、渲染距离、声音传播距离、九宫格相对位置、平面旋转角度、隐藏画框材质、循环播放和直播追帧。
- 支持 PNG、JPEG、WebP 图片，以及 MP4、HLS/M3U8 等 WATERMeDIA 支持的媒体；多频道 IPTV 列表目前只播放第一个频道。
- 支持画面适应、拉伸、平铺、填充和居中五种比例模式；支持音量、静音、循环、暂停、停止、手动刷新和调试材质。
- 屏幕可在自身平面内按连续角度旋转；可选择隐藏静态画框材质，仅保留媒体画面与右键编辑区域。
- 设置界面包含播放进度条：已知时长媒体可拖动定位；直播流不可拖动，只显示当前 HLS 片段的目标时长进度。
- 可选“暂停/配置结束后刷新直播”：默认关闭，开启后在游戏暂停恢复或屏幕配置更新时重连直播流并追到实时边缘。
- 服务端权威保存屏幕设置，并向客户端同步。
- 离开区块、切换维度或断开连接时关闭媒体会话。
- 独立 `OneScreen` 创意栏，不混入原版建筑方块分类。
- 渲染路径不绑定单一 OpenGL API，已覆盖 Minecraft 26.2 的 OpenGL 与 Vulkan 后端。

## 兼容版本

| 组件 | 编译/测试基线 | 运行兼容范围 |
|---|---|---|
| Minecraft | 26.2 | 26.2 |
| Java | 25 | 25 |
| Fabric Loader | 0.18.6 | 最低 0.18.0；较新的 0.18.x/0.19+/0.20+ 可按 Fabric 兼容规则运行 |
| Fabric API | 0.152.0+26.2 | 最低 0.152.0+26.2 |
| WATERMeDIA | 3.0.0.23 | 3.0.0.23 与 3.0.1 |
| WATERMeDIA Binaries | 3.0.0.6 | 早期客户端配套 3.0.0.23；新版客户端使用 3.0.1 |

WATERMeDIA 3.0.0.23 与 3.0.1 使用不同的公开 Java 包布局。OneScreen 通过客户端兼容层自动识别两套 API，因此同一个 OneScreen JAR 无需针对 WATERMeDIA 小版本重复替换。

> **版本支持说明：** 对 WATERMeDIA `3.0.0.23` 仅提供初版兼容，目的是让早期 26.2 客户端能够启动和播放媒体。后续版本可能根据 API 维护成本移除对该旧版本的兼容性；新安装建议优先使用 `3.0.1`。

### 推荐组合

| 使用场景 | WATERMeDIA | WATERMeDIA Binaries |
|---|---|---|
| 早期 26.2 客户端 | 3.0.0.23 | [3.0.0.6](https://www.curseforge.com/minecraft/mc-mods/watermedia-binaries/files/8519058) |
| 当前 26.2 客户端 | 3.0.1 | 3.0.1 |

请严格按照上表配套使用。经核对，WATERMeDIA `3.0.0.23` 对应 WATERMeDIA Binaries `3.0.0.6`；混用不配套的版本可能导致媒体引擎无法初始化。

## 必需前置

| 前置 | 用途 | CurseForge | Modrinth |
|---|---|---|---|
| Fabric API | Fabric 公共 API | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api) | [Modrinth](https://modrinth.com/mod/fabric-api) |
| WATERMeDIA | 图片、视频、声音解码接口 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/watermedia) | [Modrinth](https://modrinth.com/mod/watermedia) |
| WATERMeDIA Binaries | WATERMeDIA 所需原生媒体库 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/watermedia-binaries) | [Modrinth](https://modrinth.com/mod/watermedia-binaries) |

此外还需要：

- [Fabric Loader 安装器](https://fabricmc.net/use/installer/)
- [Java 25](https://adoptium.net/temurin/releases/?version=25)
- 正版或对应启动器提供的 Minecraft Java Edition 26.2

## 安装

1. 为 Minecraft 26.2 安装 Fabric Loader。
2. 将 Fabric API 放入 `mods` 文件夹。
3. 根据上表选择一组 WATERMeDIA 与 Binaries，并放入 `mods` 文件夹。
4. 将 OneScreen JAR 放入 `mods` 文件夹。
5. 启动游戏，在创意模式中选择独立的 `OneScreen` 创意栏取得屏幕画框。

单人游戏只需在客户端安装。多人游戏应确保服务端与所有需要看到屏幕的客户端都安装 OneScreen；WATERMeDIA 及其 Binaries 是客户端媒体前置。

## 配置文件

首次启动会生成 `config/onescreen.json`：

```json
{
  "maxRenderDistance": 256.0,
  "maxAudioDistance": 128.0
}
```

单机集成服务端和专用服务器都可以提高这两个上限，分别允许到 `4096` 格和 `1024` 格。修改后重启游戏或服务端；编辑器打开时会使用服务端当前生效的上限。实际可见距离仍受客户端区块渲染距离和服务端区块加载范围限制。详细说明见 [配置文件](docs/CONFIGURATION.md)。

## 使用

1. 从 `OneScreen` 创意栏取出屏幕画框并贴在方块表面。
2. 点击画框打开设置界面。
3. 填写 HTTP/HTTPS 媒体 URL，并设置尺寸、渲染距离、声音传播距离、画面位置、画面适应模式、循环播放和可选的直播追帧。
4. 保存后，服务端会同步设置，客户端异步加载媒体。

常用命令：

```text
/screen set <url>
/screen size <width> <height>
/screen renderdistance <blocks>
/screen audiodistance <blocks>
/screen align <top_left|up|top_right|left|center|right|bottom_left|down|bottom_right>
/screen fitmode <fit|stretch|tile|fill|center>
/screen rotation <pos> <-180..180>
/screen hideframe <pos> <true|false>
/screen volume <0.0-1.0>
/screen mute <true|false>
/screen loop <true|false>
/screen reload
/screen info
/screen config debug <on|off>
```

完整参数见 [命令参考](docs/COMMAND-REFERENCE.md)。

## 从源码构建

需要 JDK 25。构建命令：

```bash
./gradlew build --no-configuration-cache
```

Windows 使用：

```powershell
.\gradlew.bat build --no-configuration-cache
```

构建产物位于 `build/libs/`。GitHub Actions 会在每次推送和拉取请求时构建 JAR；推送 `v*` 标签时会自动创建 GitHub Release 并附加 JAR。

## 已知限制

- 仅接受 HTTP/HTTPS 媒体 URL。
- 不包含播放列表、遥控器、搜索、上传或多方块自动拼接系统。
- 远程媒体的可用性和格式支持取决于 WATERMeDIA、网络环境及解码器。
- `fit`、`fill` 和 `stretch` 都以整个屏幕为边界；`tile` 与 `center` 使用媒体长边 1 格作为基准尺寸。
- HLS 直播流不能在进度条上拖动；进度条只显示当前片段的目标时长进度，片段边界基于播放列表 `EXT-X-TARGETDURATION`/片段时长估算。开启直播追帧后，暂停恢复或配置更新会重新打开播放器，可能产生短暂黑屏、缓冲或音频中断。
- 直播追帧默认关闭，以避免每次打开配置或暂停恢复都增加一次重连；该选项只作用于直播流，不会把已知时长的视频跳回开头。
- 首版仍以 CPU 帧复制到 Minecraft 动态纹理为主要视频路径，4K/8K 高帧率播放不是默认性能承诺。
- 生成式 AI 主导的项目可能存在未被现有测试覆盖的边界问题；遇到问题时请保留 `latest.log`、WATERMeDIA 日志和复现步骤。

## 文档

- [范围与验收标准](docs/SCOPE.md)
- [项目计划](docs/PROJECT_PLAN.md)
- [系统架构](docs/ARCHITECTURE.md)
- [版本与 API 约束](docs/API-VERSIONS.md)
- [渲染设计](docs/RENDERING.md)
- [媒体与音频](docs/MEDIA-AUDIO.md)
- [视频处理交接指南](docs/VIDEO-HANDLING-AGENT-GUIDE.md)
- [网络同步与命令](docs/NETWORK-SYNC.md)
- [配置文件](docs/CONFIGURATION.md)
- [命令参考](docs/COMMAND-REFERENCE.md)
- [测试计划](docs/TESTING.md)
- [里程碑](docs/MILESTONES.md)
- [架构决策记录](docs/DECISIONS.md)
- [风险清单](docs/RISKS.md)
- [AI 协作规范](docs/AI-WORKFLOW.md)
- [开发环境与验证](docs/DEVELOPMENT.md)
- [发布流程](docs/PUBLISHING.md)
- [更新日志](CHANGELOG.md)

## 许可证

本项目使用 [MIT License](LICENSE)。第三方模组、Minecraft 资源和依赖项仍分别受其各自许可证约束。
