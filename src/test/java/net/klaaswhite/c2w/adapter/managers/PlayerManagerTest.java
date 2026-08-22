package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Scoreboards;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.bootstrap.Managers;
import net.klaaswhite.c2w.bootstrap.config.PluginConfig;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.game.PlayerRegistry;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.TeamColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PlayerManager")
class PlayerManagerTest {

    private Managers managers;
    private MinecraftManager mc;
    private EventManager eventManager;
    private Players players;
    private Server server;
    private Scoreboards scoreboards;

    @BeforeEach
    void setUp() {
        managers = mock(Managers.class);
        mc = mock(MinecraftManager.class);
        eventManager = mock(EventManager.class);
        players = mock(Players.class);
        server = mock(Server.class);
        scoreboards = mock(Scoreboards.class);

        when(mc.players()).thenReturn(players);
        when(mc.server()).thenReturn(server);
        when(mc.scoreboards()).thenReturn(scoreboards);

        managers.eventManager = eventManager;
    }

    private PlayerManager createManager() {
        return new PlayerManager(managers, mc);
    }

    // ---------------------------------------------------------------
    // Constructor / smoke test
    // ---------------------------------------------------------------

    @Test
    @DisplayName("constructor registers internal and minecraft event handlers")
    void constructorRegistersEventHandlers() {
        createManager();
        verify(eventManager).registerInternalEvent(
                eq(net.klaaswhite.c2w.domain.events.DraftCreatedEvent.class), any());
        verify(eventManager).registerInternalEvent(
                eq(net.klaaswhite.c2w.domain.events.PreviewRequestEvent.class), any());
        verify(eventManager).registerInternalEvent(
                eq(net.klaaswhite.c2w.domain.events.EndGameEvent.class), any());
        verify(eventManager).registerInternalEvent(
                eq(net.klaaswhite.c2w.domain.events.ResetEvent.class), any());
        verify(eventManager).registerMinecraftEvent(
                eq(org.bukkit.event.player.PlayerJoinEvent.class), any());
        verify(eventManager).registerMinecraftEvent(
                eq(org.bukkit.event.player.PlayerChangedWorldEvent.class), any());
    }

    @Test
    @DisplayName("constructor does not throw with all mocked dependencies")
    void constructorSmokeTest() {
        assertDoesNotThrow(this::createManager);
    }

    // ---------------------------------------------------------------
    // getPlayerRegistry
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getPlayerRegistry returns non-null registry")
    void getPlayerRegistry() {
        var manager = createManager();
        assertNotNull(manager.getPlayerRegistry());
    }

    // ---------------------------------------------------------------
    // getPlayer by UUID
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getPlayer(UUID) returns null for unknown UUID")
    void getPlayerByUuid_unknown() {
        var manager = createManager();
        assertNull(manager.getPlayer(UUID.randomUUID()));
    }

    // ---------------------------------------------------------------
    // getPlayer by name
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getPlayer(String) returns null for unknown name")
    void getPlayerByName_unknown() {
        var manager = createManager();
        assertNull(manager.getPlayer("NonExistentPlayer"));
    }

    // ---------------------------------------------------------------
    // getPlayer by Bukkit Player
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getPlayer(Player) returns null for unregistered player")
    void getPlayerByBukkitPlayer_unknown() {
        var manager = createManager();
        var bukkitPlayer = mock(Player.class);
        assertNull(manager.getPlayer(bukkitPlayer));
    }

    // ---------------------------------------------------------------
    // ensureTeams
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ensureTeams unregisters existing teams and creates new ones")
    void ensureTeams() {
        var manager = createManager();

        Scoreboard scoreboard = mock(Scoreboard.class);
        when(scoreboards.getMainScoreboard()).thenReturn(scoreboard);

        var existingTeam = mock(Team.class);
        when(scoreboard.getTeams()).thenReturn(java.util.Set.of(existingTeam));

        // Call onDraftCreated which calls ensureTeams internally
        manager.onDraftCreated(new net.klaaswhite.c2w.domain.events.DraftCreatedEvent("c2w_draft"));

        verify(existingTeam).unregister();
        verify(scoreboards).createTeam("Red");
        verify(scoreboards).createTeam("Blue");
        verify(scoreboards).createTeam("Spectator");
    }

    // ---------------------------------------------------------------
    // onEndGame
    // ---------------------------------------------------------------

