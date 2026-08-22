package net.klaaswhite.c2w.domain.model;

import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ItemStackRef;

import java.util.UUID;

/**
 * Narrow abstraction over a player. Zero Bukkit imports.
 * <p>
 * The domain layer uses this interface to interact with players without
 * depending on Bukkit. The Bukkit adapter translates between this and the
 * real Bukkit {@code Player}.
 */
public interface PlayerHandle {

    UUID getUniqueId();

    String getName();

    String getDisplayName();

    void sendMessage(String message);

    /** Teleport to a position in a named world. */
    void teleport(BlockPos pos, String worldName);

    /** Get the name of the world the player is currently in. */
    String getWorldName();

    /** Get the player's current position. */
    BlockPos getPosition();

    void setHelmet(ItemStackRef item);

    /**
     * Get the block the player is looking at (up to maxDistance).
     * Returns null if no block is targeted.
     */
    BlockPos getTargetBlock(int maxDistance);

    /**
     * Return the underlying Bukkit {@code Player}, or {@code null} in tests.
     * Used only at the Bukkit boundary (e.g. {@code BossBar.addPlayer}).
     */
    Object getBukkitPlayer();
}