package net.klaaswhite.c2w.domain.game;

import net.klaaswhite.c2w.adapter.minecraft.BossBar;
import net.klaaswhite.c2w.adapter.minecraft.BossBars;
import net.klaaswhite.c2w.adapter.minecraft.MarkerEngine;
import net.klaaswhite.c2w.adapter.minecraft.MarkerEntity;
import net.klaaswhite.c2w.adapter.minecraft.Markers;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Worlds;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarkerEngine")
class MarkerEngineTest {

    private MinecraftManager mc;
    private Markers markers;
    private Worlds worlds;
    private BossBars bossBars;
    private BossBar bossBar;
    private MarkerEngine engine;

    private MarkerEntity createMockMarker(String worldName, BlockPos pos, String persistentData) {
        var marker = mock(MarkerEntity.class);
        when(marker.getPosition()).thenReturn(pos);
        when(marker.getName()).thenReturn(persistentData);
        when(marker.getPersistentData("map_marker")).thenReturn(persistentData);
        return marker;
    }

    @BeforeEach
    void setUp() {
        mc = mock(MinecraftManager.class);
        markers = mock(Markers.class);
        worlds = mock(Worlds.class);
        bossBars = mock(BossBars.class);
        bossBar = mock(BossBar.class);

        when(mc.markers()).thenReturn(markers);
        when(mc.worlds()).thenReturn(worlds);
        when(mc.bossBars()).thenReturn(bossBars);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);

        when(markers.getMarkerKey()).thenReturn("map_marker");
        when(markers.getMarkersInWorld(anyString())).thenReturn(new ArrayList<>());

