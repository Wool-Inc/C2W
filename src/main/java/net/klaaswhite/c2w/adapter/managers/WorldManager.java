package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.Mirror;
import net.klaaswhite.c2w.domain.model.StructureRotation;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class WorldManager implements AutoCloseable {

    private static final Logger log = Logger.getLogger(WorldManager.class.getName());
    private static final String LOBBY_STRUCTURE = "lobby";
    private static final String DRAFT_STRUCTURE = "draft";
    private static final BlockPos SPECIAL_STRUCTURE_ORIGIN = new BlockPos(-54, 63, -54);
    private static final String SPAWNPOINT_MARKER = "spawnpoint";
    private static final String[] DRAFT_SELECTION_TEAMS = {"Red", "Blue", "Spectator"};

    private final JavaPlugin plugin;
    private final MinecraftManager mc;
    private final ManagedWorld lobbyWorld;
    private final ManagedWorld referenceWorld;

    private final ManagedWorld draftWorld;
    private final ManagedWorld gameWorld;
    private final Map<String, SelectionRegion> draftSelectionRegions = new HashMap<>();

    // ponytail: prefixes matching transient C2W worlds left over from a crash/restart.
    // NOTE: c2w_create_* creation worlds are cleaned up here on startup rather than
    // restored — stale worlds from a crash are deleted to avoid loading stale data.
    // (StructureCreationManager.restoreSessions() is retained as a fallback for
    // worlds in per-world dimension dirs that cleanupLeftoverTransientWorlds misses.)
    private static final String[] TRANSIENT_PREFIXES = {
        "c2w_draft", "c2w_game", "c2w_resource_", "c2w_layout_", "c2w_create_"
    };

    public WorldManager(JavaPlugin plugin, MinecraftManager mc) {
        this(plugin, mc, true);
    }

    public WorldManager(JavaPlugin plugin, MinecraftManager mc, boolean applyLobbyStructure) {
        this.plugin = plugin;
        this.mc = mc;
        var worlds = mc.worlds();

        // Persistent worlds: created on first load, never deleted by the plugin.
        // The lobby gets a flat 9x9 bedrock platform so more players fit waiting there.
        this.lobbyWorld = new ManagedWorld("c2w_lobby", false, worlds, false, false, 0);
        this.referenceWorld = new ManagedWorld("c2w_reference", false, worlds, true, false, 0);

        // Transient worlds: created on demand, deleted by destroyDraftAndGameWorlds.
        // The draft world places no center cross — its spawn layout (including the
        // team selection platforms and walkways) is built entirely in createTeamSelectionAreas.
        this.draftWorld = new ManagedWorld("c2w_draft", true, worlds, false, false, 0);
        // Game world: no center cross — spawn points come from layout SPAWN structures.
        this.gameWorld = new ManagedWorld("c2w_game", true, worlds, false, false, 0);

        // Nuke leftover transient worlds from a previous crash/restart.
        cleanupLeftoverTransientWorlds();

        if (applyLobbyStructure) {
            var lobby = this.lobbyWorld.loadOrCreate();
            if (lobby != null && !loadSpecialStructure(lobby, LOBBY_STRUCTURE)) {
                createLobbyPlatform(lobby);
            }
        } else if (this.lobbyWorld.getWorld() == null) {
            var lobby = this.lobbyWorld.loadOrCreate();
            if (lobby != null) createLobbyPlatform(lobby);
        }
        this.referenceWorld.loadOrCreate();
    }

    public ManagedWorld getLobbyWorld() {
        return lobbyWorld;
    }

    public ManagedWorld getReferenceWorld() {
        return referenceWorld;
    }

    public ManagedWorld getDraftWorld() {
        return draftWorld;
    }

    public ManagedWorld getGameWorld() {
        return gameWorld;
    }

    public World createDraftWorld() {
        var w = draftWorld.loadOrCreate();
        if (w == null) return null;
        draftSelectionRegions.clear();
        if (!loadSpecialStructure(w, DRAFT_STRUCTURE)) {
            createTeamSelectionAreas(w);
        }
        return w;
    }

    /** Resolve a draft selection region using the active structure, or its code fallback. */
    public @org.jspecify.annotations.Nullable String resolveTeamSelectionAt(
            String worldName, int x, int feetY, int z) {
        if ("c2w_draft".equals(worldName) && !draftSelectionRegions.isEmpty()) {
            for (var entry : draftSelectionRegions.entrySet()) {
                if (entry.getValue().contains(x, feetY, z)) return entry.getKey();
            }
            return null;
        }
        return resolveTeamSelectionAt(x, feetY, z);
    }

    public boolean hasMarkerDefinedDraftSelection() {
        return !draftSelectionRegions.isEmpty();
    }

    private boolean loadSpecialStructure(World world, String structureName) {
        File dataFolder = plugin.getDataFolder();
        if (dataFolder == null) return false;
        File structureFile = specialStructureFile(structureName);
        if (!structureFile.isFile()) return false;

        String worldName = world.getName();
        log.info(() -> "Loading " + structureName + " structure from " + structureFile.getPath()
                + " into " + worldName + " at " + SPECIAL_STRUCTURE_ORIGIN);
        boolean persistentLobby = LOBBY_STRUCTURE.equals(structureName);
        Set<UUID> previousMarkers = persistentLobby
            ? snapshotStructureMarkers(worldName) : Set.of();
        if (persistentLobby) {
            log.info(() -> "Removing " + previousMarkers.size()
                    + " existing C2W marker(s) before reloading the lobby structure");
        }
        removeStructureMarkers(worldName, previousMarkers);
        if (persistentLobby) removeLegacyLobbyPlatform(worldName);
        try {
            String structureId = mc.structures().loadStructure(structureFile);
                log.info(() -> "Loaded " + structureName + " structure as " + structureId
                        + "; placing with entities included");
                mc.structures().place(structureId, worldName, SPECIAL_STRUCTURE_ORIGIN, true,
                    StructureRotation.NONE, Mirror.NONE, -1, 1.0f, new Random());
        } catch (IOException | RuntimeException e) {
            log.warning("Failed to load " + structureFile.getPath() + ": " + e.getMessage());
            return false;
        }

        removeDuplicateStructureMarkers(worldName, previousMarkers);
        logStructureMarkers(worldName, structureName, previousMarkers);
        BlockPos spawnpoint = findSingleMarker(worldName, SPAWNPOINT_MARKER, structureName, previousMarkers);
        if (spawnpoint == null) return false;
        if (DRAFT_STRUCTURE.equals(structureName)) {
            var regions = readDraftSelectionRegions(worldName);
            if (regions == null) return false;
            mc.worlds().setSpawnPos(worldName, spawnpoint);
            logSpawnpoint(worldName, structureName, spawnpoint);
            draftSelectionRegions.putAll(regions);
        } else {
            mc.worlds().setSpawnPos(worldName, spawnpoint);
            logSpawnpoint(worldName, structureName, spawnpoint);
        }
        return true;
    }

    private void logStructureMarkers(String worldName, String structureName, Set<UUID> excludedMarkers) {
        String markerKey = mc.markers().getMarkerKey();
        var markers = mc.markers().getMarkersInWorld(worldName).stream()
                .filter(marker -> !excludedMarkers.contains(marker.getUniqueId()))
                .map(marker -> "'" + marker.getPersistentData(markerKey) + "' at " + marker.getPosition())
                .toList();
        log.info(() -> "Markers placed by " + structureName + " structure in " + worldName
                + ": " + (markers.isEmpty() ? "none" : String.join(", ", markers)));
    }

    private void logSpawnpoint(String worldName, String structureName, BlockPos spawnpoint) {
        BlockPos actualSpawn = mc.worlds().getSpawnPos(worldName);
        log.info(() -> "Set " + worldName + " spawn from " + structureName + " spawnpoint marker at "
                + spawnpoint + "; world now reports spawn " + actualSpawn);
    }

    private File specialStructureFile(String structureName) {
        File canonical = new File(plugin.getDataFolder(),
                "structures/general/instances/" + structureName + ".nbt");
        if (canonical.isFile()) return canonical;
        return new File(plugin.getDataFolder(), "structures/" + structureName + ".nbt");
    }

    private Set<UUID> snapshotStructureMarkers(String worldName) {
        String markerKey = mc.markers().getMarkerKey();
        Set<UUID> markers = new HashSet<>();
        for (var marker : mc.markers().getMarkersInWorld(worldName)) {
            if (marker.getPersistentData(markerKey) != null) {
                markers.add(marker.getUniqueId());
            }
        }
        return markers;
    }

    private void removeStructureMarkers(String worldName, Set<UUID> markers) {
        String markerKey = mc.markers().getMarkerKey();
        for (var marker : mc.markers().getMarkersInWorld(worldName)) {
            if (marker.getPersistentData(markerKey) != null && markers.contains(marker.getUniqueId())) {
                marker.remove();
            }
        }
    }

    private void removeLegacyLobbyPlatform(String worldName) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (mc.blocks().getBlockType(worldName, new BlockPos(x, 64, z)) == Material.BEDROCK) {
                    mc.blocks().setBlock(worldName, new BlockPos(x, 64, z), Material.AIR);
                }
            }
        }
    }

    private void removeDuplicateStructureMarkers(String worldName, Set<UUID> previousMarkers) {
        String markerKey = mc.markers().getMarkerKey();
        Map<String, Set<BlockPos>> seen = new HashMap<>();
        net.klaaswhite.c2w.adapter.minecraft.MarkerEntity lastSpawnpoint = null;
        for (var marker : mc.markers().getMarkersInWorld(worldName)) {
            if (previousMarkers.contains(marker.getUniqueId())) continue;
            String name = marker.getPersistentData(markerKey);
            if (name == null) continue;
            if (SPAWNPOINT_MARKER.equals(name)) {
                if (lastSpawnpoint != null) lastSpawnpoint.remove();
                lastSpawnpoint = marker;
                continue;
            }
            var positions = seen.computeIfAbsent(name, ignored -> new HashSet<>());
            if (!positions.add(marker.getPosition())) {
                marker.remove();
            }
        }
    }

    private Map<String, SelectionRegion> readDraftSelectionRegions(String worldName) {
        var regions = new HashMap<String, SelectionRegion>();
        for (var team : DRAFT_SELECTION_TEAMS) {
            String prefix = "draft-" + team.toLowerCase();
            BlockPos first = findSingleMarker(worldName, prefix + "-1", DRAFT_STRUCTURE);
            BlockPos second = findSingleMarker(worldName, prefix + "-2", DRAFT_STRUCTURE);
            if (first == null || second == null) return null;
            regions.put(team, SelectionRegion.from(first, second));
        }
        return regions;
    }

    private BlockPos findSingleMarker(String worldName, String markerName, String structureName) {
        return findSingleMarker(worldName, markerName, structureName, Set.of());
    }

    private BlockPos findSingleMarker(String worldName, String markerName, String structureName,
                                      Set<UUID> excludedMarkers) {
        var positions = findMarkers(worldName, markerName, excludedMarkers);
        if (positions.size() != 1) {
            log.warning("Structure " + structureName + " in world " + worldName
                    + " requires exactly one '" + markerName + "' marker, found " + positions.size());
            return null;
        }
        return positions.get(0);
    }

    private List<BlockPos> findMarkers(String worldName, String markerName) {
        return findMarkers(worldName, markerName, Set.of());
    }

    private List<BlockPos> findMarkers(String worldName, String markerName,
                           Set<UUID> excludedMarkers) {
        String markerKey = mc.markers().getMarkerKey();
        return mc.markers().getMarkersInWorld(worldName).stream()
            .filter(marker -> !excludedMarkers.contains(marker.getUniqueId()))
                .filter(marker -> markerName.equals(marker.getPersistentData(markerKey)))
                .map(net.klaaswhite.c2w.adapter.minecraft.MarkerEntity::getPosition)
            .distinct()
                .toList();
    }

    private void createLobbyPlatform(World world) {
        fillRect(world.getName(), Material.BEDROCK, PLATFORM_SURFACE_Y,
            -4, 4, -4, 4);
        mc.worlds().setSpawnPos(world.getName(), new BlockPos(0, 65, 0));
    }

    // --------------------------------------------------------------------
    // Draft world — team selection area
    // --------------------------------------------------------------------
    // Base level: the bedrock support blocks sit at y = PLATFORM_BASE_Y and the
    // coloured standing surface on top at PLATFORM_SURFACE_Y, so a player's feet
    // end up at PLATFORM_SURFACE_Y + 1 (= 65). The spectator platform is raised
    // (feet at SPECTATOR_SURFACE_Y + 1 = 71) and is reached via a staircase.
    private static final int PLATFORM_BASE_Y = 63;
    private static final int PLATFORM_SURFACE_Y = 64;
    private static final int SPECTATOR_BASE_Y = 69;
    private static final int SPECTATOR_SURFACE_Y = 70;

    /**
     * Place bedrock team selection areas in the draft world. Everything is
     * connected so every platform is reachable by walking:
     * - Spawn platform: 3x3 at the origin (0, 64, 0)
     * - Red team platform: (-10, 64, 0) — 3x3 RED_WOOL via a red walkway from spawn
     * - Blue team platform: (10, 64, 0) — 3x3 BLUE_WOOL via a blue walkway from spawn
     * - Spectator platform: (0, 70, 10) — raised glass platform reachable by stairs
     */
    private void createTeamSelectionAreas(World world) {
        if (mc == null) return;
        String worldName = world.getName();
        mc.worlds().setSpawnPos(worldName, new BlockPos(0, 65, 0));

        // Spawn platform (3x3) at the origin.
        fillPlatform(worldName, Material.BEDROCK, Material.SMOOTH_STONE,
                -1, 1, -1, 1, PLATFORM_BASE_Y);

        // Red side: walkway then platform.
        fillPlatform(worldName, Material.BEDROCK, Material.RED_CONCRETE, -8, -2, -1, 1, PLATFORM_BASE_Y);
        fillPlatform(worldName, Material.BEDROCK, Material.RED_WOOL, -11, -9, -1, 1, PLATFORM_BASE_Y);

        // Blue side: walkway then platform.
        fillPlatform(worldName, Material.BEDROCK, Material.BLUE_CONCRETE, 2, 8, -1, 1, PLATFORM_BASE_Y);
        fillPlatform(worldName, Material.BEDROCK, Material.BLUE_WOOL, 9, 11, -1, 1, PLATFORM_BASE_Y);

        // Spectator platform: elevated glass pad at the north end.
        fillPlatform(worldName, Material.BEDROCK, Material.GLASS, -1, 1, 9, 11, SPECTATOR_BASE_Y);

        // 3-wide staircase from spawn level up to the spectator level (one block up per z-slice).
        for (int z = 2; z <= 8; z++) {
            int surfaceY = PLATFORM_SURFACE_Y + (z - 2); // 64..70, feet 65..71
            Material m = (z == 2) ? Material.SMOOTH_STONE : Material.STONE_BRICKS;
            fillRect(worldName, m, surfaceY, -1, 1, z, z);
        }
    }

    /**
     * Resolve which team's selection platform a player is standing on in the
     * draft world, or {@code null} when the position is not on a team platform.
     *
     * @param x     the player's feet block x
     * @param feetY the player's feet block y (the surface height the player stands on)
     * @param z     the player's feet block z
     */
    public static @org.jspecify.annotations.Nullable String resolveTeamSelectionAt(int x, int feetY, int z) {
        if (feetY == PLATFORM_SURFACE_Y + 1) {
            if (x >= -11 && x <= -9 && z >= -1 && z <= 1) return "Red";
            if (x >= 9 && x <= 11 && z >= -1 && z <= 1) return "Blue";
        }
        if (feetY == SPECTATOR_SURFACE_Y + 1) {
            if (x >= -1 && x <= 1 && z >= 9 && z <= 11) return "Spectator";
        }
        return null;
    }

    private record SelectionRegion(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private static SelectionRegion from(BlockPos first, BlockPos second) {
            return new SelectionRegion(
                    Math.min(first.x(), second.x()), Math.max(first.x(), second.x()),
                    Math.min(first.y(), second.y()), Math.max(first.y(), second.y()),
                    Math.min(first.z(), second.z()), Math.max(first.z(), second.z()));
        }

        private boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    /** Fill a rectangular base+surface platform (surface one block above the base). */
    private void fillPlatform(String worldName, Material base, Material surface,
            int minX, int maxX, int minZ, int maxZ, int baseY) {
        fillRect(worldName, base, baseY, minX, maxX, minZ, maxZ);
        fillRect(worldName, surface, baseY + 1, minX, maxX, minZ, maxZ);
    }

    private void fillRect(String worldName, Material material, int y,
            int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                mc.blocks().setBlock(worldName, new BlockPos(x, y, z), material);
            }
        }
    }

    public World createGameWorld() {
        var w = gameWorld.loadOrCreate();
        if (w == null) return null;
        return w;
    }

    public boolean isGameWorldCreated() {
        return gameWorld.isLoaded();
    }

    public boolean isDraftWorldCreated() {
        return draftWorld.isLoaded();
    }

    /**
     * Unload and delete the draft and game worlds. Idempotent - safe to
     * call when they don't exist.
     */
    public void destroyDraftAndGameWorlds() {
        draftWorld.delete();
        gameWorld.delete();
        draftSelectionRegions.clear();
    }

    /**
     * Scan for leftover transient C2W worlds and nuke them before startup.
     * Handles crashes/restarts where {@link #close()} never ran.
     * <p>
     * Traverses two locations:
     * <ul>
     *   <li>Legacy: {@code <world-container>/<name>/} (pre-1.21)</li>
     *   <li>Paper 1.21+: {@code <world-container>/<main-world>/dimensions/minecraft/<name>/}</li>
     * </ul>
     */
    private void cleanupLeftoverTransientWorlds() {
        var worlds = mc.worlds();

        // 1. Unload any loaded transient worlds so folders aren't locked.
        for (var world : worlds.getLoadedWorlds()) {
            if (isTransientWorld(world.getName())) {
                worlds.unloadWorld(world);
            }
        }

        var container = worlds.getWorldContainer();

        // 2. Legacy path: <world-container>/<name>/
        deleteMatchingDirs(container);

        // 3. Paper 1.21+ path: <world-container>/<main-world>/dimensions/minecraft/<name>/
        //    Every world folder can host custom dimensions, so scan them all.
        var topDirs = container.listFiles(File::isDirectory);
        if (topDirs != null) {
            for (var topDir : topDirs) {
                var dimsMinecraft = new File(topDir, "dimensions" + File.separator + "minecraft");
                deleteMatchingDirs(dimsMinecraft);
            }
        }
    }

    private static void deleteMatchingDirs(File parent) {
        var dirs = parent.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (var dir : dirs) {
            if (isTransientWorld(dir.getName())) {
                // ponytail: best-effort; leftover files from a crash are already stale.
                deleteDirectory(dir);
            }
        }
    }

    private static boolean isTransientWorld(String worldName) {
        for (var prefix : TRANSIENT_PREFIXES) {
            if (worldName.startsWith(prefix)) return true;
        }
        return false;
    }

    private static void deleteDirectory(File dir) {
        var files = dir.listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    @Override
    public void close() {
        destroyDraftAndGameWorlds();
    }
}
