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
    // NOTE: c2w_create_* worlds are owned by StructureCreationManager and restored via
    // restoreSessions() (run after this manager in App.initManagers), so they must NOT be
    // deleted here or session restore would find nothing.
    private static final String[] TRANSIENT_PREFIXES = {
        "c2w_draft", "c2w_game", "c2w_resource_", "c2w_layout_"
    };

    public WorldManager(JavaPlugin plugin, MinecraftManager mc) {
        this.plugin = plugin;
        this.mc = mc;
        var worlds = mc.worlds();

        // Persistent worlds: created on first load, never deleted by the plugin.
        this.lobbyWorld = new ManagedWorld("c2w_lobby", false, worlds);
        this.referenceWorld = new ManagedWorld("c2w_reference", false, worlds);

        // Transient worlds: created on demand, deleted by destroyDraftAndGameWorlds.
        this.draftWorld = new ManagedWorld("c2w_draft", true, worlds);
        this.gameWorld = new ManagedWorld("c2w_game", true, worlds);

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

    /**
     * Place bedrock team selection areas in the draft world.
     * - Red team area: (-10, 64, 0)
     * - Blue team area: (10, 64, 0)
     * - Spectator area: (0, 70, 10)
     * - Spawn platform: (0, 64, 0)
     */
    private void createTeamSelectionAreas(World world) {
        if (mc == null) return;

        // Red team platform (-10, 64, 0) - 3x3 bedrock platform
        for (int x = -11; x <= -9; x++) {
            for (int z = -1; z <= 1; z++) {
                mc.blocks().setBlock(world.getName(), new BlockPos(x, 63, z), Material.BEDROCK);
                mc.blocks().setBlock(world.getName(), new BlockPos(x, 64, z), Material.RED_WOOL);
            }
        }

        // Blue team platform (10, 64, 0) - 3x3 bedrock platform
        for (int x = 9; x <= 11; x++) {
            for (int z = -1; z <= 1; z++) {
                mc.blocks().setBlock(world.getName(), new BlockPos(x, 63, z), Material.BEDROCK);
                mc.blocks().setBlock(world.getName(), new BlockPos(x, 64, z), Material.BLUE_WOOL);
            }
        }

        // Spectator platform (0, 70, 10) - 3x3 bedrock platform
        for (int x = -1; x <= 1; x++) {
            for (int z = 9; z <= 11; z++) {
                mc.blocks().setBlock(world.getName(), new BlockPos(x, 69, z), Material.BEDROCK);
                mc.blocks().setBlock(world.getName(), new BlockPos(x, 70, z), Material.GLASS);
            }
        }

        // Spawn platform (0, 64, 0) - 3x3 bedrock platform
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                mc.blocks().setBlock(world.getName(), new BlockPos(x, 63, z), Material.BEDROCK);
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
