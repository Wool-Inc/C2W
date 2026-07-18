package net.klaaswhite.c2w.domain.game;

import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.managers.LayoutManager;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.MapLayout;
import net.klaaswhite.c2w.domain.model.Wool;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Pure-domain game state machine. Handles state validation, transitions,
 * layout resolution, and win-condition checking (2 wool caps per team).
 * <p>
 * Does NOT depend on Bukkit — only on domain port interfaces and managers.
 * Fully testable via fakes.
 */
public class GameStateMachine {

    public enum State {
        NOT_STARTED,
        DRAFT_CREATED,
        GAME_IN_PROGRESS,
        GAME_ENDED
    }

    private State state;
    private final LayoutManager layoutManager;
    private final Hashtable<ManagedTeam, ArrayList<Wool>> cappedWools;

    public GameStateMachine(LayoutManager layoutManager) {
        this.layoutManager = layoutManager;
        this.state = State.NOT_STARTED;
        this.cappedWools = new Hashtable<ManagedTeam, ArrayList<Wool>>();
    }

    // --- State queries ---

    public State getState() {
        return state;
    }

    public boolean isNotStarted() {
        return state == State.NOT_STARTED;
    }

    public boolean isDraftCreated() {
        return state == State.DRAFT_CREATED;
    }

    public boolean isGameInProgress() {
        return state == State.GAME_IN_PROGRESS;
    }

    public boolean isGameEnded() {
        return state == State.GAME_ENDED;
    }

    // --- Guards ---

    public boolean canInit() {
        return state == State.NOT_STARTED;
    }

    public boolean canStart() {
        return state == State.DRAFT_CREATED;
    }

    public boolean canEnd() {
        return state == State.GAME_IN_PROGRESS;
    }

    // --- Transitions ---

    public void transitionToDraftCreated() {
        this.state = State.DRAFT_CREATED;
    }

    public void transitionToGameInProgress() {
        this.state = State.GAME_IN_PROGRESS;
    }

    public void transitionToGameEnded() {
        this.state = State.GAME_ENDED;
    }

    // --- Layout resolution ---

    public @Nullable MapLayout resolveLayout(@Nullable String layoutName) {
        var names = layoutManager.getLayoutNames();
        if (names.isEmpty()) return null;

        String chosen = layoutName;
        if (chosen == null || chosen.isBlank()) {
            chosen = names.get(0);
        }
        if (!names.contains(chosen)) return null;

        return layoutManager.getLayout(chosen);
    }

    // --- Win condition ---

    public void onWoolCaptured(WoolCapturedEvent event) {
        var player = event.getPlayer();
        var team = player.getTeam();
        if (team == null) return;

        var capturedWools = cappedWools.computeIfAbsent(team, k -> new ArrayList<>());
        capturedWools.add(event.getWool());
        if (capturedWools.size() >= 2) {
            transitionToGameEnded();
        }
    }

    // --- Layout names ---

    public List<String> resolveLayoutNames() {
        return layoutManager.getLayoutNames();
    }

    // --- Wool count ---

    public int getWoolCount(String teamName) {
        for (var entry : cappedWools.entrySet()) {
            if (entry.getKey().teamName.equals(teamName)) {
                return entry.getValue().size();
            }
        }
        return 0;
    }

    public Hashtable<ManagedTeam, ArrayList<Wool>> getCappedWools() {
        return cappedWools;
    }

    // --- Reset ---

    public void reset() {
        cappedWools.clear();
        state = State.NOT_STARTED;
    }
}