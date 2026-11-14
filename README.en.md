# OneScreen

[简体中文](README.md) | **English**

OneScreen is a screen mod for **Minecraft Java Edition 26.2 / Fabric**. It adds thin, surface-mounted screen frames that can display images, video, and audio from HTTP/HTTPS media sources.

> [!IMPORTANT]
> **This project is primarily developed with generative AI.** It is not an official Mojang, Fabric, or WATERMeDIA project and is not maintained by a mature commercial team. Code, documentation, build configuration, and troubleshooting notes are largely generated and organized by AI, while the human user provides requirements, testing environments, and in-game feedback. Back up your worlds and test new versions in a separate world first.

> Current status: initial public release `0.1.0`. Core screen rendering, the editor, image/video/audio playback, playback progress and live-stream refresh, six-direction placement, commands, networking, the dedicated creative tab, and both OpenGL and Vulkan rendering paths are implemented. Additional live-stream, stress, and multiplayer validation will continue in later versions.

> Download: [GitHub Releases](https://github.com/AnnoyingVillager/onescreen/releases) · Source and issue tracker: [GitHub repository](https://github.com/AnnoyingVillager/onescreen)

> [!NOTE]
> Detailed development and architecture documents are currently maintained in Chinese. This English README covers installation, dependencies, usage, configuration, and known limitations.

## Features

- A thin `1×1×1/16` screen frame that can be attached to any of the six faces of a block.
- Right-click the frame to open an editor for the media URL, width, height, render distance, audio distance, nine-grid alignment, looping, and live-stream refresh.
- PNG, JPEG, and WebP images, plus MP4, HLS/M3U8, and other media supported by WATERMeDIA. Multi-channel IPTV playlists currently play only the first channel.
- Five aspect-ratio modes: Fit, Stretch, Tile, Fill, and Center.
- Volume, mute, loop, pause, stop, resume, reload, and debug placeholder controls.
- A seek bar for media with a known duration. Live streams use a read-only approximate progress display based on the current HLS segment.
- Optional “refresh live stream after pause/config” behavior. It is disabled by default and reconnects live streams after a pause or configuration change.
- Server-authoritative screen settings synchronized to clients.
- Media sessions are closed when a chunk unloads, the player changes dimension, or the client disconnects.
- A dedicated `OneScreen` creative tab.
- Rendering that works with both Minecraft 26.2 OpenGL and Vulkan backends.

## Compatibility

| Component | Build/test baseline | Runtime compatibility |
|---|---|---|
| Minecraft | 26.2 | 26.2 |
| Java | 25 | 25 |
| Fabric Loader | 0.18.6 | Minimum 0.18.0 |
| Fabric API | 0.152.0+26.2 | Minimum 0.152.0+26.2 |
| WATERMeDIA | 3.0.0.23 | 3.0.0.23 and 3.0.1 |
| WATERMeDIA Binaries | 3.0.0 | Must match the installed WATERMeDIA 3.0.x series |

WATERMeDIA 3.0.0.23 and 3.0.1 use different public Java package layouts. OneScreen contains a reflective compatibility layer, so the same OneScreen JAR can detect and use either API layout.

> **Legacy compatibility:** WATERMeDIA `3.0.0.23` has initial compatibility only. Support may be removed in a future release if its maintenance cost becomes unreasonable. New installations should prefer `3.0.1`.

### Recommended combinations

| Use case | WATERMeDIA | WATERMeDIA Binaries |
|---|---|---|
| Early 26.2 client | 3.0.0.23 | 3.0.0 |
| Current 26.2 client | 3.0.1 | 3.0.1 |

Do not mix unrelated WATERMeDIA and Binaries generations. A mismatch can prevent the media engine from initializing.

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
3. Enter an HTTP/HTTPS media URL and configure size, distances, alignment, aspect-ratio mode, looping, and optional live-stream refresh.
4. Save the settings. The server synchronizes them and the client loads the media asynchronously.

Common commands:

```text
/screen set <url>
/screen size <width> <height>
/screen renderdistance <blocks>
/screen audiodistance <blocks>
/screen align <top_left|up|top_right|left|center|right|bottom_left|down|bottom_right>
/screen fitmode <fit|stretch|tile|fill|center>
/screen volume <0.0-1.0>
/screen mute <true|false>
/screen loop <true|false>
/screen reload
/screen info
/screen config debug <on|off>
```

See the [command reference](docs/COMMAND-REFERENCE.md) for details. The linked development documents are currently written in Chinese.

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
- Enabling live-stream refresh may cause a short black frame, buffering delay, or audio restart because the player is reopened.
- The primary video path still copies CPU frames into a Minecraft dynamic texture. 4K/8K high-frame-rate playback is not a default performance promise.
- Multi-channel IPTV playlists currently use only the first source.
- Public live streams have not yet received extensive long-duration and reconnect testing.
- Generative-AI-led projects may contain edge cases not covered by the current test matrix. When reporting a problem, include `latest.log`, WATERMeDIA logs, and reproduction steps with private data removed.

## Documentation

- [Scope and acceptance criteria](docs/SCOPE.md)
- [Project plan](docs/PROJECT_PLAN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Version and API constraints](docs/API-VERSIONS.md)
- [Rendering](docs/RENDERING.md)
- [Media and audio](docs/MEDIA-AUDIO.md)
- [Video handling guide](docs/VIDEO-HANDLING-AGENT-GUIDE.md)
- [Networking and commands](docs/NETWORK-SYNC.md)
- [Configuration](docs/CONFIGURATION.md)
- [Command reference](docs/COMMAND-REFERENCE.md)
- [Testing](docs/TESTING.md)
- [Milestones](docs/MILESTONES.md)
- [Architecture decisions](docs/DECISIONS.md)
- [Risk register](docs/RISKS.md)
- [AI workflow](docs/AI-WORKFLOW.md)
- [Development and verification](docs/DEVELOPMENT.md)
- [Publishing](docs/PUBLISHING.md)
- [Changelog](CHANGELOG.md)

## License

This project is licensed under the [MIT License](LICENSE). Third-party mods, Minecraft assets, and dependencies remain subject to their respective licenses.
