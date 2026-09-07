package net.klaaswhite.c2w.bootstrap;

import net.klaaswhite.c2w.domain.config.YamlLayoutFileLoader;
import net.klaaswhite.c2w.domain.managers.NbtStructureSource;
import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.adapter.commands.C2WCommand;
import net.klaaswhite.c2w.adapter.commands.LayoutEditorCommand;
import net.klaaswhite.c2w.adapter.commands.MarkerCommand;
import net.klaaswhite.c2w.adapter.commands.StructureCommand;
import net.klaaswhite.c2w.adapter.commands.WorldCommand;
import net.klaaswhite.c2w.bootstrap.config.impl.BukkitConfigAccess;
import net.klaaswhite.c2w.bootstrap.config.PluginConfig;
import net.klaaswhite.c2w.adapter.managers.BoundaryManager;
import net.klaaswhite.c2w.adapter.managers.CommandManager;
import net.klaaswhite.c2w.adapter.managers.EntityManager;
import net.klaaswhite.c2w.adapter.managers.EventManager;
import net.klaaswhite.c2w.adapter.managers.EnvironmentManager;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.adapter.managers.GameManager;
import net.klaaswhite.c2w.adapter.managers.LayoutEditorManager;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.adapter.managers.MarkerManager;
import net.klaaswhite.c2w.adapter.managers.PlayerManager;
import net.klaaswhite.c2w.adapter.managers.ResourceManager;
import net.klaaswhite.c2w.adapter.managers.ScoreboardManager;
import net.klaaswhite.c2w.adapter.managers.SpawnerManager;
import net.klaaswhite.c2w.adapter.managers.StructureCreationManager;
import net.klaaswhite.c2w.adapter.managers.TeamSelectionManager;
import net.klaaswhite.c2w.adapter.managers.TrialSpawnerManager;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.adapter.managers.WorldManager;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.bootstrap.listeners.ChangeTeamPacketListener;
import net.klaaswhite.c2w.bootstrap.minecraft.BukkitMinecraftManager;
import net.klaaswhite.c2w.bootstrap.minecraft.BukkitWoolTimerScheduler;
import net.klaaswhite.c2w.bootstrap.ops.BukkitFileSystemOps;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;

public class App implements AutoCloseable {

    public final C2W plugin;
    public final Managers managers;
    public CommandManager commandManager;

    private final List<AutoCloseable> closeables = new ArrayList<>();

    public App(C2W plugin) {
        this.plugin = plugin;
        this.managers = new Managers(plugin);
        initManagers();
        initCommands();
    }

    private void initManagers() {
        this.managers.pluginConfig = new PluginConfig(new BukkitConfigAccess(plugin));
        extractDefaultResources();
        this.managers.eventManager = new EventManager(plugin, this.managers);

        // The MinecraftManager is created at the start and passed to all managers
        MinecraftManager mc = new BukkitMinecraftManager(plugin);
        this.managers.mc = mc;
        ((BukkitMinecraftManager) mc).setEventManager(this.managers.eventManager);
        var scheduler = new BukkitWoolTimerScheduler(plugin);

        this.managers.worldManager = new WorldManager(plugin, mc);

        // Heartbeat that pins world time/weather and grants every player night vision.
        this.managers.environmentManager = new EnvironmentManager(
            this.managers.worldManager, mc, scheduler,
                PotionEffectType.NIGHT_VISION);

        this.managers.entityManager = new EntityManager(this.managers.eventManager);
        this.managers.spawnerManager = new SpawnerManager(
            this.managers.eventManager, mc, scheduler);
        this.managers.playerManager = new PlayerManager(this.managers, mc);

        // Team selection: walking onto a draft-world platform assigns the player's team.
        this.managers.teamSelectionManager = new TeamSelectionManager(
                this.managers.eventManager,
                this.managers.worldManager,
                this.managers.playerManager,
                mc);

        // WoolTimer uses Scheduler interface instead of ServerOps — must be
        // created before MarkerManager (which passes it to MarkerEngine → Wool).
        WoolTimer woolTimer = new WoolTimer(scheduler);
        this.managers.woolTimer = woolTimer;

        this.managers.markerManager = new MarkerManager(this.managers, this.managers.eventManager, mc);

        var dataFolder = plugin.getDataFolder();

        var structureTypeConfig = new FolderStructureTypeConfig(new BukkitConfigAccess(plugin));
        this.managers.structureTypeConfig = structureTypeConfig;

        var fs = new BukkitFileSystemOps();
        var nbtSource = new NbtStructureSource(dataFolder, mc, structureTypeConfig, fs);
        this.managers.structureManager = new StructureManager(nbtSource);
        this.managers.structureCreationManager = new StructureCreationManager(
                plugin, this.managers.eventManager, this.managers.worldManager,
                structureTypeConfig, mc, dataFolder, fs);
        this.managers.resourceManager = new ResourceManager(
                plugin, this.managers.eventManager, this.managers.worldManager,
                structureTypeConfig, dataFolder, mc);
            this.managers.trialSpawnerManager = new TrialSpawnerManager(
                this.managers.eventManager, mc, structureTypeConfig, this.managers.spawnerManager);
        this.managers.layoutEditorManager = new LayoutEditorManager(
                plugin, this.managers.eventManager, this.managers.worldManager,
                this.managers.structureManager, structureTypeConfig, mc, dataFolder);

        this.managers.boundaryManager = new BoundaryManager(
                this.managers.eventManager,
                this.managers.markerManager,
                this.managers.playerManager,
            woolTimer,
            mc
        );

        LayoutManager layoutManager = new LayoutManager(
                new File(plugin.getDataFolder(), "layouts"), new YamlLayoutFileLoader(fs), fs);
        this.managers.layoutManager = layoutManager;

        this.managers.gameManager = new GameManager(
                plugin,
                this.managers.eventManager,
                this.managers.worldManager,
                layoutManager,
                this.managers.structureManager,
                mc,
                this.managers.structureCreationManager,
                this.managers.pluginConfig,
                this.managers.playerManager,
                structureTypeConfig
        );

        this.managers.scoreboardManager = new ScoreboardManager(
                this.managers.eventManager,
                mc,
                this.managers.gameManager,
                this.managers.markerManager,
                layoutManager,
                structureTypeConfig
        );

        this.managers.eventManager.registerPacketListener(
                new ChangeTeamPacketListener(plugin, this.managers.playerManager)
        );

        closeables.add(this.managers.eventManager);
        closeables.add(this.managers.boundaryManager);
        closeables.add(this.managers.teamSelectionManager);
        closeables.add(this.managers.playerManager);
        closeables.add(this.managers.worldManager);
        closeables.add(this.managers.environmentManager);
        closeables.add(this.managers.markerManager);
        closeables.add(this.managers.structureManager);
        closeables.add(this.managers.layoutEditorManager);
        closeables.add(this.managers.structureCreationManager);
        closeables.add(this.managers.resourceManager);
        closeables.add(this.managers.spawnerManager);
        closeables.add(this.managers.trialSpawnerManager);
        closeables.add(this.managers.gameManager);
        closeables.add(this.managers.scoreboardManager);
        closeables.add(this.managers.entityManager);
        closeables.add(this.managers.woolTimer);
    }

