package net.klaaswhite.c2w.domain.game;

import net.klaaswhite.c2w.domain.model.DomainBoundingBox;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.Wool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;

/**
 * Pure-domain boundary engine. Handles bounding box containment checks,
 * pit/elevator capture logic, and capping modifier calculation.
 * <p>
 * Does NOT depend on Bukkit — only on domain model classes and an abstract
 * {@link WoolTimerBridge} for timer registration.
 * Fully testable via fakes.
 */
public class BoundaryEngine implements AutoCloseable {

    /** Bridge interface for WoolTimer operations (avoids direct dependency). */
    public interface WoolTimerBridge {
        int getBaseCapture();
        int getIncreasePerPlayer();
        int getDecreasePerPlayer();
        void registerWool(Wool wool);
        void unregisterWool(Wool wool);
    }

    private final WoolTimerBridge timer;
    private final ArrayList<BoundingBoxAction> boundingBoxes;
    private final HashMap<ManagedTeam, HashSet<ManagedPlayer>> teamPlayersInPit;
    private final HashSet<ManagedPlayer> playersInPit;
    private final HashSet<Wool> woolsInPit;

    public BoundaryEngine(WoolTimerBridge timer) {
        this.timer = timer;
        this.boundingBoxes = new ArrayList<>();
        this.teamPlayersInPit = new HashMap<>();
        this.playersInPit = new HashSet<>();
        this.woolsInPit = new HashSet<>();
    }

    public boolean hasBoundingBoxes() {
        return !boundingBoxes.isEmpty();
    }

    public boolean isPlayerInPit(ManagedPlayer player) {
        return playersInPit.contains(player);
    }

    public int getPitEnterCount() {
        return playersInPit.size();
    }

    // --- Setup ---

    public void initialize(HashSet<ManagedTeam> teams) {
        boundingBoxes.clear();
        playersInPit.clear();
        teamPlayersInPit.clear();
        woolsInPit.clear();
        for (var team : teams) {
            teamPlayersInPit.put(team, new HashSet<>());
        }
    }

    public void addPitBox(DomainBoundingBox box) {
        boundingBoxes.add(new BoundingBoxAction(box, this::pitPlayerEnter, this::pitPlayerExit));
    }

    public void addElevatorBox(DomainBoundingBox box) {
        boundingBoxes.add(new BoundingBoxAction(box, this::elevatorEnter, null));
    }

    // --- Player movement ---

    public void onPlayerMove(double fromX, double fromY, double fromZ,
                             double toX, double toY, double toZ,
                             ManagedPlayer player) {
        if (boundingBoxes.isEmpty()) return;

        for (var action : boundingBoxes) {
            var fromInBox = action.box.contains(fromX, fromY, fromZ);
            var toInBox = action.box.contains(toX, toY, toZ);
            if (fromInBox && !toInBox && action.exitEvent != null)
                action.exitEvent.accept(player);
            if (!fromInBox && toInBox)
                action.enterEvent.accept(player);
        }
    }

    // --- Pit logic ---

    private void pitPlayerEnter(ManagedPlayer player) {
        var team = player.getTeam();
        if (team == null) return;

        ensureTeamPitSet(team).add(player);
        playersInPit.add(player);

        if (player.getCarry() instanceof Wool wool) {
            woolsInPit.add(wool);
            wool.setCapping(true);
            timer.registerWool(wool);
        }
        recalcModifiers();
    }

    private void pitPlayerExit(ManagedPlayer player) {
        var team = player.getTeam();
        if (team == null) return;

        var teamSet = teamPlayersInPit.get(team);
        if (teamSet != null) teamSet.remove(player);
        playersInPit.remove(player);

        if (player.getCarry() instanceof Wool wool) {
            wool.setCapping(false);
            woolsInPit.remove(wool);
            timer.unregisterWool(wool);
        }
        recalcModifiers();
    }

    // --- Elevator logic ---

    private void elevatorEnter(ManagedPlayer player) {
        if (player.getCarry() instanceof Wool wool) {
            wool.capture();
        }
    }

    // --- Events ---

    public void onWoolDropped(Wool wool) {
        if (woolsInPit.remove(wool)) {
            timer.unregisterWool(wool);
        }
    }

    public void onWoolCaptured(Wool wool) {
        if (woolsInPit.remove(wool)) {
            timer.unregisterWool(wool);
        }
    }

    // --- Modifier calculation ---

    private void recalcModifiers() {
        for (var wool : woolsInPit) {
            var carrier = wool.getCarrier();
            if (carrier == null) continue;
            var team = carrier.getTeam();
            if (team == null) continue;
            var teamInPit = teamPlayersInPit.get(team).size();
            var enemyInPit = playersInPit.size() - teamInPit;
            if (enemyInPit > teamInPit) {
                wool.setCappingModifier(0);
                continue;
            }
            int modifier = timer.getBaseCapture()
                    + timer.getIncreasePerPlayer() * teamInPit
                    - timer.getDecreasePerPlayer() * enemyInPit;
            wool.setCappingModifier(modifier);
        }
    }

    // --- Player removal (e.g. death/disconnect) ---

    public void removePlayer(ManagedPlayer player) {
        if (!playersInPit.remove(player)) return;

        var team = player.getTeam();
        if (team != null) {
            var teamSet = teamPlayersInPit.get(team);
            if (teamSet != null) teamSet.remove(player);
        }
        if (player.getCarry() instanceof Wool wool) {
            wool.setCapping(false);
            woolsInPit.remove(wool);
            timer.unregisterWool(wool);
        }
        recalcModifiers();
    }

    // --- Reset ---

    public void reset() {
        boundingBoxes.clear();
        playersInPit.clear();
        teamPlayersInPit.clear();
        woolsInPit.clear();
    }

    @Override
    public void close() {
        reset();
    }

    // --- Helpers ---

    private HashSet<ManagedPlayer> ensureTeamPitSet(ManagedTeam team) {
        return teamPlayersInPit.computeIfAbsent(team, k -> new HashSet<>());
    }

    private record BoundingBoxAction(
            DomainBoundingBox box,
            Consumer<ManagedPlayer> enterEvent,
            Consumer<ManagedPlayer> exitEvent
    ) {}
}