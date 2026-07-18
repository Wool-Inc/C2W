package net.klaaswhite.c2w.domain.config;

import net.klaaswhite.c2w.domain.model.MapLayout;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Abstraction over loading layout files from disk. Implementations may use
 * Bukkit's YamlConfiguration or an in-memory source for testing.
 */
public interface LayoutFileLoader {

    /** Load a named layout from a file, or return null on failure. */
    @Nullable MapLayout loadLayout(File file, String name);
}