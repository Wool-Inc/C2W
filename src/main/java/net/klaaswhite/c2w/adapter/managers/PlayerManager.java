package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.bootstrap.Managers;
import net.klaaswhite.c2w.domain.game.PlayerRegistry;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ItemStackRef;
import net.klaaswhite.c2w.domain.model.ManagedPlayer;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.domain.model.PlayerHandle;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.domain.events.DraftCreatedEvent;
import net.klaaswhite.c2w.domain.events.EndGameEvent;
import net.klaaswhite.c2w.domain.events.PreviewRequestEvent;
import net.klaaswhite.c2w.domain.events.ResetEvent;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import org.jspecify.annotations.Nullable;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;


public class PlayerManager implements AutoCloseable {

    public static final String SPECTATOR_TEAM_NAME = "Spectator";

    private static final Logger log = Logger.getLogger("C2W");

    private final Managers managers;
    private final MinecraftManager mc;
    private final PlayerRegistry registry;

    private final Hashtable<Player, ManagedPlayer> playersByBukkitPlayer;

    public PlayerManager(Managers managers, MinecraftManager mc) {
        this.managers = managers;
        this.mc = mc;
        this.registry = new PlayerRegistry();
        var eventManager = managers.eventManager;

        eventManager.registerInternalEvent(DraftCreatedEvent.class, this::onDraftCreated);
        eventManager.registerInternalEvent(PreviewRequestEvent.class, this::preview);
        eventManager.registerInternalEvent(EndGameEvent.class, this::onEndGame);
        eventManager.registerInternalEvent(ResetEvent.class, this::onReset);
        eventManager.registerMinecraftEvent(PlayerJoinEvent.class, this::onNewPlayer);
        eventManager.registerMinecraftEvent(PlayerChangedWorldEvent.class, this::onPlayerChangedWorld);

        this.playersByBukkitPlayer = new Hashtable<>();
    }

    public ManagedPlayer getPlayer(UUID playerUUID) {
        return registry.getPlayer(playerUUID);
    }

    public ManagedPlayer getPlayer(String playerName) {
        var p = registry.getPlayer(playerName);
        log.info("[PlayerManager.getPlayer] name=" + playerName + " found=" + (p != null));
        return p;
    }

    public @Nullable ManagedPlayer getPlayer(Player player) {
        return playersByBukkitPlayer.get(player);
    }

    public void onDraftCreated(DraftCreatedEvent event) {
        ensureTeams();
        ensurePlayers();
        routeOnlinePlayersToDraft();
    }

    public void preview(PreviewRequestEvent event) {
        if (!(event.getCommandInput().commandSender instanceof Player player))
            return;

        var p = mc.players().getHandle(player.getName());
        if (p == null) return;
        PlayerHandle handle = toHandle(p);

        handle.sendMessage("Following players are currently known: ");
        ManagedTeam.teams.forEach((name, team) -> {
            team.players.forEach(teamPlayer -> {
                handle.sendMessage(teamPlayer.getPlayer().getDisplayName() + " (" + team.teamName + ")");
            });
        });
    }

    public void onEndGame(EndGameEvent event) {
        for (var mp : registry.getAllPlayers()) {
            var team = mp.getTeam();
            if (team != null) {
                mp.removeTeam(team);
            }
        }
        var sb = mc.scoreboards().getMainScoreboard();
        if (sb != null) {
            // ponytail: copy to array to avoid ConcurrentModificationException
            for (var team : sb.getTeams().toArray(new org.bukkit.scoreboard.Team[0])) {
                team.unregister();
            }
        }
    }

    /**
     * Called on a plugin reset ({@code /c2w reset}). Every player is dropped
     * from every team (both the domain model and the Bukkit scoreboard) so that
     * nobody carries a game team colour or tag into the lobby.
     */
    public void onReset(ResetEvent event) {
        clearAllTeams();
    }

    /** Remove every player from every C2W team — domain model and scoreboard. */
    public void clearAllTeams() {
        for (var team : ManagedTeam.teams.values()) {
            for (var mp : team.players.toArray(new ManagedPlayer[0])) {
                mp.removeTeam(team);
            }
        }
        for (var teamName : List.of("Red", "Blue", SPECTATOR_TEAM_NAME)) {
            mc.scoreboards().removeTeam(teamName);
        }
    }

