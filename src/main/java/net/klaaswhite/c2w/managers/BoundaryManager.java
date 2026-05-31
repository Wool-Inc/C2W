package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.classes.*;
import net.klaaswhite.c2w.events.InitializeGameEvent;
import net.klaaswhite.c2w.interfaces.IManager;
import org.bukkit.entity.Marker;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.function.Consumer;

public class BoundaryManager implements IManager {

    private Hashtable<ManagedTeam, HashSet<ManagedPlayer>> teamPlayersInPit;
    private HashSet<ManagedPlayer> playersInPit;
    private HashSet<Wool> woolsInPit;

    private final MarkerManager markerManager;
    private final PlayerManager playerManager;

    private ArrayList<BoundingBoxActions> boundingBoxes;

    public BoundaryManager(Managers managers){
        this.markerManager = managers.get(MarkerManager.class);
        this.playerManager = managers.get(PlayerManager.class);

        managers.get(EventManager.class).registerMinecraftEvent(PlayerMoveEvent.class, this::onPlayerMove);
        managers.get(EventManager.class).registerInternalEvent(InitializeGameEvent.class, this::init);
    }

    public void init(InitializeGameEvent event){
        this.boundingBoxes = new ArrayList<>();
        this.playersInPit = new HashSet<>();
        this.teamPlayersInPit = new Hashtable<>();
        for(var team : ManagedTeam.teams.values()){
            this.teamPlayersInPit.put(team, new HashSet<>());
        }
        this.woolsInPit = new HashSet<>();

        var pitMarker1 = this.markerManager.getMarker(KnownMarkers.BOUNDARY_WOOLCAP_PIT_1);
        var pitMarker2 = this.markerManager.getMarker(KnownMarkers.BOUNDARY_WOOLCAP_PIT_2);

        if (pitMarker1 != null && pitMarker2 != null){
            initializePitWoolCapture(pitMarker1, pitMarker2);
        }

        var elevatorMarker1 = this.markerManager.getMarker(KnownMarkers.BOUNDARY_WOOLCAP_ELEVATOR_1);
        var elevatorMarker2 = this.markerManager.getMarker(KnownMarkers.BOUNDARY_WOOLCAP_ELEVATOR_2);

        if (elevatorMarker1 != null && elevatorMarker2 != null){
            initializeElevatorWoolCapture(elevatorMarker1, elevatorMarker2);
        }
    }

    public void initializePitWoolCapture(Marker marker1, Marker marker2){
        var loc1 = marker1.getLocation();
        var loc2 = marker2.getLocation();

        var box = new BoundingBox(loc1.getX(), loc1.getY(), loc1.getZ(), loc2.getX(), loc2.getY(), loc2.getZ());

        var boundingBoxAction = new BoundingBoxActions(box, this::pitPlayerEnter, this::pitPlayerExit);
        boundingBoxes.add(boundingBoxAction);
    }

    public void initializeElevatorWoolCapture(Marker marker1, Marker marker2){
        var loc1 = marker1.getLocation();
        var loc2 = marker2.getLocation();

        var box = new BoundingBox(loc1.getX(), loc1.getY(), loc1.getZ(), loc2.getX(), loc2.getY(), loc2.getZ());

        var boundingBoxAction = new BoundingBoxActions(box, this::elevatorEnter, null);
        boundingBoxes.add(boundingBoxAction);
    }

    public void onPlayerMove(PlayerMoveEvent event){
        var from = event.getFrom();
        var to = event.getTo();

        var managedPlayer = this.playerManager.getPlayer(event.getPlayer());

        if (to == null || managedPlayer == null)
            return;

        for (var box : boundingBoxes){
            var fromInBox = box.boundingBox.contains(from.toVector());
            var toInBox = box.boundingBox.contains(to.toVector());

            if (fromInBox && !toInBox && box.playerExitEvent != null)
                box.playerExitEvent.accept(managedPlayer);

            if (!fromInBox && toInBox)
                box.playerEnterEvent.accept(managedPlayer);
        }
    }

    public void pitPlayerEnter(ManagedPlayer player){
        var team = player.getTeam();
        if (team == null)
            return;

        this.teamPlayersInPit.get(team).add(player);
        this.playersInPit.add(player);

        if (player.getCarry() instanceof Wool wool){
            this.woolsInPit.add(wool);
            wool.setCapping(true);
        }
        calcWoolCapping();
    }

    public void pitPlayerExit(ManagedPlayer player){
        var team = player.getTeam();
        if (team == null)
            return;

        this.teamPlayersInPit.get(team).remove(player);
        this.playersInPit.remove(player);

        if (player.getCarry() instanceof Wool wool){
            wool.setCapping(false);
            this.woolsInPit.remove(wool);
        }
        calcWoolCapping();
    }

    public void elevatorEnter(ManagedPlayer player){
        if (player.getCarry() instanceof Wool wool) {
            wool.capture();
        }
    }

    public void removeWoolFromCapping(Wool wool){
        this.woolsInPit.remove(wool);
    }

    private void calcWoolCapping(){
        for(var wool : this.woolsInPit){
            var carrier = wool.getCarrier();
            var team = carrier.getTeam();
            var teamInPit = this.teamPlayersInPit.get(team).size();
            var enemyInPit = this.playersInPit.size() - teamInPit;
            if (enemyInPit > teamInPit){
                wool.setCappingModifier(0);
                continue;
            }

            var modifier = WoolTimer.baseCapture.get() + WoolTimer.increasePerPlayer.get() * teamInPit + WoolTimer.decreasePerPlayer.get() * enemyInPit * -1;
            wool.setCappingModifier(modifier);
        }
    }

    @Override
    public void close() throws Exception {

    }

    private static class BoundingBoxActions{
        public BoundingBox boundingBox;
        public Consumer<ManagedPlayer> playerEnterEvent;
        public Consumer<ManagedPlayer> playerExitEvent;

        BoundingBoxActions(BoundingBox box, Consumer<ManagedPlayer> enterEvent, Consumer<ManagedPlayer> exitEvent){
            this.boundingBox = box;
            this.playerEnterEvent = enterEvent;
            this.playerExitEvent = exitEvent;
        }
    }
}
