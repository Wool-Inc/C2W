package net.klaaswhite.c2w.bootstrap.listeners;

import net.klaaswhite.c2w.adapter.managers.EventManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerEventListeners implements Listener {

    private final EventManager eventManager;

    public PlayerEventListeners(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

}
