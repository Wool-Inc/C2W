/*
package net.klaaswhite.c2w.legacy.timers.entityApplicationTimers;

import net.klaaswhite.c2w.legacy.timers.Timer;
import org.bukkit.entity.Entity;

import java.time.Duration;
import java.util.function.Consumer;

public abstract class EntityApplicationTimer extends Timer {

    Consumer<Entity> perEntityAction;

    public EntityApplicationTimer(Duration interval, Consumer<Entity> perEntityAction){
        super(interval);

        this.perEntityAction = perEntityAction;
    }

    protected abstract Iterable<Entity> getEntities();

    @Override
    protected void onTimer() {
        var entities = getEntities();
        for(Entity entity : entities){
            perEntityAction.accept(entity);
        }
    }
}
*/
