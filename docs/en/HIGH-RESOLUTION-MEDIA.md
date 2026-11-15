# High-Resolution Media and Hardware Decoding

- Assessment date: 2026-11-14
- Scope: WATERFrAMES `1.21.1-fabric` reference implementation and OneScreen’s Minecraft `26.2` Fabric target
- Evidence level: code audit, not a graphics-hardware certification

> **2026-11-15 implementation update**
>
> OneScreen 0.2 removed the fixed `1920x1080` video-size cap and implemented WATERMeDIA GPU texture paths for OpenGL and Vulkan. The AWT CPU path remains as a fallback. Vulkan uses the Minecraft graphics queue, and `VulkanQueueSynchronization` plus three Mixins serialize WATERMeDIA and Minecraft submission, waiting, and presentation.
>
> Sandbox testing showed 1080p H.264 at `1920x1080` with `hardwareAccel=true`; 4K AV1 Main 10-bit HLG at `3840x2160` used WATERMeDIA software decoding but a stable GPU texture path. The previous `VK_ERROR_DEVICE_LOST` occurred after roughly two seconds and no longer reproduced during more than one minute of continuous playback.
>
> The sections below preserve technical background and risk analysis. Recommendations that retained a 1280x720 cap are historical and superseded by the 0.2 implementation. 4K/8K behavior still depends on the source, codec, GPU, driver, and WATERMeDIA.

## Conclusion

WATERFrAMES itself does not configure a hard “1080p maximum” pixel limit. The older Fabric branch passed WATERMeDIA 2 media dimensions directly to an OpenGL texture. WATERMeDIA 3 provides explicit upload scaling (`scale`) and distance LOD (`LodLevel`).

Hardware decoding is possible, but a 4K/8K bitstream or an enabled setting does not prove that playback will be smooth. It requires:

1. GPU and driver support for the codec, profile, bit depth, and resolution.
2. The operating system and media backend able to create the required hardware device.
3. A frame path without expensive GPU-to-CPU readback.
4. Sufficient texture-upload, rendering, and audio-thread budgets.
5. A display size and viewing distance that justify the pixel count.

### 0.1.x recommendations and later outcome

The early 2026-11-14 CPU-readback assessment recommended:

- Keep `1280x720` as a safe output cap.
- Prefer 720p or 1080p sources.
- Treat 1080p as a per-device test tier.
- Consider 4K a formal target only after GPU texture passthrough.

Those recommendations explain the risk in the 0.1.x line but were superseded:

- OneScreen no longer imposes fixed 1280x720 or 1920x1080 limits.
- `LOD=MAX` uses WATERMeDIA’s actual output size.
- OpenGL/Vulkan GPU texture paths are implemented; the AWT path is only a fallback.
- 4K/8K hardware decoding and smoothness depend on the source, WATERMeDIA, FFmpeg, GPU, and driver.
- Report `format=GPU/CPU` separately from `hardwareAccel=true/false`.

## WATERFrAMES 1.21.1 Fabric behavior

The audit target was the WATERFrAMES 2.1.22 implementation using WATERMeDIA 2.1.34.

### No application-level pixel cap

`client/display/Display.java` obtained `width()`, `height()`, and `texture()` directly from:

- Images: `ImageRenderer.width/height/texture()`.
- Video: `VideoPlayer.width/height/texture()`.

`VideoPlayer.getBufferFormat(sourceWidth, sourceHeight)` requested an RGBA buffer at source dimensions:

```java
this.width = sourceWidth;
this.height = sourceHeight;
this.size = sourceWidth * sourceHeight * 4;
return new BufferFormat(Chroma.RGBA, sourceWidth, sourceHeight);
```

After each new frame, `VideoPlayer.preRender()` called `RenderAPI.uploadBuffer(...)` to upload the frame with `glTexImage2D` or `glTexSubImage2D`. That path had no resolution reduction, distance LOD, or total-pixel budget.

The main constraints were therefore:

- Source and hardware-decoder capability.
- RGBA frame-buffer memory.
- OpenGL `GL_MAX_TEXTURE_SIZE`.
- Per-frame RGBA upload bandwidth.
- VRAM and CPU capacity shared with Minecraft and other mods.

WATERFrAMES `maxWidth` and `maxHeight` limited the screen’s block size, not the video pixel size.

### Windows hardware-decoding candidates

WATERMeDIA 2.1.34’s Windows LibVLC parameter file included:

```text
--avcodec-hw d3d11va,dxva2,any
--vout direct3d11,glwin32,gl,any
--avcodec-fast
--avcodec-skip-idct=4
```

Windows therefore requested D3D11VA, DXVA2, or another hardware backend first. Linux settings did not contain the same `--avcodec-hw` entry, so code inspection alone could not establish equivalent Linux defaults.

“Hardware decoding” did not mean the frame remained on the GPU:

- LibVLC still delivered an RGBA callback surface to Java.
- WATERFrAMES then uploaded that buffer as an OpenGL texture.
- The chain could include decoder-frame readback, pixel conversion, and CPU-to-GPU upload again.

The old branch was a functional hardware-decoding reference, but not the best high-resolution architecture.

### Minimum single-frame RGBA size

At four bytes per pixel:

| Resolution | Bytes per frame | MiB per frame | Raw RGBA at 60 FPS |
|---|---:|---:|---:|
| 1280x720 | 3,686,400 | 3.52 | About 211 MiB/s |
| 1920x1080 | 8,294,400 | 7.91 | About 475 MiB/s |
| 3840x2160 | 33,177,600 | 31.64 | About 1.85 GiB/s |
| 7680x4320 | 132,710,400 | 126.56 | About 7.42 GiB/s |

