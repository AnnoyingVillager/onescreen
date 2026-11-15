# 命令参考

OneScreen 的命令由逻辑服务端执行，并与右键编辑器共用同一套权限、坐标、URL、数值范围和配置上限校验。修改类命令要求权限等级 2（Gamemaster）；`/screen info` 对所有玩家开放。

## 命令总览

```text
/screen set <x> <y> <z> <url>
/screen size <x> <y> <z> <width> <height>
/screen renderdistance <x> <y> <z> <blocks>
/screen audiodistance <x> <y> <z> <blocks>
/screen align <x> <y> <z> <position>
/screen fitmode <x> <y> <z> <mode>
/screen rotation <x> <y> <z> <degrees>
/screen hideframe <x> <y> <z> <true|false>
/screen volume <x> <y> <z> <0.0-1.0>
/screen mute <x> <y> <z> <true|false>
/screen loop <x> <y> <z> <true|false>
/screen reload <x> <y> <z>
/screen info <x> <y> <z>
/screen config debug [on|off]
```

## 设置 URL

```text
/screen set 120 64 -37 https://example.com/demo.mp4
```

- 仅接受 HTTP/HTTPS URL，最大长度 4096 字符。
- 拒绝空主机、控制字符、`file://`、`javascript:` 等非 HTTP(S) 输入。
- 目标区块必须已加载，且目标位置必须是 OneScreen 屏幕方块实体。
- URL 与查询参数会由服务端保存；玩家看到的日志摘要会脱敏。

## 屏幕尺寸

```text
/screen size 120 64 -37 4 2.25
```

- 宽、高单位是方块。
- 最小 `0.25`，最大 `32`。
- 尺寸变化只影响媒体显示四边形、相对位置和声音中心，不形成多方块碰撞体积。

## 渲染距离与声音距离

```text
/screen renderdistance 120 64 -37 96
/screen audiodistance 120 64 -37 48
```

默认服务端上限：

| 项目 | 默认上限 | 配置硬上限 |
|---|---:|---:|
| 渲染距离 | `256` 格 | `4096` 格 |
| 声音传播距离 | `128` 格 | `1024` 格 |

上限由 `config/onescreen.json` 配置。修改配置后需要重启游戏或服务端；已有屏幕数据不会因降低配置上限而自动裁剪。详见 [配置文件](CONFIGURATION.md)。

## 九宫格相对位置

```text
/screen align 120 64 -37 center
```

可用值：

```text
top_left
up
top_right
left
center
right
bottom_left
down
bottom_right
```

位置偏移与媒体尺寸、平面旋转共用同一几何计算，确保画面和声音中心不会错位。

## 画面适配模式

```text
/screen fitmode 120 64 -37 fill
```

| 值 | 行为 |
|---|---|
| `fit` | 保持比例并完整显示，多余区域透明。 |
| `stretch` | 拉伸到整个屏幕，不保持比例。 |
| `tile` | 保持比例，以媒体长边 1 格为基准重复平铺。 |
| `fill` | 保持比例并铺满屏幕，裁掉超出部分。 |
| `center` | 保持比例，以媒体长边 1 格为基准居中显示。 |

## 平面旋转

```text
/screen rotation 120 64 -37 30
```

范围为 `-180` 到 `180` 度，覆盖屏幕所在平面内的所有方向。旋转不会改变方块贴附面，只改变媒体四边形、九宫格偏移和声音中心在该平面内的方向。

## 隐藏画框

```text
/screen hideframe 120 64 -37 true
```

默认 `false`。设为 `true` 后隐藏静态画框模型，但保留方块实体、动态媒体画面、碰撞与右键交互区域，适合建筑和展示场景。

## 音量、静音与循环

```text
/screen volume 120 64 -37 0.8
/screen mute 120 64 -37 false
/screen loop 120 64 -37 true
```

- 音量范围 `0.0` 到 `1.0`，最终音量还会乘以玩家距离衰减。
- 静音只影响该屏幕声音，不改变画面。
- 循环设置在播放器可用时即时更新，并持久化到方块实体。

## 重新加载

```text
/screen reload 120 64 -37
```

服务端会递增持久化的 `loadRevision`，客户端观察到修订变化后关闭旧会话并重新解析同一 URL。适用于临时网络错误恢复。编辑器中播放控件左侧的刷新按钮使用相同路径。

## 查看信息

```text
/screen info 120 64 -37
```

输出 URL 脱敏摘要、尺寸、距离、九宫格位置、适配模式、旋转、画框隐藏、音量、静音、循环、直播追帧和当前加载修订。该命令不要求管理员权限。

## 调试材质

```text
/screen config debug
/screen config debug on
/screen config debug off
```

- 无参数时查询当前调试状态。
- `on` 时，无媒体画面的屏幕显示占位/画框调试材质。
- `off` 时，无媒体画面的显示区域保持透明。
- 该设置由服务端广播给客户端，仅影响占位显示，不影响正常媒体画面。

## 右键编辑器专属功能

以下功能没有独立命令，应通过屏幕上的右键编辑器操作：

- 播放、暂停、停止。
- VOD 拖动进度条定位。
- 直播片段进度只读显示。
- “暂停/配置结束后刷新直播”复选项，默认关闭。
- 编辑器关闭后保持手动暂停或停止状态。

编辑器保存时使用受校验的 `ScreenSettingsUpdatePayload`；刷新按钮使用 `ScreenReloadPayload`。客户端不能直接修改同步的方块实体数据。