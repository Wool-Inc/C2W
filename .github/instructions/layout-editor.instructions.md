---
description: "Use when working on C2W map layouts, layout YAML files, the layout editor worlds, or the /layout command (create, modify, place, remove, rotate, move, setspawn, save, discard). Covers LayoutEditorManager, LayoutManager, LayoutEditorCommand, MapLayout, LayoutCell, LayoutPlacement."
name: "C2W Layout Editor"
applyTo: ["src/main/java/net/klaaswhite/c2w/adapter/managers/LayoutEditorManager.java", "src/main/java/net/klaaswhite/c2w/domain/managers/LayoutManager.java", "src/main/java/net/klaaswhite/c2w/adapter/commands/LayoutEditorCommand.java", "src/main/java/net/klaaswhite/c2w/domain/model/MapLayout.java", "src/main/java/net/klaaswhite/c2w/domain/model/LayoutCell.java", "src/main/java/net/klaaswhite/c2w/domain/model/LayoutPlacement.java", "src/main/java/net/klaaswhite/c2w/domain/config/YamlLayoutFileLoader.java"]
---

# C2W Layout Editor

A "layout" defines where structure instances are placed in the game world. Layouts are authored in transient editor worlds and persisted as YAML in `plugins/c2w/layouts/`.

## On-disk layout

```
plugins/c2w/layouts/
  <layoutName>.yml
```

`LayoutManager` discovers `*.yml` files in the `layouts/` dir (via `FileSystemOps`), caches `MapLayout` by name, and clears the cache on plugin reset (`clearCache()`). `getLayoutNames()` / `getEditorLayoutNames()` list available layouts.

## `MapLayout` / `LayoutCell` / `LayoutPlacement` (domain model)

- `MapLayout` — holds `List<LayoutCell>` and a flag `placementsBased` (true when cells store a center position + yaw; false for grid layouts where `worldPosition()` is already the grid-snapped corner).
- `LayoutCell` — `typeName()` (structure type), `worldPosition()` (`BlockPos`), `yaw()` (rotation used for placement offset).
- `LayoutPlacement` — a placement record used by the editor to track a structure outline in the editor world.

## `LayoutEditorManager` (editor worlds)

Builds layouts in transient void worlds `c2w_layout_<layoutName>`.
- `createEditorWorld(player, layoutName)` / `modifyEditorWorld(player, layoutName)` — create/load the editor world, teleport player to `(0,65,0)`, show boundary particles, set up a scoreboard legend.
- `placeStructure(player, type, ...)` — places a wireframe/structure outline at the player's location; records a `LayoutPlacement`.
- `removeStructure`, `rotateStructure`, `moveStructure` — edit placements.
- `setSpawn(player, index, red|blue)` — marks a structure as a team spawn island.
- `saveLayout(player)` — serializes placements → `layouts/<name>.yml` via `LayoutManager`/`YamlLayoutFileLoader`.
- `discardLayout(player)` — destroys the editor world without saving.
- Particle boundaries and per-cell `BukkitTask` maps are tracked for cleanup; manager is `AutoCloseable`.

## `LayoutEditorCommand` (`/layout`)

Tree of `CommandPiece` nodes (see `command-system.instructions.md`). Subcommands: `create`, `modify`, `list`, `place`, `remove`, `rotate`, `move`, `setspawn`, `save`, `discard`. Depends on `LayoutEditorManager`, `LayoutManager`, `FolderStructureTypeConfig`, `StructureManager`, `WorldManager`, `GameManager`.

## Placement math (shared with game core)

When the game starts, `GameManager.placeCellStructure` offsets each placements-based cell from its **center** to the NBT origin corner using `yawToRotation(cell.yaw())` and the type dimensions from `FolderStructureTypeConfig.getDimensions`. Grid layouts already provide the corner. If you change how layouts store positions, update both `LayoutEditorManager` (authoring) and `GameManager.start` (consumption) together.

## Common pitfalls (layouts)

- **`placementsBased` flag matters:** center vs corner semantics differ. Keep editor and game placement in sync.
- **Layouts dir is `layouts/`**, not `structures/`. `LayoutManager` only reads `*.yml` there.
- **Cache:** `LayoutManager` caches layouts; call `clearCache()` (or rely on reset) after editing files externally.
- **Rotation offset** lives in `GameManager.start`; don't duplicate the math in the editor without understanding the game-side consumption.