    @Test
    @DisplayName("onEndGame is a no-op")
    void onEndGame_noOp() {
        var manager = createManager();
        assertDoesNotThrow(() -> manager.onEndGame(new net.klaaswhite.c2w.domain.events.EndGameEvent()));
    }

    // ---------------------------------------------------------------
    // addPlayersToTeam / removePlayersFromTeam
    // ---------------------------------------------------------------

    @Test
    @DisplayName("addPlayersToTeam delegates to registry")
    void addPlayersToTeam() {
        var manager = createManager();
        // Should not throw even with empty list
        assertDoesNotThrow(() -> manager.addPlayersToTeam("Red", List.of("player1")));
    }

    @Test
    @DisplayName("removePlayersFromTeam delegates to registry")
    void removePlayersFromTeam() {
        var manager = createManager();
        assertDoesNotThrow(() -> manager.removePlayersFromTeam("Red", List.of("player1")));
    }

    // ---------------------------------------------------------------
    // findManagedWorld
    // ---------------------------------------------------------------

    @Test
    @DisplayName("findManagedWorld returns null when worldManager is null")
    void findManagedWorld_nullWorldManager() {
        managers.worldManager = null;
        var manager = createManager();
        assertNull(manager.findManagedWorld(mock(World.class)));
    }

    @Test
    @DisplayName("findManagedWorld returns null when world is null")
    void findManagedWorld_nullWorld() {
        var worldManager = mock(WorldManager.class);
        managers.worldManager = worldManager;
        var manager = createManager();
        assertNull(manager.findManagedWorld(null));
    }

    @Test
    @DisplayName("findManagedWorld returns null when world doesn't match any managed world")
    void findManagedWorld_noMatch() {
        var worldManager = mock(WorldManager.class);
        managers.worldManager = worldManager;

        var lobbyWorld = mock(ManagedWorld.class);
        when(lobbyWorld.getWorld()).thenReturn(null);
        when(worldManager.getLobbyWorld()).thenReturn(lobbyWorld);

        var referenceWorld = mock(ManagedWorld.class);
        when(referenceWorld.getWorld()).thenReturn(null);
        when(worldManager.getReferenceWorld()).thenReturn(referenceWorld);

        var draftWorld = mock(ManagedWorld.class);
        when(draftWorld.getWorld()).thenReturn(null);
        when(worldManager.getDraftWorld()).thenReturn(draftWorld);

        var gameWorld = mock(ManagedWorld.class);
        when(gameWorld.getWorld()).thenReturn(null);
        when(worldManager.getGameWorld()).thenReturn(gameWorld);

        var manager = createManager();
        var world = mock(World.class);
        assertNull(manager.findManagedWorld(world));
    }

    @Test
    @DisplayName("findManagedWorld returns matching managed world")
    void findManagedWorld_match() {
        var worldManager = mock(WorldManager.class);
        managers.worldManager = worldManager;

        var world = mock(World.class);

        var lobbyWorld = mock(ManagedWorld.class);
        when(lobbyWorld.getWorld()).thenReturn(null);
        when(worldManager.getLobbyWorld()).thenReturn(lobbyWorld);

        var referenceWorld = mock(ManagedWorld.class);
        when(referenceWorld.getWorld()).thenReturn(world);
        when(worldManager.getReferenceWorld()).thenReturn(referenceWorld);

        var draftWorld = mock(ManagedWorld.class);
        when(draftWorld.getWorld()).thenReturn(null);
        when(worldManager.getDraftWorld()).thenReturn(draftWorld);

        var gameWorld = mock(ManagedWorld.class);
        when(gameWorld.getWorld()).thenReturn(null);
        when(worldManager.getGameWorld()).thenReturn(gameWorld);

        var manager = createManager();
        assertEquals(referenceWorld, manager.findManagedWorld(world));
    }

    // ---------------------------------------------------------------
    // SPECTATOR_TEAM_NAME constant
    // ---------------------------------------------------------------

    @Test
    @DisplayName("SPECTATOR_TEAM_NAME is 'Spectator'")
    void spectatorTeamName() {
        assertEquals("Spectator", PlayerManager.SPECTATOR_TEAM_NAME);
    }

    // ---------------------------------------------------------------
    // onReset / clearAllTeams / onPlayerChangedWorld
    // ---------------------------------------------------------------

