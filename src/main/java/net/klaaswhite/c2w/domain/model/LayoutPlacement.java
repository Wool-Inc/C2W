package net.klaaswhite.c2w.domain.model;

import org.jspecify.annotations.Nullable;

public record LayoutPlacement(String typeName, String id, int x, int y, int z, @Nullable String spawnTeam, float yaw) {
    /** Convenience constructor with no spawn team and no rotation. */
    public LayoutPlacement(String typeName, String id, int x, int y, int z) {
        this(typeName, id, x, y, z, null, 0f);
    }

    /** Convenience constructor with spawn team but no rotation. */
    public LayoutPlacement(String typeName, String id, int x, int y, int z, @Nullable String spawnTeam) {
        this(typeName, id, x, y, z, spawnTeam, 0f);
    }
}
