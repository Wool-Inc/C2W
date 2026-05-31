/*
package net.klaaswhite.c2w.legacy.timers;

import java.time.Duration;
import java.util.concurrent.Semaphore;

public class GeneralTimerRunnable implements Runnable {

    final Semaphore lock;
    Runnable action;
    Duration interval;
    boolean running;

    GeneralTimerRunnable(Runnable action, Duration interval, Semaphore lock) {
        this.action = action;
        this.interval = interval;
        this.lock = lock;
        this.running = true;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            if (lock.availablePermits() > 0)
                action.run();
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}*/
