package net.klaaswhite.c2w.domain.events;

/**
 * Fired by {@link net.klaaswhite.c2w.managers.GameManager} after the game
 * world has been created and all structures (per the chosen layout) have
 * been transported into it. The carried {@code gameWorldName} is the world to
 * operate on for the rest of the game.
 */
public class StartGameEvent implements C2WEvent {

    /** Sentinel used by older callers that do not provide a death plane. */
    public static final int NO_DEATH_PLANE = Integer.MIN_VALUE;

    private final String gameWorldName;
    private final int deathPlaneY;

    public StartGameEvent(String gameWorldName) {
        this(gameWorldName, NO_DEATH_PLANE);
    }

    public StartGameEvent(String gameWorldName, int deathPlaneY) {
        this.gameWorldName = gameWorldName;
        this.deathPlaneY = deathPlaneY;
    }

    public String getGameWorldName() {
        return gameWorldName;
    }

    /** The Y threshold below which players in the game world are killed. */
    public int getDeathPlaneY() {
        return deathPlaneY;
    }
}
