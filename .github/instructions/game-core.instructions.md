---
description: "Use when working on the C2W game loop, game state machine, wool capture, capture pit/elevator boundary, players/teams, or the WoolTimer. Covers GameManager, GameStateMachine, BoundaryEngine, Wool, PlayerManager, PlayerRegistry, WoolTimer, and the StartGame/WoolCaptured/EndGame events."
name: "C2W Game Core"
applyTo: ["src/main/java/net/klaaswhite/c2w/adapter/managers/GameManager.java", "src/main/java/net/klaaswhite/c2w/adapter/managers/BoundaryManager.java", "src/main/java/net/klaaswhite/c2w/adapter/managers/PlayerManager.java", "src/main/java/net/klaaswhite/c2w/adapter/managers/EntityManager.java", "src/main/java/net/klaaswhite/c2w/adapter/minecraft/Wool.java", "src/main/java/net/klaaswhite/c2w/domain/game/GameStateMachine.java", "src/main/java/net/klaaswhite/c2w/domain/game/BoundaryEngine.java", "src/main/java/net/klaaswhite/c2w/domain/game/PlayerRegistry.java", "src/main/java/net/klaaswhite/c2w/domain/game/WoolTimer.java", "src/main/java/net/klaaswhite/c2w/domain/events/**"]
---

# C2W Game Core

The game lifecycle and the mechanics that decide who wins. All pure logic lives in `domain/game/`; the `adapter/managers/` classes are thin adapters that wire domain services to events and `MinecraftManager`.

## Game lifecycle (`GameStateMachine` + `GameManager`)

`GameStateMachine` (pure domain) holds the `State` enum: `NOT_STARTED`, `DRAFT_CREATED`, `GAME_IN_PROGRESS`, `GAME_ENDED`.
- Guards: `canInit()`, `canStart()`, `canEnd()` (boolean).
- Transitions: `transitionToDraftCreated()`, `transitionToGameInProgress()`, `transitionToGameEnded()`, `reset()`.
- `resolveLayout(name)` → `MapLayout` via `LayoutManager` (falls back to first layout if name blank/unknown).
- Win condition: `onWoolCaptured(WoolCapturedEvent)` adds the wool to the capturing team's list; when a team reaches **2 capped wools**, it transitions to `GAME_ENDED`.

`GameManager` (adapter) owns the `GameStateMachine` and exposes the player-facing flow:
- `init(input)` — requires `canInit()`; creates the draft world via `WorldManager.createDraftWorld()`, pushes `DraftCreatedEvent`, transitions to `DRAFT_CREATED`.
- `start(input, layoutName)` — requires `canStart()`; resolves layout, calls `StructureManager.discoverTemplates()`, validates every layout cell type has a template (`structureManager.hasType`), creates the game world, destroys creation worlds, pushes `GameWorldCreatedEvent`, then places each `LayoutCell` via `placeCellStructure(cell, world, pos)`.
- `selectLayout` / `getSelectedLayoutName` — draft-phase layout selection.
- `preview(input)` — pushes `PreviewRequestEvent`.
- `reset()` — tears down game/draft worlds and resets state.

**Layout placement detail (important):** `placeCellStructure` treats `cell.worldPosition()` as the structure **center** for placements-based layouts (`layout.isPlacementsBased()`), and offsets to the NBT origin corner based on `yawToRotation(cell.yaw())` and the type's dimensions from `FolderStructureTypeConfig.getDimensions`. Grid layouts already compute the corner. Read the rotation→offset switch in `GameManager.start` before changing placement math.

## Wool capture (`Wool` + `WoolTimer` + `BoundaryEngine`)

