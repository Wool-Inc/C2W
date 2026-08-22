package net.klaaswhite.c2w.integration;

import net.klaaswhite.c2w.domain.game.WoolTimer;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link WoolTimer.Scheduler} for integration tests. Stores scheduled
 * tasks without auto-running them so capture timing is fully deterministic and
 * driven by the test. The repeating task's {@link Runnable} can be invoked
 * manually via {@link #tickAll()} if timer-based (pit) capture needs exercising.
 */
public class FakeScheduler implements WoolTimer.Scheduler {

    private final List<Runnable> tasks = new ArrayList<>();

    @Override
    public Object scheduleRepeating(Runnable task, long delay, long interval) {
        tasks.add(task);
        return task;
    }

    @Override
    public void cancel(Object taskId) {
        tasks.remove(taskId);
    }

    /** Manually run every scheduled repeating task once (simulates one tick batch). */
    public void tickAll() {
        for (var task : new ArrayList<>(tasks)) {
            task.run();
        }
    }

    public int getTaskCount() {
        return tasks.size();
    }
}
