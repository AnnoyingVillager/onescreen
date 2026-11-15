# Architecture Decision Records

This file records decisions that affect multiple modules or would be difficult to reverse later. New decisions are appended at the end.

## ADR-001: Create a focused project instead of rewriting WATERFrAMES

- Status: Accepted
- Date: 2026-11-13

### Context

The WATERFrAMES Fabric branch contains a CreativeCore GUI, old WATERMeDIA 2 media interfaces, and a larger block and networking system. OneScreen only needs a screen.

### Decision

Create a minimal Fabric 26.2 project and reuse only the geometry, data-model, and media-lifecycle ideas.

### Consequences

- Media integration had to be rewritten initially.
- The project does not carry the full WATERFrAMES legacy structure.
- The codebase is easier to validate and maintain.

## ADR-002: Use Fabric split client source sets

- Status: Accepted
- Date: 2026-11-13

### Decision

Place server-safe code and block data in `src/main`; place rendering and media code in `src/client`.

### Consequences

- Dedicated servers never load client media classes.
- Renderer registration needs a separate client initializer.
- Maintainers and Agents must respect the source-set boundary.

## ADR-003: The first version does not use Mixins or access wideners

- Status: Superseded by ADR-022, which permits only the narrow Vulkan queue-synchronization Mixins
- Date: 2026-11-13

### Decision

Use public events, `BlockEntityRenderer`, and public WATERMeDIA APIs.

### Consequences

- Avoid dependence on private fields that can change.
- If public APIs are insufficient, reduce scope before injecting internal code.

## ADR-004: Use WATERMeDIA 3 instead of Minecraft’s private audio engine

- Status: Accepted
- Date: 2026-11-13

### Decision

WATERMeDIA 3 handles media decoding, video textures, and media audio. The client calculates distance-based volume.

### Consequences

- Do not implement a complex Minecraft sound-attenuation integration.
- Avoid `SoundEngine`, `Channel`, and access wideners.
- A future native attenuation implementation should use a stable WATERMeDIA extension point.

## ADR-005: Synchronize URLs through block-entity updates

- Status: Accepted
- Date: 2026-11-13

### Decision

Only server commands modify the URL initially. Synchronization uses `getUpdateTag`, `getUpdatePacket`, and `sendBlockUpdated`.

### Consequences

- No custom client-to-server payload was needed for the first command-only editor.
- The URL remains server-authoritative.
- A validated custom payload can be added when the GUI is introduced.

## ADR-006: Use one block as the anchor while allowing a larger visual size

- Status: Accepted
- Date: 2026-11-13

### Decision

Keep collision and placement as one block; expand render geometry according to screen width and height.

### Consequences

- No multi-block system is required initially.
- Rendering distance and culling must account for the larger display.
- Wide screens can differ from block collision behavior, which is an accepted early tradeoff.

## ADR-007: Allow only HTTP/HTTPS in the first release

- Status: Accepted
- Date: 2026-11-13

### Decision

Reject `file://`, `javascript:`, and all other protocols.

### Consequences

- Safer URL handling.
- Local-file support is deferred to a dedicated `water://local/` design with explicit root boundaries.

## ADR-008: Pin Loom to 1.19.4

- Status: Accepted
- Date: 2026-11-13

### Context

The official Fabric 26.2 template declared `1.19-SNAPSHOT`. The first successful Gradle configuration resolved it to `1.19.4`.

### Decision

Pin `fabric-loom 1.19.4` during development to avoid snapshot drift and non-reproducible builds.

### Consequences

- Builds resolve the same Loom version.
- Loom upgrades require separate validation of Minecraft 26.2 project configuration and client/server launch.
- Record the final resolved version before release.

## ADR-009: Use global block-entity rendering and explicit distance checks in 26.2

- Status: Accepted
- Date: 2026-11-13

### Context

Minecraft 26.2’s `BlockEntityRenderer` no longer exposes the `getRenderBoundingBox` method assumed by the original plan.

### Decision

