package net.klaaswhite.c2w.domain.model;

import net.klaaswhite.c2w.adapter.minecraft.BossBar;
import net.klaaswhite.c2w.adapter.minecraft.BossBars;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Server;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager.Worlds;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link Wool} state machine. Zero Bukkit imports.
 * <p>
 * This demonstrates TDD-style testing of domain logic with only
 * fake implementations of the port interfaces.
 */
@DisplayName("Wool")
class WoolTest {

    private MinecraftManager createMockMc() {
        var mc = mock(MinecraftManager.class);
        var bossBars = mock(BossBars.class);
        var bossBar = mock(BossBar.class);
        var server = mock(Server.class);
        var players = mock(Players.class);
        var worlds = mock(Worlds.class);
        when(mc.bossBars()).thenReturn(bossBars);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);
        when(mc.server()).thenReturn(server);
        when(mc.players()).thenReturn(players);
        when(mc.worlds()).thenReturn(worlds);
        return mc;
    }

    private static WoolTimer newWoolTimer() {
        return new WoolTimer(new WoolTimer.Scheduler() {
            public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
            public void cancel(Object taskId) {}
        });
    }

    private Wool createWool(WoolColor color) {
        var mc = createMockMc();
        var pos = new BlockPos(0, 64, 0);
        var woolTimer = newWoolTimer();
        return new Wool(mc, woolTimer, color, pos, "game");
    }

    private ManagedPlayer createPlayer(String name) {
        var handle = mock(PlayerHandle.class);
        var uuid = UUID.randomUUID();
        when(handle.getName()).thenReturn(name);
        when(handle.getDisplayName()).thenReturn(name);
        when(handle.getUniqueId()).thenReturn(uuid);
        when(handle.getPosition()).thenReturn(new BlockPos(0, 64,0));
        return new ManagedPlayer(handle);
    }

    // --- State: initial ---

    @Test
    @DisplayName("New wool is not carried, not capped, not capping")
    void initialState() {
        var wool = createWool(WoolColor.RED);
        assertFalse(wool.isCarried());
        assertFalse(wool.isCapped());
        assertFalse(wool.isCapping());
        assertNull(wool.getCarrier());
        assertEquals(0, wool.getCappedAmount());
    }

    @Test
    @DisplayName("New wool has correct color and positions")
    void woolProperties() {
        var pos = new BlockPos(10, 20, 30);
        var mc = createMockMc();
        var woolTimer = newWoolTimer();
        var wool = new Wool(mc, woolTimer, WoolColor.BLUE, pos, "game");

        assertEquals(WoolColor.BLUE, wool.getColor());
        assertEquals(pos, wool.getSpawnPos());
        assertEquals("game", wool.getWorldName());
    }

    // --- State: pickup ---

    @Test
    @DisplayName("pickup succeeds when wool is not carried")
    void pickupSucceeds() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer("Alice");

        assertTrue(wool.pickup(player));
        assertTrue(wool.isCarried());
        assertEquals(player, wool.getCarrier());
    }

    @Test
    @DisplayName("pickup fails when wool is already carried")
    void pickupFailsWhenCarried() {
        var wool = createWool(WoolColor.RED);
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");

        assertTrue(wool.pickup(alice));
        assertFalse(wool.pickup(bob));
        assertEquals(alice, wool.getCarrier());
    }

    @Test
    @DisplayName("pickup fails when player is already carrying")
    void pickupFailsWhenPlayerCarrying() {
        var wool1 = createWool(WoolColor.RED);
        var wool2 = createWool(WoolColor.BLUE);
        var alice = createPlayer("Alice");

        assertTrue(wool1.pickup(alice));
        assertFalse(wool2.pickup(alice));
    }

    @Test
    @DisplayName("pickup broadcasts message and sets helmet")
    void pickupSideEffects() {
        var mc = createMockMc();
        var mcPlayers = mock(net.klaaswhite.c2w.adapter.minecraft.Players.class);
        var mcServer = mock(net.klaaswhite.c2w.adapter.minecraft.Server.class);
        when(mc.players()).thenReturn(mcPlayers);
        when(mc.server()).thenReturn(mcServer);

        var wool = new Wool(mc, newWoolTimer(), WoolColor.RED, new BlockPos(0, 64, 0), "game");
        var player = createPlayer("Alice");

        wool.pickup(player);

        verify(mcServer).broadcastMessage(contains("picked up"));
        verify(mcPlayers).setHelmet(eq("Alice"), any());
    }

    // --- State: dropOnDeath ---

    @Test
    @DisplayName("dropOnDeath returns wool to world")
    void dropOnDeath() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer("Alice");

        wool.pickup(player);
        assertTrue(wool.isCarried());

        wool.dropOnDeath(player);
        assertFalse(wool.isCarried());
        assertNull(wool.getCarrier());
        assertEquals(0, wool.getCappedAmount());
    }

    @Test
    @DisplayName("dropOnDeath only affects the carrier")
    void dropOnDeathOnlyCarrier() {
        var wool = createWool(WoolColor.RED);
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");

        wool.pickup(alice);
        wool.dropOnDeath(bob); // Bob is not the carrier
        assertTrue(wool.isCarried());
        assertEquals(alice, wool.getCarrier());
    }

    // --- State: capture ---

    @Test
    @DisplayName("capture succeeds when wool is carried")
    void captureSucceeds() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer("Alice");

        wool.pickup(player);
        wool.capture();

        assertTrue(wool.isCapped());
        assertFalse(wool.isCarried());
        assertNull(wool.getCarrier());
    }

    @Test
    @DisplayName("capture fails when wool is not carried")
    void captureFailsWhenNotCarried() {
        var wool = createWool(WoolColor.RED);
        wool.capture();
        assertFalse(wool.isCapped());
    }

    @Test
    @DisplayName("capture broadcasts message")
    void captureBroadcasts() {
        var mc = createMockMc();
        var mcServer = mock(net.klaaswhite.c2w.adapter.minecraft.Server.class);
        when(mc.server()).thenReturn(mcServer);

        var wool = new Wool(mc, newWoolTimer(), WoolColor.RED, new BlockPos(0, 64, 0), "game");
        var player = createPlayer("Alice");

        wool.pickup(player);
        wool.capture();

        verify(mcServer).broadcastMessage(contains("captured"));
    }

    // --- Tick: capture progress ---

    @Test
    @DisplayName("tick increases capped amount when capping")
    void tickCapping() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer("Alice");

        wool.pickup(player);
        wool.setCapping(true);
        wool.setCappingModifier(20);

        wool.tick();
        assertTrue(wool.getCappedAmount() > 0);
    }

    @Test
    @DisplayName("tick decreases capped amount when not capping")
    void tickNotCapping() {
        var wool = createWool(WoolColor.RED);
        wool.setCapping(false);

        // Set a positive amount first
        wool.setCapping(true);
        wool.setCappingModifier(100);
        wool.tick(); // Now has 100

        wool.setCapping(false);
        wool.tick(); // Should decrease by 10

        assertEquals(90, wool.getCappedAmount());
    }

    @Test
    @DisplayName("tick captures wool when amount reaches CAP_AMOUNT")
    void tickAutoCapture() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer("Alice");

        wool.pickup(player);
        wool.setCapping(true);
        wool.setCappingModifier(Wool.CAP_AMOUNT); // One tick to full

        wool.tick();
        assertTrue(wool.isCapped());
    }

    @Test
    @DisplayName("tick does nothing when wool is already capped")
    void tickNoOpWhenCapped() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer("Alice");

        wool.pickup(player);
        wool.capture();
        assertTrue(wool.isCapped());

        wool.tick();
        assertTrue(wool.isCapped());
    }

    @Test
    @DisplayName("tick progress is bounded to CAP_AMOUNT")
    void tickBounded() {
        var wool = createWool(WoolColor.RED);
        var player = createPlayer("Alice");

        wool.pickup(player);
        wool.setCapping(true);
        wool.setCappingModifier(Wool.CAP_AMOUNT + 100); // Overkill

        wool.tick();
        assertTrue(wool.isCapped());
    }

    // --- CAP_AMOUNT constant ---

    @Test
    @DisplayName("CAP_AMOUNT is 1200 (20 ticks/sec * 60 sec)")
    void capAmountValue() {
        assertEquals(1200, Wool.CAP_AMOUNT);
    }
}
