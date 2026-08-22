package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.events.EndGameEvent;
import net.klaaswhite.c2w.domain.events.ResetEvent;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.events.WoolDroppedEvent;
import net.klaaswhite.c2w.domain.events.WoolPickedUpEvent;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.LayoutCell;
import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.model.Wool;
import net.klaaswhite.c2w.domain.model.WoolColor;
import net.klaaswhite.c2w.domain.ops.TypeDimensionSource;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the in-game sidebar scoreboard: a top-down glyph "mock" of the active
 * {@link MapLayout} (one glyph per island showing its structure / spawn / wool
 * state) and per-team captured-wool counts at the bottom.
 * <p>
 * Only rows that actually contain an island are drawn, empty rows between
 * islands are collapsed, and if the map is taller than the sidebar the most
 * relevant rows (those holding a live wool) are kept so the wools stay visible.
 * Updates live on wool pickup / drop / capture via internal events. The sidebar
 * is capped at 15 lines by Minecraft, so the grid is limited to at most
 * {@link #MAX_ROWS} lines (title + bottom line take the rest).
 */
public class ScoreboardManager implements AutoCloseable {

    private static final String OBJECTIVE_NAME = "c2w_game";
    /** Max layout rows we render (15-line sidebar minus title + bottom line). */
    private static final int MAX_ROWS = 13;
    /** Cap on columns per line, so very wide maps don't overshoot the board. */
    private static final int MAX_COLS = 24;
    /** Glyph for a grid slot with no island. */
    private static final String EMPTY_SLOT = "\u00B7"; // ·
    /** Marker appended when a line was truncated at {@link #MAX_COLS}. */
    private static final String OVERFLOW_MARK = "\u2026"; // …

    private final EventManager eventManager;
    private final MinecraftManager mc;
    private final GameManager gameManager;
    private final MarkerManager markerManager;
    private final LayoutManager layoutManager;
    private final TypeDimensionSource structureTypeConfig;

    private @Nullable MapLayout activeLayout;
    private List<Wool> wools = List.of();
    private final Map<String, Integer> capturedCounts = new ConcurrentHashMap<>();

    public ScoreboardManager(
            EventManager eventManager,
            MinecraftManager mc,
            GameManager gameManager,
            MarkerManager markerManager,
            LayoutManager layoutManager,
            TypeDimensionSource structureTypeConfig
    ) {
        this.eventManager = eventManager;
        this.mc = mc;
        this.gameManager = gameManager;
        this.markerManager = markerManager;
        this.layoutManager = layoutManager;
        this.structureTypeConfig = structureTypeConfig;

        eventManager.registerInternalEvent(StartGameEvent.class, this::onStartGame);
        eventManager.registerInternalEvent(WoolPickedUpEvent.class, e -> render());
        eventManager.registerInternalEvent(WoolDroppedEvent.class, e -> render());
        eventManager.registerInternalEvent(WoolCapturedEvent.class, this::onWoolCaptured);
        eventManager.registerInternalEvent(EndGameEvent.class, e -> clear());
        eventManager.registerInternalEvent(ResetEvent.class, e -> clear());
    }

    void onStartGame(StartGameEvent event) {
        this.activeLayout = gameManager.getActiveLayout();
        // MarkerManager returns the adapter Wool, which implements the domain Wool
        // interface; wrap so the field can stay typed as the domain interface.
        this.wools = new ArrayList<>(markerManager.getWools());
        this.capturedCounts.clear();
        render();
    }

    void onWoolCaptured(WoolCapturedEvent event) {
        var team = event.getPlayer().getTeam();
        if (team != null) {
            capturedCounts.merge(team.teamName, 1, Integer::sum);
        }
        render();
    }

    /**
     * Build the sidebar: layout grid on top, team captured-wool counts on the
     * bottom line.
     */
    void render() {
        if (activeLayout == null || activeLayout.getCells().isEmpty()) return;

        String title = "§e§l" + activeLayout.getName();
        Objective objective = mc.scoreboards().registerSidebarObjective(OBJECTIVE_NAME, title);

        // Clear previous entries
        Scoreboard board = objective.getScoreboard();
        for (var entry : board.getEntries()) {
            board.resetScores(entry);
        }

        // Map each wool to the layout cell whose tile footprint contains its
        // spawn point (falling back to the nearest cell centre).
        Map<LayoutCell, Wool> woolByCell = mapWoolsToCells(activeLayout, wools);

        // Build a lookup of cell by (row,col) for quick access.
        Map<Long, LayoutCell> cellAt = new HashMap<>();
        for (var cell : activeLayout.getCells()) {
            cellAt.put(key(cell.row(), cell.col()), cell);
        }

        // Draw only the rows that actually contain an island; empty rows
        // between islands are collapsed so as many islands as possible fit.
        List<Integer> drawnRows = new ArrayList<>();
        for (int r = 0; r < activeLayout.getRows(); r++) {
            if (rowOccupied(cellAt, activeLayout.getCols(), r)) drawnRows.add(r);
        }

        // If the map is still taller than the sidebar, prefer rows holding a
        // live (uncaptured) wool, then fill the rest from the top of the map.
        if (drawnRows.size() > MAX_ROWS) {
            drawnRows = windowRows(drawnRows, woolByCell, cellAt, activeLayout.getCols());
        }

        int cols = Math.min(activeLayout.getCols(), MAX_COLS);
        boolean overflow = activeLayout.getCols() > MAX_COLS;

        // One line per drawn row, highest score (top) = first drawn row.
        for (int i = 0; i < drawnRows.size(); i++) {
            int r = drawnRows.get(i);
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                LayoutCell cell = cellAt.get(key(r, c));
                if (cell == null) {
                    line.append(EMPTY_SLOT);
                } else {
                    line.append(glyphForCell(cell, woolByCell.get(cell)));
                }
            }
            if (overflow) {
                line.append(OVERFLOW_MARK);
            }
            int score = drawnRows.size() - i; // first row -> highest score -> top
            objective.getScore(line.toString()).setScore(score);
        }

        // Bottom line: per-team captured wool counts (tracked locally from events).
        String bottom = "§cRed: " + capturedCounts.getOrDefault("Red", 0)
                + "  §9Blue: " + capturedCounts.getOrDefault("Blue", 0);
        objective.getScore(bottom).setScore(0);
    }

    void clear() {
        mc.scoreboards().unregisterObjective(OBJECTIVE_NAME);
        this.activeLayout = null;
        this.wools = List.of();
        this.capturedCounts.clear();
    }

    /**
     * Map each wool to the island (layout cell) that it belongs to. A cell
     * whose tile footprint contains the wool's spawn point wins (the closest
     * such cell if several overlap); otherwise fall back to the nearest cell
     * centre. Footprints are rotation-aware and use the configured structure
     * dimensions for placements-based layouts.
     */
    private Map<LayoutCell, Wool> mapWoolsToCells(MapLayout layout, List<Wool> wools) {
        Map<LayoutCell, Wool> result = new HashMap<>();
        for (var wool : wools) {
            BlockPos spawn = wool.getSpawnPos();
            LayoutCell nearest = null;
            LayoutCell contained = null;
            long nearestDistSq = Long.MAX_VALUE;
            long containedDistSq = Long.MAX_VALUE;
            for (var cell : layout.getCells()) {
                long distSq = centerDistSq(cell.worldPosition(), spawn);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = cell;
                }
                if (footprintContains(cell, layout, spawn) && distSq < containedDistSq) {
                    containedDistSq = distSq;
                    contained = cell;
                }
            }
            LayoutCell chosen = contained != null ? contained : nearest;
            if (chosen != null) result.put(chosen, wool);
        }
        return result;
    }

    /**
     * Whether the given world XZ point lies inside this cell's tile footprint.
     * Grid layouts store the tile corner, so the footprint extends +x/+z by the
     * layout tile size. Placements-based layouts store the structure centre, so
     * the footprint is centred; the configured structure-type dimensions are
     * preferred (the layout tile size is the fallback).
     */
    private boolean footprintContains(LayoutCell cell, MapLayout layout, BlockPos p) {
        BlockPos pos = cell.worldPosition();
        if (!layout.isPlacementsBased()) {
            return p.x() >= pos.x() && p.x() < pos.x() + layout.getTileWidth()
                    && p.z() >= pos.z() && p.z() < pos.z() + layout.getTileDepth();
        }
        int[] dims = structureTypeConfig.getDimensions(cell.typeName());
        int w = dims != null ? dims[0] : layout.getTileWidth();
        int d = dims != null ? dims[2] : layout.getTileDepth();
        return p.x() >= pos.x() - w / 2 && p.x() < pos.x() + w - w / 2
                && p.z() >= pos.z() - d / 2 && p.z() < pos.z() + d - d / 2;
    }

    private static long centerDistSq(BlockPos pos, BlockPos p) {
        long dx = (long) pos.x() - p.x();
        long dz = (long) pos.z() - p.z();
        return dx * dx + dz * dz;
    }

    private static boolean rowOccupied(Map<Long, LayoutCell> cellAt, int cols, int row) {
        for (int c = 0; c < cols; c++) {
            if (cellAt.get(key(row, c)) != null) return true;
        }
        return false;
    }

    /**
     * When the map has more filled rows than {@link #MAX_ROWS}, pick which to
     * draw: rows holding a live (uncaptured) wool first, then the top rows of
     * the map. Returns the chosen rows in ascending map-row order.
     */
    private static List<Integer> windowRows(
            List<Integer> drawnRows,
            Map<LayoutCell, Wool> woolByCell,
            Map<Long, LayoutCell> cellAt,
            int cols
    ) {
        List<Integer> live = new ArrayList<>();
        List<Integer> rest = new ArrayList<>();
        for (int r : drawnRows) {
            boolean hasLiveWool = false;
            for (int c = 0; c < cols && !hasLiveWool; c++) {
                LayoutCell cell = cellAt.get(key(r, c));
                Wool wool = cell == null ? null : woolByCell.get(cell);
                if (wool != null && !wool.isCapped()) hasLiveWool = true;
            }
            (hasLiveWool ? live : rest).add(r);
        }
        List<Integer> selected = new ArrayList<>(Math.min(MAX_ROWS, live.size() + rest.size()));
        for (int r : live) {
            if (selected.size() >= MAX_ROWS) break;
            selected.add(r);
        }
        for (int r : rest) {
            if (selected.size() >= MAX_ROWS) break;
            selected.add(r);
        }
        selected.sort(Integer::compareTo);
        return selected;
    }

    /**
     * Glyph for a cell, prioritising the wool state over the island kind:
     * <ul>
     *   <li>a wool is present → {@link #glyphForWool(Wool)}</li>
     *   <li>team spawn island → tinted fortress ▣ (red / blue)</li>
     *   <li>structure-only island → dim small square</li>
     * </ul>
     */
    private static String glyphForCell(LayoutCell cell, @Nullable Wool wool) {
        if (wool != null) return glyphForWool(wool);
        String team = cell.team();
        if (team != null) {
            return "Blue".equalsIgnoreCase(team) ? "§9\u25A3" : "§c\u25A3";
        }
        return "§8\u25AA";
    }

    /**
     * Glyph for a wool, encoding its color and state:
     * <ul>
     *   <li>present (not carried, not capped) → solid ■ in wool color</li>
     *   <li>carried/taken → outline □ in wool color</li>
     *   <li>capped → blank ▫ in gray (no colour)</li>
     * </ul>
     */
    private static String glyphForWool(Wool wool) {
        String colorCode = switch (wool.getColor()) {
            case RED -> "§c";
            case GREEN -> "§a";
            case BLUE -> "§9";
            case YELLOW -> "§e";
        };
        if (wool.isCapped()) {
            return "§8\u25AB"; // captured: no colour
        }
        if (wool.isCarried()) {
            return colorCode + "\u25A1"; // taken / outline
        }
        return colorCode + "\u25A0"; // present / solid
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }

    @Override
    public void close() {
        clear();
    }
}
