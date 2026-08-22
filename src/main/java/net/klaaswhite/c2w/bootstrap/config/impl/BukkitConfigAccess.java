package net.klaaswhite.c2w.bootstrap.config.impl;

import net.klaaswhite.c2w.domain.config.ConfigAccess;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Production implementation of {@link ConfigAccess} that delegates to
 * a {@link JavaPlugin}'s built-in config API.
 */
public class BukkitConfigAccess implements ConfigAccess {

    private final JavaPlugin plugin;

    public BukkitConfigAccess(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public void saveResource(String path, boolean replace) {
        plugin.saveResource(path, replace);
    }

    @Override
    public @Nullable String getString(String path) {
        return plugin.getConfig().getString(path);
    }

    @Override
    public int getInt(String path) {
        return plugin.getConfig().getInt(path);
    }

    @Override
    public boolean getBoolean(String path) {
        return plugin.getConfig().getBoolean(path);
    }

    @Override
    public double getDouble(String path) {
        return plugin.getConfig().getDouble(path);
    }

    @Override
    public void set(String path, Object value) {
        plugin.getConfig().set(path, value);
    }

    @Override
    public void save() {
        plugin.saveConfig();
    }

    @Override
    public boolean contains(String path) {
        return plugin.getConfig().contains(path);
    }

    @Override
    public List<String> getKeys(String path) {
        var section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return List.of();
        return section.getKeys(false).stream().toList();
    }

    @Override
    public @Nullable Object get(String path) {
        return plugin.getConfig().get(path);
    }
}