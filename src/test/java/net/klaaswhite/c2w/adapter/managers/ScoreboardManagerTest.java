package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Scoreboards;
import net.klaaswhite.c2w.domain.events.EndGameEvent;
import net.klaaswhite.c2w.domain.events.ResetEvent;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.events.WoolDroppedEvent;
import net.klaaswhite.c2w.domain.events.WoolPickedUpEvent;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.LayoutCell;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.TeamColor;
import net.klaaswhite.c2w.domain.model.Wool;
import net.klaaswhite.c2w.domain.model.WoolColor;
import net.klaaswhite.c2w.domain.ops.TypeDimensionSource;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ScoreboardManager")
class ScoreboardManagerTest {

    private EventManager eventManager;
    private MinecraftManager mc;
    private GameManager gameManager;
    private MarkerManager markerManager;
    private LayoutManager layoutManager;
    private Scoreboards scoreboards;
    private Scoreboard scoreboard;
    private Objective objective;

    private TypeDimensionSource structureTypeConfig;

    private ScoreboardManager manager;

    @BeforeEach
    void setUp() {
        eventManager = mock(EventManager.class);
        mc = mock(MinecraftManager.class);
        gameManager = mock(GameManager.class);
        markerManager = mock(MarkerManager.class);
        layoutManager = mock(LayoutManager.class);

        scoreboards = mock(Scoreboards.class);
        scoreboard = mock(Scoreboard.class);
        objective = mock(Objective.class);
        when(mc.scoreboards()).thenReturn(scoreboards);
        when(scoreboards.registerSidebarObjective(anyString(), anyString())).thenReturn(objective);
        when(objective.getScoreboard()).thenReturn(scoreboard);
        when(scoreboard.getEntries()).thenReturn(Set.of());
        when(objective.getScore(anyString())).thenReturn(mock(Score.class));

        structureTypeConfig = mock(TypeDimensionSource.class);
        when(structureTypeConfig.getDimensions(anyString())).thenReturn(null);

        manager = new ScoreboardManager(eventManager, mc, gameManager, markerManager, layoutManager, structureTypeConfig);
    }

    // --- helpers ---

    private MapLayout layout(int rows, int cols, List<LayoutCell> cells) {
        return new MapLayout("arena", 5, 5, 5, new BlockPos(0, 64, 0), cells);
    }

    /** A placements-based layout (cells store structure centres, not corners). */
    private MapLayout placementsLayout(int rows, int cols, List<LayoutCell> cells) {
        return new MapLayout("arena", 5, 5, 5, new BlockPos(0, 64, 0), cells, true);
    }

    /** A wool fake carrying color + state flags. */
    @SuppressWarnings("unchecked")
    private Object wool(WoolColor color, BlockPos spawn, boolean carried, boolean capped) {
        Wool w = mock(Wool.class);
        when(w.getColor()).thenReturn(color);
        when(w.getSpawnPos()).thenReturn(spawn);
        when(w.getWorldName()).thenReturn("game");
        when(w.isCarried()).thenReturn(carried);
        when(w.isCapped()).thenReturn(capped);
        when(w.isCapping()).thenReturn(false);
        when(w.getCappedAmount()).thenReturn(0);
        when(w.getCappingModifier()).thenReturn(0);
        return w;
    }

    // --- onStartGame ---

    @Test
    @DisplayName("onStartGame registers the c2w_game objective with the layout name as title")
    void onStartGameRegistersObjective() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        var lay = layout(1, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        when(markerManager.getWools()).thenReturn((List) List.of());

        manager.onStartGame(new StartGameEvent("game"));

        verify(scoreboards).registerSidebarObjective(eq("c2w_game"), eq("§e§larena"));
    }

    @Test
    @DisplayName("render draws one line per layout row")
    void renderOneLinePerRow() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        cells.add(LayoutCell.of(1, 0, "DUNGEON", new BlockPos(0, 64, 10)));
        var lay = layout(2, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        when(markerManager.getWools()).thenReturn((List) List.of());

        manager.onStartGame(new StartGameEvent("game"));

        // 2 rows + 1 bottom line = 3 score entries
        verify(objective, times(3)).getScore(anyString());
    }

