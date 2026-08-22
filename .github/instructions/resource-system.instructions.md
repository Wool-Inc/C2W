---
description: "Use when working on C2W resource worlds, resource instances, the /structure resource subcommands (world, mark, save, discard), or how chests/spawners are embedded into structures. Covers ResourceManager, the resourceinstance marker, and structures/<type>/resources.nbt."
name: "C2W Resource System"
applyTo: ["src/main/java/net/klaaswhite/c2w/adapter/managers/ResourceManager.java", "src/main/java/net/klaaswhite/c2w/adapter/commands/StructureCommand.java"]
---

# C2W Resource System

Resources (chests, spawners, containers) are defined per structure type in a transient "resource world" and saved as a single NBT that gets embedded when the structure is placed in the game world.

## On-disk layout

```
plugins/c2w/structures/<type>/
  resources.nbt     # saved resource world for this type
  structure.yml     # resources.<id>.minSpots / .type (see structure-editor.instructions.md)
```

`ResourceManager.resourceNbtFile(type)` → `structures/<type>/resources.nbt`. `resourceWorldName(type)` → `c2w_resource_<type>`.

## `ResourceManager` (resource worlds)

- `openResourceWorld(player, typeName)` — opens/creates void world `c2w_resource_<type>`; if `resources.nbt` exists, loads it via `mc.structures().place(...)` at origin; shows particle boundary from `FolderStructureTypeConfig.getDimensions`; teleports the player.
- `markResourceBlock(player, type, resourceId)` — targets the looked-at block (within 5 blocks):
  - **Container** (chest/barrel/…) → `markContainerResource`: spawns a `Marker` with PDC key `resourceinstance` = `resourceId` (container mode; one marker, multiple variants from inventory slots). Blocked if block-mode markers already exist for that resource.
  - **Non-container** → `markBlockResource`: block mode (one marker = one spot).
  - A resource uses **either** container mode **or** block mode, never both.
- `saveResourceWorld(player, type)` — `saveResourceNbt` scans tile entities + marker positions for bounds, captures via `mc.structures().createStructure`/`saveStructure` to `resources.nbt`, and `saveMinSpotsToConfig` writes per-resource `minSpots` into `structure.yml`. Container-mode variant counts are written as sidecar data via `saveContainerSidecars`.
- `discardResourceWorld(player, type)` — destroys the world without saving.
- `getResourceInstanceCount(worldName, resourceId)` — counts `resourceinstance` markers (block mode: one per marker; container mode: variant count).

## Marker key

Resource instances are marked with the `resourceinstance` `NamespacedKey` (a `NamespacedKey(plugin, "resourceinstance")`), value = `resourceId` (block mode) or `resourceId` (container mode). `mc.markers().findMarkersInWorld(world, "resourceinstance", prefix)` is used to enumerate them. This is **separate** from the `map_marker` key used by the marker system (see `marker-system.instructions.md`).

## Flow summary

1. `/structure resource world <type>` → opens void world, loads existing `resources.nbt`.
2. Place tile entities (chests, spawners).
3. `/structure resource mark <resourceId>` → marks targeted block (container or block mode).
4. `/structure save` → writes `resources.nbt` + updates `minSpots` in `structure.yml`.
5. `/structure discard` → destroys without saving.

## Common pitfalls (resources)

- **Two marker systems:** `resourceinstance` (this system) vs `map_marker` (marker system). Don't confuse them.
- **Container vs block mode** are mutually exclusive per resourceId; `markContainerResource` refuses if block-mode markers exist.
- **`minSpots` is derived from markers at save time** and written into `structure.yml` under `resources.<id>.minSpots`. `FolderStructureTypeConfig.getResourceRequirements` reads it back.
- Resource NBT is `structures/<type>/resources.nbt` — distinct from instance NBTs in `instances/`.
