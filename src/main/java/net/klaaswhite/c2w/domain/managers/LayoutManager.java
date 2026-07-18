package net.klaaswhite.c2w.domain.managers;

import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.config.LayoutFileLoader;
import net.klaaswhite.c2w.domain.ops.FileSystemOps;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager for discovering and caching map layouts. Layouts are parsed from
 * YAML files in the {@code layouts/} directory on first access and reused;
 * the cache is cleared when the plugin is reset.
 */
public class LayoutManager {

    private final File layoutsDir;
    private final LayoutFileLoader fileLoader;
    private final FileSystemOps fs;
    private final Map<String, MapLayout> cache = new HashMap<>();

    public LayoutManager(File layoutsDir, LayoutFileLoader fileLoader, FileSystemOps fs) {
        this.layoutsDir = layoutsDir;
        this.fileLoader = fileLoader;
        this.fs = fs;
    }

    public List<String> getLayoutNames() {
        return new ArrayList<>(discoverEditorLayouts());
    }

    public List<String> getEditorLayoutNames() {
        return discoverEditorLayouts();
    }

    public @Nullable MapLayout getLayout(String name) {
        return cache.computeIfAbsent(name, n -> tryLoadEditorLayout(n));
    }

    public void clearCache() {
        cache.clear();
    }

    private List<String> discoverEditorLayouts() {
        List<String> names = new ArrayList<>();
        if (layoutsDir == null || !fs.isDirectory(layoutsDir)) return names;
        File[] files = fs.listFiles(layoutsDir, ".yml");
        if (files == null) return names;
        for (File file : files) {
            String name = file.getName();
            names.add(name.substring(0, name.length() - 4));
        }
        return names;
    }

    private @Nullable MapLayout tryLoadEditorLayout(String name) {
        File file = new File(layoutsDir, name + ".yml");
        if (!fs.isFile(file)) return null;
        return fileLoader.loadLayout(file, name);
    }
}
