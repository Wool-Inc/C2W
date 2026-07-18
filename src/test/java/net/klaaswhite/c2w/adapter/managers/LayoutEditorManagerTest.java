package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Scoreboards;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Worlds;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("LayoutEditorManager")
class LayoutEditorManagerTest {

    private JavaPlugin plugin;
    private EventManager eventManager;
    private WorldManager worldManager;
    private StructureManager structureManager;
    private FolderStructureTypeConfig structureTypeConfig;
    private MinecraftManager mc;

    private Players players;
    private Server server;
    private Worlds worlds;
    private Scoreboards scoreboards;
    private ManagedWorld lobbyWorld;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("c2w");
        eventManager = mock(EventManager.class);
        worldManager = mock(WorldManager.class);
        structureManager = mock(StructureManager.class);
        structureTypeConfig = mock(FolderStructureTypeConfig.class);
        mc = mock(MinecraftManager.class);

        players = mock(Players.class);
        server = mock(Server.class);
        worlds = mock(Worlds.class);
        scoreboards = mock(Scoreboards.class);

        when(mc.players()).thenReturn(players);
        when(mc.server()).thenReturn(server);
        when(mc.worlds()).thenReturn(worlds);
        when(mc.scoreboards()).thenReturn(scoreboards);

        Scoreboard mockScoreboard = mock(Scoreboard.class);
        when(scoreboards.getMainScoreboard()).thenReturn(mockScoreboard);
        when(mockScoreboard.getTeam(anyString())).thenReturn(null);
        Team mockTeam = mock(Team.class);
        when(mockScoreboard.registerNewTeam(anyString())).thenReturn(mockTeam);
        when(mockScoreboard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(mock(org.bukkit.scoreboard.Objective.class));

        lobbyWorld = mock(ManagedWorld.class);
        when(lobbyWorld.getName()).thenReturn("c2w_lobby");
        when(lobbyWorld.getSpawnPos()).thenReturn(new BlockPos(0, 65, 0));
        when(worldManager.getLobbyWorld()).thenReturn(lobbyWorld);
    }

    private LayoutEditorManager createManager() {
        return new LayoutEditorManager(
                plugin, eventManager, worldManager, structureManager,
                structureTypeConfig, mc, tempDir.toFile()
        );
    }

    // ---------------------------------------------------------------
    // Constructor / smoke test
    // ---------------------------------------------------------------

    @Test
    @DisplayName("constructor registers PlayerTeleportEvent handler")
    void constructorRegistersTeleportHandler() {
        createManager();
        verify(eventManager).registerMinecraftEvent(eq(org.bukkit.event.player.PlayerTeleportEvent.class), any());
    }

    @Test
    @DisplayName("constructor does not throw with all mocked dependencies")
    void constructorSmokeTest() {
        assertDoesNotThrow(this::createManager);
    }

    // ---------------------------------------------------------------
    // createEditorWorld
    // ---------------------------------------------------------------

    @Test
    @DisplayName("createEditorWorld sends error when session already exists")
    void createEditorWorld_alreadyExists() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        // First call creates the session
        var world = mock(World.class);
        when(worlds.createVoidWorld("c2w_layout_testLayout", World.Environment.NORMAL)).thenReturn(world);
        manager.createEditorWorld(player, "testLayout");

