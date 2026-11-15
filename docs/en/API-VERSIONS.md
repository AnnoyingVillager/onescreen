# Version and API Constraints

## Build baseline

| Component | Current value | Notes |
|---|---|---|
| Minecraft | `26.2` | Target client and dedicated server version |
| Java | `25` | Required by the Minecraft 26.2 runtime |
| Fabric Loader | `0.18.6` | Build and sandbox-test baseline; `fabric.mod.json` declares a minimum of `0.18.0` |
| Fabric API | `0.152.0+26.2` | Build baseline and minimum runtime version |
| Gradle | `9.8.2` | Fixed by the Gradle Wrapper |
| Loom | `1.19.4` | Current build plugin version |
| WATERMeDIA | `3.0.0` | Official 0.2 baseline |
| WATERMeDIA Binaries | `3.0.0` | Must match the WATERMeDIA release line |
| OneScreen | `0.2.1` | Current source version; not released |

References:

- [Fabric Loader versions for 26.2](https://meta.fabricmc.net/v2/versions/loader/26.2)
- [Fabric API Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml)
- [WATERMeDIA on Modrinth](https://modrinth.com/mod/watermedia)
- [WATERMeDIA Binaries on Modrinth](https://modrinth.com/mod/watermedia-binaries)
- [WATERMeDIA Binaries on CurseForge](https://www.curseforge.com/minecraft/mc-mods/watermedia-binaries)

## WATERMeDIA version semantics

WATERMeDIA orders `3.0.0.x` test builds differently from ordinary semantic versions. OneScreen handles this explicitly in `WaterMediaVersion`:

```text
3.0.0.23 < 3.0.0 < 3.0.1 < 3.0.2
```

Rules:

- `3.0.0` is the formal baseline.
- `3.0.0.x` builds are test versions older than the formal baseline.
- `3.0.x` where `x > 0` is a later formal release.
- `3.1.0` is not currently supported.
- The minimum declared version is `3.0.0.0`.

Current `fabric.mod.json` declaration:

```json
{
  "depends": {
    "watermedia": ">=3.0.0.0 <3.1.0",
    "watermedia_binaries": ">=3.0.0"
  }
}
```

Fabric Loader cannot express the special ordering, so the loader performs range filtering and the client applies a second runtime check through `WaterMediaVersion`.

## WATERMeDIA API compatibility layer

WATERMeDIA 3.x release lines may use different public package layouts:

- Older test line: `org.watermedia.api.media.*`
- Current formal line: `org.watermedia.api.player.*`

Business code must not import either player API directly, because the other version can then fail with `ClassNotFoundException` or `NoClassDefFoundError`. All version differences are centralized in:

```text
src/client/java/dev/onescreen/client/media/WaterMediaCompat.java
src/client/java/dev/onescreen/client/media/WaterMediaVersion.java
```

The compatibility layer is responsible for:

- MRL creation.
- AWT, OpenGL, Vulkan, JavaScript-audio, and headless engine factories.
- Player creation.
- Engine methods such as `width()`, `height()`, `texture()`, `format()`, `image()`, and `uploadCount()`.
- Player methods such as `time()`, `duration()`, `canSeek()`, `seek()`, `seekQuick()`, and `live()`.
- HLS metadata helpers and legacy playlist helpers.

The current formal test target is WATERMeDIA `3.0.0` and later `3.0.x` releases. Compatibility code for `3.0.0.x` is best-effort, must not be the only validation environment for new features, and must not introduce new direct dependencies on the old package layout.

## 0.2 GPU texture API boundaries

### OpenGL

- Obtain the dynamic GL texture ID through WATERMeDIA `GLEngine`.
- `ExternalMediaTexture` provides a Minecraft `GlTexture`/`GlTextureView` facade.
- Business code must not draw the screen with direct `GL11` calls.

### Vulkan

- `WaterMediaVulkanContext` adapts the Minecraft `VulkanDevice` to the WATERMeDIA `VKContext`.
- WATERMeDIA must use Minecraft `graphicsQueue()`.
- `VulkanQueueSynchronization` and three narrow Mixins serialize:
  - `vkQueueSubmit2KHR`
  - `vkQueueWaitIdle`
  - `vkQueuePresentKHR`
- Do not switch to a separate compute or transfer queue unless a complete cross-queue semaphore and visibility design is implemented and tested.
- `ExternalMediaTexture` only wraps the current `VkImageView` returned by WATERMeDIA and must not destroy resources owned by WATERMeDIA.

## Minecraft 26.2 API notes

- Minecraft 26.x requires Java 25.
- Minecraft 26.x uses de-obfuscated source workflows; do not assume old Forge/Fabric mapping overlays.
- `ResourceLocation` has been renamed to `Identifier`.
- Block-entity persistence uses `ValueInput` / `ValueOutput`.
- `BlockEntityRenderer` uses render-state extraction plus `submit`.
- GUI, font, texture, and render-submission APIs differ substantially from older versions.
- Minecraft 26.2 resource-pack format is `88`; data-pack format is `107.1`.
- WATERMeDIA 3 is not API-compatible with WATERMeDIA 2. Do not reintroduce `ImageAPI`, `ImageCache`, `ImageRenderer`, or the old VLC `VideoPlayer`.

## Dependency upgrade rules

1. Upgrade one core dependency at a time.
2. Confirm the actual product version, matching Binaries release, and API layout.
3. Inspect Gradle caches or extracted dependency sources; never guess signatures.
4. Pass `compileJava` / `build` before starting a sandbox client.
5. Media changes must be tested with both OpenGL and Vulkan.
6. Test at least 1080p and one higher-resolution source.
7. Record `format=GPU/CPU` and `hardwareAccel=true/false` separately.
8. Update `DECISIONS.md`, `RISKS.md`, and `TESTING.md` when compatibility changes.

## Media installation requirements

- Clients must install WATERMeDIA and matching WATERMeDIA Binaries.
- OneScreen JARs do not bundle WATERMeDIA Binaries.
- Dedicated servers need OneScreen but do not load client media engines.
- Client and server native-library versions are not required to match: the server synchronizes screen URLs and settings, while each client plays media locally.

Return to the [English documentation index](README.md).