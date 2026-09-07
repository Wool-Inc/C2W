package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.domain.game.BoundaryEngine;
import net.klaaswhite.c2w.domain.model.Wool;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.events.WoolDroppedEvent;
import net.klaaswhite.c2w.domain.events.WoolPickedUpEvent;
import net.klaaswhite.c2w.domain.model.DomainBoundingBox;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;

public class BoundaryManager implements AutoCloseable {

    private static final int LOBBY_DRAFT_DEATH_PLANE_Y = 54;

    private final EventManager eventManager;
    private final MarkerManager markerManager;
    private final PlayerManager playerManager;
    private final MinecraftManager mc;
    private final BoundaryEngine engine;
    private int deathPlaneY = StartGameEvent.NO_DEATH_PLANE;
    private String gameWorldName;

    public BoundaryManager(EventManager eventManager, MarkerManager markerManager,
            PlayerManager playerManager, WoolTimer woolTimer) {
        this(eventManager, markerManager, playerManager, woolTimer, null);
    }

    public BoundaryManager(EventManager eventManager, MarkerManager markerManager,
            PlayerManager playerManager, WoolTimer woolTimer, MinecraftManager mc) {
        this.eventManager = eventManager;
        this.markerManager = markerManager;
        this.playerManager = playerManager;
        this.mc = mc;
        this.engine = new BoundaryEngine(new WoolTimerAdapter(woolTimer));

        this.eventManager.registerMinecraftEvent(PlayerMoveEvent.class, this::onPlayerMove);
        this.eventManager.registerMinecraftEvent(PlayerDeathEvent.class, this::onPlayerDeath);
        this.eventManager.registerMinecraftEvent(PlayerQuitEvent.class, this::onPlayerQuit);
        this.eventManager.registerInternalEvent(StartGameEvent.class, this::onStartGame);
        this.eventManager.registerInternalEvent(WoolDroppedEvent.class, this::onWoolDropped);
        this.eventManager.registerInternalEvent(WoolCapturedEvent.class, this::onWoolCaptured);
        this.eventManager.registerInternalEvent(WoolPickedUpEvent.class, this::onWoolPickedUp);
    }

    public void onStartGame(StartGameEvent event) {
        this.gameWorldName = event.getGameWorldName();
        this.deathPlaneY = event.getDeathPlaneY();
        engine.initialize(ManagedTeam.teams.values().stream()
                .collect(java.util.stream.Collectors.toCollection(HashSet::new)));

        var pitMarker1 = this.markerManager.getMarker("boundary-woolcap-pit-1");
        var pitMarker2 = this.markerManager.getMarker("boundary-woolcap-pit-2");

        if (pitMarker1 != null && pitMarker2 != null) {
            engine.addPitBox(new DomainBoundingBox(pitMarker1.getPosition(), pitMarker2.getPosition()));
        }

        var elevatorMarker1 = this.markerManager.getMarker("boundary-woolcap-elevator-1");
        var elevatorMarker2 = this.markerManager.getMarker("boundary-woolcap-elevator-2");

        if (elevatorMarker1 != null && elevatorMarker2 != null) {
            engine.addElevatorBox(new DomainBoundingBox(elevatorMarker1.getPosition(), elevatorMarker2.getPosition()));
        }
    }

    public void onPlayerMove(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        if (to == null) return;

        var toWorld = to.getWorld();
        String worldName = toWorld == null ? null : toWorld.getName();
        int playerDeathPlaneY = deathPlaneFor(worldName);
        if (mc != null && playerDeathPlaneY != StartGameEvent.NO_DEATH_PLANE
            && to.getY() < playerDeathPlaneY) {
            String playerName = event.getPlayer().getName();
            if (isLobbyOrDraftWorld(worldName) && toWorld != null) {
                var spawn = toWorld.getSpawnLocation();
                mc.players().teleportToWorld(playerName,
                    new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()),
                        worldName);
            } else {
                mc.players().setHealth(playerName, 0.0);
            }
            return;
        }

        var managedPlayer = this.playerManager.getPlayer(event.getPlayer());
        if (managedPlayer == null) return;

        engine.onPlayerMove(
                from.getX(), from.getY(), from.getZ(),
                to.getX(), to.getY(), to.getZ(),
                managedPlayer);
    }

    public void onPlayerDeath(PlayerDeathEvent event) {
        var managedPlayer = this.playerManager.getPlayer(event.getEntity());
        if (managedPlayer == null) return;

        // Drop and reset the carried wool before removing the player from the
        // boundary engine. dropOnDeath clears the carrier/helmet, resets capture
        // progress, spawns the wool back at its spawn position, and pushes
        // WoolDroppedEvent (which unregisters it from the WoolTimer via
        // onWoolDropped). Call it FIRST so the carry is cleared before
        // engine.removePlayer inspects the player's carried wool.
        if (managedPlayer.getCarry() instanceof Wool wool) {
            wool.dropOnDeath(managedPlayer);
        }
        engine.removePlayer(managedPlayer);

        var deathWorld = event.getEntity().getWorld();
        if (mc != null && deathWorld != null
            && deathPlaneFor(deathWorld.getName()) != StartGameEvent.NO_DEATH_PLANE) {
            mc.players().respawn(event.getEntity().getName());
        }
    }

    private int deathPlaneFor(String worldName) {
        if (worldName == null) return StartGameEvent.NO_DEATH_PLANE;
        if (gameWorldName != null && gameWorldName.equals(worldName)) return deathPlaneY;
        if (isLobbyOrDraftWorld(worldName)) {
            return LOBBY_DRAFT_DEATH_PLANE_Y;
        }
        return StartGameEvent.NO_DEATH_PLANE;
    }

    private boolean isLobbyOrDraftWorld(String worldName) {
        return "c2w_lobby".equals(worldName) || "c2w_draft".equals(worldName);
    }

    /**
     * On disconnect, remove the leaving player from the capture pit exactly as if
     * they had walked out of it. The carried wool stays with the player (left on
     * them), but it is removed from the in-pit list and stops capping. Mirrors
     * {@link net.klaaswhite.c2w.domain.game.BoundaryEngine#removePlayer}.
     */
    public void onPlayerQuit(PlayerQuitEvent event) {
        var managedPlayer = this.playerManager.getPlayer(event.getPlayer());
        if (managedPlayer != null) {
            engine.removePlayer(managedPlayer);
        }
    }

    public void onWoolDropped(WoolDroppedEvent event) {
        var wool = event.getWool();
        if (wool != null) engine.onWoolDropped(wool);
    }

    public void onWoolPickedUp(WoolPickedUpEvent event) {
        var wool = event.getWool();
        if (wool == null) return;
        engine.onWoolPickedUp(wool);
    }

    public void onWoolCaptured(WoolCapturedEvent event) {
        var wool = event.getWool();
        if (wool != null) engine.onWoolCaptured(wool);
    }

    @Override
    public void close() {
        engine.close();
    }

    /** Adapts WoolTimer to BoundaryEngine.WoolTimerBridge. */
    private record WoolTimerAdapter(WoolTimer timer) implements BoundaryEngine.WoolTimerBridge {
        @Override
        public int getBaseCapture() { return timer.getBaseCapture(); }
        @Override
        public int getIncreasePerPlayer() { return timer.getIncreasePerPlayer(); }
        @Override
        public int getDecreasePerPlayer() { return timer.getDecreasePerPlayer(); }
        @Override
        public void registerWool(Wool wool) { timer.registerWool(wool); }
        @Override
        public void unregisterWool(Wool wool) { timer.unregisterWool(wool); }
    }
}