    @Test
    @DisplayName("onReset clears every player from every team and removes scoreboard teams")
    void onReset_clearsAllTeams() {
        var manager = createManager();

        // Register a player (with Bukkit mapping) and put them on a team.
        registerPlayer(manager, "Alice");
        manager.getPlayerRegistry().ensureTeams();
        manager.addPlayersToTeam("Red", java.util.List.of("Alice"));
        assertEquals("Red", manager.getPlayer("Alice").getTeam().teamName);

        manager.onReset(new net.klaaswhite.c2w.domain.events.ResetEvent());

        assertNull(manager.getPlayer("Alice").getTeam());
        verify(scoreboards).removeTeam("Red");
        verify(scoreboards).removeTeam("Blue");
        verify(scoreboards).removeTeam("Spectator");
    }

    @Test
    @DisplayName("onPlayerChangedWorld into the lobby removes the player from their team")
    void onPlayerChangedWorld_intoLobby_clearsTeam() {
        var manager = createManager();
        var worldManager = mock(WorldManager.class);
        managers.worldManager = worldManager;

        var lobbyWorld = mock(World.class);
        when(lobbyWorld.getName()).thenReturn("c2w_lobby");
        var lobby = mock(ManagedWorld.class);
        when(lobby.getName()).thenReturn("c2w_lobby");
        when(lobby.getWorld()).thenReturn(lobbyWorld);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);

        // Register "Alice" with a Bukkit player handle so the world-change
        // handler can look them up by Bukkit Player.
        var player = registerPlayer(manager, "Alice");
        when(player.getWorld()).thenReturn(lobbyWorld);
        manager.getPlayerRegistry().ensureTeams();
        manager.addPlayersToTeam("Red", java.util.List.of("Alice"));
        assertEquals("Red", manager.getPlayer("Alice").getTeam().teamName);

        var event = new org.bukkit.event.player.PlayerChangedWorldEvent(player, mock(World.class));
        manager.onPlayerChangedWorld(event);

        assertNull(manager.getPlayer("Alice").getTeam());
        verify(scoreboards).removePlayerFromTeam("Alice", "Red");
    }

    @Test
    @DisplayName("onPlayerChangedWorld into a non-lobby world keeps the team")
    void onPlayerChangedWorld_notLobby_keepsTeam() {
        var manager = createManager();
        var worldManager = mock(WorldManager.class);
        managers.worldManager = worldManager;

        var lobbyWorld = mock(World.class);
        when(lobbyWorld.getName()).thenReturn("c2w_lobby");
        var lobby = mock(ManagedWorld.class);
        when(lobby.getName()).thenReturn("c2w_lobby");
        when(lobby.getWorld()).thenReturn(lobbyWorld);
        when(worldManager.getLobbyWorld()).thenReturn(lobby);

        var player = registerPlayer(manager, "Alice");
        // Player moves into a game world instead of the lobby.
        var gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("c2w_game");
        when(player.getWorld()).thenReturn(gameWorld);

        manager.getPlayerRegistry().ensureTeams();
        manager.addPlayersToTeam("Red", java.util.List.of("Alice"));
        assertEquals("Red", manager.getPlayer("Alice").getTeam().teamName);

        var event = new org.bukkit.event.player.PlayerChangedWorldEvent(player, mock(World.class));
        manager.onPlayerChangedWorld(event);

        assertEquals("Red", manager.getPlayer("Alice").getTeam().teamName);
        verify(scoreboards, never()).removePlayerFromTeam(anyString(), anyString());
    }

    // ---------------------------------------------------------------
    // close()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("close clears all state")
    void close_clearsState() {
        var manager = createManager();
        manager.close();

        // Registry should be reset
        assertNull(manager.getPlayer(UUID.randomUUID()));
        assertNull(manager.getPlayer("anyone"));
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
    // helpers
    // ---------------------------------------------------------------

    /**
     * Register a player through the exact ensurePlayers() flow used in
     * production so the {@code playersByBukkitPlayer} map is populated too.
     */
    private Player registerPlayer(PlayerManager manager, String name) {
        var player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(server.getOnlinePlayerNames()).thenReturn(java.util.List.of(name));
        when(players.getHandle(name)).thenReturn(player);

        manager.ensurePlayers();
        assertNotNull(manager.getPlayer(player), "player should be wired into the map");
        return player;
    }
}
