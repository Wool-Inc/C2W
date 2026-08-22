package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Manages resource definition worlds for each structure type.
 * In a resource world, admins place blocks (chests, spawners, etc.) and mark
 * them as resource instances. These are saved as NBT and used when placing
 * structures in the game world.
 */
public class ResourceManager implements AutoCloseable {

    private final JavaPlugin plugin;
    private final EventManager eventManager;
    private final WorldManager worldManager;
    private final FolderStructureTypeConfig structureTypeConfig;
    private final File dataFolder;
    private final MinecraftManager mc;
    private final NamespacedKey resourceInstanceKey;

    private final Map<String, ResourceSession> sessions = new HashMap<>();
    private final Map<String, BukkitTask> particleTasks = new HashMap<>();
    private static final Logger log = Logger.getLogger(ResourceManager.class.getName());

    public ResourceManager(
            JavaPlugin plugin,
            EventManager eventManager,
            WorldManager worldManager,
            FolderStructureTypeConfig structureTypeConfig,
            File dataFolder,
            MinecraftManager mc
    ) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.worldManager = worldManager;
        this.structureTypeConfig = structureTypeConfig;
        this.dataFolder = dataFolder;
        this.mc = mc;
        this.resourceInstanceKey = new NamespacedKey(plugin, "resourceinstance");

