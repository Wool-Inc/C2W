package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.LayoutPlacement;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.bootstrap.config.LayoutData;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LayoutEditorManager implements AutoCloseable {

    private static final int DEFAULT_W = 16;
    private static final int DEFAULT_H = 10;
    private static final int DEFAULT_D = 16;

    private final JavaPlugin plugin;
    private final EventManager eventManager;
    private final WorldManager worldManager;
    private final StructureManager structureManager;
    private final FolderStructureTypeConfig structureTypeConfig;
    private final MinecraftManager mc;
    private final File dataFolder;

    private final Map<String, EditorSession> sessions = new HashMap<>();
    private final Map<String, Map<Integer, BukkitTask>> particleTasks = new HashMap<>();
    private final Map<String, Scoreboard> editorScoreboards = new HashMap<>();
    private final Map<String, Objective> editorObjectives = new HashMap<>();

    public LayoutEditorManager(
            JavaPlugin plugin,
            EventManager eventManager,
            WorldManager worldManager,
            StructureManager structureManager,
            FolderStructureTypeConfig structureTypeConfig,
            MinecraftManager mc,
            File dataFolder
    ) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.worldManager = worldManager;
        this.structureManager = structureManager;
        this.structureTypeConfig = structureTypeConfig;
        this.mc = mc;
        this.dataFolder = dataFolder;

        this.eventManager.registerMinecraftEvent(PlayerTeleportEvent.class, this::onPlayerTeleport);
    }

    public void createEditorWorld(Player player, String layoutName) {
        String worldName = "c2w_layout_" + layoutName;

        if (sessions.containsKey(worldName)) {
            player.sendMessage("Editor world for '" + layoutName + "' already exists.");
            return;
        }

        World world = mc.worlds().createVoidWorld(worldName, World.Environment.NORMAL);
        if (world == null) {
            player.sendMessage("Failed to create editor world.");
            return;
        }

        sessions.put(worldName, new EditorSession(worldName, layoutName, player.getUniqueId()));
        particleTasks.put(worldName, new HashMap<>());

        mc.players().teleportToWorld(player.getName(), new BlockPos(0, 65, 0), worldName);
        showBoundaryParticles(world);
        setupScoreboardLegend(player, worldName);

        player.sendMessage("Layout editor world created. Use /layout place <type> to place structure outlines.");
        player.sendMessage("Use /layout setspawn <index> <red|blue> on a structure to mark it as a spawn island.");
    }

    public void modifyEditorWorld(Player player, String layoutName) {
        String worldName = "c2w_layout_" + layoutName;

        if (sessions.containsKey(worldName)) {
            player.sendMessage("Editor world for '" + layoutName + "' already exists.");
            return;
        }

        File layoutsDir = new File(dataFolder, "layouts");
        File layoutFile = new File(layoutsDir, layoutName + ".yml");
        if (!layoutFile.exists()) {
            player.sendMessage("Layout '" + layoutName + "' does not exist.");
            return;
        }

        LayoutData data;
        try {
            data = LayoutData.load(layoutFile);
        } catch (RuntimeException e) {
            player.sendMessage("Failed to load layout '" + layoutName + "': " + e.getMessage());
            return;
        }

        World world = mc.worlds().createVoidWorld(worldName, World.Environment.NORMAL);
        if (world == null) {
            player.sendMessage("Failed to create editor world.");
            return;
        }

        EditorSession session = new EditorSession(worldName, layoutName, player.getUniqueId());
        session.placements.addAll(data.getPlacements());
        sessions.put(worldName, session);
        particleTasks.put(worldName, new HashMap<>());

        // ponytail: seed particle wireframes for all existing placements
        for (int i = 0; i < session.placements.size(); i++) {
            var p = session.placements.get(i);
            startPlacementParticles(worldName, i, p.typeName(), p.x(), p.y(), p.z(), p.yaw());
        }

        mc.players().teleportToWorld(player.getName(), new BlockPos(data.getOriginX(), data.getOriginY() + 1, data.getOriginZ()), worldName);
        showBoundaryParticles(world);
        setupScoreboardLegend(player, worldName);

        player.sendMessage("Layout '" + layoutName + "' loaded with " + session.placements.size() + " structures.");
        player.sendMessage("Use /layout place/remove/rotate/move/setspawn to edit, then /layout save or /layout discard.");
    }

    public void placeStructure(Player player, String typeName) {
        String worldName = mc.players().getWorldName(player.getName());
        if (worldName == null) {
            player.sendMessage("Could not determine your world.");
            return;
        }
        EditorSession session = sessions.get(worldName);
        if (session == null) {
            player.sendMessage("You are not in a layout editor world.");
            return;
        }

        var pos = mc.players().getPosition(player.getName());

        if (!structureManager.hasType(typeName)) {
            player.sendMessage("No templates available for type '" + typeName + "'.");
            return;
        }

        // Check overlap with existing placements
        for (var existing : session.placements) {
            if (aabbsOverlap(pos.x(), pos.y(), pos.z(), typeName,
                    existing.x(), existing.y(), existing.z(), existing.typeName())) {
                player.sendMessage("Cannot place here: would overlap existing " + existing.typeName() + ".");
                return;
            }
        }

        float yaw = Math.round(player.getLocation().getYaw() / 90.0f) * 90.0f;
        session.placements.add(new LayoutPlacement(typeName,
                pos.x(), pos.y(), pos.z(), null, yaw));

        startPlacementParticles(worldName, session.placements.size() - 1, typeName,
                pos.x(), pos.y(), pos.z(), yaw);

        player.sendMessage("Placed " + typeName + " at ("
                + pos.x() + ", " + pos.y() + ", " + pos.z() + ") facing " + (int) yaw + "\u00B0");
    }

    public void removeStructure(Player player) {
        String worldName = mc.players().getWorldName(player.getName());
        if (worldName == null) {
            player.sendMessage("Could not determine your world.");
            return;
        }
        EditorSession session = sessions.get(worldName);
        if (session == null) {
            player.sendMessage("You are not in a layout editor world.");
            return;
        }

        var pos = mc.players().getPosition(player.getName());
        int bestIndex = -1;

        // Find first placement whose AABB contains the player
        for (int i = 0; i < session.placements.size(); i++) {
            var p = session.placements.get(i);
            if (pointInAABB(pos.x(), pos.y(), pos.z(), p.x(), p.y(), p.z(), p.typeName())) {
                bestIndex = i;
                break;
            }
        }

        if (bestIndex >= 0) {
            var removed = session.placements.remove(bestIndex);
            stopPlacementParticles(worldName, bestIndex);

            var tasks = particleTasks.get(worldName);
            if (tasks != null) {
                Map<Integer, BukkitTask> reindexed = new HashMap<>();
                for (var entry : tasks.entrySet()) {
                    int oldIdx = entry.getKey();
                    if (oldIdx > bestIndex) {
                        reindexed.put(oldIdx - 1, entry.getValue());
                    } else if (oldIdx < bestIndex) {
                        reindexed.put(oldIdx, entry.getValue());
                    }
                }
                tasks.clear();
                tasks.putAll(reindexed);
            }

            World world = mc.worlds().getWorld(worldName);
            if (world != null) {
                world.spawnParticle(Particle.EXPLOSION, removed.x() + 0.5, removed.y() + 1, removed.z() + 0.5, 1, 0, 0, 0);
            }
            player.sendMessage("Removed " + removed.typeName() + ".");
        } else {
            player.sendMessage("You are not inside any placed structure.");
        }
    }

    public void setSpawnTeam(Player player, String team) {
        String worldName = mc.players().getWorldName(player.getName());
        if (worldName == null) {
            player.sendMessage("Could not determine your world.");
            return;
        }
        EditorSession session = sessions.get(worldName);
        if (session == null) {
            player.sendMessage("You are not in a layout editor world.");
            return;
        }

        var pos = mc.players().getPosition(player.getName());
        int index = findPlacementAt(session, pos.x(), pos.y(), pos.z());
        if (index < 0) {
            player.sendMessage("You are not inside any placed structure.");
            return;
        }

        var p = session.placements.get(index);
        session.placements.set(index, new LayoutPlacement(p.typeName(), p.x(), p.y(), p.z(), team, p.yaw()));
        player.sendMessage("Marked placement " + index + " (" + p.typeName() + ") as " + team + " spawn.");
    }

    public void rotateStructure(Player player, float degrees) {
        String worldName = mc.players().getWorldName(player.getName());
        if (worldName == null) {
            player.sendMessage("Could not determine your world.");
            return;
        }
        EditorSession session = sessions.get(worldName);
        if (session == null) {
            player.sendMessage("You are not in a layout editor world.");
            return;
        }

        var pos = mc.players().getPosition(player.getName());
        int index = findPlacementAt(session, pos.x(), pos.y(), pos.z());
        if (index < 0) {
            player.sendMessage("You are not inside any placed structure.");
            return;
        }

        var p = session.placements.get(index);
        float newYaw = (p.yaw() + degrees) % 360;
        session.placements.set(index, new LayoutPlacement(p.typeName(), p.x(), p.y(), p.z(), p.spawnTeam(), newYaw));

        stopPlacementParticles(worldName, index);
        startPlacementParticles(worldName, index, p.typeName(), p.x(), p.y(), p.z(), newYaw);

        player.sendMessage("Rotated " + p.typeName() + " to " + (int)newYaw + "°.");
    }

    public void moveStructure(Player player, int dx, int dy, int dz) {
        String worldName = mc.players().getWorldName(player.getName());
        if (worldName == null) {
            player.sendMessage("Could not determine your world.");
            return;
        }
        EditorSession session = sessions.get(worldName);
        if (session == null) {
            player.sendMessage("You are not in a layout editor world.");
            return;
        }

        var pos = mc.players().getPosition(player.getName());
        int index = findPlacementAt(session, pos.x(), pos.y(), pos.z());
        if (index < 0) {
            player.sendMessage("You are not inside any placed structure.");
            return;
        }

        var p = session.placements.get(index);
        int newX = p.x() + dx;
        int newY = p.y() + dy;
        int newZ = p.z() + dz;

        // Check overlap against other placements (skip self)
        for (int i = 0; i < session.placements.size(); i++) {
            if (i == index) continue;
            var other = session.placements.get(i);
            if (aabbsOverlap(newX, newY, newZ, p.typeName(),
                    other.x(), other.y(), other.z(), other.typeName())) {
                player.sendMessage("Cannot move here: would overlap " + other.typeName() + ".");
                return;
            }
        }

        session.placements.set(index, new LayoutPlacement(p.typeName(), newX, newY, newZ, p.spawnTeam(), p.yaw()));

        stopPlacementParticles(worldName, index);
        startPlacementParticles(worldName, index, p.typeName(), newX, newY, newZ, p.yaw());

        player.sendMessage("Moved " + p.typeName() + " to ("
                + newX + ", " + newY + ", " + newZ + ").");
    }

    // ponytail: O(n) scan, fine for editor sessions (typically < 100 placements)
    private int findPlacementAt(EditorSession session, int px, int py, int pz) {
        for (int i = 0; i < session.placements.size(); i++) {
            var p = session.placements.get(i);
            if (pointInAABB(px, py, pz, p.x(), p.y(), p.z(), p.typeName())) {
                return i;
            }
        }
        return -1;
    }

    public void saveLayout(Player player) {
        String worldName = mc.players().getWorldName(player.getName());
        if (worldName == null) {
            player.sendMessage("Could not determine your world.");
            return;
        }
        EditorSession session = sessions.get(worldName);
        if (session == null) {
            player.sendMessage("You are not in a layout editor world.");
            return;
        }

        if (session.placements.isEmpty()) {
            player.sendMessage("No structures placed. Place at least one structure before saving.");
            return;
        }

        LayoutData data = new LayoutData(session.layoutName);
        World world = mc.worlds().getWorld(worldName);
        if (world != null) {
            data.setOrigin(world.getSpawnLocation().getBlockX(), world.getSpawnLocation().getBlockY(), world.getSpawnLocation().getBlockZ());
        }
        for (var p : session.placements) {
            data.addPlacement(p.typeName(), p.x(), p.y(), p.z(), p.yaw());
            if (p.spawnTeam() != null) {
                data.markSpawnTeam(data.getPlacements().size() - 1, p.spawnTeam());
            }
        }
        File layoutsDir = new File(dataFolder, "layouts");
        if (!layoutsDir.exists()) layoutsDir.mkdirs();
        data.save(layoutsDir);
        player.sendMessage("Layout '" + session.layoutName + "' saved with " + session.placements.size() + " structures.");
        cleanupSession(worldName);
    }

    public void discardEditor(Player player) {
        String worldName = mc.players().getWorldName(player.getName());
        if (worldName == null) {
            player.sendMessage("Could not determine your world.");
            return;
        }
        EditorSession session = sessions.get(worldName);
        if (session == null) {
            player.sendMessage("You are not in a layout editor world.");
            return;
        }

        player.sendMessage("Layout editor discarded.");
        cleanupSession(worldName);
    }

    public List<LayoutPlacement> getSessionPlacements(Player player) {
        String worldName = mc.players().getWorldName(player.getName());
        if (worldName == null) return List.of();
        EditorSession session = sessions.get(worldName);
        if (session == null) return List.of();
        return session.placements;
    }

    // ─── AABB helpers ─────────────────────────────────────────────────

    /**
     * Check whether two placement AABBs overlap.
     * Each AABB is centered at (cx, cy, cz) and sized by structure type dimensions.
     */
    private boolean aabbsOverlap(int ax, int ay, int az, String typeA,
                                 int bx, int by, int bz, String typeB) {
        int[] da = structureTypeConfig.getDimensions(typeA);
        int[] db = structureTypeConfig.getDimensions(typeB);
        int aw = da != null ? da[0] : DEFAULT_W;
        int ah = da != null ? da[1] : DEFAULT_H;
        int ad = da != null ? da[2] : DEFAULT_D;
        int bw = db != null ? db[0] : DEFAULT_W;
        int bh = db != null ? db[1] : DEFAULT_H;
        int bd = db != null ? db[2] : DEFAULT_D;

        // No gap means overlap
        return Math.abs(ax - bx) * 2 < (aw + bw)
                && Math.abs(az - bz) * 2 < (ad + bd)
                && !(ay + ah <= by || by + bh <= ay);
    }

    /** Check whether a point (px, py, pz) is inside a placement's AABB. */
    private boolean pointInAABB(int px, int py, int pz,
                                int cx, int cy, int cz, String typeName) {
        int[] dims = structureTypeConfig.getDimensions(typeName);
        int w = dims != null ? dims[0] : DEFAULT_W;
        int h = dims != null ? dims[1] : DEFAULT_H;
        int d = dims != null ? dims[2] : DEFAULT_D;

        int halfW = w / 2;
        int halfD = d / 2;
        return px >= cx - halfW && px < cx - halfW + w
                && py >= cy && py < cy + h
                && pz >= cz - halfD && pz < cz - halfD + d;
    }

    // ─── Particle wireframe ───────────────────────────────────────────

    private void startPlacementParticles(String worldName, int placementIndex,
                                         String typeName, int cx, int cy, int cz, float yaw) {
        int[] dims = structureTypeConfig.getDimensions(typeName);
        int w = dims != null ? dims[0] : DEFAULT_W;
        int h = dims != null ? dims[1] : DEFAULT_H;
        int d = dims != null ? dims[2] : DEFAULT_D;

        DustOptions color = particleColor(typeName);

        double minX = cx - w / 2.0;
        double minY = cy;
        double minZ = cz - d / 2.0;
        double maxX = cx + w / 2.0;
        double maxY = cy + h;
        double maxZ = cz + d / 2.0;

        // Convert yaw to direction vector for the directional indicator
        float normalizedYaw = ((yaw % 360) + 360) % 360;
        double yawRad = Math.toRadians(normalizedYaw);
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        if (Bukkit.getServer() != null) {
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    World world = mc.worlds().getWorld(worldName);
                    if (world == null) return;
                    drawWireframe(world, minX, minY, minZ, maxX, maxY, maxZ, color);
                    // Draw persistent directional arrow
                    double sx = cx + 0.5, sy = cy + 0.5, sz = cz + 0.5;
                    var arrowOpts = new DustOptions(org.bukkit.Color.fromRGB(255, 170, 0), 1);
                    for (double t = 0.5; t <= 3.0; t += 0.25) {
                        world.spawnParticle(Particle.DUST, sx + dirX * t, sy, sz + dirZ * t, 1, 0, 0, 0, arrowOpts);
                    }
                }
            }.runTaskTimer(plugin, 0L, 10L);

            particleTasks.computeIfAbsent(worldName, k -> new HashMap<>()).put(placementIndex, task);
        }
    }

    private void stopPlacementParticles(String worldName, int placementIndex) {
        var tasks = particleTasks.get(worldName);
        if (tasks != null) {
            BukkitTask task = tasks.remove(placementIndex);
            if (task != null) task.cancel();
        }
    }

    private void drawWireframe(World world,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ,
                               DustOptions opts) {
        double step = 1.0;
        for (double x = minX; x <= maxX; x += step) {
                spawn(world, opts, x, minY, minZ);
                spawn(world, opts, x, minY, maxZ);
                spawn(world, opts, x, maxY, minZ);
                spawn(world, opts, x, maxY, maxZ);
        }
        for (double y = minY; y <= maxY; y += step) {
                spawn(world, opts, minX, y, minZ);
                spawn(world, opts, minX, y, maxZ);
                spawn(world, opts, maxX, y, minZ);
                spawn(world, opts, maxX, y, maxZ);
        }
        for (double z = minZ; z <= maxZ; z += step) {
                spawn(world, opts, minX, minY, z);
                spawn(world, opts, minX, maxY, z);
                spawn(world, opts, maxX, minY, z);
                spawn(world, opts, maxX, maxY, z);
        }
    }

    private void spawn(World world, DustOptions opts, double x, double y, double z) {
        world.spawnParticle(Particle.DUST, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, opts);
    }

    // ─── Scoreboard legend ────────────────────────────────────────────

    private void setupScoreboardLegend(Player player, String worldName) {
        Scoreboard sb = mc.scoreboards().getMainScoreboard();
        String objName = "layout_legend_" + worldName;

        Objective old = sb.getObjective(objName);
        if (old != null) old.unregister();

        Objective objective = sb.registerNewObjective(objName, "dummy", "§e§lLayout Structures");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        var types = new ArrayList<String>();
        EditorSession session = sessions.get(worldName);
        if (session != null) {
            for (var p : session.placements) {
                if (!types.contains(p.typeName())) types.add(p.typeName());
            }
        }
        if (types.isEmpty()) {
            var typeNames = structureTypeConfig.getTypeNames();
            if (typeNames != null) types.addAll(typeNames);
        }

        int score = types.size();
        for (String type : types) {
            Team team = sb.getTeam("layout_" + type);
            if (team == null) {
                team = sb.registerNewTeam("layout_" + type);
            }
            team.setColor(colorFor(type));
            team.addEntry(type);
            objective.getScore(type).setScore(score--);
        }

        player.setScoreboard(sb);
        editorScoreboards.put(worldName, sb);
        editorObjectives.put(worldName, objective);
    }

    private void removeScoreboardLegend(Player player, String worldName) {
        Scoreboard sb = editorScoreboards.remove(worldName);
        Objective obj = editorObjectives.remove(worldName);
        if (obj != null) obj.unregister();
        if (sb != null) {
            for (var team : sb.getTeams()) {
                if (team.getName().startsWith("layout_")) team.unregister();
            }
        }
        player.setScoreboard(mc.scoreboards().getMainScoreboard());
    }

    private org.bukkit.ChatColor colorFor(String typeName) {
        return switch (typeName.toUpperCase()) {
            case "DUNGEON" -> org.bukkit.ChatColor.RED;
            case "TRANSITION" -> org.bukkit.ChatColor.GREEN;
            case "CENTER" -> org.bukkit.ChatColor.YELLOW;
            case "CORNER" -> org.bukkit.ChatColor.LIGHT_PURPLE;
            case "SIDE" -> org.bukkit.ChatColor.AQUA;
            case "SPAWN" -> org.bukkit.ChatColor.BLUE;
            default -> org.bukkit.ChatColor.WHITE;
        };
    }

    // ─── Boundary particles ───────────────────────────────────────────
    // ponytail: repeating task so particles persist, same pattern as placement wireframes

    private void showBoundaryParticles(World world) {
        int minX = -32, maxX = 32;
        int minZ = -32, maxZ = 32;
        int y = 64;
        world.setSpawnLocation(0, 65, 0);
        String worldName = world.getName();
        if (worldName == null) return;
        var tasks = particleTasks.get(worldName);
        if (tasks != null) {
            BukkitTask existing = tasks.get(-1);
            if (existing != null) existing.cancel();
        }
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                World w = Bukkit.getWorld(worldName);
                if (w == null) return;
                for (int x = minX; x <= maxX; x += 4) {
                    for (int z = minZ; z <= maxZ; z += 4) {
                        boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                        if (edge) {
                            w.spawnParticle(Particle.DUST, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, new DustOptions(org.bukkit.Color.fromRGB(100, 100, 100), 1));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        particleTasks.computeIfAbsent(worldName, k -> new HashMap<>()).put(-1, task);
    }

    private DustOptions particleColor(String typeName) {
        return switch (typeName.toUpperCase()) {
            case "DUNGEON" -> new DustOptions(org.bukkit.Color.fromRGB(255, 85, 0), 1);
            case "TRANSITION" -> new DustOptions(org.bukkit.Color.fromRGB(85, 255, 85), 1);
            case "CENTER" -> new DustOptions(org.bukkit.Color.fromRGB(255, 255, 85), 1);
            case "CORNER" -> new DustOptions(org.bukkit.Color.fromRGB(170, 0, 170), 1);
            case "SIDE" -> new DustOptions(org.bukkit.Color.fromRGB(85, 255, 255), 1);
            case "SPAWN" -> new DustOptions(org.bukkit.Color.fromRGB(255, 85, 85), 1);
            default -> new DustOptions(org.bukkit.Color.fromRGB(200, 200, 200), 1);
        };
    }

    // ─── Teleport lock ────────────────────────────────────────────────

    private void onPlayerTeleport(PlayerTeleportEvent event) {
        String fromName = mc.players().getWorldName(event.getPlayer().getName());
        if (fromName == null || !sessions.containsKey(fromName)) return;
        var toWorld = event.getTo().getWorld();
        if (toWorld != null && fromName.equals(toWorld.getName())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cYou must save or discard before leaving. Use: /layout save | /layout discard");
    }

    // ─── Cleanup ──────────────────────────────────────────────────────

    private void cleanupSession(String worldName) {
        var tasks = particleTasks.remove(worldName);
        if (tasks != null) {
            for (var task : tasks.values()) task.cancel();
        }

        EditorSession session = sessions.get(worldName);
        if (session != null && Bukkit.getServer() != null) {
            Player player = Bukkit.getPlayer(session.playerUuid);
            if (player != null) removeScoreboardLegend(player, worldName);
        }

        sessions.remove(worldName);

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
    }

    @Override
    public void close() {
        for (String worldName : new ArrayList<>(sessions.keySet())) cleanupSession(worldName);
        sessions.clear();
        particleTasks.clear();
        for (var obj : editorObjectives.values()) obj.unregister();
        editorObjectives.clear();
        for (var sb : editorScoreboards.values()) {
            for (var team : sb.getTeams()) {
                if (team.getName().startsWith("layout_")) team.unregister();
            }
        }
        editorScoreboards.clear();
    }

    private static class EditorSession {
        final String worldName;
        final String layoutName;
        final UUID playerUuid;
        final List<LayoutPlacement> placements = new ArrayList<>();

        EditorSession(String worldName, String layoutName, UUID playerUuid) {
            this.worldName = worldName;
            this.layoutName = layoutName;
            this.playerUuid = playerUuid;
        }
    }
}
