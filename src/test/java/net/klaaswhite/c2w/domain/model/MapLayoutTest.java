package net.klaaswhite.c2w.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MapLayout")
class MapLayoutTest {

    private MapLayout createLayout(String name, int tw, int th, int td, BlockPos origin, List<LayoutCell> cells) {
        return new MapLayout(name, tw, th, td, origin, cells);
    }

    private LayoutCell cell(int row, int col, String type, BlockPos pos) {
        return new LayoutCell(row, col, type, pos, null, 0f);
    }

    private LayoutCell spawnCell(int row, int col, BlockPos pos, String team) {
        return new LayoutCell(row, col, "spawn", pos, team, 0f);
    }

    // --- Basic properties ---

    @Test
    @DisplayName("stores name, tile dimensions, and origin")
    void storesProperties() {
        var origin = new BlockPos(100, 64, 200);
        var layout = createLayout("arena", 16, 10, 16, origin, List.of());

        assertEquals("arena", layout.getName());
        assertEquals(16, layout.getTileWidth());
        assertEquals(10, layout.getTileHeight());
        assertEquals(16, layout.getTileDepth());
        assertSame(origin, layout.getOrigin());
    }

    @Test
    @DisplayName("tile dimensions are independent")
    void tileDimensionsIndependent() {
        var layout = createLayout("rect", 32, 8, 24, new BlockPos(0, 0, 0), List.of());

        assertEquals(32, layout.getTileWidth());
        assertEquals(8, layout.getTileHeight());
        assertEquals(24, layout.getTileDepth());
    }

    @Test
    @DisplayName("origin point is preserved exactly")
    void originPreserved() {
        var origin = new BlockPos(-500, 100, 1000);
        var layout = createLayout("offset", 16, 10, 16, origin, List.of());

        assertEquals(-500, layout.getOrigin().x());
        assertEquals(100, layout.getOrigin().y());
        assertEquals(1000, layout.getOrigin().z());
    }

    // --- Defensive copy ---

