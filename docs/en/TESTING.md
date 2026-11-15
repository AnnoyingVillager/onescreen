# Test Plan

## Principles

Each milestone should complete:

1. Compilation.
2. Launch of the relevant client or server.
3. Acceptance scenarios for the subsystem.
4. Diff review.
5. An independent Git commit.

Do not stack another subsystem on top of code that has not completed launch validation.

## Base commands

```powershell
.\gradlew.bat compileJava
.\gradlew.bat runClient
.\gradlew.bat runServer
```

If unit tests or GameTests are added before release, also run:

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
```

## Stage tests

### Project skeleton

- [ ] Client reaches the main menu.
- [ ] Dedicated server finishes loading.
- [ ] `fabric.mod.json` dependencies are correct.
- [ ] `src/main` contains no client imports.

### Block

- [ ] Block and item registration.
- [ ] Placement, breaking, and drops.
- [ ] World reload restores the block.
- [ ] Model, texture, language, and loot table are present.
- [x] The screen frame appears in the dedicated OneScreen creative tab and is not injected into the vanilla building-blocks tab.
- [x] A screen frame can be placed on an oak-stairs surface under Vulkan.
- [x] A screen frame can be placed on an oak-fence surface under Vulkan.
- [x] Removing the stair or fence it was originally placed against leaves the frame suspended and still identified as `onescreen:screen` by F3.
- [x] Removing the support block does not drop or self-destruct the frame; its original `FACING` remains unchanged.

### Persistence

- [ ] URL persistence.
- [ ] Size persistence.
- [ ] Volume and mute persistence.
- [ ] Invalid saved values are clamped.
- [ ] Block-entity update tag and packet work.

### Placeholder rendering

- [x] Four horizontal directions are correct.
- [x] For `facing=up/down`, the model, collision shape, and media quad occupy the same thin surface.
- [x] Six-direction dynamic media and alignment offsets work under Vulkan 1.4.329.
- [x] Up/down wide-face textures are on the correct side and all four narrow edges are textured.
- [x] The display area is transparent when there is no media and debug is off; debug on shows placeholder material.
- [x] Texture direction is correct.
- [x] Aspect ratios are correct.
- [x] Per-screen distance culling works.
- [x] Multiple screens do not corrupt each other’s pose state.

### Static images

- [x] PNG.
- [x] JPEG.
- [x] WebP.
- [ ] Empty URL shows the expected state.
- [ ] Unsupported format shows an error state.

### Video and audio

- [x] MP4 video.
- [x] MP4 audio; WATERMeDIA confirms the audio track starts.
- [x] Pause and resume.
- [x] Mute.
- [x] Distance attenuation using the 2–48 block linear range.
- [x] Non-looping media reaches end state.
- [x] Looping media repeats.
- [x] OpenGL: per-frame video updates, pause reports `PAUSED`, resume reports `PLAYING`.
- [x] Vulkan: per-frame updates, looping, and runtime configuration changes.
- [x] The final frame remains after non-looping video ends.
- [x] Play, pause, and stop work in the OpenGL and Vulkan editor; replay after stop reaches `PLAYING`.

### Progress, looping, and live resynchronization

- [ ] The right-click editor toggles looping; the setting applies immediately and survives world reload.
- [ ] Known-duration media displays total duration and seeks both video and audio to the target.
- [ ] Images, audio-only media, and loading media disable or mark the progress control without crashing.
- [ ] Live progress is read-only and displays approximate HLS segment progress; metadata failure degrades safely.
- [ ] Automatic live refresh defaults to off; when disabled, pause/resume does not rebuild the player.
- [ ] When enabled, resuming from the single-player pause menu moves a live stream toward the live edge without reopening VOD.
- [ ] When enabled, URL, size, distance, alignment, fit, loop, or refresh-setting changes reopen a live stream only when required.
- [ ] Repeated configuration changes within one second do not create a reconnect storm.
- [ ] OpenGL and Vulkan both verify the progress slider, loop checkbox, and live refresh.

### Manual refresh, rotation, and hidden frame

- [x] The `↻` button works after stopping playback.
- [x] The enlarged `↻` icon remains fully visible left of the playback controls.
- [x] Stop releases the media texture and clears the image; play restores it.
- [x] Manual pause survives closing the editor and does not resume when the game menu closes.
- [x] Refresh increments server `loadRevision`; the client closes the old session and rebuilds the same URL.
- [x] Play after refresh transitions from `LOADING` to `PLAYING`.
- [x] With HTTP offline, playback reaches `ERROR (ConnectException)`; after restarting the server, the editor `↻` resolves the URL in a new session and reaches `PLAYING`.
- [x] Rotation values from `-180°` through `180°` persist; old saves default to `0°`.
- [x] Rotated media, alignment, and audio center share the same geometry.
- [x] Horizontal and up/down directions use the same rotation plane.
- [x] Hidden frame defaults to off; enabling it removes the static model while retaining media.
- [x] The original interaction area still opens the editor after hiding the frame.
- [x] Rotation and hidden-frame state survive leaving and rejoining the world.
- [x] `/screen rotation`, `/screen hideframe`, and `/screen info` work in game.
- [x] Historical compatibility: WATERMeDIA 3.0.1 completed this regression under OpenGL 3.3 and Vulkan 1.4.329.
- [x] The editor fits all fields, checkboxes, and four playback controls at 856×512.

### Fit modes

- [x] The GUI can select fit, stretch, tile, fill, and center and persists through the payload.
- [x] `/screen fitmode <pos> <mode>` changes the mode and `/screen info` reports it.
- [x] Old worlds without `fitMode` default to `fit`.
- [ ] Verify all five modes with 16:9, 4:3, portrait, and ultrawide media.
- [ ] Verify tile UV repetition, edge seams, and transparent pixels.
- [ ] Verify six-direction screens, especially up/down aspect ratio and UV direction.
- [ ] Verify runtime switching and persistence under OpenGL and Vulkan.

### Distance configuration

- [x] `config/onescreen.json` structure and `256`/`128` defaults are wired into compilation.
- [x] The editor receives current server limits through the open payload.
- [x] Commands and editor payloads use configured limits rather than fixed constants.
- [ ] Single-player creates the configuration on first launch.
- [ ] Dedicated server creates the configuration on first launch.
- [ ] After increasing limits and restarting, the GUI and commands accept the new values.
- [ ] Verify culling, attenuation, and performance for distant screens under OpenGL and Vulkan.

### High resolution and hardware decoding

- [x] OneScreen has no fixed video-resolution cap; `LOD=MAX` uses WATERMeDIA’s actual output dimensions.
- [x] Vulkan 1080p H.264 outputs `1920x1080`, `format=GPU`, `hardwareAccel=true`.
- [x] Vulkan 4K AV1 Main 10-bit HLG outputs `3840x2160`, `format=GPU`; WATERMeDIA currently chooses software decoding while GPU texture upload remains functional.
- [ ] Complete OpenGL 1080p and 4K regressions.
- [ ] Record CPU, frame latency, VRAM, and RAM for 720p60, 1080p30, and 1080p60.
- [ ] Test H.264; record HEVC and AV1 success or failure per device.
- [ ] Record `format=GPU/CPU` (texture path) separately from `hardwareAccel=true/false` (decode path).
- [ ] Log WATERMeDIA hardware initialization or software fallback when `hardwareAccel=true`; playback must also work with `hardwareAccel=false`.
- [ ] Change distance across LOD thresholds only once without rebuilding textures repeatedly inside the hysteresis band.
- [ ] Verify portrait, ultrawide, square, and other media aspect ratios in all five fit modes.
- [ ] Run a 30-minute continuous Vulkan 4K stress test.
- [ ] Record 8K only as a per-device result; OneScreen does not block it but makes no universal smooth-playback promise.

### URL synchronization and commands

- [x] Mutation commands use `LEVEL_GAMEMASTERS`.
- [x] Server-to-client updates.
- [x] Late-joining and chunk-entry synchronization.
- [x] World-rejoin persistence.
- [x] Unchanged URLs are not reloaded for ordinary settings updates.
- [x] `/screen set|size|volume|mute|loop|reload|info`.
- [x] `/screen renderdistance|audiodistance|align` apply immediately.
- [x] URLs allow only HTTP/HTTPS; empty, overlong, control-character, and hostless URLs are rejected.
- [x] Non-screen targets return a clear error.
- [x] `reload` closes and reloads the same URL through the revision.

### Lifecycle

- [x] Chunk unload closes the session.
- [x] Returning to a chunk reloads it.
- [x] Dimension changes close sessions.
- [x] Disconnects close sessions.
- [ ] Rapid URL changes produce no duplicate audio.
- [ ] Breaking the block leaves no session behind.

## WATERMeDIA compatibility matrix

The current formal baseline is `WATERMeDIA 3.0.0 + Binaries 3.0.0`:

- [x] `3.0.0`: `compileJava`, `compileClientJava`, and `build` pass.
- [x] `WaterMediaVersion` handles `3.0.0.23 < 3.0.0 < 3.0.1 < 3.0.2`.
- [x] Player API, MRL, engine, and player differences are centralized in `WaterMediaCompat`.
- [x] `3.0.0 + Binaries 3.0.0`: Vulkan 1080p hardware decode and 4K GPU texture path pass.
- [ ] `3.0.0 + Binaries 3.0.0`: OpenGL 1080p/4K playback regression.
- [ ] Repeat the matrix after each later formal `3.0.x` release.

Historical records, not the current baseline:

- [x] `WATERMeDIA 3.0.0.23 + Binaries 3.0.0.6`: Vulkan video, stop, manual refresh, and replay regression.
- [x] `WATERMeDIA 3.0.1 + Binaries 3.0.1`: OpenGL 3.3 and Vulkan 1.4.329 regression.
- [x] The pre-first-frame `format() == null` crash was fixed on historical compatibility lines.

`3.0.0.x` test builds sort below formal `3.0.0` in `WaterMediaVersion`. Best-effort compatibility may be removed; new features and formal acceptance guarantee only `3.0.0` and later `3.0.x` formal releases.

## HLS / M3U8

- [x] URL validation allows HTTP/HTTPS `.m3u8` URLs.
- [x] WATERMeDIA 3 M3U8/M3U handling parses HLS master playlists, media playlists, and IPTV lists.
- [x] The FFmpeg path handles HLS demuxing, live/VOD probing, timestamp discontinuities, and HLS variant reopen.
- [x] The pre-first-frame `format() == null` crash is fixed and smoke-tested on historical compatibility versions.
- [x] Historical local HTTP HLS master → media playlist smoke tests parsed a 6000 ms live segment on older compatibility versions.
- [x] Full Java 25 compilation, JAR validation, VOD/live progress mapping, and default-setting smoke tests pass.
- [ ] HLS VOD playback, pause, end, and loop in game.
- [ ] HLS live playback, pause, and reconnect behavior in game.
- [ ] Multi-channel IPTV behavior; only `source(0)` is currently used and channels do not switch automatically.

## Multiplayer test

Prepare clients A and B:

```text
1. A places a screen.
2. B can see the block.
3. A changes the URL.
4. Both clients see the update.
5. A and B stand at different distances.
6. Verify independent volume calculations.
7. B leaves the chunk.
8. Verify B releases the media session.
9. B returns to the chunk.
10. Verify B reloads the media.
```

## Invalid-input tests

| Input | Expected result |
|---|---|
| Empty string | Shows the unset state |
| Overlong URL | Command rejects it |
| `javascript:` | Command rejects it |
| `file://` | Rejected |
| 404 URL | Error placeholder without a crash |
| Working URL becomes 404 | Old media closes and the new session fails |
| Unsupported format | Error placeholder |
| Media server timeout | Cancellable and recoverable |
| High-frequency URL changes | Old requests are cancelled or ignored |
| Nonexistent block position | Command returns a clear error |

