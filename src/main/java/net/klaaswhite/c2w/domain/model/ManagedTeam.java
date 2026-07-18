package net.klaaswhite.c2w.domain.model;

import java.util.HashSet;
import java.util.HashMap;

/**
 * A team of players. Pure domain — no Bukkit imports.
 * The Bukkit adapter syncs this with the scoreboard team.
 */
public class ManagedTeam {

    // ponytail: static mutable map — PlayerRegistry owns lifecycle via createTeam()/removeTeam()/reset().
    // Migrate to a PlayerRegistry-owned Map<String, ManagedTeam> if tests need isolation.
    public static final HashMap<String, ManagedTeam> teams = new HashMap<>();

    public final String teamName;
    public final TeamColor color;
    public final HashSet<ManagedPlayer> players = new HashSet<>();

    public ManagedTeam(String teamName, TeamColor color) {
        this.teamName = teamName;
        this.color = color;
    }

    /**
     * Equality is based on {@link #teamName} so that distinct instances representing
     * the same team compare equal. This keeps {@code Hashtable<ManagedTeam, ...>}
     * lookups correct even if a team is reconstructed from its name rather than
     * reusing the canonical instance from {@link #teams}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ManagedTeam other)) return false;
        return teamName.equals(other.teamName);
    }

    @Override
    public int hashCode() {
        return teamName.hashCode();
    }
}
