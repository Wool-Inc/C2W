package net.klaaswhite.c2w.classes;

import net.klaaswhite.c2w.interfaces.ICarriable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;


public class ManagedPlayer {
    private Player player;
    private ManagedTeam team;

    private ICarriable carry;

    public ManagedPlayer(Player player){
        this.player = player;
        this.carry = null;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
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

    public void ensureInMcTeam(){
        if (this.team == null) return;
        var scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
        var mcTeam = scoreboard.getTeam(this.team.teamName);
        if (mcTeam == null) return;
        mcTeam.addEntry(this.player.getName());
    }

    public boolean tryAddCarriable(ICarriable carry){
        if (this.carry != null)
            return false;

        this.carry = carry;
        return true;
    }

    public ICarriable getCarry(){
        return this.carry;
    }

    public void removeCarry(){
        this.carry = null;
    }
}