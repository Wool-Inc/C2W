/*
package net.klaaswhite.c2w.legacy.timers.entityApplicationTimers;

import org.bukkit.entity.Entity;

import java.time.Duration;
import java.util.function.Consumer;

public class EntityCloseToEntityApplicationTimer extends EntityApplicationTimer{

    Entity target;
    long radius;

    public EntityCloseToEntityApplicationTimer(Duration interval, Consumer<Entity> perEntityAction, Entity target, long radius) {
        super(interval, perEntityAction);
        this.target = target;
        this.radius = radius;
    }

    @Override
    protected Iterable<Entity> getEntities() {
        return target.getNearbyEntities(radius, radius, radius);
    }
}
*/
