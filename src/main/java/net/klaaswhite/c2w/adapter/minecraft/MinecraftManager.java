package net.klaaswhite.c2w.adapter.minecraft;

import net.klaaswhite.c2w.domain.events.C2WEvent;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ItemStackRef;
import net.klaaswhite.c2w.domain.model.Mirror;
import net.klaaswhite.c2w.domain.model.StructureRotation;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Top-level interface for accessing all Minecraft-related operations.
 * <p>
 * Groups Bukkit interactions into logical sub-interfaces. A single implementation
 * of this interface provides the entire Bukkit access layer for the plugin.
 * Domain code and managers should receive this interface (or specific sub-interfaces)
 * via constructor injection — never call Bukkit APIs directly.
 */
public interface MinecraftManager extends net.klaaswhite.c2w.domain.ops.MinecraftManager {

    /** Player-related operations (teleport, inventory, effects, etc.). */
    Players players();

    /** World-related operations (create, load, unload, delete worlds). */
    Worlds worlds();

    /** Server-level operations (broadcast, online players, raw Bukkit access). */
    Server server();

    /** Marker entity operations (spawn, remove, query persistent data). */
    Markers markers();

    /** Scoreboard and team operations. */
    Scoreboards scoreboards();

    /** NBT structure load/save/create operations. */
    Structures structures();

    /** Block get/set operations in a world. */
    Blocks blocks();

    /** Boss bar operations. */
    BossBars bossBars();

    /** Plugin access (JavaPlugin, data folder, logger). */
    Plugin plugin();

    /** Trial-spawner and vault operations. */
    TrialSpawners trialSpawners();

    /** Push a C2W internal event. */
    void pushEvent(C2WEvent event);

    /**
     * World-related Minecraft operations.
     * <p>
     * Abstracts creation, copying, loading, unloading, and deletion of Bukkit worlds.
     * Also provides chunk-level operations for game world preparation.
     * Nested (rather than a top-level interface) so that the covariant override of
     * {@link net.klaaswhite.c2w.domain.ops.MinecraftManager#worlds()} resolves
     * correctly at call sites.
     */
    interface Worlds extends net.klaaswhite.c2w.domain.ops.MinecraftManager.Worlds {

        /**
         * Create a new world with the given name and environment.
         *
         * @param name        the world name
         * @param environment the world environment (NORMAL, NETHER, THE_END)
         * @return the created world
         */
        World createWorld(String name, World.Environment environment);

        /**
         * Create a new void world with the given name and environment.
         * <p>
         * The world uses {@link WorldType#FLAT} with an empty chunk generator so no
         * terrain is generated. Suitable for structure building and resource definition.
         *
         * @param name        the world name
         * @param environment the world environment (NORMAL, NETHER, THE_END)
         * @return the created world
         */
        World createVoidWorld(String name, World.Environment environment);

        /**
         * Create a world by copying from an existing source world (template).
         *
         * @param name       the new world name
         * @param sourceName the name of the source world to copy from
         * @return the created world
         */
        World createCopy(String name, String sourceName);

        /**
         * Get an existing world by name.
         *
         * @param name the world name
         * @return the world, or {@code null} if it does not exist or is not loaded
         */
        @Nullable World getWorld(String name);

        /** Get all currently loaded worlds. */
        List<World> getLoadedWorlds();

        /** Unload a world by name. Returns true if successful. */
        boolean unloadWorld(String name);

        /** Unload a world. Returns true if successful. */
        boolean unloadWorld(World world);

        /** Delete a world from disk by name. */
        void deleteWorld(String name);

        /** Load a chunk at the given coordinates. Returns true if successful. */
        boolean loadChunk(World world, int x, int z);

        /** Check if a chunk at the given coordinates is loaded. */
        boolean chunkLoaded(World world, int x, int z);

        /**
         * Drop an item stack at the given position in a world.
         *
         * @param worldName    the world name
         * @param pos          the position to drop the item
         * @param materialName the material name (e.g. "RED_WOOL")
         * @param count        the item count
         * @return the UUID of the dropped {@link org.bukkit.entity.Item}, or {@code null} if it could not be dropped
         */
        @Nullable UUID dropItem(String worldName, BlockPos pos, String materialName, int count);

        /**
         * Drop an item stack at the given position in a world.
         *
         * @param worldName the world name
         * @param pos       the position to drop the item at
         * @param item      the item stack reference
         * @return the UUID of the dropped {@link org.bukkit.entity.Item}, or {@code null} if it could not be dropped
         */
        @Nullable UUID dropItem(String worldName, BlockPos pos, ItemStackRef item);

