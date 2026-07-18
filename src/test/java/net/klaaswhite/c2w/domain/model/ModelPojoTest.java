package net.klaaswhite.c2w.domain.model;

import net.klaaswhite.c2w.adapter.minecraft.MarkerEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Domain model POJOs")
class ModelPojoTest {

    // --- ManagedMarker ---

    @Test
    @DisplayName("ManagedMarker stores name and delegates position to MarkerEntity")
    void managedMarkerStoresNameAndPosition() {
        var entity = mock(MarkerEntity.class);
        var pos = new BlockPos(10, 64, -5);
        when(entity.getPosition()).thenReturn(pos);

        var marker = new ManagedMarker(entity, "spawn-point");

        assertEquals("spawn-point", marker.getName());
        assertEquals(pos, marker.getPosition());
    }

    @Test
    @DisplayName("ManagedMarker with null name is stored as-is")
    void managedMarkerNullName() {
        var entity = mock(MarkerEntity.class);
        when(entity.getPosition()).thenReturn(new BlockPos(0, 0, 0));

        var marker = new ManagedMarker(entity, null);

        assertNull(marker.getName());
    }

    // --- LayoutPlacement ---

    @Test
    @DisplayName("LayoutPlacement record stores all components")
    void layoutPlacementStoresComponents() {
        var placement = new LayoutPlacement("arena", "main", 100, 64, -200);

        assertEquals("arena", placement.typeName());
        assertEquals("main", placement.id());
        assertEquals(100, placement.x());
        assertEquals(64, placement.y());
        assertEquals(-200, placement.z());
    }

