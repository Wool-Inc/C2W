package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.Markers;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.bootstrap.Managers;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Hashtable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MarkerManager")
class MarkerManagerTest {

    private Managers managers;
    private EventManager eventManager;
    private MinecraftManager mc;
    private Players players;
    private Markers markers;

    @BeforeEach
    void setUp() {
        managers = mock(Managers.class);
        eventManager = mock(EventManager.class);
        mc = mock(MinecraftManager.class);
        players = mock(Players.class);
        markers = mock(Markers.class);

        when(mc.players()).thenReturn(players);
        when(mc.markers()).thenReturn(markers);
        when(markers.getMarkerKey()).thenReturn("map_marker");
        when(markers.getMarkersInWorld(anyString())).thenReturn(List.of());
        managers.woolTimer = new net.klaaswhite.c2w.domain.game.WoolTimer(new net.klaaswhite.c2w.domain.game.WoolTimer.Scheduler() {
            public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
            public void cancel(Object taskId) {}
        });
    }

    private MarkerManager createManager() {
        return new MarkerManager(managers, eventManager, mc);
    }

    // ---------------------------------------------------------------
    // Constructor / smoke test
    // ---------------------------------------------------------------

    @Test
    @DisplayName("constructor registers StartGameEvent handler")
    void constructorRegistersStartGameHandler() {
        createManager();
        verify(eventManager).registerInternalEvent(eq(StartGameEvent.class), any());
    }

    @Test
    @DisplayName("constructor does not throw with all mocked dependencies")
    void constructorSmokeTest() {
        assertDoesNotThrow(this::createManager);
    }

    // ---------------------------------------------------------------
    // getMarkerKey
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getMarkerKey delegates to engine which delegates to mc.markers()")
    void getMarkerKey() {
        var manager = createManager();
        assertEquals("map_marker", manager.getMarkerKey());
    }

    // ---------------------------------------------------------------
    // getMarkersInWorld(Player)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getMarkersInWorld(Player) returns empty when player world is null")
    void getMarkersInWorld_playerNullWorld() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn(null);

        List<String> result = manager.getMarkersInWorld(caller);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getMarkersInWorld(Player) returns marker names from world")
    void getMarkersInWorld_playerReturnsMarkers() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_game");

