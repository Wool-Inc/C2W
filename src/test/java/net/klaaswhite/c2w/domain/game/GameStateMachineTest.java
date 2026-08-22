package net.klaaswhite.c2w.domain.game;

import net.klaaswhite.c2w.adapter.minecraft.BossBar;
import net.klaaswhite.c2w.adapter.minecraft.BossBars;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.TeamColor;
import net.klaaswhite.c2w.domain.model.WoolColor;
import net.klaaswhite.c2w.domain.ops.FileSystemOps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GameStateMachine")
class GameStateMachineTest {

    private FakeLayoutManager layoutManager;
    private GameStateMachine gsm;

    /** Fake LayoutManager with in-memory layouts. */
    static class FakeLayoutManager extends LayoutManager {
        private final List<String> names = new ArrayList<>();
        private final Map<String, MapLayout> layouts = new HashMap<>();

        public FakeLayoutManager() {
            super(new File("/nonexistent"), (file, name) -> null, new FileSystemOps() {
                @Override public File[] listFiles(File dir) { return new File[0]; }
                @Override public File[] listFiles(File dir, String suffix) { return new File[0]; }
                @Override public boolean isFile(File file) { return false; }
                @Override public boolean isDirectory(File dir) { return false; }
                @Override public java.io.InputStream newInputStream(File file) throws java.io.IOException { return null; }
                @Override public boolean createFile(File file) throws java.io.IOException { return false; }
                @Override public boolean delete(File file) { return false; }
            });
        }

        void addLayout(String name, MapLayout layout) {
            names.add(name);
            layouts.put(name, layout);
        }

        @Override
        public List<String> getLayoutNames() { return new ArrayList<>(names); }

        @Override
        public @Nullable MapLayout getLayout(String name) { return layouts.get(name); }
    }