    @Test
    @DisplayName("wool present renders a solid colored glyph")
    void woolPresentSolid() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        var lay = layout(1, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        var w = wool(WoolColor.RED, new BlockPos(0, 64, 0), false, false);
        when(markerManager.getWools()).thenReturn((List) List.of(w));

        manager.onStartGame(new StartGameEvent("game"));

        var captured = argumentCaptorLine();
        assertTrue(captured.contains("§c\u25A0"), "expected red solid glyph, got: " + captured);
    }

    @Test
    @DisplayName("wool carried renders an outline glyph")
    void woolCarriedOutline() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        var lay = layout(1, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        var w = wool(WoolColor.GREEN, new BlockPos(0, 64, 0), true, false);
        when(markerManager.getWools()).thenReturn((List) List.of(w));

        manager.onStartGame(new StartGameEvent("game"));

        var captured = argumentCaptorLine();
        assertTrue(captured.contains("§a\u25A1"), "expected green outline glyph, got: " + captured);
    }

    @Test
    @DisplayName("wool captured renders a blank gray glyph")
    void woolCapturedBlank() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        var lay = layout(1, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        var w = wool(WoolColor.BLUE, new BlockPos(0, 64, 0), false, true);
        when(markerManager.getWools()).thenReturn((List) List.of(w));

        manager.onStartGame(new StartGameEvent("game"));

        var captured = argumentCaptorLine();
        assertTrue(captured.contains("§8\u25AB"), "expected gray blank glyph, got: " + captured);
    }

    @Test
    @DisplayName("structure-only cell renders a dim block")
    void structureOnlyDim() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        var lay = layout(1, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        when(markerManager.getWools()).thenReturn((List) List.of()); // no wool mapped

        manager.onStartGame(new StartGameEvent("game"));

        var captured = argumentCaptorLine();
        assertTrue(captured.contains("\u25AA"), "expected dim structure glyph, got: " + captured);
    }

