package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class WorldManager implements AutoCloseable {

    private final JavaPlugin plugin;
    private final MinecraftManager mc;
    private final ManagedWorld lobbyWorld;
    private final ManagedWorld referenceWorld;

    private final ManagedWorld draftWorld;
    private final ManagedWorld gameWorld;

    // ponytail: prefixes matching transient C2W worlds left over from a crash/restart.
    // NOTE: c2w_create_* creation worlds are cleaned up here on startup rather than
    // restored — stale worlds from a crash are deleted to avoid loading stale data.
    // (StructureCreationManager.restoreSessions() is retained as a fallback for
    // worlds in per-world dimension dirs that cleanupLeftoverTransientWorlds misses.)
    private static final String[] TRANSIENT_PREFIXES = {
        "c2w_draft", "c2w_game", "c2w_resource_", "c2w_layout_", "c2w_create_"
    };

    public WorldManager(JavaPlugin plugin, MinecraftManager mc) {
        this.plugin = plugin;
        this.mc = mc;
        var worlds = mc.worlds();

        // Persistent worlds: created on first load, never deleted by the plugin.
        // The lobby gets a flat 9x9 bedrock platform so more players fit waiting there.
        this.lobbyWorld = new ManagedWorld("c2w_lobby", false, worlds, false, true, 4);
        this.referenceWorld = new ManagedWorld("c2w_reference", false, worlds, true, false, 0);

        // Transient worlds: created on demand, deleted by destroyDraftAndGameWorlds.
        // The draft world places no center cross — its spawn layout (including the
        // team selection platforms and walkways) is built entirely in createTeamSelectionAreas.
        this.draftWorld = new ManagedWorld("c2w_draft", true, worlds, false, false, 0);
        // Game world: no center cross — spawn points come from layout SPAWN structures.
        this.gameWorld = new ManagedWorld("c2w_game", true, worlds, false, false, 0);

        // Nuke leftover transient worlds from a previous crash/restart.
        cleanupLeftoverTransientWorlds();

        // Make sure persistent worlds exist at server start.
        this.lobbyWorld.loadOrCreate();
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
        createTeamSelectionAreas(w);
        return w;
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
