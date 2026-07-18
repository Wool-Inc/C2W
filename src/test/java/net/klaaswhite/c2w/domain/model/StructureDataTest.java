package net.klaaswhite.c2w.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StructureData")
class StructureDataTest {

    @Test
    @DisplayName("getWidth computes correctly")
    void getWidth() {
        var tpl = new StructureData("d", "1",
                new BlockPos(0, 0, 0),
                new BlockPos(15, 10, 10));
        assertEquals(16, tpl.getWidth());
    }

    @Test
    @DisplayName("getHeight computes correctly")
    void getHeight() {
        var tpl = new StructureData("d", "1",
                new BlockPos(0, 5, 0),
                new BlockPos(0, 15, 0));
        assertEquals(11, tpl.getHeight());
    }

    @Test
    @DisplayName("getDepth computes correctly")
    void getDepth() {
        var tpl = new StructureData("d", "1",
                new BlockPos(0, 0, 0),
                new BlockPos(0, 0, 20));
        assertEquals(21, tpl.getDepth());
    }

    @Test
    @DisplayName("dimensions work with reversed corners")
    void dimensionsReversed() {
        var tpl = new StructureData("d", "1",
                new BlockPos(16, 10, 16),
                new BlockPos(0, 0, 0));
            assertEquals(17, tpl.getWidth());
            assertEquals(11, tpl.getHeight());
            assertEquals(17, tpl.getDepth());
    }
}
