# VanillaMinimaps User Guide

This guide is for server owners and players who want to use the minimap.

## Requirements

- Java Edition client
- Paper server 1.21.10+
- The VanillaMinimaps resource pack (required)

## Getting Started (Server Owners)

1) Install the plugin jar in `plugins/` and start the server once.
2) Set up the resource pack in `server.properties`:
   - `resource-pack=<direct URL to the zip>`
   - `resource-pack-sha1=<sha1 of the zip>`
3) Restart the server.

If you host the pack yourself, make sure the URL points to the raw zip file (not a web page).

## Getting Started (Players)

- Join the server and accept the resource pack prompt.
- If you use mods, disable any shader packs (Iris/OptiFine shaders are not supported).

## Commands (Players)

- `/minimap enable` or `/minimap disable`
- `/minimap position left|right`
- `/minimap fullscreen` (toggle, sneak to close)
- `/minimap marker add <name> <icon>`
- `/minimap marker set <name> icon <icon>`
- `/minimap marker set <name> name <new_name>`
- `/minimap marker remove <name>`

## Configuration (Server Owners)

Config file: `plugins/VanillaMinimaps/config.yml`

Common settings:

- `enabledByDefault`: auto-enable for new players
- `defaultPosition`: `LEFT` or `RIGHT`
- `defaultMinimapRenderer`: `vanilla` or `flat`
- `minimapShape`: `CIRCLE` or `SQUARE`
- `minimapScale`: `1` (1 block per pixel; higher values zoom out)
- `markers.deathMarker.enabled`: show death marker
- `markers.customMarkers.limit`: max custom markers per player
- `markers.otherPlayers.enabled`: show other online players
- `markers.otherPlayers.usePlayerHeads`: use player heads as icons (fallback to default icon)
- `markers.otherPlayers.updateIntervalTicks`: update interval (20 ticks = 1 second)
- `fullscreen.segmentsX` / `fullscreen.segmentsZ`: fullscreen map size

After changing `config.yml`, restart the server.

## Custom Marker Icons

Place PNG files in `plugins/VanillaMinimaps/icons/` and use the filename (without `.png`) as the icon key.

Example:

- File: `plugins/VanillaMinimaps/icons/home.png`
- Command: `/minimap marker add home home`

## Troubleshooting

**The map shows as two items above/below the player.**
- The resource pack did not load correctly or shaders are being overridden.
- Make sure the resource pack is installed and has the highest priority.
- Disable Iris/OptiFine shader packs.

**Resource pack fails to download.**
- Verify `resource-pack` is a direct file URL.
- Update `resource-pack-sha1` to match the current zip.
- Clear client cache at `~/Library/Application Support/minecraft/server-resource-packs/`.

**Resource pack says "incompatible".**
- Make sure the server and client are on 1.21.10+ and the pack is the latest version.

**Other players are not shown on the map.**
- Check `markers.otherPlayers.enabled: true`.
- Players only appear in the same world.

**Performance issues.**
- Increase `markers.otherPlayers.updateIntervalTicks`.
- Use the `flat` renderer if needed.

**Geyser/Bedrock is not supported.**
- This plugin uses Java Edition shaders; Bedrock clients cannot render it.
