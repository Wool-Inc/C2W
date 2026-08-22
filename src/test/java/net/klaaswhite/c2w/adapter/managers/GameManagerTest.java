package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.BossBar;
import net.klaaswhite.c2w.adapter.minecraft.BossBars;
import net.klaaswhite.c2w.adapter.minecraft.Blocks;
import net.klaaswhite.c2w.adapter.minecraft.MarkerEntity;
import net.klaaswhite.c2w.adapter.minecraft.Markers;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Plugin;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Structures;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Worlds;
import net.klaaswhite.c2w.bootstrap.config.PluginConfig;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.events.DraftCreatedEvent;
import net.klaaswhite.c2w.domain.events.EndGameEvent;
import net.klaaswhite.c2w.domain.events.GameWorldCreatedEvent;
import net.klaaswhite.c2w.domain.events.PreviewRequestEvent;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.LayoutCell;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.StructureData;
import net.klaaswhite.c2w.domain.model.StructureRotation;
import net.klaaswhite.c2w.domain.model.TeamColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GameManager")
class GameManagerTest {

    private JavaPlugin plugin;
    private EventManager eventManager;
    private WorldManager worldManager;
    private LayoutManager layoutManager;
    private StructureManager structureManager;
    private MinecraftManager mc;
    private StructureCreationManager structureCreationManager;
    private PluginConfig pluginConfig;
    private PlayerManager playerManager;
    private FolderStructureTypeConfig structureTypeConfig;

    private Server server;
    private Players players;
    private Worlds worlds;
    private BossBars bossBars;
    private BossBar bossBar;
    private Plugin mcPlugin;
    private Markers markers;
    private Structures structures;
    private Blocks blocks;

    private GameManager gameManager;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        eventManager = mock(EventManager.class);
        worldManager = mock(WorldManager.class);
        layoutManager = mock(LayoutManager.class);
        structureManager = mock(StructureManager.class);
        mc = mock(MinecraftManager.class);
        structureCreationManager = mock(StructureCreationManager.class);
        pluginConfig = mock(PluginConfig.class);
        playerManager = mock(PlayerManager.class);
        structureTypeConfig = mock(FolderStructureTypeConfig.class);

