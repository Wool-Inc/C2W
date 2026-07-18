package net.klaaswhite.c2w.domain.model;

import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.TeamColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ManagedTeam")
class ManagedTeamTest {

    private ManagedPlayer createPlayer(String name) {
        var handle = mock(PlayerHandle.class);
        var uuid = UUID.randomUUID();
        when(handle.getName()).thenReturn(name);
        when(handle.getDisplayName()).thenReturn(name);
        when(handle.getUniqueId()).thenReturn(uuid);
        return new ManagedPlayer(handle);
    }

    @Test
    @DisplayName("creates team with name and color")
    void createsWithProperties() {
        var team = new ManagedTeam("red", TeamColor.RED);
        assertEquals("red", team.teamName);
        assertEquals(TeamColor.RED, team.color);
    }

    @Test
    @DisplayName("starts with empty player set")
    void startsEmpty() {
        var team = new ManagedTeam("red", TeamColor.RED);
        assertTrue(team.players.isEmpty());
    }

    @Test
    @DisplayName("player can be added via ManagedPlayer.setTeam")
    void playerAddedViaManagedPlayer() {
        var team = new ManagedTeam("red", TeamColor.RED);
        var player = createPlayer("Alice");
        player.setTeam(team);
        assertTrue(team.players.contains(player));
    }

    @Test
    @DisplayName("multiple players can join the same team")
    void multiplePlayers() {
        var team = new ManagedTeam("red", TeamColor.RED);
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");
        alice.setTeam(team);
        bob.setTeam(team);
        assertEquals(2, team.players.size());
        assertTrue(team.players.contains(alice));
        assertTrue(team.players.contains(bob));
    }

    @Test
    @DisplayName("player leaving team removes from set")
    void playerLeaving() {
        var team = new ManagedTeam("red", TeamColor.RED);
        var player = createPlayer("Alice");
        player.setTeam(team);
        player.setTeam(null);
        assertFalse(team.players.contains(player));
    }

    @Test
    @DisplayName("teams map is static and shared")
    void teamsMapIsStatic() {
        var team = new ManagedTeam("red", TeamColor.RED);
        // The static map should be accessible
        ManagedTeam.teams.put("red", team);
        assertSame(team, ManagedTeam.teams.get("red"));
        // Clean up to not affect other tests
        ManagedTeam.teams.remove("red");
    }

    @Test
    @DisplayName("different team colors are distinct")
    void differentColors() {
        var red = new ManagedTeam("red", TeamColor.RED);
        var blue = new ManagedTeam("blue", TeamColor.BLUE);
        assertNotEquals(red.color, blue.color);
    }

    @Test
    @DisplayName("two distinct instances with the same name are equal")
    void equalsByTeamName() {
        var a = new ManagedTeam("red", TeamColor.RED);
        var b = new ManagedTeam("red", TeamColor.BLUE); // different color, same name
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("instances with different names are not equal")
    void notEqualDifferentName() {
        var red = new ManagedTeam("red", TeamColor.RED);
        var blue = new ManagedTeam("blue", TeamColor.RED);
        assertNotEquals(red, blue);
    }

    @Test
    @DisplayName("equals is consistent with Hashtable keying")
    void hashtableKeying() {
        var a = new ManagedTeam("red", TeamColor.RED);
        var b = new ManagedTeam("red", TeamColor.BLUE);
        var table = new java.util.Hashtable<ManagedTeam, String>();
        table.put(a, "value");
        assertEquals("value", table.get(b));
    }
}