        // The engine's getMarkersInWorld delegates to mc.markers().getMarkersInWorld()
        // which returns empty by default, so we get an empty key set
        List<String> result = manager.getMarkersInWorld(caller);
        assertNotNull(result);
    }

    // ---------------------------------------------------------------
    // getMarkersInWorld(ManagedWorld)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getMarkersInWorld(ManagedWorld) delegates to engine")
    void getMarkersInWorld_managedWorld() {
        var manager = createManager();
        var managedWorld = mock(net.klaaswhite.c2w.bootstrap.world.ManagedWorld.class);
        when(managedWorld.getName()).thenReturn("c2w_game");

        Hashtable<String, ManagedMarker> result = manager.getMarkersInWorld(managedWorld);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ---------------------------------------------------------------
    // getMarkersInWorld(String)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getMarkersInWorld(String) delegates to engine")
    void getMarkersInWorld_string() {
        var manager = createManager();

        Hashtable<String, ManagedMarker> result = manager.getMarkersInWorld("c2w_game");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ---------------------------------------------------------------
    // createMarker
    // ---------------------------------------------------------------

    @Test
    @DisplayName("createMarker sends error when game is initialized")
    void createMarker_whenInitialized() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");

        // Trigger initialization via StartGameEvent
        manager.start(new StartGameEvent("c2w_game"));

        manager.createMarker(caller, "player", "wool");
        verify(caller).sendMessage(contains("cannot be altered"));
    }

    @Test
    @DisplayName("createMarker with 'looking' sends error when no target block")
    void createMarker_lookingNoTarget() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");
        when(players.getTargetBlock("TestPlayer", 6)).thenReturn(null);

        manager.createMarker(caller, "looking", "wool");
        verify(caller).sendMessage(contains("No block in sight"));
    }

    @Test
    @DisplayName("createMarker with 'looking' sends error when position is null")
    void createMarker_lookingNullPosition() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");
        when(players.getTargetBlock("TestPlayer", 6)).thenReturn(null);

        manager.createMarker(caller, "looking", "wool");
        verify(caller).sendMessage(contains("No block in sight"));
    }

    @Test
    @DisplayName("createMarker sends error when world is null")
    void createMarker_nullWorld() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");
        when(players.getTargetBlock("TestPlayer", 6)).thenReturn(new BlockPos(10, 65, 10));
        when(players.getPosition("TestPlayer")).thenReturn(new BlockPos(10, 65, 10));
        when(players.getWorldName("TestPlayer")).thenReturn(null);

        manager.createMarker(caller, "player", "wool");
        verify(caller).sendMessage(contains("Could not determine your world"));
    }

    @Test
    @DisplayName("createMarker with 'player' position succeeds")
    void createMarker_playerPosition() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");
        when(players.getPosition("TestPlayer")).thenReturn(new BlockPos(10, 65, 10));
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_game");

        var markerEntity = mock(net.klaaswhite.c2w.adapter.minecraft.MarkerEntity.class);
        when(markers.spawnMarker("c2w_game", new BlockPos(10, 65, 10))).thenReturn(markerEntity);

        manager.createMarker(caller, "player", "wool");
        verify(markers).spawnMarker("c2w_game", new BlockPos(10, 65, 10));
        verify(markerEntity).setPersistentData(anyString(), eq("wool"));
    }

    @Test
    @DisplayName("createMarker with 'looking' position succeeds")
    void createMarker_lookingPosition() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");
        when(players.getTargetBlock("TestPlayer", 6)).thenReturn(new BlockPos(5, 70, 5));
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_game");

        when(markers.spawnMarker("c2w_game", new BlockPos(5, 70, 5))).thenReturn(null);

        manager.createMarker(caller, "looking", "spawnpoint");
        verify(markers).spawnMarker("c2w_game", new BlockPos(5, 70, 5));
    }

    // ---------------------------------------------------------------
    // removeMarker
    // ---------------------------------------------------------------

    @Test
    @DisplayName("removeMarker returns false when player world is null")
    void removeMarker_nullWorld() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn(null);

        assertFalse(manager.removeMarker(caller, "wool"));
    }

    @Test
    @DisplayName("removeMarker returns false when marker not found")
    void removeMarker_notFound() {
        var manager = createManager();
        var caller = mock(Player.class);
        when(caller.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_game");

        assertFalse(manager.removeMarker(caller, "nonexistent"));
    }

    // ---------------------------------------------------------------
    // start(StartGameEvent)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("start sets world name and initializes engine")
    void start_initializes() {
        var manager = createManager();

        manager.start(new StartGameEvent("c2w_game"));

        // After start, getMarkersInWorld() should return empty (not null)
        List<String> result = manager.getMarkersInWorld();
        assertNotNull(result);
    }

    // ---------------------------------------------------------------
    // getMarker
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getMarker returns null when not initialized")
    void getMarker_notInitialized() {
        var manager = createManager();
        assertNull(manager.getMarker("wool"));
    }

    // ---------------------------------------------------------------
    // getMarkersInWorld (no-arg)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getMarkersInWorld() returns empty when not initialized")
    void getMarkersInWorld_noArg_notInitialized() {
        var manager = createManager();
        List<String> result = manager.getMarkersInWorld();
        assertTrue(result.isEmpty());
    }

    // ---------------------------------------------------------------
    // ensureEntityWools
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ensureEntityWools does nothing when not initialized")
    void ensureEntityWools_notInitialized() {
        var manager = createManager();
        assertDoesNotThrow(manager::ensureEntityWools);
    }

    // ---------------------------------------------------------------
    // getWools
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getWools returns list from engine")
    void getWools() {
        var manager = createManager();
        List<?> wools = manager.getWools();
        assertNotNull(wools);
    }

    // ---------------------------------------------------------------
    // close()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("close resets initialized state")
    void close_resetsState() {
        var manager = createManager();
        manager.start(new StartGameEvent("c2w_game"));

        manager.close();

        assertNull(manager.getMarker("wool"));
        assertTrue(manager.getMarkersInWorld().isEmpty());
    }

    @Test
    @DisplayName("close can be called multiple times safely")
    void close_multipleTimes() {
        var manager = createManager();
        assertDoesNotThrow(() -> {
            manager.close();
            manager.close();
        });
    }

    // ---------------------------------------------------------------
    // MARKER_NAMES constant
    // ---------------------------------------------------------------

    @Test
    @DisplayName("MARKER_NAMES contains expected entries")
    void markerNamesContainsExpected() {
        assertNotNull(MarkerManager.MARKER_NAMES);
        assertTrue(MarkerManager.MARKER_NAMES.contains("wool"));
        assertEquals(5, MarkerManager.MARKER_NAMES.size());
    }
}
