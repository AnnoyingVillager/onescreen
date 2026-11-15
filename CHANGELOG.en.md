# Changelog

This project follows semantic versioning. Release dates use the local `YYYY-MM-DD` format.

## [0.2.1] - 2026-11-15

### Fixes

- Screen frames no longer require a full, sturdy support block and can be placed on clickable non-full surfaces such as stairs and fences.
- Frames no longer store or validate a support block. Removing the block they were originally placed against leaves them suspended instead of self-destructing or dropping.
- Fixed WATERMeDIA caching a failed MRL by URL while the local HTTP service was offline. After the service returns, the `↻` button in the right-click editor now regenerates the resource and restores playback.
- In-game verification confirmed the recovered 4K video reached `PLAYING` at `3840x2160`, `format=GPU`, on the Vulkan backend.


### Documentation

- Split human documentation into `docs/zh-cn/` and `docs/en/`.
- Added English counterparts for all human-facing usage and secondary-development documents.
- Updated README and internal links to the language-specific documentation trees.

## [0.2.0] - 2026-11-15

High-resolution media and GPU texture update.

### Added and improved

- OneScreen no longer fixes video output at `1920x1080`; at close range, `LOD=MAX` lets WATERMeDIA choose output dimensions from the source and device capabilities.
- OpenGL now integrates WATERMeDIA GPU textures. Vulkan integrates `VKEngine`, the Minecraft Vulkan device, and external texture views.
- The AWT CPU texture fallback remains, with batched video-pixel copying into `NativeImage`.
- Added `VideoLod`, which lowers upload quality with distance and applies hysteresis around tier boundaries.
- Updated the WATERMeDIA build baseline to `3.0.0` and Binaries to `3.0.0`; added the special `3.0.0.x < 3.0.0 < 3.0.x` ordering.
- Fixed Vulkan submission, waiting, and presentation synchronization on the shared graphics queue. The previous independent compute/transfer queue caused `VK_ERROR_DEVICE_LOST`.
- Sandbox testing showed 1080p using hardware decoding. The 4K AV1 10-bit HLG source used WATERMeDIA software decoding while the GPU texture upload remained functional.
- Consolidated Agent documentation under `agents/` and reorganized README and `docs/` as human-facing usage and implementation documentation.

## [0.1.2] - 2026-11-14

Playback-control and icon fixes.

### Fixes

- Enlarged the manual-refresh icon left of the playback controls for better visibility at small UI scales.
- Rebuilt the mod icon from the `screen_frame` material at `128×128`, using nearest-neighbor scaling and a high-contrast screen area.
- Stop now releases the media texture and clears the video or image from the screen; Play can reload it.
- Pausing in the editor now remains `PAUSED` after closing the editor and does not resume when the game menu closes.

### Compatibility

- Preserved runtime compatibility with WATERMeDIA `3.0.0.23` + Binaries `3.0.0.6` and WATERMeDIA `3.0.1` + Binaries `3.0.1`.
- WATERMeDIA `3.0.0.23` remains initial best-effort compatibility and may be removed later.

[0.2.1]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.2.1

[0.2.0]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.2.0

[0.1.2]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.1.2

## [0.1.1] - 2026-11-14

Screen-control and display improvements.

### Added

- A manual-refresh button left of the playback controls can recreate a media session after stop or player failure.
- Screens can rotate by any continuous angle from `-180°` through `180°`; media, alignment, and audio center share the same rotation geometry.
- Added an optional hidden-frame mode, disabled by default. It hides the static frame model while retaining media and right-click editing.
- Added `/screen rotation <pos> <degrees>` and `/screen hideframe <pos> <true|false>`. `/screen info` reports the new state.
- Settings payloads, block-entity NBT, and old-world fallback logic now include rotation and frame visibility.

### Validation

- WATERMeDIA `3.0.1` + Binaries `3.0.1` passed in-game under OpenGL 3.3 and Vulkan 1.4.329.
- Verified stop, manual refresh, replay, arbitrary rotation, hidden frame, interaction, and persistence.
- WATERMeDIA `3.0.0.23` + Binaries `3.0.0.6` completed Vulkan video, hardware-decode, stop, refresh, and replay regression. The old combination is initial best-effort compatibility and may be removed.

[0.1.1]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.1.1

## [0.1.0] - 2026-11-14

First public release.

### Added

- Minecraft 26.2 / Fabric 1×1×1/16 six-direction screen frame.
- PNG, JPEG, WebP, MP4, HLS/M3U8, and other WATERMeDIA media.
- Server-authoritative URL, size, distance, volume, mute, loop, and nine-position alignment synchronization.
- Fit, stretch, tile, fill, and center aspect-ratio modes.
- Progress, loop, live-refresh, play, pause, and stop controls in the editor.
- OpenGL and Vulkan rendering paths.
- Initial WATERMeDIA 3.0.0.23 compatibility and 3.0.1 runtime compatibility.
- Dedicated OneScreen creative tab.
- `/screen` commands and `config/onescreen.json` distance limits.

### Fixes

- Fixed a render crash when `format() == null` before an image’s first frame.
- Fixed a player-lifecycle null pointer when replaying immediately after stop.
- Fixed editor overflow and misalignment at small windows and high GUI scaling.
- Fixed six-direction thin-frame model and media-face placement.

### Known limitations

- Multi-channel IPTV playlists use only the first source.
- Public live-stream long-duration playback and reconnect behavior require more testing.
- High-frame-rate 4K/8K playback is not a default performance promise.
- WATERMeDIA 3.0.0.23 is initial best-effort compatibility and may be removed later.

[0.1.0]: https://github.com/AnnoyingVillager/onescreen/releases/tag/v0.1.0