`Wool` (adapter, `adapter/minecraft/Wool.java`) is a per-wool state machine:
```
WAITING --pickup()--> CARRIED --capture()--> CAPPED
   ^                    |
   +---dropOnDeath()----+
```
- `CAP_AMOUNT = 20*60` (ticks; 60s at modifier 20).
- `pickup(player)` fails if already carried or player already carries one; sets capping modifier to 20, shows a boss bar, sets the player's helmet to the wool `ItemStackRef`.
- `tick()` is driven by `WoolTimer`; capture progress accrues while a player stands in a capture area (see `BoundaryEngine`).
- `dropOnDeath()` returns the wool to `WAITING`.
- Capture is finalized by `BoundaryManager`/`BoundaryEngine` calling `wool.capture()` when the pit/elevator logic decides.

`WoolTimer` (pure domain) ticks all registered wools on a repeating scheduler (`Scheduler` interface; production = `BukkitWoolTimerScheduler`). Configurable: `interval`, `baseCapture`, `increasePerPlayer`, `decreasePerPlayer`, `decreaseOutsideArea`. `registerWool`/`unregisterWool` start/stop the timer automatically.

`BoundaryEngine` (pure domain) manages capture pit and elevator bounding boxes:
- `WoolTimerBridge` interface adapts `WoolTimer` (provides `getBaseCapture`, `getIncreasePerPlayer`, `getDecreasePerPlayer`, `registerWool`, `unregisterWool`). `BoundaryManager` implements it as `WoolTimerAdapter`.
- `initialize(teams)` clears boxes and per-team pit occupant sets.
- `addPitBox(box)` / `addElevatorBox(box)` register `DomainBoundingBox` regions with enter/exit callbacks.
- Modifier formula: `baseCapture + (increasePerPlayer * allies) - (decreasePerPlayer * enemies)`, forced to 0 if allies ≤ enemies.
- Elevator = instant capture on entry.

`BoundaryManager` (adapter) wires `BoundaryEngine` to events: `PlayerMoveEvent` → `engine.onPlayerMove(...)`, `PlayerDeathEvent` → `engine.removePlayer`, `StartGameEvent` → `engine.initialize` + reads `boundary-woolcap-pit-1/2` and `boundary-woolcap-elevator-1/2` markers from `MarkerManager` to build boxes, `WoolDroppedEvent`/`WoolCapturedEvent` → engine updates.

## Players & teams (`PlayerManager` + `PlayerRegistry`)

`PlayerRegistry` (pure domain) maps `playersByUUID` and `playersByName`; `ensureTeams()` creates Red/Blue/Spectator `ManagedTeam`s (stored on `ManagedTeam.teams` static map). `balanceTeams()` alternates players into Red/Blue by registration order. `reset()` clears registrations.

`PlayerManager` (adapter) owns a `PlayerRegistry` and a `playersByBukkitPlayer` map. It listens to `PlayerJoinEvent` and internal events: `DraftCreatedEvent` → `ensureTeams`/`ensurePlayers`/route online players to draft; `PreviewRequestEvent` → preview teleport; `EndGameEvent` → cleanup. Exposes `getPlayer(...)` overloads (UUID / name / Bukkit `Player`). `SPECTATOR_TEAM_NAME = "Spectator"`.

## Key events (`domain/events/`)

- `DraftCreatedEvent(worldName)` — draft world ready.
- `StartGameEvent(gameWorldName)` — game world created; triggers marker discovery, boundary setup, player routing.
- `GameWorldCreatedEvent(worldName)`.
- `PreviewRequestEvent(commandInput)`.
- `WoolCapturedEvent(player, wool)` — drives win condition + boundary updates.
- `WoolDroppedEvent(wool)`.
- `EndGameEvent`.

## Common pitfalls (game core)

- **Win condition is 2 wools per team** in `GameStateMachine.onWoolCaptured` — don't change the threshold without updating tests.
- **Layout placement offset** depends on rotation + dimensions; re-read `GameManager.start` before editing.
- **State guards:** `init` requires `NOT_STARTED`, `start` requires `DRAFT_CREATED`. Respect them or commands silently no-op with a player message.
- **Wool capture math** lives in `BoundaryEngine` (modifier) and `WoolTimer` (speed config); the boss bar is created per-wool in `Wool`'s constructor.