## Performance checks

Run continuously for 30 minutes and record:

- [ ] Active `MediaSession` count remains stable.
- [ ] With nine screens, active sessions stay at or below eight and excess screens wait.
- [ ] Audio-source count remains stable.
- [ ] GPU texture count remains stable.
- [ ] Memory does not grow continuously.
- [ ] Repeated chunk loading does not create duplicate sessions.
- [ ] Error logs do not grow every frame.
- [ ] LOD logs appear only when the tier changes.
- [ ] `LOD=MAX` outputs the source size; output size and texture rebuilds match distance-tier changes.
- [ ] CPU fallback continues to use batch buffers and GPU paths avoid CPU frame readback.

There is currently no `/screen debug sessions` command. Use log filtering, a debugger, or temporary read-only diagnostics; do not document a temporary command as a released feature:

```powershell
Select-String -Path 'run\logs\latest.log' -Pattern 'Media texture ready|Video decode|Media error|Failed to release' -Context 1,2
```

## Pre-release checks

- [x] Version updated.
- [x] Changelog updated.
- [x] WATERMeDIA and Binaries versions documented in README.
- [x] Release JAR excludes local Binaries.
- [x] Client and server installation instructions are complete.
- [x] Known limitations are in the release notes.
- [x] OpenGL path tested.
- [x] Vulkan path tested on 26.2 Vulkan 1.4.329.
- [x] No sensitive URL appears in logs or test files.

