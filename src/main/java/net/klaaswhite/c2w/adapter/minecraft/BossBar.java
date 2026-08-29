package net.klaaswhite.c2w.adapter.minecraft;

/**
 * Boss bar handle for the adapter layer.
 * Abstracts boss bar visibility and progress for use by adapter classes.
 */
public interface BossBar {

    /** Set whether the boss bar is visible to players. */
    void setVisible(boolean visible);

    /** Set the boss bar progress (0.0 to 1.0). */
    void setProgress(double progress);

    /** Add an online player to see this boss bar. */
    void addPlayer(String playerName);
}