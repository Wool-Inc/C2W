package net.klaaswhite.c2w.domain.events;

/**
 * Fired after {@code /c2w init} has created the {@code c2w_draft} world.
 * Listeners can use this to reset player/team state and prepare for
 * team selection.
 */
public class DraftCreatedEvent implements C2WEvent {

    private final String draftWorldName;

    public DraftCreatedEvent(String draftWorldName) {
        this.draftWorldName = draftWorldName;
    }

    public String getDraftWorldName() {
        return draftWorldName;
    }
}
