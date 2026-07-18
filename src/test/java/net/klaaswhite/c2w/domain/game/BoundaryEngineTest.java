package net.klaaswhite.c2w.domain.game;

import net.klaaswhite.c2w.adapter.minecraft.BossBar;
import net.klaaswhite.c2w.adapter.minecraft.BossBars;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.domain.game.BoundaryEngine;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.DomainBoundingBox;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.TeamColor;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BoundaryEngine")
class BoundaryEngineTest {

    private BoundaryEngine engine;
    private FakeWoolTimer timer;

    /** Fake WoolTimer that records register/unregister calls. */
    static class FakeWoolTimer implements BoundaryEngine.WoolTimerBridge {
        final List<net.klaaswhite.c2w.domain.model.Wool> registered = new ArrayList<>();
        final List<net.klaaswhite.c2w.domain.model.Wool> unregistered = new ArrayList<>();

        @Override public void registerWool(net.klaaswhite.c2w.domain.model.Wool wool) { registered.add(wool); }
        @Override public void unregisterWool(net.klaaswhite.c2w.domain.model.Wool wool) { unregistered.add(wool); }
        @Override public int getBaseCapture() { return 10; }
        @Override public int getIncreasePerPlayer() { return 10; }
        @Override public int getDecreasePerPlayer() { return 10; }
    }