These values exclude:

- Decoder-internal YUV frames.
- Java AWT / `BufferedImage` copies.
- Minecraft `NativeImage` copies.
- GPU textures.
- Multiple simultaneous screens.

An additional full CPU copy can approach or exceed these memory-bandwidth rates. 4K60 and 8K30/60 are often limited by copying and upload rather than decoding alone.

## WATERMeDIA 3 capabilities

### Hardware decoding defaults

The audited WATERMeDIA 3 implementation enables FFmpeg hardware acceleration by default through `WaterMediaConfig.codecs.ffmpeg.hardwareAccel`.

Configuration path:

```text
<Minecraft instance>/config/watermedia.toml
```

The exact backend and fallback behavior are selected by WATERMeDIA, FFmpeg, the GPU driver, and the source codec.

### Automatic hardware-backend selection

WATERMeDIA does not guarantee that a source will use a hardware decoder. It attempts hardware initialization and can fall back to software. The result must be read from the player’s hardware-acceleration state and logs rather than inferred from the URL or file extension.

### `scale` and LOD

WATERMeDIA 3 exposes a player LOD API. OneScreen uses:

- `LOD=MAX` at close range.
- `CLOSE`, `NEAR`, `FAR`, and `FAR_AWAY` at increasing normalized distances.
- A `0.05` hysteresis band around thresholds.
- `lod(...)` only when the selected tier changes.

Distance LOD reduces upload and sampling cost without imposing a fixed source-resolution cap.

### Vulkan zero-copy conditions

A Vulkan GPU texture path is only zero-copy or near-zero-copy when all of the following hold:

1. The decoder writes into a GPU surface compatible with the consumer.
2. WATERMeDIA and Minecraft use the same Vulkan device.
3. Media writes and Minecraft sampling use the same graphics queue.
4. Submission, waiting, and presentation use a shared queue lock.
5. Image ownership and retirement are valid across frames and session shutdown.

OneScreen’s current Vulkan adapter satisfies the same-queue and shared-lock contract. Missing any part can cause unstable images or `VK_ERROR_DEVICE_LOST`.

## OneScreen 26.2 path

OneScreen 0.2 has three output paths:

```text
WATERMeDIA GLEngine / VKEngine
  -> ExternalMediaTexture
  -> Minecraft GpuTextureView
  -> ScreenRenderer

or

WATERMeDIA AWTEngine
  -> BufferedImage
  -> NativeImage
  -> DynamicTexture
  -> ScreenRenderer
```

- The GPU paths avoid per-frame GPU-to-CPU readback.
- `format=GPU` is not the same as `hardwareAccel=true`.
- The CPU path remains necessary for unsupported backends or failed GPU initialization.
- OneScreen does not apply `maxSize(1920,1080)` or a player-scale cap; WATERMeDIA chooses the media output size.

## Historical options not adopted by 0.2

### A. Keep the CPU bridge with a 720p cap

The early design considered keeping `1280x720`, enabling WATERMeDIA hardware decoding, and documenting:

```text
720p: stable default
1080p: experimental, per-device validation
4K: only after device/codec validation
8K: unsupported promise
```

This was valid before GPU texture passthrough but is not the current implementation.

### B. Add fixed quality tiers

Another historical option introduced fixed `SAFE`, `HD`, and `NATIVE` tiers. It did not survive because the player API and source dimensions can change at runtime, and fixed caps block WATERMeDIA’s decode strategy.

### C. Add an optional GPU-native path

The adopted direction was a GPU-native OpenGL/Vulkan path with CPU fallback. It requires:

- Backend-specific texture wrappers.
- Correct resource ownership.
- Vulkan queue synchronization.
- Runtime fallback when GPU initialization fails.
- Separate OpenGL and Vulkan test matrices.

## Test matrix

At minimum, record:

- Source resolution, codec, profile, bit depth, frame rate, and color metadata.
- Output resolution and `format=GPU/CPU`.
- `hardwareAccel=true/false`.
- Frame latency and CPU/GPU utilization.
- RAM and VRAM growth over time.
- OpenGL and Vulkan results separately.
- Multiple-screen behavior.
- LOD transition stability.
- Session shutdown and immediate replay.

The local test sources are:

```text
http://localhost/1080p.mp4
http://localhost/4k.mp4
```

## Evidence index

- WATERMeDIA 3 configuration and decoder sources in the external `_watermedia-sources` reference tree.
- `FFMediaPlayer` and `MediaPlayer` for decode, timing, and scaling APIs.
- `AWTEngine` for the CPU-frame callback path.
- `GLEngine` and `VKEngine` for GPU texture publication.
- WATERFrAMES 1.21.1 `Display` and `VideoPlayer` for the historical CPU-upload path.
- OneScreen `MediaSession`, `ExternalMediaTexture`, `WaterMediaCompat`, `WaterMediaVulkanContext`, and `VulkanQueueSynchronization`.

## Final feasibility assessment

- OneScreen does not need a fixed 1080p cap.
- OpenGL/Vulkan GPU texture passthrough is the correct path for high-resolution media.
- 4K/8K hardware decoding is source- and device-dependent.
- The CPU AWT path remains a compatibility fallback, not a universal high-resolution solution.
- Vulkan queue synchronization is a hard correctness requirement.
- FHD/4K playback is supported experimentally in 0.2; long-duration, multi-screen, and OpenGL stress coverage remains incomplete.

See [Media, GPU Textures, and Audio](MEDIA-AUDIO.md) and [Testing](TESTING.md) for implementation and validation.

Return to the [English documentation index](README.md).