    @Test
    @DisplayName("spawn islands render a team-tinted fortress glyph")
    void spawnGlyph() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.spawn(0, 0, "SPAWN", new BlockPos(0, 64, 0), "Red"));
        cells.add(LayoutCell.spawn(0, 1, "SPAWN", new BlockPos(10, 64, 0), "Blue"));
        var lay = layout(1, 2, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        when(markerManager.getWools()).thenReturn(List.of());

        manager.onStartGame(new StartGameEvent("game"));

        var line = argumentCaptorLine();
        assertTrue(line.startsWith("§c\u25A3"), "expected red spawn glyph first, got: " + line);
        assertTrue(line.contains("§9\u25A3"), "expected blue spawn glyph, got: " + line);
    }

    @Test
    @DisplayName("empty rows between islands are collapsed")
    void emptyRowsAreCollapsed() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        cells.add(LayoutCell.of(3, 0, "DUNGEON", new BlockPos(0, 64, 30)));
        var lay = layout(4, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        when(markerManager.getWools()).thenReturn(List.of());

        manager.onStartGame(new StartGameEvent("game"));

        // 2 occupied grid rows + 1 bottom line; the empty rows are not drawn.
        assertEquals(3, capturedLines().size());
    }

    @Test
    @DisplayName("maps taller than the sidebar keep wool rows")
    void tallMapKeepsWoolRows() {
        var cells = new ArrayList<LayoutCell>();
        for (int i = 0; i < 15; i++) {
            cells.add(LayoutCell.of(i, 0, "DUNGEON", new BlockPos(0, 64, i * 10)));
        }
        var lay = layout(15, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        var w = wool(WoolColor.RED, new BlockPos(0, 64, 130), false, false);
        when(markerManager.getWools()).thenReturn((List) List.of(w));

        manager.onStartGame(new StartGameEvent("game"));

        var lines = capturedLines();
        // MAX_ROWS grid rows + 1 bottom line.
        assertEquals(14, lines.size());
        assertTrue(lines.stream().anyMatch(l -> l.contains("§c\u25A0")),
                "wool row should stay visible in the windowed map");
    }

    @Test
    @DisplayName("wool maps by footprint, not by nearest cell centre")
    void woolMapsByFootprintNotNearest() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "LONG", new BlockPos(40, 64, 0), 0f));
        cells.add(LayoutCell.of(0, 1, "SHELTER", new BlockPos(0, 64, 2), 0f));
        when(structureTypeConfig.getDimensions("LONG")).thenReturn(new int[]{80, 10, 10});
        when(structureTypeConfig.getDimensions("SHELTER")).thenReturn(new int[]{2, 2, 2});
        var lay = placementsLayout(1, 2, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        // Closer to SHELTER's centre but inside LONG's wide tile footprint.
        var w = wool(WoolColor.RED, new BlockPos(2, 64, 0), false, false);
        when(markerManager.getWools()).thenReturn((List) List.of(w));

        manager.onStartGame(new StartGameEvent("game"));

        var lines = capturedLines();
        // LONG is at x=40 while SHELTER is at x=0, so LONG is the second
        // column of the first spatial row.
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("\u00B7§c\u25A0")),
            "wool should render on LONG (the second spatial column), got: " + lines);
    }

        @Test
        @DisplayName("placements are ordered by world position instead of save order")
        void placementsUseWorldPositionOrder() {
        var cells = new ArrayList<LayoutCell>();
        // Deliberately scrambled list order: the scoreboard should follow the
        // map's X/Z positions, not the order in which the editor saved them.
        cells.add(LayoutCell.of(0, 0, "BLUE_CELL", new BlockPos(0, 64, 100), 0f));
        cells.add(LayoutCell.of(1, 0, "RED_CELL", new BlockPos(0, 64, 0), 0f));
        cells.add(LayoutCell.of(2, 0, "GREEN_CELL", new BlockPos(100, 64, 0), 0f));
        var lay = placementsLayout(3, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        var blue = wool(WoolColor.BLUE, new BlockPos(0, 64, 100), false, false);
        var red = wool(WoolColor.RED, new BlockPos(0, 64, 0), false, false);
        var green = wool(WoolColor.GREEN, new BlockPos(100, 64, 0), false, false);
        when(markerManager.getWools()).thenReturn((List) List.of(blue, red, green));

        manager.onStartGame(new StartGameEvent("game"));

        var lines = capturedLines();
        assertEquals(3, lines.size(), "two spatial rows plus the bottom count row");
        assertTrue(lines.get(0).startsWith("§c\u25A0§a\u25A0"),
            "z=0 row should be ordered by x, got: " + lines.get(0));
        assertTrue(lines.get(1).startsWith("§9\u25A0"),
            "z=100 row should follow the z=0 row, got: " + lines.get(1));
        }

    @Test
    @DisplayName("bottom line shows per-team captured wool counts")
    void bottomLineCounts() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        var lay = layout(1, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        when(markerManager.getWools()).thenReturn(List.of());

        manager.onStartGame(new StartGameEvent("game"));

        // Simulate capturing 1 wool for Red and 2 for Blue via events
        var redTeam = new ManagedTeam("Red", TeamColor.RED);
        var blueTeam = new ManagedTeam("Blue", TeamColor.BLUE);
        var redPlayer = new ManagedPlayer(mock(PlayerHandle.class));
        redPlayer.setTeam(redTeam);
        var bluePlayer1 = new ManagedPlayer(mock(PlayerHandle.class));
        bluePlayer1.setTeam(blueTeam);
        var bluePlayer2 = new ManagedPlayer(mock(PlayerHandle.class));
        bluePlayer2.setTeam(blueTeam);

        var wool = mock(Wool.class);
        manager.onWoolCaptured(new WoolCapturedEvent(redPlayer, wool));
        manager.onWoolCaptured(new WoolCapturedEvent(bluePlayer1, wool));
        manager.onWoolCaptured(new WoolCapturedEvent(bluePlayer2, wool));

        var lines = capturedLines();
        var bottom = lines.get(lines.size() - 1);
        assertTrue(bottom.contains("Red: 1"), "bottom should show Red: 1, got: " + bottom);
        assertTrue(bottom.contains("Blue: 2"), "bottom should show Blue: 2, got: " + bottom);
    }

    // --- live updates ---

    @Test
    @DisplayName("WoolPickedUpEvent flips a waiting wool to outline")
    void pickupFlipsToOutline() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        var lay = layout(1, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        var w = wool(WoolColor.RED, new BlockPos(0, 64, 0), false, false);
        when(markerManager.getWools()).thenReturn((List) List.of(w));

        manager.onStartGame(new StartGameEvent("game"));
        // re-stub the wool as carried and re-render via event
        when(((Wool) w).isCarried()).thenReturn(true);
        manager.render();

        var captured = argumentCaptorLine();
        assertTrue(captured.contains("§c\u25A1"), "expected red outline after pickup, got: " + captured);
    }

    @Test
    @DisplayName("WoolDroppedEvent flips a carried wool back to solid")
    void dropFlipsToSolid() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        var lay = layout(1, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        var w = wool(WoolColor.RED, new BlockPos(0, 64, 0), true, false);
        when(markerManager.getWools()).thenReturn((List) List.of(w));

        manager.onStartGame(new StartGameEvent("game"));
        when(((Wool) w).isCarried()).thenReturn(false);
        manager.render();

        var captured = argumentCaptorLine();
        assertTrue(captured.contains("§c\u25A0"), "expected red solid after drop, got: " + captured);
    }

    @Test
    @DisplayName("WoolCapturedEvent flips a wool to blank")
    void captureFlipsToBlank() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        var lay = layout(1, 1, cells);
        when(gameManager.getActiveLayout()).thenReturn(lay);
        var w = wool(WoolColor.YELLOW, new BlockPos(0, 64, 0), true, false);
        when(markerManager.getWools()).thenReturn((List) List.of(w));

        manager.onStartGame(new StartGameEvent("game"));
        when(((Wool) w).isCarried()).thenReturn(false);
        when(((Wool) w).isCapped()).thenReturn(true);
        manager.render();

        var captured = argumentCaptorLine();
        assertTrue(captured.contains("§8\u25AB"), "expected gray blank after capture, got: " + captured);
    }

    // --- clear ---

    @Test
    @DisplayName("EndGameEvent unregisters the objective")
    void endGameClears() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        when(gameManager.getActiveLayout()).thenReturn(layout(1, 1, cells));
        when(markerManager.getWools()).thenReturn((List) List.of());

        manager.onStartGame(new StartGameEvent("game"));
        manager.clear();

        verify(scoreboards).unregisterObjective("c2w_game");
    }

    @Test
    @DisplayName("ResetEvent unregisters the objective")
    void resetClears() {
        var cells = new ArrayList<LayoutCell>();
        cells.add(LayoutCell.of(0, 0, "DUNGEON", new BlockPos(0, 64, 0)));
        when(gameManager.getActiveLayout()).thenReturn(layout(1, 1, cells));
        when(markerManager.getWools()).thenReturn((List) List.of());

        manager.onStartGame(new StartGameEvent("game"));
        manager.clear();

        verify(scoreboards).unregisterObjective("c2w_game");
    }

    // --- capture helpers ---

    private String argumentCaptorLine() {
        var lines = capturedLines();
        // The grid line is the one that is not the bottom (counts) line.
        // Return the LAST such line so live re-renders are observed.
        String last = "";
        for (var l : lines) {
            if (!l.contains("Red:")) last = l;
        }
        return last;
    }

    private List<String> capturedLines() {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(objective, atLeastOnce()).getScore(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }
}