## 0.2 Vulkan high-resolution and GPU texture regression (2026-11-15)

Environment:

- Minecraft `26.2`
- Fabric Loader `0.18.6`
- Vulkan `1.4.329`
- NVIDIA GeForce RTX 5070 Ti, driver `596.49`
- WATERMeDIA `3.0.0` + Binaries `3.0.0`
- Sandbox client inside `E:\waterframes\onescreen` only

Results:

- [x] Build: `gradlew build --offline --no-daemon --no-configuration-cache` passed.
- [x] `onescreen.client.mixins.json` loaded and all three Vulkan queue-sync Mixins injected.
- [x] `http://localhost/1080p.mp4` output `1920x1080`, `format=GPU`, `hardwareAccel=true`.
- [x] `http://localhost/4k.mp4` output `3840x2160`, `format=GPU`; WATERMeDIA selected software decoding for the AV1 10-bit source.
- [x] Failed-MRL recovery regression: after HTTP returned, the same `4k.mp4` screen reached `PLAYING` through the editor `↻`; logs confirmed `3840x2160`, `format=GPU`, and `backend=Vulkan`.
- [x] Continuous 4K playback exceeded one minute without `Retention wait failed`, `VK_ERROR_DEVICE_LOST`, or `GpuDeviceLossException`.
- [x] No new crash report was generated after the fix.
- [x] Switching between 1080p and 4K, closing the old session, and creating a new one worked.

Root cause and implementation:

- WATERMeDIA’s Vulkan texture path requires the consumer’s graphics queue and must serialize submission and presentation with the same `queueLock()`.
- The old implementation selected an independent compute/transfer queue without cross-queue semaphores.
- Current OneScreen, Minecraft submission, idle waiting, and swapchain presentation share one lock keyed by the `VkQueue` handle.

Remaining tests:

- [ ] OpenGL 1080p/4K regression.
- [ ] Continuous Vulkan 4K 30-minute stress test.
- [ ] Multiple simultaneous 1080p/4K screens, rapid URL switching, and immediate replay after stop.

Return to the [English documentation index](README.md).