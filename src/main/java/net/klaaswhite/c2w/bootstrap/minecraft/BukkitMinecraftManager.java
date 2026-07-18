package net.klaaswhite.c2w.bootstrap.minecraft;

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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
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
    private final Map<String, Structure> structureCache = new HashMap<>();
    private final AtomicLong structureIdCounter = new AtomicLong(0);
    private net.klaaswhite.c2w.adapter.managers.EventManager eventManager; // ponytail: late-set, wired by App after both exist

    public BukkitMinecraftManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "map_marker");
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
    public BossBars bossBars() {
        return new BukkitBossBars();
    }

    @Override
    public void close() {
        // No resources to clean up
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
        public void addPlayer(PlayerHandle player) {
            Object bp = player.getBukkitPlayer();
            if (bp instanceof org.bukkit.entity.Player p) {
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
        public void setFoodLevel(String playerName, int food) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.setFoodLevel(food);
            }
        }

        @Override
        public void setHelmet(String playerName, ItemStackRef item) {
            Player player = Bukkit.getPlayer(playerName);
            if (player == null) return;
            if (item.isEmpty()) {
                player.getInventory().setHelmet(null);
            } else {
                Material mat = Material.matchMaterial(item.materialName());
                if (mat != null) {
                    player.getInventory().setHelmet(new ItemStack(mat, item.count()));
                }
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
        public @Nullable UUID dropItem(String worldName, BlockPos pos, String materialName, int count) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            Material mat = Material.matchMaterial(materialName);
            if (mat != null) {
                return world.dropItemNaturally(new Location(world, pos.x(), pos.y(), pos.z()), new ItemStack(mat, count)).getUniqueId();
            }
            return null;
        }

        @Override
        public @Nullable UUID dropItem(String worldName, BlockPos pos, ItemStackRef item) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            Material mat = Material.matchMaterial(item.materialName());
            if (mat != null) {
                return world.dropItemNaturally(new Location(world, pos.x(), pos.y(), pos.z()), new ItemStack(mat, item.count())).getUniqueId();
            }
            return null;
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
            return markerKey.toString();
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
            return board.registerNewTeam(teamName);
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