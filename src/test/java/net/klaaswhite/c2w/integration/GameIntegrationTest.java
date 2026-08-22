package net.klaaswhite.c2w.integration;

import net.klaaswhite.c2w.adapter.managers.*;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.bootstrap.Managers;
import net.klaaswhite.c2w.bootstrap.config.FolderStructureTypeConfig;
import net.klaaswhite.c2w.bootstrap.config.PluginConfig;
import net.klaaswhite.c2w.domain.commands.CommandInput;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.managers.NbtStructureSource;
import net.klaaswhite.c2w.domain.managers.StructureManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.WoolColor;
import net.klaaswhite.c2w.domain.ops.FileSystemOps;
import net.klaaswhite.c2w.domain.ops.FakeFileSystemOps;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full end-to-end integration test. Wires the REAL plugin managers together
 * headlessly (no live Bukkit server) using {@link FakeMinecraftManager} and
 * drives the complete game flow:
 * <pre>
 *   init (draft) → start (game world + markers + wools) →
 *   pickup wool → enter elevator box → capture → repeat → win (2 caps/team)
 * </pre>
 * This exercises the real {@link GameManager}, {@link MarkerManager},
 * {@link BoundaryManager}, {@link PlayerManager}, {@link WorldManager},
 * {@link StructureManager} and {@link net.klaaswhite.c2w.domain.game.GameStateMachine}
 * behaviour, not mocked collaborators.
 */
public class GameIntegrationTest {

    private File tempDir;
    private FakeMinecraftManager fakeMc;
    private FakeScheduler fakeScheduler;
    private FakeFileSystemOps fakeFs;
    private TempDirConfigAccess tempDirConfigAccess;
    private Managers managers;

    private Player mockAlice;
    private Player mockBob;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("c2w-it").toFile();
        fakeMc = new FakeMinecraftManager(tempDir);
        fakeScheduler = new FakeScheduler();
        fakeFs = new FakeFileSystemOps();
        tempDirConfigAccess = new TempDirConfigAccess(tempDir);

        // --- Real structure type config (reads a real structure.yml from disk) ---
        File dungeonDir = new File(tempDir, "structures/dungeon");
        assertTrue(dungeonDir.mkdirs());
        Files.writeString(new File(dungeonDir, "structure.yml").toPath(),
                "width: 5\nheight: 5\ndepth: 5\n");

        // --- In-memory NBT instance so StructureManager.discoverTemplates finds a template ---
        File nbtFile = new File(tempDir, "structures/dungeon/instances/dungeon1.nbt");
        fakeFs.createFile(nbtFile.getAbsolutePath(), new byte[]{1, 2, 3, 4});

        // --- In-memory layout file (placements-based, two dungeon cells + a Red spawn) ---
        File layoutsDir = new File(tempDir, "layouts");
        fakeFs.createDir(layoutsDir.getAbsolutePath());
        String layoutYaml = String.join("\n",
                "tileWidth: 32",
                "tileHeight: 32",
                "tileDepth: 32",
                "origin:",
                "  x: 0",
                "  y: 64",
                "  z: 0",
                "placements:",
                "  - type: dungeon",
                "    x: 0",
                "    y: 64",
                "    z: 0",
                "  - type: dungeon",
                "    x: 64",
                "    y: 64",
                "    z: 64",
                "");
        fakeFs.createFile(new File(layoutsDir, "test.yml").getAbsolutePath(), layoutYaml);

        // --- Wire the real managers (headless) ---
        JavaPlugin mockPlugin = org.mockito.Mockito.mock(JavaPlugin.class);
        managers = new Managers(mockPlugin);

