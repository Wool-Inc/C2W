---
description: "Use when working on C2W structure creation worlds, NBT structure storage/transport, structure types/dimensions, or the /structure command (create, modify, define, resize, list, delete). Covers StructureCreationManager, NbtStructureSource, StructureManager, StructureCommand, FolderStructureTypeConfig, and the structures/ directory layout."
name: "C2W Structure Editor"
applyTo: ["src/main/java/net/klaaswhite/c2w/adapter/managers/StructureCreationManager.java", "src/main/java/net/klaaswhite/c2w/domain/managers/NbtStructureSource.java", "src/main/java/net/klaaswhite/c2w/domain/managers/StructureManager.java", "src/main/java/net/klaaswhite/c2w/adapter/commands/StructureCommand.java", "src/main/java/net/klaaswhite/c2w/bootstrap/config/FolderStructureTypeConfig.java", "src/main/java/net/klaaswhite/c2w/domain/model/StructureData.java"]
---

# C2W Structure Editor

Structures are built by admins in transient void worlds and saved as vanilla `.nbt` files. There is **no template world** and **no marker-based discovery** — everything is file-based under `plugins/c2w/structures/`.

## On-disk layout (code-accurate)

```
plugins/c2w/structures/
  <typeName>/
    structure.yml          # type definition: width, height, depth, resources
    instances/
      <id>.nbt             # one saved structure instance
    resources.nbt          # saved resource world for this type (see resource-system)
```

> The root `copilot-instructions.md`/old `ARCHITECTURE.md` said `structures/<type>/<id>.nbt` and a root `structures.yml` — that is **outdated**. The real paths are `instances/<id>.nbt` and per-type `structure.yml`. Trust this file.

## `FolderStructureTypeConfig` (type definitions)

Implements `TypeDimensionSource`. Reads/writes `structures/<type>/structure.yml`:
- `getDimensions(typeName)` → `int[]{width, height, depth}` (0 if missing/invalid).
- `getTypeNames()` → directory names under `structures/` that contain a `structure.yml`.
- `getResourceRequirements(typeName)` → `Map<resourceId, minSpots>` from the `resources` section.
- `getResourceType(typeName, resourceId)` → `"block"` or `"container"`, or null.
- `saveType`, `setResourceRequirements`, `addResourceRequirement`, `removeResourceRequirement`, `removeType`.

## `NbtStructureSource` (discovery + transport)

- Constructor: `(dataFolder, MinecraftManager, TypeDimensionSource, FileSystemOps)`.
- `discover()` scans `structures/<type>/instances/*.nbt`, loads each via `mc.structures().loadStructure(nbtFile)`, records the Bukkit structure id keyed by `type/id`, and returns `List<StructureData>` (type, id, corner `BlockPos` from size).
- `transport(template, worldName, pos, rotation)` looks up the cached structure id (or reloads the NBT) and calls `mc.structures().place(...)`.
- `getNbtDimensions(type, id)` / `getTypeDimensions(type)` for size queries.

## `StructureManager` (registry)

Owns `Map<String, List<StructureData>> templatesByTypeName` and `Map<type, Set<usedInstanceIds>>` (so the same instance isn't placed twice until all are used).
- `discoverTemplates()` → delegates to `NbtStructureSource.discover()`, repopulates the map. **Always call this before placing** (GameManager.start does).
- `placeRandom(typeName, worldName, pos, rotation)` → picks an unused instance (or random if all used), transports it, marks it used.
- `hasType(typeName)`, `getTemplates(typeName)`, `getTemplateCount()`.

## `StructureCreationManager` (creation worlds)

Builds structures in transient void worlds `c2w_create_<type>_<id>`.
- `createCreationWorld(player, type, id)` — creates void world, shows particle boundary (END_ROD along 12 edges), locks with `.lock_<type>_<id>` file, tracks visualization armor stands.
- `saveCreationWorld(...)` — exports the built structure to `instances/<id>.nbt` via `mc.structures()`, destroys the world, removes lock.
- `discardCreationWorld(...)` — destroys without saving.
- `modifyCreationWorld(...)` — loads existing NBT into a fresh void world for editing.
- `restoreSessions()` (called in constructor) re-creates active creation worlds on plugin enable using lock files.
- `destroyAllCreationWorlds()` — used by `GameManager.start` to clean up before a game.

## `StructureCommand` (`/structure`)

Tree of `CommandPiece` nodes (see `command-system.instructions.md`). Subcommands: `define`, `resize`, `create`, `modify`, `list`, `delete`, `resource world`, `resource mark`, `save`, `discard`. It is context-sensitive based on the player's current world (`c2w_create_*` vs `c2w_resource_*` vs lobby). `DynamicListChoiceCommandPiece` supplies type/id suggestions from `FolderStructureTypeConfig`/`StructureManager`.

## Common pitfalls (structures)

- **NBT path is `instances/<id>.nbt`**, not `<id>.nbt`. `NbtStructureSource` scans `instances/`.
- **Type config is per-type `structure.yml`**, not a root `structures.yml`.
- **Call `discoverTemplates()` before placing** or the template map is empty and placement fails validation in `GameManager.start`.
- **Resource NBT** for a type lives at `structures/<type>/resources.nbt` (see `resource-system.instructions.md`) — distinct from instance NBTs.
- **Lock files** (`.lock_<type>_<id>`) prevent concurrent editing and drive session restore; don't delete them outside the manager.
