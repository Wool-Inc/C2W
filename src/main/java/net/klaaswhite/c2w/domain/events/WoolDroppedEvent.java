package net.klaaswhite.c2w.domain.events;

import net.klaaswhite.c2w.domain.model.Wool;

public class WoolDroppedEvent implements C2WEvent {
    private final Wool wool;

    public WoolDroppedEvent(Wool wool) {
        this.wool = wool;
    }

    public Wool getWool() {
        return wool;
    }
}