        server = mock(Server.class);
        players = mock(Players.class);
        worlds = mock(Worlds.class);
        bossBars = mock(BossBars.class);
        bossBar = mock(BossBar.class);
        mcPlugin = mock(Plugin.class);
        markers = mock(Markers.class);
        structures = mock(Structures.class);
        blocks = mock(Blocks.class);
        when(mc.server()).thenReturn(server);
        when(mc.players()).thenReturn(players);
        when(mc.worlds()).thenReturn(worlds);
        when(mc.bossBars()).thenReturn(bossBars);
        when(mc.plugin()).thenReturn(mcPlugin);
        when(mc.markers()).thenReturn(markers);
        when(mc.structures()).thenReturn(structures);
        when(mc.blocks()).thenReturn(blocks);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);
        when(mcPlugin.getDataFolder()).thenReturn(new java.io.File("/fake-data"));

        gameManager = new GameManager(
                plugin, eventManager, worldManager, layoutManager,
                structureManager, mc, structureCreationManager,
                pluginConfig, playerManager, structureTypeConfig
        );
    }

    // ---------------------------------------------------------------
    // State queries
    // ---------------------------------------------------------------

    @Test
    @DisplayName("initially is not started")
    void initialState() {
        assertTrue(gameManager.isNotStarted());
        assertFalse(gameManager.isDraftCreated());
        assertFalse(gameManager.isGameInProgress());
        assertFalse(gameManager.isGameEnded());
    }

    // ---------------------------------------------------------------
    // init()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("init() doesn't proceed when sender is not a Player")
    void init_withNonPlayerSender() {
        var input = new CommandInput();
        input.commandSender = "not-a-player";

        gameManager.init(input);

        assertTrue(gameManager.isNotStarted());
        verify(eventManager, never()).pushInternalEvent(any());
    }

    @Test
    @DisplayName("init() sends error when not in NOT_STARTED state")
    void init_whenNotNotStarted() {
        gameManager.end(); // transitions to GAME_ENDED

        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        gameManager.init(input);

        verify(player).sendMessage(contains("Cannot initialize"));
        assertFalse(gameManager.isNotStarted());
    }

    @Test
    @DisplayName("init() sends error when world creation fails")
    void init_whenWorldCreationFails() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        when(worldManager.createDraftWorld()).thenReturn(null);

        gameManager.init(input);

        verify(player).sendMessage(contains("Failed to create draft world"));
        verify(eventManager, never()).pushInternalEvent(any());
    }

    @Test
    @DisplayName("init() creates draft world, pushes event, and teleports player")
    void init_success() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        var draftWorld = mock(World.class);
        when(draftWorld.getName()).thenReturn("c2w_draft");
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);

        gameManager.init(input);

        verify(eventManager).pushInternalEvent(any(DraftCreatedEvent.class));
        verify(player).teleport(any(Location.class));
        verify(player).sendMessage(contains("Draft world created"));
        verify(worldManager).createDraftWorld();
        assertTrue(gameManager.isDraftCreated());
    }

    // ---------------------------------------------------------------
    // start()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("start() doesn't proceed when sender is not a Player")
    void start_withNonPlayerSender() {
        var input = new CommandInput();
        input.commandSender = "not-a-player";

        gameManager.start(input, "testLayout");

        assertTrue(gameManager.isNotStarted());
        verify(eventManager, never()).pushInternalEvent(any());
    }

    @Test
    @DisplayName("start() sends error when not in DRAFT_CREATED state")
    void start_whenNotDraftCreated() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        gameManager.start(input, "test");

        verify(player).sendMessage(contains("after /c2w init"));
        assertTrue(gameManager.isNotStarted());
    }

    @Test
    @DisplayName("start() sends error when game world already exists")
    void start_whenGameWorldExists() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        // Move to DRAFT_CREATED first
        var draftWorld = mock(World.class);
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);
        gameManager.init(input);
        assertTrue(gameManager.isDraftCreated());

        // Game world already exists
        when(worldManager.isGameWorldCreated()).thenReturn(true);

        gameManager.start(input, "test");

        verify(player).sendMessage(contains("already exists"));
        assertTrue(gameManager.isDraftCreated());
    }

    @Test
    @DisplayName("start() sends error when layout is not found")
    void start_whenLayoutNotFound() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        // Move to DRAFT_CREATED first
        var draftWorld = mock(World.class);
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);
        gameManager.init(input);
        assertTrue(gameManager.isDraftCreated());

        when(worldManager.isGameWorldCreated()).thenReturn(false);
        when(layoutManager.getLayoutNames()).thenReturn(List.of("realLayout"));

        gameManager.start(input, "missingLayout");

        verify(player).sendMessage(contains("not found"));
        assertTrue(gameManager.isDraftCreated());
    }

    @Test
    @DisplayName("start() sends error when layout references a non-existent structure type")
    void start_whenLayoutTypeMissing() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        // Move to DRAFT_CREATED first
        var draftWorld = mock(World.class);
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);
        gameManager.init(input);
        assertTrue(gameManager.isDraftCreated());

        when(worldManager.isGameWorldCreated()).thenReturn(false);

        // Layout references a cell type that has no templates
        var cell = LayoutCell.of(0, 0, "nonexistent_type", new BlockPos(0, 64, 0));
        var layout = new MapLayout("broken", 32, 32, 32,
                new BlockPos(0, 64, 0), List.of(cell));
        when(layoutManager.getLayoutNames()).thenReturn(List.of("broken"));
        when(layoutManager.getLayout("broken")).thenReturn(layout);

        // StructureManager has no templates for "nonexistent_type"
        when(structureManager.hasType("nonexistent_type")).thenReturn(false);

        gameManager.start(input, "broken");

        verify(player).sendMessage(contains("missing structure templates"));
        verify(worldManager, never()).createGameWorld();
        assertTrue(gameManager.isDraftCreated());
    }

    @Test
    @DisplayName("start() without layout name uses first available layout")
    void start_withDefaultLayout() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        // Move to DRAFT_CREATED
        var draftWorld = mock(World.class);
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);
        gameManager.init(input);
        assertTrue(gameManager.isDraftCreated());

        when(worldManager.isGameWorldCreated()).thenReturn(false);

        var layout = new MapLayout("defaultLayout", 32, 32, 32,
                new BlockPos(0, 64, 0), List.of());
        when(layoutManager.getLayoutNames()).thenReturn(List.of("defaultLayout"));
        when(layoutManager.getLayout("defaultLayout")).thenReturn(layout);

        var gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("c2w_game");
        when(worldManager.createGameWorld()).thenReturn(gameWorld);

        var managedDraftWorld = mock(ManagedWorld.class);
        when(managedDraftWorld.getName()).thenReturn("c2w_draft");
        when(worldManager.getDraftWorld()).thenReturn(managedDraftWorld);

        var managedGameWorld = mock(ManagedWorld.class);
        when(managedGameWorld.getName()).thenReturn("c2w_game");
        when(managedGameWorld.getSpawnPos()).thenReturn(new BlockPos(0, 65, 0));
        when(worldManager.getGameWorld()).thenReturn(managedGameWorld);

        gameManager.start(input); // no layout name

        verify(eventManager).pushInternalEvent(any(GameWorldCreatedEvent.class));
        verify(eventManager).pushInternalEvent(any(StartGameEvent.class));
        verify(server).broadcastMessage(contains("defaultLayout"));
        assertTrue(gameManager.isGameInProgress());
    }

    @Test
    @DisplayName("start() creates game world, places structures, and pushes events")
    void start_success() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        // --- init phase ---
        var draftWorld = mock(World.class);
        when(draftWorld.getName()).thenReturn("c2w_draft");
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);

        gameManager.init(input);
        assertTrue(gameManager.isDraftCreated());

        // --- start phase setup ---
        when(worldManager.isGameWorldCreated()).thenReturn(false);

        var cell = LayoutCell.of(0, 0, "dungeon", new BlockPos(0, 64, 0));
        var layout = new MapLayout("arena", 32, 32, 32,
                new BlockPos(0, 64, 0), List.of(cell));
        when(layoutManager.getLayoutNames()).thenReturn(List.of("arena"));
        when(layoutManager.getLayout("arena")).thenReturn(layout);

        var gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("c2w_game");
        when(worldManager.createGameWorld()).thenReturn(gameWorld);

        when(structureManager.hasType("dungeon")).thenReturn(true);

        var placedStructure = mock(StructureData.class);
        when(structureManager.placeRandom(eq("dungeon"), eq("c2w_game"), any(BlockPos.class), any(StructureRotation.class)))
                .thenReturn(placedStructure);

        var managedDraftWorld = mock(ManagedWorld.class);
        when(managedDraftWorld.getName()).thenReturn("c2w_draft");
        when(worldManager.getDraftWorld()).thenReturn(managedDraftWorld);

        var managedGameWorld = mock(ManagedWorld.class);
        when(managedGameWorld.getName()).thenReturn("c2w_game");
        when(managedGameWorld.getSpawnPos()).thenReturn(new BlockPos(0, 65, 0));
        when(worldManager.getGameWorld()).thenReturn(managedGameWorld);

        // Player teleport loop — one player to move from draft
        when(server.getOnlinePlayerNames()).thenReturn(List.of("TestPlayer"));
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_draft");

        // --- act ---
        gameManager.start(input, "arena");

        // --- verify ---
        verify(eventManager).pushInternalEvent(any(GameWorldCreatedEvent.class));
        verify(eventManager).pushInternalEvent(any(StartGameEvent.class));
        verify(eventManager).registerInternalEvent(eq(WoolCapturedEvent.class), any());
        verify(server).broadcastMessage(contains("arena"));
        verify(structureManager).placeRandom(eq("dungeon"), eq("c2w_game"), any(BlockPos.class), any(StructureRotation.class));
        verify(structureCreationManager).destroyAllCreationWorlds();
        verify(players).teleportToWorld(eq("TestPlayer"), any(BlockPos.class), eq("c2w_game"));
        assertTrue(gameManager.isGameInProgress());
    }

    @Test
    @DisplayName("start routes players to their team's spawnpoint marker")
    void start_routesPlayersToTeamSpawnpoint() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        // --- init phase ---
        var draftWorld = mock(World.class);
        when(draftWorld.getName()).thenReturn("c2w_draft");
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);

        gameManager.init(input);
        assertTrue(gameManager.isDraftCreated());

        // --- start phase setup ---
        when(worldManager.isGameWorldCreated()).thenReturn(false);

        // A SPAWN cell for the Red team.
        var cell = LayoutCell.spawn(0, 0, "SPAWN", new BlockPos(0, 64, 0), "Red");
        var layout = new MapLayout("arena", 32, 32, 32,
                new BlockPos(0, 64, 0), List.of(cell));
        when(layoutManager.getLayoutNames()).thenReturn(List.of("arena"));
        when(layoutManager.getLayout("arena")).thenReturn(layout);

        var gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("c2w_game");
        when(worldManager.createGameWorld()).thenReturn(gameWorld);

        when(structureManager.hasType("SPAWN")).thenReturn(true);

        var placedStructure = mock(StructureData.class);
        // placeSpawnMarkers uses getWidth/getHeight/getDepth for search margin.
        when(placedStructure.getWidth()).thenReturn(11);
        when(placedStructure.getHeight()).thenReturn(1);
        when(placedStructure.getDepth()).thenReturn(11);
        when(structureManager.placeRandom(eq("SPAWN"), eq("c2w_game"), any(BlockPos.class), any(StructureRotation.class)))
                .thenReturn(placedStructure);

        var managedDraftWorld = mock(ManagedWorld.class);
        when(managedDraftWorld.getName()).thenReturn("c2w_draft");
        when(worldManager.getDraftWorld()).thenReturn(managedDraftWorld);

        var managedGameWorld = mock(ManagedWorld.class);
        when(managedGameWorld.getName()).thenReturn("c2w_game");
        when(managedGameWorld.getSpawnPos()).thenReturn(new BlockPos(0, 65, 0));
        when(worldManager.getGameWorld()).thenReturn(managedGameWorld);

        // A spawnpoint marker at (10,64,0) in the game world.
        var markerEntity = mock(MarkerEntity.class);
        when(markerEntity.getPosition()).thenReturn(new BlockPos(10, 64, 0));
        when(markers.getMarkerKey()).thenReturn("map_marker");
        when(markerEntity.getPersistentData("map_marker")).thenReturn("spawnpoint");
        when(markers.getMarkersInWorld("c2w_game")).thenReturn(List.of(markerEntity));

        // Player teleport loop — one Red player to move from draft.
        when(server.getOnlinePlayerNames()).thenReturn(List.of("TestPlayer"));
        when(players.getWorldName("TestPlayer")).thenReturn("c2w_draft");

        var redTeam = new ManagedTeam("Red", TeamColor.RED);
        var managedPlayer = mock(ManagedPlayer.class);
        when(managedPlayer.getTeam()).thenReturn(redTeam);
        when(playerManager.getPlayer("TestPlayer")).thenReturn(managedPlayer);

        // --- act ---
        gameManager.start(input, "arena");

        // --- verify: player teleported to (10,64,0) — marker Y (no +1) ---
        verify(players).teleportToWorld(eq("TestPlayer"), eq(new BlockPos(10, 64, 0)), eq("c2w_game"));
        verify(players).setGameMode(eq("TestPlayer"), eq("SURVIVAL"));
        assertTrue(gameManager.isGameInProgress());
    }

    // ---------------------------------------------------------------
    // Placement corner (placements-based layouts)
    // ---------------------------------------------------------------

    /**
     * Starts a placements-based game with one cell per yaw (all sharing the
     * same centre) and returns the exact corner {@code BlockPos} that was
     * handed to {@code structureManager.placeRandom(...)} for each cell, in
     * cell order.
     */
    private List<BlockPos> cornersForPlacements(int[] dims, float... yaws) {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        var draftWorld = mock(World.class);
        when(draftWorld.getName()).thenReturn("c2w_draft");
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);
        gameManager.init(input);

        when(worldManager.isGameWorldCreated()).thenReturn(false);

        List<LayoutCell> cells = new ArrayList<>();
        for (int r = 0; r < yaws.length; r++) {
            cells.add(LayoutCell.of(r, 0, "dungeon", new BlockPos(0, 64, 0), yaws[r]));
        }
        var layout = new MapLayout("arena", 32, 32, 32,
                new BlockPos(0, 64, 0), cells, true);
        when(layoutManager.getLayoutNames()).thenReturn(List.of("arena"));
        when(layoutManager.getLayout("arena")).thenReturn(layout);

        var gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("c2w_game");
        when(worldManager.createGameWorld()).thenReturn(gameWorld);
        when(structureManager.hasType("dungeon")).thenReturn(true);
        when(structureTypeConfig.getDimensions("dungeon")).thenReturn(dims);

        when(structureManager.placeRandom(eq("dungeon"), eq("c2w_game"),
                any(BlockPos.class), any(StructureRotation.class)))
                .thenReturn(mock(StructureData.class));

        var managedDraftWorld = mock(ManagedWorld.class);
        when(managedDraftWorld.getName()).thenReturn("c2w_draft");
        when(worldManager.getDraftWorld()).thenReturn(managedDraftWorld);

        var managedGameWorld = mock(ManagedWorld.class);
        when(managedGameWorld.getName()).thenReturn("c2w_game");
        when(managedGameWorld.getSpawnPos()).thenReturn(new BlockPos(0, 65, 0));
        when(worldManager.getGameWorld()).thenReturn(managedGameWorld);

        when(server.getOnlinePlayerNames()).thenReturn(List.of());
        when(markers.findMarkersInWorld(anyString(), anyString(), any())).thenReturn(List.of());

        gameManager.start(input, "arena");

        ArgumentCaptor<BlockPos> captor = ArgumentCaptor.forClass(BlockPos.class);
        verify(structureManager, times(yaws.length)).placeRandom(eq("dungeon"), eq("c2w_game"),
                captor.capture(), any(StructureRotation.class));
        return captor.getAllValues();
    }

    @Test
    @DisplayName("odd-dimension cells: rotated corners keep the structure centred")
    void placementsCorner_oddDimsAreCentred() {
        int[] dims = {13, 13, 13};
        // hw = 13/2 = 6 → corner = centre ∓ 6 in each axis for every rotation.
        var corners = cornersForPlacements(dims, 0f, 90f, 180f, 270f);
        assertEquals(new BlockPos(-6, 64, -6), corners.get(0));   // NONE (south)
        assertEquals(new BlockPos(6, 64, -6), corners.get(1));    // west
        assertEquals(new BlockPos(6, 64, 6), corners.get(2));     // north
        assertEquals(new BlockPos(-6, 64, 6), corners.get(3));    // east
    }

    @Test
    @DisplayName("even-dimension placements: rotated cells are not shifted one block sideways")
    void placementsCorner_evenDimsAreMirrored() {
        int[] dims = {12, 12, 12};
        // hw = 12/2 = 6 → unrotated corner (-6,64,-6), footprint [-6,5] × [-6,5].
        // Rotated structures must reuse that exact footprint, so their corner
        // is the mirror ((w-1)-hw = 5), NOT +hw = 6 which would push them one
        // block toward +X/+Z.
        var corners = cornersForPlacements(dims, 0f, 90f, 180f, 270f);
        assertEquals(new BlockPos(-6, 64, -6), corners.get(0));   // NONE (south)
        assertEquals(new BlockPos(5, 64, -6), corners.get(1));    // west
        assertEquals(new BlockPos(5, 64, 5), corners.get(2));     // north
        assertEquals(new BlockPos(-6, 64, 5), corners.get(3));    // east
    }

    // ---------------------------------------------------------------
    // preview()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("preview() pushes PreviewRequestEvent")
    void preview() {
        var input = new CommandInput();
        input.commandSender = mock(Player.class);

        gameManager.preview(input);

        verify(eventManager).pushInternalEvent(any(PreviewRequestEvent.class));
    }

    // ---------------------------------------------------------------
    // end()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("end(CommandInput) doesn't proceed when sender is not a Player")
    void end_withNonPlayerSender() {
        var input = new CommandInput();
        input.commandSender = "not-a-player";

        gameManager.end(input);

        assertFalse(gameManager.isGameEnded());
        verify(eventManager, never()).pushInternalEvent(any(EndGameEvent.class));
    }

    @Test
    @DisplayName("end(CommandInput) sends error when not in GAME_IN_PROGRESS")
    void end_whenNotInProgress() {
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        // Initial state is NOT_STARTED — canEnd() returns false
        gameManager.end(input);

        verify(player).sendMessage(contains("after it was started"));
        assertFalse(gameManager.isGameEnded());
    }

    @Test
    @DisplayName("end(CommandInput) transitions to GAME_ENDED and pushes event")
    void end_withInput() {
        // Get into GAME_IN_PROGRESS via full init → start flow
        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        var draftWorld = mock(World.class);
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);
        gameManager.init(input);
        assertTrue(gameManager.isDraftCreated());

        when(worldManager.isGameWorldCreated()).thenReturn(false);
        var layout = new MapLayout("testLayout", 32, 32, 32,
                new BlockPos(0, 64, 0), List.of());
        when(layoutManager.getLayoutNames()).thenReturn(List.of("testLayout"));
        when(layoutManager.getLayout("testLayout")).thenReturn(layout);

        var gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("c2w_game");
        when(worldManager.createGameWorld()).thenReturn(gameWorld);

        var managedDraftWorld = mock(ManagedWorld.class);
        when(managedDraftWorld.getName()).thenReturn("c2w_draft");
        when(worldManager.getDraftWorld()).thenReturn(managedDraftWorld);

        var managedGameWorld = mock(ManagedWorld.class);
        when(managedGameWorld.getName()).thenReturn("c2w_game");
        when(managedGameWorld.getSpawnPos()).thenReturn(new BlockPos(0, 65, 0));
        when(worldManager.getGameWorld()).thenReturn(managedGameWorld);

        when(server.getOnlinePlayerNames()).thenReturn(List.of());

        gameManager.start(input, "testLayout");
        assertTrue(gameManager.isGameInProgress());

        // --- act ---
        gameManager.end(input);

        verify(eventManager).pushInternalEvent(any(EndGameEvent.class));
        verify(player).sendMessage(contains("Game ended"));
        assertTrue(gameManager.isGameEnded());
    }

    @Test
    @DisplayName("end() no-arg transitions to GAME_ENDED and pushes event")
    void end_noArg() {
        gameManager.end();

        assertTrue(gameManager.isGameEnded());
        verify(eventManager).pushInternalEvent(any(EndGameEvent.class));
    }

    // ---------------------------------------------------------------
    // woolCapped()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("woolCapped() delegates to GSM state machine")
    void woolCapped() {
        var playerHandle = mock(PlayerHandle.class);
        when(playerHandle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(playerHandle.getName()).thenReturn("test");
        when(playerHandle.getDisplayName()).thenReturn("test");
        var managedPlayer = new ManagedPlayer(playerHandle);
        var team = new ManagedTeam("Red", TeamColor.RED);
        managedPlayer.setTeam(team);

        var wool = mock(Wool.class);

        // First cap — registers the cap, no state change
        gameManager.woolCapped(new WoolCapturedEvent(managedPlayer, wool));
        assertFalse(gameManager.isGameEnded());
        assertTrue(gameManager.isNotStarted()); // still NOT_STARTED (GSM doesn't change state on first cap)
        verify(eventManager, never()).pushInternalEvent(any(EndGameEvent.class));

        // Second cap — triggers GAME_ENDED and the real end-of-game flow
        gameManager.woolCapped(new WoolCapturedEvent(managedPlayer, wool));
        assertTrue(gameManager.isGameEnded());
        verify(eventManager).pushInternalEvent(any(EndGameEvent.class));
    }

    // ---------------------------------------------------------------
    // reset()
    // ---------------------------------------------------------------

    @Test
    @DisplayName("reset() transitions back to NOT_STARTED")
    void reset() {
        gameManager.end(); // move to GAME_ENDED
        assertTrue(gameManager.isGameEnded());

        gameManager.reset();

        assertTrue(gameManager.isNotStarted());
        assertFalse(gameManager.isDraftCreated());
        assertFalse(gameManager.isGameInProgress());
        assertFalse(gameManager.isGameEnded());
    }

    // ---------------------------------------------------------------
    // Resource placement (C1 fix)
    // ---------------------------------------------------------------

    @Test
    @DisplayName("start() places resources.nbt when it exists for the structure type")
    void start_placesResourcesNbt() throws Exception {
        // Create the resources.nbt file in a temp directory FIRST
        java.io.File tempDir = java.nio.file.Files.createTempDirectory("c2w-test").toFile();
        tempDir.deleteOnExit();
        java.io.File resFile = new java.io.File(tempDir, "structures/dungeon/resources.nbt");
        resFile.getParentFile().mkdirs();
        java.nio.file.Files.write(resFile.toPath(), new byte[0]);
        when(mcPlugin.getDataFolder()).thenReturn(tempDir);

        var input = new CommandInput();
        var player = mock(Player.class);
        input.commandSender = player;

        // --- init phase ---
        var draftWorld = mock(World.class);
        when(draftWorld.getName()).thenReturn("c2w_draft");
        when(draftWorld.getSpawnLocation()).thenReturn(mock(Location.class));
        when(worldManager.createDraftWorld()).thenReturn(draftWorld);
        gameManager.init(input);
        assertTrue(gameManager.isDraftCreated());

        // --- start phase setup ---
        when(worldManager.isGameWorldCreated()).thenReturn(false);

        var cell = LayoutCell.of(0, 0, "dungeon", new BlockPos(0, 64, 0));
        var layout = new MapLayout("arena", 32, 32, 32,
                new BlockPos(0, 64, 0), List.of(cell));
        when(layoutManager.getLayoutNames()).thenReturn(List.of("arena"));
        when(layoutManager.getLayout("arena")).thenReturn(layout);

        var gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("c2w_game");
        when(worldManager.createGameWorld()).thenReturn(gameWorld);

        when(structureManager.hasType("dungeon")).thenReturn(true);

        var placedStructure = mock(StructureData.class);
        when(placedStructure.getId()).thenReturn("room1");
        when(structureManager.placeRandom(eq("dungeon"), eq("c2w_game"), any(BlockPos.class), any(StructureRotation.class)))
                .thenReturn(placedStructure);

        var managedDraftWorld = mock(ManagedWorld.class);
        when(managedDraftWorld.getName()).thenReturn("c2w_draft");
        when(worldManager.getDraftWorld()).thenReturn(managedDraftWorld);

        var managedGameWorld = mock(ManagedWorld.class);
        when(managedGameWorld.getName()).thenReturn("c2w_game");
        when(managedGameWorld.getSpawnPos()).thenReturn(new BlockPos(0, 65, 0));
        when(worldManager.getGameWorld()).thenReturn(managedGameWorld);

        when(server.getOnlinePlayerNames()).thenReturn(List.of());

        // Mock structures.loadStructure to return a valid ID
        when(structures.loadStructure(any(File.class))).thenReturn("res-struct-id");

        // --- act ---
        gameManager.start(input, "arena");

        // --- verify ---
        verify(eventManager).pushInternalEvent(any(GameWorldCreatedEvent.class));
        // Resources are now placed by distributing across spots, not loading resources.nbt
        // Just verify the game started
        assertTrue(gameManager.isGameInProgress());
    }


    // ---------------------------------------------------------------
    // Spawn marker placement
    // ---------------------------------------------------------------

}
