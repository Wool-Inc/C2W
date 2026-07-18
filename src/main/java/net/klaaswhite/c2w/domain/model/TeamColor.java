package net.klaaswhite.c2w.domain.model;

/**
 * Team color enumeration. Replaces Bukkit {@code ChatColor} in the domain layer
 * so that domain classes have no Bukkit dependency.
 */
public enum TeamColor {
    RED("Red"),
    BLUE("Blue"),
    GRAY("Gray");

    private final String displayName;

    TeamColor(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
