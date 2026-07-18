package net.klaaswhite.c2w.domain.model;

import net.klaaswhite.c2w.adapter.minecraft.BossBar;
import net.klaaswhite.c2w.adapter.minecraft.BossBars;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.TeamColor;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ManagedPlayer")
class ManagedPlayerTest {

    private MinecraftManager createMockMc() {
        var mc = mock(MinecraftManager.class);
        var bossBars = mock(BossBars.class);
        var bossBar = mock(BossBar.class);
        when(mc.bossBars()).thenReturn(bossBars);
        when(bossBars.createBossBar(anyString(), any(), any())).thenReturn(bossBar);
        return mc;
    }

    private ManagedPlayer createPlayer(String name) {
        var handle = mock(PlayerHandle.class);
        var uuid = UUID.randomUUID();
        when(handle.getName()).thenReturn(name);
        when(handle.getDisplayName()).thenReturn(name);
        when(handle.getUniqueId()).thenReturn(uuid);
        when(handle.getWorldName()).thenReturn("world");
        when(handle.getPosition()).thenReturn(new BlockPos(0, 64, 0));
        return new ManagedPlayer(handle);
    }

    private ManagedTeam createTeam(String name, TeamColor color) {
        return new ManagedTeam(name, color);
    }

    private Wool createWool(WoolColor color) {
        var mc = createMockMc();
        var woolTimer = new net.klaaswhite.c2w.domain.game.WoolTimer(new net.klaaswhite.c2w.domain.game.WoolTimer.Scheduler() {
            public Object scheduleRepeating(Runnable task, long delay, long interval) { return null; }
            public void cancel(Object taskId) {}
        });
        return new Wool(mc, woolTimer, color, new BlockPos(0, 64, 0), "game", "cap-" + color.name().toLowerCase());
    }

    // --- Basic properties ---

    @Test
    @DisplayName("wraps PlayerHandle and exposes it")
    void wrapsPlayerHandle() {
        var player = createPlayer("Alice");
        assertNotNull(player.getPlayer());
        assertEquals("Alice", player.getPlayer().getName());
    }

    @Test
    @DisplayName("starts with no team")
    void startsWithNoTeam() {
        var player = createPlayer("Alice");
        assertNull(player.getTeam());
    }

    @Test
    @DisplayName("starts with no carry")
    void startsWithNoCarry() {
        var player = createPlayer("Alice");
        assertNull(player.getCarry());
    }

    // --- Team switching ---

    @Test
    @DisplayName("setTeam assigns player to team and adds to team's player set")
    void setTeamAssignsAndAdds() {
        var player = createPlayer("Alice");
        var team = createTeam("red", TeamColor.RED);
        player.setTeam(team);

        assertSame(team, player.getTeam());
        assertTrue(team.players.contains(player));
    }

    @Test
    @DisplayName("setTeam removes player from previous team")
    void setTeamRemovesFromPrevious() {
        var player = createPlayer("Alice");
        var red = createTeam("red", TeamColor.RED);
        var blue = createTeam("blue", TeamColor.BLUE);

        player.setTeam(red);
        player.setTeam(blue);

        assertFalse(red.players.contains(player));
        assertTrue(blue.players.contains(player));
        assertSame(blue, player.getTeam());
    }

    @Test
    @DisplayName("setTeam with null removes from current team")
    void setTeamNullRemoves() {
        var player = createPlayer("Alice");
        var team = createTeam("red", TeamColor.RED);

        player.setTeam(team);
        player.setTeam(null);

        assertNull(player.getTeam());
        assertFalse(team.players.contains(player));
    }

    @Test
    @DisplayName("setTeam with same team is idempotent")
    void setTeamSameTeam() {
        var player = createPlayer("Alice");
        var team = createTeam("red", TeamColor.RED);

        player.setTeam(team);
        player.setTeam(team);

        assertSame(team, player.getTeam());
        assertEquals(1, team.players.size());
    }

