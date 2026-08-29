package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("EnvironmentManager")
class EnvironmentManagerTest {

    private FakeScheduler scheduler;
    private WorldManager worldManager;
    private MinecraftManager mc;
    private MinecraftManager.Worlds worlds;
    private Players players;
    private Server server;

    /** Fake scheduler that records heartbeat tasks so tests can invoke them manually. */
    static class FakeScheduler implements WoolTimer.Scheduler {
        final List<Runnable> scheduledTasks = new ArrayList<>();
        final List<Object> cancelledTasks = new ArrayList<>();
        private int nextId = 1;

        @Override
        public Object scheduleRepeating(Runnable task, long delay, long interval) {
            scheduledTasks.add(task);
            return nextId++;
        }

        @Override
        public void cancel(Object taskId) {
            cancelledTasks.add(taskId);
        }
    }

    @BeforeEach
    void setUp() {
        scheduler = new FakeScheduler();
        worldManager = mock(WorldManager.class);
        var gameManagedWorld = mock(ManagedWorld.class);
        when(gameManagedWorld.getName()).thenReturn("c2w_game");
        when(worldManager.getGameWorld()).thenReturn(gameManagedWorld);

        mc = mock(MinecraftManager.class);
        worlds = mock(MinecraftManager.Worlds.class);
        players = mock(Players.class);
        server = mock(Server.class);
        when(mc.worlds()).thenReturn(worlds);
        when(mc.players()).thenReturn(players);
        when(mc.server()).thenReturn(server);

        World lobby = mock(World.class);
        when(lobby.getName()).thenReturn("c2w_lobby");
        World gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("c2w_game");
        when(worlds.getLoadedWorlds()).thenReturn(List.of(lobby, gameWorld));

        when(server.getOnlinePlayerNames()).thenReturn(List.of("Alice", "Bob"));
        when(players.hasPotionEffect(anyString(), any())).thenReturn(false);
    }

    /** The manager threads the night-vision type through untouched, so {@code null}
     *  suffices here — headless JVMs cannot initialize {@code PotionEffectType}. */
    private EnvironmentManager newManager() {
        return new EnvironmentManager(worldManager, mc, scheduler, null);
    }

    @Test
    @DisplayName("schedules a heartbeat task on construction and cancels it on close")
    void heartbeatLifecycle() {
        var manager = newManager();
        assertEquals(1, scheduler.scheduledTasks.size());
        manager.close();
        assertEquals(List.of(1), scheduler.cancelledTasks);
    }

    @Test
    @DisplayName("every non-game world is pinned to noon with clear weather")
    void noonForNonGameWorlds() {
        var manager = newManager();
        scheduler.scheduledTasks.get(0).run();

        verify(worlds).setTime("c2w_lobby", EnvironmentManager.NOON);
        verify(worlds).setDoDaylightCycle("c2w_lobby", false);
        verify(worlds).setDoMobSpawning("c2w_lobby", false);
        verify(worlds).setStorm("c2w_lobby", false);
        verify(worlds).setThundering("c2w_lobby", false);
        verify(worlds).setDoWeatherCycle("c2w_lobby", false);
        manager.close();
    }

    @Test
    @DisplayName("the game world is pinned to midnight with clear weather")
    void midnightForGameWorld() {
        var manager = newManager();
        scheduler.scheduledTasks.get(0).run();

        verify(worlds).setTime("c2w_game", EnvironmentManager.MIDNIGHT);
        verify(worlds).setDoDaylightCycle("c2w_game", false);
        verify(worlds).setDoMobSpawning("c2w_game", false);
        verify(worlds).setStorm("c2w_game", false);
        verify(worlds).setThundering("c2w_game", false);
        verify(worlds).setDoWeatherCycle("c2w_game", false);
        manager.close();
    }

    @Test
    @DisplayName("players without night vision get it applied")
    void appliesNightVision() {
        var manager = newManager();
        scheduler.scheduledTasks.get(0).run();

        verify(players).addPotionEffect(eq("Alice"), any(), eq(Integer.MAX_VALUE), eq(0));
        verify(players).addPotionEffect(eq("Bob"), any(), eq(Integer.MAX_VALUE), eq(0));
        manager.close();
    }

    @Test
    @DisplayName("players that already have night vision are left alone")
    void skipsNightVisionWhenAlreadyPresent() {
        when(players.hasPotionEffect(eq("Alice"), any())).thenReturn(true);
        var manager = newManager();
        scheduler.scheduledTasks.get(0).run();

        verify(players, never()).addPotionEffect(eq("Alice"), any(), eq(Integer.MAX_VALUE), eq(0));
        verify(players).addPotionEffect(eq("Bob"), any(), eq(Integer.MAX_VALUE), eq(0));
        manager.close();
    }
}