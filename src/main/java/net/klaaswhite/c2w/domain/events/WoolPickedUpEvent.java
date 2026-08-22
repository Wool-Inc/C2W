package net.klaaswhite.c2w.domain.events;

import net.klaaswhite.c2w.domain.model.Wool;

/**
 * Fired by {@link net.klaaswhite.c2w.adapter.minecraft.Wool#pickup} when a
 * player successfully picks up a wool. Lets the scoreboard flip a wool's cube
 * from "present" (solid) to "taken" (outline) without polling.
 */
public class WoolPickedUpEvent implements C2WEvent {

    private final Wool wool;

    public WoolPickedUpEvent(Wool wool) {
        this.wool = wool;
    }

    public Wool getWool() {
        return wool;
    }
}