        managers.mc = fakeMc;
        managers.woolTimer = new WoolTimer(fakeScheduler);
        managers.eventManager = new EventManager(managers); // headless constructor
        managers.worldManager = new WorldManager(mockPlugin, fakeMc);
        managers.structureTypeConfig = new FolderStructureTypeConfig(tempDirConfigAccess);
        managers.structureManager = new StructureManager(
                new NbtStructureSource(tempDir, fakeMc, managers.structureTypeConfig, fakeFs));
        managers.layoutManager = new LayoutManager(layoutsDir, new net.klaaswhite.c2w.domain.config.YamlLayoutFileLoader(fakeFs), fakeFs);
        managers.playerManager = new PlayerManager(managers, fakeMc);
        managers.markerManager = new MarkerManager(managers, managers.eventManager, fakeMc);
        managers.boundaryManager = new BoundaryManager(
                managers.eventManager, managers.markerManager, managers.playerManager, managers.woolTimer);
        managers.pluginConfig = new PluginConfig(tempDirConfigAccess);
        managers.gameManager = new GameManager(
                mockPlugin, managers.eventManager, managers.worldManager, managers.layoutManager,
                managers.structureManager, fakeMc, null, managers.pluginConfig,
                managers.playerManager, managers.structureTypeConfig);

        fakeMc.setEventManager(managers.eventManager);

        // --- Seed markers in the game world (discovered at game start) ---
        // NOTE: the game world itself is created by GameManager.start; the markers
        // only need to be present in the fake's marker map keyed by world name.
        // Two generic wool markers → assigned RED, GREEN by MarkerEngine
        fakeMc.addMarker("c2w_game", new BlockPos(10, 64, 10), "map_marker", "wool");
        fakeMc.addMarker("c2w_game", new BlockPos(20, 64, 20), "map_marker", "wool");
        // Elevator capture box (100,60,100) - (110,70,110)
        fakeMc.addMarker("c2w_game", new BlockPos(100, 60, 100), "map_marker", "boundary-woolcap-elevator-1");
        fakeMc.addMarker("c2w_game", new BlockPos(110, 70, 110), "map_marker", "boundary-woolcap-elevator-2");
        // Pit capture box (200,60,200) - (210,70,210) — used by the pit-capture test
        fakeMc.addMarker("c2w_game", new BlockPos(200, 60, 200), "map_marker", "boundary-woolcap-pit-1");
        fakeMc.addMarker("c2w_game", new BlockPos(210, 70, 210), "map_marker", "boundary-woolcap-pit-2");

