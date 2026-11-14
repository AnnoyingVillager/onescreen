# 测试计划

## 原则

每个里程碑至少完成：

1. 编译。
2. 对应的客户端或服务端启动。
3. 该阶段的验收场景。
4. Diff 审查。
5. 独立 Git 提交。

不在未完成启动验证的代码上继续叠加下一个子系统。

## 基础命令

```text
gradlew compileJava
gradlew runClient
gradlew runServer
```

发布前如有单元测试和 GameTest，再增加：

```text
gradlew test
gradlew runGameTestServer
```

## 阶段测试

### 项目骨架

- [ ] 客户端进入主菜单。
- [ ] 专用服务器加载完成。
- [ ] `fabric.mod.json` 依赖正确。
- [ ] `src/main` 无客户端 import。

### 方块

- [ ] 方块和物品注册。
- [ ] 放置、破坏、掉落。
- [ ] 世界重启后仍能加载。
- [ ] 模型、纹理、语言、loot table 无缺失。

- [x] 屏幕画框显示在专属 OneScreen 创意栏，且未注入原版建筑方块栏。

### 数据保存

- [ ] URL 保存。
- [ ] 尺寸保存。
- [ ] 音量与静音保存。
- [ ] 非法存档值被夹紧。
- [ ] BlockEntity update tag/packet 正常。

### 占位渲染

- [x] 四向水平朝向正确。
- [x] `facing=up/down` 的模型、碰撞形状与媒体面位于同一薄面。
- [x] Vulkan 1.4.329 下六向动态媒体和九宫格偏移正常。
- [x] 上下宽面贴图位于正确一侧，四个窄边贴图完整。
- [x] 无媒体且 debug 关闭时显示区域透明，debug 开启时显示占位材质。
- [x] 纹理方向正确。
- [x] 宽高比例正确。
- [x] 每屏渲染距离剔除正常。
- [x] 多屏幕不会互相污染 PoseStack。

### 静态图片

- [x] PNG。
- [x] JPEG。
- [x] WebP。
- [ ] 无 URL 显示占位。
- [ ] 错误格式显示错误占位。

### 视频和声音

- [x] MP4 画面。
- [x] MP4 声音（WATERMeDIA 确认音频轨道启动）。
- [x] 暂停和继续。
- [x] 静音。
- [x] 距离衰减（2–48 格线性）。
- [x] 非循环媒体结束。
- [x] 循环媒体行为。
- [x] OpenGL：视频逐帧更新、暂停 `PAUSED`、继续 `PLAYING`。
- [x] Vulkan：视频逐帧更新、循环和运行时配置更新。
- [x] 视频结束后画面保留最后一帧。
- [x] OpenGL 与 Vulkan 编辑器的播放、暂停、停止按钮可用；停止后重新播放进入 `PLAYING`。

### 播放进度、循环与直播追帧

- [ ] 右键菜单可切换循环播放，保存后立即生效并可重进世界恢复。
- [ ] 已知时长视频显示总时长，拖动进度条后画面与声音定位到目标位置。
- [ ] 图片、音频无时长或加载中时进度控件显示不可用/加载中且不崩溃。
- [ ] 直播流进度条不可拖动，并能显示当前 HLS 片段时长进度；元数据获取失败时安全降级。
- [ ] 自动刷新直播默认关闭；关闭时暂停恢复不重建播放器。
- [ ] 开启后，单机暂停菜单恢复时直播重新追到实时边缘；VOD 不重开。
- [ ] 开启后，修改 URL、尺寸、距离、对齐、适配、循环或追帧设置会按需刷新直播。
- [ ] 1 秒内连续配置更新不会造成重连风暴。
- [ ] OpenGL 与 Vulkan 下均验证进度滑块、循环勾选和直播追帧。

### 手动刷新、旋转与隐藏画框

