package net.klaaswhite.c2w.domain.game;

import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.domain.model.TeamColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PlayerRegistry")
class PlayerRegistryTest {

    private PlayerRegistry registry;

    @BeforeEach
    void setUp() {
        // Clear teams before each test
        ManagedTeam.teams.clear();
        registry = new PlayerRegistry();
        registry.ensureTeams();
    }

    private ManagedPlayer createPlayer(String name) {
        var handle = mock(PlayerHandle.class);
        var uuid = UUID.randomUUID();
        when(handle.getName()).thenReturn(name);
        when(handle.getDisplayName()).thenReturn(name);
        when(handle.getUniqueId()).thenReturn(uuid);
        return registry.registerPlayer(handle, name);
    }

    // --- Initial state ---

    @Test
    @DisplayName("starts with default teams")
    void defaultTeams() {
        assertNotNull(ManagedTeam.teams.get("Red"));
        assertNotNull(ManagedTeam.teams.get("Blue"));
        assertNotNull(ManagedTeam.teams.get("Spectator"));
    }

    // --- Registration ---

    @Test
    @DisplayName("registerPlayer creates ManagedPlayer and stores it")
    void registerPlayer() {
        var handle = mock(PlayerHandle.class);
        var uuid = UUID.randomUUID();
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        when(handle.getUniqueId()).thenReturn(uuid);

        var mp = registry.registerPlayer(handle, "Alice");

        assertNotNull(mp);
        assertEquals("Alice", mp.getPlayer().getDisplayName());
        assertSame(mp, registry.getPlayer(uuid));
        assertSame(mp, registry.getPlayer("Alice"));
    }

    @Test
    @DisplayName("registering same player twice returns same instance")
    void registerDuplicate() {
        var handle = mock(PlayerHandle.class);
        var uuid = UUID.randomUUID();
        when(handle.getName()).thenReturn("Alice");
        when(handle.getDisplayName()).thenReturn("Alice");
        when(handle.getUniqueId()).thenReturn(uuid);

        var first = registry.registerPlayer(handle, "Alice");
        var second = registry.registerPlayer(handle, "Alice");

        assertSame(first, second);
        assertEquals(1, registry.getPlayerCount());
    }

    // --- Lookup ---

    @Test
    @DisplayName("getPlayer by UUID returns correct player")
    void getPlayerByUUID() {
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");

        assertSame(alice, registry.getPlayer(alice.getPlayer().getUniqueId()));
        assertSame(bob, registry.getPlayer(bob.getPlayer().getUniqueId()));
    }

    @Test
    @DisplayName("getPlayer by name returns correct player")
    void getPlayerByName() {
        var alice = createPlayer("Alice");

        assertSame(alice, registry.getPlayer("Alice"));
    }

    @Test
    @DisplayName("getPlayer by name is case-sensitive")
    void getPlayerByNameCaseSensitive() {
        createPlayer("Alice");

        assertNull(registry.getPlayer("alice"));
    }

    @Test
    @DisplayName("getPlayer returns null for unknown UUID")
    void getPlayerUnknownUUID() {
        assertNull(registry.getPlayer(UUID.randomUUID()));
    }

    @Test
    @DisplayName("getPlayer returns null for unknown name")
    void getPlayerUnknownName() {
        assertNull(registry.getPlayer("Unknown"));
    }

    // --- Player count ---

    @Test
    @DisplayName("playerCount tracks registered players")
    void playerCount() {
        assertEquals(0, registry.getPlayerCount());

        createPlayer("Alice");
        assertEquals(1, registry.getPlayerCount());

        createPlayer("Bob");
        assertEquals(2, registry.getPlayerCount());
    }

    // --- Team management ---

    @Test
    @DisplayName("addPlayersToTeam assigns players to team")
    void addPlayersToTeam() {
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");

        registry.addPlayersToTeam("Red", List.of("Alice", "Bob"));

        assertEquals("Red", alice.getTeam().teamName);
        assertEquals("Red", bob.getTeam().teamName);
    }

    @Test
    @DisplayName("addPlayersToTeam ignores unknown players")
    void addPlayersToTeamUnknown() {
        createPlayer("Alice");

        assertDoesNotThrow(() -> registry.addPlayersToTeam("Red", List.of("Unknown")));
    }

    @Test
    @DisplayName("addPlayersToTeam ignores unknown teams")
    void addPlayersToTeamUnknownTeam() {
        createPlayer("Alice");

        assertDoesNotThrow(() -> registry.addPlayersToTeam("NonExistent", List.of("Alice")));
    }

    @Test
    @DisplayName("removePlayersFromTeam removes players from team")
    void removePlayersFromTeam() {
        var alice = createPlayer("Alice");
        registry.addPlayersToTeam("Red", List.of("Alice"));
        assertNotNull(alice.getTeam());

        registry.removePlayersFromTeam("Red", List.of("Alice"));

        assertNull(alice.getTeam());
    }

    @Test
    @DisplayName("getAllPlayers returns all registered players")
    void getAllPlayers() {
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");

        var all = registry.getAllPlayers();
        assertEquals(2, all.size());
        assertTrue(all.contains(alice));
        assertTrue(all.contains(bob));
    }

    // --- Team balancing ---

    @Test
    @DisplayName("balanceTeams balances players evenly")
    void balanceTeams() {
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");
        var charlie = createPlayer("Charlie");
        var dave = createPlayer("Dave");

        registry.balanceTeams();

        var redCount = registry.getPlayerCountByTeam("Red");
        var blueCount = registry.getPlayerCountByTeam("Blue");
        assertEquals(2, redCount);
        assertEquals(2, blueCount);
    }

    @Test
    @DisplayName("balanceTeams handles odd number of players")
    void balanceTeamsOdd() {
        var alice = createPlayer("Alice");
        var bob = createPlayer("Bob");
        var charlie = createPlayer("Charlie");

        registry.balanceTeams();

        var redCount = registry.getPlayerCountByTeam("Red");
        var blueCount = registry.getPlayerCountByTeam("Blue");
        assertEquals(2, redCount);
        assertEquals(1, blueCount);
    }

    @Test
    @DisplayName("balanceTeams clears existing team assignments")
    void balanceTeamsClearsExisting() {
        var alice = createPlayer("Alice");
        registry.addPlayersToTeam("Blue", List.of("Alice"));

        registry.balanceTeams();

        assertEquals("Red", alice.getTeam().teamName);
    }

    @Test
    @DisplayName("balanceTeams is idempotent")
    void balanceTeamsIdempotent() {
        createPlayer("Alice");
        createPlayer("Bob");
        createPlayer("Charlie");

        registry.balanceTeams();
        var red1 = registry.getPlayerCountByTeam("Red");
        var blue1 = registry.getPlayerCountByTeam("Blue");

        registry.balanceTeams();
        var red2 = registry.getPlayerCountByTeam("Red");
        var blue2 = registry.getPlayerCountByTeam("Blue");

        assertEquals(red1, red2);
        assertEquals(blue1, blue2);
    }

    // --- Reset ---

    @Test
    @DisplayName("reset clears all registered players but keeps teams")
    void reset() {
        createPlayer("Alice");
        createPlayer("Bob");
        assertEquals(2, registry.getPlayerCount());

        registry.reset();

        assertEquals(0, registry.getPlayerCount());
        assertNotNull(ManagedTeam.teams.get("Red"));
    }
}