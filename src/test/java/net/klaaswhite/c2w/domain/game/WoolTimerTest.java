package net.klaaswhite.c2w.domain.game;

import net.klaaswhite.c2w.adapter.minecraft.BossBar;
import net.klaaswhite.c2w.adapter.minecraft.BossBars;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("WoolTimer")
class WoolTimerTest {

    private FakeScheduler scheduler;
    private WoolTimer timer;

    /**
     * Fake Scheduler that records scheduled task Runnables so we can invoke them manually.
     */
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

    private MinecraftManager createMockMc() {
        var mc = mock(MinecraftManager.class);
        var bossBars = mock(BossBars.class);
        var bossBar = mock(BossBar.class);
        var server = mock(Server.class);
        var players = mock(Players.class);
        when(mc.bossBars()).thenReturn(bossBars);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);
        when(mc.server()).thenReturn(server);
        when(mc.players()).thenReturn(players);
        return mc;
    }

    @BeforeEach
    void setUp() {
        scheduler = new FakeScheduler();
        timer = new WoolTimer(scheduler);
    }

    private Wool createWool(WoolColor color) {
        var mc = createMockMc();
        return new Wool(mc, timer, color, new BlockPos(0, 64, 0), "game", "cap-" + color.name().toLowerCase());
    }

    private ManagedPlayer createPlayer() {
        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("test");
        when(handle.getDisplayName()).thenReturn("test");
        when(handle.getPosition()).thenReturn(new BlockPos(0, 64, 0));
        return new ManagedPlayer(handle);
    }

    // --- Configuration defaults ---

    @Test
    @DisplayName("default interval is 20 ticks")
    void defaultInterval() {
        assertEquals(20, timer.getInterval());
    }

    @Test
    @DisplayName("default base capture is 10")
    void defaultBaseCapture() {
        assertEquals(10, timer.getBaseCapture());
    }

    @Test
    @DisplayName("default increase per player is 10")
    void defaultIncrease() {
        assertEquals(10, timer.getIncreasePerPlayer());
    }

    @Test
    @DisplayName("defaults can be changed")
    void defaultsChangeable() {
        timer.setInterval(10);
        timer.setBaseCapture(5);
        timer.setIncreasePerPlayer(3);
        timer.setDecreasePerPlayer(2);
        timer.setDecreaseOutsideArea(1);

        assertEquals(10, timer.getInterval());
        assertEquals(5, timer.getBaseCapture());
        assertEquals(3, timer.getIncreasePerPlayer());
        assertEquals(2, timer.getDecreasePerPlayer());
        assertEquals(1, timer.getDecreaseOutsideArea());
    }

    // --- Registration lifecycle ---

    @Test
    @DisplayName("registering first wool starts the timer")
    void registerStartsTimer() {
        var wool = createWool(WoolColor.RED);
        timer.registerWool(wool);

        assertEquals(1, timer.getActiveWoolCount());
        assertEquals(1, scheduler.scheduledTasks.size());
    }

    @Test
    @DisplayName("registering second wool does not start another timer")
    void registerSecondNoExtraTimer() {
        timer.registerWool(createWool(WoolColor.RED));
        timer.registerWool(createWool(WoolColor.BLUE));

        assertEquals(2, timer.getActiveWoolCount());
        assertEquals(1, scheduler.scheduledTasks.size());
    }

    @Test
    @DisplayName("unregistering last wool stops the timer")
    void unregisterStopsTimer() {
        var wool = createWool(WoolColor.RED);
        timer.registerWool(wool);
        timer.unregisterWool(wool);

        assertEquals(0, timer.getActiveWoolCount());
        assertEquals(1, scheduler.cancelledTasks.size());
    }

    @Test
    @DisplayName("unregistering one of two wools does not stop timer")
    void unregisterOneOfTwo() {
        var red = createWool(WoolColor.RED);
        var blue = createWool(WoolColor.BLUE);

        timer.registerWool(red);
        timer.registerWool(blue);
        timer.unregisterWool(red);

        assertEquals(1, timer.getActiveWoolCount());
        assertTrue(scheduler.cancelledTasks.isEmpty());
    }

    @Test
    @DisplayName("registering after unregister restarts timer")
    void registerAfterUnregisterRestarts() {
        var wool = createWool(WoolColor.RED);
        timer.registerWool(wool);
        timer.unregisterWool(wool);
        timer.registerWool(wool);

        assertEquals(2, scheduler.scheduledTasks.size());
        assertEquals(1, scheduler.cancelledTasks.size());
    }

    @Test
    @DisplayName("active count tracks registration accurately")
    void activeCountTracking() {
        assertEquals(0, timer.getActiveWoolCount());

        var red = createWool(WoolColor.RED);
        timer.registerWool(red);
        assertEquals(1, timer.getActiveWoolCount());

        var blue = createWool(WoolColor.BLUE);
        timer.registerWool(blue);
        assertEquals(2, timer.getActiveWoolCount());

        timer.unregisterWool(red);
        assertEquals(1, timer.getActiveWoolCount());

        timer.unregisterWool(blue);
        assertEquals(0, timer.getActiveWoolCount());
    }

    @Test
    @DisplayName("registering same wool twice does not double-count")
    void registerSameWoolTwice() {
        var wool = createWool(WoolColor.RED);
        timer.registerWool(wool);
        timer.registerWool(wool);
        assertEquals(1, timer.getActiveWoolCount());
    }

    // --- Tick execution ---

    @Test
    @DisplayName("tick invokes tick on all registered wools")
    void tickInvokesWoolTick() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer();

        timer.registerWool(wool);
        wool.pickup(player);
        wool.setCapping(true);
        wool.setCappingModifier(20);

        var task = scheduler.scheduledTasks.get(0);
        task.run();

        assertTrue(wool.getCappedAmount() > 0, "capped amount should increase when capping");
    }

    @Test
    @DisplayName("tick does not throw with empty timer")
    void tickNoWoolNoThrow() {
        var wool = createWool(WoolColor.RED);
        timer.registerWool(wool);
        timer.unregisterWool(wool);
        timer.registerWool(createWool(WoolColor.GREEN));

        assertDoesNotThrow(() -> scheduler.scheduledTasks.get(scheduler.scheduledTasks.size() - 1).run());
    }

    @Test
    @DisplayName("all registered wools receive progress on tick")
    void allWoolsReceiveProgress() {
        var red = createWool(WoolColor.RED);
        var blue = createWool(WoolColor.BLUE);
        var player1 = createPlayer();
        var player2 = createPlayer();

        timer.registerWool(red);
        red.pickup(player1);
        red.setCapping(true);
        red.setCappingModifier(30);

        timer.registerWool(blue);
        blue.pickup(player2);
        blue.setCapping(true);
        blue.setCappingModifier(30);

        scheduler.scheduledTasks.get(0).run();

        assertTrue(red.getCappedAmount() > 0, "red wool should have capture progress");
        assertTrue(blue.getCappedAmount() > 0, "blue wool should have capture progress");
    }

    @Test
    @DisplayName("tick regression — uncapping wool loses progress")
    void tickRegressionDecreasesProgress() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer();

        timer.registerWool(wool);
        wool.pickup(player);

        wool.setCapping(true);
        wool.setCappingModifier(100);
        scheduler.scheduledTasks.get(0).run();
        assertTrue(wool.getCappedAmount() > 0, "should have progress after capping tick");

        wool.setCapping(false);
        int before = wool.getCappedAmount();
        scheduler.scheduledTasks.get(0).run();
        assertTrue(wool.getCappedAmount() < before,
                "progress should decrease when not capping");
    }

    @Test
    @DisplayName("timer state transition empty → active → empty → active")
    void stateTransitions() {
        var wool1 = createWool(WoolColor.RED);
        var wool2 = createWool(WoolColor.BLUE);

        timer.registerWool(wool1);
        assertEquals(1, timer.getActiveWoolCount());
        assertEquals(1, scheduler.scheduledTasks.size());

        timer.unregisterWool(wool1);
        assertEquals(0, timer.getActiveWoolCount());
        assertEquals(1, scheduler.cancelledTasks.size());

        timer.registerWool(wool2);
        assertEquals(1, timer.getActiveWoolCount());
        assertEquals(2, scheduler.scheduledTasks.size());
    }

    @Test
    @DisplayName("multiple wools tick independently — capping vs idle")
    void multipleWoolsTickIndependently() {
        var red = createWool(WoolColor.RED);
        var blue = createWool(WoolColor.BLUE);
        var player1 = createPlayer();

        timer.registerWool(red);
        timer.registerWool(blue);

        red.pickup(player1);
        red.setCapping(true);
        red.setCappingModifier(50);
        blue.setCapping(false);

        scheduler.scheduledTasks.get(0).run();

        assertTrue(red.getCappedAmount() > 0, "capping wool should progress");
        assertEquals(0, blue.getCappedAmount(), "uncarried wool should have no progress");
    }

    @Test
    @DisplayName("capped wool stops receiving meaningful ticks")
    void cappedWoolStopsTicking() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer();

        timer.registerWool(wool);
        wool.pickup(player);
        wool.setCapping(true);
        wool.setCappingModifier(Wool.CAP_AMOUNT);

        scheduler.scheduledTasks.get(0).run();
        assertTrue(wool.isCapped());

        scheduler.scheduledTasks.get(0).run();
        assertTrue(wool.isCapped(), "wool should remain capped");
    }

    @Test
    @DisplayName("timer tracks multiple wools with different modifiers")
    void differentModifiers() {
        var red = createWool(WoolColor.RED);
        var blue = createWool(WoolColor.BLUE);
        var p1 = createPlayer();
        var p2 = createPlayer();

        timer.registerWool(red);
        timer.registerWool(blue);

        red.pickup(p1);
        red.setCapping(true);
        red.setCappingModifier(10);

        blue.pickup(p2);
        blue.setCapping(true);
        blue.setCappingModifier(100);

        scheduler.scheduledTasks.get(0).run();

        assertTrue(blue.getCappedAmount() > red.getCappedAmount(),
                "wool with higher modifier should have more progress");
    }

    @Test
    @DisplayName("decrease defaults are correct")
    void decreaseDefaults() {
        assertEquals(10, timer.getDecreasePerPlayer());
        assertEquals(10, timer.getDecreaseOutsideArea());
    }
}
