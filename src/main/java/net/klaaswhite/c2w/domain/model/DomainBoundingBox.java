package net.klaaswhite.c2w.domain.model;

/**
 * Axis-aligned bounding box for containment checks. Zero Bukkit imports.
 */
public record DomainBoundingBox(double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ) {

    public DomainBoundingBox(BlockPos pos1, BlockPos pos2) {
        this(
                Math.min(pos1.x(), pos2.x()), Math.min(pos1.y(), pos2.y()), Math.min(pos1.z(), pos2.z()),
                Math.max(pos1.x(), pos2.x()), Math.max(pos1.y(), pos2.y()), Math.max(pos1.z(), pos2.z())
        );
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
