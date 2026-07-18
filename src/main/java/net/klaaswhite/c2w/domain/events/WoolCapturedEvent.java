package net.klaaswhite.c2w.domain.events;

import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.Wool;

public class WoolCapturedEvent implements C2WEvent {

    private final ManagedPlayer player;
    private final Wool wool;

    public WoolCapturedEvent(ManagedPlayer player, Wool wool) {
        this.player = player;
        this.wool = wool;
    }

    public ManagedPlayer getPlayer() {
        return this.player;
    }

    public Wool getWool() {
        return this.wool;
    }
}
