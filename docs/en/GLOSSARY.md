# Glossary

## Block / BlockState

`ScreenBlock` is the screen block in the world. Its blockstate stores the six-direction `FACING` value and whether the frame model is hidden. The physical frame is 1x1x1/16 of a block, while the rendered media area may extend beyond one block.

## BlockEntity

The authoritative data container attached to a screen block. `ScreenBlockEntity` stores `ScreenSettings` and `loadRevision`; it does not store client players, texture handles, or decoder threads.

## BlockEntityRenderer

The Minecraft 26.2 client renderer for block entities. `ScreenRenderer` submits media or placeholder quads through render-state extraction and `submit`.

## RenderState

A read-only snapshot of the data needed for one render frame. `ScreenRenderState` contains facing, size, rotation, fit mode, texture, and media state; it does not hold a `MediaPlayer`.

## SubmitNodeCollector

The Minecraft 26.2 queue used to submit geometry, text, and effects. The renderer cannot fall back to the old immediate `MultiBufferSource` drawing model.

## ScreenGeometry

A pure geometry utility that calculates six-direction plane basis vectors, in-plane rotation, alignment offsets, and the audio center. The media quad and audio position must share these calculations.

## ScreenFitMode

The media-fit algorithm: `fit`, `stretch`, `tile`, `fill`, or `center`. `tile` produces UV values greater than 1 and relies on texture repeat sampling.

## ScreenMediaManager

The client-side media-session coordinator. It manages `MediaSession` instances by dimension and block position and handles URL/revision deduplication, concurrency limits, distance values, and lifecycle cleanup.

## MediaSession

The media loading and playback lifecycle for one screen on one client. It manages the MRL, engines, player, textures, state machine, VOD seeking, HLS segment progress, and live-stream resynchronization.

## MRL

WATERMeDIA’s media-resource locator. It resolves a URL into a resource that can be handled by image, video, or audio engines.

## MediaPlayerHandle

OneScreen’s wrapped handle for the WATERMeDIA player reflection API. Business code calls the wrapper instead of importing WATERMeDIA player classes directly.

## WaterMediaCompat

The single boundary for WATERMeDIA API version differences, reflection, and engine/player construction.

## WaterMediaVersion

The version-comparison class implementing WATERMeDIA’s special ordering. Test versions `3.0.0.x` are older than formal `3.0.0`, while `3.0.x` with `x > 0` represents later formal releases.

## CPU Texture Fallback

The fallback path where the AWT engine outputs a `BufferedImage`, which is copied to `NativeImage` and uploaded through `DynamicTexture`. It includes GPU-to-CPU readback and Java pixel copying and is a compatibility path rather than a performance promise.

## ExternalMediaTexture

An adapter that exposes a WATERMeDIA OpenGL texture ID or Vulkan `VkImageView` as a Minecraft texture abstraction. OneScreen owns the wrapper object, not the underlying WATERMeDIA image resource.

## GPU Texture Path

The path where WATERMeDIA `GLEngine` or `VKEngine` publishes media frames directly as GPU textures for Minecraft to render. `format=GPU` describes the texture path and does not imply `hardwareAccel=true`.

## hardwareAccel

The hardware-decoding state reported by the WATERMeDIA player. It describes decoder selection, not texture-upload routing, and must be recorded separately from `format=GPU/CPU`.

## VideoLod

A level-of-detail algorithm that selects `MAX`, `CLOSE`, `NEAR`, `FAR`, or `FAR_AWAY` from the screen-distance ratio. A `0.05` hysteresis band prevents rapid tier changes.

## Vulkan Queue Contract

WATERMeDIA Vulkan texture publication and Minecraft sampling must use the same graphics queue. Submission, waiting, and presentation must be serialized by the same lock keyed by native `VkQueue`.

## VulkanQueueSynchronization

A utility that provides a shared lock keyed by the native `VkQueue` handle. Three narrow Mixins include Minecraft `vkQueueSubmit2KHR`, `vkQueueWaitIdle`, and `vkQueuePresentKHR` in that synchronization contract.

## HLS / M3U8

HTTP Live Streaming playlists. VOD streams can be seeked; live streams cannot be scrubbed and show only current-segment target-duration progress. Multi-channel IPTV playlists currently use only the first source.

## ValueInput / ValueOutput

The Minecraft 26.x block-entity persistence interfaces that replace the old `CompoundTag + HolderLookup.Provider` save/load pattern.

## Update Tag

A snapshot used to synchronize the complete block-entity state when a player enters a chunk.

## Update Packet

A targeted update packet sent to clients tracking a block entity when its data changes.

## Payload

A custom network-message object. OneScreen uses payloads to open the editor, submit settings, reload, and broadcast debug state. The server always repeats permission and value validation.

## Server Authoritative

The server determines URLs and screen configuration; clients cannot become the persistent source of truth.

## Session Leak

A media session that remains open after it is no longer needed, leaving decoder threads, memory, GPU textures, or audio active. Chunk unload, disconnect, dimension change, URL changes, and explicit stop must clean up sessions.

Return to the [English documentation index](README.md).