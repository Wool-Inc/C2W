package net.klaaswhite.c2w.adapter.minecraft;

import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Marker entity operations.
 * <p>
 * Markers are Bukkit {@code Marker} entities with a persistent data key
 * {@code map_marker} (a {@code NamespacedKey}) that identifies them as
 * C2W markers. Marker names are plain strings following conventions like
 * {@code wool}, {@code spawnpoint}, {@code boundary-woolcap-pit-<1|2>}, etc.
 */
public interface Markers {

    /**
     * Get the {@code NamespacedKey} string used for marker persistent data.
     *
     * @return {@code "map_marker"}
     */
    String getMarkerKey();

    /** Get all marker entities in a world. */
    List<MarkerEntity> getMarkersInWorld(String worldName);

    /**
     * Spawn a new marker entity at the given position in a world.
     *
     * @return the spawned marker entity, or {@code null} if spawning failed
     */
    @Nullable MarkerEntity spawnMarker(String worldName, BlockPos pos);

    /** Remove a marker by name from a world. Returns true if successful. */
    boolean removeMarker(String worldName, String name);

    /**
     * Find markers in a world that have a PDC key matching and optional value prefix.
     *
     * @param worldName   the world name
     * @param key         the PDC key
     * @param valuePrefix optional prefix to filter marker values, or null for all
     * @return list of matching managed markers
     */
    List<ManagedMarker> findMarkersInWorld(String worldName, String key, @Nullable String valuePrefix);
}