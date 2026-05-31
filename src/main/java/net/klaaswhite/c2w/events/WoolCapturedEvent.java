package net.klaaswhite.c2w.events;

import net.klaaswhite.c2w.classes.ManagedPlayer;
import net.klaaswhite.c2w.classes.Wool;

public class WoolCapturedEvent implements IC2WEvent{

    private final ManagedPlayer player;
    private final Wool wool;

    public WoolCapturedEvent(ManagedPlayer player, Wool wool){
        this.player = player;
        this.wool = wool;
    }

    public ManagedPlayer getPlayer(){
        return this.player;
    }

    public Wool getWool(){
        return this.wool;
    }
}
