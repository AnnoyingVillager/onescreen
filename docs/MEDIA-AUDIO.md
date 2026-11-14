# 媒体与音频设计

## 依赖

- WATERMeDIA 3.0.1
- 与 WATERMeDIA 匹配的 Binaries
- 客户端必须安装两者

高分辨率、硬件解码、`scale` 和 `LodLevel` 的边界见 [高分辨率与硬件解码评估](HIGH-RESOLUTION-MEDIA.md)。修改视频处理前必须阅读 [视频处理修改交接指南](VIDEO-HANDLING-AGENT-GUIDE.md)。

不使用：

- WATERMeDIA 2.x 的 `ImageAPI`
- `ImageCache`
- `ImageRenderer`
- 旧 VLC `VideoPlayer`
- Minecraft 私有 `SoundEngine` / `Channel` / `ChannelAccess`

## 统一媒体入口

图片、视频和视频声音统一通过 WATERMeDIA 3 的 `MediaPlayer` 流程。上层只关心：

- 解析 URL。
- 得到可播放媒体。
- 获取动态纹理。
- 控制播放、暂停、音量和循环。
- 在关闭时释放资源。

## M6 实际实现

- 图片：`HeadlessGFXEngine` 单帧上传到 Minecraft `DynamicTexture`。
- 视频：`AWTEngine` 通过软件转换接收 packed BGRA/ARGB，客户端渲染线程仅在帧序号变化时更新 `DynamicTexture`。
- 音频：`JSEngine` 播放解码后的 PCM，不依赖 OpenGL 后端的当前 OpenAL context。
- 安全默认输出上限：1280x720；1920x1080 只能作为实机测试档位，未通过压测前不得变成默认值。
- 渲染层只持有 Minecraft `Identifier` / `DynamicTexture`，不接触 OpenGL 或 Vulkan 原生纹理句柄。
- 单机暂停菜单会暂停和恢复 `MediaPlayer`；多人游戏不跟随本地菜单暂停。
- 设置界面通过 `time()` / `duration()` / `canSeek()` 显示播放进度；已知时长媒体使用 `seekQuick()` 拖动预览，并在松开时用 `seek()` 做一次精确定位。
- 直播流通过 `live()`（旧版兼容 `liveSource()`）识别，并保持不可 seek；HLS 片段进度优先从 WATERMeDIA 对应版本的公开播放列表工具异步读取目标片段时长，并用独立 HTTP 播放列表解析作为旧版/工具不可用时的后备。读取失败时只显示“正在获取”或不可用状态，不影响播放。
- 可选的“暂停/配置结束后刷新直播”默认关闭。开启后，单机游戏从暂停恢复或屏幕设置更新时会释放并重建同一 URL 的播放器，让直播重新从实时边缘开始；已知时长视频不会被强制重开。

## 状态机

```text
IDLE
  -> RESOLVING
  -> LOADING
  -> READY
  -> PLAYING
  -> PAUSED / ENDED
任意状态
  -> ERROR
  -> CLOSED
```

状态变化应发生在客户端主线程。媒体解码可以发生在 WATERMeDIA 的后台线程中。

## MediaSession

```java
public final class MediaSession {
    private final ScreenKey key;
    private final String url;

    private volatile MediaState state = MediaState.IDLE;
    private DynamicTexture texture;
    private MediaPlayer player;
    private volatile Throwable lastError;
    private volatile boolean closed;

    public void resolveAsync() { ... }
    public void tick(ScreenSettings settings, double distanceGain, boolean paused) { ... }
    public MediaSnapshot snapshot() { ... }
    public void close() { ... }
}
```

## 会话打开伪代码

```java
public void reconcile(ScreenBlockEntity screen) {
    ScreenKey key = ScreenKey.of(screen);
    String url = screen.getUrl();

    MediaSession current = sessions.get(key);
    if (current != null && current.url().equals(url)) {
        return;
    }

    if (current != null) {
        current.close();
        sessions.remove(key);
    }

    if (url.isBlank()) {
        return;
    }

    if (sessions.size() >= MAX_ACTIVE_SESSIONS) {
        queueOrReject(key, url);
        return;
    }

    MediaSession next = MediaSession.openAsync(key, url);
    sessions.put(key, next);
}
```

## 关闭条件

所有情况都必须关闭：

- BlockEntity unload。
- 玩家离开服务器。
- 客户端关闭。
- 维度改变。
- URL 改变。
- 方块被移除。
- 媒体句柄和播放器销毁失败。
- 达到并发上限后被淘汰。

## 播放进度与直播追帧

- 进度查询全部走 `WaterMediaCompat`，业务层不直接链接任一 WATERMeDIA 玩家包布局。
- 已知时长：进度条范围为 `0..duration()`，拖动时调用 `seekQuick(target)`，松开时调用 `seek(target)`。
- 直播：播放器报告 `live=true` 且 `canSeek=false`，进度条只读。若播放列表可解析出片段时长，显示 `floorMod(time, segmentDuration) / segmentDuration`；这是片段级近似显示，不声称能定位到 HLS 序号。
- 自动追帧只对直播流生效，并带 1 秒重连防抖。配置更新发生在暂停期间时会延迟到恢复后执行；VOD 和图片不会触发追帧。
- HLS 播放列表探测在守护线程执行，不阻塞客户端 tick、GUI 渲染或首帧上传。

## 音量

第一版使用客户端距离衰减：

```java
float distanceGain(double distance, double minDistance, double maxDistance) {
    if (distance <= minDistance) {
        return 1.0f;
    }
    if (distance >= maxDistance) {
        return 0.0f;
    }
    double t = (distance - minDistance) / (maxDistance - minDistance);
    return (float) (1.0 - t);
}

float outputGain(boolean muted, float masterVolume, float distanceGain) {
    return muted ? 0.0f : clamp(masterVolume * distanceGain, 0.0f, 1.0f);
}
```

默认值建议：

- 最小距离 `2` 格。
- 最大距离 `48` 格。
- 超出最大距离暂停声音，但视频可继续显示。

## 多客户端

- 每个客户端独立创建媒体会话。
- 服务器只同步 URL 和音量配置。
- 玩家距离不同，听到的音量不同。
- 一个玩家静音不影响其他玩家。

## 资源限制

```text
默认最大活动会话：4–8
超限策略：暂停新屏幕声音并要求手动 reload，或排队加载
并发解码：由 WATERMeDIA 控制，不在渲染线程另建线程池
自动重试：最多一次
```

## 失败处理

- 不支持的格式显示错误占位图。
- 网络不可达不阻塞客户端。
- 同一错误不逐帧写日志。
- 连续失败不无限重试。
- 清理失败仍需从活动 map 中移除，防止继续渲染和发声。

## 验证

- 静态 PNG/JPEG。
- MP4（已验证）。
- HLS/M3U8：WATERMeDIA 3.0.1 支持主播放列表、媒体播放列表、直播/VOD 探测和 FFmpeg HLS 解复用，仍待 OneScreen 实机回归。
- 多频道 IPTV：当前 OneScreen 只播放第一个源。
- 有视频无音频。
- 有音频无视频。
- 超长 URL。
- 重定向 URL。
- 无效 URL。
- 加载过程中修改 URL。
- 加载过程中卸载区块。
