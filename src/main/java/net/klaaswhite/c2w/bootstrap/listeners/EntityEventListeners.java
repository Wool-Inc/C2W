package net.klaaswhite.c2w.bootstrap.listeners;

import net.klaaswhite.c2w.adapter.managers.EventManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;

public class EntityEventListeners implements Listener {
    private final EventManager eventManager;

    public EntityEventListeners(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        var causing = event.getDamageSource().getCausingEntity();
        if (causing != null && !(causing instanceof Player))
            event.getDrops().clear();

        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onEntitySpawn(SpawnerSpawnEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onTrialSpawnerSpawn(TrialSpawnerSpawnEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }
}