        var woolTimer = new WoolTimer(new WoolTimer.Scheduler() {
            public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
            public void cancel(Object taskId) {}
        });
        engine = new MarkerEngine(mc, woolTimer);
    }

    // --- Marker key ---

    @Test
    @DisplayName("getMarkerKey delegates to Markers")
    void markerKey() {
        assertEquals("map_marker", engine.getMarkerKey());
    }

    // --- Discovery ---

    @Test
    @DisplayName("discoverMarkers populates marker entities from world")
    void discoverMarkers() {
        var pos = new BlockPos(10, 64, 10);
        var marker = createMockMarker("game", pos, "wool");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(marker));

        engine.discoverMarkers("game");

        assertNotNull(engine.getMarker("wool"));
        assertEquals(pos, engine.getMarker("wool").getPosition());
    }

    @Test
    @DisplayName("discoverMarkers ignores markers without persistent data")
    void discoverMarkersNoData() {
        var marker = mock(MarkerEntity.class);
        when(marker.getPosition()).thenReturn(new BlockPos(10, 64, 10));
        when(marker.getPersistentData("map_marker")).thenReturn(null);
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(marker));

        engine.discoverMarkers("game");

        assertTrue(engine.getMarkerNames().isEmpty());
    }

    @Test
    @DisplayName("discoverMarkers in empty world produces empty result")
    void discoverMarkersEmptyWorld() {
        when(markers.getMarkersInWorld("empty_world")).thenReturn(List.of());
        engine.discoverMarkers("empty_world");
        assertTrue(engine.getMarkerNames().isEmpty());
    }

    // --- getMarkersInWorld ---

    @Test
    @DisplayName("getMarkersInWorld returns markers in a world")
    void getMarkersInWorld() {
        var pos = new BlockPos(10, 64, 10);
        var marker = createMockMarker("game", pos, "wool");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(marker));

        var result = engine.getMarkersInWorld("game");
        assertEquals(1, result.size());
        assertNotNull(result.get("wool"));
    }

    @Test
    @DisplayName("getMarkersInWorld returns empty for unknown world")
    void getMarkersInWorldUnknown() {
        when(markers.getMarkersInWorld("nonexistent")).thenReturn(new ArrayList<>());
        assertTrue(engine.getMarkersInWorld("nonexistent").isEmpty());
    }

    // --- getMarker ---

    @Test
    @DisplayName("getMarker returns null for unknown marker")
    void getMarkerUnknown() {
        assertNull(engine.getMarker("wool"));
    }

    @Test
    @DisplayName("getMarker returns null before discovery")
    void getMarkerBeforeDiscovery() {
        assertNull(engine.getMarker("wool"));
    }

    // --- Wool initialization ---

    @Test
    @DisplayName("initWools creates Wool from generic wool marker")
    void initWoolsFromWoolMarker() {
        var pos = new BlockPos(10, 64, 10);
        var marker = createMockMarker("game", pos, "wool");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(marker));
        engine.discoverMarkers("game");

        engine.initWools("game");

        var wools = engine.getWools();
        assertEquals(1, wools.size());
        assertEquals(WoolColor.RED, wools.get(0).getColor());  // First wool gets RED
        assertEquals(pos, wools.get(0).getSpawnPos());
    }

    @Test
    @DisplayName("initWools assigns colors sequentially to multiple wool markers")
    void initWoolsMultipleWools() {
        var pos1 = new BlockPos(10, 64, 10);
        var pos2 = new BlockPos(20, 64, 20);
        var m1 = createMockMarker("game", pos1, "wool");
        var m2 = createMockMarker("game", pos2, "wool");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(m1, m2));
        engine.discoverMarkers("game");

        engine.initWools("game");

        var wools = engine.getWools();
        assertEquals(2, wools.size());
        assertEquals(WoolColor.RED, wools.get(0).getColor());
        assertEquals(WoolColor.GREEN, wools.get(1).getColor());
    }

    @Test
    @DisplayName("initWools ignores non-wool markers")
    void initWoolsIgnoresNonWool() {
        var pos = new BlockPos(10, 64, 10);
        var marker = createMockMarker("game", pos, "boundary-woolcap-pit-1");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(marker));
        engine.discoverMarkers("game");

        engine.initWools("game");

        assertTrue(engine.getWools().isEmpty());
    }

    @Test
    @DisplayName("initWools creates wools from multiple wool markers")
    void initWoolsMultiple() {
        var pos1 = new BlockPos(10, 64, 10);
        var pos2 = new BlockPos(20, 64, 20);
        var m1 = createMockMarker("game", pos1, "wool");
        var m2 = createMockMarker("game", pos2, "wool");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(m1, m2));
        engine.discoverMarkers("game");

        engine.initWools("game");

        assertEquals(2, engine.getWools().size());
    }

    @Test
    @DisplayName("initWools places entity for each wool")
    void initWoolsPlacesEntities() {
        var pos = new BlockPos(10, 64, 10);
        var marker = createMockMarker("game", pos, "wool");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(marker));
        engine.discoverMarkers("game");

        engine.initWools("game");

        var wools = engine.getWools();
        assertEquals(1, wools.size());
        // placeEntityInWorld was called via initWools
        verify(mc.worlds()).dropItem(eq("game"), eq(pos), anyString(), eq(1));
    }

    @Test
    @DisplayName("initWools before discoverMarkers is no-op")
    void initWoolsBeforeDiscover() {
        assertDoesNotThrow(() -> engine.initWools("game"));
        assertTrue(engine.getWools().isEmpty());
    }

    @Test
    @DisplayName("initWools assigns RED, GREEN, BLUE to three wool markers")
    void initWoolsThreeWools() {
        var m1 = createMockMarker("game", new BlockPos(10, 64, 10), "wool");
        var m2 = createMockMarker("game", new BlockPos(20, 64, 20), "wool");
        var m3 = createMockMarker("game", new BlockPos(30, 64, 30), "wool");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(m1, m2, m3));
        engine.discoverMarkers("game");

        engine.initWools("game");

        var wools = engine.getWools();
        assertEquals(3, wools.size());
        assertEquals(WoolColor.RED, wools.get(0).getColor());
        assertEquals(WoolColor.GREEN, wools.get(1).getColor());
        assertEquals(WoolColor.BLUE, wools.get(2).getColor());
    }

    @Test
    @DisplayName("initWools assigns RED, GREEN, BLUE, YELLOW to four wool markers")
    void initWoolsFourWools() {
        var list = new ArrayList<MarkerEntity>();
        for (int i = 0; i < 4; i++) {
            list.add(createMockMarker("game", new BlockPos(i * 10, 64, i * 10), "wool"));
        }
        when(markers.getMarkersInWorld("game")).thenReturn(list);
        engine.discoverMarkers("game");

        engine.initWools("game");

        var wools = engine.getWools();
        assertEquals(4, wools.size());
        assertEquals(WoolColor.RED, wools.get(0).getColor());
        assertEquals(WoolColor.GREEN, wools.get(1).getColor());
        assertEquals(WoolColor.BLUE, wools.get(2).getColor());
        assertEquals(WoolColor.YELLOW, wools.get(3).getColor());
    }

    @Test
    @DisplayName("initWools caps at four wools even when more markers exist")
    void initWoolsMoreThanFour() {
        var list = new ArrayList<MarkerEntity>();
        for (int i = 0; i < 6; i++) {
            list.add(createMockMarker("game", new BlockPos(i * 10, 64, i * 10), "wool"));
        }
        when(markers.getMarkersInWorld("game")).thenReturn(list);
        engine.discoverMarkers("game");

        engine.initWools("game");

        // Only four colors exist, so only four wools are created.
        assertEquals(4, engine.getWools().size());
    }

    // --- ensureEntityWools ---

    @Test
    @DisplayName("ensureEntityWools calls ensureEntity on all wools")
    void ensureEntityWools() {
        var pos = new BlockPos(10, 64, 10);
        var marker = createMockMarker("game", pos, "wool");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(marker));
        engine.discoverMarkers("game");
        engine.initWools("game");

        assertDoesNotThrow(() -> engine.ensureEntityWools());
    }

    // --- Close ---

    @Test
    @DisplayName("close clears all wools and markers")
    void close() {
        var pos = new BlockPos(10, 64, 10);
        var marker = createMockMarker("game", pos, "wool");
        when(markers.getMarkersInWorld("game")).thenReturn(List.of(marker));
        engine.discoverMarkers("game");
        engine.initWools("game");

        assertFalse(engine.getWools().isEmpty());
        assertFalse(engine.getMarkerNames().isEmpty());

        engine.close();

        assertTrue(engine.getWools().isEmpty());
        assertTrue(engine.getMarkerNames().isEmpty());
    }
}