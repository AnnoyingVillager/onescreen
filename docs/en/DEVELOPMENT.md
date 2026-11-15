# Development Environment and Verification

## Requirements

- JDK 25
- Git
- An IDE with Java 25 support, preferably IntelliJ IDEA
- Minecraft Java Edition 26.2
- Fabric Loader `0.18.6` as the build/test baseline
- Fabric API `0.152.0+26.2`
- WATERMeDIA `3.0.0`
- WATERMeDIA Binaries matching the WATERMeDIA release line

The Gradle Wrapper is pinned to `9.8.2`; a separate Gradle installation is not required. The project uses Fabric Loom `1.19.4` and Java 25.

## Clone and build

```powershell
git clone https://github.com/AnnoyingVillager/onescreen.git
Set-Location onescreen
.\gradlew.bat build --no-configuration-cache
```

Linux/macOS can use `./gradlew build --no-configuration-cache`.

Build outputs:

- `build/libs/onescreen-0.2.0.jar`: mod JAR.
- `build/libs/onescreen-0.2.0-sources.jar`: sources JAR.

The current public release is `0.2.0`. Check the actual version in `gradle.properties` before building.

## Local run

```powershell
.\gradlew.bat runClient --no-configuration-cache
```

Dedicated server only:

```powershell
.\gradlew.bat runServer --no-configuration-cache
```

With an offline sandbox or a prepared dependency cache:

```powershell
.\gradlew.bat build --offline --no-daemon --no-configuration-cache
.\gradlew.bat runClient --offline --no-daemon --no-configuration-cache
```

`run/`, `logs/`, `build/`, `.gradle/`, crash reports, worlds, and downloaded Binaries are local artifacts and must not be committed.

## Source-set constraints

- `src/main`: blocks, block entities, settings, commands, network payloads, configuration, and server validation.
- `src/client`: renderer, editor, textures, WATERMeDIA players, CPU/GPU media paths, and client lifecycle.
- `src/main` must not reference `net.minecraft.client.*`.
- WATERMeDIA version differences must remain in `WaterMediaCompat`.
- Do not directly import WATERMeDIA player-package classes.
- Mixins are allowed only for the current Vulkan queue-synchronization contract; do not add unrelated Mixins or access wideners.

## GPU media implementation boundaries

- OpenGL: WATERMeDIA `GLEngine` plus `ExternalMediaTexture`.
- Vulkan: WATERMeDIA `VKEngine`, Minecraft `VulkanDevice`, and the shared graphics queue.
- CPU fallback: WATERMeDIA `AWTEngine`, `NativeImage`, and `DynamicTexture`.
- See [Media, GPU Textures, and Audio](MEDIA-AUDIO.md) for implementation and ownership details.

WATERMeDIA and Minecraft must use the same Vulkan queue lock. Do not switch to an independent compute/transfer queue; doing so reintroduces device-loss risk.

## Verification after changes

Minimum commands:

```powershell
.\gradlew.bat compileJava compileClientJava
.\gradlew.bat build --no-configuration-cache
```

Media or rendering changes also require:

1. Start a sandbox or isolated test client and enter a world.
2. Play a 1080p source and a 4K/high-resolution source.
3. Check both OpenGL and Vulkan backends.
4. Regress images, VOD, HLS metadata, and playback controls.
5. Test pause, stop, refresh, looping, world exit, and disconnect cleanup.
6. Check `format=GPU/CPU` and `hardwareAccel=true/false` logs separately.

See [Testing](TESTING.md) for the complete matrix.

## API fact-checking

Before using an unfamiliar Minecraft or WATERMeDIA API:

1. Find the dependency source or decompiled class.
2. Check package, method, parameters, return type, and threading constraints.
3. Do not guess signatures.
4. If the signature cannot be verified, reduce the task scope or report the blocker.

Local reference-source directories usually include:

```text
E:\waterframes\_mc26.2-sources
E:\waterframes\_watermedia-sources
```

## Network and privacy

- Documentation, logs, and test data must not contain access tokens, passwords, private keys, local usernames, or absolute paths.
- Do not commit `run/`, `logs/`, crash reports, world saves, or downloaded native Binaries.
- Remote media URLs may contain private parameters; remove query strings and credentials before posting issue reports.
- Remove chat, server addresses, and other personal information from screenshots.

## Agent maintenance entry point

The project is primarily implemented with generative AI. Maintainers or automated Agents that need to perform development work must first read [agents/README.md](../../agents/README.md), including the project context, development log, and runbook. This document only covers human-reproducible environment and verification steps.

Return to the [English documentation index](README.md).