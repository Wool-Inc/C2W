package net.klaaswhite.c2w.domain.events;

/**
 * Fired after the game world has been created and before any structures
 * are placed. Listeners that need to pre-stage state on the world can
 * react here.
 */
public class GameWorldCreatedEvent implements C2WEvent {

    private final String gameWorldName;

    public GameWorldCreatedEvent(String gameWorldName) {
        this.gameWorldName = gameWorldName;
    }

    public String getGameWorldName() {
        return gameWorldName;
    }
}
