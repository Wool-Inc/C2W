package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.adapter.minecraft.MarkerEntity;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import net.klaaswhite.c2w.domain.ops.FileSystemOps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages structure creation worlds — transient void worlds where players
 * build structure instances and save them as NBT files.
 */
public class StructureCreationManager implements AutoCloseable {

    private final JavaPlugin plugin;
    private final EventManager eventManager;
    private final WorldManager worldManager;
    private final FolderStructureTypeConfig structureTypeConfig;
    private final MinecraftManager mc;
    private final File dataFolder;
    private final FileSystemOps fs;

    private final Map<String, CreationSession> sessions = new HashMap<>();
    private final Map<String, BukkitTask> particleTasks = new HashMap<>();
    // World name -> list of armor stand UUIDs for visualization
    private final Map<String, List<UUID>> visualizationArmorStands = new HashMap<>();
    private static final Logger log = Logger.getLogger(StructureCreationManager.class.getName());

    public StructureCreationManager(
            JavaPlugin plugin,
            EventManager eventManager,
            WorldManager worldManager,
            FolderStructureTypeConfig structureTypeConfig,
            MinecraftManager mc,
            File dataFolder,
            FileSystemOps fs
    ) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.worldManager = worldManager;
        this.structureTypeConfig = structureTypeConfig;
        this.mc = mc;
        this.dataFolder = dataFolder;
        this.fs = fs;

