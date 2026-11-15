# OneScreen Scope and Acceptance Criteria

## Product goal

OneScreen provides a thin screen that can be attached to any block surface in Minecraft. It plays images, video, and audio from a URL stored by the server. The server owns screen configuration, while each client resolves media, manages its local player, and calculates distance-based volume independently.

## Current user flow

1. Obtain the 1x1x1/16 screen frame from the `OneScreen` creative tab.
2. Attach it to one of the six surfaces of a block.
3. Right-click the frame to open the editor, or let an administrator configure it with `/screen`.
4. The server validates and stores the URL, size, distances, alignment, fit mode, and related settings.
5. Clients receive the block-entity update, create a media session asynchronously, and render the result.
6. Leaving the chunk, changing dimensions, or disconnecting releases media resources; returning reloads them.

## Implemented scope

### Blocks and rendering

- Six-direction thin frame for horizontal, upward, and downward surfaces.
- Minable and drop-producing block with a dedicated creative tab, model, collision, textures, and loot table.
- Transparent display area when there is no media and debug mode is off; debug mode shows placeholder material.
- Optional hidden frame model while dynamic media and right-click interaction remain.
- In-plane rotation from `-180°` through `180°`.
- `fit`, `stretch`, `tile`, `fill`, and `center` media-fit modes.
- OpenGL and Vulkan support. Version 0.2 prefers WATERMeDIA GPU textures and falls back to CPU textures.

### Screen settings

- HTTP/HTTPS media URLs.
- Screen width and height in blocks.
- Nine-position alignment and in-plane rotation.
- Render distance, audio distance, volume, mute, and looping.
- Frame visibility, live-stream refresh, and load revision.
- Explicit defaults when loading old worlds that lack newer fields.

### Media and playback

- Images, video, HLS/M3U8, and audio are handled by WATERMeDIA 3.
- Play, pause, stop, manual refresh, and looping.
- Known-duration media can be seeked; live progress is read-only.
- Live refresh is disabled by default and applies only to live streams after pause/resume or configuration updates.
- Multi-channel IPTV playlists currently use only the first source.
- OneScreen does not impose a fixed 1080p pixel limit; `LOD=MAX` uses WATERMeDIA’s actual output size.

### Synchronization and security

- The server is the only authority for URLs and screen settings.
- Block-entity updates synchronize existing players and players entering the chunk.
- The editor and refresh button submit validated custom payloads.
- Mutation commands require permission level 2; `/screen info` is public.
- URLs must use HTTP/HTTPS and are limited by length, host, and control-character validation.

### Configuration

- `config/onescreen.json` provides maximum render and audio distances.
- Defaults are `256` / `128`; hard caps are `4096` / `1024`.
- The limits are used by commands and editor validation. Actual visibility still depends on chunk loading, client render distance, and server simulation distance.

## Non-functional requirements

- `src/main` must not reference `net.minecraft.client.*`, texture handles, players, or render classes.
- A client retains at most eight active media sessions by default; additional screens wait for capacity.
- Network requests, decoding, and media preparation must not block the render thread.
- Each screen has at most one active player; URL, reload revision, or lifecycle changes close the old session first.
- WATERMeDIA version differences stay inside `WaterMediaCompat` and `WaterMediaVersion`.
- Vulkan must use the Minecraft graphics queue, with the shared queue lock covering submission, waiting, and presentation.
- Documentation, logs, and test data must not contain tokens, private keys, private media URLs, or sensitive host paths.

## Explicitly out of scope

- Playlist management, remote controls, media search, uploads, or transcoding services.
- Automatic multi-block assembly, seamless display arrays, or projectors.
- Full 3D Euler rotation; only in-plane rotation is supported.
- Per-player URLs, complex permission groups, or a server-side URL allowlist.
- Built-in file servers, proxy servers, DRM bypass, or IPTV channel switching.
- WaterVision, Valkyrien Skies, Sable, or other third-party integrations.

## 0.2 acceptance gate

- `.\gradlew.bat build --offline --no-daemon --no-configuration-cache` passes.
- The sandbox client enters a world; six-direction rendering, interaction, and block drops work.
- Image, VOD, HLS metadata, pause, stop, refresh, and loop regressions pass.
- 1080p and 4K sources are each played at least once, with `format=GPU/CPU` and `hardwareAccel=true/false` recorded separately.
- OpenGL and Vulkan media-path regressions pass.
- All three Vulkan queue-synchronization Mixins are injected and no `VK_ERROR_DEVICE_LOST` occurs.
- Chunk unload, dimension change, and disconnect leave no player, texture, or audio residue.
- Unfinished items remain tracked in [Testing](TESTING.md) and must not be marked as passed.

See the complete matrix in [Testing](TESTING.md). Architecture constraints are documented in [Architecture](ARCHITECTURE.md) and [Architecture Decisions](DECISIONS.md).

Return to the [English documentation index](README.md).