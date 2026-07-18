package net.klaaswhite.c2w.domain.game;

import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.TeamColor;
import net.klaaswhite.c2w.domain.model.PlayerHandle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

/**
 * Pure-domain player registry. Handles player registration, lookup by
 * UUID/name, team assignment, and team balancing.
 * <p>
 * Does NOT depend on Bukkit — only on domain model classes and
 * {@link PlayerHandle}. Fully testable via fakes.
 */
public class PlayerRegistry {

    private final Hashtable<UUID, ManagedPlayer> playersByUUID;
    private final Hashtable<String, ManagedPlayer> playersByName;

    public PlayerRegistry() {
        this.playersByUUID = new Hashtable<>();
        this.playersByName = new Hashtable<>();
    }

    // --- Teams ---

    public void ensureTeams() {
        ManagedTeam.teams.computeIfAbsent("Red", k -> new ManagedTeam("Red", TeamColor.RED));
        ManagedTeam.teams.computeIfAbsent("Blue", k -> new ManagedTeam("Blue", TeamColor.BLUE));
        ManagedTeam.teams.computeIfAbsent("Spectator", k -> new ManagedTeam("Spectator", TeamColor.GRAY));
    }

    // --- Registration ---

    public ManagedPlayer registerPlayer(PlayerHandle handle, String playerName) {
        var existing = playersByUUID.get(handle.getUniqueId());
        if (existing != null) {
            playersByName.put(playerName, existing);
            return existing;
        }

        var mp = new ManagedPlayer(handle);
        playersByUUID.put(handle.getUniqueId(), mp);
        playersByName.put(playerName, mp);
        return mp;
    }

    // --- Lookup ---

    public ManagedPlayer getPlayer(UUID uuid) {
        return playersByUUID.get(uuid);
    }

    public ManagedPlayer getPlayer(String name) {
        return playersByName.get(name);
    }

    public int getPlayerCount() {
        return playersByUUID.size();
    }

    public Collection<ManagedPlayer> getAllPlayers() {
        return new ArrayList<>(playersByUUID.values());
    }

    // --- Team management ---

    public void addPlayersToTeam(String teamName, Iterable<String> playerNames) {
        var team = ManagedTeam.teams.get(teamName);
        if (team == null) return;

        for (var name : playerNames) {
            var mp = playersByName.get(name);
            if (mp != null) {
                mp.setTeam(team);
            }
        }
    }

    public void removePlayersFromTeam(String teamName, Iterable<String> playerNames) {
        var team = ManagedTeam.teams.get(teamName);
        if (team == null) return;

        for (var name : playerNames) {
            var mp = playersByName.get(name);
            if (mp != null) {
                mp.removeTeam(team);
            }
        }
    }

    public int getPlayerCountByTeam(String teamName) {
        var team = ManagedTeam.teams.get(teamName);
        if (team == null) return 0;
        return team.players.size();
    }

    // --- Auto-balance ---

    public void balanceTeams() {
        var redTeam = ManagedTeam.teams.get("Red");
        var blueTeam = ManagedTeam.teams.get("Blue");
        if (redTeam == null || blueTeam == null) return;

        // Remove all players from teams
        new ArrayList<>(redTeam.players).forEach(mp -> mp.removeTeam(redTeam));
        new ArrayList<>(blueTeam.players).forEach(mp -> mp.removeTeam(blueTeam));

        // Balance
        var redCount = 0;
        var blueCount = 0;
        for (var mp : playersByUUID.values()) {
            if (redCount <= blueCount) {
                mp.setTeam(redTeam);
                redCount++;
            } else {
                mp.setTeam(blueTeam);
                blueCount++;
            }
        }
    }

    // --- Reset ---

    public void reset() {
        playersByUUID.clear();
        playersByName.clear();
    }
}