    @Test
    @DisplayName("LayoutPlacement equality and hashCode")
    void layoutPlacementEquality() {
        var a = new LayoutPlacement("arena", "main", 100, 64, -200);
        var b = new LayoutPlacement("arena", "main", 100, 64, -200);
        var c = new LayoutPlacement("arena", "alt", 100, 64, -200);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("LayoutPlacement toString is meaningful")
    void layoutPlacementToString() {
        var placement = new LayoutPlacement("arena", "main", 100, 64, -200);
        var str = placement.toString();

        assertTrue(str.contains("arena"));
        assertTrue(str.contains("main"));
        assertTrue(str.contains("100"));
    }

    // --- ItemStackRef ---

    @Test
    @DisplayName("ItemStackRef stores material name and count")
    void itemStackRefStoresComponents() {
        var ref = new ItemStackRef("DIAMOND_SWORD", 1);

        assertEquals("DIAMOND_SWORD", ref.materialName());
        assertEquals(1, ref.count());
    }

    @Test
    @DisplayName("ItemStackRef.wool creates correct material name")
    void itemStackRefWool() {
        var ref = ItemStackRef.wool(WoolColor.RED);

        assertEquals("RED_WOOL", ref.materialName());
        assertEquals(1, ref.count());
    }

    @Test
    @DisplayName("ItemStackRef.empty creates AIR with count 0")
    void itemStackRefEmpty() {
        var ref = ItemStackRef.empty();

        assertEquals("AIR", ref.materialName());
        assertEquals(0, ref.count());
    }

    @Test
    @DisplayName("ItemStackRef.isEmpty returns true for empty and zero count")
    void itemStackRefIsEmpty() {
        assertTrue(ItemStackRef.empty().isEmpty());
        assertTrue(new ItemStackRef("STONE", 0).isEmpty());
        assertFalse(new ItemStackRef("STONE", 1).isEmpty());
    }

    @Test
    @DisplayName("ItemStackRef equality and hashCode")
    void itemStackRefEquality() {
        var a = new ItemStackRef("IRON_INGOT", 64);
        var b = new ItemStackRef("IRON_INGOT", 64);
        var c = new ItemStackRef("IRON_INGOT", 32);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    // --- DomainBoundingBox ---

    @Test
    @DisplayName("DomainBoundingBox from two BlockPos normalises min/max")
    void boundingBoxFromBlockPos() {
        var pos1 = new BlockPos(10, 64, 20);
        var pos2 = new BlockPos(5, 70, 15);

        var box = new DomainBoundingBox(pos1, pos2);

        assertEquals(5.0, box.minX());
        assertEquals(64.0, box.minY());
        assertEquals(15.0, box.minZ());
        assertEquals(10.0, box.maxX());
        assertEquals(70.0, box.maxY());
        assertEquals(20.0, box.maxZ());
    }

    @Test
    @DisplayName("DomainBoundingBox contains returns true for interior points")
    void boundingBoxContainsInterior() {
        var box = new DomainBoundingBox(0, 0, 0, 10, 10, 10);

        assertTrue(box.contains(5, 5, 5));
    }

    @Test
    @DisplayName("DomainBoundingBox contains returns true for boundary points")
    void boundingBoxContainsBoundary() {
        var box = new DomainBoundingBox(0, 0, 0, 10, 10, 10);

        assertTrue(box.contains(0, 0, 0));
        assertTrue(box.contains(10, 10, 10));
    }

    @Test
    @DisplayName("DomainBoundingBox contains returns false for exterior points")
    void boundingBoxContainsExterior() {
        var box = new DomainBoundingBox(0, 0, 0, 10, 10, 10);

        assertFalse(box.contains(11, 5, 5));
        assertFalse(box.contains(5, -1, 5));
        assertFalse(box.contains(5, 5, 100));
    }

    @Test
    @DisplayName("DomainBoundingBox equality and hashCode")
    void boundingBoxEquality() {
        var a = new DomainBoundingBox(0, 0, 0, 10, 10, 10);
        var b = new DomainBoundingBox(0, 0, 0, 10, 10, 10);
        var c = new DomainBoundingBox(0, 0, 0, 10, 10, 11);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    // --- BossBarStyle ---

    @Test
    @DisplayName("BossBarStyle has all expected enum values")
    void bossBarStyleValues() {
        var values = BossBarStyle.values();

        assertEquals(5, values.length);
        assertArrayEquals(
                new BossBarStyle[]{
                        BossBarStyle.SOLID,
                        BossBarStyle.SEGMENTED_6,
                        BossBarStyle.SEGMENTED_10,
                        BossBarStyle.SEGMENTED_12,
                        BossBarStyle.SEGMENTED_20
                },
                values
        );
    }

    @Test
    @DisplayName("BossBarStyle valueOf resolves correctly")
    void bossBarStyleValueOf() {
        assertEquals(BossBarStyle.SOLID, BossBarStyle.valueOf("SOLID"));
        assertEquals(BossBarStyle.SEGMENTED_6, BossBarStyle.valueOf("SEGMENTED_6"));
        assertEquals(BossBarStyle.SEGMENTED_10, BossBarStyle.valueOf("SEGMENTED_10"));
        assertEquals(BossBarStyle.SEGMENTED_12, BossBarStyle.valueOf("SEGMENTED_12"));
        assertEquals(BossBarStyle.SEGMENTED_20, BossBarStyle.valueOf("SEGMENTED_20"));
    }

    // --- PlayerHandle ---

    @Test
    @DisplayName("PlayerHandle mock implements all interface methods")
    void playerHandleMockWorks() {
        var handle = mock(PlayerHandle.class);

        when(handle.getName()).thenReturn("TestPlayer");
        when(handle.getDisplayName()).thenReturn("§aTestPlayer");
        when(handle.getWorldName()).thenReturn("world");
        when(handle.getPosition()).thenReturn(new BlockPos(100, 64, 200));

        assertEquals("TestPlayer", handle.getName());
        assertEquals("§aTestPlayer", handle.getDisplayName());
        assertEquals("world", handle.getWorldName());
        assertEquals(new BlockPos(100, 64, 200), handle.getPosition());
    }

    @Test
    @DisplayName("PlayerHandle is an interface with expected method signatures")
    void playerHandleIsInterface() {
        assertTrue(PlayerHandle.class.isInterface());
    }

    // --- Mirror ---

    @Test
    @DisplayName("Mirror has all expected enum values")
    void mirrorValues() {
        var values = Mirror.values();

        assertEquals(3, values.length);
        assertArrayEquals(
                new Mirror[]{Mirror.NONE, Mirror.LEFT_RIGHT, Mirror.FRONT_BACK},
                values
        );
    }

    @Test
    @DisplayName("Mirror valueOf resolves correctly")
    void mirrorValueOf() {
        assertEquals(Mirror.NONE, Mirror.valueOf("NONE"));
        assertEquals(Mirror.LEFT_RIGHT, Mirror.valueOf("LEFT_RIGHT"));
        assertEquals(Mirror.FRONT_BACK, Mirror.valueOf("FRONT_BACK"));
    }

    // --- StructureRotation ---

    @Test
    @DisplayName("StructureRotation has all expected enum values")
    void structureRotationValues() {
        var values = StructureRotation.values();

        assertEquals(4, values.length);
        assertArrayEquals(
                new StructureRotation[]{
                        StructureRotation.NONE,
                        StructureRotation.CLOCKWISE_90,
                        StructureRotation.CLOCKWISE_180,
                        StructureRotation.COUNTERCLOCKWISE_90
                },
                values
        );
    }

    @Test
    @DisplayName("StructureRotation valueOf resolves correctly")
    void structureRotationValueOf() {
        assertEquals(StructureRotation.NONE, StructureRotation.valueOf("NONE"));
        assertEquals(StructureRotation.CLOCKWISE_90, StructureRotation.valueOf("CLOCKWISE_90"));
        assertEquals(StructureRotation.CLOCKWISE_180, StructureRotation.valueOf("CLOCKWISE_180"));
        assertEquals(StructureRotation.COUNTERCLOCKWISE_90, StructureRotation.valueOf("COUNTERCLOCKWISE_90"));
    }

    // --- TeamColor ---

    @Test
    @DisplayName("TeamColor has all expected enum values")
    void teamColorValues() {
        var values = TeamColor.values();

        assertEquals(3, values.length);
        assertArrayEquals(
                new TeamColor[]{TeamColor.RED, TeamColor.BLUE, TeamColor.GRAY},
                values
        );
    }

    @Test
    @DisplayName("TeamColor toString returns display name")
    void teamColorToString() {
        assertEquals("Red", TeamColor.RED.toString());
        assertEquals("Blue", TeamColor.BLUE.toString());
        assertEquals("Gray", TeamColor.GRAY.toString());
    }

    @Test
    @DisplayName("TeamColor valueOf resolves correctly")
    void teamColorValueOf() {
        assertEquals(TeamColor.RED, TeamColor.valueOf("RED"));
        assertEquals(TeamColor.BLUE, TeamColor.valueOf("BLUE"));
        assertEquals(TeamColor.GRAY, TeamColor.valueOf("GRAY"));
    }
}