    private MinecraftManager createMockMc() {
        var mc = mock(MinecraftManager.class);
        var bossBars = mock(BossBars.class);
        var bossBar = mock(BossBar.class);
        var server = mock(Server.class);
        var players = mock(Players.class);
        when(mc.bossBars()).thenReturn(bossBars);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);
        when(mc.server()).thenReturn(server);
        when(mc.players()).thenReturn(players);
        return mc;
    }

    private ManagedPlayer createPlayer(String name) {
        var handle = mock(PlayerHandle.class);
        when(handle.getName()).thenReturn(name);
        when(handle.getDisplayName()).thenReturn(name);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getPosition()).thenReturn(new BlockPos(0, 64, 0));
        return new ManagedPlayer(handle);
    }

    private ManagedPlayer createPlayer(String name, ManagedTeam team) {
        var player = createPlayer(name);
        player.setTeam(team);
        return player;
    }

    private Wool createWool(WoolColor color) {
        var mc = createMockMc();
        var woolTimer = new WoolTimer(new WoolTimer.Scheduler() {
            public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
            public void cancel(Object taskId) {}
        });
        return new Wool(mc, woolTimer, color, new BlockPos(0, 64, 0), "game", "cap-" + color.name().toLowerCase());
    }

    @BeforeEach
    void setUp() {
        timer = new FakeWoolTimer();
        engine = new BoundaryEngine(timer);
    }

    // --- Initial state ---

    @Test
    @DisplayName("starts with no bounding boxes")
    void initialState() {
        assertFalse(engine.hasBoundingBoxes());
    }

    // --- Bounding box setup ---

    @Test
    @DisplayName("adding a pit bounding box makes hasBoundingBoxes return true")
    void addPitBox() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        assertTrue(engine.hasBoundingBoxes());
    }

    @Test
    @DisplayName("adding an elevator bounding box makes hasBoundingBoxes return true")
    void addElevatorBox() {
        engine.addElevatorBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        assertTrue(engine.hasBoundingBoxes());
    }

    // --- Player movement ---

    @Test
    @DisplayName("player entering pit triggers pit enter")
    void playerEntersPit() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");
        player.setTeam(new ManagedTeam("Red", TeamColor.RED));

        // Move from outside to inside
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);

        assertTrue(engine.isPlayerInPit(player));
    }

    @Test
    @DisplayName("player exiting pit triggers pit exit")
    void playerExitsPit() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");
        player.setTeam(new ManagedTeam("Red", TeamColor.RED));

        // Enter
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);
        assertTrue(engine.isPlayerInPit(player));

        // Exit
        engine.onPlayerMove(5, 5, 5, -1, 5, 5, player);
        assertFalse(engine.isPlayerInPit(player));
    }

    @Test
    @DisplayName("player staying inside pit does not trigger enter/exit")
    void playerStaysInPit() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");
        player.setTeam(new ManagedTeam("Red", TeamColor.RED));

        // Enter
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);
        assertEquals(1, engine.getPitEnterCount());

        // Move within pit
        engine.onPlayerMove(5, 5, 5, 6, 5, 6, player);
        assertEquals(1, engine.getPitEnterCount());
    }

    @Test
    @DisplayName("player entering elevator triggers instant capture")
    void elevatorEnterCaptures() {
        engine.addElevatorBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");
        var wool = createWool(WoolColor.RED);
        wool.pickup(player);

        // Move into elevator
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);

        assertTrue(wool.isCapped());
    }

    @Test
    @DisplayName("elevator does nothing when player has no carry")
    void elevatorNoCarry() {
        engine.addElevatorBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");

        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);

        assertNull(player.getCarry());
    }

    @Test
    @DisplayName("no bounding boxes means no-op on move")
    void noBoxesNoOp() {
        var player = createPlayer("Alice");

        assertDoesNotThrow(() -> engine.onPlayerMove(0, 0, 0, 5, 5, 5, player));
    }

    // --- Pit wool tracking ---

    @Test
    @DisplayName("entering pit with wool registers it with timer")
    void pitEnterWithWoolRegisters() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");
        player.setTeam(new ManagedTeam("Red", TeamColor.RED));
        var wool = createWool(WoolColor.RED);
        wool.pickup(player);

        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);

        assertTrue(timer.registered.contains(wool));
        assertTrue(wool.isCapping());
    }

    @Test
    @DisplayName("exiting pit with wool unregisters it from timer")
    void pitExitWithWoolUnregisters() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");
        player.setTeam(new ManagedTeam("Red", TeamColor.RED));
        var wool = createWool(WoolColor.RED);
        wool.pickup(player);

        // Enter
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);
        assertTrue(timer.registered.contains(wool));

        // Exit
        engine.onPlayerMove(5, 5, 5, -1, 5, 5, player);
        assertTrue(timer.unregistered.contains(wool));
        assertFalse(wool.isCapping());
    }

    @Test
    @DisplayName("entering pit without wool does not register anything")
    void pitEnterNoWool() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");

        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);

        assertTrue(timer.registered.isEmpty());
    }

    // --- Capping modifier ---

    @Test
    @DisplayName("capping modifier increases with more teammates in pit")
    void modifierIncreasesWithTeammates() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var team = new ManagedTeam("Red", TeamColor.RED);
        var player1 = createPlayer("Alice");
        var player2 = createPlayer("Bob");
        player1.setTeam(team);
        player2.setTeam(team);
        var wool = createWool(WoolColor.RED);
        wool.pickup(player1);

        // Player1 enters pit with wool
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player1);
        int modifier1 = wool.getCappingModifier();

        // Player2 enters pit (teammate)
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player2);
        int modifier2 = wool.getCappingModifier();

        assertTrue(modifier2 > modifier1, "modifier should increase with more teammates");
    }

    @Test
    @DisplayName("capping modifier decreases with enemies in pit")
    void modifierDecreasesWithEnemies() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var redTeam = new ManagedTeam("Red", TeamColor.RED);
        var blueTeam = new ManagedTeam("Blue", TeamColor.BLUE);
        var redPlayer = createPlayer("Alice");
        var bluePlayer = createPlayer("Bob");
        redPlayer.setTeam(redTeam);
        bluePlayer.setTeam(blueTeam);
        var wool = createWool(WoolColor.RED);
        wool.pickup(redPlayer);

        // Red enters pit with wool
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, redPlayer);
        int modifierWithAlone = wool.getCappingModifier();

        // Blue enters pit (enemy)
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, bluePlayer);
        int modifierWithEnemy = wool.getCappingModifier();

        assertTrue(modifierWithEnemy < modifierWithAlone, "modifier should decrease with enemies");
    }

    @Test
    @DisplayName("modifier is zero when enemies outnumber teammates")
    void modifierZeroWhenOutnumbered() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var redTeam = new ManagedTeam("Red", TeamColor.RED);
        var blueTeam = new ManagedTeam("Blue", TeamColor.BLUE);
        var redPlayer = createPlayer("Alice");
        var bluePlayer1 = createPlayer("Bob");
        var bluePlayer2 = createPlayer("Charlie");
        redPlayer.setTeam(redTeam);
        bluePlayer1.setTeam(blueTeam);
        bluePlayer2.setTeam(blueTeam);
        var wool = createWool(WoolColor.RED);
        wool.pickup(redPlayer);

        // Red enters pit with wool
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, redPlayer);

        // Two enemies enter pit
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, bluePlayer1);
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, bluePlayer2);

        assertEquals(0, wool.getCappingModifier(), "modifier should be 0 when outnumbered");
    }

    // --- Wool dropped/captured ---

    @Test
    @DisplayName("onWoolDropped removes wool from pit tracking")
    void onWoolDropped() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");
        player.setTeam(new ManagedTeam("Red", TeamColor.RED));
        var wool = createWool(WoolColor.RED);
        wool.pickup(player);

        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);
        assertTrue(timer.registered.contains(wool));

        engine.onWoolDropped(wool);
        assertTrue(timer.unregistered.contains(wool));
    }

    @Test
    @DisplayName("onWoolCaptured removes wool from pit tracking")
    void onWoolCaptured() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");
        player.setTeam(new ManagedTeam("Red", TeamColor.RED));
        var wool = createWool(WoolColor.RED);
        wool.pickup(player);

        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);
        assertTrue(timer.registered.contains(wool));

        engine.onWoolCaptured(wool);
        assertTrue(timer.unregistered.contains(wool));
    }

    // --- Reset ---

    @Test
    @DisplayName("reset clears all state")
    void reset() {
        engine.addPitBox(new DomainBoundingBox(0, 0, 0, 10, 10, 10));
        var player = createPlayer("Alice");
        player.setTeam(new ManagedTeam("Red", TeamColor.RED));
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);

        assertTrue(engine.hasBoundingBoxes());
        assertTrue(engine.isPlayerInPit(player));

        engine.reset();

        assertFalse(engine.hasBoundingBoxes());
        assertFalse(engine.isPlayerInPit(player));
    }

    // --- Multiple bounding boxes ---

    @Test
    @DisplayName("player can be in both pit and elevator simultaneously")
    void overlappingBoxes() {
        var box = new DomainBoundingBox(0, 0, 0, 10, 10, 10);
        engine.addPitBox(box);
        engine.addElevatorBox(box);

        var player = createPlayer("Alice");
        player.setTeam(new ManagedTeam("Red", TeamColor.RED));
        var wool = createWool(WoolColor.RED);
        wool.pickup(player);

        // Enter the overlapping area
        engine.onPlayerMove(-1, 5, 5, 5, 5, 5, player);

        assertTrue(engine.isPlayerInPit(player));
        assertTrue(wool.isCapped(), "elevator should capture instantly");
    }
}