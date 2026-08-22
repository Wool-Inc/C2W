package net.klaaswhite.c2w.domain.config;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Abstraction over configuration key-value access.
 * Production wraps Bukkit's YamlConfiguration; tests use in-memory config.
 */
public interface ConfigAccess {

    /** Return the plugin data folder. */
    File getDataFolder();

    /** Copy a bundled resource into the data folder if it doesn't exist. */
    void saveResource(String path, boolean replace);

    /** Get a string value at the given path, or null if not present. */
    @Nullable String getString(String path);

    /** Get an integer value at the given path. Returns 0 if not present. */
    int getInt(String path);

    /** Get a boolean value at the given path. Returns false if not present. */
    boolean getBoolean(String path);

    /** Get a double value at the given path. Returns 0.0 if not present. */
    double getDouble(String path);

    /** Set a value at the given path. */
    void set(String path, Object value);

    /** Persist the configuration to disk. */
    void save();

    /** Return true if the given path has a value set. */
    boolean contains(String path);

    /** Return the child keys at the given path, or an empty list. */
    List<String> getKeys(String path);

    /** Get the raw object at the given path, or null if not present. */
    @Nullable Object get(String path);
}