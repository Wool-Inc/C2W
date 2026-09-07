package net.klaaswhite.c2w.bootstrap.minecraft;

import java.lang.reflect.Field;
import net.klaaswhite.c2w.adapter.minecraft.*;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ItemStackRef;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import net.klaaswhite.c2w.domain.model.Mirror;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.StructureRotation;
import net.klaaswhite.c2w.domain.model.BossBarStyle;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.TrialSpawner;
import org.bukkit.block.Vault;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.spawner.SpawnerEntry;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;
import org.jspecify.annotations.Nullable;

import org.bukkit.util.BlockVector;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Concrete implementation of {@link MinecraftManager} that wraps the Bukkit API.
 * <p>
 * Uses inner classes to implement each sub-interface. All Bukkit calls go through
 * this single class, making it the sole bridge between domain/adapter code and
 * the Bukkit runtime.
 */
public class BukkitMinecraftManager implements MinecraftManager, AutoCloseable {

    private final JavaPlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey trialEntityKey;
    private final Map<String, Structure> structureCache = new HashMap<>();
    private final AtomicLong structureIdCounter = new AtomicLong(0);
    /**
     * Worlds already flagged as having no world clock (MC 26.1+). Used to avoid
     * logging the same warning on every EnvironmentManager heartbeat tick.
     */
    private final Set<String> warnedNoClockWorlds = new HashSet<>();
    /**
     * Tracks the armor stand used to display a carried wool above a player:
     * key = player UUID, value = armor stand UUID. Used to clear the display
     * when the wool is dropped or captured.
     */
    private final Map<UUID, UUID> carriedWoolStandUuids = new HashMap<>();
    private final Map<String, List<ItemStack>> trialSpawnEggs = new HashMap<>();
    private final Map<String, BukkitTask> trialSpawnTasks = new HashMap<>();
    private final Map<String, List<ItemStack>> trialVaultLoot = new HashMap<>();
    private net.klaaswhite.c2w.adapter.managers.EventManager eventManager; // ponytail: late-set, wired by App after both exist

    /**
     * The GameRule static fields were renamed between API builds: the running server
     * (paper-api 26.1.2 build.61) exposes {@code DO_DAYLIGHT_CYCLE} /
     * {@code DO_WEATHER_CYCLE}, while the spigot-api SNAPSHOT the plugin compiles
     * against exposes {@code ADVANCE_TIME} / {@code ADVANCE_WEATHER}. Referencing
     * either constant directly would throw {@link NoSuchFieldError} on the other API,
     * so the correct field is located at runtime (new name first, old name as
     * fallback) and cached here.
     */
    private static final GameRule<Boolean> DAYLIGHT_CYCLE_RULE =
            resolveGameRule("ADVANCE_TIME", "DO_DAYLIGHT_CYCLE");
    private static final GameRule<Boolean> WEATHER_CYCLE_RULE =
            resolveGameRule("ADVANCE_WEATHER", "DO_WEATHER_CYCLE");
    private static final GameRule<Boolean> MOB_SPAWNING_RULE =
            resolveGameRule("SPAWN_MOBS", "DO_MOB_SPAWNING");
    private static final GameRule<Boolean> KEEP_INVENTORY_RULE =
            resolveGameRule("KEEP_INVENTORY");

