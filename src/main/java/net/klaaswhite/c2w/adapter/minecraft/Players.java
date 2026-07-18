package net.klaaswhite.c2w.adapter.minecraft;

import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ItemStackRef;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.Nullable;

/**
 * Player-related Minecraft operations.
 * <p>
 * Abstracts Bukkit {@link Player} interactions — teleportation, inventory,
 * potion effects, health/food, and position queries.
 */
public interface Players {

    /** Get the Bukkit {@link Player} for a given player name. */
    Player getHandle(String playerName);

    /** Get the player's current block position. */
    BlockPos getPosition(String playerName);

    /**
     * Get the block the player is looking at, up to {@code range} blocks away.
     *
     * @return the targeted block position, or {@code null} if nothing is in range
     */
    @Nullable BlockPos getTargetBlock(String playerName, int range);

    /**
     * Get the name of the world the player is currently in.
     *
     * @return the world name, or {@code null} if the player is offline
     */
    @Nullable String getWorldName(String playerName);

    /** Teleport the player to a specific position in a specific world. */
    void teleportToWorld(String playerName, BlockPos pos, String worldName);

    /** Send a chat message to the player. */
    void sendMessage(String playerName, String message);

    /** Give an item stack to the player's inventory. */
    void giveItemStack(String playerName, ItemStackRef item);

    /** Clear the player's inventory. */
    void clearInventory(String playerName);

    /** Add a potion effect to the player with the given duration (ticks) and amplifier. */
    void addPotionEffect(String playerName, PotionEffectType type, int duration, int amplifier);

    /** Remove all active potion effects from the player. */
    void removePotionEffects(String playerName);

    /** Set the player's health (0–20). */
    void setHealth(String playerName, double health);

    /** Set the player's food level (0–20). */
    void setFoodLevel(String playerName, int food);

    /** Set the player's helmet slot to the given item. */
    void setHelmet(String playerName, ItemStackRef item);

    /** Send an action bar message to the player. */
    void sendActionBar(String playerName, String message);

    /** Send a title and subtitle to the player with fade-in, stay, and fade-out ticks. */
    void sendTitle(String playerName, String title, String subtitle, int fadeIn, int stay, int fadeOut);

    /** Play a sound to the player by name at the given volume and pitch. */
    void playSound(String playerName, String soundName, float volume, float pitch);

    /**
     * Spawn colored dust particles at a position in a world.
     *
     * @param playerName   the player to send particles to (used for visibility range)
     * @param worldName    the world name
     * @param x            block x coordinate
     * @param y            block y coordinate
     * @param z            block z coordinate
     * @param particleType the particle type name (e.g. "REDSTONE")
     * @param count        number of particles
     * @param r            red component (0-255)
     * @param g            green component (0-255)
     * @param b            blue component (0-255)
     */
    void spawnParticles(String playerName, String worldName, int x, int y, int z, String particleType, int count, int r, int g, int b);
}