        // --- A fake online player "Alice" ---
        var alice = fakeMc.createFakePlayer("Alice");
        mockAlice = alice.bukkitPlayer;
        // --- A fake online player "Bob" (Blue team) ---
        var bob = fakeMc.createFakePlayer("Bob");
        mockBob = bob.bukkitPlayer;
    }

    @AfterEach
    void tearDown() {
        if (managers != null) {
            managers.gameManager.close();
            managers.boundaryManager.close();
            managers.markerManager.close();
            managers.playerManager.close();
            managers.worldManager.close();
            managers.eventManager.close();
        }
        deleteRecursively(tempDir);
    }

    private static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (var f : files) deleteRecursively(f);
        }
        dir.delete();
    }

    private CommandInput inputFor(Player player) {
        var input = new CommandInput();
        input.commandSender = player;
        return input;
    }

    private PlayerMoveEvent move(Player player, double fx, double fy, double fz, double tx, double ty, double tz) {
        Location from = new Location(null, fx, fy, fz);
        Location to = new Location(null, tx, ty, tz);
        return new PlayerMoveEvent(player, from, to);
    }

    // ========================================================================
    // Tests
    // ========================================================================

    @Test
    void fullGameFlow_elevatorCapture_winsWithTwoCaps() {
        var gameManager = managers.gameManager;
        var playerManager = managers.playerManager;
        var markerManager = managers.markerManager;
        var eventManager = managers.eventManager;

        // 1) Draft phase
        gameManager.init(inputFor(mockAlice));
        assertTrue(gameManager.isDraftCreated(), "Game should be in DRAFT_CREATED after init");
        assertNotNull(playerManager.getPlayer("Alice"), "Alice should be registered after draft");

        // 2) Start the game — world created, markers discovered, wools spawned
        gameManager.start(inputFor(mockAlice), "test");
        assertTrue(gameManager.isGameInProgress(), "Game should be GAME_IN_PROGRESS after start");
        List<Wool> wools = markerManager.getWools();
        assertEquals(2, wools.size(), "Two wools should be created from the two wool markers");

        // 3) Put Alice on the Red team
        playerManager.addPlayersToTeam("Red", List.of("Alice"));
        ManagedPlayer alice = playerManager.getPlayer("Alice");
        assertNotNull(alice.getTeam(), "Alice should have a team after addPlayersToTeam");
        assertEquals("Red", alice.getTeam().teamName, "Alice should be on the Red team");

        // 4) Pick up the RED wool
        Wool redWool = wools.stream().filter(w -> w.getColor() == WoolColor.RED).findFirst().orElseThrow();
        assertTrue(redWool.pickup(alice), "Alice should be able to pick up the red wool");
        assertTrue(redWool.isCarried(), "Red wool should be carried after pickup");

        // 5) Enter the elevator box → instant capture
        eventManager.pushMinecraftEvent(move(mockAlice, 0, 65, 0, 105, 65, 105));
        assertTrue(redWool.isCapped(), "Red wool should be captured after entering the elevator box");
        assertEquals(1, gameManager.getWoolCount("Red"), "Red should have 1 captured wool");

        // 6) Pick up the GREEN wool
        Wool greenWool = wools.stream().filter(w -> w.getColor() == WoolColor.GREEN).findFirst().orElseThrow();
        assertTrue(greenWool.pickup(alice), "Alice should be able to pick up the green wool");
        assertTrue(greenWool.isCarried(), "Green wool should be carried after pickup");

        // 7) Leave and re-enter the elevator box → second capture → win
        eventManager.pushMinecraftEvent(move(mockAlice, 105, 65, 105, 0, 65, 0)); // exit (no-op for elevator)
        eventManager.pushMinecraftEvent(move(mockAlice, 0, 65, 0, 105, 65, 105)); // re-enter → capture
        assertTrue(greenWool.isCapped(), "Green wool should be captured after re-entering the elevator box");
        assertEquals(2, gameManager.getWoolCount("Red"), "Red should have 2 captured wools");
        assertTrue(gameManager.isGameEnded(), "Game should end after a team captures 2 wools");
    }

    @Test
    void pitCapture_usesWoolTimer_toCapture() {
        var gameManager = managers.gameManager;
        var playerManager = managers.playerManager;
        var markerManager = managers.markerManager;
        var eventManager = managers.eventManager;

        // Start a game
        gameManager.init(inputFor(mockAlice));
        gameManager.start(inputFor(mockAlice), "test");
        playerManager.addPlayersToTeam("Red", List.of("Alice"));
        ManagedPlayer alice = playerManager.getPlayer("Alice");

        // Pick up the RED wool and carry it into the pit box
        Wool redWool = markerManager.getWools().stream()
                .filter(w -> w.getColor() == WoolColor.RED).findFirst().orElseThrow();
        assertTrue(redWool.pickup(alice));

        // Make capture happen in a single timer tick for determinism
        managers.woolTimer.setBaseCapture(Wool.CAP_AMOUNT);
        managers.woolTimer.setIncreasePerPlayer(0);
        managers.woolTimer.setDecreasePerPlayer(0);

        // Enter the pit box → registers wool with the timer, sets capping
        eventManager.pushMinecraftEvent(move(mockAlice, 0, 65, 0, 205, 65, 205));
        assertTrue(redWool.isCapping(), "Wool should be capping after entering the pit box");

        // Drive the timer once → capture
        fakeScheduler.tickAll();
        assertTrue(redWool.isCapped(), "Red wool should be captured via the pit timer");
        assertEquals(1, gameManager.getWoolCount("Red"), "Red should have 1 captured wool via pit");
    }

    @Test
    void twoTeamCompetition_blueWins() {
        var gameManager = managers.gameManager;
        var playerManager = managers.playerManager;
        var markerManager = managers.markerManager;
        var eventManager = managers.eventManager;

        gameManager.init(inputFor(mockAlice));
        gameManager.start(inputFor(mockAlice), "test");

        // Alice on Red, Bob on Blue
        playerManager.addPlayersToTeam("Red", List.of("Alice"));
        playerManager.addPlayersToTeam("Blue", List.of("Bob"));
        ManagedPlayer alice = playerManager.getPlayer("Alice");
        ManagedPlayer bob = playerManager.getPlayer("Bob");
        assertNotNull(alice.getTeam());
        assertNotNull(bob.getTeam());
        assertEquals("Red", alice.getTeam().teamName);
        assertEquals("Blue", bob.getTeam().teamName);

        // Bob captures both wools → Blue wins
        Wool redWool = markerManager.getWools().stream()
                .filter(w -> w.getColor() == WoolColor.RED).findFirst().orElseThrow();
        Wool greenWool = markerManager.getWools().stream()
                .filter(w -> w.getColor() == WoolColor.GREEN).findFirst().orElseThrow();

        assertTrue(redWool.pickup(bob));
        eventManager.pushMinecraftEvent(move(mockBob, 0, 65, 0, 105, 65, 105));
        assertTrue(redWool.isCapped(), "Red wool should be captured by Blue");
        assertEquals(1, gameManager.getWoolCount("Blue"));

        assertTrue(greenWool.pickup(bob));
        eventManager.pushMinecraftEvent(move(mockBob, 105, 65, 105, 0, 65, 0));
        eventManager.pushMinecraftEvent(move(mockBob, 0, 65, 0, 105, 65, 105));
        assertTrue(greenWool.isCapped(), "Green wool should be captured by Blue");
        assertEquals(2, gameManager.getWoolCount("Blue"));
        assertTrue(gameManager.isGameEnded(), "Game should end when Blue captures 2 wools");
        assertEquals(0, gameManager.getWoolCount("Red"), "Red should have no captures");
    }

    @Test
    void woolDroppedOnDeath_midGame() {
        var gameManager = managers.gameManager;
        var playerManager = managers.playerManager;
        var markerManager = managers.markerManager;

        gameManager.init(inputFor(mockAlice));
        gameManager.start(inputFor(mockAlice), "test");
        playerManager.addPlayersToTeam("Red", List.of("Alice"));
        ManagedPlayer alice = playerManager.getPlayer("Alice");

        Wool redWool = markerManager.getWools().stream()
                .filter(w -> w.getColor() == WoolColor.RED).findFirst().orElseThrow();
        assertTrue(redWool.pickup(alice));
        assertTrue(redWool.isCarried(), "Wool should be carried after pickup");

        // Alice dies while carrying → wool drops, no longer carried
        redWool.dropOnDeath(alice);
        assertFalse(redWool.isCarried(), "Wool should no longer be carried after death");
        assertFalse(redWool.isCapped(), "Dropped wool should not be capped");
        assertEquals(0, gameManager.getWoolCount("Red"), "No capture should be counted on drop");
    }

    @Test
    void resetMidGame_tearsDownState() {
        var gameManager = managers.gameManager;
        var playerManager = managers.playerManager;
        var markerManager = managers.markerManager;
        var eventManager = managers.eventManager;

        gameManager.init(inputFor(mockAlice));
        gameManager.start(inputFor(mockAlice), "test");
        playerManager.addPlayersToTeam("Red", List.of("Alice"));
        ManagedPlayer alice = playerManager.getPlayer("Alice");

        Wool redWool = markerManager.getWools().stream()
                .filter(w -> w.getColor() == WoolColor.RED).findFirst().orElseThrow();
        assertTrue(redWool.pickup(alice));
        eventManager.pushMinecraftEvent(move(mockAlice, 0, 65, 0, 105, 65, 105));
        assertEquals(1, gameManager.getWoolCount("Red"));

        // Reset mid-game
        gameManager.reset();

        assertFalse(gameManager.isGameInProgress(), "Game should not be in progress after reset");
        assertFalse(gameManager.isGameEnded(), "Game should not be ended after reset");
        assertEquals(0, gameManager.getWoolCount("Red"), "Captured wools should be cleared after reset");
        assertTrue(markerManager.getWools().isEmpty(), "Wools should be cleared after reset");
    }
}
