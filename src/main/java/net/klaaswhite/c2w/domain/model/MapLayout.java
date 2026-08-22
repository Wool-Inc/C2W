package net.klaaswhite.c2w.domain.model;

import java.util.List;

/**
 * A 2D layout blueprint expanded into 3D world positions. The grid is a
 * list of {@link LayoutCell}s, one per non-empty char in the original
 * blueprint. World positions are computed from {@code origin},
 * {@code tileWidth}, {@code tileHeight}, {@code tileDepth} and any
 * per-cell overrides from the config.
 */
public class MapLayout {

    private final String name;
    private final int tileWidth;
    private final int tileHeight;
    private final int tileDepth;
    private final BlockPos origin;
    private final List<LayoutCell> cells;
    private final int rows;
    private final int cols;
    private final boolean placementsBased;

    public MapLayout(
            String name,
            int tileWidth,
            int tileHeight,
            int tileDepth,
            BlockPos origin,
            List<LayoutCell> cells
    ) {
        this(name, tileWidth, tileHeight, tileDepth, origin, cells, false);
    }

    public MapLayout(
            String name,
            int tileWidth,
            int tileHeight,
            int tileDepth,
            BlockPos origin,
            List<LayoutCell> cells,
            boolean placementsBased
    ) {
        this.name = name;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.tileDepth = tileDepth;
        this.origin = origin;
        this.cells = List.copyOf(cells);
        this.placementsBased = placementsBased;

        int maxRow = 0;
        int maxCol = 0;
        for (LayoutCell cell : cells) {
            if (cell.row() > maxRow) maxRow = cell.row();
            if (cell.col() > maxCol) maxCol = cell.col();
        }
        if (cells.isEmpty()) {
            this.rows = 0;
            this.cols = 0;
        } else {
            this.rows = maxRow + 1;
            this.cols = maxCol + 1;
        }
    }

    public String getName() {
        return name;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public int getTileDepth() {
        return tileDepth;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public List<LayoutCell> getCells() {
        return cells;
    }

    public int getRows() {
        return rows;
    }

    public boolean isPlacementsBased() {
        return placementsBased;
    }

    public int getCols() {
        return cols;
    }
}
