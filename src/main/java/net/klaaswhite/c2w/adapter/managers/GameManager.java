package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.domain.game.GameStateMachine;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.events.DraftCreatedEvent;
import net.klaaswhite.c2w.domain.events.EndGameEvent;
import net.klaaswhite.c2w.domain.events.GameWorldCreatedEvent;
import net.klaaswhite.c2w.domain.events.PreviewRequestEvent;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.LayoutCell;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import net.klaaswhite.c2w.domain.model.Mirror;
import net.klaaswhite.c2w.domain.model.StructureData;
import net.klaaswhite.c2w.domain.model.StructureRotation;
import net.klaaswhite.c2w.adapter.minecraft.MarkerEntity;
import net.klaaswhite.c2w.adapter.minecraft.Markers;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.bootstrap.config.PluginConfig;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.bukkit.World;

public class GameManager implements AutoCloseable {

    private final JavaPlugin plugin;
    private final EventManager eventManager;
    private final WorldManager worldManager;
    private final StructureManager structureManager;
    private final MinecraftManager mc;
    private final StructureCreationManager structureCreationManager;
    private final PluginConfig pluginConfig;
    private final PlayerManager playerManager;
    private final FolderStructureTypeConfig structureTypeConfig;

    private final GameStateMachine gsm;

    private @Nullable String selectedLayoutName;

    public GameManager(
            JavaPlugin plugin,
            EventManager eventManager,
            WorldManager worldManager,
            LayoutManager layoutManager,
            StructureManager structureManager,
            MinecraftManager mc,
            StructureCreationManager structureCreationManager,
            PluginConfig pluginConfig,
            PlayerManager playerManager,
            FolderStructureTypeConfig structureTypeConfig
    ) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.worldManager = worldManager;
        this.structureManager = structureManager;
        this.mc = mc;
        this.structureCreationManager = structureCreationManager;
        this.pluginConfig = pluginConfig;
        this.playerManager = playerManager;
        this.structureTypeConfig = structureTypeConfig;

