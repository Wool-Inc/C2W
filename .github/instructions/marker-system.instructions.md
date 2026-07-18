---
description: "Use when working on C2W markers, the map_marker persistent data key, wool spawn points, capture points, boundary pit/elevator markers, or the /marker command. Covers MarkerManager, MarkerEngine, MarkerEntity, Markers, and how wools are created from markers at game start."
name: "C2W Marker System"
applyTo: ["src/main/java/net/klaaswhite/c2w/adapter/managers/MarkerManager.java", "src/main/java/net/klaaswhite/c2w/adapter/minecraft/MarkerEngine.java", "src/main/java/net/klaaswhite/c2w/adapter/minecraft/MarkerEntity.java", "src/main/java/net/klaaswhite/c2w/adapter/minecraft/Markers.java", "src/main/java/net/klaaswhite/c2w/adapter/commands/MarkerCommand.java"]
---

# C2W Marker System

Markers are Bukkit `Marker` entities carrying a persistent-data string under the key `map_marker` (a `NamespacedKey`). They define spawn points, capture points, and boundary boxes used by the game. The marker system is **distinct** from the `resourceinstance` key used by the resource system (see `resource-system.instructions.md`).

## Naming conventions (code-accurate)

| Marker name | Purpose |
|---|---|
| `wool` | Generic wool spawn point. At game start, `MarkerEngine.initWools` assigns colors in order (red, green, blue, yellow) to the first 4 `wool` markers and creates a `Wool` at each. |
| `cap-<color>` | Capture point matching a wool color (e.g. `cap-red`). Built as `cap-` + `color.name().toLowerCase()`. |
| `boundary-woolcap-pit-1` / `-2` | Capture pit bounding box corners (read by `BoundaryManager.onStartGame`). |
| `boundary-woolcap-elevator-1` / `-2` | Elevator (instant capture) bounding box corners. |

> The old docs listed `wool-<color>` and `resourcespot-*` markers — those are **outdated**. The code uses a single generic `wool` marker (colors assigned by order) plus `cap-<color>`. There is no `resourcespot-*` marker in the current code.

`MarkerManager.MARKER_NAMES` lists the known names for `/marker create` tab-completion, but marker names are **not a closed set** — structures may add internal markers the plugin does not interpret.

## `MarkerManager` (adapter)

Owns a `MarkerEngine` and tracks the active game world. Listens to `StartGameEvent` → `start(event)`: sets `worldName`, calls `engine.discoverMarkers(worldName)`, sets `initialized`, and `engine.initWools(worldName)`.
- `createMarker(player, at, name)` — `at` is `"looking"` (targeted block within 6) or `"player"` (player position); spawns a marker and sets the `map_marker` PDC. Blocked once `initialized` (game started).
- `removeMarker(player, name)`, `getMarker(name)`, `getMarkersInWorld(...)`, `ensureEntityWools()`, `getWools()`.
- `initialized` is an `AtomicBoolean`; most reads return empty until `start` runs.

## `MarkerEngine` (adapter/minecraft)

Uses `MinecraftManager.markers()` for all Bukkit access.
- `getMarkersInWorld(worldName)` → `Hashtable<name, ManagedMarker>` by reading `map_marker` PDC on each marker entity.
- `discoverMarkers(worldName)` — caches marker entities.
- `initWools(worldName)` — finds `wool` markers, assigns `WoolColor` by index, builds `Wool(mc, color, spawnPos, worldName, "cap-<color>")`, calls `wool.placeEntityInWorld()`.
- `ensureEntityWools()` / `getWools()` / `close()`.

## `MarkerEntity` / `Markers`

- `MarkerEntity` wraps a Bukkit `Marker`: `getPosition()`, `getPersistentData(key)`, `setPersistentData(key, value)`, `remove()`.
- `Markers` (sub-interface of `MinecraftManager`) — `getMarkerKey()` (the `map_marker` `NamespacedKey`), `getMarkersInWorld(name)`, `spawnMarker(world, pos)`, `findMarkersInWorld(world, pdcKey, prefix)`.

## `/marker command`

`MarkerCommand` (`/marker`) — `create <at> <name>`, `remove <name>`, `list`. Tab-completion uses `MarkerManager.MARKER_NAMES`.

## Common pitfalls (markers)

- **`wool` is generic**, not `wool-red`. Colors are assigned by marker order in `MarkerEngine.initWools`.
- **Markers are locked after game start** (`initialized`); `createMarker` refuses post-start.
- **Two PDC keys:** `map_marker` (this system) vs `resourceinstance` (resource system). Keep them separate.
- **Boundary boxes** require both `-1` and `-2` markers present, or `BoundaryManager.onStartGame` skips that box.
