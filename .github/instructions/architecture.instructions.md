---
description: "Use when working on the C2W plugin's overall architecture, wiring, dependency injection, layer boundaries, event system, or the App/Managers bootstrap. Covers domain/adapter/bootstrap layering, EventManager channels, and the manager lifecycle."
name: "C2W Architecture & Wiring"
applyTo: ["src/main/java/net/klaaswhite/c2w/bootstrap/App.java", "src/main/java/net/klaaswhite/c2w/bootstrap/Managers.java", "src/main/java/net/klaaswhite/c2w/bootstrap/C2W.java", "src/main/java/net/klaaswhite/c2w/domain/**", "src/main/java/net/klaaswhite/c2w/adapter/**", "src/main/java/net/klaaswhite/c2w/bootstrap/**"]
---

# C2W Architecture & Wiring

## Three layers (strict one-way dependency)

```
domain  →  adapter  →  bootstrap
(pure logic)  (orchestration)  (Bukkit glue)
```

- **`domain/`** — Pure game logic. Zero Bukkit imports, fully testable. Contains `model/`, `game/`, `managers/`, `commands/`, `events/`, `config/`, `ops/`.
- **`adapter/`** — Testable orchestration. `managers/` (thin adapters wiring domain services to events), `commands/` (command trees), `minecraft/` (`MinecraftManager` interface + impls like `Wool`, `MarkerEngine`, `Markers`). Imports from `domain/` and `bootstrap/` for constructor parameter types.
- **`bootstrap/`** — Untestable Bukkit glue. `ops/` (`BukkitFileSystemOps`), `config/` (`PluginConfig`, `FolderStructureTypeConfig`), `listeners/`, `world/` (`ManagedWorld`), `minecraft/` (`BukkitMinecraftManager`, `BukkitWoolTimerScheduler`), `App.java`, `C2W.java`, `Managers.java`.

**Dependency rule (enforced by package structure):** `domain/` NEVER imports from `adapter/` or `bootstrap/`. If a domain class needs file I/O or Bukkit access, create a port interface in `domain/ops/` and implement it in `bootstrap/ops/`.

## Wiring root: `App.java`

`App` is the single wiring point. `initManagers()` constructs every manager in dependency order; `initCommands()` wires commands. `rebuild()` (called on `/c2w reset`) closes `closeables` in reverse order, nulls managers, then re-inits.

Construction order (from `App.initManagers`):
1. `PluginConfig` (loads `config.yml`)
2. `EventManager`
3. `MinecraftManager` (`BukkitMinecraftManager`) — passed to nearly all managers
4. `WorldManager`
5. `EntityManager`
6. `PlayerManager` (owns a `PlayerRegistry`)
7. `MarkerManager` (owns a `MarkerEngine`)
8. `FolderStructureTypeConfig` + `BukkitFileSystemOps` + `NbtStructureSource` → `StructureManager`
9. `StructureCreationManager`, `ResourceManager`, `LayoutEditorManager`
10. `WoolTimer` (scheduler = `BukkitWoolTimerScheduler`)
11. `BoundaryManager` (owns a `BoundaryEngine`, adapts `WoolTimer` via `WoolTimerBridge`)
12. `LayoutManager` (layouts dir + `YamlLayoutFileLoader`)
13. `GameManager` (owns a `GameStateMachine`)
14. `ChangeTeamPacketListener` (ProtocolLib)

`closeables` is torn down in reverse of the above (game → layout → resource → creation → marker → world → player → boundary → event).

## `Managers.java`

Holder for all manager references, constructed once in `App` and passed around (e.g. `PlayerManager` and `MarkerManager` receive `Managers` for cross-manager access). Add new managers here and assign in `App.initManagers`.

## Event system: `EventManager`

Two channels, both via `EventManager`:
- **Minecraft events** — `registerMinecraftEvent(Class, handler)` / `pushMinecraftEvent(event)`. Wraps Bukkit's event bus (handlers receive Bukkit event objects).
- **Internal events** — `registerInternalEvent(Class, handler)` / `pushInternalEvent(event)`. Game-specific events in `domain/events/` (`C2WEvent` marker interface): `DraftCreatedEvent`, `StartGameEvent`, `GameWorldCreatedEvent`, `PreviewRequestEvent`, `WoolCapturedEvent`, `WoolDroppedEvent`, `EndGameEvent`.

Managers register their handlers in their constructor. Internal events are the primary decoupling mechanism between managers (e.g. `StartGameEvent` triggers `MarkerManager.start`, `BoundaryManager.onStartGame`, `PlayerManager` routing).

## `MinecraftManager` (the Ops aggregator)

`adapter/minecraft/MinecraftManager.java` is the top-level port for all Bukkit access, grouped into sub-interfaces: `players()`, `worlds()`, `server()`, `markers()`, `scoreboards()`, `structures()`, `blocks()`, `bossBars()`, `plugin()`, plus `pushEvent(C2WEvent)`. Production impl: `BukkitMinecraftManager`. Domain/managers receive this interface (or a sub-interface) via constructor injection — never call Bukkit APIs directly.

## Worlds

| World | Type | Purpose |
|---|---|---|
| `c2w_lobby` | Persistent | Waiting area |
| `c2w_reference` | Persistent | Reference world |
| `c2w_draft` | Transient | Pre-game staging (created on `/c2w init`) |
| `c2w_game` | Transient | Active game world (created on `/c2w start`) |
| `c2w_create_<type>_<id>` | Transient | Structure building worlds |
| `c2w_resource_<type>` | Transient | Resource definition worlds |
| `c2w_layout_<layoutName>` | Transient | Layout editor worlds |

`WorldManager` owns persistent + transient `ManagedWorld` instances and cleans up leftover transient worlds on startup.

## Default resource packaging

Default files (`layouts/`, `structures/` with NBTs and per-type `structure.yml`) are bundled in the JAR at `src/main/resources/`. On startup `App.extractDefaultResources()` extracts them: if `overrideOnStartup: true` → always overwrites; if `false` (default) → only extracts files that don't exist yet.
