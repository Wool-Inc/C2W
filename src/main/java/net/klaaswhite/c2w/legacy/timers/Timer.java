/*
package net.klaaswhite.c2w.legacy.timers;

import java.time.Duration;
import java.util.HashSet;
import java.util.concurrent.Semaphore;

public abstract class Timer {
    static HashSet<Timer> timers = new HashSet<Timer>();

    Thread t;
    Duration interval;
    Semaphore lock;
    GeneralTimerRunnable runnable;

    public Timer(Duration interval) {
        this.interval = interval;
        timers.add(this);
    }

    public final void startTimer() {
        lock = new Semaphore(1);
        runnable = new GeneralTimerRunnable(this::onTimer, interval, lock);
        t = new Thread(runnable);
        t.start();
    }

    public final void pause() {
        if (lock.availablePermits() < 1) return;
        try {
            lock.acquire();
        } catch (InterruptedException ignored) {
        }
    }

    public final void unPause() {
        if (lock.availablePermits() > 1) return;
        lock.release();
    }

    public final void end() {
        runnable.stop();
        t.interrupt();
        timers.remove(this);
    }

    protected abstract void onTimer();
}
*/
