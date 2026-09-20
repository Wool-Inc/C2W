package net.klaaswhite.c2w.bootstrap.listeners;

import net.klaaswhite.c2w.adapter.managers.EventManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerCommandEvent;

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
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (isVanillaTeamCommand(event.getMessage())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Use /c2w team <player> <team>.");
            return;
        }
        this.eventManager.pushMinecraftEvent(event);
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        if (isVanillaTeamCommand(event.getCommand())) {
            event.setCancelled(true);
            event.getSender().sendMessage("Use /c2w team <player> <team>.");
            return;
        }
        this.eventManager.pushMinecraftEvent(event);
    }

    static boolean isVanillaTeamCommand(String rawCommand) {
        if (rawCommand == null) return false;
        String command = rawCommand.trim();
        if (command.startsWith("/")) command = command.substring(1).trim();
        if (command.isEmpty()) return false;
        String label = command.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        int namespaceSeparator = label.lastIndexOf(':');
        if (namespaceSeparator >= 0) label = label.substring(namespaceSeparator + 1);
        return "team".equals(label);
    }

}
