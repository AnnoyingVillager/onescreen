# Command Reference

OneScreen commands execute on the logical server and share the same permission, coordinate, URL, numeric-range, and configuration-limit validation path as the in-game editor. Commands that modify a screen require permission level 2 (Gamemaster). `/screen info` is available to all players.

## Command overview

```text
/screen set <x> <y> <z> <url>
/screen size <x> <y> <z> <width> <height>
/screen renderdistance <x> <y> <z> <blocks>
/screen audiodistance <x> <y> <z> <blocks>
/screen align <x> <y> <z> <position>
/screen fitmode <x> <y> <z> <mode>
/screen rotation <x> <y> <z> <degrees>
/screen hideframe <x> <y> <z> <true|false>
/screen volume <x> <y> <z> <0.0-1.0>
/screen mute <x> <y> <z> <true|false>
/screen loop <x> <y> <z> <true|false>
/screen reload <x> <y> <z>
/screen info <x> <y> <z>
/screen config debug [on|off]
```

## Set the media URL

```text
/screen set 120 64 -37 https://example.com/demo.mp4
```

- Only HTTP/HTTPS URLs are accepted, with a maximum length of 4096 characters.
- Empty hosts, control characters, `file://`, `javascript:`, and other non-HTTP(S) inputs are rejected.
- The target chunk must be loaded, and the target position must be an OneScreen block entity.
- The URL and query parameters are stored by the server; user-visible log summaries are redacted.

## Screen size

```text
/screen size 120 64 -37 4 2.25
```

- Width and height are measured in blocks.
- The minimum is `0.25`; the maximum is `32`.
- Changing the size affects the media quad, alignment offsets, and audio center. It does not create a multi-block collision shape.

## Render and audio distance

```text
/screen renderdistance 120 64 -37 96
/screen audiodistance 120 64 -37 48
```

Default server limits:

| Setting | Default limit | Hard configured limit |
|---|---:|---:|
| Render distance | `256` blocks | `4096` blocks |
| Audio distance | `128` blocks | `1024` blocks |

The limits are configured in `config/onescreen.json`. Restart the game or server after changing the file. Existing screen data is not automatically clamped when a configured limit is reduced. See [Configuration](CONFIGURATION.md).

## Alignment position

```text
/screen align 120 64 -37 center
```

Available values:

```text
top_left
up
top_right
left
center
right
bottom_left
down
bottom_right
```

Alignment offsets use the same geometry as media size and in-plane rotation, keeping the visual quad and audio center synchronized.

## Fit mode

```text
/screen fitmode 120 64 -37 fill
```

| Value | Behavior |
|---|---|
| `fit` | Preserve aspect ratio and show the complete media with transparent extra space. |
| `stretch` | Fill the screen without preserving aspect ratio. |
| `tile` | Preserve aspect ratio and repeat the media using one block along its longest edge as the base size. |
| `fill` | Preserve aspect ratio, cover the screen, and crop the overflow. |
| `center` | Preserve aspect ratio, use one block along the longest edge as the natural size, and center the media. |

## In-plane rotation

```text
/screen rotation 120 64 -37 30
```

The range is `-180` through `180` degrees and covers every orientation within the screen plane. Rotation does not change the frame mounting orientation; it changes the media quad, alignment offsets, and audio center within that plane.

## Hide the frame model

```text
/screen hideframe 120 64 -37 true
```

The default is `false`. When set to `true`, the static frame model is hidden while the block entity, dynamic media, collision, and right-click interaction area remain. This is useful for buildings and display installations.

## Volume, mute, and looping

```text
/screen volume 120 64 -37 0.8
/screen mute 120 64 -37 false
/screen loop 120 64 -37 true
```

- Volume ranges from `0.0` to `1.0`; the final volume is also multiplied by player-distance attenuation.
- Muting affects only that screen’s audio and does not change the image.
- The loop setting is applied to the player when available and persisted in the block entity.

## Reload

```text
/screen reload 120 64 -37
```

The server increments the persisted `loadRevision`. When the client observes the new revision, it closes the old session and resolves the same URL again. This is useful for recovering from temporary network errors. The refresh button to the left of the playback controls in the editor uses the same path.

## View information

```text
/screen info 120 64 -37
```

Displays a redacted URL summary, size, distances, alignment, fit mode, rotation, frame visibility, volume, mute state, loop state, live-stream refresh state, and the current load revision. This command does not require administrator permission.

## Debug material

```text
/screen config debug
/screen config debug on
/screen config debug off
```

- With no argument, reports the current debug state.
- `on` makes screens without media display the placeholder/frame debug material.
- `off` keeps the display area transparent when there is no media.
- The server broadcasts this state to clients. It affects only placeholder rendering and does not modify normal media rendering.

## Editor-only controls

The following controls are available through the right-click editor and do not have separate commands:

- Play, pause, and stop.
- Seek a VOD with the progress bar.
- Read-only progress display for live HLS streams.
- “Refresh live stream after pause/configuration,” disabled by default.
- Keep manual pause or stop state after the editor is closed.

Saving the editor sends a validated `ScreenSettingsUpdatePayload`. The refresh button uses `ScreenReloadPayload`. Clients cannot directly modify synchronized block-entity data.

Return to the [English documentation index](README.md).