# OneScreen 详细项目计划

本计划面向 Minecraft 模组开发新人。总原则是每个阶段都能独立编译和验证，避免同时引入方块、媒体、网络和渲染错误。

预计工作量：**15–25 个工作日**。AI 可以加速样板代码和错误定位，但每个里程碑仍必须实际运行验证。

---

## 阶段 0：确定范围

**预计：0.5–1 天**

### 交付物

- `SCOPE.md`
- `AGENTS.md`
- 一句话产品定义
- 首版功能和非功能列表

### 设计

只实现一块屏幕方块、图片/视频、声音、管理员命令和 URL 同步。不加入复杂 GUI、遥控器、播放列表或第三方兼容。

### 验证

- 用三句话说明第一版做什么。
- 列出所有明确不做的事项。
- 固定 Minecraft、Fabric、Java 和 WATERMeDIA 版本。

---

## 阶段 1：建立 26.2 空项目

**预计：1 天**

### 设计

使用 Fabric 官方 26.2 模板，启用 split client source set。先不实现任何方块。

### 目标结构

```text
src/main/java/example/onescreen/OneScreen.java
src/client/java/example/onescreen/client/OneScreenClient.java
src/main/resources/fabric.mod.json
```

### 入口伪代码

```java
public final class OneScreen implements ModInitializer {
    public static final String MOD_ID = "onescreen";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing OneScreen for Minecraft 26.2");
    }
}
```

### 验证

```text
gradlew compileJava
gradlew runClient
gradlew runServer
```

客户端和服务端都应能启动，日志中能看到初始化消息。

---

## 阶段 2：实现方块与资源

**预计：1–2 天**

### 设计

只注册一个 `ScreenBlock`。方块使用 `HORIZONTAL_FACING` 状态，渲染逻辑稍后实现。

### 文件

```text
src/main/java/example/onescreen/block/ScreenBlock.java
src/main/java/example/onescreen/block/ScreenBlockEntity.java
src/main/java/example/onescreen/registry/ModBlocks.java
src/main/java/example/onescreen/registry/ModBlockEntities.java
src/main/resources/assets/onescreen/...
src/main/resources/data/onescreen/loot_table/blocks/screen.json
```

### 方块伪代码

```java
public final class ScreenBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScreenBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ScreenBlock::new);
    }
}
```

### 验证

- 物品栏可通过测试命令取得方块。
- 方块可以放置、破坏和掉落。
- 世界重启后方块存在。
- 资源和本地化均无缺失。

---

## 阶段 3：保存屏幕配置

**预计：1–2 天**

### 数据边界

方块实体只保存可跨服务端使用的基础数据：

```java
public record ScreenSettings(
        String url,
        float width,
        float height,
        float renderDistance,
        float audioDistance,
        ScreenAlignment alignment,
        ScreenFitMode fitMode,
        float rotationDegrees,
        boolean hideFrame,
        float volume,
        boolean muted,
        boolean loop,
        boolean refreshOnResume
) {}
```

不保存纹理句柄、`MediaPlayer`、播放器状态或客户端媒体缓存。

### 保存伪代码

```java
@Override
protected void saveAdditional(ValueOutput out) {
    out.putString("url", url);
    out.putFloat("width", width);
    out.putFloat("height", height);
    out.putFloat("volume", volume);
    out.putBoolean("muted", muted);
    out.putBoolean("loop", loop);
    super.saveAdditional(out);
}

@Override
protected void loadAdditional(ValueInput in) {
    super.loadAdditional(in);
    url = in.getStringOr("url", "");
    width = clamp(in.getFloatOr("width", 4.0f), 0.25f, 8.0f);
    height = clamp(in.getFloatOr("height", 2.25f), 0.25f, 8.0f);
    volume = clamp(in.getFloatOr("volume", 1.0f), 0.0f, 1.0f);
    muted = in.getBooleanOr("muted", false);
    loop = in.getBooleanOr("loop", true);
}
```

### 同步伪代码

