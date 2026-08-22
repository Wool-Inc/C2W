package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.*;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Worlds;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.events.WoolDroppedEvent;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.TeamColor;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BoundaryManager")
class BoundaryManagerTest {

    private EventManager eventManager;
    private MarkerManager markerManager;
    private PlayerManager playerManager;
    private WoolTimer woolTimer;
    private BoundaryManager boundaryManager;

    @BeforeEach
    void setUp() {
        ManagedTeam.teams.clear();

        eventManager = mock(EventManager.class);
        markerManager = mock(MarkerManager.class);
        playerManager = mock(PlayerManager.class);
        woolTimer = mock(WoolTimer.class);

        when(markerManager.getMarker(anyString())).thenReturn(null);

        boundaryManager = new BoundaryManager(eventManager, markerManager, playerManager, woolTimer);
    }

    // --- Construction ---

    @Test
    @DisplayName("constructs without exception")
    void constructs() {
        assertNotNull(boundaryManager);
    }

    // --- onStartGame ---

    @Test
    @DisplayName("onStartGame handles no teams and no markers")
    void onStartGameEmpty() {
        var event = new StartGameEvent("c2w_game");
        assertDoesNotThrow(() -> boundaryManager.onStartGame(event));
    }

    @Test
    @DisplayName("onStartGame handles teams but no markers")
    void onStartGameWithTeamsNoMarkers() {
        ManagedTeam.teams.put("Red", new ManagedTeam("Red", TeamColor.RED));
        ManagedTeam.teams.put("Blue", new ManagedTeam("Blue", TeamColor.BLUE));

        var event = new StartGameEvent("c2w_game");
        assertDoesNotThrow(() -> boundaryManager.onStartGame(event));
    }

    @Test
    @DisplayName("onStartGame initialises pit boxes when markers exist")
    void onStartGameWithPitMarkers() {
        ManagedTeam.teams.put("Red", new ManagedTeam("Red", TeamColor.RED));

        var marker1 = mock(MarkerEntity.class);
        var marker2 = mock(MarkerEntity.class);
        when(marker1.getPosition()).thenReturn(new BlockPos(0, 0, 0));
        when(marker2.getPosition()).thenReturn(new BlockPos(10, 10, 10));
        when(markerManager.getMarker("boundary-woolcap-pit-1")).thenReturn(marker1);
        when(markerManager.getMarker("boundary-woolcap-pit-2")).thenReturn(marker2);

        var event = new StartGameEvent("c2w_game");
        assertDoesNotThrow(() -> boundaryManager.onStartGame(event));
    }

    @Test
    @DisplayName("onStartGame initialises elevator boxes when markers exist")
    void onStartGameWithElevatorMarkers() {
        ManagedTeam.teams.put("Red", new ManagedTeam("Red", TeamColor.RED));

        var marker1 = mock(MarkerEntity.class);
        var marker2 = mock(MarkerEntity.class);
        when(marker1.getPosition()).thenReturn(new BlockPos(0, 0, 0));
        when(marker2.getPosition()).thenReturn(new BlockPos(15, 15, 15));
        when(markerManager.getMarker("boundary-woolcap-elevator-1")).thenReturn(marker1);
        when(markerManager.getMarker("boundary-woolcap-elevator-2")).thenReturn(marker2);

        var event = new StartGameEvent("c2w_game");
        assertDoesNotThrow(() -> boundaryManager.onStartGame(event));
    }

    @Test
    @DisplayName("onStartGame initialises both pit and elevator boxes")
    void onStartGameWithAllMarkers() {
        ManagedTeam.teams.put("Red", new ManagedTeam("Red", TeamColor.RED));
        ManagedTeam.teams.put("Blue", new ManagedTeam("Blue", TeamColor.BLUE));

        var pit1 = mock(MarkerEntity.class);
        var pit2 = mock(MarkerEntity.class);
        var ele1 = mock(MarkerEntity.class);
        var ele2 = mock(MarkerEntity.class);
        when(pit1.getPosition()).thenReturn(new BlockPos(0, 0, 0));
        when(pit2.getPosition()).thenReturn(new BlockPos(10, 10, 10));
        when(ele1.getPosition()).thenReturn(new BlockPos(20, 20, 20));
        when(ele2.getPosition()).thenReturn(new BlockPos(30, 30, 30));
        when(markerManager.getMarker("boundary-woolcap-pit-1")).thenReturn(pit1);
        when(markerManager.getMarker("boundary-woolcap-pit-2")).thenReturn(pit2);
        when(markerManager.getMarker("boundary-woolcap-elevator-1")).thenReturn(ele1);
        when(markerManager.getMarker("boundary-woolcap-elevator-2")).thenReturn(ele2);

        var event = new StartGameEvent("c2w_game");
        assertDoesNotThrow(() -> boundaryManager.onStartGame(event));
    }