    @SuppressWarnings("unchecked")
    private static @Nullable GameRule<Boolean> resolveGameRule(String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Field field = GameRule.class.getField(fieldName);
                return (GameRule<Boolean>) field.get(null);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // try the next candidate name
            }
        }
        Bukkit.getLogger().warning("[c2w] None of the GameRule fields "
                + String.join(", ", fieldNames) + " exist in this server's API; "
                + "daylight/weather cycle pinning is disabled");
        return null;
    }

    public BukkitMinecraftManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "map_marker");
        this.trialEntityKey = new NamespacedKey(plugin, "trial_spawner_id");
    }

    public void setEventManager(net.klaaswhite.c2w.adapter.managers.EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public void pushEvent(net.klaaswhite.c2w.domain.events.C2WEvent event) {
        if (eventManager != null) eventManager.pushInternalEvent(event);
    }

    @Override
    public Players players() {
        return new BukkitPlayers();
    }

    @Override
    public MinecraftManager.Worlds worlds() {
        return new BukkitWorlds();
    }

    @Override
    public Server server() {
        return new BukkitServer();
    }

    @Override
    public Markers markers() {
        return new BukkitMarkers();
    }

    @Override
    public Scoreboards scoreboards() {
        return new BukkitScoreboards();
    }

    @Override
    public MinecraftManager.Structures structures() {
        return new BukkitStructures();
    }

    @Override
    public Blocks blocks() {
        return new BukkitBlocks();
    }

    @Override
    public Plugin plugin() {
        return new BukkitPlugin();
    }

    @Override
    public TrialSpawners trialSpawners() {
        return new BukkitTrialSpawners();
    }

    @Override
    public BossBars bossBars() {
        return new BukkitBossBars();
    }

    @Override
    public void close() {
        // Remove any armor stands still displaying carried wools.
        for (UUID standId : new HashSet<>(carriedWoolStandUuids.values())) {
            var entity = Bukkit.getEntity(standId);
            if (entity instanceof ArmorStand stand) {
                stand.remove();
            }
        }
        carriedWoolStandUuids.clear();
    }

    // =========================================================================
    // BukkitBossBars
    // =========================================================================

    private class BukkitBossBars implements BossBars {

        @Override
        public BossBar createBossBar(String title, WoolColor color, BossBarStyle style) {
            org.bukkit.boss.BarColor bukkitColor = toBukkitBarColor(color);
            org.bukkit.boss.BarStyle bukkitStyle = toBukkitBarStyle(style);
            org.bukkit.boss.BossBar bar = Bukkit.createBossBar(title, bukkitColor, bukkitStyle);
            return new BukkitBossBar(bar);
        }

        private org.bukkit.boss.BarColor toBukkitBarColor(WoolColor color) {
            return switch (color) {
                case RED -> org.bukkit.boss.BarColor.RED;
                case GREEN -> org.bukkit.boss.BarColor.GREEN;
                case BLUE -> org.bukkit.boss.BarColor.BLUE;
                case YELLOW -> org.bukkit.boss.BarColor.YELLOW;
            };
        }

        private org.bukkit.boss.BarStyle toBukkitBarStyle(BossBarStyle style) {
            return switch (style) {
                case SOLID -> org.bukkit.boss.BarStyle.SOLID;
                case SEGMENTED_6 -> org.bukkit.boss.BarStyle.SEGMENTED_6;
                case SEGMENTED_10 -> org.bukkit.boss.BarStyle.SEGMENTED_10;
                case SEGMENTED_12 -> org.bukkit.boss.BarStyle.SEGMENTED_12;
                case SEGMENTED_20 -> org.bukkit.boss.BarStyle.SEGMENTED_20;
            };
        }
    }

    private record BukkitBossBar(org.bukkit.boss.BossBar bar) implements BossBar {

        @Override
        public void setVisible(boolean visible) {
            bar.setVisible(visible);
        }

        @Override
        public void setProgress(double progress) {
            bar.setProgress(progress);
        }

        @Override
        public void addPlayer(String playerName) {
            Player p = Bukkit.getPlayerExact(playerName);
            if (p != null) {
                bar.addPlayer(p);
            }
        }
    }

    // =========================================================================
    // BukkitPlayers
    // =========================================================================

    private class BukkitPlayers implements Players {

        @Override
        public @Nullable Player getHandle(String playerName) {
            return Bukkit.getPlayer(playerName);
        }

        @Override
        public BlockPos getPosition(String playerName) {
            Player player = Bukkit.getPlayer(playerName);
            if (player == null) {
                return new BlockPos(0, 0, 0);
            }
            return new BlockPos(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
        }

        @Override
        public @Nullable BlockPos getTargetBlock(String playerName, int range) {
            Player player = Bukkit.getPlayer(playerName);
            if (player == null) {
                return null;
            }
            Block block = player.getTargetBlockExact(range);
            if (block == null) {
                return null;
            }
            return new BlockPos(block.getX(), block.getY(), block.getZ());
        }

        @Override
        public @Nullable String getWorldName(String playerName) {
            Player player = Bukkit.getPlayer(playerName);
            if (player == null) {
                return null;
            }
            return player.getWorld().getName();
        }

        @Override
        public void teleportToWorld(String playerName, BlockPos pos, String worldName) {
            try {
                Player player = Bukkit.getPlayer(playerName);
                World world = Bukkit.getWorld(worldName);
                if (player == null || world == null) {
                    return;
                }
                player.teleport(new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to teleport player " + playerName, e);
            }
        }

        @Override
        public void setRespawnLocation(String playerName, BlockPos pos, String worldName, boolean force) {
            try {
                Player player = Bukkit.getPlayer(playerName);
                World world = Bukkit.getWorld(worldName);
                if (player == null || world == null) {
                    return;
                }
                player.setRespawnLocation(new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5), force);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to set respawn location for player " + playerName, e);
            }
        }

        @Override
        public void sendMessage(String playerName, String message) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.sendMessage(message);
            }
        }

        @Override
        public void giveItemStack(String playerName, ItemStackRef ref) {
            try {
                Player player = Bukkit.getPlayer(playerName);
                if (player == null) {
                    return;
                }
                Material material = Material.getMaterial(ref.materialName());
                if (material == null) {
                    return;
                }
                player.getInventory().addItem(new ItemStack(material, ref.count()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to give item to player " + playerName, e);
            }
        }

        @Override
        public void clearInventory(String playerName) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.getInventory().clear();
            }
        }

        @Override
        public void addPotionEffect(String playerName, PotionEffectType type, int duration, int amplifier) {
            try {
                Player player = Bukkit.getPlayer(playerName);
                if (player != null) {
                    player.addPotionEffect(new PotionEffect(type, duration, amplifier));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to add potion effect to player " + playerName, e);
            }
        }

        @Override
        public boolean hasPotionEffect(String playerName, PotionEffectType type) {
            Player player = Bukkit.getPlayer(playerName);
            return player != null && player.hasPotionEffect(type);
        }

        @Override
        public void removePotionEffects(String playerName) {
            Player player = Bukkit.getPlayer(playerName);
            if (player == null) {
                return;
            }
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
        }

        @Override
        public void setHealth(String playerName, double health) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.setHealth(health);
            }
        }

        @Override
        public void respawn(String playerName) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, player.spigot()::respawn);
            }
        }

        @Override
        public void setFoodLevel(String playerName, int food) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.setFoodLevel(food);
            }
        }

        @Override
        public void setSaturation(String playerName, float saturation) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.setSaturation(saturation);
            }
        }

        @Override
        public void setWoolDisplay(String playerName, ItemStackRef item) {
            Player player = Bukkit.getPlayer(playerName);
            if (player == null) return;
            if (item.isEmpty()) {
                removeCarriedWoolStand(player);
            } else {
                Material mat = Material.matchMaterial(item.materialName());
                if (mat != null) {
                    spawnCarriedWoolStand(player, new ItemStack(mat, item.count()));
                }
            }
        }

        /**
         * Spawn a hidden, small armor stand that wears the item on its head and
         * rides as a passenger on the given player, so the wool visibly floats
         * above the carrier. Any previously-displayed stand for this player is
         * replaced.
         */
        private void spawnCarriedWoolStand(Player player, ItemStack item) {
            removeCarriedWoolStand(player);
            var loc = player.getLocation();
            var stand = player.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setSmall(true);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setCanPickupItems(false);
                as.setArms(false);
                as.setBasePlate(false);
                as.getEquipment().setHelmet(item);
                as.setMarker(true);
            });
            player.addPassenger(stand);
            carriedWoolStandUuids.put(player.getUniqueId(), stand.getUniqueId());
        }

        /** Remove and forget the armor stand currently displaying a wool for the given player. */
        private void removeCarriedWoolStand(Player player) {
            UUID standId = carriedWoolStandUuids.remove(player.getUniqueId());
            if (standId == null) return;
            var entity = Bukkit.getEntity(standId);
            if (entity instanceof ArmorStand stand) {
                player.removePassenger(stand);
                stand.remove();
            }
        }

        @Override
        public void sendActionBar(String playerName, String message) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
            }
        }

        @Override
        public void sendTitle(String playerName, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            }
        }

        @Override
        public void playSound(String playerName, String soundName, float volume, float pitch) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                try {
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (IllegalArgumentException e) {
                    // Unknown sound name — ignore
                }
            }
        }

        @Override
        public void setGameMode(String playerName, String gameMode) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                try {
                    player.setGameMode(GameMode.valueOf(gameMode));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown game mode: " + gameMode);
                }
            }
        }

        @Override
        public void spawnParticles(String playerName, String worldName, int x, int y, int z, String particleType, int count, int r, int g, int b) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            try {
                Particle particle = Particle.valueOf(particleType);
                Particle.DustOptions dustOptions = new Particle.DustOptions(org.bukkit.Color.fromRGB(r, g, b), 1);
                world.spawnParticle(particle, x + 0.5, y + 0.5, z + 0.5, count, dustOptions);
            } catch (IllegalArgumentException e) {
                // Unknown particle type — ignore
            }
        }
    }

    // =========================================================================
    // BukkitWorlds
    // =========================================================================

    private class BukkitWorlds implements MinecraftManager.Worlds {

        @Override
        public @Nullable World createWorld(String name, World.Environment environment) {
            try {
                return new WorldCreator(name).environment(environment).createWorld();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create world " + name, e);
                return null;
            }
        }

        @Override
        public @Nullable World createVoidWorld(String name, World.Environment environment) {
            try {
                return new WorldCreator(name)
                        .environment(environment)
                        .type(WorldType.FLAT)
                        .generator(new VoidChunkGenerator())
                        .createWorld();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create void world " + name, e);
                return null;
            }
        }

        @Override
        public @Nullable World createCopy(String name, String sourceName) {
            try {
                // Unload source if loaded
                World sourceWorld = Bukkit.getWorld(sourceName);
                if (sourceWorld != null) {
                    Bukkit.unloadWorld(sourceWorld, false);
                }

                // Copy world folder
                File container = Bukkit.getWorldContainer();
                File sourceFolder = new File(container, sourceName);
                File targetFolder = new File(container, name);

                if (!sourceFolder.exists()) {
                    plugin.getLogger().warning("Source world folder not found: " + sourceFolder);
                    return null;
                }

                copyFolder(sourceFolder, targetFolder);

                // Load the copied world
                return new WorldCreator(name).createWorld();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to copy world " + sourceName + " to " + name, e);
                return null;
            }
        }

        @Override
        public @Nullable World getWorld(String name) {
            return Bukkit.getWorld(name);
        }

        @Override
        public List<World> getLoadedWorlds() {
            return Bukkit.getWorlds();
        }

        @Override
        public boolean unloadWorld(String name) {
            try {
                World world = Bukkit.getWorld(name);
                if (world == null) {
                    return false;
                }
                return Bukkit.unloadWorld(world, false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to unload world " + name, e);
                return false;
            }
        }

        @Override
        public boolean unloadWorld(World world) {
            try {
                return Bukkit.unloadWorld(world, false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to unload world", e);
                return false;
            }
        }

        @Override
        public void deleteWorld(String name) {
            try {
                unloadWorld(name);
                File container = Bukkit.getWorldContainer();
                // Legacy path: <container>/<name>/
                File folder = new File(container, name);
                if (folder.exists()) {
                    deleteFolder(folder);
                }
                // Paper 1.21+ path: <container>/dimensions/minecraft/<name>/
                File paperFolder = new File(container,
                        "dimensions" + File.separator + "minecraft" + File.separator + name);
                if (paperFolder.exists()) {
                    deleteFolder(paperFolder);
                }
                // Paper 1.21+ per-world dimension path:
                // <container>/<world>/dimensions/minecraft/<name>/
                File[] topDirs = container.listFiles(File::isDirectory);
                if (topDirs != null) {
                    for (File topDir : topDirs) {
                        File perWorldFolder = new File(topDir,
                                "dimensions" + File.separator + "minecraft"
                                        + File.separator + name);
                        if (perWorldFolder.exists()) {
                            deleteFolder(perWorldFolder);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete world " + name, e);
            }
        }

        @Override
        public boolean loadChunk(World world, int x, int z) {
            try {
                return world.getChunkAt(x, z).load();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load chunk at " + x + "," + z, e);
                return false;
            }
        }

        @Override
        public boolean chunkLoaded(World world, int x, int z) {
            try {
                return world.isChunkLoaded(x, z);
            } catch (Exception e) {
                return false;
            }
        }

        private void copyFolder(File source, File target) throws IOException {
            if (source.isDirectory()) {
                if (!target.exists() && !target.mkdirs()) {
                    throw new IOException("Cannot create directory " + target);
                }
                String[] children = source.list();
                if (children != null) {
                    for (String child : children) {
                        copyFolder(new File(source, child), new File(target, child));
                    }
                }
            } else {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        private void deleteFolder(File folder) throws IOException {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteFolder(file);
                    } else {
                        Files.delete(file.toPath());
                    }
                }
            }
            Files.delete(folder.toPath());
        }

        @Override
        public boolean isWorldLoaded(String name) {
            return Bukkit.getWorld(name) != null;
        }

        @Override
        public BlockPos getSpawnPos(String worldName) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return new BlockPos(0, 65, 0);
            }
            Location spawn = world.getSpawnLocation();
            return new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        }

        @Override
        public File getWorldContainer() {
            return Bukkit.getWorldContainer();
        }

        @Override
        public void setTime(String worldName, long time) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return;
            }
            try {
                world.setTime(time);
            } catch (IllegalArgumentException e) {
                // Dimension types without a world clock (nether, end, and
                // custom flat/void worlds) cannot have their time set on
                // MC 26.1+. Time pinning is a no-op for them, so swallow the
                // documented exception and warn once per world instead of
                // spamming every heartbeat tick.
                if (warnedNoClockWorlds.add(worldName)) {
                    plugin.getLogger().log(Level.FINE,
                            "World " + worldName + " has no world clock; skipping time pinning", e);
                }
            }
        }

        @Override
        public void setDoDaylightCycle(String worldName, boolean enabled) {
            World world = Bukkit.getWorld(worldName);
            if (world != null && DAYLIGHT_CYCLE_RULE != null) {
                world.setGameRule(DAYLIGHT_CYCLE_RULE, enabled);
            }
        }

        @Override
        public void setDoMobSpawning(String worldName, boolean enabled) {
            World world = Bukkit.getWorld(worldName);
            if (world != null && MOB_SPAWNING_RULE != null) {
                world.setGameRule(MOB_SPAWNING_RULE, enabled);
            }
        }

        @Override
        public void setKeepInventory(String worldName, boolean enabled) {
            World world = Bukkit.getWorld(worldName);
            if (world != null && KEEP_INVENTORY_RULE != null) {
                world.setGameRule(KEEP_INVENTORY_RULE, enabled);
            }
        }

        @Override
        public void setDifficulty(String worldName, org.bukkit.Difficulty difficulty) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.setDifficulty(difficulty);
            }
        }

        @Override
        public void setStorm(String worldName, boolean storm) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.setStorm(storm);
            }
        }

        @Override
        public void setThundering(String worldName, boolean thundering) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.setThundering(thundering);
            }
        }

        @Override
        public void setDoWeatherCycle(String worldName, boolean enabled) {
            World world = Bukkit.getWorld(worldName);
            if (world != null && WEATHER_CYCLE_RULE != null) {
                world.setGameRule(WEATHER_CYCLE_RULE, enabled);
            }
        }

        @Override
        public @Nullable UUID dropItem(String worldName, BlockPos pos, String materialName, int count) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            Material mat = Material.matchMaterial(materialName);
            if (mat == null) return null;
            var item = world.dropItem(new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5), new ItemStack(mat, count), i -> {
                i.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                i.setGravity(false);
            });
            if (item == null) return null;
            item.setPersistent(true);
            item.setUnlimitedLifetime(true);
            return item.getUniqueId();
        }

        @Override
        public @Nullable UUID dropItem(String worldName, BlockPos pos, ItemStackRef item) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            Material mat = Material.matchMaterial(item.materialName());
            if (mat == null) return null;
            var dropped = world.dropItem(new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5), new ItemStack(mat, item.count()), i -> {
                i.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                i.setGravity(false);
            });
            if (dropped == null) return null;
            dropped.setPersistent(true);
            dropped.setUnlimitedLifetime(true);
            return dropped.getUniqueId();
        }

        /** A chunk generator that produces empty chunks (void world). */
        private static class VoidChunkGenerator extends ChunkGenerator {
        }
    }

    // =========================================================================
    // BukkitServer
    // =========================================================================

    private class BukkitServer implements Server {

        @Override
        public void broadcastMessage(String message) {
            Bukkit.broadcastMessage(message);
        }

        @Override
        public List<String> getOnlinePlayerNames() {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
        }

        @Override
        public int getOnlinePlayerCount() {
            return Bukkit.getOnlinePlayers().size();
        }

        @Override
        public org.bukkit.Server bukkit() {
            return Bukkit.getServer();
        }
    }

    // =========================================================================
    // BukkitMarkers
    // =========================================================================

    private class BukkitMarkers implements Markers {

        @Override
        public String getMarkerKey() {
            // Return the key portion only. Callers wrap this in
            // `new NamespacedKey(plugin, key)`, so a full "c2w:map_marker"
            // string would be illegal (':' is not allowed in the key part).
            return markerKey.getKey();
        }

        @Override
        public List<MarkerEntity> getMarkersInWorld(String worldName) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return List.of();
            }
            return world.getEntitiesByClass(Marker.class).stream()
                    .filter(m -> m.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING))
                    .map(m -> (MarkerEntity) new BukkitMarkerEntity(m,
                            m.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING),
                            plugin))
                    .toList();
        }

        @Override
        public @Nullable MarkerEntity spawnMarker(String worldName, BlockPos pos) {
            try {
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    return null;
                }
                Location loc = new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5);
                Marker marker = world.spawn(loc, Marker.class);
                return new BukkitMarkerEntity(marker, null, plugin);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to spawn marker at " + pos, e);
                return null;
            }
        }

        @Override
        public boolean removeMarker(String worldName, String name) {
            try {
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    return false;
                }
                for (Marker marker : world.getEntitiesByClass(Marker.class)) {
                    String data = marker.getPersistentDataContainer()
                            .get(markerKey, PersistentDataType.STRING);
                    if (name.equals(data)) {
                        marker.remove();
                        return true;
                    }
                }
                return false;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove marker " + name, e);
                return false;
            }
        }

        @Override
        public List<ManagedMarker> findMarkersInWorld(String worldName, String key, @Nullable String valuePrefix) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return List.of();
            var result = new java.util.ArrayList<ManagedMarker>();
            for (Marker marker : world.getEntitiesByClass(Marker.class)) {
                String val = marker.getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
                if (val == null) continue;
                if (valuePrefix != null && !val.startsWith(valuePrefix)) continue;
                var entity = new BukkitMarkerEntity(marker, val, plugin);
                result.add(new ManagedMarker(entity, val));
            }
            return result;
        }
    }

    // =========================================================================
    // BukkitMarkerEntity
    // =========================================================================

    private record BukkitMarkerEntity(Marker bukkit, @Nullable String name, JavaPlugin plugin) implements MarkerEntity {

        @Override
        public BlockPos getPosition() {
            Location loc = bukkit.getLocation();
            return new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }

        @Override
        public String getName() {
            return name != null ? name : "";
        }

        @Override
        public @Nullable String getPersistentData(String key) {
            try {
                return bukkit.getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void setPersistentData(String key, String value) {
            bukkit.getPersistentDataContainer()
                    .set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        }

        @Override
        public void remove() {
            bukkit.remove();
        }
    }

    // =========================================================================
    // BukkitScoreboards
    // =========================================================================

    private class BukkitScoreboards implements Scoreboards {

        @Override
        public Team createTeam(String teamName) {
            Scoreboard board = getMainScoreboard();
            Team team = board.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
            team = board.registerNewTeam(teamName);
            // Set team colour so players see the right colour in tab list, name tags, etc.
            team.setColor(switch (teamName) {
                case "Red" -> org.bukkit.ChatColor.RED;
                case "Blue" -> org.bukkit.ChatColor.BLUE;
                case "Spectator" -> org.bukkit.ChatColor.GRAY;
                default -> org.bukkit.ChatColor.WHITE;
            });
            return team;
        }

        @Override
        public @Nullable Team getTeam(String teamName) {
            return getMainScoreboard().getTeam(teamName);
        }

        @Override
        public boolean removeTeam(String teamName) {
            try {
                Team team = getMainScoreboard().getTeam(teamName);
                if (team == null) {
                    return false;
                }
                team.unregister();
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove team " + teamName, e);
                return false;
            }
        }

        @Override
        public void addPlayerToTeam(String playerName, String teamName) {
            try {
                Scoreboard board = getMainScoreboard();
                Team team = board.getTeam(teamName);
                if (team == null) {
                    team = board.registerNewTeam(teamName);
                    team.setColor(switch (teamName) {
                        case "Red" -> org.bukkit.ChatColor.RED;
                        case "Blue" -> org.bukkit.ChatColor.BLUE;
                        case "Spectator" -> org.bukkit.ChatColor.GRAY;
                        default -> org.bukkit.ChatColor.WHITE;
                    });
                }
                Player player = Bukkit.getPlayer(playerName);
                if (player != null) {
                    team.addPlayer(player);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to add player " + playerName + " to team " + teamName, e);
            }
        }

        @Override
        public void removePlayerFromTeam(String playerName, String teamName) {
            try {
                Scoreboard board = getMainScoreboard();
                Team team = board.getTeam(teamName);
                if (team == null) {
                    return;
                }
                Player player = Bukkit.getPlayer(playerName);
                if (player != null) {
                    team.removePlayer(player);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove player " + playerName + " from team " + teamName, e);
            }
        }

        @Override
        public Scoreboard getMainScoreboard() {
            return Bukkit.getScoreboardManager().getMainScoreboard();
        }

        @Override
        public Objective registerSidebarObjective(String name, String displayName) {
            Scoreboard board = getMainScoreboard();
            Objective objective = board.getObjective(name);
            if (objective == null) {
                objective = board.registerNewObjective(name, "dummy", displayName);
            } else {
                objective.setDisplayName(displayName);
            }
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            return objective;
        }

        @Override
        public void unregisterObjective(String name) {
            Scoreboard board = getMainScoreboard();
            Objective objective = board.getObjective(name);
            if (objective != null) {
                objective.unregister();
            }
        }
    }

    // =========================================================================
    // BukkitStructures
    // =========================================================================

    private class BukkitStructures implements MinecraftManager.Structures {

        @Override
        public String loadStructure(File file) throws IOException {
            Structure structure = Bukkit.getStructureManager().loadStructure(file);
            String id = "struct-" + structureIdCounter.incrementAndGet();
            structureCache.put(id, structure);
            return id;
        }

        @Override
        public void saveStructure(File file, String structureId) throws IOException {
            Structure structure = structureCache.get(structureId);
            if (structure == null) {
                structure = loadExisting(file);
                if (structure == null) throw new IOException("No structure data for " + structureId);
            }
            Bukkit.getStructureManager().saveStructure(file, structure);
        }

        @Override
        public void place(String structureId, String worldName, BlockPos pos, boolean includeEntities,
                          StructureRotation rotation, Mirror mirror, int palette, float integrity, Random random) {
            Structure structure = structureCache.get(structureId);
            if (structure == null) return;
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            org.bukkit.util.BlockVector origin = new org.bukkit.util.BlockVector(pos.x(), pos.y(), pos.z());
            org.bukkit.block.structure.Mirror bukkitMirror = switch (mirror) {
                case NONE -> org.bukkit.block.structure.Mirror.NONE;
                case LEFT_RIGHT -> org.bukkit.block.structure.Mirror.LEFT_RIGHT;
                case FRONT_BACK -> org.bukkit.block.structure.Mirror.FRONT_BACK;
            };
            org.bukkit.block.structure.StructureRotation bukkitRotation = switch (rotation) {
                case NONE -> org.bukkit.block.structure.StructureRotation.NONE;
                case CLOCKWISE_90 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_90;
                case CLOCKWISE_180 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_180;
                case COUNTERCLOCKWISE_90 -> org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;
            };
            structure.place(world, origin, includeEntities, bukkitRotation, bukkitMirror, palette, integrity, random);
        }

        @Override
        public void fill(String structureId, String worldName, BlockPos origin, BlockPos size, boolean includeEntities) {
            Structure structure = structureCache.get(structureId);
            if (structure == null) return;
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            Location loc = new Location(world, origin.x(), origin.y(), origin.z());
            structure.fill(loc, new org.bukkit.util.BlockVector(size.x(), size.y(), size.z()), includeEntities);
        }

        @Override
        public String createStructure() {
            Structure structure = Bukkit.getStructureManager().createStructure();
            String id = "struct-" + structureIdCounter.incrementAndGet();
            structureCache.put(id, structure);
            return id;
        }

        @Override
        public @Nullable String createStructure(String worldName, BlockPos origin, BlockPos size) {
            Structure structure = Bukkit.getStructureManager().createStructure();
            String id = "struct-" + structureIdCounter.incrementAndGet();
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            Location loc = new Location(world, origin.x(), origin.y(), origin.z());
            structure.fill(loc, new org.bukkit.util.BlockVector(size.x(), size.y(), size.z()), true);
            structureCache.put(id, structure);
            return id;
        }

        @Override
        public @Nullable int[] getSize(String structureId) {
            Structure structure = structureCache.get(structureId);
            if (structure == null) return null;
            return new int[]{structure.getSize().getBlockX(), structure.getSize().getBlockY(), structure.getSize().getBlockZ()};
        }

        @Override
        public boolean isTileEntity(String structureId, int x, int y, int z) {
            return getTileEntityString(structureId, x, y, z, "") != null;
        }

        @Override
        public @Nullable String getTileEntityString(String structureId, int x, int y, int z, String key) {
            Structure structure = structureCache.get(structureId);
            if (structure == null) return null;
            try {
                for (Palette palette : structure.getPalettes()) {
                    for (var blockState : palette.getBlocks()) {
                        if (blockState.getX() == x && blockState.getY() == y && blockState.getZ() == z) {
                            if (blockState instanceof TileState tile) {
                                NamespacedKey nsKey = key.contains(":") ? NamespacedKey.fromString(key) : new NamespacedKey(plugin, key);
                                if (nsKey == null) return null;
                                return tile.getPersistentDataContainer().get(nsKey, PersistentDataType.STRING);
                            }
                            return null;
                        }
                    }
                }
            } catch (Exception e) {
                // Not a tile entity
            }
            return null;
        }

        @Override
        public void setSize(String structureId, int x, int y, int z) {
            Structure structure = structureCache.get(structureId);
            if (structure == null) return;
            // Structures in Bukkit API do not support resizing once created; this is a no-op
        }

        @Override
        public void setBlockFromWorld(String structureId, String worldName, int x, int y, int z) {
            // Not supported by Bukkit Structure API at the individual block level.
            // Use createStructure(worldName, origin, size) to capture via fill() instead.
        }

        private @Nullable Structure loadExisting(File file) throws IOException {
            return Bukkit.getStructureManager().loadStructure(file);
        }
    }

    // =========================================================================
    // BukkitBlocks
    // =========================================================================

    private class BukkitBlocks implements Blocks {

        @Override
        public Material getBlockType(String worldName, BlockPos pos) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return Material.AIR;
            }
            return world.getBlockAt(pos.x(), pos.y(), pos.z()).getType();
        }

        @Override
        public void setBlock(String worldName, BlockPos pos, Material material) {
            try {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    world.getBlockAt(pos.x(), pos.y(), pos.z()).setType(material);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to set block at " + pos, e);
            }
        }

        @Override
        public BlockData getBlockData(String worldName, BlockPos pos) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return Material.AIR.createBlockData();
            }
            return world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData();
        }
    }

    // =========================================================================
    // BukkitTrialSpawners
    // =========================================================================

    private class BukkitTrialSpawners implements TrialSpawners {

        @Override
        public boolean isTrialSpawner(String worldName, BlockPos pos) {
            World world = Bukkit.getWorld(worldName);
            return world != null && world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == Material.TRIAL_SPAWNER;
        }

        @Override
        public boolean isVault(String worldName, BlockPos pos) {
            World world = Bukkit.getWorld(worldName);
            return world != null && world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == Material.VAULT;
        }

        @Override
        public void configureSpawner(String worldName, BlockPos pos, List<ItemStack> spawnEggs) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (!(block.getState() instanceof TrialSpawner trialSpawner)) return;

            List<SpawnerEntry> entries = new java.util.ArrayList<>();
            int totalSpawns = 0;
            SpawnRule defaultRule = new SpawnRule(0, 15, 0, 15);
            for (ItemStack egg : spawnEggs) {
                if (egg == null || egg.getType().isAir()
                        || !(egg.getItemMeta() instanceof SpawnEggMeta eggMeta)) {
                    plugin.getLogger().warning("[TrialSpawner] Ignoring non-spawn-egg source item at " + pos);
                    continue;
                }
                var snapshot = eggMeta.getSpawnedEntity();
                if (snapshot == null) {
                    plugin.getLogger().warning("[TrialSpawner] Spawn egg has no entity snapshot at " + pos);
                    continue;
                }
                int amount = Math.max(1, egg.getAmount());
                entries.add(new SpawnerEntry(snapshot, amount, defaultRule.clone()));
                totalSpawns += amount;
            }
            if (entries.isEmpty()) {
                plugin.getLogger().warning("[TrialSpawner] No valid spawn eggs configured at " + pos);
                return;
            }

            String key = locationKey(worldName, pos);
            List<ItemStack> copiedEggs = new java.util.ArrayList<>();
            for (ItemStack egg : spawnEggs) copiedEggs.add(egg.clone());
            trialSpawnEggs.put(key, copiedEggs);

            var configuration = trialSpawner.getNormalConfiguration();
            configuration.setPotentialSpawns(entries);
            configuration.setBaseSpawnsBeforeCooldown(1);
            configuration.setAdditionalSpawnsBeforeCooldown(0);
            configuration.setBaseSimultaneousEntities(1);
            configuration.setAdditionalSimultaneousEntities(0);
            trialSpawner.update(true, false);
        }

        @Override
        public int startExactTrial(String worldName, BlockPos pos, String trialId) {
            return startExactTrial(worldName, pos, trialId, Integer.MAX_VALUE, null, null);
        }

        @Override
        public int startExactTrial(String worldName, BlockPos pos, String trialId, int amount,
                                   java.util.function.Consumer<Entity> onSpawn, Runnable onComplete) {
            String key = locationKey(worldName, pos);
            List<ItemStack> source = trialSpawnEggs.get(key);
            World world = Bukkit.getWorld(worldName);
            if (source == null || source.isEmpty() || world == null) return 0;

            BukkitTask oldTask = trialSpawnTasks.remove(key);
            if (oldTask != null) oldTask.cancel();

            List<org.bukkit.entity.EntitySnapshot> queue = new java.util.ArrayList<>();
            for (ItemStack egg : source) {
                if (!(egg.getItemMeta() instanceof SpawnEggMeta eggMeta)) continue;
                var snapshot = eggMeta.getSpawnedEntity();
                if (snapshot == null) continue;
                for (int i = 0; i < Math.max(1, egg.getAmount()); i++) queue.add(snapshot);
            }
            if (queue.isEmpty()) return 0;
            java.util.Collections.shuffle(queue);
            if (amount != Integer.MAX_VALUE && amount < queue.size()) {
                queue = new java.util.ArrayList<>(queue.subList(0, Math.max(0, amount)));
            }
            if (queue.isEmpty()) return 0;
            final List<org.bukkit.entity.EntitySnapshot> spawnQueue = queue;

            Location spawnLocation = world.getBlockAt(pos.x(), pos.y(), pos.z()).getLocation().add(0.5, 1, 0.5);
            int queueSize = spawnQueue.size();
            int[] index = {0};
            BukkitTask[] task = new BukkitTask[1];
            task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (index[0] >= spawnQueue.size()) {
                    trialSpawnTasks.remove(key);
                    task[0].cancel();
                    if (onComplete != null) onComplete.run();
                    return;
                }
                Entity entity = spawnQueue.get(index[0]++).createEntity(spawnLocation);
                if (entity != null) {
                    tagEntity(entity, trialId);
                    if (onSpawn != null) onSpawn.accept(entity);
                }
            }, 0L, 5L);
            trialSpawnTasks.put(key, task[0]);
            return queueSize;
        }

        @Override
        public void cancelExactTrial(String worldName, BlockPos pos) {
            BukkitTask task = trialSpawnTasks.remove(locationKey(worldName, pos));
            if (task != null) task.cancel();
        }

        @Override
        public void configureVault(String worldName, BlockPos pos, List<ItemStack> loot) {
            World world = Bukkit.getWorld(worldName);
            if (world == null || loot.isEmpty()) return;
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (!(block.getState() instanceof Vault vault)) return;

            List<ItemStack> copiedLoot = new java.util.ArrayList<>();
            for (ItemStack item : loot) {
                if (item != null && !item.getType().isAir()) copiedLoot.add(item.clone());
            }
            if (copiedLoot.isEmpty()) return;
            trialVaultLoot.put(locationKey(worldName, pos), copiedLoot);
            vault.setKeyItem(new ItemStack(Material.TRIAL_KEY, 1));
            vault.update(true, false);
        }

        @Override
        public boolean claimVault(Player player, String worldName, BlockPos pos) {
            List<ItemStack> loot = trialVaultLoot.get(locationKey(worldName, pos));
            if (loot == null || loot.isEmpty()) return false;
            ItemStack key = new ItemStack(Material.TRIAL_KEY, 1);
            var inventory = player.getInventory();
            if (!inventory.containsAtLeast(key, 1)) return false;

            ItemStack reward = loot.get(ThreadLocalRandom.current().nextInt(loot.size())).clone();
            var leftovers = inventory.addItem(reward);
            if (!leftovers.isEmpty()) return false;
            var removed = inventory.removeItem(key);
            if (!removed.isEmpty()) {
                inventory.removeItem(reward);
                inventory.addItem(key);
                return false;
            }
            return true;
        }

        @Override
        public void clearConfiguredTrialData() {
            for (BukkitTask task : trialSpawnTasks.values()) task.cancel();
            trialSpawnTasks.clear();
            trialSpawnEggs.clear();
            trialVaultLoot.clear();
        }

        @Override
        public void tagEntity(org.bukkit.entity.Entity entity, String trialId) {
            entity.getPersistentDataContainer().set(trialEntityKey,
                    PersistentDataType.STRING, trialId);
        }

        @Override
        public @Nullable String getEntityTag(org.bukkit.entity.Entity entity) {
            return entity.getPersistentDataContainer().get(trialEntityKey, PersistentDataType.STRING);
        }

        @Override
        public boolean giveTrialKey(Player player) {
            var leftovers = player.getInventory().addItem(new ItemStack(Material.TRIAL_KEY, 1));
            return leftovers.isEmpty();
        }

        private String locationKey(String worldName, BlockPos pos) {
            return worldName + "@" + pos.x() + "," + pos.y() + "," + pos.z();
        }
    }

    // =========================================================================
    // BukkitPlugin
    // =========================================================================

    private class BukkitPlugin implements Plugin {

        @Override
        public JavaPlugin getPlugin() {
            return plugin;
        }

        @Override
        public File getDataFolder() {
            return plugin.getDataFolder();
        }

        @Override
        public Logger getLogger() {
            return plugin.getLogger();
        }
    }
}