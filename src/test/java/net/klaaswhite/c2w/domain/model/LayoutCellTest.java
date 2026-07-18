package net.klaaswhite.c2w.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LayoutCell")
class LayoutCellTest {

    @Test
    @DisplayName("record stores row, col, typeName, worldPosition, team")
    void recordStoresAllFields() {
        var pos = new BlockPos(100, 64, 200);
        var cell = new LayoutCell(2, 3, "dungeon", pos, "Red", 0f);

        assertEquals(2, cell.row());
        assertEquals(3, cell.col());
        assertEquals("dungeon", cell.typeName());
        assertEquals(pos, cell.worldPosition());
        assertEquals("Red", cell.team());
    }

    @Test
    @DisplayName("LayoutCell.of() factory creates cell with null team")
    void ofFactoryCreatesNonSpawn() {
        var pos = new BlockPos(10, 20, 30);
        var cell = LayoutCell.of(1, 2, "dungeon", pos);

        assertEquals(1, cell.row());
        assertEquals(2, cell.col());
        assertEquals("dungeon", cell.typeName());
        assertEquals(pos, cell.worldPosition());
        assertNull(cell.team());
    }

    @Test
    @DisplayName("LayoutCell.spawn() factory creates cell with team")
    void spawnFactoryCreatesWithTeam() {
        var pos = new BlockPos(0, 64, 0);
        var cell = LayoutCell.spawn(0, 3, "spawn", pos, "Blue");

        assertEquals(0, cell.row());
        assertEquals(3, cell.col());
        assertEquals("spawn", cell.typeName());
        assertEquals("Blue", cell.team());
    }

    @Test
    @DisplayName("non-spawn cells have null team")
    void nonSpawnCellsNullTeam() {
        var cell = LayoutCell.of(5, 5, "center", new BlockPos(0, 0, 0));
        assertNull(cell.team());
    }

    @Test
    @DisplayName("spawn cells preserve team association")
    void spawnCellsPreserveTeam() {
        var redSpawn = LayoutCell.spawn(0, 0, "spawn", new BlockPos(0, 64, 0), "Red");
        var blueSpawn = LayoutCell.spawn(4, 6, "spawn", new BlockPos(0, 64, 0), "Blue");

        assertEquals("Red", redSpawn.team());
        assertEquals("Blue", blueSpawn.team());
    }

    @Test
    @DisplayName("LayoutCell is a record — equality based on all fields")
    void recordEquality() {
        var pos = new BlockPos(10, 20, 30);
        var cell1 = LayoutCell.of(1, 2, "dungeon", pos);
        var cell2 = LayoutCell.of(1, 2, "dungeon", pos);
        var cell3 = LayoutCell.of(1, 2, "spawn", pos);

        assertEquals(cell1, cell2);
        assertNotEquals(cell1, cell3);
    }

    @Test
    @DisplayName("LayoutCell toString contains all fields")
    void toStringContainsFields() {
        var pos = new BlockPos(10, 20, 30);
        var cell = LayoutCell.of(1, 2, "dungeon", pos);
        var str = cell.toString();

        assertTrue(str.contains("LayoutCell"), "toString should mention LayoutCell");
        assertTrue(str.contains("dungeon"), "toString should include typeName");
    }

    @Test
    @DisplayName("zero coordinates are valid")
    void zeroCoordinates() {
        var cell = LayoutCell.of(0, 0, "spawn", new BlockPos(0, 0, 0));
        assertEquals(0, cell.row());
        assertEquals(0, cell.col());
    }
}
