# Media, GPU Textures, and Audio

## 1. Dependencies and goals

OneScreen 0.2 uses:

- WATERMeDIA `3.0.0` and later formal `3.0.x` releases.
- WATERMeDIA Binaries matching the WATERMeDIA release line.
- Both are required on clients; dedicated servers do not load native media libraries.

See [High-Resolution Media and Hardware Decoding](HIGH-RESOLUTION-MEDIA.md) for codec and hardware capability analysis.

Not used:

- WATERMeDIA 2.x `ImageAPI`, `ImageCache`, or `ImageRenderer`.
- The old VLC `VideoPlayer`.
- Minecraft-private `SoundEngine`, `Channel`, or `ChannelAccess`.
- Direct imports of WATERMeDIA player classes in business code.

## 2. Unified entry point

All WATERMeDIA version and API differences are centralized in:

- `src/client/java/dev/onescreen/client/media/WaterMediaCompat.java`
- `src/client/java/dev/onescreen/client/media/WaterMediaVersion.java`

Business layers interact only with wrappers:

- `MediaResource`
- `MediaEngine`
- `MediaPlayerHandle`
- `MediaSnapshot`

This supports different WATERMeDIA package layouts and prevents reflection from spreading into renderers or commands.

## 3. Per-screen session structure

`ScreenMediaManager` manages `MediaSession` by dimension and block position. Each session has:

- One MRL.
- One video or image engine.
- One player.
- One Minecraft texture identifier.
- Current settings and distance values.
- Playback state, error, and frame sequence.

A single client retains at most eight active sessions; additional screens retry later to avoid unlimited decoder creation.

### MRL cache lifecycle

- WATERMeDIA caches MRLs by URL. A new `MediaSession` caused by a `loadRevision` change may still receive the same old object when the URL is resolved again.
- After obtaining the cached object, `WaterMediaCompat.mrl()` checks its status. `ERROR`, `BLOCKED`, `EXPIRED`, and `FORGOTTEN` resources call `reload()` reflectively and replace the internal handle.
- `FETCHING` is not reloaded, so a valid in-flight resolution is not interrupted by duplicate requests.
- Typical recovery: an offline HTTP server causes `ERROR (ConnectException)`; start the server, click the editor `↻`, and the new session resolves to `PLAYING`.

## 4. Image path

Images use the WATERMeDIA headless engine to produce the first frame:

```text
HeadlessGFXEngine
  -> ByteBuffer / PixelFormat
  -> NativeImage
  -> DynamicTexture
  -> screen quad
```

Notes:

- `format()` may be `null` before the first frame; this is valid.
- Copy and upload only when `uploadCount` or the frame sequence changes.
- Rebuild `DynamicTexture` when image dimensions change.
- Texture creation, update, and release occur on the client thread.

## 5. Video path overview

`MediaSession.createVideoPlayer()` first tries a GPU engine and falls back to the AWT CPU engine if GPU initialization or player creation fails.

```text
Preferred: WATERMeDIA GLEngine / VKEngine
  -> ExternalMediaTexture
  -> GPU texture view

Fallback: WATERMeDIA AWTEngine
  -> BufferedImage packed ARGB
  -> NativeImage
  -> DynamicTexture
```

`format=GPU` describes the texture path and does not prove that the decoder uses hardware acceleration. Record `hardwareAccel=true/false` separately.

## 6. OpenGL GPU textures

OpenGL path:

- Uses WATERMeDIA `GLEngine`.
- `ExternalMediaTexture.WaterMediaGlTexture` overrides `glId()` and reads the current GL texture ID while drawing.
- `WaterMediaGlView` also reads the ID dynamically, preventing Minecraft from staying bound to an old handle after WATERMeDIA rotates textures.
- The wrapper does not own the WATERMeDIA texture; it manages only the small Minecraft-side view/facade.

## 7. Vulkan GPU textures

### Object relationships

```text
Minecraft VulkanDevice
  -> WaterMediaVulkanContext
  -> WATERMeDIA VKContext proxy
  -> WATERMeDIA VKEngine
  -> VkImageView
  -> WaterMediaVulkanView
  -> Minecraft texture binding
```

### Queue contract

WATERMeDIA `VKEngine` assumes:

1. Media writes and Minecraft sampling use the same graphics queue.
2. All queue operations requiring external synchronization are serialized by the same `queueLock()`.
3. An image barrier transitions the result to `FRAGMENT_SHADER / SHADER_READ`, and the consumer samples it in a later graphics-queue submission.

Current implementation:

- `WaterMediaVulkanContext.queue()` returns Minecraft `graphicsQueue()`.
- `VulkanQueueSynchronization` provides a shared lock keyed by the native `VkQueue` address.
- `VulkanQueueSubmissionMixin` locks Minecraft `vkQueueSubmit2KHR`.
- `VulkanQueueMixin` locks `vkQueueWaitIdle`.
- `VulkanGpuSurfaceMixin` locks `vkQueuePresentKHR`.

### Historical failure

The old implementation pointed WATERMeDIA at a separate compute/transfer queue without a cross-queue semaphore. About two seconds after 4K playback began, it produced:

```text
Retention wait failed (VkResult -4)
VK_ERROR_DEVICE_LOST
```

This was not caused by HTTPS, the source, or texture dimensions. It was a Vulkan queue and visibility contract violation. Do not restore the separate-queue design.

### Ownership

- WATERMeDIA owns `VkImage`, `VkDeviceMemory`, `VkImageView`, and its retirement queue.
- OneScreen only wraps the current view handle and does not destroy WATERMeDIA’s view in `ExternalMediaTexture.close()`.
- WATERMeDIA retirement callbacks execute through fenced tasks on the Minecraft render thread.

## 8. Resolution and distance LOD

OneScreen 0.2 no longer imposes a fixed `1920x1080` limit:

- `LOD=MAX` at close range permits source dimensions such as `3840x2160`.
- `VideoLod` selects `MAX/CLOSE/NEAR/FAR/FAR_AWAY` as distance increases.
- Tiers use normalized distance with `0.05` hysteresis to avoid oscillation.
- Call WATERMeDIA `lod(...)` only when the tier actually changes.
- WATERMeDIA, FFmpeg, the GPU, driver, and source decide whether hardware decoding is used.

The test source `4k.mp4` is AV1 Main 10-bit HLG. On the current RTX 5070 Ti / FFmpeg / WATERMeDIA combination, logs show `hardwareAccel=false` but `format=GPU`, meaning the frame still uses the GPU texture path.

## 9. CPU texture fallback

The AWT path uses packed ARGB/BGRA `BufferedImage`. `MediaTextureUploader` writes it in bulk to `NativeImage` through a native-order `IntBuffer`, avoiding per-pixel `setPixel` calls.

Constraints:

- Watermark callback threads only increment a frame sequence.
- The render thread processes only the newest frame.
- Call `DynamicTexture.upload()` at most once per frame.
- Never create players or start network requests on the render thread.
- CPU fallback still performs GPU-to-CPU readback, Java pixel copying, and CPU-to-GPU upload, so 4K/8K performance is not guaranteed.

## 10. Playback states and controls

Main states:

```text
RESOLVING -> LOADING -> PLAYING
                    \-> PAUSED -> PLAYING
                    \-> ENDED
Any state -> ERROR / IDLE
```

Editor controls:

- Play: recreate/resume playback after stop or end.
- Pause: the manual pause override survives editor close.
- Stop: release the media texture and clear the screen image; no frames remain visible.
- Refresh: server increments `loadRevision`; client closes the old session and rebuilds the same URL.
- Loop: applies the player `repeat` setting immediately.
- Progress: VOD can be seeked; live streams are read-only.
- Automatic live refresh: disabled by default and applies only to live streams.

## 11. VOD progress and live resynchronization

VOD:

- Uses `time()` / `duration()`.
- Dragging calls `seekQuick()` for preview and `seek()` for precise seek on release.
- Known-duration media is not reopened by the live-refresh setting.

Live:

- Identified through `live()` and the legacy `liveSource()` wrapper.
- Progress is not draggable and shows only approximate HLS segment progress.
- Segment duration is read from WATERMeDIA public playlist helpers when available.
- If unavailable, `HlsPlaylistResolver` parses the playlist directly.
- Automatic catch-up reopens the stream after pause/resume or settings changes, with a one-second debounce to avoid reconnect storms.

## 12. Audio and distance

Audio uses the WATERMeDIA JavaScript engine and does not depend on the active OpenGL/Vulkan context.

Output volume:

```text
finalVolume = clamp(screenVolume * distanceGain, 0, 1)
distanceGain = 1 at <= minDistance
distanceGain = 0 at >= maxDistance
```

- Each client calculates distance volume independently.
- Muting one player does not affect other players.
- A video continues rendering beyond the audio distance while volume reaches zero.
- Server configuration can increase render and audio limits, but visibility remains constrained by chunk loading and client view distance.

## 13. Lifecycle

The following events must close the session:

- Block-entity unload.
- Player disconnect.
- Client shutdown.
- Dimension change.
- URL change.
- Block removal.
- GPU initialization failure requiring CPU fallback.
- Stop from the editor.
- Manual refresh replacing the old session.

Close order:

1. Unregister the Minecraft texture.
2. Release the player.
3. Release the CPU/GPU engine.
4. Let WATERMeDIA retire GPU views.
5. Remove the session from the active table.

## 14. Troubleshooting

| Log or symptom | First checks |
|---|---|
| `format=GPU` but `hardwareAccel=false` | The source/codec/FFmpeg selected software decoding; the texture path may still be valid |
| `VK_ERROR_DEVICE_LOST` | Check graphics queue, queue lock, and all three Vulkan Mixins |
| Black screen after stop or replay failure | Check texture unregister, player release, and engine release order |
| Only the first frame appears | Check frame sequence, engine texture handle changes, and dynamic view reads |
| HLS progress unavailable | Playlist parsing may have degraded safely; playback should continue |
| GPU path creation failed | It should fall back to AWT; inspect `MediaSession.createVideoPlayer()` logs |

See [Testing](TESTING.md) for more validation steps.

Return to the [English documentation index](README.md).