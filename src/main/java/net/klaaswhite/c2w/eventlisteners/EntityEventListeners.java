package net.klaaswhite.c2w.eventlisteners;

import net.klaaswhite.c2w.managers.EventManager;
import net.klaaswhite.c2w.managers.Managers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

public class EntityEventListeners implements Listener {
    Managers managers;
    EventManager eventManager;


    public EntityEventListeners(Managers managers, EventManager eventManager){
        this.managers = managers;
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getDamageSource().getCausingEntity() instanceof Player killingPlayer))
            event.getDrops().clear();

        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onEntitySpawn(SpawnerSpawnEvent event){
        this.eventManager.pushMinecraftEvent(event);
    }
}
