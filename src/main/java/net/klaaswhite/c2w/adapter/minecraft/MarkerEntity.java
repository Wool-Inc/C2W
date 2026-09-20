package net.klaaswhite.c2w.adapter.minecraft;

import net.klaaswhite.c2w.domain.model.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Represents a single marker entity in the world.
 * <p>
 * Markers are Bukkit {@code Marker} entities identified by a {@code map_marker}
 * persistent data key. Each marker has a position, a visible name, and
 * persistent data for custom attributes. Implements the domain
 * {@link MarkerEntity} interface.
 */
public interface MarkerEntity extends net.klaaswhite.c2w.domain.model.MarkerEntity {

    /** Stable identity of the backing marker entity. */
    UUID getUniqueId();

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