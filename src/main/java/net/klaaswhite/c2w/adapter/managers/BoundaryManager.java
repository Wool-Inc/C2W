package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.domain.game.BoundaryEngine;
import net.klaaswhite.c2w.domain.model.Wool;
import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.domain.events.WoolCapturedEvent;
import net.klaaswhite.c2w.domain.events.WoolDroppedEvent;
import net.klaaswhite.c2w.domain.events.WoolPickedUpEvent;
import net.klaaswhite.c2w.domain.model.DomainBoundingBox;
import net.klaaswhite.c2w.domain.model.ManagedTeam;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;

public class BoundaryManager implements AutoCloseable {

    private final EventManager eventManager;
    private final MarkerManager markerManager;
    private final PlayerManager playerManager;
    private final BoundaryEngine engine;

    public BoundaryManager(EventManager eventManager, MarkerManager markerManager,
            PlayerManager playerManager, WoolTimer woolTimer) {
        this.eventManager = eventManager;
        this.markerManager = markerManager;
        this.playerManager = playerManager;
        this.engine = new BoundaryEngine(new WoolTimerAdapter(woolTimer));

        this.eventManager.registerMinecraftEvent(PlayerMoveEvent.class, this::onPlayerMove);
        this.eventManager.registerMinecraftEvent(PlayerDeathEvent.class, this::onPlayerDeath);
        this.eventManager.registerInternalEvent(StartGameEvent.class, this::onStartGame);
        this.eventManager.registerInternalEvent(WoolDroppedEvent.class, this::onWoolDropped);
        this.eventManager.registerInternalEvent(WoolCapturedEvent.class, this::onWoolCaptured);
        this.eventManager.registerInternalEvent(WoolPickedUpEvent.class, this::onWoolPickedUp);
    }

    public void onStartGame(StartGameEvent event) {
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
        var managedPlayer = this.playerManager.getPlayer(event.getPlayer());
        if (to == null || managedPlayer == null) return;

        engine.onPlayerMove(
                from.getX(), from.getY(), from.getZ(),
                to.getX(), to.getY(), to.getZ(),
                managedPlayer);
    }

    public void onPlayerDeath(PlayerDeathEvent event) {
        var managedPlayer = this.playerManager.getPlayer(event.getEntity());
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
