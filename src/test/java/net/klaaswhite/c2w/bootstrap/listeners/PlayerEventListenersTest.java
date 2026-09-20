package net.klaaswhite.c2w.bootstrap.listeners;

import net.klaaswhite.c2w.adapter.managers.EventManager;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;import org.bukkit.event.player.PlayerQuitEvent;import org.junit.jupiter.api.BeforeEach;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerEventListeners")
class PlayerEventListenersTest {

    @Mock private EventManager eventManager;

    private PlayerEventListeners listeners;

    @BeforeEach
    void setUp() {
        listeners = new PlayerEventListeners(eventManager);
    }

    @Test
    @DisplayName("onPlayerJoin pushes the event")
    void joinPushesEvent(@Mock PlayerJoinEvent event) {
        listeners.onPlayerJoin(event);
        verify(eventManager).pushMinecraftEvent(event);
    }

    @Test
    @DisplayName("onPlayerChangedWorld pushes the event")
    void changedWorldPushesEvent(@Mock PlayerChangedWorldEvent event) {
        listeners.onPlayerChangedWorld(event);
        verify(eventManager).pushMinecraftEvent(event);
    }

    @Test
    @DisplayName("onPlayerDeath pushes the event")
    void deathPushesEvent(@Mock PlayerDeathEvent event) {
        listeners.onPlayerDeath(event);
        verify(eventManager).pushMinecraftEvent(event);
    }

    @Test
    @DisplayName("onPlayerMove pushes the event")
    void movePushesEvent(@Mock PlayerMoveEvent event) {
        listeners.onPlayerMove(event);
        verify(eventManager).pushMinecraftEvent(event);
    }

    @Test
    @DisplayName("onPlayerQuit pushes the event")
    void quitPushesEvent(@Mock PlayerQuitEvent event) {
        listeners.onPlayerQuit(event);
        verify(eventManager).pushMinecraftEvent(event);
    }

    @Test
    void blocksVanillaPlayerTeamCommand(@Mock PlayerCommandPreprocessEvent event, @Mock Player player) {
        when(event.getMessage()).thenReturn("/minecraft:team join Red Alice");
        when(event.getPlayer()).thenReturn(player);

        listeners.onPlayerCommandPreprocess(event);

        verify(event).setCancelled(true);
        verify(player).sendMessage("Use /c2w team <player> <team>.");
        verifyNoInteractions(eventManager);
    }

    @Test
    void blocksVanillaServerTeamCommand(@Mock ServerCommandEvent event, @Mock CommandSender sender) {
        when(event.getCommand()).thenReturn("team join Red Alice");
        when(event.getSender()).thenReturn(sender);

        listeners.onServerCommand(event);

        verify(event).setCancelled(true);
        verify(sender).sendMessage("Use /c2w team <player> <team>.");
        verifyNoInteractions(eventManager);
    }

    @Test
    void allowsC2wTeamCommand() {
        assertFalse(PlayerEventListeners.isVanillaTeamCommand("/c2w team Alice Red"));
    }

    @Test
    void recognizesNamespacedTeamCommand() {
        assertTrue(PlayerEventListeners.isVanillaTeamCommand("minecraft:team list"));
    }
}
