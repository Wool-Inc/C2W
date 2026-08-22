package net.klaaswhite.c2w.bootstrap.minecraft;

import net.klaaswhite.c2w.domain.game.WoolTimer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bukkit implementation of {@link WoolTimer.Scheduler}.
 * Wraps the Bukkit scheduler to run repeating tasks on the server thread.
 */
public class BukkitWoolTimerScheduler implements WoolTimer.Scheduler {

    private final JavaPlugin plugin;

    public BukkitWoolTimerScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Object scheduleRepeating(Runnable task, long delay, long interval) {
        BukkitTask bukkitTask = Bukkit.getScheduler()
                .runTaskTimer(plugin, task, delay, interval);
        return bukkitTask.getTaskId();
    }

    @Override
    public void cancel(Object taskId) {
        if (taskId instanceof Integer id) {
            Bukkit.getScheduler().cancelTask(id);
        }
    }
}