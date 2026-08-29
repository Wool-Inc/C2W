package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import org.bukkit.World;
import org.bukkit.potion.PotionEffectType;

/**
 * Keeps the environment consistent across all loaded worlds:
 * <ul>
 *   <li>Every world except the game world is pinned to midday (noon) and clear weather.</li>
 *   <li>The game world is pinned to midnight (night) and clear weather.</li>
 *   <li>Every online player carries night vision so the darkness never gets in the way.</li>
 * </ul>
 * Worlds whose dimension type has no world clock (nether, end, and custom flat
 * void worlds on MC 26.1+) cannot be time-pinned; time setting is skipped for
 * them but weather and cycle pinning still apply.
 * Runs a repeating heartbeat on the server thread (once per second). The world
 * gamerules {@code doDaylightCycle}, {@code doWeatherCycle}, and
 * {@code doMobSpawning} are disabled so the pinned time/weather hold permanently
 * and mobs only appear through configured spawners.
 */
public class EnvironmentManager implements AutoCloseable {

    /** Time of day in ticks since dawn: 6000 = noon, 18000 = midnight. */
    public static final long NOON = 6000;
    public static final long MIDNIGHT = 18000;

    /** Effect duration long enough to behave as permanent. */
    private static final int PERMANENT_EFFECT_DURATION = Integer.MAX_VALUE;

    private final WorldManager worldManager;
    private final MinecraftManager mc;
    private final WoolTimer.Scheduler scheduler;
    private final PotionEffectType nightVisionType;
    private final Object taskId;

    public EnvironmentManager(WorldManager worldManager, MinecraftManager mc,
            WoolTimer.Scheduler scheduler, PotionEffectType nightVisionType) {
        this.worldManager = worldManager;
        this.mc = mc;
        this.scheduler = scheduler;
        this.nightVisionType = nightVisionType;
        this.taskId = scheduler.scheduleRepeating(this::enforce, 0L, 20L);
    }

    /**
     * Enforce world time/weather and player night vision. Public so tests and
     * other managers can trigger one pass directly; normally driven by the
     * repeating heartbeat scheduled in the constructor.
     */
    public void enforce() {
        String gameWorldName = worldManager.getGameWorld().getName();

        for (World world : mc.worlds().getLoadedWorlds()) {
            String worldName = world.getName();
            boolean isGameWorld = worldName.equals(gameWorldName);
            mc.worlds().setTime(worldName, isGameWorld ? MIDNIGHT : NOON);
            mc.worlds().setDoDaylightCycle(worldName, false);
            mc.worlds().setDoMobSpawning(worldName, false);
            mc.worlds().setStorm(worldName, false);
            mc.worlds().setThundering(worldName, false);
            mc.worlds().setDoWeatherCycle(worldName, false);
        }

        for (String playerName : mc.server().getOnlinePlayerNames()) {
            if (!mc.players().hasPotionEffect(playerName, nightVisionType)) {
                mc.players().addPotionEffect(playerName, nightVisionType,
                        PERMANENT_EFFECT_DURATION, 0);
            }
        }
    }

    @Override
    public void close() {
        scheduler.cancel(taskId);
    }
}