    @Test
    @DisplayName("getCells returns unmodifiable list — cannot add")
    void cellsUnmodifiable() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(cell(0, 0, "dungeon", new BlockPos(0, 64, 0)));
        var layout = createLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), cells);

        var returned = layout.getCells();
        assertEquals(1, returned.size());
        assertThrows(UnsupportedOperationException.class, () ->
                returned.add(cell(1, 1, "spawn", new BlockPos(0, 0, 0))));
    }

    @Test
    @DisplayName("defensive copy — modifying original list does not affect layout")
    void defensiveCopy() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(cell(0, 0, "dungeon", new BlockPos(0, 64, 0)));
        var layout = createLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), cells);

        cells.add(cell(1, 1, "spawn", new BlockPos(0, 0, 0)));
        assertEquals(1, layout.getCells().size());
    }

    // --- Row/col computation ---

    @Test
    @DisplayName("computes rows and cols from cell coordinates")
    void computesDimensions() {
        var cells = List.of(
                cell(0, 0, "dungeon", new BlockPos(0, 64, 0)),
                cell(0, 1, "dungeon", new BlockPos(16, 64, 0)),
                spawnCell(1, 0, new BlockPos(0, 64, 16), "Red"),
                cell(1, 1, "dungeon", new BlockPos(16, 64, 16))
        );
        var layout = createLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), cells);

        assertEquals(2, layout.getRows());
        assertEquals(2, layout.getCols());
    }

    @Test
    @DisplayName("handles non-square layout")
    void nonSquare() {
        var cells = List.of(
                cell(0, 0, "dungeon", new BlockPos(0, 64, 0)),
                spawnCell(2, 3, new BlockPos(0, 0, 0), "Blue")
        );
        var layout = createLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), cells);

        assertEquals(3, layout.getRows());
        assertEquals(4, layout.getCols());
    }

    @Test
    @DisplayName("rows/cols based on max index, not cell count")
    void rowsFromMaxIndex() {
        // Only 2 cells but row 5, col 7
        var cells = List.of(
                cell(5, 7, "dungeon", new BlockPos(0, 64, 0)),
                cell(0, 0, "spawn", new BlockPos(0, 0, 0))
        );
        var layout = createLayout("sparse", 16, 10, 16, new BlockPos(0, 0, 0), cells);

        assertEquals(6, layout.getRows());
        assertEquals(8, layout.getCols());
    }

    // --- Single cell ---

    @Test
    @DisplayName("handles single cell")
    void singleCell() {
        var cells = List.of(spawnCell(0, 0, new BlockPos(0, 64, 0), "Red"));
        var layout = createLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), cells);

        assertEquals(1, layout.getRows());
        assertEquals(1, layout.getCols());
        assertEquals(1, layout.getCells().size());
    }

    // --- Empty layout edge case ---

    @Test
    @DisplayName("handles empty layout — zero rows, zero cols")
    void emptyLayout() {
        var layout = createLayout("empty", 16, 10, 16, new BlockPos(0, 0, 0), List.of());

        assertEquals(0, layout.getRows());
        assertEquals(0, layout.getCols());
        assertTrue(layout.getCells().isEmpty());
    }

    // --- Cell properties ---

    @Test
    @DisplayName("cells preserve their row, col, typeName, and worldPosition")
    void cellsPreserveProperties() {
        var pos = new BlockPos(100, 70, 200);
        var c = cell(2, 3, "dungeon", pos);
        var layout = createLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), List.of(c));

        var retrieved = layout.getCells().get(0);
        assertEquals(2, retrieved.row());
        assertEquals(3, retrieved.col());
        assertEquals("dungeon", retrieved.typeName());
        assertEquals(100, retrieved.worldPosition().x());
        assertEquals(70, retrieved.worldPosition().y());
        assertEquals(200, retrieved.worldPosition().z());
        assertNull(retrieved.team());
    }

    // --- Spawn cells with team association ---

    @Test
    @DisplayName("spawn cells carry team association")
    void spawnCellsCarryTeam() {
        var redCell = spawnCell(0, 3, new BlockPos(0, 64, 0), "Red");
        var blueCell = spawnCell(4, 3, new BlockPos(0, 64, 128), "Blue");
        var layout = createLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0),
                List.of(redCell, blueCell));

        assertEquals("Red", layout.getCells().get(0).team());
        assertEquals("Blue", layout.getCells().get(1).team());
    }

    @Test
    @DisplayName("non-spawn cells have null team")
    void nonSpawnCellsNullTeam() {
        var c = cell(1, 1, "dungeon", new BlockPos(0, 64, 0));
        assertNull(c.team());
    }

    // --- Layout with gaps (missing cells) ---

    @Test
    @DisplayName("layout with gaps — rows/cols span the full grid")
    void layoutWithGaps() {
        // A 3x3 grid with only the corners filled
        var cells = List.of(
                cell(0, 0, "dungeon", new BlockPos(0, 64, 0)),
                cell(0, 2, "dungeon", new BlockPos(32, 64, 0)),
                cell(2, 0, "dungeon", new BlockPos(0, 64, 32)),
                cell(2, 2, "spawn", new BlockPos(32, 64, 32))
        );
        var layout = createLayout("gaps", 16, 10, 16, new BlockPos(0, 0, 0), cells);

        assertEquals(3, layout.getRows());
        assertEquals(3, layout.getCols());
        assertEquals(4, layout.getCells().size());
    }

    @Test
    @DisplayName("layout with gaps — cell count less than rows * cols")
    void cellCountLessThanGrid() {
        var cells = List.of(
                cell(0, 0, "dungeon", new BlockPos(0, 64, 0)),
                cell(3, 3, "spawn", new BlockPos(48, 64, 48))
        );
        var layout = createLayout("sparse", 16, 16, 16, new BlockPos(0, 0, 0), cells);

        assertEquals(4, layout.getRows());
        assertEquals(4, layout.getCols());
        assertEquals(2, layout.getCells().size());
    }

    // --- Mixed cell types ---

    @Test
    @DisplayName("mixed cell types — spawn and non-spawn")
    void mixedCellTypes() {
        var cells = List.of(
                spawnCell(0, 0, new BlockPos(0, 64, 0), "Red"),
                cell(0, 1, "dungeon", new BlockPos(16, 64, 0)),
                cell(1, 0, "center", new BlockPos(0, 64, 16)),
                spawnCell(1, 1, new BlockPos(16, 64, 16), "Blue")
        );
        var layout = createLayout("mixed", 16, 10, 16, new BlockPos(0, 0, 0), cells);

        assertEquals(4, layout.getCells().size());
        assertEquals("Red", layout.getCells().get(0).team());
        assertNull(layout.getCells().get(1).team());
        assertNull(layout.getCells().get(2).team());
        assertEquals("Blue", layout.getCells().get(3).team());
    }
}