        // Second call should fail
        manager.createEditorWorld(player, "testLayout");
        verify(player).sendMessage(contains("already exists"));
    }

    @Test
    @DisplayName("createEditorWorld sends error when world creation fails")
    void createEditorWorld_worldCreationFails() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        when(worlds.createVoidWorld("c2w_layout_testLayout", World.Environment.NORMAL)).thenReturn(null);

        manager.createEditorWorld(player, "testLayout");
        verify(player).sendMessage(contains("Failed to create editor world"));
    }

    @Test
    @DisplayName("createEditorWorld creates world, session, and teleports player")
    void createEditorWorld_success() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        var world = mock(World.class);
        when(worlds.createVoidWorld("c2w_layout_myMap", World.Environment.NORMAL)).thenReturn(world);

        manager.createEditorWorld(player, "myMap");

        verify(worlds).createVoidWorld("c2w_layout_myMap", World.Environment.NORMAL);
        verify(players).teleportToWorld(eq("TestPlayer"), any(BlockPos.class), eq("c2w_layout_myMap"));
        verify(player).sendMessage(contains("Layout editor world created"));
    }

    // ---------------------------------------------------------------
    // placeStructure
    // ---------------------------------------------------------------

    @Test
    @DisplayName("placeStructure sends error when player world is null")
    void placeStructure_nullWorld() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn(null);

        manager.placeStructure(player, "dungeon");
        verify(player).sendMessage(contains("Could not determine your world"));
    }

    @Test
    @DisplayName("placeStructure sends error when not in editor world")
    void placeStructure_notInEditorWorld() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn("some_other_world");

        manager.placeStructure(player, "dungeon");
        verify(player).sendMessage(contains("not in a layout editor world"));
    }

    @Test
    @DisplayName("placeStructure sends error when placement would overlap existing")
    void placeStructure_overlap() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        var world = mock(World.class);
        when(worlds.createVoidWorld("c2w_layout_test", World.Environment.NORMAL)).thenReturn(world);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        manager.createEditorWorld(player, "test");

        when(players.getWorldName("TestPlayer")).thenReturn("c2w_layout_test");
        Location mockLoc = mock(Location.class);
        when(mockLoc.getYaw()).thenReturn(0f);
        when(player.getLocation()).thenReturn(mockLoc);
        when(players.getPosition("TestPlayer")).thenReturn(new BlockPos(5, 65, 5));
        when(structureManager.hasType("dungeon")).thenReturn(true);
        when(structureTypeConfig.getDimensions("dungeon")).thenReturn(new int[]{16, 10, 16});

        // First placement succeeds
        manager.placeStructure(player, "dungeon");

        // Second placement at same position should overlap
        manager.placeStructure(player, "dungeon");
        verify(player).sendMessage(contains("Cannot place here: would overlap"));
    }

    @Test
    @DisplayName("placeStructure sends error when no templates available for type")
    void placeStructure_noTemplates() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        var world = mock(World.class);
        when(worlds.createVoidWorld("c2w_layout_test", World.Environment.NORMAL)).thenReturn(world);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        manager.createEditorWorld(player, "test");

        when(players.getWorldName("TestPlayer")).thenReturn("c2w_layout_test");
        Location mockLoc = mock(Location.class);
        when(mockLoc.getYaw()).thenReturn(0f);
        when(player.getLocation()).thenReturn(mockLoc);
        when(players.getPosition("TestPlayer")).thenReturn(new BlockPos(5, 65, 5));
        when(structureManager.hasType("dungeon")).thenReturn(false);

        manager.placeStructure(player, "dungeon");
        verify(player).sendMessage(contains("No templates available"));
    }

    @Test
    @DisplayName("placeStructure places structure and confirms to player")
    void placeStructure_success() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        // Create the editor world first
        var world = mock(World.class);
        when(worlds.createVoidWorld("c2w_layout_test", World.Environment.NORMAL)).thenReturn(world);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        manager.createEditorWorld(player, "test");

        // Player is now in the editor world
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_layout_test");
        Location mockLoc = mock(Location.class);
        when(mockLoc.getYaw()).thenReturn(90f);
        when(player.getLocation()).thenReturn(mockLoc);
        when(players.getPosition("TestPlayer")).thenReturn(new BlockPos(5, 65, 5));
        when(structureManager.hasType("dungeon")).thenReturn(true);

        manager.placeStructure(player, "dungeon");
        verify(player).sendMessage(contains("Placed dungeon/"));
    }

    // ---------------------------------------------------------------
    // saveLayout
    // ---------------------------------------------------------------

    @Test
    @DisplayName("saveLayout sends error when player world is null")
    void saveLayout_nullWorld() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn(null);

        manager.saveLayout(player);
        verify(player).sendMessage(contains("Could not determine your world"));
    }

    @Test
    @DisplayName("saveLayout sends error when not in editor world")
    void saveLayout_notInEditorWorld() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn("some_world");

        manager.saveLayout(player);
        verify(player).sendMessage(contains("not in a layout editor world"));
    }

    @Test
    @DisplayName("saveLayout saves and cleans up session")
    void saveLayout_success() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        // Create the editor world
        var world = mock(World.class);
        when(worlds.createVoidWorld("c2w_layout_myMap", World.Environment.NORMAL)).thenReturn(world);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        manager.createEditorWorld(player, "myMap");

        // Place a structure so the layout is non-empty
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_layout_myMap");
        Location mockLoc = mock(Location.class);
        when(mockLoc.getYaw()).thenReturn(180f);
        when(player.getLocation()).thenReturn(mockLoc);
        when(players.getPosition("TestPlayer")).thenReturn(new BlockPos(5, 65, 5));
        when(structureManager.hasType("dungeon")).thenReturn(true);
        manager.placeStructure(player, "dungeon");

        // Save
        when(server.getOnlinePlayerNames()).thenReturn(java.util.List.of());
        manager.saveLayout(player);

        verify(player).sendMessage(contains("saved"));
        verify(worlds).unloadWorld("c2w_layout_myMap");
        verify(worlds).deleteWorld("c2w_layout_myMap");
    }

    // ---------------------------------------------------------------
    // discardEditor
    // ---------------------------------------------------------------

    @Test
    @DisplayName("discardEditor sends error when not in editor world")
    void discardEditor_notInEditorWorld() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn("other_world");

        manager.discardEditor(player);
        verify(player).sendMessage(contains("not in a layout editor world"));
    }

    @Test
    @DisplayName("discardEditor cleans up session")
    void discardEditor_success() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        // Create the editor world
        var world = mock(World.class);
        when(worlds.createVoidWorld("c2w_layout_myMap", World.Environment.NORMAL)).thenReturn(world);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        manager.createEditorWorld(player, "myMap");

        // Player is now in the editor world
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_layout_myMap");
        // Discard
        when(server.getOnlinePlayerNames()).thenReturn(java.util.List.of());
        manager.discardEditor(player);

        verify(player).sendMessage(contains("discarded"));
        verify(worlds).unloadWorld("c2w_layout_myMap");
        verify(worlds).deleteWorld("c2w_layout_myMap");
    }

    // ---------------------------------------------------------------
    // removeStructure (placeholder)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("removeStructure sends error when player not in editor world")
    void removeStructure_notInEditorWorld() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn(null);

        manager.removeStructure(player);
        verify(player).sendMessage(contains("Could not determine your world"));
    }

    // ---------------------------------------------------------------
    // rotateStructure
    // ---------------------------------------------------------------

    @Test
    @DisplayName("rotateStructure sends error when player not in editor world")
    void rotateStructure_notInEditorWorld() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn(null);

        manager.rotateStructure(player, 90f);
        verify(player).sendMessage(contains("Could not determine your world"));
    }

    @Test
    @DisplayName("rotateStructure sends error for invalid index")
    void rotateStructure_invalidIndex() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_layout_test");

        // No session exists, so it should fail
        manager.rotateStructure(player, 90f);
        verify(player).sendMessage(contains("You are not in a layout editor world"));
    }

    // ---------------------------------------------------------------
    // moveStructure
    // ---------------------------------------------------------------

    @Test
    @DisplayName("moveStructure sends error when player not in editor world")
    void moveStructure_notInEditorWorld() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn(null);

        manager.moveStructure(player, 1, 0, 0);
        verify(player).sendMessage(contains("Could not determine your world"));
    }

    @Test
    @DisplayName("moveStructure sends error for invalid index")
    void moveStructure_invalidIndex() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_layout_test");

        // No session exists, so it should fail
        manager.moveStructure(player, 1, 0, 0);
        verify(player).sendMessage(contains("You are not in a layout editor world"));
    }

    // ---------------------------------------------------------------
    // close()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("close cleans up all active sessions")
    void close_cleansUpAllSessions() {
        var manager = createManager();
        var player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");

        // Create two editor worlds
        var world1 = mock(World.class);
        var world2 = mock(World.class);
        when(worlds.createVoidWorld("c2w_layout_map1", World.Environment.NORMAL)).thenReturn(world1);
        when(worlds.createVoidWorld("c2w_layout_map2", World.Environment.NORMAL)).thenReturn(world2);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        manager.createEditorWorld(player, "map1");
        manager.createEditorWorld(player, "map2");

        when(server.getOnlinePlayerNames()).thenReturn(java.util.List.of());

        manager.close();

        verify(worlds).unloadWorld("c2w_layout_map1");
        verify(worlds).deleteWorld("c2w_layout_map1");
        verify(worlds).unloadWorld("c2w_layout_map2");
        verify(worlds).deleteWorld("c2w_layout_map2");
    }

    @Test
    @DisplayName("close on empty manager does not throw")
    void close_emptyManager() {
        var manager = createManager();
        assertDoesNotThrow(manager::close);
    }
}
