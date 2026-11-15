# Network Synchronization and Commands

## Authority model

The server is the sole authority for the URL, dimensions, distances, alignment, fit mode, rotation, frame visibility, volume, mute state, looping, and live-stream refresh. Clients display the editor, submit user input, and play media locally.

The server does not proxy media files. It synchronizes URLs, and every client makes its own request to that URL.

## Synchronization channels

### Persistence and block-entity updates

`ScreenBlockEntity` uses the Minecraft 26.2 data interfaces:

- `saveAdditional` / `loadAdditional` persist `ScreenSettings` and `loadRevision`.
- `getUpdateTag` supplies the complete state to a client entering the chunk.
- `getUpdatePacket` sends targeted updates to tracking clients.
- `setSettings` calls `setChanged()` and `sendBlockUpdated()`.

Routine URL and settings changes do not need a separate S2C packet per field; block-entity updates are the authoritative data channel.

### Opening the editor

When a player right-clicks a screen, the server validates:

1. The target block entity exists.
2. The player is within the permitted edit distance.
3. The current screen data can be read.

If validation passes, the server sends `ScreenEditorOpenPayload`, containing the current settings, server distance limits, and current load revision.

### Saving settings

`ScreenSettingsUpdatePayload` submits:

- Block position.
- URL, width, height, and distances.
- Alignment and fit mode.
- Rotation and frame visibility.
- Loop and live-stream refresh.

The server repeats all validation and never trusts client-side ranges:

1. Permission level.
2. Chunk loaded.
3. Target is a `ScreenBlockEntity`.
4. Player remains within edit distance.
5. URL is HTTP/HTTPS, host is non-empty, length is at most 4096, and control characters are rejected.
6. Size, distances, volume, and rotation are in range.
7. Distances do not exceed current `OneScreenConfig` limits.

After validation, the server writes new `ScreenSettings` and broadcasts the block-entity update.

### Manual refresh

`ScreenReloadPayload` contains only the block position. The server verifies permission, the target block entity, edit distance, and a non-empty URL, then calls `ScreenBlockEntity.reload()`:

```text
loadRevision = loadRevision + 1
setChanged()
sendBlockUpdated()
```

Clients that observe a revision change close the old `MediaSession` and recreate the same URL. Refresh never carries player objects and clients cannot directly create sessions through it.

### Debug state

`/screen config debug on|off` changes server `ScreenDebugSettings` and broadcasts `ScreenDebugStatePayload`. The state controls whether empty screens submit placeholder material and does not affect normal media textures.

## Command tree

```text
/screen
  config debug [on|off]
  set <pos> <url>
  size <pos> <width> <height>
  renderdistance <pos> <value>
  audiodistance <pos> <value>
  align <pos> <position>
  fitmode <pos> <mode>
  rotation <pos> <degrees>
  hideframe <pos> <hidden>
  volume <pos> <value>
  mute <pos> <muted>
  loop <pos> <loop>
  reload <pos>
  info <pos>
```

Permissions:

- Mutation commands and `config debug` require `Commands.LEVEL_GAMEMASTERS`.
- `info` is available to all players.
- See [Command Reference](COMMAND-REFERENCE.md) for syntax and value ranges.

## URL validation

`ScreenUrlValidator` accepts only:

- `http://`
- `https://`

Rejects:

- `file://`, `javascript:`, and other protocols.
- Empty URLs or empty hosts.
- More than 4096 characters.
- Control characters or clearly malformed URI encodings.
- Missing or non-finite host information.

URLs may contain authentication query parameters. Logs must use redacted summaries and never print the complete sensitive URL.

## Lifecycle and races

```text
Server changes settings
  -> BlockEntity data updated
  -> Client ScreenMediaManager compares URL/loadRevision
  -> Close old MediaSession
  -> Create new MediaSession
  -> Load media asynchronously
```

Required invariants:

- At most one active player per screen.
- The old session must close before a new session emits audio.
- Late callbacks from an old session must not overwrite the current session.
- Chunk unload, disconnect, dimension change, and block removal must clean up sessions.
- `loadRevision` wraps to 1 after `Integer.MAX_VALUE`, avoiding overflow.

## Command implementation principles

- Commands execute only on the logical server.
- Brigadier argument types parse block positions, floats, and booleans.
- `BlockPosArgument.getLoadedBlockPos` obtains the target and reports an unloaded target clearly.
- Non-screen targets return a localized error.
- Successful mutation returns a message and triggers a block update.
- Logs never print complete URLs or sensitive player data.

## Editor UI and network boundary

- The client editor only collects input and displays server-provided state.
- Closing the editor must not directly modify the block entity.
- Play, pause, stop, and progress dragging control the current client media session; they do not change server URL settings.
- Saving the settings panel sends `ScreenSettingsUpdatePayload`.
- The refresh button sends `ScreenReloadPayload`, and the server increments the revision.

## Verification checklist

- Player A changes settings and player B receives the update.
- A player entering the chunk immediately receives current settings.
- Leaving a chunk releases sessions; returning recreates them.
- Dimension changes and disconnects leave no audio or texture residue.
- Players without permission cannot run mutation commands.
- Non-screen positions, unloaded chunks, and invalid URLs return clear errors.
- Rapid URL changes, reloads, stop, and replay do not create duplicate players.
- Server and client logs do not expose complete sensitive URLs.

Return to the [English documentation index](README.md).