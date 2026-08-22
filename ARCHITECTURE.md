# Architecture

## How the plugin works internally

This document is for developers who want to understand, modify, or contribute to C2W's codebase.

---

## Hexagonal (Ports & Adapters) Architecture

C2W splits into three layers that strictly depend in one direction:

```
domain  →  adapter  →  bootstrap
(pure logic)  (orchestration)  (Bukkit glue)
```

### domain/ — Pure game logic (zero Bukkit imports, fully testable)

- `domain.model` — Entities (Wool, ManagedPlayer, ManagedTeam, etc.)
- `domain.game` — Application services (GameStateMachine, BoundaryEngine, PlayerRegistry, MarkerEngine, WoolTimer)
- `domain.ops` — Port interfaces for Bukkit interactions (MarkerOps, ServerOps, WoolOps, etc.)
- `domain.commands` — Command framework (CommandPiece, CommandInput, etc.)
- `domain.events` — Internal events (WoolCapturedEvent, StartGameEvent, etc.)
- `domain.managers` — Managers that coordinate using port interfaces (LayoutManager, StructureManager, NbtStructureSource)

### adapter/ — Testable orchestration

- `adapter.managers` — Thin adapters wiring domain services to Bukkit events (GameManager, BoundaryManager, PlayerManager, MarkerManager)
- `adapter.commands` — Command trees (C2WCommand, StructureCommand, MarkerCommand, WorldCommand)

### bootstrap/ — Untestable Bukkit glue

- `bootstrap.ops` — Bukkit implementations of port interfaces (BukkitMarkerOps, BukkitServerOps, etc.)
- `bootstrap.config` — Config file loading (PluginConfig, StructureTypeConfig)
- `bootstrap.listeners` — Bukkit/ProtocolLib event listeners
- `bootstrap.world` — World wrapper (ManagedWorld)
- `bootstrap.App` — Single wiring point (constructs every manager in order)
- `bootstrap.C2W` — JavaPlugin entry point
- `bootstrap.Managers` — Holder for all manager references

**Dependency rule (enforced by package structure):**
- `domain/` NEVER imports from `adapter/` or `bootstrap/`
- `adapter/` imports from `domain/` and `bootstrap/`
- `bootstrap/` imports from `adapter/` and `domain/`

### Wiring diagram

```
C2W (JavaPlugin)
  -> App
       -> PluginConfig         (loads config.yml)
       -> StructureTypeConfig  (loads structures.yml)
       -> WorldManager         (manages lobby / reference / creation / resource / draft / game worlds)
       -> EventManager         (registers Bukkit listeners + ProtocolLib packets)
       -> ServerOps            (BukkitServerOps)
       -> EntityManager        (item pickup listeners)
       -> PlayerManager        (players, teams, boss bars, death listeners)
       -> MarkerOps            (BukkitMarkerOps)
       -> MarkerManager        (named marker entities, creates Wools)
       -> NbtStructureSource   (loads/pastes NBT structure files)
       -> StructureManager     (structure template index + placement)
       -> StructureCreationManager (creation worlds for building structures)
       -> ResourceManager      (resource worlds)
       -> BoundaryManager      (pit / elevator bounding boxes)
       -> LayoutManager        (map layouts)
       -> GameManager          (game state machine)
       -> CommandManager       (registers all commands)
```

### Dependency rules

- No service locator. Every manager declares dependencies in its constructor.
- No `Lazy<T>`, no class-keyed registry. Wiring is visible in `App`.
- Managers register event handlers in their constructor via `EventManager`.

---

## Events

Two event channels through `EventManager`:

- **Minecraft events** — `registerMinecraftEvent` / `pushMinecraftEvent`. Wraps Bukkit's event bus.
- **Internal events** — `registerInternalEvent` / `pushInternalEvent`. Game-specific events like `WoolCapturedEvent`, `WoolDroppedEvent`.

---

## Marker system

Bukkit `Marker` entities with persistent data key `map_marker` (`NamespacedKey`).

### Naming conventions

| Pattern | Purpose |
|---|---|
| `wool` | Generic wool spawn point (colors assigned by order at game start) |
| `boundary-woolcap-pit-1` / `-2` | Capture pit bounding box |
| `boundary-woolcap-elevator-1` / `-2` | Elevator (instant capture) bounding box |
| `resourcespot-<structureId>-<resourceId>-<counter>` | Resource placement spots |
| `resourceinstance` (PDC key) | Marks a tile entity as a resource instance |

---

## NBT Structure System

Structures are `.nbt` files at `plugins/c2w/structures/<typeName>/<id>.nbt`. There is no template world.

### Key classes

