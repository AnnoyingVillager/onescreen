# OneScreen AI Coding Rules

## Target

- Minecraft 26.2
- Fabric Loader 0.18.0 minimum (build/test baseline 0.18.6)
- Fabric API 0.152.0+26.2
- Java 25
- Gradle 9.8.2
- Loom 1.19-SNAPSHOT
- WATERMeDIA 3.0.0.23 build baseline; 3.0.1 runtime-compatible through WaterMediaCompat
- WATERMeDIA Binaries 3.0.0.6 paired with 3.0.0.23; 3.0.1 pairing supported

Pin exact resolved versions before release. Do not silently upgrade Minecraft, mappings, media APIs, or editor models.

## Source Sets

- `src/main` is common server/client code and must never import `net.minecraft.client.*`.
- `src/client` contains rendering, textures, media players, audio and client lifecycle code.
- Block entity data must be serializable primitives/records that are safe on a dedicated server.

## Forbidden in v1

- Mixins
- Access wideners
- Minecraft private audio classes such as `SoundEngine`, `ChannelAccess`, or `Channel`
- Raw OpenGL-only texture rendering that would fail under the 26.2 Vulkan path
- CreativeCore GUI/network/config dependencies
- Copying old WATERMeDIA 2 APIs such as `ImageAPI`, `ImageCache`, or `VideoPlayer`
- Creating media players in render methods
- Loading a remote URL synchronously on the render thread
- Trusting client-provided URLs without server-side validation

## Required API Rules

- Use `Identifier`, not `ResourceLocation`.
- Use `ValueInput` and `ValueOutput` for BlockEntity persistence.
- Use `BlockEntityRenderer` render-state extraction plus `submit`.
- Use the Fabric 26.2 split client source set for renderers.
- Video resolution must stay bounded through `MediaPlayer.scale(...)`; never render an untrusted remote source at unlimited output dimensions.
- Apply `LodLevel` only when a distance tier changes, with hysteresis. Do not call `scale` or `lod` every tick/frame.
- Preserve the single-latest-frame design: the WATERMeDIA callback may be on a decoder thread and must not call Minecraft rendering APIs.
- Treat WATERMeDIA hardware decoding as an automatic capability with software fallback; do not claim zero-copy or guaranteed hardware decode without runtime evidence.
- Read `docs/VIDEO-HANDLING-AGENT-GUIDE.md` before changing video resolution, frame copying or LOD behavior.
- Use `CustomPacketPayload` only when block entity update packets are insufficient.
- Route version-specific WATERMeDIA player classes through `WaterMediaCompat`; do not add direct imports from either player package layout.
- Keep the server authoritative for URL and screen settings.
- Every media session must have an explicit close path.

## Work Procedure

1. Read the relevant document under `docs/` before editing.
2. Change one milestone or one subsystem at a time.
3. Search the compiled dependency sources before using an unfamiliar API.
4. Never invent method signatures. Cite the source file or official documentation in the response.
5. Run `gradlew compileJava` after every code change.
6. Run client and dedicated-server checks for milestones that change lifecycle behavior.
7. Record important design changes in `docs/DECISIONS.md`.
8. Update `docs/MILESTONES.md` only after the acceptance test passes.
9. Keep unrelated modules unchanged.
10. Do not commit generated caches, downloaded Binaries, or local run data.

## Test Rule

A change is not complete because it compiles. The relevant acceptance criteria in `docs/TESTING.md` must also pass, or the remaining manual checks must be reported explicitly.