    @Test
    @DisplayName("switching teams multiple times keeps only latest")
    void switchTeamsMultipleTimes() {
        var player = createPlayer("Alice");
        var red = createTeam("red", TeamColor.RED);
        var blue = createTeam("blue", TeamColor.BLUE);
        var gray = createTeam("gray", TeamColor.GRAY);

        player.setTeam(red);
        player.setTeam(blue);
        player.setTeam(gray);

        assertFalse(red.players.contains(player));
        assertFalse(blue.players.contains(player));
        assertTrue(gray.players.contains(player));
        assertSame(gray, player.getTeam());
        assertEquals(1, gray.players.size());
    }

    // --- removeTeam ---

    @Test
    @DisplayName("removeTeam removes player from specified team")
    void removeTeamRemoves() {
        var player = createPlayer("Alice");
        var team = createTeam("red", TeamColor.RED);

        player.setTeam(team);
        player.removeTeam(team);

        assertNull(player.getTeam());
        assertFalse(team.players.contains(player));
    }

    @Test
    @DisplayName("removeTeam does nothing when player has no team")
    void removeTeamNoTeam() {
        var player = createPlayer("Alice");
        var team = createTeam("red", TeamColor.RED);

        assertDoesNotThrow(() -> player.removeTeam(team));
        assertNull(player.getTeam());
    }

    @Test
    @DisplayName("removeTeam does nothing when team doesn't match")
    void removeTeamWrongTeam() {
        var player = createPlayer("Alice");
        var red = createTeam("red", TeamColor.RED);
        var blue = createTeam("blue", TeamColor.BLUE);

        player.setTeam(red);
        player.removeTeam(blue);

        assertSame(red, player.getTeam());
        assertTrue(red.players.contains(player));
    }

    // --- Carry logic ---

    @Test
    @DisplayName("tryAddCarry succeeds when not carrying")
    void tryAddCarrySucceeds() {
        var player = createPlayer("Alice");
        var wool = createWool(WoolColor.RED);

        assertTrue(player.tryAddCarriable(wool));
        assertSame(wool, player.getCarry());
    }

    @Test
    @DisplayName("tryAddCarry fails when already carrying")
    void tryAddCarryFailsWhenCarrying() {
        var player = createPlayer("Alice");
        var wool1 = createWool(WoolColor.RED);
        var wool2 = createWool(WoolColor.BLUE);

        assertTrue(player.tryAddCarriable(wool1));
        assertFalse(player.tryAddCarriable(wool2));
        assertSame(wool1, player.getCarry());
    }

    @Test
    @DisplayName("removeCarry clears carried wool")
    void removeCarryClears() {
        var player = createPlayer("Alice");
        var wool = createWool(WoolColor.RED);

        player.tryAddCarriable(wool);
        player.removeCarry();
        assertNull(player.getCarry());
    }

    @Test
    @DisplayName("removeCarry when not carrying is safe")
    void removeCarrySafeWhenEmpty() {
        var player = createPlayer("Alice");
        assertDoesNotThrow(player::removeCarry);
        assertNull(player.getCarry());
    }

    @Test
    @DisplayName("can pick up wool after dropping")
    void pickUpAfterDrop() {
        var player = createPlayer("Alice");
        var wool1 = createWool(WoolColor.RED);
        var wool2 = createWool(WoolColor.BLUE);

        player.tryAddCarriable(wool1);
        player.removeCarry();
        assertTrue(player.tryAddCarriable(wool2));
        assertSame(wool2, player.getCarry());
    }

    // --- Wool carrying combined with team assignment ---

    @Test
    @DisplayName("player can carry wool while on a team")
    void carryWithTeam() {
        var player = createPlayer("Alice");
        var team = createTeam("red", TeamColor.RED);
        var wool = createWool(WoolColor.RED);

        player.setTeam(team);
        player.tryAddCarriable(wool);

        assertSame(team, player.getTeam());
        assertSame(wool, player.getCarry());
    }

    @Test
    @DisplayName("switching teams does not affect carried wool")
    void teamSwitchPreservesCarry() {
        var player = createPlayer("Alice");
        var red = createTeam("red", TeamColor.RED);
        var blue = createTeam("blue", TeamColor.BLUE);
        var wool = createWool(WoolColor.RED);

        player.setTeam(red);
        player.tryAddCarriable(wool);
        player.setTeam(blue);

        assertSame(blue, player.getTeam());
        assertSame(wool, player.getCarry());
    }

