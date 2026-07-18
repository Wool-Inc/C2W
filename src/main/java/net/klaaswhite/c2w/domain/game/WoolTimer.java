package net.klaaswhite.c2w.domain.game;

import net.klaaswhite.c2w.domain.model.Wool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Timer that ticks all active wools for capture progress.
 * <p>
 * This is a domain class with zero Bukkit imports. The scheduling
 * mechanism is abstracted through the {@link Scheduler} interface.
 * Implements {@link AutoCloseable} so the repeating task is always cancelled
 * on plugin reset/shutdown (previously the task leaked across {@code /c2w reset}).
 */
public class WoolTimer implements AutoCloseable {

    public interface Scheduler {
        Object scheduleRepeating(Runnable task, long delay, long interval);
        void cancel(Object taskId);
    }

    private final Scheduler scheduler;
    private final ConcurrentHashMap<Wool, Wool> wools = new ConcurrentHashMap<>();
    private final AtomicReference<Object> taskId = new AtomicReference<>();

    private int interval = 20;
    private int baseCapture = 10;
    private int increasePerPlayer = 10;
    private int decreasePerPlayer = 10;
    private int decreaseOutsideArea = 10;

    public WoolTimer(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void registerWool(Wool wool) {
        boolean wasEmpty = wools.isEmpty();
        wools.put(wool, wool);
        if (wasEmpty) {
            startTimer();
        }
    }

    public void unregisterWool(Wool wool) {
        wools.remove(wool);
        if (wools.isEmpty()) {
            stopTimer();
        }
    }

    /** Unregister every wool and stop the scheduler task. */
    public void clear() {
        wools.clear();
        stopTimer();
    }

    public int getActiveWoolCount() { return wools.size(); }
    public int getInterval() { return interval; }
    public int getBaseCapture() { return baseCapture; }
    public int getIncreasePerPlayer() { return increasePerPlayer; }
    public int getDecreasePerPlayer() { return decreasePerPlayer; }
    public int getDecreaseOutsideArea() { return decreaseOutsideArea; }

    public void setInterval(int interval) { this.interval = interval; }
    public void setBaseCapture(int baseCapture) { this.baseCapture = baseCapture; }
    public void setIncreasePerPlayer(int increasePerPlayer) { this.increasePerPlayer = increasePerPlayer; }
    public void setDecreasePerPlayer(int decreasePerPlayer) { this.decreasePerPlayer = decreasePerPlayer; }
    public void setDecreaseOutsideArea(int decreaseOutsideArea) { this.decreaseOutsideArea = decreaseOutsideArea; }

    private void startTimer() {
        taskId.set(scheduler.scheduleRepeating(this::tick, 0, interval));
    }

    private void stopTimer() {
        Object id = taskId.getAndSet(null);
        if (id != null) {
            scheduler.cancel(id);
        }
    }

    private void tick() {
        for (var wool : wools.keySet()) {
            wool.tick();
        }
    }

    @Override
    public void close() {
        clear();
    }
}
