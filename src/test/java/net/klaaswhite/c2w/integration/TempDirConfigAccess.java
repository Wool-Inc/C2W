package net.klaaswhite.c2w.integration;

import net.klaaswhite.c2w.domain.config.ConfigAccess;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * {@link ConfigAccess} backed by a real temporary directory on disk. Used by
 * integration tests that need {@code FolderStructureTypeConfig} to read a real
 * {@code structures/<type>/structure.yml} (it uses Bukkit's
 * {@code YamlConfiguration.loadConfiguration(File)} directly, not the
 * in-memory config access). All other config methods are no-ops / defaults.
 */
public class TempDirConfigAccess implements ConfigAccess {

    private final File dataFolder;

    public TempDirConfigAccess(File dataFolder) {
        this.dataFolder = dataFolder;
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create temp data folder: " + dataFolder);
        }
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public void saveResource(String path, boolean replace) {
        // no-op for tests
    }

    @Override
    public @Nullable String getString(String path) {
        return null;
    }

    @Override
    public int getInt(String path) {
        return 0;
    }

    @Override
    public boolean getBoolean(String path) {
        return false;
    }

    @Override
    public double getDouble(String path) {
        return 0.0;
    }

    @Override
    public void set(String path, Object value) {
        // no-op
    }

    @Override
    public void save() {
        // no-op
    }

    @Override
    public boolean contains(String path) {
        return false;
    }

    @Override
    public List<String> getKeys(String path) {
        return List.of();
    }

    @Override
    public @Nullable Object get(String path) {
        return null;
    }
}