```java
@Override
public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return saveWithoutMetadata(registries);
}

@Override
public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
}
```

### 验证

- 保存和重新加载世界后数据一致。
- 玩家进入区块时客户端获得当前配置。
- 非法数值会在加载时被夹紧。
- 服务端类没有引用 `net.minecraft.client.*`。

---

## 阶段 4：渲染固定占位屏幕

**预计：2–3 天**

### 目标

先验证位置、朝向、尺寸和包围盒，不接媒体。

### 渲染状态

```java
public final class ScreenRenderState extends BlockEntityRenderState {
    Direction facing;
    float width;
    float height;
    boolean mediaReady;
    long textureHandle;
}
```

### 提取与提交伪代码

```java
@Override
public ScreenRenderState createRenderState() {
    return new ScreenRenderState();
}

@Override
public void extractRenderState(
        ScreenBlockEntity entity,
        ScreenRenderState state,
        float tickProgress,
        Vec3 cameraPos,
        CrumblingOverlay overlay
) {
    super.extractRenderState(entity, state, tickProgress, cameraPos, overlay);
    state.facing = entity.getBlockState().getValue(ScreenBlock.FACING);
    state.width = entity.getScreenWidth();
    state.height = entity.getScreenHeight();
}

@Override
public void submit(
        ScreenRenderState state,
        PoseStack pose,
        SubmitNodeCollector collector,
        CameraRenderState camera
) {
    pose.pushPose();
    applyScreenTransform(pose, state);
    submitPlaceholderQuad(pose, collector, state.width, state.height);
    pose.popPose();
}
```

### 注意

- `submit` 中不创建媒体播放器。
- 使用后端的动态纹理抽象，避免写死 OpenGL 纹理调用。
- 大屏幕必须扩展 `getRenderBoundingBox`。
- 第一版只显示正面，背面使用暗色外壳。

### 验证

- 四个水平方向的朝向正确。
- 屏幕尺寸为 `4x2.25` 时符合 16:9。
- 屏幕超出一个方块时不会被过早剔除。
- 多个相邻屏幕不会共享错误的变换。

---

## 阶段 5：接入静态图片

**预计：2–3 天**

### 设计

创建客户端 `MediaSessionManager`。方块实体 URL 变化时替换对应媒体会话。

```java
public void reconcile(ScreenBlockEntity screen) {
    String currentUrl = screen.getUrl();
    MediaSession old = sessions.get(screen.getBlockPos());

    if (old != null && old.url().equals(currentUrl)) {
        return;
    }

    if (old != null) {
        old.close();
        sessions.remove(screen.getBlockPos());
    }

    if (currentUrl.isBlank()) {
        return;
    }

    sessions.put(screen.getBlockPos(), MediaSession.openAsync(currentUrl));
}
```

### 生命周期

```text
IDLE -> RESOLVING -> LOADING -> READY
                          \-> ERROR
任意状态 -> CLOSED
```

### 验证

- PNG、JPEG 和常见 WebP 图片可显示。
- URL 修改后旧资源关闭。
- 错误 URL 显示占位内容。
- 离开区块会关闭会话。
- 返回区块后重新加载。

---

## 阶段 6：视频与声音

**预计：3–5 天**

### 设计

使用同一个 WATERMeDIA `MediaPlayer` 处理视频和音频。客户端 tick 计算屏幕与本地玩家的距离音量。

### 音量伪代码

```java
float gain = muted ? 0.0f : volume * linearFalloff(
        player.position(),
        screenCenter,
        minDistance,
        maxDistance
);
session.setVolume(clamp(gain, 0.0f, 1.0f));
```

### 生命周期事件

- 客户端断开：关闭全部会话。
- 维度改变：关闭全部会话。
- 方块实体卸载：关闭对应会话。
- 媒体结束且不循环：停止音频并更新状态。
- URL 改变：先关闭旧会话，再打开新会话。

### 验证

- 视频画面和声音同步。
- 暂停时停止媒体输出。
- 距离衰减平滑。
- 静音立即生效。
- 同一屏幕不会出现重复音频。

