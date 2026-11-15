# 版本与 API 约束

## 构建基线

| 项目 | 当前值 | 说明 |
|---|---|---|
| Minecraft | `26.2` | 目标客户端和专用服务器版本 |
| Java | `25` | Minecraft 26.2 运行时要求 |
| Fabric Loader | `0.18.6` | 构建与沙箱测试基线；`fabric.mod.json` 最低声明为 `0.18.0` |
| Fabric API | `0.152.0+26.2` | 构建基线和最低运行版本 |
| Gradle | `9.8.2` | Gradle Wrapper 固定版本 |
| Loom | `1.19.4` | 当前实际构建插件版本 |
| WATERMeDIA | `3.0.0` | 0.2 正式基线 |
| WATERMeDIA Binaries | `3.0.0` | 与 WATERMeDIA 发布线配套 |
| OneScreen | `0.2.1` | 当前已发布版本 |

参考：

- [Fabric Loader 版本清单](https://meta.fabricmc.net/v2/versions/loader/26.2)
- [Fabric API Maven](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml)
- [WATERMeDIA - Modrinth](https://modrinth.com/mod/watermedia)
- [WATERMeDIA Binaries - Modrinth](https://modrinth.com/mod/watermedia-binaries)
- [WATERMeDIA Binaries - CurseForge](https://www.curseforge.com/minecraft/mc-mods/watermedia-binaries)

## WATERMeDIA 版本语义

WATERMeDIA 对 `3.0.0.x` 测试版的排序不同于普通语义化版本，OneScreen 在 `WaterMediaVersion` 中显式处理：

```text
3.0.0.23 < 3.0.0 < 3.0.1 < 3.0.2
```

规则：

- `3.0.0`：正式基线。
- `3.0.0.x`：正式基线之前的测试版本。
- `3.0.x`（x > 0）：高于所有 `3.0.0.x` 的后续正式版本。
- `3.1.0`：当前不支持。
- 最低声明版本：`3.0.0.0`。

`fabric.mod.json` 当前声明：

```json
{
  "depends": {
    "watermedia": ">=3.0.0.0 <3.1.0",
    "watermedia_binaries": ">=3.0.0"
  }
}
```

Fabric Loader 无法直接表达上述特殊排序，因此加载器只负责范围筛选，客户端启动后还会通过 `WaterMediaVersion` 做二次判断。

## WATERMeDIA API 兼容层

WATERMeDIA 3.x 的不同发布线可能使用不同公开包布局：

- 旧测试线：`org.watermedia.api.media.*`
- 当前正式线：`org.watermedia.api.player.*`

业务代码不得直接 import 其中任一玩家 API，以避免一个 WATERMeDIA 版本下发生 `NoClassDefFoundError`。所有版本差异集中在：

```text
src/client/java/dev/onescreen/client/media/WaterMediaCompat.java
src/client/java/dev/onescreen/client/media/WaterMediaVersion.java
```

兼容层负责：

- MRL 创建。
- AWT、OpenGL、Vulkan、JS 音频和 Headless 引擎工厂。
- Player 创建。
- `width()`、`height()`、`texture()`、`format()`、`image()`、`uploadCount()` 等引擎接口。
- `time()`、`duration()`、`canSeek()`、`seek()`、`seekQuick()`、`live()` 等播放器接口。
- HLS 元数据工具和旧版播放列表工具。

当前正式测试目标为 WATERMeDIA `3.0.0` 及后续 `3.0.x`。`3.0.0.x` 兼容代码属于 best-effort，不应成为新功能的唯一验证环境，也不应继续新增对旧包布局的直接引用。

## 0.2 GPU 纹理 API 边界

### OpenGL

- 通过 WATERMeDIA `GLEngine` 获取动态 GL texture id。
- `ExternalMediaTexture` 提供 Minecraft `GlTexture`/`GlTextureView` 外观。
- 不在业务代码直接调用 `GL11` 绘制屏幕。

### Vulkan

- 通过 `WaterMediaVulkanContext` 把 Minecraft `VulkanDevice` 适配为 WATERMeDIA `VKContext`。
- WATERMeDIA 必须使用 Minecraft `graphicsQueue()`。
- `VulkanQueueSynchronization` 与三个窄范围 Mixin 串行化：
  - `vkQueueSubmit2KHR`
  - `vkQueueWaitIdle`
  - `vkQueuePresentKHR`
- 禁止改成独立 compute/transfer queue，除非同时实现并验证完整的跨队列 semaphore 与可见性方案。
- `ExternalMediaTexture` 只包装 WATERMeDIA 返回的当前 `VkImageView`，不得销毁其所有权资源。

## 26.2 API 注意事项

- 26.x 使用 Java 25。
- Minecraft 26.x 已采用去混淆源码流程，不再沿用旧 Forge/Fabric 映射叠加假设。
- `ResourceLocation` 已更名为 `Identifier`。
- BlockEntity 持久化使用 `ValueInput` / `ValueOutput`。
- BlockEntityRenderer 使用 render-state extraction + `submit`。
- GUI、字体、纹理和渲染提交 API 与旧版本差异较大。
- 26.2 资源包格式为 `88`，数据包格式为 `107.1`。
- WATERMeDIA 3 与 WATERMeDIA 2 API 不兼容；不要重新引用 `ImageAPI`、`ImageCache`、`ImageRenderer` 或旧 VLC `VideoPlayer`。

## 依赖升级规则

1. 一次只升级一个核心依赖。
2. 先确认真实产品版本、配套 Binaries 和 API 布局。
3. 检查 Gradle 缓存或解压后的依赖源码，不凭记忆推测签名。
4. 先通过 `compileJava`/`build`，再运行沙箱客户端。
5. 媒体改动至少验证 OpenGL 和 Vulkan。
6. 至少验证 1080p 与一个更高分辨率片源。
7. 记录 `format=GPU/CPU` 和 `hardwareAccel=true/false`，两者不是同一指标。
8. 将兼容性变化同步到 `DECISIONS.md`、`RISKS.md` 和 `TESTING.md`。

## 媒体安装要求

- 客户端必须安装 WATERMeDIA 和匹配的 WATERMeDIA Binaries。
- OneScreen JAR 不包含 WATERMeDIA Binaries。
- 专用服务器需要安装 OneScreen，但不加载客户端媒体引擎。
- 不保证客户端与服务端 WATERMeDIA 原生库版本一致；屏幕 URL 和设置由服务端同步，媒体由各客户端本地播放。