package net.klaaswhite.c2w.classes;

import net.klaaswhite.c2w.managers.GameManager;
import net.klaaswhite.c2w.managers.Managers;
import net.minecraft.util.parsing.packrat.Atom;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WoolTimer {
    @Nullable
    private static BukkitTask timer;

    private static ConcurrentHashMap<Wool, Wool> wools;
    public static AtomicInteger interval = new AtomicInteger(20);
    public static AtomicInteger baseCapture = new AtomicInteger(10);
    public static AtomicInteger increasePerPlayer = new AtomicInteger(10);
    public static AtomicInteger decreasePerPlayer = new AtomicInteger(10);
    public static AtomicInteger decreaseOutsideArea = new AtomicInteger(10);

    public static void registerWoolForTiming(Managers manager, Wool wool){
        if (timer == null){
            timer = createTimer(manager);
        }

        wools.put(wool, wool);
    }

    public static void unregisterWoolForTiming(Wool wool){

        if (wools == null)
            return;

        wools.remove(wool);

        if (timer instanceof BukkitTask localTimer && wools.isEmpty()){
            localTimer.cancel();
            wools = null;
            timer = null;
        }
    }

    private static BukkitTask createTimer(Managers managers){
        wools = new ConcurrentHashMap<>();

        return Bukkit.getScheduler().runTaskTimer(
                managers.getPlugin(),
                WoolTimer::tick,
                0,
                interval.get()
                );
    }

    private static void tick(){
        var wools = WoolTimer.wools.keySet();
        for (var wool : wools){
            wool.tick();
        }
    }

    public static void setInterval(int interval){
        WoolTimer.interval.set(interval);
    }

    public static void setBaseCapture(int baseCapture){
        WoolTimer.baseCapture.set(baseCapture);
    }

    public static void setIncreasePerPlayer(int increasePerPlayer){
        WoolTimer.increasePerPlayer.set(increasePerPlayer);
    }

    public static void setDecreasePerPlayer(int decreasePerPlayer){
        WoolTimer.decreasePerPlayer.set(decreasePerPlayer);
    }

    public static void setDecreaseOutsideArea(int decreaseOutsideArea){
        WoolTimer.decreaseOutsideArea.set(decreaseOutsideArea);
    }
}