Set `shouldRenderOffScreen() = true` so the screen renderer is considered globally, then use `shouldRender()` to compare the camera with configured render distance and display radius. Submit geometry through `SubmitNodeCollector.submitCustomGeometry`.

### Consequences

- Large screens are not culled solely because the anchor chunk section is outside the default render set.
- Distant screens are still culled by their configured distance.
- Rendering remains backend-independent and does not depend on OpenGL-specific texture binding.

## ADR-010: M5 uses WATERMeDIA HeadlessGFXEngine and Minecraft DynamicTexture

- Status: Accepted
- Date: 2026-11-13

### Context

A WATERMeDIA GPU `texture()` handle is meaningful only inside its concrete graphics backend. Minecraft 26.2 `RenderType` resolves textures through `TextureManager` and an `Identifier`. Wrapping the raw handle as an OpenGL texture would violate backend independence.

### Decision

Static images use the public WATERMeDIA `HeadlessGFXEngine`:

- Create the engine through `PlayerAPI.headlessEngine()`.
- Read public `uploadCount()`, `format()`, `width()`, `height()`, and `lastUpload()`.
- Copy a single-plane 8-bit `BGRA`/`RGBA`/`RGB`/`GRAY` frame into a Minecraft `NativeImage`.
- Register a stable dynamic `Identifier` through `DynamicTexture` and `TextureManager`.
- Use clamp-to-edge and linear filtering; release the texture and session when the player, chunk, or connection closes.
- Disconnect callbacks are not on the client thread, so cleanup must first dispatch through `Minecraft.execute(...)`.

### Consequences

- M5 does not use Mixins, access wideners, `GlTexture` wrappers, or `GL11.glGetTexImage`.
- Static images perform one CPU pixel copy; PNG, JPEG, and WebP were verified in game.
- This approach is not suitable for continuous per-frame video copies. M6 must add frame updates or a public backend texture bridge and choose based on performance tests.
- Each screen keeps one stable texture `Identifier` while the player and session lifecycle close independently.

## ADR-011: M6 uses a software video engine and JavaScript audio for backend-independent playback

- Status: Partially superseded by ADR-023; AWT remains the CPU fallback
- Date: 2026-11-13

### Context

The M5 `HeadlessGFXEngine` exposes only the first plane and cannot fully represent multi-plane YUV frames common in MP4 decoding. Passing WATERMeDIA OpenGL/Vulkan GPU handles directly to Minecraft `RenderType` would reintroduce backend coupling. Using `ALEngine` directly would also require the Minecraft OpenAL context to be current on the calling thread.

### Decision

M6 uses public `PlayerAPI.awtEngine(...)` for video:

- `SWEngine` rejects YUV/RGB layouts; WATERMeDIA converts to packed BGRA/RGBA first.
- Each `onFrame` callback increments a frame sequence. The client render thread copies only when the sequence changes, writes packed ARGB into `NativeImage`, and calls `DynamicTexture.upload()`.
- The historical 1280x720 upload cap prevented unbounded CPU copying in the first release.

Audio uses public `PlayerAPI.jsEngine()`:

- Java Sound does not depend on the current OpenAL context and does not require Minecraft-private `SoundEngine` or `Channel`.
- Volume, mute, loop, and distance gain are applied directly to the `MediaPlayer`.
- The single-player pause menu pauses the player and resumes it on close; in multiplayer the server continues advancing the media clock.

### Consequences

- The same implementation was verified under OpenGL 3.3 and Vulkan 1.4.329.
- It does not reference `GL11`, `GlTexture`, Vulkan native handles, Mixins, or access wideners.
- The video path includes decoder output, ARGB conversion, and `NativeImage` writes; CPU cost is higher than GPU passthrough.
- Java Sound provides local client stereo output and no OpenAL positional panning; only distance volume was required initially.

## ADR-012: Six-direction thin frames must keep the model and media quad on the same surface

- Status: Accepted
- Date: 2026-11-14

### Context

After the frame became a 1×1×1/16 six-direction thin block, the `facing=up/down` blockstate rotation was reversed. The static model appeared on the opposite side of the block while the dynamic media quad remained on the intended face.

### Decision

