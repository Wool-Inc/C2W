package net.klaaswhite.c2w.bootstrap.listeners;

import net.klaaswhite.c2w.adapter.managers.EventManager;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

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
}
