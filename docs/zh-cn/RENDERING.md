# 26.2 屏幕渲染实现

## 目标

OneScreen 使用 Minecraft 26.2 的 render-state extraction 与 `SubmitNodeCollector` 在方块位置提交动态媒体四边形。渲染器不直接管理播放器，也不在绘制阶段下载、解码或创建纹理。

## 渲染链路

```text
ScreenBlockEntity
  -> extractRenderState
ScreenRenderState
  -> submit
ScreenRenderer
  -> submitCustomGeometry
  -> SubmitNodeCollector
```

`ScreenRenderer` 实现：

```java
BlockEntityRenderer<ScreenBlockEntity, ScreenRenderState>
```

它不实现旧版的即时绘制签名，也不复制过时的 `PoseStack + MultiBufferSource` 渲染模式。

## `ScreenRenderState`

每帧快照包含：

- `facing`
- `alignment`
- `fitMode`
- `rotationDegrees`
- `width` / `height`
- `renderDistance`
- `hasUrl`
- 当前 `mediaTexture` / `mediaRenderType`
- `mediaWidth` / `mediaHeight`
- `mediaState`

`extractRenderState` 只能读取 BlockEntity 和 `MediaSnapshot`。不得在提取阶段阻塞、启动播放器、发起 HTTP 请求或修改全局渲染状态。

## 媒体提交

`submit` 分两种情况：

1. 有媒体纹理：根据媒体宽高与 `ScreenFitMode` 计算四边形尺寸和 UV。
2. 无媒体纹理：debug 关闭时直接不提交；debug 开启时提交 `screen_front.png` 占位材质。

媒体使用 `RenderTypes.entityCutout(...)`，并采用：

- 全亮烘焙光 `0x00F000F0`。
- `OverlayTexture.NO_OVERLAY`。
- 正常面法线。
- 错误状态红色 tint。

## 四边形几何

屏幕使用六个方向：

- `NORTH`
- `SOUTH`
- `EAST`
- `WEST`
- `UP`
- `DOWN`

`ScreenGeometry.right(facing, rotation)` 与 `up(facing, rotation)` 为当前屏幕平面提供右手基向量。`submitQuad` 再完成：

1. 以方块中心为锚点。
2. 沿 `facing` 反方向移动到薄面表面。
3. 应用九宫格水平/垂直偏移。
4. 按半宽、半高生成四个角。
5. 用统一的 `u0/v0/u1/v1` 提交 UV。

当前表面偏移常量为 `SURFACE_OFFSET = 0.4365`。`UP`/`DOWN` 不能复用水平面的 UV 顺序；历史上曾出现宽面贴图落在同方块另一侧、上下方向 UV 反向和窄边缺失的问题，六向修改后必须分别实机检查。

## 放置与悬空

`ScreenBlock.getStateForPlacement()` 只使用 `BlockPlaceContext.getClickedFace()` 设置 `FACING`，不调用 `isFaceSturdy`，也不要求目标方块为完整方块。因此楼梯、台阶、栅栏、墙等具有可点击外形的方块表面都可以放置薄画框。

画框不保存承托方块坐标，也没有 `canSurvive` 或邻居更新自毁逻辑。破坏原本贴附的方块只会触发普通相邻更新，画框继续保持悬空；只有直接破坏画框本身时才会执行方块破坏和 loot table 掉落。`FACING` 继续决定模型、媒体面和交互面所在的六个朝向之一。

## 九宫格偏移

`ScreenAlignment` 提供：

```text
top_left, up, top_right
left, center, right
bottom_left, down, bottom_right
```

`ScreenGeometry.horizontalOffset` 和 `verticalOffset` 使用屏幕自身宽高计算偏移。旋转时偏移方向跟随屏幕平面基向量，因此九宫格布局会随画面一起旋转，而不会留在世界坐标轴上。

## 画面适配

`ScreenFitGeometry.calculate(...)` 返回四边形尺寸和 UV：

| 模式 | 实现 |
|---|---|
| `fit` | contain：保持比例并完整显示。 |
| `stretch` | 填满屏幕，不保持比例。 |
| `tile` | 四边形填满屏幕，UV 按“媒体长边 1 格”重复。 |
| `fill` | cover：保持比例，裁剪超出的 UV。 |
| `center` | 按“媒体长边 1 格”的自然尺寸居中。 |

`tile` 的 UV 可以超过 `1.0`，依赖 Minecraft 纹理采样器的重复行为。验证时必须检查接缝、透明像素和非整数平铺比例。

## 距离剔除

`ScreenRenderer.shouldRender` 使用：

- 屏幕媒体中心。
- 当前摄像机位置。
- 屏幕配置的 `renderDistance`。
- 屏幕显示半径。

只有媒体中心到摄像机的距离小于“渲染距离 + 显示半径”时才提交几何。`getViewDistance()` 使用硬上限 `4096`，避免超过服务端允许范围后 renderer 提前剔除。实际可见性仍受客户端区块视距和服务端区块加载限制。

26.2 当前目标没有此处可覆盖的旧版 `getRenderBoundingBox` 方法。不要复制旧版本签名；超大屏和贴边视角仍需实机确认。

## 透明与 Debug

- 无媒体且 `/screen config debug off`：不提交显示区域，屏幕视觉透明。
- 无媒体且 debug on：提交占位材质，便于定位方块与贴图方向。
- 静态画框模型由 block model 控制；`hideFrame=true` 时 `ScreenBlock.getRenderShape()` 返回不可见。
- 隐藏画框不会删除 BlockEntityRenderer 的媒体面或方块点击区域。
- 媒体错误只改变 tint 和状态，不应每帧刷错误日志。

## 平面旋转

旋转范围 `-180°` 到 `180°`，覆盖屏幕平面内的所有角度。实现只旋转屏幕平面基向量，不引入完整三维欧拉旋转：

- 媒体四边形跟随基向量旋转。
- 九宫格偏移跟随基向量旋转。
- 声音中心使用同一几何计算。
- `FACING` 仍决定模型、媒体面和交互面的放置朝向。

## 纹理来源

`ExternalMediaTexture` 为 Minecraft 提供统一 `GpuTextureView` 外观：

- OpenGL view 动态读取 WATERMeDIA 当前 GL texture id。
- Vulkan view 动态读取 WATERMeDIA 当前 `VkImageView`。
- CPU 回退使用 `DynamicTexture`。
- WATERMeDIA 拥有底层图像资源；OneScreen 只管理包装 view 和注销顺序。

GPU 纹理句柄、Vulkan 队列锁和资源退休详见 [媒体、GPU 纹理与音频](MEDIA-AUDIO.md)。

## 修改渲染代码后的验收

- 六向方块模型、媒体面、点击区域位于同一薄面。
- `UP`/`DOWN` 的宽面 UV、四个窄边贴图方向正确。
- 九宫格九个位置在水平与上下朝向均正确。
- `fit/stretch/tile/fill/center` 在 16:9、4:3、竖屏、超宽屏上正确。
- 平面旋转后画面、偏移和声音中心一致。
- 无媒体透明、debug 占位、隐藏画框均符合预期。
- 远距离剔除和超大屏幕不会异常消失或闪烁。
- OpenGL 与 Vulkan 均完成实机回归。