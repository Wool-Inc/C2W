package net.klaaswhite.c2w.adapter.minecraft;

import net.klaaswhite.c2w.domain.model.WoolColor;
import net.klaaswhite.c2w.domain.model.BossBarStyle;

/**
 * Boss bar operations.
 * <p>
 * Abstracts creating boss bars for tracking wool capture progress.
 */
public interface BossBars {

    /**
     * Create a boss bar with the given title, color, and style.
     *
     * @param title the boss bar title
     * @param color the boss bar color
     * @param style the boss bar style
     * @return the created boss bar handle
     */
    BossBar createBossBar(String title, WoolColor color, BossBarStyle style);
}