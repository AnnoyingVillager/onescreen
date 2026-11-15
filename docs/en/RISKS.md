# Risk Register

| Risk | Probability | Impact | Mitigation |
|---|---:|---:|---|
| Incorrect assumptions about the Minecraft 26.2 render-state API | High | High | Inspect dependency sources, build placeholder rendering first, and require real signatures |
| WATERMeDIA 3 texture submission and OpenGL/Vulkan abstractions are unclear | Medium | High | Verify official examples and JAR sources; keep the render layer in a small set of classes |
| URL downloads block the render thread | Medium | High | Resolve and decode asynchronously; `submit` only reads snapshots |
| Rapid URL changes leak resources | High | High | Enforce the state machine, close old sessions first, and deduplicate session keys |
| Audio continues after a chunk unloads | Medium | High | Use block-entity load/unload events and global disconnect cleanup |
| Multiple screens decoding simultaneously cause stutter | High | Medium | Limit active sessions, use distance LOD, and provide queueing or rejection behavior |
| “Hardware acceleration available” is mistaken for guaranteed hardware decoding or zero-copy playback | High | Medium | Check WATERMeDIA logs; treat AWT as a CPU-readback path; do not promise 4K/8K performance globally |
| CPU video conversion and `NativeImage` copying are expensive | High | High | Prefer GPU textures and distance LOD; benchmark the CPU fallback per device |
| Large screens are culled incorrectly | Medium | Medium | Validate `ScreenRenderer.shouldRender`, display radius, and the 26.2 renderer contract across six directions |
| `src/main` references client classes and crashes the dedicated server | Medium | High | Keep split source sets, use CI/code review, and run the server |
| A URL is treated as malicious input | Medium | High | Require administrator permission, restrict schemes, limit length, and redact logs |
| Legacy WATERMeDIA test-layout maintenance becomes expensive | Medium | Medium | Keep `3.0.0` as the formal baseline, treat `3.0.0.x` as best-effort, and centralize differences in `WaterMediaCompat` |
| Old WATERMeDIA APIs are reintroduced | Medium | High | Enforce rules in `agents/`, code review, and compile checks |
| A Loom snapshot causes build drift | Medium | Medium | Record the resolved version and pin or cache it before release |
| HLS live streams and IPTV playlists behave differently | Medium | Medium | Separate live/VOD behavior, use only the first IPTV source, and test pause, seek, and reconnect |
| The media service is unstable | High | Medium | Use timeouts, error placeholders, bounded retries, and recoverable sessions |
| Texture and audio release order is wrong | Medium | High | Make close operations idempotent and centralize cleanup in the manager |
| Feature scope expands too far | High | Medium | Use [Scope](SCOPE.md), [Testing](TESTING.md), and release gates as boundaries |

## High-risk API areas

### Rendering

- `BlockEntityRenderer<BE, RenderState>`
- `extractRenderState`
- `SubmitNodeCollector`
- Dynamic texture handles
- `ScreenRenderer.shouldRender` and distance culling

### Media

- WATERMeDIA 3 MRL resolution.
- Media player creation and shutdown.
- Image/video texture access.
- Audio backend and volume.
- Asynchronous callbacks and main-thread switching.

### Client lifecycle

- Block-entity unload versus world disconnect ordering.
- Access to the old `ClientLevel` during dimension changes.
- Old callbacks completing after a newer session is created.

## Risk handling principles

1. Reduce the problem to the smallest reproducible case.
2. Inspect dependency sources and official documentation.
3. Preserve placeholder rendering as a diagnostic path.
4. Disable unrelated media features while debugging.
5. Record decisions in [DECISIONS.md](DECISIONS.md) and risks here.

Prefer public APIs. Add a narrowly scoped, testable, documented Mixin only when the synchronization or lifecycle contract cannot be expressed through public APIs. Do not copy a private implementation wholesale.

## 2026-11-15 risk update: Vulkan queues and texture lifetime

- WATERMeDIA `VKEngine` assumes media writes and Minecraft sampling occur on the same queue, with both sides serialized by `VKContext.queueLock()`.
- Using a separate queue without a cross-queue semaphore, or locking only WATERMeDIA while Minecraft submits outside the lock, causes undefined behavior and `VK_ERROR_DEVICE_LOST`.
- Current mitigation: share the Minecraft graphics queue; `VulkanQueueSynchronization` provides a lock keyed by the native `VkQueue`; the three Mixins cover submission, `vkQueueWaitIdle`, and `vkQueuePresentKHR`.
- Re-check injection targets and continuous 4K playback after updating Minecraft/Vulkan, WATERMeDIA, or the graphics driver.
- `ExternalMediaTexture` still relies on WATERMeDIA exposing a stable current `VkImageView`; release order, swapchain recreation, and backend switching require further testing.

Return to the [English documentation index](README.md).