        this.gsm = new GameStateMachine(layoutManager);
    }

    public GameStateMachine.State getState() {
        return gsm.getState();
    }

    public boolean isNotStarted() {
        return gsm.isNotStarted();
    }

    public boolean isDraftCreated() {
        return gsm.isDraftCreated();
    }

    public boolean isGameInProgress() {
        return gsm.isGameInProgress();
    }

    public boolean isGameEnded() {
        return gsm.isGameEnded();
    }

    public void selectLayout(CommandInput input, String layoutName) {
        if (!(input.commandSender instanceof Player player))
            return;

        if (!gsm.isDraftCreated()) {
            player.sendMessage("Layout can only be selected during draft phase. Use /c2w init first.");
            return;
        }

        var layout = gsm.resolveLayout(layoutName);
        if (layout == null) {
            var available = gsm.resolveLayoutNames();
            player.sendMessage("Layout '" + layoutName + "' not found. Available layouts: " + String.join(", ", available));
            return;
        }

        this.selectedLayoutName = layoutName;
        player.sendMessage("§aLayout '" + layoutName + "' selected for next game start.");
    }

    public @Nullable String getSelectedLayoutName() {
        return selectedLayoutName;
    }

    public void init(CommandInput input) {
        if (!(input.commandSender instanceof Player player))
            return;

        if (!gsm.canInit()) {
            player.sendMessage("Cannot initialize: game is currently in " + gsm.getState() + " state. Use /c2w reset first.");
            return;
        }

        var draft = worldManager.createDraftWorld();
        if (draft == null) {
            player.sendMessage("Failed to create draft world.");
            return;
        }

        eventManager.pushInternalEvent(new DraftCreatedEvent(draft.getName()));

        player.teleport(draft.getSpawnLocation());

        gsm.transitionToDraftCreated();
        player.sendMessage("Draft world created. Use /team to add players, then /c2w start [layout].");
    }

    public void start(CommandInput input) {
        start(input, null);
    }

    public void preview(CommandInput input) {
        eventManager.pushInternalEvent(new PreviewRequestEvent(input));
    }

    public void start(CommandInput input, @org.jspecify.annotations.Nullable String layoutName) {
        if (!(input.commandSender instanceof Player player))
            return;

        if (!gsm.canStart()) {
            player.sendMessage("Game can only be started after /c2w init.");
            return;
        }

        if (worldManager.isGameWorldCreated()) {
            player.sendMessage("A game world already exists. Use /c2w reset to clear it.");
            return;
        }

        // Use explicitly provided layout, or previously selected layout, or prompt for selection
        if (layoutName == null) {
            layoutName = selectedLayoutName;
        }

        boolean autoSelected = false;
        if (layoutName == null) {
            var layoutNames = gsm.resolveLayoutNames();
            if (layoutNames.size() == 1) {
                layoutName = layoutNames.get(0);
                autoSelected = true;
            } else if (layoutNames.size() > 1) {
                player.sendMessage("Multiple layouts available: " + String.join(", ", layoutNames) + ". Use /c2w layout select <layout> or /c2w start <layout>.");
                return;
            } else {
                player.sendMessage("No layouts configured. Use /c2w layout list to see available layouts.");
                return;
            }
        }

        var layout = gsm.resolveLayout(layoutName);
        if (layout == null) {
            player.sendMessage("Layout '" + layoutName + "' not found or no layouts configured.");
            return;
        }

        // Refresh template registry before checking
        structureManager.discoverTemplates();

        // Validate that all layout cell types have at least one template available
        List<String> missingTypes = layout.getCells().stream()
                .map(LayoutCell::typeName)
                .distinct()
                .filter(typeName -> !structureManager.hasType(typeName))
                .toList();
        if (!missingTypes.isEmpty()) {
            player.sendMessage("Cannot start game: missing structure templates for type(s): "
                    + String.join(", ", missingTypes) + ".");
            return;
        }

        var game = worldManager.createGameWorld();
        if (game == null) {
            player.sendMessage("Failed to create game world.");
            return;
        }

        if (structureCreationManager != null) {
            structureCreationManager.destroyAllCreationWorlds();
        }

        eventManager.pushInternalEvent(new GameWorldCreatedEvent(game.getName()));

        for (var cell : layout.getCells()) {
            BlockPos pos = cell.worldPosition();
            var logger = java.util.logging.Logger.getLogger("C2W");

            // placements-based layouts store structure center (as shown by
            // the editor wireframe). Structure.place() rotates the NBT around
            // the origin corner, so the structure extends from that corner in a
            // rotation-dependent direction (verified against Minecraft's
            // transformedBlockPos):
            //   NONE (yaw≈0):   extends +X, +Z → corner = center − (w/2, d/2)
            //   CW_90  (yaw≈90):  extends −X, +Z → corner = center + (w/2, −d/2)
            //   CW_180 (yaw≈180): extends −X, −Z → corner = center + (w/2, d/2)
            //   CCW_90 (yaw≈270): extends +X, −Z → corner = center − (w/2, −d/2)
            // Grid layouts already compute worldPosition as the grid-snapped corner.
            if (layout.isPlacementsBased()) {
                int[] dims = structureTypeConfig.getDimensions(cell.typeName());
                if (dims != null) {
                    int hw = dims[0] / 2;
                    int hd = dims[2] / 2;
                    var rot = yawToRotation(cell.yaw());
                    int dx = switch (rot) {
                        case NONE, COUNTERCLOCKWISE_90    -> -hw;
                        case CLOCKWISE_90, CLOCKWISE_180  ->  hw;
                    };
                    int dz = switch (rot) {
                        case NONE, CLOCKWISE_90           -> -hd;
                        case CLOCKWISE_180, COUNTERCLOCKWISE_90 -> hd;
                    };
                    pos = new BlockPos(pos.x() + dx, pos.y(), pos.z() + dz);
                }
            }

            placeCellStructure(cell, game.getName(), pos);
        }

        if (autoSelected) {
            mc.server().broadcastMessage("Starting game with only layout '" + layout.getName() + "'.");
        } else {
            mc.server().broadcastMessage("Starting game with layout '" + layout.getName() + "'!");
        }

        eventManager.registerInternalEvent(WoolCapturedEvent.class, this::woolCapped);
        gsm.transitionToGameInProgress();

        // Clear selected layout after use
        this.selectedLayoutName = null;

        eventManager.pushInternalEvent(new StartGameEvent(game.getName()));

        String draftName = worldManager.getDraftWorld().getName();
        var gameWorld = worldManager.getGameWorld();
        for (String playerName : mc.server().getOnlinePlayerNames()) {
            String playerWorld = mc.players().getWorldName(playerName);
            if (playerWorld != null && playerWorld.equals(draftName)) {
                mc.players().teleportToWorld(playerName, gameWorld.getSpawnPos(), game.getName());
            }
        }
    }

    private @Nullable StructureData placeCellStructure(
            LayoutCell cell,
            String worldName,
            BlockPos pos
    ) {
        var placed = structureManager.placeRandom(cell.typeName(), worldName, pos, yawToRotation(cell.yaw()));
        if (placed == null) {
            mc.server().broadcastMessage(
                    "No templates available for structure type " + cell.typeName()
                            + " (cell " + cell.row() + "," + cell.col() + ")");
            return null;
        }
        var warning = structureManager.checkDimensions(placed.getTypeName(), placed.getId());
        if (warning != null) {
            mc.server().broadcastMessage("§eWarning: " + warning);
        }

        // Place resources (chests, spawners, etc.) defined for this structure type
        placeResources(cell.typeName(), worldName, pos, yawToRotation(cell.yaw()));

        if ("SPAWN".equalsIgnoreCase(cell.typeName()) && cell.team() != null) {
            placeSpawnMarkers(placed, worldName, pos, cell.team());
        }

        return placed;
    }

    /**
     * Place resource blocks directly from in-memory block data (no 1×1×1 structures).
     *
     * Flow per call (one structure cell):
     * 1. Find resourcespot markers in the game world within this structure's bounding box
     * 2. Load resources.nbt into the reference world
     * 3. For each resourceinstance marker: read block + tile entity data into memory
     *    – block mode: Material + BlockData + tile entity BlockState (spawner NBT, chest contents, etc.)
     *    – container mode: each non-empty inventory slot as an ItemStack variant
     * 4. Clean up reference world immediately
     * 5. For each resourceId's spots: shuffle, don't cycle (leave extra spots unfilled), place blocks
     */
    private void placeResources(String typeName, String worldName, BlockPos pos, StructureRotation rotation) {
        List<ManagedMarker> spotMarkers = mc.markers().findMarkersInWorld(worldName, "resourcespot", null);
        if (spotMarkers.isEmpty()) return;

        int[] filterDims = structureTypeConfig.getDimensions(typeName);
        if (filterDims != null) {
            // ponytail: rotated structures extend in different directions — compute rotated AABB
            // (must match the corner offset in start(): NONE +X/+Z, CW_90 −X/+Z, CW_180 −X/−Z, CCW_90 +X/−Z)
            int w = filterDims[0], h = filterDims[1], d = filterDims[2];
            int xMin, xMax, zMin, zMax;
            switch (rotation) {
                case CLOCKWISE_90 ->          { xMin = pos.x() - w;   xMax = pos.x();      zMin = pos.z();      zMax = pos.z() + d; }
                case CLOCKWISE_180 ->         { xMin = pos.x() - w;   xMax = pos.x();      zMin = pos.z() - d;  zMax = pos.z(); }
                case COUNTERCLOCKWISE_90 ->   { xMin = pos.x();       xMax = pos.x() + w;  zMin = pos.z() - d;  zMax = pos.z(); }
                default ->                    { xMin = pos.x();       xMax = pos.x() + w;  zMin = pos.z();      zMax = pos.z() + d; }
            }
            final int fxMin = xMin, fxMax = xMax, fzMin = zMin, fzMax = zMax;
            spotMarkers.removeIf(m -> {
                BlockPos p = m.getPosition();
                return p.x() < fxMin || p.x() >= fxMax
                    || p.y() < pos.y() || p.y() >= pos.y() + h
                    || p.z() < fzMin || p.z() >= fzMax;
            });
        }
        if (spotMarkers.isEmpty()) return;

        // Group spots by resourceId (PDC value is "resourceId-counter", e.g. "chest-0")
        Map<String, List<BlockPos>> spotsByResourceId = new HashMap<>();
        for (var marker : spotMarkers) {
            String val = marker.getName();
            if (val == null) continue;
            int lastDash = val.lastIndexOf('-');
            if (lastDash < 0) continue;
            String resourceId = val.substring(0, lastDash);
            spotsByResourceId.computeIfAbsent(resourceId, k -> new ArrayList<>()).add(marker.getPosition());
        }

        File resFile = new File(mc.plugin().getDataFolder(), "structures/" + typeName + "/resources.nbt");
        if (!resFile.exists()) return;

        try {
            String structureId = mc.structures().loadStructure(resFile);

            // Paste into reference world to read blocks (NOT into game world)
            String refWorldName = worldManager.getReferenceWorld().getName();
            BlockPos refOrigin = new BlockPos(0, 64, 0);
            mc.structures().place(structureId, refWorldName, refOrigin, true,
                    StructureRotation.NONE, Mirror.NONE, -1, 1.0f, new Random());

            World refWorld = mc.worlds().getWorld(refWorldName);
            if (refWorld != null) refWorld.getChunkAt(refOrigin.x() >> 4, refOrigin.z() >> 4);

            // Read resource blocks into memory
            //   block mode: List<CapturedBlock>  (Material + BlockData + tile entity state)
            //   container mode: List<ItemStack>
            Map<String, List<Object>> variantsByResourceId = new HashMap<>();
            for (ManagedMarker m : mc.markers().findMarkersInWorld(refWorldName, "resourceinstance", null)) {
                String val = m.getName();
                if (val == null) continue;
                String resourceId = val.replaceAll("-\\d+$", "");
                String type = structureTypeConfig.getResourceType(typeName, resourceId);
                BlockPos bp = m.getPosition();

                if ("container".equals(type)) {
                    readContainerVariants(refWorldName, refWorld, bp, resourceId, variantsByResourceId);
                } else {
                    readBlockVariant(refWorldName, refWorld, bp, resourceId, variantsByResourceId);
                }
                m.remove();
            }

            // Clean up reference world
            int[] dims = structureTypeConfig.getDimensions(typeName);
            if (dims != null) {
                for (int x = 0; x < dims[0]; x++)
                    for (int y = 0; y < dims[1]; y++)
                        for (int z = 0; z < dims[2]; z++)
                            mc.blocks().setBlock(refWorldName,
                                    new BlockPos(refOrigin.x() + x, refOrigin.y() + y, refOrigin.z() + z),
                                    Material.AIR);
            }

            // Place blocks at spots in the game world
            World gameWorld = mc.worlds().getWorld(worldName);
            if (gameWorld == null) return;

            Map<String, Integer> minSpots = structureTypeConfig.getResourceRequirements(typeName);
            Random random = new Random();

            for (var entry : spotsByResourceId.entrySet()) {
                String resourceId = entry.getKey();
                List<BlockPos> spots = entry.getValue();
                List<Object> variants = variantsByResourceId.get(resourceId);

                if (variants == null || variants.isEmpty()) {
                    plugin.getLogger().warning("[placeResources] No resource instances found for '"
                            + resourceId + "', skipping (" + spots.size() + " spots)");
                    continue;
                }

                int required = minSpots.getOrDefault(resourceId, 0);
                if (required > variants.size()) {
                    plugin.getLogger().warning("[placeResources] " + resourceId + " needs " + required
                            + " spots but only " + variants.size() + " resource instances"
                            + " — leaving extra spots unfilled");
                }

                Collections.shuffle(spots, random);
                Collections.shuffle(variants, random);

                // Place without cycling — extra spots stay unfilled
                int placeCount = Math.min(spots.size(), variants.size());
                for (int i = 0; i < placeCount; i++) {
                    BlockPos spotPos = spots.get(i);
                    Object variant = variants.get(i);
                    try {
                        if (variant instanceof ItemStack item) {
                            placeResourceFromItem(gameWorld, spotPos, item);
                        } else if (variant instanceof CapturedBlock cb) {
                            placeCapturedBlock(gameWorld, spotPos, cb);
                        }
                    } catch (Exception e) {
                        mc.server().broadcastMessage("§eWarning: failed to place resource block at " + spotPos);
                        plugin.getLogger().warning("[placeResources] Failed at " + spotPos + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            mc.server().broadcastMessage("§eWarning: failed to place resources for " + typeName + ": " + e.getMessage());
        }
    }

    /** In-memory block snapshot for block-mode resources. */
    private record CapturedBlock(Material material, BlockData blockData, @Nullable BlockState tileState) {}

    /** Read a block-mode resource variant: full block + tile entity data. */
    private void readBlockVariant(String refWorldName, World refWorld, BlockPos bp, String resourceId,
                                  Map<String, List<Object>> variantsByResourceId) {
        Block block = refWorld.getBlockAt(bp.x(), bp.y(), bp.z());
        Material mat = block.getType();
        if (mat.isAir()) return;
        BlockData bd = block.getBlockData().clone();
        BlockState state = block.getState().copy();
        variantsByResourceId.computeIfAbsent(resourceId, k -> new ArrayList<>())
                .add(new CapturedBlock(mat, bd, state));
    }

    /** Read container-mode resource variants: each non-empty inventory slot becomes one variant. */
    private void readContainerVariants(String refWorldName, World refWorld, BlockPos bp, String resourceId,
                                       Map<String, List<Object>> variantsByResourceId) {
        Block block = refWorld.getBlockAt(bp.x(), bp.y(), bp.z());
        if (!(block.getState() instanceof Container container)) return;
        List<Object> list = variantsByResourceId.computeIfAbsent(resourceId, k -> new ArrayList<>());
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                list.add(item.clone());
            }
        }
    }

    /** Place a block-mode resource: set type + BlockData, then restore tile entity data. */
    private void placeCapturedBlock(World world, BlockPos pos, CapturedBlock cb) {
        if (cb.material.isAir()) return;
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        block.setType(cb.material, false);
        block.setBlockData(cb.blockData, false);

        if (cb.tileState == null) return;
        BlockState current = block.getState();

        if (cb.tileState instanceof CreatureSpawner src && current instanceof CreatureSpawner dst) {
            dst.setSpawnedType(src.getSpawnedType());
            dst.setDelay(src.getDelay());
            dst.setMinSpawnDelay(src.getMinSpawnDelay());
            dst.setMaxSpawnDelay(src.getMaxSpawnDelay());
            dst.setSpawnCount(src.getSpawnCount());
            dst.setMaxNearbyEntities(src.getMaxNearbyEntities());
            dst.setRequiredPlayerRange(src.getRequiredPlayerRange());
            dst.setSpawnRange(src.getSpawnRange());
            dst.update();
        } else if (cb.tileState instanceof Container src && current instanceof Container dst) {
            dst.getInventory().setContents(src.getInventory().getContents());
            dst.update();
        } else {
            // Generic fallback: apply BlockData and force update
            current.setBlockData(cb.blockData);
            current.update(true);
        }
    }

    /** Place a container-mode resource: convert ItemStack to a block, applying NBT for spawners. */
    private void placeResourceFromItem(World world, BlockPos pos, ItemStack item) {
        Material mat = item.getType();
        if (!mat.isBlock() || mat.isAir()) {
            plugin.getLogger().warning("[placeResources] Item " + mat + " is not a block, skipping");
            return;
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        block.setType(mat, false);

        if (mat == Material.SPAWNER && item.getItemMeta() instanceof BlockStateMeta bsm
                && bsm.hasBlockState()) {
            if (block.getState() instanceof CreatureSpawner spawner) {
                BlockState spawnerState = bsm.getBlockState();
                if (spawnerState instanceof CreatureSpawner cs) {
                    spawner.setSpawnedType(cs.getSpawnedType());
                    spawner.setDelay(cs.getDelay());
                    spawner.setMinSpawnDelay(cs.getMinSpawnDelay());
                    spawner.setMaxSpawnDelay(cs.getMaxSpawnDelay());
                    spawner.setSpawnCount(cs.getSpawnCount());
                    spawner.setMaxNearbyEntities(cs.getMaxNearbyEntities());
                    spawner.setRequiredPlayerRange(cs.getRequiredPlayerRange());
                    spawner.setSpawnRange(cs.getSpawnRange());
                    spawner.update();
                }
            }
        }
    }

    private void placeSpawnMarkers(StructureData placed, String worldName, BlockPos pos, String team) {
        int radius = 64;
        int minX = pos.x() - radius, maxX = pos.x() + radius;
        int minY = pos.y() - radius, maxY = pos.y() + radius;
        int minZ = pos.z() - radius, maxZ = pos.z() + radius;

        var worldMarkers = mc.markers().getMarkersInWorld(worldName);
        for (var markerEntity : worldMarkers) {
            var markerPos = markerEntity.getPosition();
            if (markerPos.x() < minX || markerPos.x() > maxX) continue;
            if (markerPos.y() < minY || markerPos.y() > maxY) continue;
            if (markerPos.z() < minZ || markerPos.z() > maxZ) continue;

            String name = markerEntity.getPersistentData(mc.markers().getMarkerKey());
            // The marker stays generic "spawnpoint"; the team is determined by the layout
            // cell (passed in via `team`), not by the marker name. spawn-<team> is legacy.
            if ("spawnpoint".equalsIgnoreCase(name)) {
                mc.server().broadcastMessage("Marked spawnpoint for team " + team + " at ("
                        + markerPos.x() + ", " + markerPos.y() + ", " + markerPos.z() + ")");
            }
        }
    }

    public void end(CommandInput input) {
        if (!(input.commandSender instanceof Player player))
            return;

        if (!gsm.canEnd()) {
            player.sendMessage("Game can only be ended after it was started (/c2w start)");
            return;
        }

        end();
        player.sendMessage("Game ended. Use /c2w reset to return to the lobby.");
    }

    public void end() {
        gsm.transitionToGameEnded();
        eventManager.unregisterInternalEvent(WoolCapturedEvent.class, this::woolCapped);
        eventManager.pushInternalEvent(new EndGameEvent());
    }

    public void woolCapped(WoolCapturedEvent event) {
        gsm.onWoolCaptured(event);
    }

    public int getWoolCount(String teamName) {
        return gsm.getWoolCount(teamName);
    }
    public void reset() {
        eventManager.unregisterInternalEvent(WoolCapturedEvent.class, this::woolCapped);
        gsm.reset();
        this.selectedLayoutName = null;
        structureManager.clearUsedInstances();
        eventManager.pushInternalEvent(new net.klaaswhite.c2w.domain.events.ResetEvent());
    }

    /**
     * Convert a yaw angle (0-360, Minecraft convention: 0=south, +west, -east)
     * to the nearest StructureRotation for NBT placement.
     */
    private static StructureRotation yawToRotation(float yaw) {
        // Normalize to 0-360
        float normalized = ((yaw % 360) + 360) % 360;
        if (normalized < 45 || normalized >= 315) return StructureRotation.NONE;       // south
        if (normalized < 135) return StructureRotation.CLOCKWISE_90;                   // west
        if (normalized < 225) return StructureRotation.CLOCKWISE_180;                   // north
        return StructureRotation.COUNTERCLOCKWISE_90;                                   // east
    }

    @Override
    public void close() {
        reset();
    }
}
