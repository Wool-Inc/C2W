package net.klaaswhite.c2w.adapter.minecraft;

import java.util.List;

/**
 * Server-level Minecraft operations.
 * <p>
 * Provides broadcasting, online player queries, and raw access to the
 * Bukkit {@link org.bukkit.Server} instance for advanced operations.
 */
public interface Server {

    /** Broadcast a message to all online players. */
    void broadcastMessage(String message);

    /** Get the names of all currently online players. */
    List<String> getOnlinePlayerNames();

    /** Get the number of currently online players. */
    int getOnlinePlayerCount();

    /**
     * Get the raw Bukkit {@link org.bukkit.Server} instance.
     * Use this sparingly — prefer specific methods on this interface.
     */
    org.bukkit.Server bukkit();
}