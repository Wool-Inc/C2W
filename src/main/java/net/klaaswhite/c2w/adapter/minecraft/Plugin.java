package net.klaaswhite.c2w.adapter.minecraft;

import java.io.File;
import java.util.logging.Logger;

/**
 * Plugin access operations.
 * <p>
 * Provides access to the Bukkit {@link org.bukkit.plugin.java.JavaPlugin}
 * instance, the plugin's data folder, and its logger.
 */
public interface Plugin {

    /** Get the Bukkit JavaPlugin instance for C2W. */
    org.bukkit.plugin.java.JavaPlugin getPlugin();

    /** Get the plugin's data folder ({@code plugins/c2w/}). */
    File getDataFolder();

    /** Get the plugin's logger. */
    Logger getLogger();
}