        /** Check if a world is currently loaded. */
        boolean isWorldLoaded(String name);

        /** Get the spawn position of a world. */
        BlockPos getSpawnPos(String worldName);

        /** Get the world container directory. */
        File getWorldContainer();

        /**
         * Set the absolute daytime of the world (ticks since dawn:
         * 0 = sunrise, 6000 = noon, 18000 = midnight).
         * <p>
         * Worlds whose dimension type has no world clock (e.g. the nether, the
         * end, or custom flat/void worlds on MC 26.1+) cannot track a time of
         * day; setting the time is then a silent no-op.
         */
        void setTime(String worldName, long time);

        /** Toggle the daylight cycle game rule (whether time advances by itself). */
        void setDoDaylightCycle(String worldName, boolean enabled);

        /** Toggle the natural mob spawning game rule. */
        void setDoMobSpawning(String worldName, boolean enabled);

        /** Toggle the keep-inventory game rule. */
        void setKeepInventory(String worldName, boolean enabled);

        /** Set the world's difficulty. */
        void setDifficulty(String worldName, org.bukkit.Difficulty difficulty);

        /** Set whether it is currently raining (storming) in the world. */
        void setStorm(String worldName, boolean storm);

        /** Set whether it is currently thundering in the world. */
        void setThundering(String worldName, boolean thundering);

        /** Toggle the weather cycle game rule (whether weather changes by itself). */
        void setDoWeatherCycle(String worldName, boolean enabled);
    }

    /**
     * NBT structure operations.
     * <p>
     * Abstracts loading, saving, creating, and placing vanilla structures
     * using string-based IDs for cached structures.
     * Nested (rather than a top-level interface) so that the covariant override of
     * {@link net.klaaswhite.c2w.domain.ops.MinecraftManager#structures()} resolves
     * correctly at call sites.
     */
    interface Structures extends net.klaaswhite.c2w.domain.ops.MinecraftManager.Structures {

        /**
         * Load a structure from an NBT file on disk and return an ID for it.
         *
         * @param file the NBT file to load
         * @return the structure ID string, or throws if loading failed
         * @throws IOException if loading fails
         */
        String loadStructure(File file) throws IOException;

        /** Save a structure identified by ID to an NBT file on disk. */
        void saveStructure(File file, String structureId) throws IOException;

        /**
         * Place a structure in the world at the given position.
         *
         * @param structureId    the cached structure ID
         * @param worldName      the target world name
         * @param pos            the origin position
         * @param includeEntities whether to include entities
         * @param rotation       the rotation
         * @param mirror         the mirror
         * @param palette        the palette index (-1 for default)
         * @param integrity      the integrity (0.0–1.0)
         * @param random         the random instance
         */
        void place(String structureId, String worldName, BlockPos pos, boolean includeEntities,
                   StructureRotation rotation, Mirror mirror, int palette, float integrity, Random random);

        /**
         * Fill a structure at the given origin and size.
         *
         * @param structureId    the cached structure ID
         * @param worldName      the target world name
         * @param origin         the origin position
         * @param size           the size
         * @param includeEntities whether to include entities
         */
        void fill(String structureId, String worldName, BlockPos origin, BlockPos size, boolean includeEntities);

        /**
         * Create a new empty structure and return its ID.
         */
        String createStructure();

        /**
         * Create a structure by capturing blocks from the world.
         *
         * @param worldName the source world
         * @param origin    the origin position
         * @param size      the size
         * @return the structure ID, or null if capturing failed
         */
        @Nullable String createStructure(String worldName, BlockPos origin, BlockPos size);

        /**
         * Get the size of a cached structure.
         *
         * @param structureId the cached structure ID
         * @return int array {width, height, depth}, or null if not found
         */
        @Nullable int[] getSize(String structureId);

        /**
         * Check if a tile entity at the given coordinates has a specific key value.
         *
         * @param structureId the cached structure ID
         * @param x           block x
         * @param y           block y
         * @param z           block z
         * @return the value, or null if not a tile entity or key not found
         */
        boolean isTileEntity(String structureId, int x, int y, int z);

        /**
         * Get a tile entity string value by key.
         *
         * @param structureId the cached structure ID
         * @param x           block x
         * @param y           block y
         * @param z           block z
         * @param key         the key
         * @return the value, or null if not found
         */
        @Nullable String getTileEntityString(String structureId, int x, int y, int z, String key);

        /**
         * Set the size of a structure.
         */
        void setSize(String structureId, int x, int y, int z);

        /**
         * Copy a block from the world into the structure at the given coordinates.
         */
        void setBlockFromWorld(String structureId, String worldName, int x, int y, int z);
    }
}
