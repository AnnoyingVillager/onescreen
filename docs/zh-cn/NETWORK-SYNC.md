# 网络同步与命令

## 权威模型

服务端是 URL、尺寸、距离、九宫格位置、画面适配、旋转、画框隐藏、音量、静音、循环和直播追帧的唯一权威。客户端只负责显示编辑器、提交用户输入和在本地播放媒体。

媒体文件本身不由服务端代理；服务端只同步 URL，各客户端向该 URL 发起自己的媒体请求。

## 同步通道

### 持久化与方块实体更新

`ScreenBlockEntity` 使用 26.2 数据接口：

- `saveAdditional` / `loadAdditional` 保存和读取 `ScreenSettings` 与 `loadRevision`。
- `getUpdateTag` 为后进入区块的客户端提供完整状态。
- `getUpdatePacket` 为已跟踪方块实体发送定向更新。
- `setSettings` 后调用 `setChanged()` 和 `sendBlockUpdated()`。

常规 URL 和设置修改不需要为每个字段另造 S2C 包，方块实体更新就是权威数据通道。

### 编辑器打开

玩家右键屏幕后，服务端验证：

1. 目标方块实体存在。
2. 玩家仍在允许的编辑距离内。
3. 当前屏幕数据可读取。

验证通过后发送 `ScreenEditorOpenPayload`，其中包含当前设置、服务端距离上限和当前加载修订。

### 设置保存

`ScreenSettingsUpdatePayload` 从客户端提交：

- 方块坐标。
- URL、宽高、距离。
- 九宫格位置和画面适配。
- 旋转、隐藏画框。
- 音量、静音、循环、直播追帧。

服务端收到后必须重新执行完整校验，不能信任客户端显示过的范围：

1. 权限等级。
2. 目标区块已加载。
3. 目标为 `ScreenBlockEntity`。
4. 玩家仍在编辑距离内。
5. URL 为 HTTP/HTTPS、主机非空、长度不超过 4096、无控制字符。
6. 尺寸、距离、音量、旋转等数值在合法范围内。
7. 距离不超过 `OneScreenConfig` 当前上限。

校验完成后写入新 `ScreenSettings`，再通过方块实体更新广播。

### 手动刷新

`ScreenReloadPayload` 只携带方块坐标。服务端验证权限、目标方块、编辑距离和非空 URL 后调用 `ScreenBlockEntity.reload()`：

```text
loadRevision = loadRevision + 1
setChanged()
sendBlockUpdated()
```

客户端观察到修订号变化后关闭旧 `MediaSession` 并重建同一 URL。刷新不携带播放器对象，也不允许客户端直接创建新会话。

### Debug 状态

`/screen config debug on|off` 修改服务端 `ScreenDebugSettings`，再通过 `ScreenDebugStatePayload` 广播给客户端。该状态只决定无媒体时是否提交占位材质，不影响正常媒体纹理。

## 命令树

```text
/screen
  config debug [on|off]
  set <pos> <url>
  size <pos> <width> <height>
  renderdistance <pos> <value>
  audiodistance <pos> <value>
  align <pos> <position>
  fitmode <pos> <mode>
  rotation <pos> <degrees>
  hideframe <pos> <hidden>
  volume <pos> <value>
  mute <pos> <muted>
  loop <pos> <loop>
  reload <pos>
  info <pos>
```

权限：

- 所有修改类命令和 `config debug` 要求 `Commands.LEVEL_GAMEMASTERS`。
- `info` 对所有玩家开放。
- 具体语法和值域见 [命令参考](COMMAND-REFERENCE.md)。

## URL 校验

`ScreenUrlValidator` 只接受：

- `http://`
- `https://`

拒绝：

- `file://`、`javascript:` 和其他协议。
- 空 URL 或空主机。
- 超过 4096 字符。
- 控制字符或明显非法的 URI 编码。
- 非有限或缺失主机信息。

URL 可能包含鉴权查询参数。日志必须使用脱敏摘要，不得输出完整敏感 URL。

## 生命周期与竞态

```text
服务端修改设置
  -> BlockEntity data updated
  -> 客户端 ScreenMediaManager 比较 URL/loadRevision
  -> 关闭旧 MediaSession
  -> 创建新 MediaSession
  -> 异步加载媒体
```

必须保持：

- 同一屏幕同时最多一个活动播放器。
- 旧会话关闭后才能允许新会话输出声音。
- 快速连续修改 URL 时，晚到的旧异步回调不能覆盖新会话。
- 区块卸载、断线、维度切换和方块移除必须清理会话。
- `loadRevision` 使用整数回绕到 1，避免从最大值溢出。

## 命令实现原则

- 命令只在逻辑服务端执行。
- 使用 Brigadier 参数类型解析方块坐标、浮点值和布尔值。
- 目标方块通过 `BlockPosArgument.getLoadedBlockPos` 获取，未加载时明确失败。
- 非屏幕目标返回本地化错误。
- 修改后返回成功消息并触发方块更新。
- 不在日志中打印完整 URL 或玩家敏感数据。

## 编辑器 UI 与网络的边界

- 客户端编辑器只收集输入并显示服务端返回的状态。
- 编辑器关闭时不应直接修改 BlockEntity。
- 播放、暂停、停止和进度拖动是当前客户端媒体会话控制；它们不需要修改服务端 URL 配置。
- 右侧设置保存才发送 `ScreenSettingsUpdatePayload`。
- 刷新按钮发送 `ScreenReloadPayload`，由服务端递增修订。

## 验证清单

- 玩家 A 修改设置，玩家 B 能看到更新。
- 新玩家进入区块后立刻获得当前设置。
- 玩家离开区块后媒体会话释放，返回后重建。
- 维度切换和断线不残留声音或纹理。
- 无权限玩家不能执行修改命令。
- 非屏幕位置、未加载区块和非法 URL 返回明确错误。
- 快速修改 URL、刷新、停止和重播不会产生重复播放器。
- 服务端与客户端日志不泄露完整敏感 URL。