        this.eventManager.registerMinecraftEvent(PlayerTeleportEvent.class, this::onPlayerTeleport);
        restoreSessions();
    }

    public StructureCreationManager(
            JavaPlugin plugin,
            EventManager eventManager,
            WorldManager worldManager,
            FolderStructureTypeConfig structureTypeConfig,
            MinecraftManager mc,
            File dataFolder
    ) {
        this(plugin, eventManager, worldManager, structureTypeConfig, mc, dataFolder, new FileSystemOps() {
            @Override public File[] listFiles(File dir) { return dir != null && dir.isDirectory() ? dir.listFiles() : new File[0]; }
            @Override public File[] listFiles(File dir, String suffix) { return dir != null && dir.isDirectory() ? dir.listFiles((d, name) -> name.endsWith(suffix)) : new File[0]; }
            @Override public boolean isFile(File file) { return file != null && file.isFile(); }
            @Override public boolean isDirectory(File dir) { return dir != null && dir.isDirectory(); }
            @Override public java.io.InputStream newInputStream(File file) throws java.io.IOException { return new java.io.FileInputStream(file); }
            @Override public boolean createFile(File file) throws java.io.IOException { if (file == null) throw new java.io.IOException("null"); var p = file.getParentFile(); if (p != null) p.mkdirs(); return file.createNewFile(); }
            @Override public boolean delete(File file) { return file != null && file.delete(); }
        });
    }

    /** Create a void world for building a structure instance. */
    public World createCreationWorld(Player player, String typeName, String id) {
        String worldName = creationWorldName(typeName, id);

        if (sessions.containsKey(worldName)) {
            player.sendMessage("Creation world for " + typeName + "/" + id + " already exists.");
            return null;
        }
        if (isStructureLocked(typeName, id)) {
            player.sendMessage("Structure " + typeName + "/" + id + " is locked by another player.");
            return null;
        }

        // ponytail: clean up orphan world folders from crashes
        if (mc.worlds().getWorld(worldName) != null) {
            mc.worlds().unloadWorld(worldName);
            mc.worlds().deleteWorld(worldName);
        }

        int[] dims = structureTypeConfig.getDimensions(typeName);
        if (dims == null) {
            player.sendMessage("Unknown structure type: " + typeName + ". Define it first with /structure define.");
            return null;
        }

        if (mc.worlds().createVoidWorld(worldName, World.Environment.NORMAL) == null) {
            player.sendMessage("Failed to create creation world.");
            return null;
        }

        int cx = dims[0] / 2, cy = dims[1] / 2, cz = dims[2] / 2;

        sessions.put(worldName, new CreationSession(worldName, typeName, id, player.getUniqueId(), 0f));
        startParticleBoundary(worldName, dims[0], dims[1], dims[2], cx, cy, cz);

        mc.players().teleportToWorld(player.getName(), new BlockPos(cx, cy, cz), worldName);
        lockStructure(typeName, id, player);
        return getWorld(worldName);
    }

    /** Load an existing structure NBT into a fresh creation world for editing. */
    public World modifyCreationWorld(Player player, String typeName, String id) {
        String worldName = creationWorldName(typeName, id);

        if (sessions.containsKey(worldName)) {
            player.sendMessage("Creation world for " + typeName + "/" + id + " already exists.");
            return null;
        }
        if (isStructureLocked(typeName, id)) {
            player.sendMessage("Structure " + typeName + "/" + id + " is locked by another player.");
            return null;
        }

        // ponytail: clean up orphan world folders from crashes
        if (mc.worlds().getWorld(worldName) != null) {
            mc.worlds().unloadWorld(worldName);
            mc.worlds().deleteWorld(worldName);
        }

        File nbtFile = new File(dataFolder, "structures/" + typeName + "/instances/" + id + ".nbt");
        if (!nbtFile.exists()) {
            player.sendMessage("No saved structure found for " + typeName + "/" + id + ".");
            return null;
        }

        if (mc.worlds().createVoidWorld(worldName, World.Environment.NORMAL) == null) {
            player.sendMessage("Failed to create creation world.");
            return null;
        }

        String structId;
        try {
            structId = mc.structures().loadStructure(nbtFile);
            mc.structures().place(structId, worldName, new BlockPos(0, 0, 0), true,
                    net.klaaswhite.c2w.domain.model.StructureRotation.NONE,
                    net.klaaswhite.c2w.domain.model.Mirror.NONE, 0, 1.0f, new java.util.Random());
        } catch (IOException e) {
            player.sendMessage("Failed to load structure NBT: " + e.getMessage());
            return null;
        }

        int[] dims = structureTypeConfig.getDimensions(typeName);
        if (dims == null) {
            player.sendMessage("Unknown structure type: " + typeName);
            return null;
        }

        int[] nbtSize = mc.structures().getSize(structId);
        if (nbtSize != null && (nbtSize[0] != dims[0] || nbtSize[1] != dims[1] || nbtSize[2] != dims[2])) {
            player.sendMessage("§eWarning: This instance is " + nbtSize[0] + "x" + nbtSize[1] + "x" + nbtSize[2] +
                    " but the type is now " + dims[0] + "x" + dims[1] + "x" + dims[2] +
                    ". Adjust and re-save to update.");
        }

        int cx = dims[0] / 2, cy = dims[1] / 2, cz = dims[2] / 2;

        sessions.put(worldName, new CreationSession(worldName, typeName, id, player.getUniqueId(), 0f));
        startParticleBoundary(worldName, dims[0], dims[1], dims[2], cx, cy, cz);

        mc.players().teleportToWorld(player.getName(), new BlockPos(cx, cy, cz), worldName);
        lockStructure(typeName, id, player);
        return getWorld(worldName);
    }

    /** Save the creation world to NBT and clean up. */
    public boolean saveCreationWorld(Player player, String typeName, String id) {
        String worldName = creationWorldName(typeName, id);
        if (!sessions.containsKey(worldName)) {
            player.sendMessage("No active creation session for " + typeName + "/" + id + ".");
            return false;
        }

        if (mc.worlds().getWorld(worldName) == null) {
            player.sendMessage("Creation world is not loaded.");
            sessions.remove(worldName);
            return false;
        }

        int[] dims = structureTypeConfig.getDimensions(typeName);
        if (dims == null) {
            player.sendMessage("Unknown structure type: " + typeName);
            return false;
        }

        // Validate resource spot markers against minSpots requirements
        var requirements = structureTypeConfig.getResourceRequirements(typeName);
        if (!requirements.isEmpty()) {
            var placed = getResourceSpotsGrouped(worldName);
            var shortfalls = new java.util.ArrayList<String>();
            for (var entry : requirements.entrySet()) {
                String resId = entry.getKey();
                int required = entry.getValue();
                int count = placed.containsKey(resId) ? placed.get(resId).size() : 0;
                if (count < required) {
                    shortfalls.add(resId + ": " + count + "/" + required);
                }
            }
            if (!shortfalls.isEmpty()) {
                player.sendMessage("Cannot save — resource spot requirements not met:");
                for (String s : shortfalls) {
                    player.sendMessage("  " + s);
                }
                return false;
            }
        }

        // Remove any visualization armor stands so they are not captured in the NBT.
        // Use the thorough sweep so stands loaded from a previous save (which are
        // not in the tracking map) are also removed. Entity.remove() is deferred to
        // the end of the tick in modern Spigot/Paper, so ejectVisualizationArmorStands
        // first teleports the stands outside the capture box before removing them.
        World world = mc.worlds().getWorld(worldName);
        if (world != null) {
            ejectVisualizationArmorStands(world);
        }

        try {
            exportWorldToNbt(worldName, typeName, id, dims[0], dims[1], dims[2]);
        } catch (Exception e) {
            player.sendMessage("Failed to export structure: " + e.getMessage());
            return false;
        }

        cleanupSession(worldName);
        return true;
    }

    /** Discard the creation world without saving. */
    public boolean discardCreationWorld(Player player, String typeName, String id) {
        String worldName = creationWorldName(typeName, id);
        if (!sessions.containsKey(worldName)) {
            player.sendMessage("No active creation session for " + typeName + "/" + id + ".");
            return false;
        }
        cleanupSession(worldName);
        return true;
    }

    /** Delete a structure's NBT file and lock. */
    public boolean deleteCreationWorld(Player player, String typeName, String id) {
        if (isStructureLocked(typeName, id)) {
            player.sendMessage("Structure " + typeName + "/" + id + " is currently being edited.");
            return false;
        }
        File nbtFile = new File(dataFolder, "structures/" + typeName + "/instances/" + id + ".nbt");
        if (nbtFile.exists()) nbtFile.delete();
        unlockStructure(typeName, id);
        return true;
    }

    /** Destroy all active creation worlds. Called on game start. */
    public void destroyAllCreationWorlds() {
        for (String worldName : new ArrayList<>(sessions.keySet())) {
            destroyCreationWorld(worldName);
        }
    }

    public boolean isStructureLocked(String typeName, String id) {
        return fs.isFile(lockFile(typeName, id));
    }

    /** Count resource spot marker entities in a world matching the given resourceId prefix. */
    public int countResourceSpots(String worldName, String resourceId) {
        return mc.markers().findMarkersInWorld(worldName, "resourcespot", resourceId + "-").size();
    }

    /** Get all resource spot markers grouped by their resource ID. */
    public Map<String, List<ManagedMarker>> getResourceSpotsGrouped(String worldName) {
        Map<String, List<ManagedMarker>> grouped = new HashMap<>();
        var markers = mc.markers().findMarkersInWorld(worldName, "resourcespot", null);
        for (var mm : markers) {
            String name = mm.getName(); // PDC value, e.g. "chest-0"
            if (name == null) continue;
            int lastDash = name.lastIndexOf('-');
            if (lastDash < 0) continue;
            String resId = name.substring(0, lastDash);
            grouped.computeIfAbsent(resId, k -> new ArrayList<>()).add(mm);
        }
        return grouped;
    }

    /** Remove a resource spot marker at the given position (double coords from block center + offset). */
    public boolean removeResourceSpotAt(String worldName, double x, double y, double z) {
        var markers = mc.markers().findMarkersInWorld(worldName, "resourcespot", null);
        BlockPos targetPos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        for (var mm : markers) {
            if (mm.getPosition().x() == targetPos.x()
                    && mm.getPosition().y() == targetPos.y()
                    && mm.getPosition().z() == targetPos.z()) {
                mm.remove();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Game markers (wools, spawnpoints, capture areas)
    // ------------------------------------------------------------------
    // These are distinct from resource spots: they mark locations the game
    // itself uses (wool spawns, team spawn points, capture boundaries). They
    // are stored under the marker system's `map_marker` PDC key, exactly like
    // markers placed in the live game world via /marker create.

    /** Place a game marker at the targeted block (within 5 blocks). */
    public boolean placeGameMarker(Player player, String markerName) {
        String worldName = player.getWorld().getName();
        var block = player.getTargetBlockExact(5);
        if (block == null) {
            player.sendMessage("No block targeted. Look at a block within 5 blocks.");
            return false;
        }
        MarkerEntity markerEntity = mc.markers().spawnMarker(worldName,
                new BlockPos(block.getX(), block.getY(), block.getZ()));
        if (markerEntity == null) {
            player.sendMessage("Failed to spawn marker.");
            return false;
        }
        markerEntity.setPersistentData(mc.markers().getMarkerKey(), markerName);
        player.sendMessage("Placed game marker '" + markerName + "' at ("
                + block.getX() + ", " + block.getY() + ", " + block.getZ() + ").");
        return true;
    }

    /** Place a game marker at the player's standing position. */
    public boolean placeGameMarkerHere(Player player, String markerName) {
        String worldName = player.getWorld().getName();
        var pos = mc.players().getPosition(player.getName());
        if (pos == null) {
            player.sendMessage("Could not determine your position.");
            return false;
        }
        MarkerEntity markerEntity = mc.markers().spawnMarker(worldName, pos);
        if (markerEntity == null) {
            player.sendMessage("Failed to spawn marker.");
            return false;
        }
        markerEntity.setPersistentData(mc.markers().getMarkerKey(), markerName);
        player.sendMessage("Placed game marker '" + markerName + "' at ("
                + pos.x() + ", " + pos.y() + ", " + pos.z() + ").");
        return true;
    }

    /** List all game markers in a creation world, grouped by name. */
    public Map<String, List<MarkerEntity>> getGameMarkersGrouped(String worldName) {
        Map<String, List<MarkerEntity>> grouped = new HashMap<>();
        var markers = mc.markers().getMarkersInWorld(worldName);
        for (var mm : markers) {
            String name = mm.getName();
            if (name == null || name.isEmpty()) continue;
            grouped.computeIfAbsent(name, k -> new ArrayList<>()).add(mm);
        }
        return grouped;
    }

    /** Remove a game marker by name at the targeted block (within 5 blocks). */
    public boolean removeGameMarkerAt(Player player, String markerName) {
        String worldName = player.getWorld().getName();
        var block = player.getTargetBlockExact(5);
        if (block == null) {
            player.sendMessage("No block targeted. Look at a block within 5 blocks.");
            return false;
        }
        var markers = mc.markers().getMarkersInWorld(worldName);
        BlockPos targetPos = new BlockPos(block.getX(), block.getY(), block.getZ());
        for (var mm : markers) {
            if (markerName.equals(mm.getName())
                    && mm.getPosition().x() == targetPos.x()
                    && mm.getPosition().y() == targetPos.y()
                    && mm.getPosition().z() == targetPos.z()) {
                mm.remove();
                player.sendMessage("Removed game marker '" + markerName + "'.");
                return true;
            }
        }
        player.sendMessage("No game marker '" + markerName + "' found at your targeted block.");
        return false;
    }


    // ------------------------------------------------------------------
    // Event handlers
    // ------------------------------------------------------------------

    private void onPlayerTeleport(PlayerTeleportEvent event) {
        String fromName = mc.players().getWorldName(event.getPlayer().getName());
        if (fromName == null || !sessions.containsKey(fromName)) return;
        var toWorld = event.getTo().getWorld();
        if (toWorld != null && fromName.equals(toWorld.getName())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("You must save or discard the structure before leaving. "
                + "Use /structure save or /structure discard.");
    }

    // ------------------------------------------------------------------
    // Particle boundary
    // ------------------------------------------------------------------

    private void startParticleBoundary(String worldName, int w, int h, int d, int cx, int cy, int cz) {
        BukkitTask existing = particleTasks.remove(worldName);
        if (existing != null) existing.cancel();

        BoundingBox box = new BoundingBox(0, 0, 0, w, h, d);
        int halfW = w / 2;
        int halfD = d / 2;
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                World w = getWorld(worldName);
                if (w == null || w.getPlayers().isEmpty()) return;
                drawParticleEdges(w, box);
                // ponytail: arrow below floor so it doesn't interfere with building
                double sx = halfW + 0.5, sy = -2, sz = halfD + 0.5;
                double arrowLen = Math.max(halfW, halfD) + 1.0;
                var arrowOpts = new DustOptions(org.bukkit.Color.fromRGB(255, 170, 0), 1);
                for (double t = 0.5; t <= arrowLen; t += 0.25) {
                    w.spawnParticle(Particle.DUST, sx, sy, sz + t, 1, 0, 0, 0, arrowOpts);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        particleTasks.put(worldName, task);
    }

    private void drawParticleEdges(World world, BoundingBox box) {
        double spacing = 0.5;
        double x0 = box.getMinX(), y0 = box.getMinY(), z0 = box.getMinZ();
        double x1 = box.getMaxX(), y1 = box.getMaxY(), z1 = box.getMaxZ();
        drawLine(world, x0, y0, z0, x1, y0, z0, spacing);
        drawLine(world, x1, y0, z0, x1, y0, z1, spacing);
        drawLine(world, x1, y0, z1, x0, y0, z1, spacing);
        drawLine(world, x0, y0, z1, x0, y0, z0, spacing);
        drawLine(world, x0, y1, z0, x1, y1, z0, spacing);
        drawLine(world, x1, y1, z0, x1, y1, z1, spacing);
        drawLine(world, x1, y1, z1, x0, y1, z1, spacing);
        drawLine(world, x0, y1, z1, x0, y1, z0, spacing);
        drawLine(world, x0, y0, z0, x0, y1, z0, spacing);
        drawLine(world, x1, y0, z0, x1, y1, z0, spacing);
        drawLine(world, x1, y0, z1, x1, y1, z1, spacing);
        drawLine(world, x0, y0, z1, x0, y1, z1, spacing);
    }

    private void drawLine(World world, double x1, double y1, double z1,
                          double x2, double y2, double z2, double spacing) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) (len / spacing));
        var opts = new DustOptions(org.bukkit.Color.fromRGB(0, 200, 255), 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            world.spawnParticle(Particle.DUST, x1 + dx * t, y1 + dy * t, z1 + dz * t, 1, 0, 0, 0, opts);
        }
    }

    // ------------------------------------------------------------------
    // NBT export
    // ------------------------------------------------------------------

    private World getWorld(String worldName) {
        return mc.worlds().getWorld(worldName);
    }

    private void exportWorldToNbt(String worldName, String typeName, String id,
                                  int w, int h, int d) throws IOException {
        var structId = mc.structures().createStructure(worldName, new BlockPos(0, 0, 0), new BlockPos(w, h, d));
        if (structId == null) throw new IOException("Failed to capture structure from world: " + worldName);
        File instancesDir = new File(dataFolder, "structures/" + typeName + "/instances");
        instancesDir.mkdirs();
        mc.structures().saveStructure(new File(instancesDir, id + ".nbt"), structId);
    }

    // ------------------------------------------------------------------
    // Session cleanup
    // ------------------------------------------------------------------

    private void cleanupSession(String worldName) {
        cleanupVisualization(worldName);
        BukkitTask task = particleTasks.remove(worldName);
        cleanupParticleTask(task);

        CreationSession session = sessions.remove(worldName);

        var lobby = worldManager.getLobbyWorld();
        BlockPos lobbySpawn = lobby.getSpawnPos();
        String lobbyName = lobby.getName();
        for (String playerName : mc.server().getOnlinePlayerNames()) {
            String playerWorld = mc.players().getWorldName(playerName);
            if (worldName.equals(playerWorld)) {
                mc.players().teleportToWorld(playerName, lobbySpawn, lobbyName);
            }
        }
        mc.worlds().unloadWorld(worldName);
        mc.worlds().deleteWorld(worldName);
        if (session != null) unlockStructure(session.typeName, session.id);
    }

    private void destroyCreationWorld(String worldName) {
        cleanupVisualization(worldName);
        BukkitTask task = particleTasks.remove(worldName);
        cleanupParticleTask(task);

        CreationSession session = sessions.remove(worldName);

        var lobby = worldManager.getLobbyWorld();
        BlockPos lobbySpawn = lobby.getSpawnPos();
        String lobbyName = lobby.getName();
        for (String playerName : mc.server().getOnlinePlayerNames()) {
            String playerWorld = mc.players().getWorldName(playerName);
            if (worldName.equals(playerWorld)) {
                mc.players().teleportToWorld(playerName, lobbySpawn, lobbyName);
            }
        }
        mc.worlds().unloadWorld(worldName);
        mc.worlds().deleteWorld(worldName);
        if (session != null) unlockStructure(session.typeName, session.id);
    }

    private void restoreSessions() {
        File container = mc.worlds().getWorldContainer();
        if (container == null) return;

        // Collect all directories that could contain c2w_create_* worlds,
        // including per-world dimension subdirectories.
        java.util.List<File> searchDirs = new java.util.ArrayList<>();
        searchDirs.add(container);

        // Add per-world dimension/minecraft directories
        File[] topDirs = container.listFiles(File::isDirectory);
        if (topDirs != null) {
            for (File topDir : topDirs) {
                File dimsMinecraft = new File(topDir,
                        "dimensions" + File.separator + "minecraft");
                if (dimsMinecraft.isDirectory()) {
                    searchDirs.add(dimsMinecraft);
                }
            }
        }

        for (File searchDir : searchDirs) {
            File[] worlds = searchDir.listFiles();
            if (worlds == null) continue;
            for (File dir : worlds) {
                if (!dir.isDirectory() || !dir.getName().startsWith("c2w_create_")) continue;
                String suffix = dir.getName().substring("c2w_create_".length());
                int idx = suffix.indexOf('_');
                if (idx < 0) continue;
                String typeName = suffix.substring(0, idx);
                String id = suffix.substring(idx + 1);
                if (mc.worlds().createVoidWorld(dir.getName(), World.Environment.NORMAL) == null) continue;
                sessions.put(dir.getName(), new CreationSession(dir.getName(), typeName, id, null, 0f));
                int[] dims = structureTypeConfig.getDimensions(typeName);
                if (dims != null) {
                    int cx = dims[0] / 2, cy = dims[1] / 2, cz = dims[2] / 2;
                    startParticleBoundary(dir.getName(), dims[0], dims[1], dims[2], cx, cy, cz);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Locking
    // ------------------------------------------------------------------

    private File lockFile(String typeName, String id) {
        return new File(dataFolder, "structures/.lock_" + typeName + "_" + id);
    }

    private static String creationWorldName(String typeName, String id) {
        return "c2w_create_" + typeName + "_" + id;
    }

    private void lockStructure(String typeName, String id, Player player) {
        try {
            File f = lockFile(typeName, id);
            if (!fs.isFile(f)) {
                fs.createFile(f);
            }
        } catch (IOException e) {
            log.warning("Failed to lock: " + e.getMessage());
        }
    }

    private void unlockStructure(String typeName, String id) {
        File f = lockFile(typeName, id);
        if (fs.isFile(f)) {
            fs.delete(f);
        }
    }

    @Override
    public void close() {
        for (BukkitTask task : particleTasks.values()) cleanupParticleTask(task);
        particleTasks.clear();
        for (String worldName : new ArrayList<>(visualizationArmorStands.keySet())) {
            cleanupVisualization(worldName);
        }
        for (String worldName : new ArrayList<>(sessions.keySet())) destroyCreationWorld(worldName);
        sessions.clear();
    }

    public boolean toggleVisualization(String worldName, World world) {
        if (visualizationArmorStands.containsKey(worldName) && !visualizationArmorStands.get(worldName).isEmpty()) {
            removeVisualizationArmorStands(world);
            return false;
        }
        // If there are stray visualization stands (e.g. loaded from a saved NBT)
        // but nothing tracked, clean them up and stay disabled.
        if (world.getEntitiesByClass(ArmorStand.class).stream()
                .anyMatch(as -> as.isGlowing() && as.isSmall() && as.isCustomNameVisible()
                        && as.isInvulnerable() && !as.hasGravity())) {
            removeAllVisualizationArmorStands(world);
            return false;
        }
        List<UUID> armorStandIds = new ArrayList<>();

        // Resource spot markers
        var resourceMarkers = mc.markers().findMarkersInWorld(worldName, "resourcespot", null);
        for (var mm : resourceMarkers) {
            spawnVisualizationStand(world, armorStandIds, mm.getPosition(), mm.getName());
        }

        // Game markers (wools, spawnpoints, capture areas, boundaries, etc.)
        var gameMarkers = getGameMarkersGrouped(worldName);
        for (var entry : gameMarkers.entrySet()) {
            for (var me : entry.getValue()) {
                spawnVisualizationStand(world, armorStandIds, me.getPosition(), entry.getKey());
            }
        }

        visualizationArmorStands.put(worldName, armorStandIds);
        return true;
    }

    private void spawnVisualizationStand(World world, List<UUID> armorStandIds,
                                         BlockPos pos, String name) {
        var loc = new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5);
        var stand = world.spawn(loc, ArmorStand.class, as -> {
            as.setCustomName(name);
            as.setCustomNameVisible(true);
            as.setVisible(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setMarker(false);
            as.setGlowing(true);
            as.setSmall(true);
        });
        armorStandIds.add(stand.getUniqueId());
    }

    private void removeVisualizationArmorStands(World world) {
        String worldName = world.getName();
        List<UUID> ids = visualizationArmorStands.remove(worldName);
        if (ids == null) return;
        for (UUID id : ids) {
            var entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
    }

    /**
     * Remove every visualization armor stand from the world before the structure
     * is exported to NBT.
     *
     * <p>Plain {@link org.bukkit.entity.Entity#remove()} is deferred to the end
     * of the current tick in modern Spigot/Paper, so calling it immediately
     * before {@code createStructure(...)} (which captures entities) would still
     * include the stands in the saved NBT — they would then reappear the next
     * time the structure is modified. To make the removal effective within the
     * same tick we first teleport the stands outside the capture bounding box;
     * the subsequent capture therefore excludes them, and the deferred
     * {@code remove()} cleans them up afterwards.
     */
    private void ejectVisualizationArmorStands(World world) {
        // A point guaranteed to lie outside the [0,w]x[0,h]x[0,d] capture box
        // (the export always uses origin 0,0,0), so the stands are not captured.
        var outside = new Location(world, -64, -64, -64);
        for (ArmorStand as : world.getEntitiesByClass(ArmorStand.class)) {
            if (as.isGlowing() && as.isSmall() && as.isCustomNameVisible()
                    && as.isInvulnerable() && !as.hasGravity()) {
                as.teleport(outside);
                as.remove();
            }
        }
        visualizationArmorStands.remove(world.getName());
    }

    /**
     * Remove every armor stand in the world that matches the visualization
     * signature (glowing, small, custom-name-visible, invulnerable, no gravity).
     * Unlike {@link #removeVisualizationArmorStands(World)} this does not rely on
     * the tracking map, so it also cleans up stands that were loaded from a
     * previously-saved NBT (which are not tracked) and stray stands left behind
     * by a prior toggle.
     */
    private void removeAllVisualizationArmorStands(World world) {
        for (ArmorStand as : world.getEntitiesByClass(ArmorStand.class)) {
            if (as.isGlowing() && as.isSmall() && as.isCustomNameVisible()
                    && as.isInvulnerable() && !as.hasGravity()) {
                as.remove();
            }
        }
        visualizationArmorStands.remove(world.getName());
    }

    private void cleanupVisualization(String worldName) {
        World world = mc.worlds().getWorld(worldName);
        if (world != null) {
            removeVisualizationArmorStands(world);
        } else {
            visualizationArmorStands.remove(worldName);
        }
    }

    private static void cleanupParticleTask(BukkitTask task) {
        if (task != null) task.cancel();
    }

    public record CreationSession(String worldName, String typeName, String id, UUID playerUuid, float yaw) {
        public CreationSession withYaw(float yaw) {
            return new CreationSession(worldName, typeName, id, playerUuid, yaw);
        }
    }

    public @Nullable CreationSession getCreationSession(String worldName) {
        return sessions.get(worldName);
    }
}