    private MinecraftManager createMockMc() {
        var mc = mock(MinecraftManager.class);
        var bossBars = mock(BossBars.class);
        var bossBar = mock(BossBar.class);
        when(mc.bossBars()).thenReturn(bossBars);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);
        return mc;
    }

    private ManagedPlayer createPlayer() {
        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("test");
        when(handle.getDisplayName()).thenReturn("test");
        return new ManagedPlayer(handle);
    }

    private Wool createWool(WoolColor color) {
        var mc = createMockMc();
        var woolTimer = new WoolTimer(new WoolTimer.Scheduler() {
            public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
            public void cancel(Object taskId) {}
        });
        return new Wool(mc, woolTimer, color, new BlockPos(0, 64, 0), "game");
    }

    @BeforeEach
    void setUp() {
        layoutManager = new FakeLayoutManager();
        gsm = new GameStateMachine(layoutManager);
    }

    // --- Initial state ---

    @Test
    @DisplayName("starts in NOT_STARTED state")
    void initialState() {
        assertEquals(GameStateMachine.State.NOT_STARTED, gsm.getState());
        assertTrue(gsm.isNotStarted());
        assertFalse(gsm.isDraftCreated());
        assertFalse(gsm.isGameInProgress());
        assertFalse(gsm.isGameEnded());
    }

    // --- Init ---

    @Test
    @DisplayName("canInit returns true when in NOT_STARTED")
    void canInitFromNotStarted() {
        assertTrue(gsm.canInit());
    }

    @Test
    @DisplayName("canInit returns false when not in NOT_STARTED")
    void cannotInitFromOtherStates() {
        gsm.transitionToDraftCreated();
        assertFalse(gsm.canInit());

        gsm.transitionToGameInProgress();
        assertFalse(gsm.canInit());
    }

    @Test
    @DisplayName("transitionToDraftCreated moves to DRAFT_CREATED")
    void transitionToDraftCreated() {
        gsm.transitionToDraftCreated();
        assertEquals(GameStateMachine.State.DRAFT_CREATED, gsm.getState());
        assertTrue(gsm.isDraftCreated());
        assertFalse(gsm.isNotStarted());
    }

    // --- Start ---

    @Test
    @DisplayName("canStart returns true when in DRAFT_CREATED")
    void canStartFromDraftCreated() {
        gsm.transitionToDraftCreated();
        assertTrue(gsm.canStart());
    }

    @Test
    @DisplayName("canStart returns false when not in DRAFT_CREATED")
    void cannotStartFromOtherStates() {
        assertFalse(gsm.canStart()); // NOT_STARTED

        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();
        assertFalse(gsm.canStart()); // GAME_IN_PROGRESS
    }

    @Test
    @DisplayName("resolveLayout returns null when no layouts exist")
    void resolveLayoutNoLayouts() {
        gsm.transitionToDraftCreated();
        assertNull(gsm.resolveLayout(null));
    }

    @Test
    @DisplayName("resolveLayout returns first layout when name is null")
    void resolveLayoutDefaultsToFirst() {
        var layout = new MapLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), List.of());
        layoutManager.addLayout("arena", layout);

        gsm.transitionToDraftCreated();
        assertEquals(layout, gsm.resolveLayout(null));
    }

    @Test
    @DisplayName("resolveLayout returns named layout when specified")
    void resolveLayoutByName() {
        var layout1 = new MapLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), List.of());
        var layout2 = new MapLayout("flat", 16, 10, 16, new BlockPos(0, 0, 0), List.of());
        layoutManager.addLayout("arena", layout1);
        layoutManager.addLayout("flat", layout2);

        gsm.transitionToDraftCreated();
        assertEquals(layout2, gsm.resolveLayout("flat"));
    }

    @Test
    @DisplayName("resolveLayout returns null for unknown layout name")
    void resolveLayoutUnknown() {
        layoutManager.addLayout("arena", new MapLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), List.of()));

        gsm.transitionToDraftCreated();
        assertNull(gsm.resolveLayout("unknown"));
    }

    @Test
    @DisplayName("transitionToGameInProgress moves to GAME_IN_PROGRESS")
    void transitionToGameInProgress() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();

        assertEquals(GameStateMachine.State.GAME_IN_PROGRESS, gsm.getState());
        assertTrue(gsm.isGameInProgress());
    }

    // --- End ---

    @Test
    @DisplayName("canEnd returns true when in GAME_IN_PROGRESS")
    void canEndFromInProgress() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();
        assertTrue(gsm.canEnd());
    }

    @Test
    @DisplayName("canEnd returns false when not in GAME_IN_PROGRESS")
    void cannotEndFromOtherStates() {
        assertFalse(gsm.canEnd()); // NOT_STARTED

        gsm.transitionToDraftCreated();
        assertFalse(gsm.canEnd()); // DRAFT_CREATED
    }

    @Test
    @DisplayName("transitionToGameEnded moves to GAME_ENDED")
    void transitionToGameEnded() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();
        gsm.transitionToGameEnded();

        assertEquals(GameStateMachine.State.GAME_ENDED, gsm.getState());
        assertTrue(gsm.isGameEnded());
    }

    // --- Full lifecycle ---

    @Test
    @DisplayName("full lifecycle: NOT_STARTED → DRAFT_CREATED → GAME_IN_PROGRESS → GAME_ENDED")
    void fullLifecycle() {
        assertTrue(gsm.isNotStarted());

        gsm.transitionToDraftCreated();
        assertTrue(gsm.isDraftCreated());

        var layout = new MapLayout("arena", 16, 10, 16, new BlockPos(0, 0, 0), List.of());
        layoutManager.addLayout("arena", layout);
        gsm.transitionToGameInProgress();
        assertTrue(gsm.isGameInProgress());

        gsm.transitionToGameEnded();
        assertTrue(gsm.isGameEnded());
    }

    @Test
    @DisplayName("reset returns to NOT_STARTED and clears capped wools")
    void reset() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();

        // Simulate a win and end
        var team = new ManagedTeam("Red", TeamColor.RED);
        var player = createPlayer();
        player.setTeam(team);
        var wool1 = createWool(WoolColor.RED);
        gsm.onWoolCaptured(new WoolCapturedEvent(player, wool1));
        var wool2 = createWool(WoolColor.BLUE);
        gsm.onWoolCaptured(new WoolCapturedEvent(player, wool2));

        assertTrue(gsm.isGameEnded());

        gsm.reset();
        assertTrue(gsm.isNotStarted());
        assertEquals(GameStateMachine.State.NOT_STARTED, gsm.getState());
    }

    // --- Win condition ---

    @Test
    @DisplayName("first wool cap is recorded but does not end game")
    void firstCapRecorded() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();

        var team = new ManagedTeam("Red", TeamColor.RED);
        var player = createPlayer();
        player.setTeam(team);
        var wool = createWool(WoolColor.RED);

        gsm.onWoolCaptured(new WoolCapturedEvent(player, wool));

        assertTrue(gsm.isGameInProgress(), "game should still be in progress after one cap");
    }

    @Test
    @DisplayName("second wool cap ends the game")
    void secondCapEndsGame() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();

        var team = new ManagedTeam("Red", TeamColor.RED);
        var player = createPlayer();
        player.setTeam(team);
        var wool1 = createWool(WoolColor.RED);
        var wool2 = createWool(WoolColor.BLUE);

        gsm.onWoolCaptured(new WoolCapturedEvent(player, wool1));
        assertTrue(gsm.isGameInProgress());

        gsm.onWoolCaptured(new WoolCapturedEvent(player, wool2));

        assertTrue(gsm.isGameEnded(), "game should end after second cap");
    }

    @Test
    @DisplayName("two teams can each cap one wool without ending game")
    void separateTeamCaps() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();

        var redTeam = new ManagedTeam("Red", TeamColor.RED);
        var blueTeam = new ManagedTeam("Blue", TeamColor.BLUE);
        var redPlayer = createPlayer();
        redPlayer.setTeam(redTeam);
        var bluePlayer = createPlayer();
        bluePlayer.setTeam(blueTeam);

        gsm.onWoolCaptured(new WoolCapturedEvent(redPlayer, createWool(WoolColor.RED)));
        assertTrue(gsm.isGameInProgress(), "game still in progress after red caps one");

        gsm.onWoolCaptured(new WoolCapturedEvent(bluePlayer, createWool(WoolColor.BLUE)));
        assertTrue(gsm.isGameInProgress(), "game still in progress after blue caps one");
    }

    @Test
    @DisplayName("team needs two caps even if other team has one")
    void teamNeedsTwoCaps() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();

        var redTeam = new ManagedTeam("Red", TeamColor.RED);
        var redPlayer = createPlayer();
        redPlayer.setTeam(redTeam);

        // Blue caps one
        var blueTeam = new ManagedTeam("Blue", TeamColor.BLUE);
        var bluePlayer = createPlayer();
        bluePlayer.setTeam(blueTeam);
        gsm.onWoolCaptured(new WoolCapturedEvent(bluePlayer, createWool(WoolColor.BLUE)));

        // Red caps two
        gsm.onWoolCaptured(new WoolCapturedEvent(redPlayer, createWool(WoolColor.RED)));
        assertTrue(gsm.isGameInProgress(), "game still in progress after red caps one");

        gsm.onWoolCaptured(new WoolCapturedEvent(redPlayer, createWool(WoolColor.GREEN)));
        assertTrue(gsm.isGameEnded(), "game ends after red caps two");
    }

    // --- Invalid transitions ---

    @Test
    @DisplayName("transitionToDraftCreated from GAME_IN_PROGRESS still works (no guard — manager guards)")
    void transitionFromAnyState() {
        gsm.transitionToGameInProgress();
        gsm.transitionToDraftCreated(); // No guard in GSM, manager uses canInit() guard
        assertEquals(GameStateMachine.State.DRAFT_CREATED, gsm.getState());
    }

    @Test
    @DisplayName("onWoolCaptured does not throw when player has no team")
    void onWoolCapturedNoTeam() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();

        var player = createPlayer(); // no team
        var wool = createWool(WoolColor.RED);

        assertDoesNotThrow(() -> gsm.onWoolCaptured(new WoolCapturedEvent(player, wool)));
    }

    @Test
    @DisplayName("cappedWools are tracked per team")
    void cappedWoolsPerTeam() {
        gsm.transitionToDraftCreated();
        gsm.transitionToGameInProgress();

        var redTeam = new ManagedTeam("Red", TeamColor.RED);
        var blueTeam = new ManagedTeam("Blue", TeamColor.BLUE);
        var redPlayer = createPlayer();
        redPlayer.setTeam(redTeam);
        var bluePlayer = createPlayer();
        bluePlayer.setTeam(blueTeam);

        gsm.onWoolCaptured(new WoolCapturedEvent(redPlayer, createWool(WoolColor.RED)));
        gsm.onWoolCaptured(new WoolCapturedEvent(bluePlayer, createWool(WoolColor.BLUE)));

        // Both teams have one cap, game still in progress
        assertTrue(gsm.isGameInProgress());

        // Red caps second → Red wins
        gsm.onWoolCaptured(new WoolCapturedEvent(redPlayer, createWool(WoolColor.GREEN)));
        assertTrue(gsm.isGameEnded());
    }
}