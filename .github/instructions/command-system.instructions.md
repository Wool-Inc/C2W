---
description: "Use when working on C2W commands, the command tree, tab completion, CommandPiece nodes, or adding/extending a subcommand (C2WCommand, StructureCommand, MarkerCommand, WorldCommand, LayoutEditorCommand). Covers the CommandPiece/TreeChoiceCommandPiece/ListChoiceCommandPiece/DynamicListChoiceCommandPiece framework and BaseCommand."
name: "C2W Command System"
applyTo: ["src/main/java/net/klaaswhite/c2w/adapter/commands/**", "src/main/java/net/klaaswhite/c2w/domain/commands/**"]
---

# C2W Command System

Commands are built as a tree of `CommandPiece` nodes traversed left-to-right over the args. Each top-level command class extends `BaseCommand` and registers itself with Bukkit in its constructor.

## Command piece types (`domain/commands/`)

- **`CommandPiece`** — terminal node with a handler lambda `Function<CommandInput, Boolean>`. Consumes one arg.
- **`TreeChoiceCommandPiece`** — branch; `addChoice(display, nextPiece)` matches the next arg against registered choices; `getNextPiece(choice)` returns the child.
- **`ListChoiceCommandPiece`** — presents a fixed `List<String>` of choices (tab completion).
- **`DynamicListChoiceCommandPiece`** — presents choices from a `Supplier<List<String>>` (e.g. type/id suggestions). Can be an intermediate node (consumes arg, forwards to `nextPiece`) or terminal (has a handler).

## `CommandInput` and arg indexing (critical)

When a handler runs, `commandInput.strings` still contains **ALL** original args, including the consumed tree nodes. Index 0 is the first arg **after the command name**.

Example `/structure define dungeon 16 10 16`:
- Tree consumes: `define` → `dungeon` → `16` → `10` → `16` (handler).
- `commandInput.strings` = `["define", "dungeon", "16", "10", "16"]`.
- Handler reads: `strings[0]` = `"define"`, `strings[1]` = `"dungeon"` (type), `strings[2]` = width, `strings[3]` = height, `strings[4]` = depth.

Always account for every consumed tree node when indexing `strings`.

## `BaseCommand` (adapter/commands/)

- Subclasses implement `getCommandName()` (the Bukkit command label, e.g. `"structure"`) and `createCommandChain()` (builds `initialCommandPiece`).
- `register()` wires `C2WCommandExecutor` (execution) and `C2WTabCompleter` (completion) to Bukkit.
- `onCommand` walks the tree: for each arg, `getNextPiece(choice)`; at the end calls `execute(input)` on the current piece.
- `onTabComplete` walks to the last arg and returns `getChoices(input)`.
- Implements `AutoCloseable` (unregisters executor/completer on close — used by `App.closeables`).

## Top-level commands

| Class | Label | Purpose |
|---|---|---|
| `C2WCommand` | `c2w` | init, start, preview, end, reset, reload, layout select, wooltimer settings |
| `StructureCommand` | `structure` | define, resize, create, modify, list, delete, resource world/mark/save/discard |
| `MarkerCommand` | `marker` | create, remove, list |
| `WorldCommand` | `world` | world utilities |
| `LayoutEditorCommand` | `layout` | create, modify, list, place, remove, rotate, move, setspawn, save, discard |

All are constructed and registered in `CommandManager` (wired in `App.initCommands`). `CommandManager` itself is added to `App.closeables`.

## Adding a subcommand

1. In the relevant `*Command` class, extend `createCommandChain()` with new `TreeChoiceCommandPiece`/`CommandPiece` nodes.
2. Add a handler method; read args from `commandInput.strings` accounting for consumed nodes.
3. For tab completion, use `ListChoiceCommandPiece` / `DynamicListChoiceCommandPiece` with a supplier.
4. No change to `plugin.yml` is needed if the command label already exists; new labels must be added to `plugin.yml`.

## Common pitfalls (commands)

- **Arg indices include consumed tree nodes** — the #1 source of off-by-one bugs.
- **`DynamicListChoiceCommandPiece`** can be intermediate OR terminal; set both `nextPiece` and a handler as needed.
- **New command labels** require an entry in `plugin.yml` (root `resources/plugin.yml`).
- **Handlers return `Boolean`**; returning `false` typically means "no match / usage message".
