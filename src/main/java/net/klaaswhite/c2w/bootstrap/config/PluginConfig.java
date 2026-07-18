package net.klaaswhite.c2w.bootstrap.config;

import net.klaaswhite.c2w.domain.config.ConfigAccess;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class PluginConfig {

    public ConfigAccess configAccess;
    private YamlConfiguration config;

    public PluginConfig(ConfigAccess configAccess) {
        this.configAccess = configAccess;
        this.config = load();
    }

    /** Constructor for tests — uses in-memory config with no file I/O. */
    PluginConfig(YamlConfiguration config) {
        this.configAccess = null;
        this.config = config;
    }

    private YamlConfiguration load() {
        var dataFolder = configAccess.getDataFolder();
        var configFile = new File(dataFolder, "config.yml");

        if (!configFile.exists()) {
            configAccess.saveResource("config.yml", false);
        }

        return YamlConfiguration.loadConfiguration(configFile);
    }

    public void reload() {
        this.config = load();
    }

    public YamlConfiguration getRaw() {
        return config;
    }

    public String getGameOverTarget() {
        return config.getString("gameOverTarget", "draft");
    }

    public boolean isLobbyAutoJoin() {
        return config.getBoolean("lobbyAutoJoin", true);
    }

    public boolean isOverrideOnStartup() {
        return config.getBoolean("overrideOnStartup", false);
    }
}
