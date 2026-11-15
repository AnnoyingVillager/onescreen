# Minecraft 26.2 Screen Rendering

## Goal

OneScreen uses Minecraft 26.2 render-state extraction and `SubmitNodeCollector` to submit a dynamic media quad at a block position. The renderer does not manage players and never downloads, decodes, or creates textures during draw submission.

## Render chain

```text
ScreenBlockEntity
  -> extractRenderState
ScreenRenderState
  -> submit
ScreenRenderer
  -> submitCustomGeometry
  -> SubmitNodeCollector
```

`ScreenRenderer` implements:

```java
BlockEntityRenderer<ScreenBlockEntity, ScreenRenderState>
```

It does not implement the old immediate `PoseStack + MultiBufferSource` drawing signature.

## `ScreenRenderState`

Each frame snapshot contains:

- `facing`
- `alignment`
- `fitMode`
- `rotationDegrees`
- `width` / `height`
- `renderDistance`
- `hasUrl`
- Current `mediaTexture` / `mediaRenderType`
- `mediaWidth` / `mediaHeight`
- `mediaState`

`extractRenderState` may only read the block entity and `MediaSnapshot`. It must not block, start players, perform HTTP requests, or mutate global render state.

## Media submission

`submit` has two paths:

1. A media texture exists: calculate quad size and UV using media dimensions and `ScreenFitMode`.
2. No media texture exists: return without geometry when debug is off, or submit `screen_front.png` placeholder material when debug is on.

Media uses `RenderTypes.entityCutout(...)` with:

- Full-bright baked light `0x00F000F0`.
- `OverlayTexture.NO_OVERLAY`.
- Correct face normals.
- A red tint for error state.

## Quad geometry

The screen supports six directions:

- `NORTH`
- `SOUTH`
- `EAST`
- `WEST`
- `UP`
- `DOWN`

`ScreenGeometry.right(facing, rotation)` and `up(facing, rotation)` provide the current screen-plane basis vectors. `submitQuad` then:

1. Anchors the quad at the block center.
2. Moves toward the thin surface against `FACING`.
3. Applies horizontal and vertical alignment offsets.
4. Creates four corners from half-width and half-height.
5. Submits shared `u0/v0/u1/v1` UV values.

The current surface offset is `SURFACE_OFFSET = 0.4365`. Up/down faces cannot reuse horizontal UV ordering; historical bugs included wide-face textures on the wrong side of the block, reversed up/down UVs, and missing narrow-edge textures. Every six-direction change requires separate in-game verification.

## Placement and support independence

`ScreenBlock.getStateForPlacement()` uses only `BlockPlaceContext.getClickedFace()` to set `FACING`. It does not call `isFaceSturdy` and does not require a full cube. Visible, clickable surfaces of stairs, slabs, fences, walls, and other non-full blocks are therefore valid placement targets for the thin frame.

The screen does not store a support-block position, and it has no `canSurvive` or neighbor-update self-destruction rule. Removing the block it was originally placed against only causes a normal neighbor update; the frame remains suspended. The loot table runs only when the frame itself is broken. `FACING` continues to select one of the six model, media, and interaction orientations.
## Alignment offsets

`ScreenAlignment` provides:

```text
top_left, up, top_right
left, center, right
bottom_left, down, bottom_right
```

`ScreenGeometry.horizontalOffset` and `verticalOffset` use the screen’s own dimensions. When the screen is rotated, the offset follows the in-plane basis vectors, so the alignment layout rotates with the image instead of staying aligned with world axes.

## Fit modes

`ScreenFitGeometry.calculate(...)` returns quad size and UV:

| Mode | Implementation |
|---|---|
| `fit` | Contain: preserve aspect ratio and keep the full media visible. |
| `stretch` | Fill the screen without preserving aspect ratio. |
| `tile` | Fill the screen quad and repeat UV using one block along the media’s longest edge. |
| `fill` | Cover: preserve aspect ratio and crop overflow UV. |
| `center` | Use one block along the longest edge as natural size and center the media. |

`tile` can produce UVs greater than `1.0` and relies on the Minecraft texture sampler’s repeat behavior. Test seams, transparent pixels, and non-integer tile ratios.

## Distance culling

`ScreenRenderer.shouldRender` checks:

- The media center.
- Camera position.
- Configured `renderDistance`.
- Screen display radius.

Geometry is submitted only when the media center is closer than `renderDistance + display radius`. `getViewDistance()` uses the `4096` hard cap so an allowed long-distance screen is not culled early by the renderer. Actual visibility still depends on client render distance and server chunk loading.

The 26.2 target has no applicable old `getRenderBoundingBox` override here. Do not copy an older signature; very large screens and edge-on camera positions still require in-game verification.

## Transparency and debug

- No media and `/screen config debug off`: no display geometry is submitted, so the screen area is visually transparent.
- No media and debug on: placeholder material is submitted for locating the block and checking texture direction.
- The static frame model is controlled by the block model; `hideFrame=true` makes `ScreenBlock.getRenderShape()` invisible.
- Hiding the frame does not remove the BlockEntityRenderer media quad or block interaction area.
- Media errors only change tint/state and must not log every frame.

## In-plane rotation

Rotation ranges from `-180°` through `180°` and covers every angle within the screen plane. Only the plane basis vectors rotate:

- The media quad follows the basis.
- Alignment offsets follow the basis.
- The audio center uses the same geometry.
- `FACING` still determines the mounting orientation of the model, media quad, and interaction area.

## Texture sources

`ExternalMediaTexture` gives Minecraft a unified `GpuTextureView` facade:

- OpenGL views dynamically read the current WATERMeDIA GL texture ID.
- Vulkan views dynamically read the current WATERMeDIA `VkImageView`.
- The CPU fallback uses `DynamicTexture`.
- WATERMeDIA owns the underlying image; OneScreen only manages the wrapper view and unregister order.

See [Media, GPU Textures, and Audio](MEDIA-AUDIO.md) for texture lifetime, Vulkan queue locking, and resource retirement.

## Acceptance after renderer changes

- The six-direction model, media quad, and interaction area occupy the same thin surface.
- Up/down wide-face UVs and four narrow-edge textures are correct.
- All nine alignment positions work on horizontal and vertical faces.
- `fit/stretch/tile/fill/center` work on 16:9, 4:3, portrait, and ultrawide media.
- Rotation keeps the image, alignment, and audio center synchronized.
- Empty-screen transparency, debug placeholder, and hidden frame behave as intended.
- Long-distance culling and oversized screens do not disappear unexpectedly.
- OpenGL and Vulkan both receive in-game regression testing.

Return to the [English documentation index](README.md).