- The base model thin plate occupies the north side `z=15..16`. Both wide faces use `screen_frame` with a transparent center and opaque border; the four narrow edges keep their side textures.
- The blockstate uses `facing=up: x=270` and `facing=down: x=90`; horizontal directions use `y=0/90/180/270`.
- The block `VoxelShape`, model rotation, and media quad must use the same facing surface.
- The media-plane center uses `0.5 - facing.step × 0.4365`, placing it just inside the facing surface. Do not offset in the positive facing direction.

### Consequences

- Up/down thin plates must land on the correct bottom/top face of the `VoxelShape`; reversed rotation can put the texture on the opposite side even when collision is correct.
- With no media and debug off, only the physical frame remains and the center is transparent; debug on submits placeholder material.
- Changes to model plate direction or blockstate rotation require six-direction regression, not just horizontal tests.

## ADR-013: Put the screen frame in a mod-specific creative tab

- Status: Accepted
- Date: 2026-11-13

### Context

The screen frame was initially injected into `CreativeModeTabs.BUILDING_BLOCKS`. It is an OneScreen media device and does not belong in the vanilla building-block category.

### Decision

- Create and register `onescreen:screen` with `FabricCreativeModeTab.builder()`.
- Use `itemGroup.onescreen` as the tab title and the screen frame as the icon and only displayed item.
- Remove the `CreativeModeTabs.BUILDING_BLOCKS` output injection so the item does not appear in the vanilla building tab.

### Consequences

- The screen frame is obtained from the OneScreen tab; future mod items should also prefer that tab.
- New items must be checked in both the dedicated tab and the search page.

## ADR-014: Default video to 720p and validate LOD, batch copying, and hardware decoding in stages

- Historical note: This records the 0.1.x design and no longer represents the 0.2 fixed-cap policy.
- Status: Superseded by ADR-023
- Date: 2026-11-14

### Context

The WATERFrAMES 1.21.1 reference implementation passed source resolution into OpenGL textures. WATERMeDIA 3 can attempt hardware decoding, but OneScreen’s AWT software output still involved GPU-to-CPU readback and `BufferedImage`/`NativeImage` copies. Unbounded 4K/8K playback would make VRAM, bandwidth, and main-thread cost unpredictable.

### Decision

- Keep the default video output at `1280x720`.
- Add `1920x1080` only as a per-device test tier.
- Apply WATERMeDIA `LodLevel` from normalized `renderDistance` with hysteresis.
- Call `MediaPlayer.scale(...)` before `start()` and avoid repeated equal size changes.
- Convert ARGB video frames to `NativeImage` through a native-order `IntBuffer`.
- Leave hardware decoding to WATERMeDIA `hardwareAccel` and software fallback; do not fake hardware state.
- Treat GPU-native texture passthrough as a separate milestone.

### Consequences

- 720p remained the baseline while per-pixel cost was reduced and 1080p was validated.
- Distance reduces upload cost without rebuilding textures at boundaries.
- 4K/8K remained experimental until hardware/software, OpenGL/Vulkan, multi-screen, and 30-minute tests were complete.
- Version 0.2 replaced this fixed cap with GPU texture paths and dynamic LOD.

## ADR-015: Support WATERMeDIA 3.0.0.23 and 3.0.1 in one JAR through reflection

- Status: Partially superseded by ADR-024; compatibility layer retained, formal baseline changed to 3.0.0
- Date: 2026-11-14

### Context

WATERMeDIA `3.0.0.23` exposes its player API under `org.watermedia.api.media`, while `3.0.1` moved it to `org.watermedia.api.player`. Directly linking either class causes `ClassNotFoundException` or `NoClassDefFoundError` under the other version.

### Decision

- Business code references only neutral wrapper types in `WaterMediaCompat`.
- At startup, detect `PlayerAPI` first and fall back to legacy `MediaAPI`.
- Create the MRL, image/audio engines, player factory, playback controls, and size controls through public reflection.
- The graphics-engine supplier passed to the player must return the native WATERMeDIA `GFXEngine`, not an OneScreen wrapper.
- Keep `3.0.0.x` as best-effort compatibility; the formal target is WATERMeDIA `3.0.0` and later `3.0.x`.