- [x] 停止播放后 `↻` 按钮仍可用。
- [x] `↻` 图标放大后仍完整显示在播放控件左侧。
- [x] 停止播放会释放媒体纹理，退出编辑器后不再显示视频或图片画面；再次播放可恢复画面。
- [x] 点击暂停后退出编辑器，媒体保持 `PAUSED`，不会随游戏菜单关闭而恢复播放。
- [x] 手动刷新由服务端递增 `loadRevision`，客户端关闭旧会话并重建同一 URL。
- [x] 刷新后点击播放可从 `LOADING` 进入 `PLAYING`。
- [x] `-180°` 到 `180°` 数值可保存；旧存档缺少字段时回退到 `0°`。
- [x] 旋转后媒体四边形、九宫格偏移和声音距离中心使用一致几何。
- [x] 水平与上下朝向使用同一旋转平面。
- [x] 隐藏画框默认关闭；开启后静态模型消失，动态媒体保留。
- [x] 隐藏画框后仍可通过原点击区域右键打开编辑器。
- [x] 旋转与隐藏状态在退出并重进世界后恢复。
- [x] `/screen rotation`、`/screen hideframe` 与 `/screen info` 实机可用。
- [x] WATERMeDIA 3.0.1：OpenGL 3.3 与 Vulkan 1.4.329 均完成上述回归。
- [x] 编辑面板在 856×512 下完整显示新增字段、复选框和四个播放控制按钮。

### 画面适应模式

- [x] GUI 可选择适应、拉伸、平铺、填充和居中，并通过 payload 保存。
- [x] `/screen fitmode <pos> <mode>` 可修改模式，`/screen info` 可查看当前模式。
- [x] 旧世界缺少 `fitMode` 字段时回退到 `fit`。
- [ ] 16:9、4:3、竖屏和超宽屏媒体分别验证五种模式。
- [ ] 平铺模式的 UV 重复、边缘接缝和透明像素。
- [ ] 六向屏幕，尤其 `facing=up/down` 的比例和 UV 方向。
- [ ] OpenGL 与 Vulkan 下的运行时切换和重进世界持久化。

### 距离配置

- [x] 默认 `config/onescreen.json` 结构和 `256`/`128` 默认上限已接入编译路径。
- [x] GUI 会通过编辑 payload 接收服务端当前距离上限。
- [x] 指令和编辑 payload 使用配置上限而不是固定常量。
- [ ] 单人游戏首次启动生成配置文件。
- [ ] 专用服务器首次启动生成配置文件。
- [ ] 修改为更远上限并重启后，GUI 与 `/screen renderdistance`、`/screen audiodistance` 均接受新值。
- [ ] OpenGL 与 Vulkan 下验证远距离屏幕的渲染剔除、声音衰减和性能。

### 高分辨率与硬件解码

- [ ] 默认仍为 1280x720。
- [ ] 1920x1080 仅作为测试档位逐设备验收。
- [ ] 720p60、1080p30、1080p60 分别记录 CPU、帧时延和内存。
- [ ] H.264 必测；HEVC、AV1 按设备能力记录成功或失败。
- [ ] `hardwareAccel=true` 时记录 WATERMeDIA 硬解初始化或软件回退日志。
- [ ] `hardwareAccel=false` 时视频仍能播放。
- [ ] 距离跨越 LOD 阈值时只切换一次，不在边界反复重建纹理。
- [ ] 竖屏、超宽屏和正方形视频保持宽高比。
- [ ] 4K/8K 只作为实验数据记录，不进入默认支持承诺。

### URL 同步与命令

- [x] 命令权限（修改命令使用 `LEVEL_GAMEMASTERS`）。
- [x] 服务端到客户端更新。
- [x] 后进入区块/重进世界同步。
- [x] 重进世界恢复。
- [x] 相同 URL 在普通配置更新时不重复加载。
- [x] `/screen set|size|volume|mute|loop|reload|info`。
- [x] `/screen renderdistance|audiodistance|align` 即时生效。
- [x] URL 仅允许 HTTP/HTTPS，拒绝空值、超长、控制字符和无主机 URL。
- [x] 非屏幕目标返回明确错误。
- [x] `reload` 修订触发同一 URL 关闭并重新加载。

### 生命周期

- [x] 区块卸载关闭会话。
- [x] 返回区块重新加载。
- [x] 维度切换关闭会话。
- [x] 断线关闭会话。
- [ ] 快速连续修改 URL 无重复声音。
- [ ] 方块被破坏后不残留会话。

## WATERMeDIA 双版本兼容

