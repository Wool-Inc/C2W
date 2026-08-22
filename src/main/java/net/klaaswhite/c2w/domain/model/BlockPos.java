package net.klaaswhite.c2w.domain.model;

public record BlockPos(int x, int y, int z) {
    public BlockPos add(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }
}
