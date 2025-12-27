# Upgrading VanillaMinimaps

This repo builds both the plugin jar and the matching resource pack zip.
Minecraft targeting and versioning are centralized in `gradle.properties`.

## How it works (plain language)

- The plugin jar runs on the server and sends map data/packets to players.
- The minimap visuals are rendered client-side using vanilla shaders that come from the resource pack.
- The resource pack is built from `resourcepack/` and must match the shaders in `src/main/resources/shaders`.
- The version numbers in `gradle.properties` control which Paper API is used and how the outputs are named.
- The bump script updates versions, syncs shaders, and validates that everything stays in lockstep.

## Sources of truth

- Plugin and MC versions: `gradle.properties`
- Resource pack content: `resourcepack/`
- Plugin jar resources: `src/main/resources/`
  - `src/main/resources/shaders/` is a mirror of the pack shaders.

## Version bump checklist

1) Update version settings
- Use the bump script (recommended) or edit `gradle.properties` manually.

```bash
./scripts/bump-minecraft.sh --mc 1.21.10 --pack-format 34 --plugin-version 1.0.1
```

The script expects a `<minecraftVersion>.jar` in the repo root for an offline version check.
Pass `--skip-paper-check` if you want to skip that check.
It also runs shader sync + validation automatically.

If you edit by hand, update:
- `gradle.properties`:
  - `pluginVersion` (plugin release version)
  - `minecraftVersion` (target server version, e.g. `1.21.10`)
  - `resourcepackPackFormat` (match the MC version)

2) Update the resource pack metadata
- Ensure `resourcepack/pack.mcmeta` has the same `pack_format`.

3) Sync and validate shaders
- Canonical shader sources live in `resourcepack/assets/minecraft/shaders/`.
- Sync the mirror and validate:

```bash
./gradlew syncShaderResources validateResourcepack
```

4) Update NMS/Paper dependencies
- `build.gradle.kts` derives Paper coordinates from `minecraftVersion`.
- If Paper does not publish the exact snapshot yet, update `minecraftVersion` to the closest available
  Paper version or adjust the coordinates manually.
- Run a build to surface NMS or API changes:

```bash
./gradlew build
```

5) Update docs
- Update the supported version in `README.MD`.
- If a major version changes API behavior, update `src/main/resources/plugin.yml` `api-version`.

## Build outputs

```bash
./gradlew build
```

- Plugin jar: `build/libs/vanillaminimaps-<pluginVersion>-mc<minecraftVersion>.jar`
- Resource pack zip: `build/resourcepack/vanillaminimaps-resourcepack-<pluginVersion>-mc<minecraftVersion>.zip`

## Troubleshooting

- If shaders look wrong in-game, confirm `resourcepack/` and `src/main/resources/shaders/` are identical.
- If the build fails on NMS classes, update the NMS implementations under
  `src/main/java/com/jnngl/vanillaminimaps/clientside/impl` and
  `src/main/java/com/jnngl/vanillaminimaps/injection`.
