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
import net.klaaswhite.c2w.domain.model.MapLayout;
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
    private @Nullable MapLayout activeLayout;

    // Team name -> spawnpoint BlockPos, captured from SPAWN structure markers at game start.
    private final Map<String, BlockPos> teamSpawnPoints = new HashMap<>();

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

        // Show immediate title feedback before the heavy draft world creation
        mc.players().sendTitle(player.getName(), "§6⚔ Draft Starting ⚔", "§ePreparing the draft world…", 10, 60, 20);

        var draft = worldManager.createDraftWorld();
        if (draft == null) {
            player.sendMessage("Failed to create draft world.");
            return;
        }

        eventManager.pushInternalEvent(new DraftCreatedEvent(draft.getName()));

        // Reset the initiator to survival so they're not stuck in spectator
        // from a previous game when arriving in the draft world.
        mc.players().setGameMode(player.getName(), "SURVIVAL");
        player.teleport(draft.getSpawnLocation());

        gsm.transitionToDraftCreated();
        player.sendMessage("Draft world created. Use /c2w team join <player> <team> to add players, then /c2w start [layout].");
        // Confirm the draft is ready
        mc.players().sendTitle(player.getName(), "§6⚔ Draft Ready ⚔", "§eUse /c2w team join to add players", 10, 60, 20);
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
        this.activeLayout = layout;

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
            // The +X/+Z offsets use hw = w/2 so the unrotated footprint is
            // [center−hw, center−hw+w−1]. The −X/−Z directions extend the other
            // way, so their corner must be the mirror of that: (w−1)−hw. This
            // equals hw when w is odd but is hw−1 when w is even — without this
            // the rotated structures land one block off-centre (e.g. 180° cells
            // shifted 1 block sideways from their editor wireframe).
            // Grid layouts already compute worldPosition as the grid-snapped corner.
            if (layout.isPlacementsBased()) {
                int[] dims = structureTypeConfig.getDimensions(cell.typeName());
                if (dims != null) {
                    int w = dims[0];
                    int d = dims[2];
                    int hw = w / 2;
                    int hd = d / 2;
                    int maxX = (w - 1) - hw;   // corner offset when extending −X
                    int maxZ = (d - 1) - hd;   // corner offset when extending −Z
                    var rot = yawToRotation(cell.yaw());
                    int dx = switch (rot) {
                        case NONE, COUNTERCLOCKWISE_90    -> -hw;
                        case CLOCKWISE_90, CLOCKWISE_180  -> maxX;
                    };
                    int dz = switch (rot) {
                        case NONE, CLOCKWISE_90           -> -hd;
                        case CLOCKWISE_180, COUNTERCLOCKWISE_90 -> maxZ;
                    };
                    pos = new BlockPos(pos.x() + dx, pos.y(), pos.z() + dz);
                }
            }

            placeCellStructure(cell, game.getName(), pos);
        }

        // Show immediate title feedback to all players before the game starts
        for (String name : mc.server().getOnlinePlayerNames()) {
            mc.players().sendTitle(name, "§c⚔ Game Starting ⚔", "§ePlacing structures and preparing the arena…", 10, 60, 20);
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
        var teleportLogger = java.util.logging.Logger.getLogger("C2W");
        for (String playerName : mc.server().getOnlinePlayerNames()) {
            String playerWorld = mc.players().getWorldName(playerName);
            teleportLogger.info("[GameManager.start] processing " + playerName
                    + " world=" + playerWorld + " draft=" + draftName);
            if (playerWorld != null && playerWorld.equals(draftName)) {
                BlockPos target = gameWorld.getSpawnPos();
                String gameMode = "SPECTATOR";
                teleportLogger.info("[GameManager.start] " + playerName + " is in draft world, fallback target=" + target);
                var mp = playerManager.getPlayer(playerName);
                if (mp != null) {
                    teleportLogger.info("[GameManager.start] " + playerName + " has ManagedPlayer, team="
                            + (mp.getTeam() != null ? mp.getTeam().teamName : "null"));
                    var team = mp.getTeam();
                    if (team != null) {
                        if ("Spectator".equalsIgnoreCase(team.teamName)) {
                            // Spectators: teleport to fallback, stay in spectator mode
                            teleportLogger.info("[GameManager.start] " + playerName + " is a spectator");
                        } else {
                            // Red/Blue team members: set survival and teleport to team spawn
                            gameMode = "SURVIVAL";
                            var teamSpawn = teamSpawnPoints.get(team.teamName);
                            teleportLogger.info("[GameManager.start] " + playerName + " team=" + team.teamName
                                    + " teamSpawnPoints containsKey=" + teamSpawnPoints.containsKey(team.teamName)
                                    + " teamSpawn=" + teamSpawn);
                            if (teamSpawn != null) {
                                // Spawn on the marker block itself (the marker sits at the player's feet)
                                target = new BlockPos(teamSpawn.x(), teamSpawn.y(), teamSpawn.z());
                                teleportLogger.info("[GameManager.start] " + playerName + " using team spawn target=" + target);
                            }
                        }
                    } else {
                        // No team: put them on spectator team, stay in spectator mode
                        teleportLogger.info("[GameManager.start] " + playerName + " has no team, assigning spectator");
                        var specTeam = net.klaaswhite.c2w.domain.model.ManagedTeam.teams.get("Spectator");
                        if (specTeam != null) mp.setTeam(specTeam);
                    }
                } else {
                    teleportLogger.warning("[GameManager.start] " + playerName + " has NO ManagedPlayer in registry!");
                }
                mc.players().setGameMode(playerName, gameMode);
                mc.players().teleportToWorld(playerName, target, game.getName());
                // Set the player's respawn point so they respawn in the game world
                // (at their team's spawn) rather than the main overworld on death.
                mc.players().setRespawnLocation(playerName, target, game.getName(), true);
            } else {
                teleportLogger.info("[GameManager.start] " + playerName + " NOT in draft world (world="
                        + playerWorld + "), skipping teleport");
            }
        }

        // Confirm the game has fully started
        for (String name : mc.server().getOnlinePlayerNames()) {
            mc.players().sendTitle(name, "§a⚔ Game Started! ⚔", "§eCapture 2 wools to win!", 10, 80, 30);
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
            // ponytail: rotated structures extend in different directions — compute rotated AABB.
            // Ranges are half-open [min, max) and must match where start() actually
            // placed the structure around `pos` (its origin corner):
            //   NONE  → +X by w, +Z by d
            //   CW_90 → −X by d, +Z by w   (the −X run is [pos-(d-1) … pos])
            //   CW_180→ −X by w, −Z by d
            //   CCW_90→ +X by d, −Z by w
            int w = filterDims[0], h = filterDims[1], d = filterDims[2];
            int xMin, xMax, zMin, zMax;
            switch (rotation) {
                case CLOCKWISE_90 ->          { xMin = pos.x() - d + 1; xMax = pos.x() + 1;     zMin = pos.z();      zMax = pos.z() + w; }
                case CLOCKWISE_180 ->         { xMin = pos.x() - w + 1; xMax = pos.x() + 1;     zMin = pos.z() - d + 1; zMax = pos.z() + 1; }
                case COUNTERCLOCKWISE_90 ->   { xMin = pos.x();         xMax = pos.x() + d;     zMin = pos.z() - w + 1; zMax = pos.z() + 1; }
                default ->                    { xMin = pos.x();         xMax = pos.x() + w;     zMin = pos.z();      zMax = pos.z() + d; }
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
            Map<String, List<ItemStack>> trialEggsByResourceId = new HashMap<>();
            Map<String, List<ItemStack>> trialLootByResourceId = new HashMap<>();
            for (ManagedMarker m : mc.markers().findMarkersInWorld(refWorldName, "resourceinstance", null)) {
                String val = m.getName();
                if (val == null) continue;
                String resourceId = val.replaceAll("-\\d+$", "");
                String trialResourceId = resourceId;
                int sourceSeparator = resourceId.indexOf(':');
                String sourceKind = null;
                if (sourceSeparator >= 0) {
                    trialResourceId = resourceId.substring(0, sourceSeparator);
                    sourceKind = resourceId.substring(sourceSeparator + 1);
                }
                String type = structureTypeConfig.getResourceType(typeName, resourceId);
                BlockPos bp = m.getPosition();

                if ("trial-spawner".equals(structureTypeConfig.getResourceType(typeName, trialResourceId))) {
                    if ("eggs".equals(sourceKind)) {
                        readTrialSourceVariants(refWorldName, refWorld, bp, trialResourceId, trialEggsByResourceId);
                    } else if ("loot".equals(sourceKind)) {
                        readTrialSourceVariants(refWorldName, refWorld, bp, trialResourceId, trialLootByResourceId);
                    }
                } else if ("container".equals(type)) {
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
            configureTrialSpawners(typeName, worldName, trialEggsByResourceId);
            configureTrialVaults(typeName, worldName, trialLootByResourceId);
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

    private void readTrialSourceVariants(String refWorldName, World refWorld, BlockPos bp, String resourceId,
                                         Map<String, List<ItemStack>> variantsByResourceId) {
        Block block = refWorld.getBlockAt(bp.x(), bp.y(), bp.z());
        if (!(block.getState() instanceof Container container)) return;
        List<ItemStack> list = variantsByResourceId.computeIfAbsent(resourceId, k -> new ArrayList<>());
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) list.add(item.clone());
        }
    }

    private void configureTrialSpawners(String typeName, String worldName,
                                        Map<String, List<ItemStack>> eggsByResourceId) {
        if (eggsByResourceId.isEmpty()) return;
        for (MarkerEntity marker : mc.markers().getMarkersInWorld(worldName)) {
            String name = marker.getName();
            if (name == null || !name.startsWith("trial-spawner-")) continue;
            String resourceId = name.substring("trial-spawner-".length());
            if (!"trial-spawner".equals(structureTypeConfig.getResourceType(typeName, resourceId))) continue;
            List<ItemStack> eggs = eggsByResourceId.get(resourceId);
            if (eggs != null) {
                mc.trialSpawners().configureSpawner(worldName, marker.getPosition(), eggs);
            }
        }
    }

    private void configureTrialVaults(String typeName, String worldName,
                                      Map<String, List<ItemStack>> lootByResourceId) {
        if (lootByResourceId.isEmpty()) return;
        for (MarkerEntity marker : mc.markers().getMarkersInWorld(worldName)) {
            String name = marker.getName();
            if (name == null || !name.startsWith("trial-vault-")) continue;
            String resourceId = name.substring("trial-vault-".length());
            if (!"trial-spawner".equals(structureTypeConfig.getResourceType(typeName, resourceId))) continue;
            List<ItemStack> loot = lootByResourceId.get(resourceId);
            if (loot != null) {
                mc.trialSpawners().configureVault(worldName, marker.getPosition(), loot);
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
        BlockState destinationState = cb.tileState.copy(block.getLocation());
        destinationState.setBlockData(cb.blockData);
        destinationState.update(true, false);
    }

    /** Place a container-mode resource: convert ItemStack to a block and restore its block-state data. */
    private void placeResourceFromItem(World world, BlockPos pos, ItemStack item) {
        Material mat = item.getType();
        if (!mat.isBlock() || mat.isAir()) {
            plugin.getLogger().warning("[placeResources] Item " + mat + " is not a block, skipping");
            return;
        }
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        block.setType(mat, false);

        if (item.getItemMeta() instanceof BlockStateMeta bsm && bsm.hasBlockState()) {
            BlockState itemState = bsm.getBlockState();
            BlockState destinationState = itemState.copy(block.getLocation());
            destinationState.update(true, false);
        }
    }

    private void placeSpawnMarkers(StructureData placed, String worldName, BlockPos pos, String team) {
        // Use the structure dimensions to constrain the search to just this structure,
        // centered on the placement corner + a small margin. This prevents cross-team
        // marker interference when spawn structures are close together.
        int w = placed.getWidth();
        int h = placed.getHeight();
        int d = placed.getDepth();
        int margin = Math.max(Math.max(w, h), d);
        int minX = pos.x() - margin, maxX = pos.x() + margin;
        int minY = pos.y() - margin, maxY = pos.y() + margin;
        int minZ = pos.z() - margin, maxZ = pos.z() + margin;
        var logger = java.util.logging.Logger.getLogger("C2W");
        logger.info("[placeSpawnMarkers] team=" + team + " pos=" + pos + " dims=" + w + "x" + h + "x" + d
                + " margin=" + margin + " boundingBox=[("
                + minX + "," + minY + "," + minZ + ")-("
                + maxX + "," + maxY + "," + maxZ + ")]");

        var worldMarkers = mc.markers().getMarkersInWorld(worldName);
        logger.info("[placeSpawnMarkers] found " + worldMarkers.size() + " markers in world " + worldName);
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
                // Capture the first spawnpoint marker per team for player routing at start.
                teamSpawnPoints.putIfAbsent(team, markerPos);
                logger.info("[placeSpawnMarkers] captured " + team + " -> " + markerPos
                        + " (teamSpawnPoints now has " + teamSpawnPoints.size() + " entries)");
            } else {
                logger.info("[placeSpawnMarkers] marker at " + markerPos + " has name=" + name + " (not 'spawnpoint'), ignoring");
            }
        }
    }

    /**
     * Return the captured spawnpoint position for a team (e.g. "Red", "Blue",
     * "Spectator"), or null if none was found in the layout's SPAWN structures.
     */
    public @Nullable BlockPos getSpawnPointForTeam(String teamName) {
        return teamSpawnPoints.get(teamName);
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
        if (gsm.isGameEnded()) {
            var winningTeam = event.getPlayer().getTeam();
            end();
            if (winningTeam != null) {
                mc.server().broadcastMessage("§6§l" + winningTeam.teamName
                        + " team wins the game! §eThey captured 2 wools! 🏆");
            }
        }
    }

    public int getWoolCount(String teamName) {
        return gsm.getWoolCount(teamName);
    }

    /**
     * The layout resolved for the most recent {@link #start}. Null before a game
     * starts. Consumed by {@code ScoreboardManager} to render the layout mock.
     */
    public @Nullable MapLayout getActiveLayout() {
        return activeLayout;
    }

    public void reset() {
        eventManager.unregisterInternalEvent(WoolCapturedEvent.class, this::woolCapped);
        gsm.reset();
        this.selectedLayoutName = null;
        this.activeLayout = null;
        this.teamSpawnPoints.clear();
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
