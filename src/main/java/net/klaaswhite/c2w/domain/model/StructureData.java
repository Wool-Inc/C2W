package net.klaaswhite.c2w.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * A single structure data holder. Defined by its type name, an opaque id,
 * and two corner positions.
 */
public class StructureData {

    private final String typeName;
    private final String id;
    private final BlockPos corner1;
    private final BlockPos corner2;

    public StructureData(
            String typeName,
            String id,
            BlockPos corner1,
            BlockPos corner2
    ) {
        this.typeName = typeName;
        this.id = id;
        this.corner1 = corner1;
        this.corner2 = corner2;
    }

    /**
     * The dynamic type name (e.g. "dungeon").
     * This is the canonical identifier for structures.
     */
    public String getTypeName() {
        return typeName;
    }

    public String getId() {
        return id;
    }

    public BlockPos getCorner1() {
        return corner1;
    }

    public BlockPos getCorner2() {
        return corner2;
    }

    /** Width (X span) of the bounding box, always non-negative. */
    public int getWidth() {
        return Math.abs(corner2.x() - corner1.x()) + 1;
    }

    /** Height (Y span) of the bounding box, always non-negative. */
    public int getHeight() {
        return Math.abs(corner2.y() - corner1.y()) + 1;
    }

    /** Depth (Z span) of the bounding box, always non-negative. */
    public int getDepth() {
        return Math.abs(corner2.z() - corner1.z()) + 1;
    }
}
