# OneScreen

[简体中文](README.md) | **English**

OneScreen is a screen mod for **Minecraft Java Edition 26.2 / Fabric**. It adds thin, surface-mounted screen frames that can display images, video, and audio from HTTP/HTTPS media sources.

> [!IMPORTANT]
> **This project is primarily developed with generative AI.** It is not an official Mojang, Fabric, or WATERMeDIA project and is not maintained by a mature commercial team. Code, documentation, build configuration, and troubleshooting notes are largely generated and organized by AI, while the human user provides requirements, testing environments, and in-game feedback. Back up your worlds and test new versions in a separate world first.

> Current status: the latest public release is `0.2.1`. It fixes frame placement and suspension on non-full blocks and recovers media when WATERMeDIA has cached a failed MRL.

> Download: [GitHub Releases](https://github.com/AnnoyingVillager/onescreen/releases) · Source and issue tracker: [GitHub repository](https://github.com/AnnoyingVillager/onescreen)

> [!NOTE]
> Detailed development and architecture documents are currently maintained in Chinese. This English README covers installation, dependencies, usage, configuration, and known limitations.

## Features

- A thin `1×1×1/16` screen frame that can be attached to any of the six faces of a block, including non-full shapes such as stairs and fences. The frame does not depend on its support block and remains suspended if that block is removed.
- Right-click the frame to open an editor for the media URL, width, height, render distance, audio distance, nine-grid alignment, in-plane rotation, frame visibility, looping, and live-stream refresh.
- PNG, JPEG, and WebP images, plus MP4, HLS/M3U8, and other media supported by WATERMeDIA. Multi-channel IPTV playlists currently play only the first channel.
- Five aspect-ratio modes: Fit, Stretch, Tile, Fill, and Center.
- Volume, mute, loop, pause, stop, resume, manual reload, and debug placeholder controls.
- Continuous in-plane rotation and an optional hidden-frame-material mode that keeps media and right-click editing available.
- A seek bar for media with a known duration. Live streams use a read-only approximate progress display based on the current HLS segment.
- Optional “refresh live stream after pause/config” behavior. It is disabled by default and reconnects live streams after a pause or configuration change.
- Server-authoritative screen settings synchronized to clients.
- Media sessions are closed when a chunk unloads, the player changes dimension, or the client disconnects.
- A dedicated `OneScreen` creative tab.
- Rendering that works with both Minecraft 26.2 OpenGL and Vulkan backends. The 0.2 development line also supports WATERMeDIA GPU textures with an AWT CPU-texture fallback.

## Compatibility

| Component | Build/test baseline | Runtime compatibility |
|---|---|---|
| Minecraft | 26.2 | 26.2 |
| Java | 25 | 25 |
| Fabric Loader | 0.18.6 | Minimum 0.18.0 |
| Fabric API | 0.152.0+26.2 | Minimum 0.152.0+26.2 |
| WATERMeDIA | 3.0.0 | 3.0.x; `3.0.0.x` test builds rank below formal `3.0.0` and are best-effort only |
| WATERMeDIA Binaries | 3.0.0 | Must match the WATERMeDIA release line |

WATERMeDIA version ordering differs from a conventional four-component version:

```text
3.0.0.23 < 3.0.0 < 3.0.1 < 3.0.2
```

`3.0.0` is the current formal baseline, `3.0.0.x` is treated as a test build, and `3.0.x` with x > 0 ranks above the baseline. OneScreen declares `>=3.0.0.0 <3.1.0` in Fabric metadata and performs a second runtime compatibility check.

### Recommended combinations

| Use case | WATERMeDIA | WATERMeDIA Binaries |
|---|---|---|
| Current stable setup | `3.0.0` | `3.0.0` |
| Future formal 3.0.x release | Matching `3.0.x` | The matching Binaries release specified by WATERMeDIA |

Always follow WATERMeDIA's published pairing. `3.0.0.x` test builds may load through the compatibility layer, but they are not the primary target for new features or formal testing.

## Required dependencies

| Dependency | Purpose | CurseForge | Modrinth |
|---|---|---|---|
| Fabric API | Core Fabric APIs | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api) | [Modrinth](https://modrinth.com/mod/fabric-api) |
| WATERMeDIA | Image, video, audio, and HLS decoding APIs | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/watermedia) | [Modrinth](https://modrinth.com/mod/watermedia) |
| WATERMeDIA Binaries | Native media libraries used by WATERMeDIA | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/watermedia-binaries) | [Modrinth](https://modrinth.com/mod/watermedia-binaries) |

You also need:

- [Fabric Loader](https://fabricmc.net/use/installer/)
- [Java 25](https://adoptium.net/temurin/releases/?version=25)
- A valid Minecraft Java Edition 26.2 installation

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Place Fabric API in the `mods` directory.
3. Select a matching WATERMeDIA and WATERMeDIA Binaries pair from the table above and place both files in `mods`.
4. Place the OneScreen JAR in `mods`.
5. Launch the game and find the screen frame in the dedicated `OneScreen` creative tab.

For single-player, OneScreen and its media dependencies are required only on the client. For multiplayer, install OneScreen on the server and on every client that should see screens. WATERMeDIA and WATERMeDIA Binaries are client-side media dependencies.

## Configuration

On first launch, OneScreen creates `config/onescreen.json`:

```json
{
  "maxRenderDistance": 256.0,
  "maxAudioDistance": 128.0
}
```

The integrated server and dedicated servers can increase these limits up to `4096` blocks for rendering and `1024` blocks for audio. Restart the game or server after changing the file. The editor uses the limits currently active on the server. Actual visibility is still constrained by client render distance and server chunk loading.

## Usage

1. Obtain a screen frame from the `OneScreen` creative tab and attach it to a block surface.
2. Right-click the frame to open the editor.
3. Enter an HTTP/HTTPS media URL and configure size, distances, alignment, rotation, frame visibility, aspect-ratio mode, looping, and optional live-stream refresh.
4. Save the settings. The server synchronizes them and the client loads the media asynchronously.

Common commands:

```text
/screen set <x> <y> <z> <url>
/screen size <x> <y> <z> <width> <height>
/screen renderdistance <x> <y> <z> <blocks>
/screen audiodistance <x> <y> <z> <blocks>
/screen align <x> <y> <z> <top_left|up|top_right|left|center|right|bottom_left|down|bottom_right>
/screen fitmode <x> <y> <z> <fit|stretch|tile|fill|center>
/screen rotation <x> <y> <z> <-180..180>
/screen hideframe <x> <y> <z> <true|false>
/screen volume <x> <y> <z> <0.0-1.0>
/screen mute <x> <y> <z> <true|false>
/screen loop <x> <y> <z> <true|false>
/screen reload <x> <y> <z>
/screen info <x> <y> <z>
/screen config debug <on|off>
```

See the [command reference](docs/en/COMMAND-REFERENCE.md) for details.

## Building from source

JDK 25 is required.

Linux/macOS:

```bash
./gradlew build --no-configuration-cache
```

Windows:

```powershell
.\gradlew.bat build --no-configuration-cache
```

Artifacts are written to `build/libs/`. GitHub Actions builds every push and pull request, and creates a GitHub Release with attached JAR files when a `v*` tag is pushed.

## Known limitations

- Only HTTP/HTTPS media URLs are accepted.
- There is no playlist manager, remote control, search, upload system, or automatic multi-block screen assembly.
- Remote media availability and codec support depend on WATERMeDIA, the network, and the local decoder.
- `fit`, `fill`, and `stretch` use the whole screen as their bounds. `tile` and `center` use one block along the media's longest side as their base size.
- HLS live streams cannot be seeked with the progress bar. Only an approximate current-segment progress value is shown.
- Rotation accepts `-180°` through `180°`, which covers every in-plane orientation. Hiding the frame removes only the static model; the screen block and media interaction area remain.
- Enabling live-stream refresh may cause a short black frame, buffering delay, or audio restart because the player is reopened.
- Version 0.2 prefers WATERMeDIA OpenGL/Vulkan GPU textures and falls back to CPU frame copying when GPU initialization is unavailable. Whether 4K/8K uses hardware decoding and remains smooth depends on the source, codecs, GPU, drivers, and WATERMeDIA. OneScreen no longer imposes a fixed 1080p cap, but it does not promise unrestricted high-frame-rate 4K/8K performance.
- Multi-channel IPTV playlists currently use only the first source.
- Public live streams have not yet received extensive long-duration and reconnect testing.
- Generative-AI-led projects may contain edge cases not covered by the current test matrix. When reporting a problem, include `latest.log`, WATERMeDIA logs, and reproduction steps with private data removed.

## Documentation

### Human usage and development documentation

See the complete [English documentation index](docs/en/README.md).

- [Scope and acceptance criteria](docs/en/SCOPE.md)
- [Architecture](docs/en/ARCHITECTURE.md)
- [Version and API constraints](docs/en/API-VERSIONS.md)
- [Rendering implementation](docs/en/RENDERING.md)
- [Media, GPU textures, and audio](docs/en/MEDIA-AUDIO.md)
- [High-resolution and hardware decoding](docs/en/HIGH-RESOLUTION-MEDIA.md)
- [Networking and commands](docs/en/NETWORK-SYNC.md)
- [Configuration](docs/en/CONFIGURATION.md)
- [Command reference](docs/en/COMMAND-REFERENCE.md)
- [Testing](docs/en/TESTING.md)
- [Architecture decisions](docs/en/DECISIONS.md)
- [Risk register](docs/en/RISKS.md)
- [Development and verification](docs/en/DEVELOPMENT.md)
- [Publishing](docs/en/PUBLISHING.md)
- [Changelog](CHANGELOG.en.md)

## License

This project is licensed under the [MIT License](LICENSE). Third-party mods, Minecraft assets, and dependencies remain subject to their respective licenses.
