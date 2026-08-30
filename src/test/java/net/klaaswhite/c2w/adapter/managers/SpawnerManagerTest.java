package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class SpawnerManagerTest {

    private EventManager eventManager;
    private MinecraftManager mc;
    private MinecraftManager.Worlds worlds;
    private World world;
    private TestScheduler scheduler;
    private CreatureSpawner spawner;
    private LivingEntity mob;

    @BeforeEach
    void setUp() {
        eventManager = new EventManager(new net.klaaswhite.c2w.bootstrap.Managers(
                mock(org.bukkit.plugin.java.JavaPlugin.class)));
        mc = mock(MinecraftManager.class);
        worlds = mock(MinecraftManager.Worlds.class);
        world = mock(World.class);
        scheduler = new TestScheduler();
        spawner = mock(CreatureSpawner.class);
        mob = mock(LivingEntity.class);

        when(mc.worlds()).thenReturn(worlds);
        when(worlds.getLoadedWorlds()).thenReturn(List.of(world));
        when(world.getName()).thenReturn("c2w_game");
        when(spawner.getWorld()).thenReturn(world);
        when(spawner.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void removesTrackedMobsOnlyWhenNoPlayerIsNearby() {
        SpawnerManager manager = new SpawnerManager(eventManager, mc, scheduler);
        SpawnerSpawnEvent spawn = mock(SpawnerSpawnEvent.class);
        when(spawn.getSpawner()).thenReturn(spawner);
        when(spawn.getEntity()).thenReturn(mob);

        Player nearbyPlayer = mock(Player.class);
        when(nearbyPlayer.getLocation()).thenReturn(new Location(world, 29, 64, 0));
        when(world.getPlayers()).thenReturn(List.of(nearbyPlayer));
        eventManager.pushMinecraftEvent(spawn);

        scheduler.tick();
        verify(mob, never()).remove();

        when(world.getPlayers()).thenReturn(List.of());
        scheduler.tick();
        verify(mob).remove();

        manager.close();
    }

    private static final class TestScheduler implements WoolTimer.Scheduler {
        private Runnable task;

        @Override
        public Object scheduleRepeating(Runnable task, long delay, long interval) {
            this.task = task;
            return task;
        }

        @Override
        public void cancel(Object taskId) {
            if (taskId == task) task = null;
        }

        private void tick() {
            if (task != null) task.run();
        }
    }
}