- [x] `WATERMeDIA 3.0.0.23`：`compileJava`、`compileClientJava` 和 `build` 通过。
- [x] `WATERMeDIA 3.0.0.23`：运行时识别为 `WATERMeDIA 3.0.0.x MediaAPI`。
- [x] `WATERMeDIA 3.0.1`：运行时识别为 `WATERMeDIA 3.0.1+ PlayerAPI`。
- [x] `WATERMeDIA Binaries 3.0.0.6`：与 WATERMeDIA 3.0.0.23 的配套关系及官方文件大小已核对。
- [x] `WATERMeDIA 3.0.0.23 + Binaries 3.0.0.6`：Vulkan 1.4.329 下视频、硬件解码路径、停止、手动刷新与重新播放进入 `PLAYING`。
- [x] `WATERMeDIA 3.0.1 + Binaries 3.0.1`：OpenGL 3.3 与 Vulkan 1.4.329 下完成媒体与 0.1.1 功能回归。
- [ ] 3.0.0.23 + Binaries 3.0.0.6：OpenGL 3.3 播放回归。

`3.0.0.23` 仅提供初版兼容，未来可能移除；新增功能默认只保证 `3.0.1`。

## HLS / M3U8

- [x] OneScreen URL 校验允许 HTTP/HTTPS 的 `.m3u8` 地址。
- [x] WATERMeDIA 3.0.1 将 M3U8/M3U 映射为视频，并解析 HLS Master、Media Playlist 与 IPTV 列表。
- [x] FFmpeg 路径包含 HLS 解复用、直播/VOD 探测、时间戳不连续和 HLS 变体重开处理。
- [x] 图片引擎首帧前 `format() == null` 的崩溃路径已修复，并有双版本冒烟回归。
- [x] 本地 HTTP 的 HLS Master → Media Playlist 冒烟测试在 WATERMeDIA `3.0.0.23` 与 `3.0.1` 下均解析出 6000 ms 直播片段。
- [x] 全量 Java 25 源码编译、JAR 校验、VOD/直播进度映射和设置默认值冒烟测试通过。
- [ ] HLS VOD 实机播放、暂停、结束与循环。
- [ ] HLS 直播实机播放、暂停与重连行为。
- [ ] IPTV 多频道列表行为；当前只读取 `source(0)`，不会自动切换频道。

## 多人测试

准备两个客户端 A/B。

```text
1. A 放置屏幕。
2. B 能看到方块。
3. A 修改 URL。
4. A/B 都看到更新。
5. A/B 站在不同距离。
6. 验证音量分别计算。
7. B 离开区块。
8. 验证 B 不再保留媒体会话。
9. B 返回区块。
10. 验证 B 重新加载。
```

## 异常测试

| 输入 | 预期 |
|---|---|
| 空字符串 | 显示未设置状态 |
| 超长 URL | 命令拒绝 |
| `javascript:` | 命令拒绝 |
| `file://` | 第一版拒绝 |
| 404 URL | 错误占位，不崩溃 |
| 正常 URL 后变成 404 | 旧媒体关闭，新会话失败 |
| 不支持格式 | 错误占位 |
| 媒体服务超时 | 可取消并恢复 |
| 高频修改 URL | 旧请求被取消或忽略 |
| 不存在的方块坐标 | 命令返回明确错误 |

## 性能检查

连续运行 30 分钟并记录：

- [ ] 活动 MediaSession 数量稳定。
- [ ] 9 个屏幕时活动会话不超过 8，超限屏幕等待容量释放。
- [ ] 音频 source 数量稳定。
- [ ] GPU 纹理数量稳定。
- [ ] 内存没有持续增长。
- [ ] 区块反复加载不产生重复会话。
- [ ] 错误日志不逐帧增长。
- [ ] LOD 变化日志只在档位变化时出现。
- [ ] 720p 与 1080p 测试档位的输出尺寸符合预期。
- [ ] `NativeImage` 像素写入已完成批量 Buffer 优化。

建议临时调试命令：

```text
/screen debug sessions
```

输出：

```text
activeSessions=3
closingSessions=0
errorSessions=0
totalTextureBytesEstimate=...
```

## 发布前检查

- [x] 版本号更新。
- [x] changelog 更新。
- [x] WATERMeDIA 与 Binaries 版本在 README 中说明。
- [x] Release JAR 不包含本地 Binaries。
- [x] 客户端与服务端安装说明完整。
- [x] 已知限制写入发布说明。
- [x] OpenGL 路径测试。
- [x] Vulkan 路径测试（26.2 Vulkan 1.4.329）。
- [x] 无敏感 URL 出现在日志或测试文件。