    /**
     * When a player enters the lobby world, drop them from their game team so
     * they do not keep the team (and coloured name tag) they had in a game.
     */
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        var player = event.getPlayer();
        if (!isInLobbyWorld(player)) return;
        var managed = playersByBukkitPlayer.get(player);
        if (managed != null) {
            removeFromTeam(managed);
        }
    }

    private boolean isInLobbyWorld(Player player) {
        var wm = managers.worldManager;
        if (wm == null) return false;
        var lobby = wm.getLobbyWorld();
        if (lobby == null || lobby.getWorld() == null) return false;
        var current = player.getWorld();
        return current != null && current.getName().equals(lobby.getName());
    }

    /** Remove a single player from their current team, both model and scoreboard. */
    public void removeFromTeam(ManagedPlayer mp) {
        var team = mp.getTeam();
        if (team == null) return;
        var name = mp.getPlayer().getName();
        mc.scoreboards().removePlayerFromTeam(name, team.teamName);
        mp.removeTeam(team);
    }

    private void ensureTeams() {
        // Remove all teams from scoreboard
        var sb = mc.scoreboards().getMainScoreboard();
        // ponytail: copy to array to avoid ConcurrentModificationException
        for (var team : sb.getTeams().toArray(new org.bukkit.scoreboard.Team[0])) {
            team.unregister();
        }
        registry.ensureTeams();

        mc.scoreboards().createTeam("Red");
        mc.scoreboards().createTeam("Blue");
        mc.scoreboards().createTeam(SPECTATOR_TEAM_NAME);
    }

    public void ensurePlayers() {
        log.info("[PlayerManager.ensurePlayers] starting");
        for (String playerName : mc.server().getOnlinePlayerNames()) {
            var p = mc.players().getHandle(playerName);
            if (p != null) {
                ensurePlayer(toHandle(p), playerName);
            } else {
                log.warning("[PlayerManager.ensurePlayers] cannot get handle for " + playerName);
            }
        }
        log.info("[PlayerManager.ensurePlayers] done");
    }

    private void ensurePlayer(PlayerHandle handle, String playerName) {
        var foundPlayer = registry.getPlayer(handle.getUniqueId());
        if (foundPlayer != null) {
            log.info("[PlayerManager.ensurePlayer] " + playerName + " already registered");
            Object bp = handle.getBukkitPlayer();
            // ponytail: bukkitPlayer null in tests; Hashtable rejects null keys
            if (bp instanceof Player p) playersByBukkitPlayer.put(p, foundPlayer);
            return;
        }

        log.info("[PlayerManager.ensurePlayer] registering new player " + playerName);
        var newC2WPlayer = registry.registerPlayer(handle, playerName);
        Object bp = handle.getBukkitPlayer();
        if (bp instanceof Player p) playersByBukkitPlayer.put(p, newC2WPlayer);
    }

    /** Overload for Bukkit event handlers that receive a raw Player. */
    private void ensurePlayer(Player player) {
        ensurePlayer(toHandle(player), player.getName());
    }

    /** Build a domain {@link PlayerHandle} backed by a live Bukkit {@link Player}. */
    private PlayerHandle toHandle(Player p) {
        return new PlayerHandle() {
            @Override public UUID getUniqueId() { return p.getUniqueId(); }
            @Override public String getName() { return p.getName(); }
            @Override public String getDisplayName() { return p.getDisplayName(); }
            @Override public void sendMessage(String msg) { p.sendMessage(msg); }
            @Override public void teleport(BlockPos pos, String worldName) { mc.players().teleportToWorld(p.getName(), pos, worldName); }
            @Override public String getWorldName() { return p.getWorld().getName(); }
            @Override public BlockPos getPosition() { return new BlockPos(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ()); }
            @Override public void setHelmet(ItemStackRef item) { /* no-op inline */ }
            @Override public BlockPos getTargetBlock(int maxDistance) { Block b = p.getTargetBlockExact(maxDistance); return b != null ? new BlockPos(b.getX(), b.getY(), b.getZ()) : null; }
            @Override public Object getBukkitPlayer() { return p; }
        };
    }

    public void onNewPlayer(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var playerName = player.getName();
        ensurePlayer(player);

        var gameManager = managers.gameManager;
        var worldManager = managers.worldManager;
        var pluginConfig = managers.pluginConfig;
        if (gameManager == null || worldManager == null || pluginConfig == null) return;

        if (gameManager.isGameInProgress()) {
            var specTeam = ManagedTeam.teams.get(SPECTATOR_TEAM_NAME);
            if (specTeam != null) {
                playersByBukkitPlayer.get(player).setTeam(specTeam);
            }
            var gameWorld = worldManager.getGameWorld();
            if (gameWorld != null) {
                BlockPos target = gameWorld.getSpawnPos();
                var specSpawn = gameManager.getSpawnPointForTeam(SPECTATOR_TEAM_NAME);
                if (specSpawn != null) {
                    target = new BlockPos(specSpawn.x(), specSpawn.y() + 1, specSpawn.z());
                }
                mc.players().teleportToWorld(playerName, target, gameWorld.getName());
                mc.players().setRespawnLocation(playerName, target, gameWorld.getName(), true);
                mc.server().broadcastMessage("Player " + playerName + " joined and was sent to game world as spectator.");
            }
            return;
        }

        if (gameManager.isDraftCreated()) {
            var draftWorld = worldManager.getDraftWorld();
            if (draftWorld != null) {
                // Reset to survival so the joining player isn't stuck in spectator.
                mc.players().setGameMode(playerName, "SURVIVAL");
                mc.players().teleportToWorld(playerName, draftWorld.getSpawnPos(), draftWorld.getName());
                mc.server().broadcastMessage("Player " + playerName + " joined during draft phase and was sent to draft world.");
            }
            return;
        }

        if (gameManager.isNotStarted() && pluginConfig.isLobbyAutoJoin()) {
            var lobby = worldManager.getLobbyWorld();
            if (lobby != null) {
                // Reset to survival so players waiting in the lobby aren't stuck in spectator,
                // and drop them from any game team so nobody carries it into the lobby.
                mc.players().setGameMode(playerName, "SURVIVAL");
                var managed = playersByBukkitPlayer.get(player);
                if (managed != null) {
                    removeFromTeam(managed);
                }
                mc.players().teleportToWorld(playerName, lobby.getSpawnPos(), lobby.getName());
            }
        }
    }

    private void routeOnlinePlayersToDraft() {
        var draft = managers.worldManager != null ? managers.worldManager.getDraftWorld() : null;
        if (draft == null) return;
        for (String playerName : mc.server().getOnlinePlayerNames()) {
            // Reset to survival so a player who was a spectator in a previous
            // game doesn't stay in spectator mode in the draft world.
            mc.players().setGameMode(playerName, "SURVIVAL");
            mc.players().teleportToWorld(playerName, draft.getSpawnPos(), draft.getName());
        }
    }

    public void addPlayersToTeam(String teamName, Iterable<String> players) {
        registry.addPlayersToTeam(teamName, players);
    }

    public void removePlayersFromTeam(String teamName, Iterable<String> players) {
        registry.removePlayersFromTeam(teamName, players);
    }

    public PlayerRegistry getPlayerRegistry() {
        return registry;
    }

    public @Nullable ManagedWorld findManagedWorld(World world) {
        var wm = managers.worldManager;
        if (wm == null) return null;
        if (world == null) return null;
        for (var mw : new ManagedWorld[]{
                wm.getLobbyWorld(), null, wm.getReferenceWorld(),
                wm.getDraftWorld(), wm.getGameWorld()
        }) {
            if (mw == null) continue;
            var mwWorld = mw.getWorld();
            if (mwWorld != null && mwWorld.equals(world)) return mw;
        }
        return null;
    }

    @Override
    public void close() {
        ManagedTeam.teams.clear();
        registry.reset();
        playersByBukkitPlayer.clear();
    }
}
