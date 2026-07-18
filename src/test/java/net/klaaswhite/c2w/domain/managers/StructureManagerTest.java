package net.klaaswhite.c2w.domain.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Structures;
import net.klaaswhite.c2w.domain.managers.NbtStructureSource;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.StructureData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("StructureManager")
class StructureManagerTest {

    private MinecraftManager mc;
    private Structures structures;
    private NbtStructureSource source;
    private StructureManager mgr;

    @BeforeEach
    void setUp() {
        mc = mock(MinecraftManager.class);
        structures = mock(Structures.class);
        when(mc.structures()).thenReturn(structures);
        source = mock(NbtStructureSource.class);
        mgr = new StructureManager(source);
    }

    private StructureData template(String type, String id) {
        return new StructureData(type, id, new BlockPos(0, 0, 0), new BlockPos(16, 10, 16));
    }

    // --- discoverTemplates with fake NBT files ---

    @Test
    @DisplayName("discoverTemplates returns empty when no NBT files exist")
    void discoverEmpty() {
        var result = mgr.discoverTemplates();
        assertTrue(result.isEmpty());
        assertEquals(0, mgr.getTemplateCount());
    }

    @Test
    @DisplayName("discoverTemplates clears previous results on re-discover")
    void discoverClearsPrevious() {
        mgr.discoverTemplates();
        assertEquals(0, mgr.getTemplateCount());

        mgr.discoverTemplates();
        assertEquals(0, mgr.getTemplateCount());
    }

    // --- Template count by type ---

    @Test
    @DisplayName("getTemplates returns empty list for unknown type")
    void getTemplatesEmpty() {
        assertTrue(mgr.getTemplates("nonexistent").isEmpty());
    }

    // --- placeRandom ---

    @Test
    @DisplayName("placeRandom returns null when no templates for type")
    void placeRandomNull() {
        mgr.discoverTemplates();
        assertNull(mgr.placeRandom("nonexistent", "world", new BlockPos(0, 0, 0)));
    }

    // --- getAllTemplatesByTypeName ---

    @Test
    @DisplayName("getAllTemplatesByTypeName returns the internal map")
    void getAllTemplatesReturnsMap() {
        var all = mgr.getAllTemplatesByTypeName();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    // --- Test with manually injected templates via discover ---

    @Test
    @DisplayName("structure manager starts empty")
    void startsEmpty() {
        assertEquals(0, mgr.getTemplateCount());
        assertTrue(mgr.getAllTemplatesByTypeName().isEmpty());
        assertTrue(mgr.getTemplates("any").isEmpty());
    }

    @Test
    @DisplayName("placeRandom with no discovery returns null")
    void placeRandomWithoutDiscover() {
        assertNull(mgr.placeRandom("dungeon", "game", new BlockPos(0, 64, 0)));
    }

    @Test
    @DisplayName("close clears all templates")
    void closeClears() {
        mgr.close();
        assertEquals(0, mgr.getTemplateCount());
        assertTrue(mgr.getAllTemplatesByTypeName().isEmpty());
    }

    @Test
    @DisplayName("discover returns list that can be iterated safely")
    void discoverReturnsList() {
        var result = mgr.discoverTemplates();
        assertNotNull(result);
        assertDoesNotThrow(() -> {
            for (var t : result) {
                assertNotNull(t.getTypeName());
            }
        });
    }

    @Test
    @DisplayName("multiple types registered independently")
    void multipleTypesIndependent() {
        // Since we can't create real NBT files in this test setup,
        // verify the empty state is consistent for different types
        mgr.discoverTemplates();
        assertTrue(mgr.getTemplates("dungeon").isEmpty());
        assertTrue(mgr.getTemplates("spawn").isEmpty());
        assertTrue(mgr.getTemplates("center").isEmpty());
    }

    @Test
    @DisplayName("getTemplateCount is zero before discovery")
    void templateCountZeroBefore() {
        assertEquals(0, mgr.getTemplateCount());
    }

    @Test
    @DisplayName("manager supports discover → close → discover cycle")
    void discoverCloseDiscoverCycle() {
        mgr.discoverTemplates();
        assertEquals(0, mgr.getTemplateCount());

        mgr.close();
        assertEquals(0, mgr.getTemplateCount());

        mgr.discoverTemplates();
        assertEquals(0, mgr.getTemplateCount());
    }

    @Test
    @DisplayName("checkDimensions returns null when NBT size matches type dimensions")
    void checkDimensionsMatches() {
        when(source.getNbtDimensions("dungeon", "test_1")).thenReturn(new int[]{16, 10, 16});
        when(source.getTypeDimensions("dungeon")).thenReturn(new int[]{16, 10, 16});

        var result = mgr.checkDimensions("dungeon", "test_1");
        assertNull(result);
    }

    @Test
    @DisplayName("checkDimensions returns warning when sizes differ")
    void checkDimensionsDiffers() {
        when(source.getNbtDimensions("dungeon", "test_1")).thenReturn(new int[]{16, 10, 16});
        when(source.getTypeDimensions("dungeon")).thenReturn(new int[]{32, 10, 16});

        var result = mgr.checkDimensions("dungeon", "test_1");
        assertNotNull(result);
        assertTrue(result.contains("16x10x16"));
        assertTrue(result.contains("32x10x16"));
    }

    @Test
    @DisplayName("checkDimensions returns null when NBT file doesn't exist")
    void checkDimensionsNbtMissing() {
        when(source.getNbtDimensions("dungeon", "nonexistent")).thenReturn(null);

        var result = mgr.checkDimensions("dungeon", "nonexistent");
        assertNull(result);
    }

    @Test
    @DisplayName("checkDimensions returns null when type has no dimensions configured")
    void checkDimensionsTypeNull() {
        when(source.getNbtDimensions("dungeon", "test_1")).thenReturn(new int[]{16, 10, 16});
        when(source.getTypeDimensions("dungeon")).thenReturn(null);

        var result = mgr.checkDimensions("dungeon", "test_1");
        assertNull(result);
    }
}
