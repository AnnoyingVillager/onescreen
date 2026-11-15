# Configuration

OneScreen creates the following file on first startup:

```text
<game or server directory>/config/onescreen.json
```

Default contents:

```json
{
  "maxRenderDistance": 256.0,
  "maxAudioDistance": 128.0
}
```

## Fields

| Field | Default | Allowed range | Purpose |
|---|---:|---:|---|
| `maxRenderDistance` | `256.0` | `8.0`–`4096.0` | Maximum render distance configurable for one screen |
| `maxAudioDistance` | `128.0` | `2.0`–`1024.0` | Maximum audio propagation distance configurable for one screen |

## Applying changes

- In single-player, the integrated server reads this file.
- On a dedicated server, the server reads this file and sends the active limits to players who open the editor.
- Restart the game or server after changing the file.
- The configuration only limits new editor and command changes; existing screen data is not automatically clamped when a limit is reduced.
- Large distances increase media-session, texture-upload, and audio-processing load. Choose values appropriate for the client hardware.
- Actual visibility is still constrained by client render distance, server simulation distance, and chunk loading.

Return to the [English documentation index](README.md).