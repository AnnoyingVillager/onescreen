# 版本与 API 约束

## 构建基线

| 项目 | 值 | 说明 |
|---|---|---|
| Minecraft | `26.2` | 目标版本 |
| Fabric Loader | `0.18.6` | 构建基线；模组最低声明为 `0.18.0`，兼容早期 26.2 客户端 |
| Fabric API | `0.152.0+26.2` | 构建基线；最低运行版本同为 `0.152.0+26.2` |
| Java | `25` | Minecraft 26.2 运行时要求 |
| Gradle | `9.8.2` | Wrapper 固定版本 |
| Loom | `1.19.4` | 当前实际解析版本 |
| WATERMeDIA | `3.0.0.23` | 编译基线；与 `3.0.1` 共享运行时兼容层 |
| WATERMeDIA Binaries | `3.0.0` | 配合 3.0.0.23；新版应使用配套的 `3.0.1` |

参考：

- https://meta.fabricmc.net/v2/versions/loader/26.2
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
- https://modrinth.com/mod/watermedia
- https://modrinth.com/mod/watermedia-binaries

## WATERMeDIA 双版本兼容

WATERMeDIA 3.0.0.23 使用以下公开布局：

- `org.watermedia.api.media.MediaAPI`
- `org.watermedia.api.media.MRL`
- `org.watermedia.api.media.players.MediaPlayer`
- `org.watermedia.api.media.engines.*`

WATERMeDIA 3.0.1 将玩家 API 调整为：

- `org.watermedia.api.player.PlayerAPI`
- `org.watermedia.api.player.MRL`
- `org.watermedia.api.player.MediaPlayer`
- `org.watermedia.api.player.*Engine`

因此 OneScreen 不在业务代码中直接导入任一版本的玩家类。所有版本差异由 `src/client/java/dev/onescreen/client/media/WaterMediaCompat.java` 通过公开反射方法适配：

- 启动时优先检测 `3.0.1+` 的 `PlayerAPI`。
- 未检测到新版时回退到 `3.0.0.x` 的 `MediaAPI`。
- 工厂方法、引擎、MRL 和播放器调用均经过兼容层。
- `maxSize(...)` 与新版 `scale(...)` 的差异也由兼容层处理。
- 播放进度统一使用 `time()`、`duration()`、`canSeek()`、`seek()` 与 `seekQuick()`；直播识别兼容旧版 `liveSource()` 和新版 `live()`。
- HLS 片段时长优先通过新版 `org.watermedia.tools.M3UTool` 或旧版 `org.watermedia.tools.MPEGTool` 的公开 `fetch`/`Media` 记录反射读取；若旧工具因运行时模块差异不可用，则使用不依赖 WATERMeDIA 类布局的 HTTP 播放列表后备解析。两者都失败时仅禁用片段进度，不影响播放。

对 `3.0.0.23` 仅提供初版兼容，未来可能因维护成本移除。新的安装和开发验证应优先使用 `3.0.1`。

## 26.2 关键变化

- 26.x 使用 Java 25。
- 26.x 官方 Minecraft 已去混淆，不再沿用旧的官方映射叠加流程。
- `ResourceLocation` 已更名为 `Identifier`。
- 方块实体渲染采用渲染状态设计和 `submit` 提交流程。
- GUI、字体和渲染类型均有大幅变化。
- 方块实体持久化使用 `ValueInput` / `ValueOutput`。
- 26.2 资源包格式为 `88`，数据包格式为 `107.1`。
- WATERMeDIA 3 API 与 2.x API 不兼容。

## Loom 固定版本

项目已固定 `fabric-loom:1.19.4`。升级 Loom、Loader 或 Fabric API 前必须单独建立兼容性任务，不能与功能修改混合。

## 依赖升级规则

- 每次只升级一个核心依赖。
- 先升级 Minecraft/Loom，再升级 Fabric API，最后升级 WATERMeDIA。
- 升级后先完成占位渲染，再恢复媒体。
- 不自动接受破坏性 API 替换。
- 修改 WATERMeDIA 版本时同时验证 3.0.0.23 回退路径和 3.0.1 新版路径。
- 将不兼容项记录到 `DECISIONS.md` 和 `RISKS.md`。

## 媒体兼容要求

- 客户端必须安装 WATERMeDIA 和匹配 Binaries。
- `fabric.mod.json` 声明 WATERMeDIA 依赖。
- 不在模组 JAR 中嵌入 Binaries。
- 不引用旧 VLC API、`ImageAPI`、`ImageCache` 或 `ImageRenderer`。
- 不为特定 WATERMeDIA 小版本添加不可回退的直接编译链接；版本差异必须收敛在兼容层。
