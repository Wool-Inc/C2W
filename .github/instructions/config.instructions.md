---
description: "Use when working on C2W configuration loading, config.yml, per-type structure.yml, PluginConfig, FolderStructureTypeConfig, ConfigAccess, or default resource extraction on startup. Covers bootstrap/config classes and the config file formats."
name: "C2W Config"
applyTo: ["src/main/java/net/klaaswhite/c2w/bootstrap/config/**", "src/main/resources/config.yml", "src/main/resources/plugin.yml"]
---

# C2W Config

Two separate config concerns: the plugin-wide `config.yml` and per-type structure definitions (`structure.yml`). Do not mix them up.

## `PluginConfig` (`config.yml`)

- Loads `plugins/c2w/config.yml` via its **own** `YamlConfiguration` instance (NOT the plugin's built-in `getConfig()`). Always read/write through the `PluginConfig` instance.
- Constructor takes a `ConfigAccess` (production = `BukkitConfigAccess`); a package-private constructor accepts an in-memory `YamlConfiguration` for tests.
- Key getters: `getGameOverTarget()` (default `"draft"`), `isLobbyAutoJoin()` (default `true`), `isOverrideOnStartup()` (default `false`).
- `reload()` re-reads the file. `getRaw()` exposes the `YamlConfiguration` for ad-hoc access.

## `FolderStructureTypeConfig` (`structure.yml` per type)

- Lives at `structures/<type>/structure.yml` (see `structure-editor.instructions.md`). Implements `TypeDimensionSource`.
- `getDimensions(type)` → `int[]{w,h,d}`. `getTypeNames()` → types with a `structure.yml`.
- `getResourceRequirements(type)` → `Map<id, minSpots>`; `getResourceType(type, id)` → `"block"`/`"container"`.
- Writers: `saveType`, `setResourceRequirements`, `addResourceRequirement`, `removeResourceRequirement`, `removeType`.

## `ConfigAccess` (port)

`domain/config/ConfigAccess` is the port; `bootstrap/config/impl/BukkitConfigAccess` is the Bukkit impl (provides `getDataFolder()` and `saveResource`). Tests use `FakeConfigAccess`.

## Default resource extraction

On startup, `App.extractDefaultResources()` scans the JAR for `layouts/` and `structures/` entries and extracts them:
- `overrideOnStartup: true` → always overwrites server files.
- `overrideOnStartup: false` (default) → only extracts files that don't already exist.

Bundled defaults live in `src/main/resources/{layouts,structures}/` and are picked up automatically — add/remove files there; no manifest needed.

## `plugin.yml`

Bukkit plugin descriptor. Command labels (`c2w`, `structure`, `marker`, `world`, `layout`) are declared here; **new command labels must be added to `plugin.yml`** or Bukkit won't route them.

## Common pitfalls (config)

- **Use `PluginConfig`'s own `YamlConfiguration`**, never `plugin.getConfig()` — they are different instances.
- **Structure types are per-type `structure.yml`**, not a root `structures.yml`.
- **`overrideOnStartup`** controls whether bundled defaults clobber admin edits on restart.
- **New command labels** need a `plugin.yml` entry.