- **`NbtStructureSource`** — Scans `plugins/c2w/structures/` for `.nbt` files. `discover()` loads each file. `transport()` pastes into target world.
- **`StructureTemplate`** — Holds cached Bukkit `Structure` + `BlockPos` corners.
- **`StructureType`** — Enum of built-in types (backwards compat with `charMap` and `LayoutCell`).
- **`StructureManager`** — Holds `Map<String, List<StructureTemplate>>`. Delegates to `NbtStructureSource`.

### Structure placement flow

1. `GameManager.placeCellStructure()` calls `StructureManager.placeRandom(typeName, world, origin)`
2. `StructureManager` picks a random template and calls `NbtStructureSource.transport()`
3. `NbtStructureSource` pastes the cached Bukkit template into the game world

---

## Ops Interfaces

Every Bukkit interaction is abstracted behind an interface. Tests use fake implementations.

| Interface | Production | Test |
|---|---|---|
| `ServerOps` | `BukkitServerOps` | Inline |
| `MarkerOps` | `BukkitMarkerOps` | Inline |
| `PlayerEntityOps` | `BukkitPlayerEntityOps` | Inline |
| `WoolOps` | `BukkitWoolOps` | `FakeWoolOps` |
| `BlockOps` | `BukkitBlockOps` | Inline |
| `PlayerHandle` | `BukkitPlayerHandle` | `FakePlayerHandle` |
| `ConfigAccess` | `BukkitConfigAccess` | `FakeConfigAccess` |

---

## Worlds

| World | Type | Purpose |
|---|---|---|
| `c2w_lobby` | Persistent | Waiting area |
| `c2w_reference` | Persistent | Reference world |
| `c2w_draft` | Transient | Pre-game staging |
| `c2w_game` | Transient | Active game world |
| `c2w_create_<type>_<id>` | Transient | Structure building |
| `c2w_resource_<type>` | Transient | Resource definition |

---

## Command System

Uses a tree of `CommandPiece` nodes. Tree traversal consumes args left to right.

**Important:** When a handler is called, `commandInput.strings` still contains ALL original args. Index 0 is the first arg after the command name.

Example: `/structure define dungeon 16 10 16`
- Tree consumes: `define` → `dungeon` → `16` → `10` → `16` (handler)
- `commandInput.strings` = `["define", "dungeon", "16", "10", "16"]`

### Command piece types

- **`CommandPiece`** — Terminal with handler lambda. Consumes one arg.
- **`TreeChoiceCommandPiece`** — Branch. Matches next arg against registered choices.
- **`ListChoiceCommandPiece`** — Presents a fixed list of choices.
- **`DynamicListChoiceCommandPiece`** — Presents dynamic choices (from supplier). Can act as intermediate or terminal.

---

## Structure creation worlds

Built in transient void worlds (`c2w_create_<type>_<id>`).

1. `/structure create <type> <id>` — creates void world, shows particle boundary, locks with `.lock_<type>_<id>` file
2. Player builds inside the boundary
3. `/structure save` — exports to NBT, destroys world
4. `/structure discard` — destroys without saving
5. `/structure modify <type> <id>` — loads existing NBT into fresh void world

---

## Resource system

Resources (chests, spawners) defined in "resource worlds" (`c2w_resource_<type>`).

1. `/structure resource world <type>` — opens void world, loads existing resource NBT
2. Place tile entities
3. `/structure resource mark <resourceId>` — marks with `resourceinstance` PDC key
4. `/structure save` — saves to `resources/<typeName>.nbt`

---

## Default resource packaging

Default files (`structures.yml`, NBTs, resource NBTs) are bundled in the JAR at `src/main/resources/`. On startup :

- If `overrideOnStartup: true` → always overwrites server files
- If `overrideOnStartup: false` (default) → only extracts files that don't exist yet

The manifest `bundled-resources.txt` lists which files to extract.

---

## Test coverage

| Service | Tests | What's covered |
|---|---|---|
| `GameStateMachine` | 17 | Lifecycle, guards, transitions, layout resolution, win conditions |
| `BoundaryEngine` | 16 | Pit enter/exit, elevator capture, modifier calc, reset, overlapping boxes |
| `PlayerRegistry` | 18 | Registration, lookup, team management, balancing, reset |
| `MarkerEngine` | 12 | Marker discovery, getMarkersInWorld, initWools, entity wools, close |
| `WoolTimer` | 10 | Register/unregister, tick scheduling, defaults |
| `Wool` | 20+ | State machine: pickup, drop, capture, tick, CAP_AMOUNT |
| `ManagedPlayer` | 12 | Team assignment, removeTeam, carry logic |
| `ManagedTeam` | 7 | Player add/remove, colors, static registry |
| `MapLayout` | 9 | Layout dimensions, cells, defensive copy |
| `GameManager` | - | State transitions (basic) |
| `LayoutManager` | - | Layout parsing |
| `StructureManager` | - | Template discovery |