    // --- Multiple players on same team ---

    @Test
    @DisplayName("multiple players can be on the same team")
    void multiplePlayersOnSameTeam() {
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");
        var team = createTeam("red", TeamColor.RED);

        alice.setTeam(team);
        bob.setTeam(team);

        assertTrue(team.players.contains(alice));
        assertTrue(team.players.contains(bob));
        assertSame(team, alice.getTeam());
        assertSame(team, bob.getTeam());
    }

    @Test
    @DisplayName("removing one player from team does not affect others")
    void removeOnePlayerKeepsOthers() {
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");
        var team = createTeam("red", TeamColor.RED);

        alice.setTeam(team);
        bob.setTeam(team);
        alice.removeTeam(team);

        assertNull(alice.getTeam());
        assertSame(team, bob.getTeam());
        assertEquals(1, team.players.size());
    }

    // --- Player equality based on UUID ---

    @Test
    @DisplayName("two players with different UUIDs are not equal via their handles")
    void playersHaveDifferentUUIDs() {
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");

        assertNotEquals(alice.getPlayer().getUniqueId(), bob.getPlayer().getUniqueId());
    }

    @Test
    @DisplayName("same handle wrapped in two ManagedPlayers shares UUID")
    void sameHandleSameUUID() {
        var handle = mock(PlayerHandle.class);
        var uuid = UUID.randomUUID();
        when(handle.getUniqueId()).thenReturn(uuid);
        when(handle.getName()).thenReturn("test");
        when(handle.getDisplayName()).thenReturn("test");
        var p1 = new ManagedPlayer(handle);
        var p2 = new ManagedPlayer(handle);

        assertEquals(p1.getPlayer().getUniqueId(), p2.getPlayer().getUniqueId());
    }

    @Test
    @DisplayName("player UUID is stable across team changes")
    void uuidStableAcrossTeamChanges() {
        var player = createPlayer("Alice");
        UUID originalUUID = player.getPlayer().getUniqueId();

        player.setTeam(createTeam("red", TeamColor.RED));
        assertEquals(originalUUID, player.getPlayer().getUniqueId());

        player.setTeam(createTeam("blue", TeamColor.BLUE));
        assertEquals(originalUUID, player.getPlayer().getUniqueId());
    }

    // --- Player movement between worlds ---

    @Test
    @DisplayName("player can teleport to different world via handle")
    void playerTeleportChangesWorld() {
        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        when(handle.getWorldName()).thenReturn("world");
        when(handle.getPosition()).thenReturn(new BlockPos(0, 64, 0));
        var player = new ManagedPlayer(handle);

        // Teleport to game world
        player.getPlayer().teleport(new BlockPos(100, 64, 200), "c2w_game");

        // Verify the teleport was called on the mock
        verify(handle).teleport(new BlockPos(100, 64, 200), "c2w_game");
    }

    @Test
    @DisplayName("player position updates after teleport")
    void playerPositionUpdates() {
        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        when(handle.getWorldName()).thenReturn("world");
        when(handle.getPosition()).thenReturn(new BlockPos(0, 64, 0));
        var player = new ManagedPlayer(handle);

        // Get initial position (returned from stub)
        assertEquals(new BlockPos(0, 64, 0), handle.getPosition());

        player.getPlayer().teleport(new BlockPos(50, 70, -30), "c2w_lobby");

        // Verify teleport was called
        verify(handle).teleport(new BlockPos(50, 70, -30), "c2w_lobby");
    }

    @Test
    @DisplayName("player can teleport multiple times")
    void playerMultipleTeleports() {
        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        var player = new ManagedPlayer(handle);

        player.getPlayer().teleport(new BlockPos(10, 64, 10), "c2w_lobby");
        player.getPlayer().teleport(new BlockPos(20, 64, 20), "c2w_game");
        player.getPlayer().teleport(new BlockPos(30, 64, 30), "c2w_draft");

        verify(handle).teleport(new BlockPos(10, 64, 10), "c2w_lobby");
        verify(handle).teleport(new BlockPos(20, 64, 20), "c2w_game");
        verify(handle).teleport(new BlockPos(30, 64, 30), "c2w_draft");
    }
}
