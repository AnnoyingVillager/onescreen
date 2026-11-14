# URL 同步与命令设计

## 第一版同步策略

URL 以服务端 BlockEntity 为权威，并通过 Minecraft 自带的 BlockEntity 更新机制同步：

- `SaveAdditional` / `LoadAdditional` 负责持久化。
- `GetUpdateTag` 负责玩家进入区块时的初始同步。
- `GetUpdatePacket` 负责方块数据变化时的定向同步。
- `sendBlockUpdated` 负责通知跟踪该区块的客户端。

URL 和其他设置的持久化同步本身不需要额外客户端 payload；GUI 提交修改时使用受验证的 `CustomPacketPayload`。

屏幕编辑器本身需要使用受验证的 payload：服务端打开编辑界面时发送当前 URL、尺寸、距离、对齐、适配、旋转、隐藏画框、循环、直播追帧和有效距离上限；客户端保存时提交同字段更新 payload。服务端仍执行权限、编辑距离、URL、数值范围和配置上限校验，再写回 BlockEntity。

## 修改流程

```text
玩家执行 /screen set
  -> 命令在逻辑服务端执行
  -> 验证权限
  -> 验证坐标和 BlockEntity 类型
  -> 验证 URL
  -> 更新服务端字段
  -> setChanged()
  -> sendBlockUpdated()
  -> 客户端收到 BlockEntity 数据
  -> MediaSessionManager 替换会话
```

## 命令树

```text
/screen
  set <pos> <url>
  size <pos> <width> <height>
  renderdistance <pos> <value>
  audiodistance <pos> <value>
  align <pos> <position>
  fitmode <pos> <mode>
  rotation <pos> <degrees>
  hideframe <pos> <true|false>
  volume <pos> <value>
  mute <pos> <true|false>
  loop <pos> <true|false>
  reload <pos>
  info <pos>
```

权限：

- 第一版统一要求权限等级 `2`。
- `info` 可以降低为所有人可用。
- URL、尺寸和音量修改只允许管理员。

## 命令校验

按顺序执行：

1. 权限检查。
2. 坐标解析。
3. 区块是否已加载。
4. 目标是否为 `ScreenBlockEntity`。
5. 参数范围检查。
6. URL scheme、主机和长度检查。
7. 实际修改。
8. 返回成功或明确失败原因。

伪代码：

```java
if (!source.hasPermission(2)) {
    source.sendFailure(Component.translatable("onescreen.error.permission"));
    return 0;
}

BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
ServerLevel level = source.getLevel();

if (!level.isLoaded(pos)) {
    source.sendFailure(Component.translatable("onescreen.error.not_loaded"));
    return 0;
}

if (!(level.getBlockEntity(pos) instanceof ScreenBlockEntity screen)) {
    source.sendFailure(Component.translatable("onescreen.error.not_screen"));
    return 0;
}

String url = UrlValidator.validateOrThrow(
        StringArgumentType.getString(context, "url")
);
screen.setUrl(url);
source.sendSuccess(() -> Component.translatable("onescreen.success.updated"), false);
return 1;
```

## URL 校验

第一版允许：

- `http://`
- `https://`

第一版拒绝：

- `file://`
- `javascript:`
- 空主机
- 超过 4096 字符
- 控制字符
- 包含不可见字符或明显伪造的 URI 编码

后续如需本地文件，单独设计 `water://local/` 前缀并明确根目录边界。

## 自定义 payload 的使用条件

仅在以下场景引入 `CustomPacketPayload`：

- 玩家使用按键或 GUI 请求修改屏幕。
- 客户端需要主动发送播放/暂停控制和续播位置。
- 服务器需要对客户端播放状态发起高频协商。
- 服务端需要把当前有效距离上限同步给屏幕编辑器。

即使引入 payload，服务端仍必须：

- 使用 `Identifier` 定义 payload type。
- 使用 `StreamCodec`。
- 在 common initializer 注册类型。
- 在 server receiver 中验证权限、距离、方块和值范围。
- 限制日志和请求频率。

## 禁止模式

- 客户端直接修改同步的屏幕 URL。
- 收到任意 URL 就无上限创建播放器。
- 每个 tick 广播 URL。
- 将完整鉴权 URL 写进日志。
- 只做客户端校验。
- 使用字符串拼接形成文件路径。

## 验证

- 客户端 A 修改 URL，客户端 B 看到更新。
- 客户端 B 后进入区块，立刻获得当前 URL。
- 无权限玩家请求被拒绝。
- 非屏幕保存了错误 BlockEntity 时命令安全失败。
- 广告 URL 或错误 URL 不会破坏方块数据。
- 服务器日志不泄露敏感参数。

## 0.1.1 手动刷新 payload

`ScreenReloadPayload` 只携带方块坐标。服务端收到后必须依次验证：

1. 玩家具有 gamemaster 权限。
2. 坐标处存在 `ScreenBlockEntity`。
3. 玩家仍在编辑器允许的距离内。
4. 屏幕已保存非空 URL。

验证通过后调用 `screen.reload()`，递增 `loadRevision` 并同步 BlockEntity。客户端媒体协调器观察到修订变化后关闭旧会话并重建播放器。刷新操作不在客户端直接创建播放器，也不能绕过 URL 或权限校验。
