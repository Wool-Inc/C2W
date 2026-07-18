package net.klaaswhite.c2w.bootstrap.listeners;

import net.klaaswhite.c2w.adapter.managers.EventManager;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntityEventListeners")
class EntityEventListenersTest {

    @Mock private EventManager eventManager;

    private EntityEventListeners listeners;

    @BeforeEach
    void setUp() {
        listeners = new EntityEventListeners(eventManager);
    }

    private EntityDeathEvent deathEventWithCausing(Entity causing) {
        var event = mock(EntityDeathEvent.class);
        var source = mock(DamageSource.class);
        when(event.getDamageSource()).thenReturn(source);
        when(source.getCausingEntity()).thenReturn(causing);
        var drops = new ArrayList<ItemStack>();
        drops.add(mock(ItemStack.class));
        when(event.getDrops()).thenReturn(drops);
        return event;
    }

    @Test
    @DisplayName("onEntityDeath clears drops when killed by a non-player mob")
    void deathByMobClearsDrops() {
        var mob = mock(Entity.class); // not a Player
        var event = deathEventWithCausing(mob);

        listeners.onEntityDeath(event);

        assertTrue(event.getDrops().isEmpty());
        verify(eventManager).pushMinecraftEvent(event);
    }

    @Test
    @DisplayName("onEntityDeath keeps drops when killed by a player")
    void deathByPlayerKeepsDrops() {
        var player = mock(Player.class);
        var event = deathEventWithCausing(player);

        listeners.onEntityDeath(event);

        assertFalse(event.getDrops().isEmpty());
        verify(eventManager).pushMinecraftEvent(event);
    }

    @Test
    @DisplayName("onEntityDeath keeps drops when there is no causing entity")
    void deathNoCausingKeepsDrops() {
        var event = deathEventWithCausing(null);

        listeners.onEntityDeath(event);

        assertFalse(event.getDrops().isEmpty());
        verify(eventManager).pushMinecraftEvent(event);
    }

    @Test
    @DisplayName("onEntityPickupItem pushes the event")
    void pickupPushesEvent(@Mock EntityPickupItemEvent event) {
        listeners.onEntityPickupItem(event);
        verify(eventManager).pushMinecraftEvent(event);
    }

    @Test
    @DisplayName("onEntitySpawn pushes the event")
    void spawnPushesEvent(@Mock SpawnerSpawnEvent event) {
        listeners.onEntitySpawn(event);
        verify(eventManager).pushMinecraftEvent(event);
    }
}
