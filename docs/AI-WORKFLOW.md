# AI 协作开发规范

## 目标

让 AI 在有明确上下文、真实 API 源码和可重复测试的条件下修改小范围代码。不要把一句“帮我实现整个模组”直接交给 AI。

## 每次任务的固定格式

```text
任务：
只修改 [具体文件/模块]。

目标：
[一个可观察结果]

约束：
- Minecraft 26.2 / Fabric Loader 0.18.6（最低 0.18.0） / Fabric API 0.152.0+26.2
- WATERMeDIA 3.0.0.23 编译基线，兼容 3.0.1
- Java 25
- 不使用 Mixin/access widener
- media/render 代码只能放在 src/client
- 不修改范围外文件

参考资料：
- docs/xxx.md
- Gradle 缓存中的真实 Minecraft/WATERMeDIA 源码

完成前：
- 运行 gradlew compileJava
- 报告无法通过编译验证的部分
- 列出需要游戏内人工验证的步骤
```

## API 事实检查

让 AI 在实现陌生 API 前执行：

1. 找到依赖 JAR 或 decompiled source。
2. 使用 IDE 跳转、`javap` 或 Gradle 缓存确认类、方法、泛型和构造器。
3. 在回答中说明参考的数据来源。
4. 找不到签名时停止生成，不凭记忆补全。

26.2 中特别容易变化的区域：

- `BlockEntityRenderer` 渲染状态签名。
- `SubmitNodeCollector` 和动态纹理提交接口。
- `DeltaTracker`。
- GUI 和字体接口。
- 方块实体持久化。
- WATERMeDIA 3 player、MRL、texture 和 close 接口。

## 任务粒度

推荐一次只完成：

- 一个类。
- 一个接口边界。
- 一个资源类型。
- 一个命令子节点。
- 一个媒体生命周期转换。

不推荐一次完成：

- 同时添加方块、方块实体和渲染器。
- 同时接入图片、视频、声音和网络。
- 在大面积重写时顺便升级依赖。
- 未通过编译就添加下一个功能。

## 验证循环

```text
修改
  -> gradlew compileJava
  -> 检查 diff
  -> runClient / runServer（需要时）
  -> 执行本阶段验收
  -> git commit
```

失败时：

1. 保留完整错误的第一段根因。
2. 不提交生成成功但未启动验证的代码。
3. 连续两次修复失败后缩小修改范围。
4. 如果是 API 不存在，查询依赖源码，而不是继续猜方法名。

## 渲染任务提示模板

```text
只实现固定占位纹理的屏幕四边形。
先查找当前项目依赖中的 BlockEntityRenderer、ScreenRenderState、
SubmitNodeCollector 和动态纹理提交 API 的真实签名。

禁止：
- 使用 MultiBufferSource 旧流程
- 在渲染方法中创建媒体播放器
- 写死 OpenGL 纹理调用

必须：
- push/pop PoseStack
- 支持 HORIZONTAL_FACING
- 扩展 getRenderBoundingBox
- 运行 compileJava

完成后列出需要人工验证的朝向、尺寸和剔除行为。
```

## 媒体任务提示模板

```text
只实现 MediaSession 的 URL 解析和静态图片 READY 状态。
先检查 WATERMeDIA 3.0.1 的实际 API。
禁止引用 ImageAPI、ImageCache、ImageRenderer、VideoPlayer 等 2.x 类。

要求：
- 解析异步
- 同一 BlockPos 最多一个会话
- URL 改变时 close 旧会话
- 不在渲染线程阻塞
- 错误状态可以回到占位显示
- 完成 compileJava
```

## 网络任务提示模板

```text
优先使用 BlockEntity 更新包完成服务端到客户端的 URL 同步。
只有确实需要客户端请求时才添加 CustomPacketPayload。

payload 必须：
- 使用 Identifier
- 有 StreamCodec
- 服务端验证权限、方块存在、URL 长度和协议
- 限制异常日志频率
- 通过 runClient/runServer 验证 registry 两侧都注册
```

## 代码审查清单

- [ ] 没有在 `src/main` 使用客户端类。
- [ ] 没有在每帧创建媒体或纹理。
- [ ] 没有把播放器存入 BlockEntity。
- [ ] 没有未关闭的媒体会话。
- [ ] URL 修改只在数据实际变化时发生。
- [ ] 没有旧的 WATERMeDIA 2 API。
- [ ] 没有凭记忆生成 26.2 API。
- [ ] 本阶段测试实际通过。
- [ ] 更新了必要的决策和风险文档。