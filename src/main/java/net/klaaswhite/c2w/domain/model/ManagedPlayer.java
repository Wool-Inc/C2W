package net.klaaswhite.c2w.domain.model;

import net.klaaswhite.c2w.domain.model.PlayerHandle;

/**
 * A player in the C2W game. Pure domain — no Bukkit imports.
 * Wraps a {@link PlayerHandle} for interacting with the actual player.
 */
public class ManagedPlayer {

    private final PlayerHandle handle;
    private ManagedTeam team;
    private Wool carry;

    public ManagedPlayer(PlayerHandle handle) {
        this.handle = handle;
        this.carry = null;
    }

    public PlayerHandle getPlayer() {
        return handle;
    }

    public ManagedTeam getTeam() {
        return team;
    }

    public void setTeam(ManagedTeam team) {
        if (this.team != null) this.team.players.remove(this);
        this.team = team;
        if (this.team != null) this.team.players.add(this);
    }

    public void removeTeam(ManagedTeam team) {
        if (this.team == null) return;
        if (!this.team.equals(team)) return;
        this.team.players.remove(this);
        this.team = null;
    }

    public boolean tryAddCarriable(Wool carry) {
        if (this.carry != null) return false;
        this.carry = carry;
        return true;
    }

    public Wool getCarry() {
        return this.carry;
    }

    public void removeCarry() {
        this.carry = null;
    }
}
