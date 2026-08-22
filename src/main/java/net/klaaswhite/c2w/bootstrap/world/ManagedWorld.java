package net.klaaswhite.c2w.bootstrap.world;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Worlds;
import net.klaaswhite.c2w.domain.model.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;
import org.jspecify.annotations.Nullable;

import java.io.File;

public class ManagedWorld {

    private static final BlockPos DEFAULT_SPAWN_POS = new BlockPos(0, 65, 0);

    private final String name;
    private final boolean isTransient;
    private final boolean placeCenterCross;
    /** When true, a flat filled square of bedrock is placed at the world origin. */
    private final boolean placeCenterPlatform;
    /** Half-width (in blocks) of the filled platform: the platform is (2*halfWidth+1) wide. */
    private final int centerPlatformHalfWidth;
    private final WorldCreator creator;
    private final @Nullable Worlds worlds;
    private BlockPos spawnPos = DEFAULT_SPAWN_POS;

    public ManagedWorld(String name) {
        this(name, false);
    }

    public ManagedWorld(String name, boolean isTransient) {
        this(name, isTransient, null);
    }

    /**
     * Construct a managed world that routes world operations through the given
     * {@link Worlds} abstraction instead of Bukkit statics. Used by
     * {@link net.klaaswhite.c2w.adapter.managers.WorldManager} and headless tests.
     * When {@code worlds} is null, Bukkit statics are used as a fallback.
     */
    public ManagedWorld(String name, boolean isTransient, @Nullable Worlds worlds) {
        this(name, isTransient, worlds, true);
    }

    /**
     * Construct a managed world. When {@code placeCenterCross} is true, a 5-block
     * bedrock cross is placed at the world origin on creation. When
     * {@code placeCenterPlatform} is true, a flat square platform of bedrock
     * ({@code 2*centerPlatformHalfWidth+1} wide) is placed instead — used by the
     * lobby so more players fit on the waiting area. When both are false nothing
     * is placed at the origin (the game world passes false so its spawn points
     * come from layout SPAWN structures instead).
     */
    public ManagedWorld(String name, boolean isTransient, @Nullable Worlds worlds,
            boolean placeCenterCross, boolean placeCenterPlatform, int centerPlatformHalfWidth) {
        this.name = name;
        this.isTransient = isTransient;
        this.worlds = worlds;
        this.placeCenterCross = placeCenterCross;
        this.placeCenterPlatform = placeCenterPlatform;
        this.centerPlatformHalfWidth = centerPlatformHalfWidth;
        this.creator = makeCreator(name);
    }

    /** Kept for callers that only toggle the legacy center cross (e.g. the game world). */
    public ManagedWorld(String name, boolean isTransient, @Nullable Worlds worlds, boolean placeCenterCross) {
        this(name, isTransient, worlds, placeCenterCross, false, 0);
    }

    private static WorldCreator makeCreator(String name) {
        var c = new WorldCreator(name);
        c.type(WorldType.FLAT);
        c.generator(new VoidChunkGenerator());
        return c;
    }

    public String getName() {
        return name;
    }

    /** Get the spawn position for this world as a domain {@link BlockPos}. */
    public BlockPos getSpawnPos() {
        World w = getWorld();
        if (w == null) return spawnPos;
        Location spawn = w.getSpawnLocation();
        return new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
    }

    public boolean isTransient() {
        return isTransient;
    }

    /**
     * Return the loaded world, or null if it isn't loaded. Does not load.
     */
    public @Nullable World getWorld() {
        if (worlds != null) return worlds.getWorld(name);
        return Bukkit.getWorld(name);
    }

    public boolean isLoaded() {
        if (worlds != null) return worlds.isWorldLoaded(name);
        return Bukkit.getWorld(name) != null;
    }

    /**
     * Load the world, creating it if it doesn't exist. Returns the loaded
     * world, or null if it could not be created. Runs {@link #initializeWorld}
     * on both creation and load so persistent wait areas (e.g. the lobby
     * platform) are always present even on worlds created by older versions.
     */
    public @Nullable World loadOrCreate() {
        var w = getWorld();
        if (w == null) {
            w = createWorld();
            if (w == null) return null;
        }
        initializeWorld(w);
        return w;
    }

    private @Nullable World createWorld() {
        if (worlds != null) {
            return worlds.createVoidWorld(name, World.Environment.NORMAL);
        }
        return creator.createWorld();
    }

    /**
     * Unload the world and (if transient) delete its folder on disk.
     * Returns true if the world no longer exists on disk after the call.
     */
    public boolean delete() {
        if (!isTransient) {
            throw new IllegalStateException(
                    "Cannot delete persistent world '" + name + "'");
        }
        if (worlds != null) {
            worlds.unloadWorld(name);
            worlds.deleteWorld(name);
            return true;
        }
        var loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            Bukkit.unloadWorld(loaded, false);
        }
        var container = Bukkit.getWorldContainer();

        // Legacy path: <container>/<name>/
        var worldFolder = new File(container, name);
        if (worldFolder.exists()) {
            deleteDirectory(worldFolder);
        }
        // Paper 1.21+ path: <container>/<main-world>/dimensions/minecraft/<name>/
        var topDirs = container.listFiles(File::isDirectory);
        if (topDirs != null) {
            for (var topDir : topDirs) {
                var dimFolder = new File(topDir,
                        "dimensions" + File.separator + "minecraft" + File.separator + name);
                if (dimFolder.exists()) {
                    deleteDirectory(dimFolder);
                }
            }
        }
        return true;
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

    protected void initializeWorld(World world) {
        if (placeCenterPlatform) {
            // Flat filled platform: more room for players waiting in the lobby.
            for (int x = -centerPlatformHalfWidth; x <= centerPlatformHalfWidth; x++) {
                for (int z = -centerPlatformHalfWidth; z <= centerPlatformHalfWidth; z++) {
                    world.getBlockAt(x, 64, z).setType(Material.BEDROCK);
                }
            }
        } else if (placeCenterCross) {
            world.getBlockAt(0, 64, 0).setType(Material.BEDROCK);
            world.getBlockAt(1, 64, 0).setType(Material.BEDROCK);
            world.getBlockAt(-1, 64, 0).setType(Material.BEDROCK);
            world.getBlockAt(0, 64, 1).setType(Material.BEDROCK);
            world.getBlockAt(0, 64, -1).setType(Material.BEDROCK);
        }
        world.setSpawnLocation(new Location(world, 0, 65, 0));
        this.spawnPos = new BlockPos(0, 65, 0);
    }

    private static class VoidChunkGenerator extends ChunkGenerator {
    }
}
