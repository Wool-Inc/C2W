package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Scoreboards;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.TeamColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TeamSelectionManager")
class TeamSelectionManagerTest {

    private Scoreboards scoreboards;
    private PlayerManager playerManager;
    private TeamSelectionManager teamSelection;
    private ManagedWorld draft;
    private World draftWorld;
    private ManagedPlayer managedPlayer;
    private Player player;

    @BeforeEach
    void setUp() {
        ManagedTeam.teams.clear();
        ManagedTeam.teams.put("Red", new ManagedTeam("Red", TeamColor.RED));
        ManagedTeam.teams.put("Blue", new ManagedTeam("Blue", TeamColor.BLUE));
        ManagedTeam.teams.put("Spectator", new ManagedTeam("Spectator", TeamColor.GRAY));

        var eventManager = mock(EventManager.class);
        var worldManager = mock(WorldManager.class);
        playerManager = mock(PlayerManager.class);
        var mc = mock(MinecraftManager.class);
        scoreboards = mock(Scoreboards.class);
        when(mc.scoreboards()).thenReturn(scoreboards);

        draft = mock(ManagedWorld.class);
        draftWorld = mock(World.class);
        when(draftWorld.getName()).thenReturn("c2w_draft");
        when(draft.getName()).thenReturn("c2w_draft");
        when(draft.getWorld()).thenReturn(draftWorld);
        when(worldManager.getDraftWorld()).thenReturn(draft);

        var handle = mock(PlayerHandle.class);
        when(handle.getUniqueId()).thenReturn(UUID.randomUUID());
        when(handle.getName()).thenReturn("Alice");
        managedPlayer = new ManagedPlayer(handle);

        player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        when(playerManager.getPlayer(player)).thenReturn(managedPlayer);

        teamSelection = new TeamSelectionManager(eventManager, worldManager, playerManager, mc);
    }

    @Test
    @DisplayName("constructs without exception")
    void constructs() {
        assertNotNull(teamSelection);
    }

    @Test
    @DisplayName("stepping on the red platform sets the player's team to Red")
    void moveOnRedPlatformSetsRed() {
        teamSelection.onPlayerMove(moveEvent(-10, 65, 0));

        assertEquals("Red", managedPlayer.getTeam().teamName);
        verify(scoreboards).addPlayerToTeam("Alice", "Red");
        verify(player).sendMessage(contains("Red"));
    }

    @Test
    @DisplayName("stepping on the blue platform sets the player's team to Blue")
    void moveOnBluePlatformSetsBlue() {
        teamSelection.onPlayerMove(moveEvent(10, 65, 0));

        assertEquals("Blue", managedPlayer.getTeam().teamName);
        verify(scoreboards).addPlayerToTeam("Alice", "Blue");
    }

    @Test
    @DisplayName("stepping on the spectator platform sets the player's team to Spectator")
    void moveOnSpectatorPlatformSetsSpectator() {
        teamSelection.onPlayerMove(moveEvent(0, 71, 10));

        assertEquals("Spectator", managedPlayer.getTeam().teamName);
        verify(scoreboards).addPlayerToTeam("Alice", "Spectator");
    }

    @Test
    @DisplayName("walking on the spawn platform or walkways changes nothing")
    void moveOffPlatformNoTeam() {
        teamSelection.onPlayerMove(moveEvent(0, 65, 0));   // spawn platform
        teamSelection.onPlayerMove(moveEvent(-5, 65, 0));  // red walkway
        teamSelection.onPlayerMove(moveEvent(5, 65, 0));   // blue walkway

        assertNull(managedPlayer.getTeam());
        verify(scoreboards, never()).addPlayerToTeam(anyString(), anyString());
    }

    @Test
    @DisplayName("standing on the same platform again does not re-apply the team")
    void samePlatformDoesNotReApply() {
        teamSelection.onPlayerMove(moveEvent(-10, 65, 0));
        teamSelection.onPlayerMove(moveEvent(-10, 65, 0));

        verify(scoreboards, times(1)).addPlayerToTeam("Alice", "Red");
    }

    @Test
    @DisplayName("moving to another platform switches the player's team")
    void movingToOtherPlatformSwitchesTeam() {
        teamSelection.onPlayerMove(moveEvent(-10, 65, 0));
        teamSelection.onPlayerMove(moveEvent(10, 65, 0));

        assertEquals("Blue", managedPlayer.getTeam().teamName);
        verify(scoreboards).removePlayerFromTeam("Alice", "Red");
        verify(scoreboards).addPlayerToTeam("Alice", "Blue");
    }

    @Test
    @DisplayName("moving outside the draft world does nothing")
    void moveOutsideDraftIgnored() {
        var otherWorld = mock(World.class);
        when(otherWorld.getName()).thenReturn("c2w_game");

        var to = mock(Location.class);
        when(to.getWorld()).thenReturn(otherWorld);
        when(to.getBlockX()).thenReturn(-10);
        when(to.getBlockY()).thenReturn(65);
        when(to.getBlockZ()).thenReturn(0);

        var event = mock(PlayerMoveEvent.class);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);

        teamSelection.onPlayerMove(event);

        assertNull(managedPlayer.getTeam());
        verify(scoreboards, never()).addPlayerToTeam(anyString(), anyString());
    }

    @Test
    @DisplayName("handles a null destination location")
    void nullDestinationIgnored() {
        var event = mock(PlayerMoveEvent.class);
        when(event.getTo()).thenReturn(null);

        assertDoesNotThrow(() -> teamSelection.onPlayerMove(event));
        assertNull(managedPlayer.getTeam());
    }

    /**
     * Build a move event with a feet position in the draft world. Team platforms
     * are detected at standing height: feet y=65 for Red/Blue and y=71 for the
     * Spectator platform (the platform's surface top).
     */
    private PlayerMoveEvent moveEvent(int x, int feetY, int z) {
        var event = mock(PlayerMoveEvent.class);
        var to = mock(Location.class);
        when(to.getWorld()).thenReturn(draftWorld);
        when(to.getBlockX()).thenReturn(x);
        when(to.getBlockY()).thenReturn(feetY);
        when(to.getBlockZ()).thenReturn(z);
        when(event.getTo()).thenReturn(to);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }
}