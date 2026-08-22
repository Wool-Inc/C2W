package net.klaaswhite.c2w.adapter.minecraft;

import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.Nullable;

/**
 * Scoreboard and team operations.
 * <p>
 * Abstracts the Bukkit scoreboard system for creating and managing teams,
 * adding and removing players, and querying the main scoreboard.
 */
public interface Scoreboards {

    /** Create a new team with the given name. Returns the created team. */
    Team createTeam(String teamName);

    /**
     * Get an existing team by name.
     *
     * @return the team, or {@code null} if it does not exist
     */
    @Nullable Team getTeam(String teamName);

    /** Remove a team by name. Returns true if successful. */
    boolean removeTeam(String teamName);

    /** Add a player to a team. */
    void addPlayerToTeam(String playerName, String teamName);

    /** Remove a player from a team. */
    void removePlayerFromTeam(String playerName, String teamName);

    /** Get the main server scoreboard. */
    Scoreboard getMainScoreboard();

    /**
     * Register (or fetch) a sidebar objective with the given name and display.
     * Returns the objective so callers can set scores.
     */
    Objective registerSidebarObjective(String name, String displayName);

    /** Remove a sidebar objective by name, if present. */
    void unregisterObjective(String name);
}