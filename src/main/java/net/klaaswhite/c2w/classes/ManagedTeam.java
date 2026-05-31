package net.klaaswhite.c2w.classes;

import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Hashtable;

public class ManagedTeam {
    public static Hashtable<String, ManagedTeam> teams = new Hashtable<>();

    public String teamName;
    public ChatColor colour;
    public HashSet<ManagedPlayer> players = new HashSet<>();
    public Team mcTeam;

    public ManagedTeam(String teamName, ChatColor colour, Scoreboard scoreboard){
        this.teamName = teamName;
        this.colour = colour;

        mcTeam = scoreboard.registerNewTeam(teamName);
        mcTeam.setColor(colour);
        mcTeam.setAllowFriendlyFire(true);
    }
}