---

## 阶段 7：命令与 URL 同步

**预计：1–2 天**

### 命令

```text
/screen set <x> <y> <z> <url>
/screen size <x> <y> <z> <width> <height>
/screen volume <x> <y> <z> <value>
/screen mute <x> <y> <z> <true|false>
/screen reload <x> <y> <z>
```

### 命令伪代码

```java
if (!source.hasPermission(2)) {
    source.sendFailure(Component.literal("需要管理员权限"));
    return 0;
}

BlockPos pos = getBlockPos(context, "pos");
if (!(source.getLevel().getBlockEntity(pos) instanceof ScreenBlockEntity screen)) {
    source.sendFailure(Component.literal("目标位置不存在屏幕方块"));
    return 0;
}

screen.setUrl(validateUrl(getString(context, "url")));
source.sendSuccess(() -> Component.literal("屏幕已更新"), false);
return 1;
```

### URL 策略

- 最大长度 4096。
- 第一版只允许 `http` 和 `https`。
- 拒绝空主机和未知协议。
- 服务端命令受限权限等级。
- 第一版不实现客户端 URL 编辑包，直接使用方块实体更新包。

### 验证

- 无权限玩家不能修改。
- 跟踪方块的客户端自动更新。
- 无效位置返回错误。
- URL 被保存并在重进后恢复。

---

## 阶段 8：稳定性和性能

**预计：2–4 天**

### 默认限制

```text
单客户端活动会话：4–8
屏幕最大尺寸：8x8 方块
URL 最大长度：4096
视频安全默认：1280x720
视频实验档位：1920x1080
距离 LOD：25% / 50% / 75% 阈值并带迟滞
失败重试：1 次
```

### 必须覆盖

- 快速连续修改 URL。
- 玩家快速进出区块。
- 大屏幕远距离渲染。
- 多个玩家观看同一屏幕。
- 断线、重连、维度切换。
- 媒体服务不可达和格式不支持。
- 客户端渲染线程不做阻塞请求。
- 720p 默认输出与 1080p 测试档位。
- 硬解成功、软解和硬件回退。
- `NativeImage` 视频像素批量写入。
- LOD 阈值迟滞，边界移动不抖动。

### 验证

- 连续运行 30 分钟无持续内存增长。
- 关闭屏幕后无残留声音或解码线程。
- 活动会话数量不超过上限。
- 错误日志有限流，不逐帧刷屏。

---

## 阶段 9：多人测试与发布

**预计：3–5 天**

### 验证矩阵

| 场景 | 验收 |
|---|---|
| 单人图片 | 显示正确 |
| 单人视频 | 画面和音频正确 |
| 专用服务器 | 服务端不加载客户端类 |
| 两个客户端 | URL 一致 |
| 两个客户端距离不同 | 各自音量不同 |
| 离开区块 | 媒体释放 |
| 返回区块 | 重新加载 |
| URL 修改 | 所有观看者更新 |
| 重进世界 | URL 与尺寸保存 |
| 非法 URL | 显示错误，不崩溃 |
| 快速切 URL | 无重复声音和纹理泄漏 |
| 测试结束 | 无残留解码资源 |

### 发布材料

- 版本号和 changelog。
- Fabric Loader/Fabric API/WATERMeDIA 依赖说明。
- 客户端和服务器安装说明。
- 测试图片、视频和错误 URL 示例。
- 已知限制列表。

---

## AI 协作规则

每次只处理一个阶段。要求 AI：

1. 先阅读目标文件和对应专题文档。
2. 查询 Gradle 缓存中的真实依赖源码。
3. 不凭记忆生成 26.2 方法签名。
4. 修改后运行 `compileJava`。
5. 列出必须人工启动游戏验证的项目。
6. 不顺手添加范围外功能。

每个阶段完成后再提交一次 Git commit。出现连续两次无法解释的编译错误时，缩小任务而不是让 AI 大面积重写。