    private void initCommands() {
        this.commandManager = new CommandManager(
                plugin,
                new C2WCommand(plugin, this.managers.gameManager, this.managers.markerManager,
                        this.managers.layoutManager, this.managers.playerManager, v -> this.rebuild(),
                        this.managers.woolTimer, this.managers.mc),
                new MarkerCommand(plugin, this.managers.markerManager),
                new WorldCommand(plugin, this.managers.worldManager),
                new StructureCommand(plugin, this.managers.gameManager, this.managers.structureCreationManager,
                        this.managers.resourceManager, this.managers.structureTypeConfig, this.managers.structureManager,
                        this.managers.worldManager),
                new LayoutEditorCommand(plugin, this.managers.layoutEditorManager,
                        this.managers.layoutManager, this.managers.structureTypeConfig, this.managers.structureManager,
                        this.managers.worldManager, this.managers.gameManager)
        );
        closeables.add(commandManager);
    }

    public void rebuild() {
        var lobby = this.managers.worldManager != null ? this.managers.worldManager.getLobbyWorld().getWorld() : null;
        if (lobby != null) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                // Reset players to survival when returning them to the lobby so
                // ex-spectators don't stay in spectator mode in the lobby world.
                p.setGameMode(GameMode.SURVIVAL);
                p.teleport(lobby.getSpawnLocation());
            }
        }

        for (var i = closeables.size() - 1; i >= 0; i--) {
            try {
                closeables.get(i).close();
            } catch (Exception ignored) {
            }
        }
        closeables.clear();

        this.managers.eventManager = null;
        this.managers.boundaryManager = null;
        this.managers.markerManager = null;
        this.managers.playerManager = null;
        this.managers.teamSelectionManager = null;
        this.managers.structureManager = null;
        this.managers.structureCreationManager = null;
        this.managers.resourceManager = null;
        this.managers.trialSpawnerManager = null;
        this.managers.spawnerManager = null;
        this.managers.worldManager = null;
        this.managers.environmentManager = null;
        this.managers.gameManager = null;
        this.managers.scoreboardManager = null;
        this.managers.entityManager = null;
        this.managers.woolTimer = null;
        this.managers.layoutEditorManager = null;
        this.managers.layoutManager = null;
        this.commandManager = null;

        // mc, structureTypeConfig, pluginConfig are re-created in initManagers().

        initManagers();
        initCommands();
    }

    @Override
    public void close() {
        for (var i = closeables.size() - 1; i >= 0; i--) {
            try {
                closeables.get(i).close();
            } catch (Exception ignored) {
            }
        }
    }

    // ponytail: scans JAR for bundled resources instead of maintaining a manifest file.
    // Add/remove files in src/main/resources/{layouts,structures}/ — they're picked up automatically.
    private void extractDefaultResources() {
        boolean override = managers.pluginConfig.isOverrideOnStartup();
        var dataFolder = plugin.getDataFolder();

        // Resolve the JAR we are running from. In dev/test this may be a directory or null,
        // in which case we skip JAR enumeration and rely on saveResource's own fallback.
        java.io.File jar;
        try {
            var loc = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) return;
            jar = new java.io.File(java.net.URI.create(loc.toString()));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not resolve plugin JAR for resource extraction: " + e.getMessage());
            return;
        }
        if (!jar.isFile()) return; // running from a directory (dev/test) — nothing to extract

        try (var jf = new java.util.jar.JarFile(jar)) {
            var prefixes = new String[]{"layouts/", "structures/"};
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var name = entry.getName();
                if (entry.isDirectory() || name.endsWith(".gitkeep")) continue;
                boolean match = false;
                for (var prefix : prefixes) {
                    if (name.startsWith(prefix)) { match = true; break; }
                }
                if (!match) continue;

                var target = new File(dataFolder, name);
                if (override) {
                    plugin.saveResource(name, true);
                } else {
                    if (!target.exists()) {
                        target.getParentFile().mkdirs();
                        plugin.saveResource(name, false);
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to extract default resources: " + e.getMessage());
        }
    }
}
