package net.klaaswhite.c2w.domain.model;

/**
 * A single cell of a {@link MapLayout}. Carries the original grid
 * coordinates ({@code row}, {@code col}) and the type name the cell
 * represents, plus the world-space {@code worldPosition} the cell's
 * structure will be stamped at.
 * <p>
 * The {@code team} field is set for spawn cells to indicate which team
 * the spawn belongs to ({@code "Red"} or {@code "Blue"}). It is
 * {@code null} for non-spawn cells.
 */
public record LayoutCell(
        int row,
        int col,
        String typeName,
        BlockPos worldPosition,
        String team,
        float yaw
) {
    /** Create a non-spawn cell with no team and no rotation. */
    public static LayoutCell of(int row, int col, String typeName, BlockPos worldPosition) {
        return new LayoutCell(row, col, typeName, worldPosition, null, 0f);
    }

    /** Create a spawn cell for a specific team. */
    public static LayoutCell spawn(int row, int col, String typeName, BlockPos worldPosition, String team) {
        return new LayoutCell(row, col, typeName, worldPosition, team, 0f);
    }

    /** Create a cell with rotation. */
    public static LayoutCell of(int row, int col, String typeName, BlockPos worldPosition, float yaw) {
        return new LayoutCell(row, col, typeName, worldPosition, null, yaw);
    }
}