### Consequences

- One OneScreen JAR can handle both WATERMeDIA package layouts.
- Reflection lacks compile-time signature checks, so every WATERMeDIA upgrade needs smoke tests.
- The formal combination is WATERMeDIA `3.0.0` + Binaries `3.0.0`; older `3.0.0.x` + `3.0.0.6` is best-effort only.
- OneScreen does not bundle native libraries.

## ADR-016: A null media format before the first frame is valid

- Status: Accepted
- Date: 2026-11-14

### Context

WATERMeDIA 3.0.1 can return `null` from `GFXEngine.format()` before an image engine uploads its first frame. The compatibility layer once converted null to an empty string and used it in a `switch`, causing a render-thread `NullPointerException` and a `Render Frame` crash. Logs showed HTTPS images had loaded successfully, so HTTPS was not the cause.

### Decision

- A `null` format means frames are not ready. It is not an error and must not crash render-state extraction.
- Skip the frame when the pixel format is null, upload count is zero, or dimensions are not ready.
- Normalize state and media-type names; map unknown values to `UNKNOWN` or an empty pixel format.
- Isolate media reads during render extraction and convert exceptions into a single media error instead of a Minecraft render crash.

### Consequences

- The client can safely wait for the first image frame.
- `3.0.0.23` and `3.0.1` passed `format(null)` smoke tests.
- HLS/M3U8 was not the crash cause. OneScreen allows HTTP/HTTPS M3U8 URLs and relies on WATERMeDIA’s HLS path.

## ADR-017: Use five explicit fit modes for media aspect ratios

- Status: Accepted
- Date: 2026-11-14

### Context

A single “preserve aspect ratio” behavior cannot cover cropping, stretching, tiling, and natural-size requirements when media and screen aspect ratios differ.

### Decision

- `fit`: preserve aspect ratio and show the complete media with transparent empty space.
- `stretch`: fill the quad without preserving aspect ratio.
- `tile`: preserve aspect ratio and repeat using one block along the longest media edge as the base.
- `fill`: preserve aspect ratio, cover the screen, and crop overflow through UVs.
- `center`: preserve aspect ratio and center the natural one-block-long-edge size.
- Default to `fit` so upgraded old worlds keep their visual behavior.
- Keep calculations in client-independent `ScreenFitGeometry`; the renderer only submits geometry and UVs.

### Consequences

- Tiling relies on the `DynamicTexture` repeat sampler; UV values may exceed `1.0`.
- The GUI, commands, block-entity persistence, and editor payload all carry `ScreenFitMode`.
- The one-block natural size is a stable world-space definition independent of decoder output resolution.

## ADR-018: Control distance limits with server JSON configuration

- Status: Accepted
- Date: 2026-11-14

### Context

Fixed `256`-block render and `128`-block audio limits cannot satisfy large single-player screens or large server venues. The client GUI and server validation must use the same limits; otherwise configuration only affects commands.

### Decision

- Add `config/onescreen.json` with `maxRenderDistance` and `maxAudioDistance`.
- Keep defaults `256`/`128`; use hard caps `4096`/`1024` to prevent invalid values from breaking float ranges and renderer distance methods.
- Load the file on the integrated or dedicated server and send active limits through `ScreenEditorOpenPayload`.
- Validate commands and editor updates against server configuration.
- Existing screen values are not automatically rewritten when limits are reduced.

### Consequences

- Clients do not maintain their own server-limit configuration; multiplayer editor sessions use the server values.
- The renderer uses the hard cap for global view distance; each screen still uses its own `renderDistance` and `shouldRender`.
- Large distances increase load and require a restart after configuration changes.

## ADR-019: Add progress, live resynchronization, and looping to the screen editor

- Status: Accepted
- Date: 2026-11-14

### Context

When a single-player pause menu opens, a live stream continues advancing on the server while `MediaSession` freezes the player clock from the game-pause state. After resume, audio and video can lag behind the live edge. Users also need direct progress and loop controls for known-duration media.

