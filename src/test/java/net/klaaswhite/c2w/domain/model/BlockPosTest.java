package net.klaaswhite.c2w.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BlockPos")
class BlockPosTest {

    @Test
    @DisplayName("constructor stores all fields")
    void constructorStoresFields() {
        var pos = new BlockPos(1, 2, 3);
        assertEquals(1, pos.x());
        assertEquals(2, pos.y());
        assertEquals(3, pos.z());
    }

    @Test
    @DisplayName("toString uses record format")
    void toStringFormatsCorrectly() {
        var pos = new BlockPos(10, 20, 30);
        assertEquals("BlockPos[x=10, y=20, z=30]", pos.toString());
    }

    @Test
    @DisplayName("record equality works")
    void recordEquality() {
        var a = new BlockPos(1, 2, 3);
        var b = new BlockPos(1, 2, 3);
        var c = new BlockPos(1, 2, 4);
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("add returns a new BlockPos with offset")
    void addReturnsOffset() {
        var pos = new BlockPos(5, 10, 15);
        var result = pos.add(1, 2, 3);
        assertEquals(6, result.x());
        assertEquals(12, result.y());
        assertEquals(18, result.z());
    }
}
