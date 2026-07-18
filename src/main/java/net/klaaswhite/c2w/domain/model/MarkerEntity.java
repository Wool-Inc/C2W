package net.klaaswhite.c2w.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * Domain abstraction for a single marker entity in the world.
 * <p>
 * Markers are identified by a persistent data key (e.g. {@code map_marker}).
 * Pure domain — no Bukkit imports. The adapter layer provides the concrete
 * implementation backed by a Bukkit {@code Marker} entity.
 */
public interface MarkerEntity {

    /** Get the marker's current position in the world. */
    BlockPos getPosition();

    /** Get the marker's in-game name. */
    String getName();

    /**
     * Get a persistent data value by key.
     *
     * @param key the data key
     * @return the value, or {@code null} if not set
     */
    @Nullable String getPersistentData(String key);

    /** Set a persistent data value. */
    void setPersistentData(String key, String value);

    /** Remove this marker entity from the world. */
    void remove();
}