### Decision

- Add persisted boolean `refreshOnResume` to `ScreenSettings`, default `false`, synchronized through editor payloads, block-entity NBT, and server validation.
- Add loop, progress-slider, and live-resynchronization controls to the editor.
- Use WATERMeDIA `time()`/`duration()`/`canSeek()` for VOD; call `seekQuick()` while dragging and `seek()` on release.
- Make live progress read-only. Read HLS segment duration from WATERMeDIA helpers asynchronously, with an independent HTTP playlist parser as fallback. Calculate approximate segment progress from player time and target duration; do not fabricate a percentage when metadata is unavailable.
- Automatic resynchronization applies only when `live()` or legacy `liveSource()` is true. Pause/resume or settings changes reopen the same URL from the live edge, at most once per second.
- When disabled, never reconnect for this feature. VOD and images are not reopened by this option.

### Consequences

- Live catch-up can cause a short buffer/black frame and audio reinitialization, which is why it defaults off.
- HLS segment progress is approximate target-duration progress, not an exact media-sequence guarantee.
- Loop state can now be saved from the GUI; `/screen loop` remains compatible.
- New features primarily target WATERMeDIA `3.0.0` and later `3.0.x`; older `3.0.0.x` receives best-effort regression only.

## ADR-020: Use a compact editor and explicit playback-control state

- Status: Accepted
- Date: 2026-11-14

### Context

The right-click editor clipped bottom controls at 856×512 and high GUI scaling, and numeric labels were vertically misaligned from inputs. After adding play, pause, and stop, editor pause behavior also had to coexist with the single-player pause menu, live resynchronization, and unexpected-stop detection.

### Decision

- Keep the editor panel at `400×236`, constrain it to the viewport, use `15px` control height, and vertically center labels with fields.
- Place play, pause, and stop in the bottom green control area; disable them until media is ready.
- While the editor is open, explicit play/pause uses `MediaSession.manualPauseOverride`; closing the editor clears the override and follows game pause state again.
- Use `manuallyStopped` for explicit stop so `STOPPED` is not misclassified as an error. When replaying from `STOPPED/ENDED`, keep the flag until the player leaves the terminal state.
- Re-check that the session still owns a player after each tick update before querying live state; never dereference a player released in the update path.

### Consequences

- Immediate replay after stop reaches `LOADING → PLAYING` on WATERMeDIA 3.0.1.
- The compact editor passed visual checks at 856×512 under OpenGL and Vulkan.
- Explicit stop, pause override, and unexpected stop are separate states that future lifecycle work must preserve.

## ADR-021: Add manual refresh, in-plane rotation, and hidden frame material

- Status: Accepted
- Date: 2026-11-14

### Context

A stopped or failed player cannot always recover through Play alone. Building displays also require the frame not to cover the environment and require arbitrary in-plane angles. Settings must remain server-authoritative rather than being hidden or rotated only on the client.

### Decision

- Add the `↻` manual-refresh button to the left of the playback group. It depends only on a saved URL, not on player readiness.
- Send a validated `ScreenReloadPayload`; the server checks permission, edit distance, target block, and URL, then calls `reload()` to increment `loadRevision`. The media coordinator closes and rebuilds the session.
- Add persisted `rotationDegrees` (`-180..180`) and `hideFrame` (default `false`) to `ScreenSettings`.
- Use `ScreenGeometry.right/up(facing, rotationDegrees)` for both rendering and the audio center; alignment offsets follow rotated local axes.
- Add a boolean `hidden` blockstate. When hidden, skip the static block model while retaining the original `VoxelShape`, block entity, media renderer, and interaction.
- Carry new fields through open/update payloads, NBT, `/screen info`, GUI, and commands; old saves fall back to `0°` and `false`.
- Add `/screen rotation <pos> <degrees>` and `/screen hideframe <pos> <true|false>`.

### Consequences