    // --- onPlayerMove ---

    @Test
    @DisplayName("onPlayerMove handles null to location")
    void onPlayerMoveNullTo() {
        var event = mock(PlayerMoveEvent.class);
        var from = mock(Location.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(null);

        assertDoesNotThrow(() -> boundaryManager.onPlayerMove(event));
    }

    @Test
    @DisplayName("onPlayerMove handles unknown player")
    void onPlayerMoveUnknownPlayer() {
        var event = mock(PlayerMoveEvent.class);
        var player = mock(Player.class);
        var from = mock(Location.class);
        var to = mock(Location.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);
        when(playerManager.getPlayer(player)).thenReturn(null);

        assertDoesNotThrow(() -> boundaryManager.onPlayerMove(event));
    }

    @Test
    @DisplayName("onPlayerMove handles known player with no bounding boxes")
    void onPlayerMoveKnownPlayerNoBoxes() {
        var event = mock(PlayerMoveEvent.class);
        var player = mock(Player.class);
        var from = mock(Location.class);
        var to = mock(Location.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        var managedPlayer = new ManagedPlayer(handle);
        when(playerManager.getPlayer(player)).thenReturn(managedPlayer);

        assertDoesNotThrow(() -> boundaryManager.onPlayerMove(event));
    }

    // --- onWoolDropped ---

    @Test
    @DisplayName("onWoolDropped handles null wool")
    void onWoolDroppedNull() {
        var event = new WoolDroppedEvent(null);
        assertDoesNotThrow(() -> boundaryManager.onWoolDropped(event));
    }

    @Test
    @DisplayName("onWoolDropped handles valid wool event")
    void onWoolDroppedValid() {
        var mc = createMockMc();
        var wool = new Wool(mc, new net.klaaswhite.c2w.domain.game.WoolTimer(new net.klaaswhite.c2w.domain.game.WoolTimer.Scheduler() {
            public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
            public void cancel(Object taskId) {}
        }), WoolColor.RED, new BlockPos(0, 64, 0), "game");
        var event = new WoolDroppedEvent(wool);
        assertDoesNotThrow(() -> boundaryManager.onWoolDropped(event));
    }

    // --- onWoolCaptured ---

    @Test
    @DisplayName("onWoolCaptured handles null wool")
    void onWoolCapturedNull() {
        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        var player = new ManagedPlayer(handle);

        var event = new WoolCapturedEvent(player, null);
        assertDoesNotThrow(() -> boundaryManager.onWoolCaptured(event));
    }

    @Test
    @DisplayName("onWoolCaptured handles valid wool event")
    void onWoolCapturedValid() {
        var mc = createMockMc();
        var wool = new Wool(mc, new net.klaaswhite.c2w.domain.game.WoolTimer(new net.klaaswhite.c2w.domain.game.WoolTimer.Scheduler() {
            public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
            public void cancel(Object taskId) {}
        }), WoolColor.RED, new BlockPos(0, 64, 0), "game");
        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        var player = new ManagedPlayer(handle);

        var event = new WoolCapturedEvent(player, wool);
        assertDoesNotThrow(() -> boundaryManager.onWoolCaptured(event));
    }

    // --- close ---

    @Test
    @DisplayName("close does not throw")
    void close() {
        assertDoesNotThrow(() -> boundaryManager.close());
    }

    // --- Helper ---

    private static MinecraftManager createMockMc() {
        var mc = mock(MinecraftManager.class);
        var bossBars = mock(BossBars.class);
        var bossBar = mock(BossBar.class);
        var server = mock(Server.class);
        var players = mock(Players.class);
        var worlds = mock(Worlds.class);
        when(mc.bossBars()).thenReturn(bossBars);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);
        when(mc.server()).thenReturn(server);
        when(mc.players()).thenReturn(players);
        when(mc.worlds()).thenReturn(worlds);
        return mc;
    }
}