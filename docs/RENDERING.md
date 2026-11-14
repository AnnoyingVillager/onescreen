# 26.2 屏幕渲染设计

## 目标

用 26.2 的渲染状态与提交体系在方块位置显示动态四边形。第一版先渲染固定占位纹理，确认位置和尺寸正确后再接媒体纹理。

## 为什么使用 BlockEntityRenderer

- 屏幕随方块实体加载和卸载。
- 可以利用 Minecraft 的视野剔除和渲染距离。
- 不需要全局世界渲染事件或每帧扫描。
- 后续可以保存宽高、朝向等方块实体数据。

## 渲染类结构

```text
ScreenBlockEntity
  -> extractRenderState
ScreenRenderState
  -> submit
ScreenRenderer
  -> SubmitNodeCollector
```

渲染器不能再直接使用旧的：

```java
render(entity, partialTick, pose, MultiBufferSource, light, overlay)
```

## 渲染状态

```java
public final class ScreenRenderState extends BlockEntityRenderState {
    public Direction facing;
    public float width;
    public float height;
    public float brightness;
    public float alpha;
    public boolean hasMediaTexture;
    public long mediaTextureHandle;
    public MediaVisualState mediaState;
}
```

不把 `BlockEntity`、`MediaPlayer` 或可变 Java 集合放进状态对象。

## 提取状态

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
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
) {
    super.extractRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);

    state.facing = entity.getBlockState().getValue(ScreenBlock.FACING);
    state.width = entity.getScreenWidth();
    state.height = entity.getScreenHeight();
    state.brightness = entity.getBrightness();
    state.alpha = entity.getAlpha();

    MediaSnapshot media = clientMediaManager.snapshot(entity.getBlockPos());
    state.hasMediaTexture = media.hasTexture();
    state.mediaTextureHandle = media.textureHandle();
    state.mediaState = media.visualState();
}
```

`extractRenderState` 只能读取或生成快照，不得阻塞、下载或启动播放器。

## 坐标与变换

约定：

- 方块局部坐标范围为 `[0, 1]`。
- 屏幕中心为方块中心附近。
- 屏幕正面由 `FACING` 决定。
- 四边使用 `width` 和 `height` 个方块单位。
- 当前支持水平四向和 `UP` / `DOWN`；媒体可在所在平面内按 `rotationDegrees` 连续旋转。

伪代码：

```java
private void transform(PoseStack pose, ScreenRenderState state) {
    pose.translate(0.5, 0.5, 0.5);
    pose.mulPose(rotationForFacing(state.facing));
    pose.translate(-state.width * 0.5f, -state.height * 0.5f, 0.501f);
}
```

必须验证：

- 北、东、南、西的贴图方向。
- 从正面看纹理没有镜像。
- 从背面不会因错误面绕序而闪烁。
- 大屏幕不会进入相邻方块。

## 提交流程

```java
@Override
public void submit(
        ScreenRenderState state,
        PoseStack pose,
        SubmitNodeCollector collector,
        CameraRenderState cameraState
) {
    pose.pushPose();
    transform(pose, state);

    if (state.hasMediaTexture) {
        submitMediaQuad(pose, collector, state);
    } else {
        submitPlaceholderQuad(pose, collector, state);
    }

    pose.popPose();
}
```

`submitMediaQuad` 的精确纹理类型必须匹配 WATERMeDIA 3 和 26.2 的后端抽象。不要直接把 OpenGL texture id 写死到所有图形后端。

## 渲染包围盒

大屏幕可能远超一个方块。需要根据朝向和尺寸扩展 AABB：

```java
@Override
public AABB getRenderBoundingBox(ScreenBlockEntity entity) {
    AABB base = new AABB(entity.getBlockPos());
    return expandForScreen(base, entity.getFacing(), entity.getScreenWidth(), entity.getScreenHeight());
}
```

验收：

- 摄像机接近包围盒边缘时屏幕不会突然消失。
- 屏幕无论宽度或高度较大都能完整渲染。
- 包围盒计算不会随帧无限分配大量对象；如有必要可缓存。

## 材质与透明

第一版推荐：

- 正面：动态媒体四边形。
- 背面：深色外壳。
- 透明通道：固定启用或由配置决定，不要每帧切换全局渲染状态。
- 光照：默认全亮或固定亮度，避免媒体黑屏。
- 混合模式：使用稳定的 Translucent 渲染路径。

第一版不实现：

- 双面同时播放。
- 投影仪。
- 可破碎模型的精细 overlay。
- 任意三维旋转。

## 占位状态

以下状态要有明显但轻量的占位图：

- 未设置 URL。
- 正在解析。
- 正在加载。
- 不支持格式。
- 网络错误。
- 音频已播放但没有视频帧。

错误占位内容不要每帧打印日志。

## 官方参考

- Fabric Block Entity Renderers: https://docs.fabricmc.net/develop/blocks/block-entity-renderer
- Fabric Block Entities: https://docs.fabricmc.net/develop/blocks/block-entities
- NeoForge 26.2 渲染变更: https://docs.neoforged.net/primer/docs/26.2/

## 0.1.1 平面旋转与隐藏画框

- `ScreenGeometry.right(facing, rotationDegrees)` 与 `up(facing, rotationDegrees)` 在原始平面基向量上做二维旋转，保持右手基和面法线不变。
- 媒体四边形、九宫格偏移和声音中心共用同一组旋转基向量，避免画面与声音中心错位。
- `ScreenBlock.hidden` 为默认关闭的 blockstate；开启时 `getRenderShape()` 返回 `INVISIBLE`，BlockEntityRenderer 仍按原流程提交动态媒体。
- 点击形状不随隐藏状态移除，因此建筑展示场景仍可编辑屏幕。
- 旋转只改变平面内方向，不引入完整三维欧拉旋转，也不改变 `FACING` 决定的贴附表面。
- 手动刷新由服务端递增 `loadRevision`，渲染器不直接创建或释放播放器；媒体协调器在后续 tick 中按既有生命周期重建会话。
