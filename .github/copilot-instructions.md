# C2W Plugin — Developer Instructions (Index)

Capture 2 Wool is a Paper/Spigot Minecraft minigame plugin. Two teams race to capture colored wools. The plugin uses a hexagonal (ports & adapters) architecture with explicit constructor injection — `App.java` is the single wiring root.

**Package:** `net.klaaswhite.c2w`
**Build:** Maven (`mvn clean package` → `target/c2w-1.0-SNAPSHOT.jar`)
**Requirements:** Java 25, Paper/Spigot 1.21+, ProtocolLib (soft-dependency)

## How to use these instructions

This file is the **entry point only**. Detailed context is split into focused instruction files under `.github/instructions/`. Each is auto-attached when you touch matching files, and discoverable on-demand by its `description`. You normally do **not** need to read all of them — pick the ones relevant to the task:

| Subsystem | Instruction file | Auto-attaches to |
|---|---|---|
| Architecture, wiring, layers, events | `architecture.instructions.md` | `App.java`, `Managers.java`, `bootstrap/**`, `domain/**`, `adapter/**` |
| Game loop, state machine, wool capture, boundary, players | `game-core.instructions.md` | `GameManager`, `GameStateMachine`, `BoundaryEngine`, `Wool`, `PlayerManager`, `PlayerRegistry`, `WoolTimer` |
| Structure editor (creation worlds, NBT, types) | `structure-editor.instructions.md` | `StructureCreationManager`, `NbtStructureSource`, `StructureManager`, `StructureCommand`, `FolderStructureTypeConfig` |
| Resource system (resource worlds, marking) | `resource-system.instructions.md` | `ResourceManager`, `resource` command paths |
| Layout editor (layouts, editor worlds) | `layout-editor.instructions.md` | `LayoutEditorManager`, `LayoutManager`, `LayoutEditorCommand`, `MapLayout`, `LayoutCell` |
| Marker system (markers, wool spawn) | `marker-system.instructions.md` | `MarkerManager`, `MarkerEngine`, `MarkerEntity`, `Markers` |
| Command framework (command tree) | `command-system.instructions.md` | `adapter/commands/**`, `domain/commands/**` |
| Config (PluginConfig, structure types) | `config.instructions.md` | `bootstrap/config/**`, `config.yml`, `structure.yml` |

## Global architecture rules (always apply)

- **Three layers, one direction:** `domain/` (pure logic, zero Bukkit imports) → `adapter/` (orchestration) → `bootstrap/` (Bukkit glue). `domain/` never imports from `adapter/` or `bootstrap/`.
- **No service locator.** Every manager declares dependencies in its constructor; `App` wires them. No `Lazy<T>`, no class-keyed registries.
- **Managers register event handlers in their constructor** via `EventManager`. Two channels: Minecraft events (`registerMinecraftEvent`/`pushMinecraftEvent`) and internal events (`registerInternalEvent`/`pushInternalEvent`).
- **Resource lifecycle:** Managers implement `AutoCloseable`. `App.closeables` tears down in reverse construction order on `/c2w reset`.
- **Ops interfaces:** Every Bukkit interaction is abstracted behind an interface (see `MinecraftManager` and its sub-interfaces, plus `domain/ops/*`). Never call Bukkit APIs directly in domain logic.
- **Adding a feature:** (1) domain classes in `domain/` (zero Bukkit imports); (2) if needed, an Ops interface + `Bukkit*Ops` impl; (3) a manager in `adapter/managers/` with constructor injection; (4) wire in `App.initManagers()` in correct dependency order; (5) add to `Managers.java`; (6) add/extend commands; (7) add to `CommandManager` constructor; (8) update `App.closeables` (reverse order).
- **Testing:** Prefer inline anonymous implementations of Ops interfaces. Only 3 hand-written fakes exist: `FakeWoolOps`, `FakePlayerHandle`, `FakeConfigAccess`.
- **Builder tools:** `mvn` is not available in all environments. Changes must be manually reviewed for compilation correctness.
- **Removed features (do NOT reference):** `TemplateWorldStructureSource`, `StructureSource` interface, `SpawnerManager`, `SpawnerCommand`, `BlockEventListeners`, and the `c2w_templates` world.

> Note: `ARCHITECTURE.md` at the repo root is the human-readable design doc. The `.instructions.md` files below are the authoritative, code-accurate guidance for AI agents and take precedence over `ARCHITECTURE.md` where they differ (e.g. NBT path is `structures/<type>/instances/<id>.nbt`, not `structures/<type>/<id>.nbt`).
