package net.klaaswhite.c2w.integration;

import net.klaaswhite.c2w.adapter.minecraft.*;
import net.klaaswhite.c2w.adapter.minecraft.MarkerEntity;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.events.C2WEvent;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.BossBarStyle;
import net.klaaswhite.c2w.domain.model.ItemStackRef;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import net.klaaswhite.c2w.domain.model.Mirror;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.StructureRotation;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * In-memory {@link MinecraftManager} implementation for headless integration
 * tests. Wires the real plugin managers together without a running Bukkit
 * server. State is held in plain Java collections; the large Bukkit abstract
 * types ({@link World}, {@link Team}, {@link Scoreboard}, {@link Player}) are
 * faked with Mockito mocks whose behaviour is driven by the in-memory state.
 * <p>
 * The {@link #pushEvent(C2WEvent)} method delegates to the headless
 * {@link net.klaaswhite.c2w.adapter.managers.EventManager} supplied via
 * {@link #setEventManager}.
 */
public class FakeMinecraftManager implements MinecraftManager {

    private final Map<String, FakeWorld> worlds = new HashMap<>();
    private final Map<String, FakePlayer> players = new HashMap<>();
    private final Map<String, FakeMarkerEntity> markers = new HashMap<>();
    private final Map<String, FakeStructure> structures = new HashMap<>();
    private final Map<String, Material> blocks = new HashMap<>();
    private final List<String> broadcasts = new ArrayList<>();

    private net.klaaswhite.c2w.adapter.managers.EventManager eventManager;
    private final File dataFolder;

    public FakeMinecraftManager(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void setEventManager(net.klaaswhite.c2w.adapter.managers.EventManager eventManager) {
        this.eventManager = eventManager;
    }

    // --- Test helpers ---

    public FakeWorld createFakeWorld(String name) {
        var w = new FakeWorld(name);
        worlds.put(name, w);
        return w;
    }

    public FakePlayer createFakePlayer(String name) {
        var fp = new FakePlayer(name);
        fp.bukkitPlayer = BukkitProxies.newPlayer(name, fp.uuid, fp.worldName, fp.position);
        players.put(name, fp);
        return fp;
    }

    public FakeMarkerEntity addMarker(String worldName, BlockPos pos, String markerKey, String markerValue) {
        var m = new FakeMarkerEntity(worldName, pos, markerKey, markerValue);
        markers.put(markerValue + "@" + worldName + "@" + pos.x() + "," + pos.y() + "," + pos.z(), m);
        return m;
    }

    public List<String> getBroadcasts() {
        return broadcasts;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    // --- MinecraftManager (adapter) ---

    @Override public Players players() { return new FakePlayers(); }
    @Override public MinecraftManager.Worlds worlds() { return new FakeWorlds(); }
    @Override public Server server() { return new FakeServer(); }
    @Override public Markers markers() { return new FakeMarkers(); }
    @Override public Scoreboards scoreboards() { return new FakeScoreboards(); }
    @Override public MinecraftManager.Structures structures() { return new FakeStructures(); }
    @Override public Blocks blocks() { return new FakeBlocks(); }
    @Override public BossBars bossBars() { return new FakeBossBars(); }
    @Override public Plugin plugin() { return new FakePlugin(); }
    @Override public TrialSpawners trialSpawners() { return new FakeTrialSpawners(); }

    @Override
    public void pushEvent(C2WEvent event) {
        if (eventManager != null) eventManager.pushInternalEvent(event);
    }

    // ========================================================================
    // Players
    // ========================================================================

    public static class FakePlayer {
        public final String name;
        public String worldName;
        public BlockPos position;
        public final List<String> messages = new ArrayList<>();
        public final List<String> actionBars = new ArrayList<>();
        public final List<String> titles = new ArrayList<>();
        public ItemStackRef woolDisplay;
        public final UUID uuid = UUID.randomUUID();
        public Player bukkitPlayer;
        FakePlayer(String name) { this.name = name; this.worldName = "c2w_game"; this.position = new BlockPos(0, 65, 0); }
    }

    private class FakePlayers implements Players {
        @Override public Player getHandle(String playerName) {
            var p = players.get(playerName);
            return p != null ? p.bukkitPlayer : null;
        }
        @Override public BlockPos getPosition(String playerName) {
            var p = players.get(playerName);
            return p != null ? p.position : new BlockPos(0, 65, 0);
        }
        @Override public @Nullable BlockPos getTargetBlock(String playerName, int range) { return null; }
        @Override public @Nullable String getWorldName(String playerName) {
            var p = players.get(playerName);
            return p != null ? p.worldName : null;
        }
        @Override public void teleportToWorld(String playerName, BlockPos pos, String worldName) {
            var p = players.get(playerName);
            if (p != null) { p.position = pos; p.worldName = worldName; }
        }
        @Override public void setRespawnLocation(String playerName, BlockPos pos, String worldName, boolean force) {
            // no-op in tests
        }
        @Override public void sendMessage(String playerName, String message) {
            var p = players.get(playerName);
            if (p != null) p.messages.add(message);
        }
        @Override public void giveItemStack(String playerName, ItemStackRef item) {}
        @Override public void clearInventory(String playerName) {}
        @Override public void addPotionEffect(String playerName, PotionEffectType type, int duration, int amplifier) {}
        @Override public boolean hasPotionEffect(String playerName, PotionEffectType type) { return false; }
        @Override public void removePotionEffects(String playerName) {}
        @Override public void setHealth(String playerName, double health) {}
        @Override public void respawn(String playerName) {}
        @Override public void setFoodLevel(String playerName, int food) {}
        @Override public void setSaturation(String playerName, float saturation) {}
        @Override public void setWoolDisplay(String playerName, ItemStackRef item) {
            var p = players.get(playerName);
            if (p != null) p.woolDisplay = item;
        }
        @Override public void sendActionBar(String playerName, String message) {
            var p = players.get(playerName);
            if (p != null) p.actionBars.add(message);
        }
        @Override public void sendTitle(String playerName, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            var p = players.get(playerName);
            if (p != null) p.titles.add(title + " | " + subtitle);
        }
        @Override public void playSound(String playerName, String soundName, float volume, float pitch) {}
        @Override public void setGameMode(String playerName, String gameMode) {}
        @Override public void spawnParticles(String playerName, String worldName, int x, int y, int z,
                                           String particleType, int count, int r, int g, int b) {}
    }

    // ========================================================================
    // Worlds
    // ========================================================================

    public static class FakeWorld {
        public final String name;
        public BlockPos spawnLocation = new BlockPos(0, 65, 0);
        FakeWorld(String name) { this.name = name; }
    }

    private World mockWorld(String name, BlockPos spawn) {
        return BukkitProxies.newWorld(name, spawn);
    }

    private class FakeWorlds implements MinecraftManager.Worlds {
        @Override public @Nullable World createWorld(String name, World.Environment environment) {
            return mockWorld(name, new BlockPos(0, 65, 0));
        }
        @Override public @Nullable World createVoidWorld(String name, World.Environment environment) {
            return mockWorld(name, new BlockPos(0, 65, 0));
        }
        @Override public @Nullable World createCopy(String name, String sourceName) {
            return mockWorld(name, new BlockPos(0, 65, 0));
        }
        @Override public @Nullable World getWorld(String name) {
            var fw = worlds.get(name);
            return fw != null ? mockWorld(fw.name, fw.spawnLocation) : null;
        }
        @Override public List<World> getLoadedWorlds() {
            return worlds.values().stream().map(fw -> mockWorld(fw.name, fw.spawnLocation)).toList();
        }
        @Override public boolean unloadWorld(String name) { worlds.remove(name); return true; }
        @Override public boolean unloadWorld(World world) { worlds.remove(world.getName()); return true; }
        @Override public void deleteWorld(String name) { worlds.remove(name); }
        @Override public boolean loadChunk(World world, int x, int z) { return true; }
        @Override public boolean chunkLoaded(World world, int x, int z) { return true; }
        @Override public @Nullable UUID dropItem(String worldName, BlockPos pos, String materialName, int count) {
            return null;
        }
        @Override public @Nullable UUID dropItem(String worldName, BlockPos pos, ItemStackRef item) {
            return dropItem(worldName, pos, item.materialName(), item.count());
        }
        @Override public boolean isWorldLoaded(String name) { return worlds.containsKey(name); }
        @Override public void setTime(String worldName, long time) {}
        @Override public void setDoDaylightCycle(String worldName, boolean enabled) {}
        @Override public void setDoMobSpawning(String worldName, boolean enabled) {}
        @Override public void setKeepInventory(String worldName, boolean enabled) {}
        @Override public void setDifficulty(String worldName, org.bukkit.Difficulty difficulty) {}
        @Override public void setStorm(String worldName, boolean storm) {}
        @Override public void setThundering(String worldName, boolean thundering) {}
        @Override public void setDoWeatherCycle(String worldName, boolean enabled) {}
        @Override public BlockPos getSpawnPos(String worldName) {
            var fw = worlds.get(worldName);
            return fw != null ? fw.spawnLocation : new BlockPos(0, 65, 0);
        }
        @Override public File getWorldContainer() { return dataFolder; }
    }

    // ========================================================================
    // Server
    // ========================================================================

    private class FakeServer implements Server {
        @Override public void broadcastMessage(String message) { broadcasts.add(message); }
        @Override public List<String> getOnlinePlayerNames() { return new ArrayList<>(players.keySet()); }
        @Override public int getOnlinePlayerCount() { return players.size(); }
        @Override public org.bukkit.Server bukkit() { return null; }
    }

    // ========================================================================
    // Markers
    // ========================================================================

    public class FakeMarkerEntity implements MarkerEntity {
        private final String worldName;
        private final BlockPos position;
        private final String markerValue;
        private final Map<String, String> pdc = new HashMap<>();

        FakeMarkerEntity(String worldName, BlockPos position, String markerKey, String markerValue) {
            this.worldName = worldName;
            this.position = position;
            this.markerValue = markerValue;
            this.pdc.put(markerKey, markerValue);
        }

        @Override public BlockPos getPosition() { return position; }
        @Override public String getName() { return markerValue; }
        @Override public @Nullable String getPersistentData(String key) { return pdc.get(key); }
        @Override public void setPersistentData(String key, String value) { pdc.put(key, value); }
        @Override public void remove() {
            markers.entrySet().removeIf(e -> e.getValue() == this);
        }
    }

    private class FakeMarkers implements Markers {
        @Override public String getMarkerKey() { return "map_marker"; }
        @Override public List<MarkerEntity> getMarkersInWorld(String worldName) {
            List<MarkerEntity> result = new ArrayList<>();
            for (var m : markers.values()) {
                if (m.worldName.equals(worldName)) result.add(m);
            }
            return result;
        }
        @Override public @Nullable MarkerEntity spawnMarker(String worldName, BlockPos pos) { return null; }
        @Override public boolean removeMarker(String worldName, String name) {
            return markers.entrySet().removeIf(e -> e.getValue().worldName.equals(worldName)
                    && e.getValue().markerValue.equals(name));
        }
        @Override public List<ManagedMarker> findMarkersInWorld(String worldName, String key, @Nullable String valuePrefix) {
            List<ManagedMarker> result = new ArrayList<>();
            for (var m : markers.values()) {
                if (!m.worldName.equals(worldName)) continue;
                String val = m.getPersistentData(key);
                if (val == null) continue;
                if (valuePrefix == null || val.startsWith(valuePrefix)) {
                    result.add(new ManagedMarker(m, val));
                }
            }
            return result;
        }
    }

    // ========================================================================
    // Scoreboards
    // ========================================================================

    public static class FakeTeam {
        public final String name;
        public final Set<String> members = new HashSet<>();
        FakeTeam(String name) { this.name = name; }
    }

    private class FakeScoreboards implements Scoreboards {
        private final Map<String, Team> createdTeams = new HashMap<>();

        @Override public Team createTeam(String teamName) {
            Team t = mock(Team.class);
            FakeTeam ft = new FakeTeam(teamName);
            when(t.getName()).thenReturn(teamName);
            when(t.getEntries()).thenAnswer(i -> new HashSet<>(ft.members));
            doAnswer(inv -> { ft.members.add(inv.getArgument(0)); return null; }).when(t).addEntry(anyString());
            doAnswer(inv -> { ft.members.remove(inv.getArgument(0)); return null; }).when(t).removeEntry(anyString());
            when(t.hasEntry(anyString())).thenAnswer(i -> ft.members.contains(i.getArgument(0)));
            doAnswer(inv -> null).when(t).unregister();
            createdTeams.put(teamName, t);
            return t;
        }
        @Override public @Nullable Team getTeam(String teamName) { return createdTeams.get(teamName); }
        @Override public boolean removeTeam(String teamName) { return createdTeams.remove(teamName) != null; }
        @Override public void addPlayerToTeam(String playerName, String teamName) {
            var t = createdTeams.get(teamName);
            if (t != null) t.addEntry(playerName);
        }
        @Override public void removePlayerFromTeam(String playerName, String teamName) {
            var t = createdTeams.get(teamName);
            if (t != null) t.removeEntry(playerName);
        }
        @Override public Scoreboard getMainScoreboard() {
            Scoreboard sb = mock(Scoreboard.class);
            when(sb.getTeams()).thenReturn(new HashSet<>(createdTeams.values()));
            return sb;
        }
        @Override public Objective registerSidebarObjective(String name, String displayName) {
            Objective obj = mock(Objective.class);
            when(obj.getName()).thenReturn(name);
            when(obj.getDisplayName()).thenReturn(displayName);
            Scoreboard sb = getMainScoreboard();
            when(obj.getScoreboard()).thenReturn(sb);
            when(obj.getScore(anyString())).thenAnswer(inv -> {
                Score s = mock(Score.class);
                when(s.getEntry()).thenReturn(inv.getArgument(0));
                return s;
            });
            when(sb.getObjective(name)).thenReturn(obj);
            return obj;
        }
        @Override public void unregisterObjective(String name) {
            // no-op in fake
        }
    }

    // ========================================================================
    // Structures
    // ========================================================================

    public static class FakeStructure {
        public final String id;
        public int[] size = {5, 5, 5};
        FakeStructure(String id) { this.id = id; }
    }

    private class FakeStructures implements MinecraftManager.Structures {
        @Override public String loadStructure(File file) throws IOException {
            String id = "struct-" + file.getName().replace(".nbt", "");
            structures.put(id, new FakeStructure(id));
            return id;
        }
        @Override public void saveStructure(File file, String structureId) throws IOException {}
        @Override public void place(String structureId, String worldName, BlockPos pos, boolean includeEntities,
                                   StructureRotation rotation, Mirror mirror, int palette, float integrity, Random random) {}
        @Override public void fill(String structureId, String worldName, BlockPos origin, BlockPos size, boolean includeEntities) {}
        @Override public String createStructure() {
            String id = "struct-" + UUID.randomUUID();
            structures.put(id, new FakeStructure(id));
            return id;
        }
        @Override public @Nullable String createStructure(String worldName, BlockPos origin, BlockPos size) {
            String id = "struct-" + UUID.randomUUID();
            structures.put(id, new FakeStructure(id));
            return id;
        }
        @Override public @Nullable int[] getSize(String structureId) {
            var s = structures.get(structureId);
            return s != null ? s.size : null;
        }
        @Override public boolean isTileEntity(String structureId, int x, int y, int z) { return false; }
        @Override public @Nullable String getTileEntityString(String structureId, int x, int y, int z, String key) { return null; }
        @Override public void setSize(String structureId, int x, int y, int z) {
            var s = structures.get(structureId);
            if (s != null) s.size = new int[]{x, y, z};
        }
        @Override public void setBlockFromWorld(String structureId, String worldName, int x, int y, int z) {}
    }

    // ========================================================================
    // Blocks
    // ========================================================================

    private class FakeBlocks implements Blocks {
        private String key(String worldName, BlockPos pos) {
            return worldName + "@" + pos.x() + "," + pos.y() + "," + pos.z();
        }
        @Override public Material getBlockType(String worldName, BlockPos pos) {
            return blocks.getOrDefault(key(worldName, pos), Material.AIR);
        }
        @Override public void setBlock(String worldName, BlockPos pos, Material material) {
            blocks.put(key(worldName, pos), material);
        }
        @Override public org.bukkit.block.data.BlockData getBlockData(String worldName, BlockPos pos) { return null; }
    }

    private class FakeTrialSpawners implements TrialSpawners {
        @Override public boolean isTrialSpawner(String worldName, BlockPos pos) { return false; }
        @Override public boolean isVault(String worldName, BlockPos pos) { return false; }
        @Override public void configureSpawner(String worldName, BlockPos pos,
                               java.util.List<org.bukkit.inventory.ItemStack> spawnEggs) {}
        @Override public int startExactTrial(String worldName, BlockPos pos, String trialId) { return 0; }
        @Override public int startExactTrial(String worldName, BlockPos pos, String trialId, int amount,
                                             java.util.function.Consumer<org.bukkit.entity.Entity> onSpawn,
                                             Runnable onComplete) {
            if (onComplete != null) onComplete.run();
            return 0;
        }
        @Override public void cancelExactTrial(String worldName, BlockPos pos) {}
        @Override public void configureVault(String worldName, BlockPos pos,
                             java.util.List<org.bukkit.inventory.ItemStack> loot) {}
        @Override public boolean claimVault(Player player, String worldName, BlockPos pos) { return false; }
        @Override public void clearConfiguredTrialData() {}
        @Override public void tagEntity(org.bukkit.entity.Entity entity, String trialId) {}
        @Override public @Nullable String getEntityTag(org.bukkit.entity.Entity entity) { return null; }
        @Override public boolean giveTrialKey(Player player) { return false; }
    }

    // ========================================================================
    // BossBars
    // ========================================================================

    private static class FakeBossBar implements BossBar {
        private boolean visible;
        private double progress;
        @Override public void setVisible(boolean visible) { this.visible = visible; }
        @Override public void setProgress(double progress) { this.progress = progress; }
        @Override public void addPlayer(String playerName) {}
    }

    private class FakeBossBars implements BossBars {
        @Override public BossBar createBossBar(String title, WoolColor color, BossBarStyle style) {
            return new FakeBossBar();
        }
    }

    // ========================================================================
    // Plugin
    // ========================================================================

    private class FakePlugin implements Plugin {
        @Override public org.bukkit.plugin.java.JavaPlugin getPlugin() { return null; }
        @Override public File getDataFolder() { return dataFolder; }
        @Override public java.util.logging.Logger getLogger() { return java.util.logging.Logger.getAnonymousLogger(); }
    }
}
