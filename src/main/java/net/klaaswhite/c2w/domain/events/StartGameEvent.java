package net.klaaswhite.c2w.domain.events;

/**
 * Fired by {@link net.klaaswhite.c2w.managers.GameManager} after the game
 * world has been created and all structures (per the chosen layout) have
 * been transported into it. The carried {@code gameWorldName} is the world to
 * operate on for the rest of the game.
 */
public class StartGameEvent implements C2WEvent {

    private final String gameWorldName;

    public StartGameEvent(String gameWorldName) {
        this.gameWorldName = gameWorldName;
    }

    public String getGameWorldName() {
        return gameWorldName;
    }
}
