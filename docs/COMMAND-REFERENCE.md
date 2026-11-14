# 命令参考

第一版使用命令代替复杂 GUI。命令均在服务端执行，客户端只负责发送标准命令。

## 设置 URL

```text
/screen set <x> <y> <z> <url>
```

示例：

```text
/screen set 120 64 -37 https://example.com/demo.mp4
```

要求：

- 修改命令需要权限等级 2；`info` 可公开使用。
- 目标区块已加载。
- 目标方块为屏幕。
- URL 为 HTTP/HTTPS 且不超过 4096 字符。

## 设置尺寸

```text
/screen size <x> <y> <z> <width> <height>
```

示例：

```text
/screen size 120 64 -37 4 2.25
```

限制：

- 宽高最小 `0.25`。
- 宽高最大 `32`。
- 输入非有限数或超出范围时拒绝。

渲染距离和声音传播距离的上限由 `config/onescreen.json` 提供：

```text
/screen renderdistance <x> <y> <z> <blocks>
/screen audiodistance <x> <y> <z> <blocks>
```

默认上限分别为 `256` 和 `128`，可配置硬上限分别为 `4096` 和 `1024`。详见 [配置文件](CONFIGURATION.md)。

## 设置画面适应

```text
/screen fitmode <x> <y> <z> <mode>
```

可用模式：

- `fit`（适应）：保持比例并完整显示，多余区域透明。
- `stretch`（拉伸）：拉伸到整个屏幕，不保持比例。
- `tile`（平铺）：保持比例，以媒体长边 1 格为基准重复平铺。
- `fill`（填充）：保持比例并铺满屏幕，裁掉超出部分。
- `center`（居中）：保持比例，以媒体长边 1 格为基准居中显示。

示例：

```text
/screen fitmode 120 64 -37 fill
```

## 设置音量

```text
/screen volume <x> <y> <z> <value>
```

范围：`0.0` 到 `1.0`。

## 静音

```text
/screen mute <x> <y> <z> <true|false>
```

## 循环

```text
/screen loop <x> <y> <z> <true|false>
```

右键菜单也提供“循环播放”勾选项，保存行为与命令一致。

## 播放进度与直播追帧

播放进度和“暂停/配置结束后刷新直播”只存在于右键设置界面，不新增命令：

- 已知时长视频：拖动进度条定位，松开后进行精确定位。
- 直播流：进度条只读，仅显示当前 HLS 片段的目标时长进度。
- 自动刷新直播默认关闭。开启后，游戏暂停恢复或屏幕配置更新会重连直播流，以追上实时边缘。

## 重新加载

```text
/screen reload <x> <y> <z>
```

行为：

- 服务端递增持久化的 `loadRevision`。
- 客户端关闭当前媒体并重新解析同一 URL。
- 适用于临时网络错误恢复。

## 查看信息

```text
/screen info <x> <y> <z>
```

建议显示：

- URL 的脱敏摘要。
- 宽高。
- 音量与静音。
- 最后修改者。
- 最近一次加载状态，仅客户端命令可显示本地媒体状态。

## 调试

```text
/screen debug sessions
```

仅管理员可见，输出活动会话数量、错误数量和上限。

## 错误消息

建议本地化键：

```text
onescreen.error.permission
onescreen.error.not_loaded
onescreen.error.not_screen
onescreen.error.invalid_url
onescreen.error.url_too_long
onescreen.error.invalid_size
onescreen.error.invalid_volume
onescreen.success.updated
onescreen.success.reloaded
```

## 后续 GUI 兼容

未来 GUI 不应直接修改方块数据。GUI 应通过服务端命令等价的安全 payload 提交，并执行相同的权限、坐标、距离和参数校验。
