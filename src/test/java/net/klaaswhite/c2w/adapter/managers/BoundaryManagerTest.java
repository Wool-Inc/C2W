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
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BoundaryManager")
class BoundaryManagerTest {

    private EventManager eventManager;
    private MarkerManager markerManager;
    private PlayerManager playerManager;
    private WoolTimer woolTimer;
    private MinecraftManager mc;
    private Players players;
    private BoundaryManager boundaryManager;

    @BeforeEach
    void setUp() {
        ManagedTeam.teams.clear();

        eventManager = mock(EventManager.class);
        markerManager = mock(MarkerManager.class);
        playerManager = mock(PlayerManager.class);
        woolTimer = mock(WoolTimer.class);
        mc = mock(MinecraftManager.class);
        players = mock(Players.class);
        when(mc.players()).thenReturn(players);

        when(markerManager.getMarker(anyString())).thenReturn(null);

        boundaryManager = new BoundaryManager(eventManager, markerManager, playerManager, woolTimer, mc);
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

    @Test
    @DisplayName("onPlayerMove kills game-world players below the death plane")
    void onPlayerMoveKillsBelowDeathPlane() {
        var player = mock(Player.class);
        var from = mock(Location.class);
        var to = mock(Location.class);
        var world = mock(World.class);
        when(world.getName()).thenReturn("c2w_game");
        when(to.getWorld()).thenReturn(world);
        when(to.getY()).thenReturn(53.9);

        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        var managedPlayer = new ManagedPlayer(handle);
        when(playerManager.getPlayer(player)).thenReturn(managedPlayer);
        when(player.getName()).thenReturn("Alice");

        var event = mock(PlayerMoveEvent.class);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);

        boundaryManager.onStartGame(new StartGameEvent("c2w_game", 54));
        boundaryManager.onPlayerMove(event);

        org.mockito.Mockito.verify(players).setHealth("Alice", 0.0);
    }

    @Test
    @DisplayName("onPlayerMove teleports players below the lobby and draft death plane")
    void onPlayerMoveTeleportsBelowLobbyAndDraftDeathPlane() {
        for (String worldName : new String[]{"c2w_lobby", "c2w_draft"}) {
            var player = mock(Player.class);
            var from = mock(Location.class);
            var to = mock(Location.class);
            var world = mock(World.class);
            when(world.getName()).thenReturn(worldName);
            when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 65, 0));
            when(to.getWorld()).thenReturn(world);
            when(to.getY()).thenReturn(53.9);
            when(player.getName()).thenReturn(worldName);

            var handle = mock(PlayerHandle.class);
            when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
            when(handle.getName()).thenReturn(worldName);
            when(handle.getDisplayName()).thenReturn(worldName);
            when(playerManager.getPlayer(player)).thenReturn(new ManagedPlayer(handle));

            var event = mock(PlayerMoveEvent.class);
            when(event.getFrom()).thenReturn(from);
            when(event.getTo()).thenReturn(to);
            when(event.getPlayer()).thenReturn(player);

            boundaryManager.onPlayerMove(event);

            verify(players).teleportToWorld(worldName, new BlockPos(0, 65, 0), worldName);
        }

        verify(players, never()).setHealth(anyString(), eq(0.0));
    }

    // --- onPlayerDeath ---

    @Test
    @DisplayName("onPlayerDeath handles null managed player")
    void onPlayerDeathUnknownPlayer() {
        var event = mock(PlayerDeathEvent.class);
        var player = mock(Player.class);
        when(event.getEntity()).thenReturn(player);
        when(playerManager.getPlayer(player)).thenReturn(null);

        assertDoesNotThrow(() -> boundaryManager.onPlayerDeath(event));
    }

    @Test
    @DisplayName("onPlayerDeath drops and resets the carried wool")
    void onPlayerDeathDropsCarriedWool() {
        var mc = createMockMc();
        var woolTimer = new net.klaaswhite.c2w.domain.game.WoolTimer(
                new net.klaaswhite.c2w.domain.game.WoolTimer.Scheduler() {
                    public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
                    public void cancel(Object taskId) {}
                });
        var wool = new Wool(mc, woolTimer, WoolColor.RED, new BlockPos(0, 64, 0), "game");

        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        when(handle.getPosition()).thenReturn(new BlockPos(10, 64, 10));
        var managedPlayer = new ManagedPlayer(handle);
        assertTrue(wool.pickup(managedPlayer));
        assertSame(wool, managedPlayer.getCarry());
        assertTrue(wool.isCarried());

        var event = mock(PlayerDeathEvent.class);
        var player = mock(Player.class);
        when(event.getEntity()).thenReturn(player);
        when(playerManager.getPlayer(player)).thenReturn(managedPlayer);

        boundaryManager.onPlayerDeath(event);

        // dropOnDeath must clear the carrier, making the wool no longer carried.
        assertNull(managedPlayer.getCarry());
        assertFalse(wool.isCarried());
    }

    @Test
    @DisplayName("onPlayerDeath immediately respawns game-world players")
    void onPlayerDeathImmediatelyRespawnsGamePlayer() {
        var player = mock(Player.class);
        var world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("c2w_game");
        when(player.getName()).thenReturn("Alice");
        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        var managedPlayer = new ManagedPlayer(handle);
        when(playerManager.getPlayer(player)).thenReturn(managedPlayer);

        var event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);

        boundaryManager.onStartGame(new StartGameEvent("c2w_game", 64));
        boundaryManager.onPlayerDeath(event);

        verify(players).respawn("Alice");
    }

    @Test
    @DisplayName("onPlayerQuit removes player from the pit but keeps the wool on them")
    void onPlayerQuitKeepsWoolRemovesFromPit() {
        var mc = createMockMc();
        var woolTimer = new net.klaaswhite.c2w.domain.game.WoolTimer(
                new net.klaaswhite.c2w.domain.game.WoolTimer.Scheduler() {
                    public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
                    public void cancel(Object taskId) {}
                });
        var wool = new Wool(mc, woolTimer, WoolColor.RED, new BlockPos(0, 64, 0), "game");

        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        when(handle.getPosition()).thenReturn(new BlockPos(10, 64, 10));
        var managedPlayer = new ManagedPlayer(handle);
        assertTrue(wool.pickup(managedPlayer));

        // Put the player in a capture pit so quitting would normally remove them.
        ManagedTeam.teams.put("Red", new ManagedTeam("Red", TeamColor.RED));
        managedPlayer.setTeam(ManagedTeam.teams.get("Red"));
        var marker1 = mock(MarkerEntity.class);
        var marker2 = mock(MarkerEntity.class);
        when(marker1.getPosition()).thenReturn(new BlockPos(0, 0, 0));
        when(marker2.getPosition()).thenReturn(new BlockPos(10, 10, 10));
        when(markerManager.getMarker("boundary-woolcap-pit-1")).thenReturn(marker1);
        when(markerManager.getMarker("boundary-woolcap-pit-2")).thenReturn(marker2);
        boundaryManager.onStartGame(new StartGameEvent("c2w_game"));

        var event = mock(PlayerQuitEvent.class);
        var player = mock(Player.class);
        when(event.getPlayer()).thenReturn(player);
        when(playerManager.getPlayer(player)).thenReturn(managedPlayer);

        boundaryManager.onPlayerQuit(event);

        // Wool stays carried (kept on the player) on disconnect.
        assertSame(wool, managedPlayer.getCarry());
        assertTrue(wool.isCarried());
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