- Manual refresh briefly rebuilds the player; a live stream reconnects from the current live edge.
- Hiding the frame does not break the block or remove media; the original thin interaction area still opens the editor.
- `-180..180` covers all plane orientations. If unrestricted input is added later, normalize it before saving.
- New settings must continue to update server validation, GUI payloads, block-entity persistence, and release documentation together.

## ADR-022: Share the Minecraft graphics queue for Vulkan GPU textures and serialize with Mixins

- Status: Accepted
- Date: 2026-11-15

### Context

WATERMeDIA `VKEngine` texture publication assumes media writes and consumer sampling occur on the same Vulkan graphics queue. The first 0.2 bridge used a separate compute/transfer queue without cross-queue semaphores, causing `VK_ERROR_DEVICE_LOST` about two seconds into 4K playback.

### Decision

- `WaterMediaVulkanContext.queue()` returns Minecraft `graphicsQueue()`.
- `VulkanQueueSynchronization` provides one lock keyed by the native `VkQueue` handle.
- `VulkanQueueSubmissionMixin`, `VulkanQueueMixin`, and `VulkanGpuSurfaceMixin` lock Minecraft submission, waiting, and presentation.
- Do not remove these Mixins or return to an independent queue unless a complete cross-queue synchronization implementation passes device-loss stress tests.

### Consequences

- WATERMeDIA GPU textures and Minecraft use the same queue and lock, satisfying external synchronization.
- This is the only permitted Mixin area in the project.
- Every Minecraft or WATERMeDIA update requires rechecking injection targets and continuous 4K playback.

## ADR-023: Remove the fixed video-resolution cap while retaining distance LOD

- Status: Accepted
- Date: 2026-11-15

### Context

The 0.1.x line limited video to 1280x720/1080p test tiers to avoid unbounded CPU copies. After 0.2 added GPU textures, keeping an OneScreen-side maximum would prevent WATERMeDIA from selecting higher resolutions for the source and device.

### Decision

- Remove `VIDEO_MAX_WIDTH`, `VIDEO_MAX_HEIGHT`, and player `maxSize(...)` limits.
- Retain `VideoLod` based on normalized distance with hysteresis.
- Allow source size at close range when `LOD=MAX`.
- Leave hardware/software decode selection to WATERMeDIA, FFmpeg, the GPU, driver, and source.
- Fall back to the AWT CPU path when GPU texture initialization fails.

### Consequences

- A 4K texture can be published at `3840x2160`.
- `format=GPU` does not mean hardware decoding; stable GPU texture upload is possible with `hardwareAccel=false`.
- 4K/8K high-frame-rate performance still requires per-device stress testing and cannot be promised universally.

## ADR-024: Make WATERMeDIA 3.0.0 the formal baseline

- Status: Accepted
- Date: 2026-11-15

### Decision

- Formal baseline: WATERMeDIA `3.0.0` with Binaries `3.0.0`.
- `3.0.0.x` builds sort below `3.0.0`.
- `3.0.x` where `x > 0` sorts above all `3.0.0.x` builds.
- Declare loader range `>=3.0.0.0 <3.1.0` and apply the special ordering at runtime through `WaterMediaVersion`.
- Keep old `3.0.0.x` package layouts only as best-effort compatibility, not as the primary target for new features.

### Consequences

- A normal four-part version comparator cannot be used.
- All WATERMeDIA player API differences remain in `WaterMediaCompat`.
- WATERMeDIA upgrades require checking the actual package layout, matching Binaries, and sandbox runtime logs.

## ADR-025: Separate Agent documentation from human documentation

- Status: Accepted
- Date: 2026-11-15

### Decision

- `agents/` contains the Agent entry point, project context, development log, and runbook.
- Root `README.md`, `README.en.md`, and `docs/` are for human users and developers, covering installation, usage, architecture, rendering, media, networking, and testing.
- Root `AGENTS.md` contains only the minimal navigation required for automatic discovery and does not duplicate detailed rules.

### Consequences

- New Agents can follow the fixed order in `agents/README.md`.
- Human documentation does not repeatedly maintain “instructions for the next AI.”
- After link changes, search the repository for obsolete filenames to prevent broken paths.

Return to the [English documentation index](README.md).