        this.eventManager.registerMinecraftEvent(PlayerTeleportEvent.class, this::onPlayerTeleport);
    }

    /** Open (or create) the resource definition world for a structure type. */
    public void openResourceWorld(Player player, String typeName) {
        String worldName = resourceWorldName(typeName);

        if (sessions.containsKey(worldName)) {
            if (Bukkit.getWorld(worldName) != null) {
                var dims = structureTypeConfig.getDimensions(typeName);
                if (dims != null) {
                    startParticleBoundary(worldName, dims[0], dims[1], dims[2]);
                }
                mc.players().teleportToWorld(
                        player.getName(),
                        new BlockPos(0, 1, 0),
                        worldName);
                return;
            }
            sessions.remove(worldName);
        }

        World world = mc.worlds().createVoidWorld(worldName, World.Environment.NORMAL);
        if (world == null) {
            player.sendMessage("Failed to create resource world.");
            return;
        }

        File nbtFile = resourceNbtFile(typeName);
        if (nbtFile.exists()) {
            try {
                String structureId = mc.structures().loadStructure(nbtFile);
                mc.structures().place(
                        structureId, worldName,
                        new BlockPos(0, 0, 0),
                        true,
                        net.klaaswhite.c2w.domain.model.StructureRotation.NONE,
                        net.klaaswhite.c2w.domain.model.Mirror.NONE,
                        -1, 1.0f, new java.util.Random());
            } catch (IOException e) {
                log.warning("Failed to load resource NBT: " + e.getMessage());
            }
        }

        world.setSpawnLocation(new Location(world, 0.5, 1, 0.5));
        world.getBlockAt(0, 0, 0).setType(Material.BEDROCK);

        sessions.put(worldName, new ResourceSession(worldName, typeName, player.getUniqueId()));

        var dims = structureTypeConfig.getDimensions(typeName);
        if (dims != null) {
            startParticleBoundary(worldName, dims[0], dims[1], dims[2]);
        } else {
            player.sendMessage("Structure type '" + typeName + "' has no dimensions defined. No boundary shown.");
        }

        mc.players().teleportToWorld(
                player.getName(),
                new BlockPos(0, 1, 0),
                worldName);
    }

    /**
         * Mark the player's targeted block as a resource instance.
         * The mode (block vs. container) is driven by the resource's defined type in
         * structure.yml (see {@link FolderStructureTypeConfig#getResourceType}), matching
         * how GameManager.placeResources reads it at placement time. This keeps marking
         * consistent with the resource definition rather than the targeted block's type.
     * If the resource is not yet defined, it defaults to block mode (multiple markers,
     * count message). Container mode has special inventory-slot semantics and must be
     * opted into explicitly via /structure resource define &lt;container&gt; ..., so it is
     * never inferred from the targeted block's type. A resource can use either container
     * mode or block mode, never both.
         */
        public boolean markResourceBlock(Player player, String typeName, String resourceId) {
            String worldName = resourceWorldName(typeName);
            if (!sessions.containsKey(worldName)) {
                player.sendMessage("No active resource session. Use /structure resource world first.");
                return false;
            }

            Block target = player.getTargetBlockExact(5);
            if (target == null) {
                player.sendMessage("No block targeted. Look at a block within 5 blocks.");
                return false;
            }

            String resourceType = structureTypeConfig.getResourceType(typeName, resourceId);
            if ("container".equals(resourceType)) {
                if (target.getState() instanceof Container container) {
                    return markContainerResource(player, container, typeName, resourceId, worldName, target);
                }
                player.sendMessage("Resource '" + resourceId + "' is defined as a container resource. "
                        + "Look at a container block (chest, barrel, etc.) to mark it.");
                return false;
            }
            if ("block".equals(resourceType)) {
                return markBlockResource(player, typeName, resourceId, worldName, target);
            }

            // Undefined resource: default to block mode (multiple markers, count message).
            // Inferring the mode from the targeted block's type was wrong: a chest/barrel
            // intended as a block-mode spot was silently routed to container mode (single
            // marker, no count). Container mode has special inventory-slot semantics and
            // should be opted into explicitly via /structure resource define <container> ...
            return markBlockResource(player, typeName, resourceId, worldName, target);
        }

    /**
     * Mark a container block as a resource. The container's inventory contents
     * define multiple resource variants (one per non-empty slot).
     * Blocked if block-mode markers already exist for this resource.
     */
    private boolean markContainerResource(Player player, Container container, String typeName,
                                          String resourceId, String worldName, Block target) {
        if (hasBlockModeMarkers(worldName, resourceId)) {
            player.sendMessage("Resource '" + resourceId + "' already has block-mode markers. "
                    + "Remove them before using container mode.");
            return false;
        }

        boolean replaced = removeExistingContainerMarker(worldName, resourceId);

        Marker marker = target.getWorld().spawn(
                target.getLocation().add(0.5, 0, 0.5),
                Marker.class, m -> {
                    m.setPersistent(true);
                    m.getPersistentDataContainer().set(resourceInstanceKey, PersistentDataType.STRING, resourceId);
                });

        if (marker == null) {
            player.sendMessage("Failed to create resource marker.");
            return false;
        }

        player.sendMessage((replaced ? "Replaced" : "Marked") + " container '" + resourceId
                + "' at (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")");
        return true;
    }

    /** Remove any existing container-mode marker for a resource; returns true if one was found and removed. */
    private boolean removeExistingContainerMarker(String worldName, String resourceId) {
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", resourceId);
        for (ManagedMarker m : markers) {
            String val = m.getName();
            if (resourceId.equals(val)) {
                m.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Mark a non-container block as a resource instance.
     * Blocked if container-mode data already exists for this resource.
     */
    private boolean markBlockResource(Player player, String typeName,
                                       String resourceId, String worldName, Block target) {
        if (hasContainerMode(worldName, resourceId)) {
            player.sendMessage("Resource '" + resourceId + "' already uses container mode. "
                    + "Remove the container marker before placing block-mode markers.");
            return false;
        }

        int nextNumber = countMarkedInstances(worldName, resourceId) + 1;

        String pdcValue = resourceId + "-" + nextNumber;
        Marker marker = target.getWorld().spawn(
                target.getLocation().add(0.5, 0, 0.5),
                Marker.class, m -> {
                    m.setPersistent(true);
                    m.getPersistentDataContainer().set(resourceInstanceKey, PersistentDataType.STRING, pdcValue);
                });

        if (marker == null) {
            player.sendMessage("Failed to create resource marker.");
            return false;
        }

        player.sendMessage("Marked '" + resourceId + "' at ("
                + target.getX() + ", " + target.getY() + ", " + target.getZ()
                + "). " + nextNumber + " marker(s) for this resource in the world.");
        return true;
    }

    /** Check if block-mode markers exist for a resource in the world. */
    private boolean hasBlockModeMarkers(String worldName, String resourceId) {
        return countMarkedInstances(worldName, resourceId) > 0;
    }

    /** Check if container-mode data exists for a resource (in world). */
    public boolean hasContainerModeData(String typeName, String resourceId) {
        String worldName = resourceWorldName(typeName);
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", resourceId);
        for (ManagedMarker m : markers) {
            if (resourceId.equals(m.getName())) return true;
        }
        return false;
    }

    /** Check if container-mode data exists for a resource in the live world only. */
    private boolean hasContainerMode(String worldName, String resourceId) {
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", resourceId);
        for (ManagedMarker m : markers) {
            if (resourceId.equals(m.getName())) return true;
        }
        return false;
    }

    /** Count non-empty slots in a container (each slot = one resource variant). */
    private int countContainerVariants(Container container) {
        int count = 0;
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) count++;
        }
        return count;
    }

    /** Save the resource world to NBT and return the player to lobby. */
    public boolean saveAndExit(Player player, String typeName) {
        String worldName = resourceWorldName(typeName);
        if (!sessions.containsKey(worldName)) {
            player.sendMessage("No active resource session for " + typeName + ".");
            return false;
        }

        if (Bukkit.getWorld(worldName) != null) {
            try {
                saveResourceNbt(worldName, typeName);
                saveMinSpotsToConfig(worldName, typeName);
            } catch (Exception e) {
                player.sendMessage("Failed to save resource NBT: " + e.getMessage());
                return false;
            }
        }

        cleanupSession(worldName);
        return true;
    }

    /** Discard the resource world without saving. */
    public boolean discardAndExit(Player player, String typeName) {
        String worldName = resourceWorldName(typeName);
        if (!sessions.containsKey(worldName)) {
            player.sendMessage("No active resource session for " + typeName + ".");
            return false;
        }
        cleanupSession(worldName);
        return true;
    }

    /** Get all container-mode resources and their variant counts for a type. */
    public Map<String, Integer> getContainerModeResources(String typeName) {
        Map<String, Integer> result = new HashMap<>();
        String worldName = resourceWorldName(typeName);
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", null);
        for (ManagedMarker m : markers) {
            String val = m.getName();
            if (val == null) continue;
            // Container mode: exact match (no -N suffix)
            int lastDash = val.lastIndexOf('-');
            if (lastDash >= 0) continue; // skip block-mode markers
            World bukkitWorld = Bukkit.getWorld(worldName);
            if (bukkitWorld != null) {
                BlockPos pos = m.getPosition();
                Block block = bukkitWorld.getBlockAt(pos.x(), pos.y(), pos.z());
                if (block.getState() instanceof Container container) {
                    result.merge(val, countContainerVariants(container), Integer::sum);
                } else {
                    result.merge(val, 1, Integer::sum);
                }
            }
        }
        return result;
    }

    /**
     * Remove a container-mode resource definition (world markers).
     * Called when an admin wants to redefine a resource from scratch.
     */
    public boolean removeContainerModeData(Player player, String typeName, String resourceId) {
        String worldName = resourceWorldName(typeName);
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", resourceId);
        for (ManagedMarker m : markers) {
            if (resourceId.equals(m.getName())) {
                m.remove();
            }
        }
        player.sendMessage("Removed container-mode data for '" + resourceId + "'. You can now redefine it.");
        return true;
    }

    // ------------------------------------------------------------------
    // Resource marker management (delegated from StructureCommand)
    // ------------------------------------------------------------------

    /** Get a ResourceSession by world name, or null if none. */
    public @Nullable ResourceSession getResourceSession(String worldName) {
        return sessions.get(worldName);
    }

    /** List all resource instance markers in a resource world, grouped by resource ID. */
    public Map<String, List<Location>> listMarkers(String worldName) {
        Map<String, List<Location>> result = new HashMap<>();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return result;
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", null);
        for (ManagedMarker m : markers) {
            String val = m.getName();
            if (val == null) continue;
            int lastDash = val.lastIndexOf('-');
            String resId = lastDash < 0 ? val : val.substring(0, lastDash);
            BlockPos pos = m.getPosition();
            result.computeIfAbsent(resId, k -> new ArrayList<>())
                    .add(new Location(world, pos.x(), pos.y(), pos.z()));
        }
        return result;
    }

    /** Remove all resource instance markers for a given resource ID. Returns count removed. */
    public int removeAllMarkersForResource(String worldName, String resourceId) {
        int count = 0;
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", resourceId + "-");
        for (ManagedMarker m : markers) {
            m.remove();
            count++;
        }
        return count;
    }

    /** Remove a resource instance marker at a specific location. */
    public boolean removeMarkerAt(String worldName, Location location) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", null);
        for (ManagedMarker m : markers) {
            BlockPos pos = m.getPosition();
            if (pos.x() == location.getBlockX() && pos.y() == location.getBlockY() && pos.z() == location.getBlockZ()) {
                m.remove();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Event handlers
    // ------------------------------------------------------------------

    private void onPlayerTeleport(PlayerTeleportEvent event) {
        String fromName = event.getPlayer().getWorld().getName();
        if (!sessions.containsKey(fromName)) return;
        if (event.getTo().getWorld() != null && fromName.equals(event.getTo().getWorld().getName())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cYou must save or discard before leaving. Use: /structure save | /structure discard");
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private String resourceWorldName(String typeName) {
        return "c2w_resource_" + typeName;
    }

    private File resourceNbtFile(String typeName) {
        return new File(dataFolder, "structures/" + typeName + "/resources.nbt");
    }

    private int countMarkedInstances(String worldName, String resourceId) {
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", resourceId + "-");
        return markers.size();
    }

    /** Count all resourceinstance markers per resourceId and write minSpots to structure.yml. */
    private void saveMinSpotsToConfig(String worldName, String typeName) {
        Map<String, Integer> counts = new HashMap<>();
        World bukkitWorld = Bukkit.getWorld(worldName);
        List<ManagedMarker> markers = mc.markers().findMarkersInWorld(worldName, "resourceinstance", null);
        for (ManagedMarker m : markers) {
            String val = m.getName();
            if (val == null) continue;
            int lastDash = val.lastIndexOf('-');
            if (lastDash < 0) {
                // container mode: count non-empty inventory slots
                int variantCount = 1; // ponytail: default if world unloaded
                if (bukkitWorld != null) {
                    BlockPos pos = m.getPosition();
                    Block block = bukkitWorld.getBlockAt(pos.x(), pos.y(), pos.z());
                    if (block.getState() instanceof Container container) {
                        variantCount = countContainerVariants(container);
                    }
                }
                counts.merge(val, variantCount, Integer::sum);
            } else {
                // block mode: one marker = one spot
                String resId = val.substring(0, lastDash);
                counts.merge(resId, 1, Integer::sum);
            }
        }
        if (!counts.isEmpty()) {
            structureTypeConfig.setResourceRequirements(typeName, counts);
        }
    }

    private void saveResourceNbt(String worldName, String typeName) throws IOException {
        if (Bukkit.getWorld(worldName) == null) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // Use the structure type's declared dimensions as the capture bounds. Scanning only
        // loaded chunks can miss unloaded tile entities / markers and produce wrong bounds.
        int maxX = 16, maxY = 16, maxZ = 16;
        int[] dims = structureTypeConfig.getDimensions(typeName);
        if (dims != null && dims.length == 3) {
            maxX = dims[0];
            maxY = dims[1];
            maxZ = dims[2];
        }

        String structureId = mc.structures().createStructure(worldName, new BlockPos(0, 0, 0), new BlockPos(maxX, maxY, maxZ));
        if (structureId == null) return;

        File resFile = resourceNbtFile(typeName);
        resFile.getParentFile().mkdirs();
        mc.structures().saveStructure(resFile, structureId);
    }

    private void cancelParticleTask(String worldName) {
        BukkitTask task = particleTasks.remove(worldName);
        if (task != null) task.cancel();
    }

    private void startParticleBoundary(String worldName, int w, int h, int d) {
        cancelParticleTask(worldName);

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        BoundingBox box = new BoundingBox(0, 0, 0, w, h, d);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                World w = Bukkit.getWorld(worldName);
                if (w == null || w.getPlayers().isEmpty()) return;
                drawParticleEdges(w, box);
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
        var opts = new DustOptions(org.bukkit.Color.fromRGB(50, 255, 50), 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            world.spawnParticle(Particle.DUST, x1 + dx * t, y1 + dy * t, z1 + dz * t, 1, 0, 0, 0, opts);
        }
    }

    // ------------------------------------------------------------------
    // Session management
    // ------------------------------------------------------------------

    private void cleanupSession(String worldName) {
        cancelParticleTask(worldName);
        sessions.remove(worldName);
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            var lobby = worldManager.getLobbyWorld().getWorld();
            for (Player p : world.getPlayers()) {
                if (lobby != null) {
                    mc.players().teleportToWorld(
                            p.getName(),
                            new BlockPos(lobby.getSpawnLocation().getBlockX(), lobby.getSpawnLocation().getBlockY(), lobby.getSpawnLocation().getBlockZ()),
                            lobby.getName());
                }
            }
            mc.worlds().unloadWorld(worldName);
        }
        mc.worlds().deleteWorld(worldName);
    }

    @Override
    public void close() {
        for (BukkitTask task : particleTasks.values()) task.cancel();
        particleTasks.clear();
        for (String worldName : new ArrayList<>(sessions.keySet())) cleanupSession(worldName);
        sessions.clear();
    }

    public record ResourceSession(String worldName, String typeName, UUID playerUuid) {}
}
