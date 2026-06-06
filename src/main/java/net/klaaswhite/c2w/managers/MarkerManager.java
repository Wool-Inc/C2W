package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.classes.KnownMarkers;
import net.klaaswhite.c2w.classes.Lazy;
import net.klaaswhite.c2w.classes.ManagedMarker;
import net.klaaswhite.c2w.classes.Wool;
import net.klaaswhite.c2w.events.InitializeGameEvent;
import net.klaaswhite.c2w.events.StartGameEvent;
import net.klaaswhite.c2w.interfaces.IManager;
import net.klaaswhite.c2w.worlds.ManagedWorld;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MarkerManager implements IManager {

    private final Managers managers;
    private final Lazy<EventManager> eventManager;

    private final ArrayList<Wool> wools;
    private final NamespacedKey markerKey;
    private final AtomicBoolean initialized;

    private World world;
    private Hashtable<KnownMarkers, Marker> markers;

    public MarkerManager(Managers managers){
        this.managers = managers;
        this.eventManager = managers.get(EventManager.class);
        this.eventManager.getValue().registerInternalEvent(InitializeGameEvent.class, this::init);
        this.eventManager.getValue().registerInternalEvent(StartGameEvent.class, this::start);

        markerKey = new NamespacedKey(managers.getPlugin(), "map_marker");
        wools = new ArrayList<>();

        initialized = new AtomicBoolean(false);
    }

    public Hashtable<String, ManagedMarker> getMarkersInWorld(ManagedWorld world){
        return getMarkersInWorld(world.getWorld());
    }

    public Hashtable<String, ManagedMarker> getMarkersInWorld(World world) {
        var markers = new Hashtable<String, ManagedMarker>();

        var entities = world.getEntities();
        for(var entity : entities){
            if (!(entity instanceof Marker marker))
                continue;

            var name = marker.getPersistentDataContainer().get(this.markerKey, PersistentDataType.STRING);
            if (name == null)
                continue;
            markers.put(name, new ManagedMarker(marker, name));
        }

        return markers;
    }

    public static Hashtable<String, KnownMarkers> getKnownMarkers(){
        var knowMarkersMap = new Hashtable<String, KnownMarkers>();
        var knowMarkers = KnownMarkers.values();

        for(var knownMarker : knowMarkers){
            knowMarkersMap.put(knownMarker.getName(), knownMarker);
        }

        return knowMarkersMap;
    }

    private void ensureMarkers(World world) {
        this.markers = new Hashtable<KnownMarkers, Marker>();

        var entities = world.getEntities();
        var knownMarkers = getKnownMarkers();

        for(var entity : entities){
            if (!(entity instanceof Marker marker))
                continue;

            var name = marker.getPersistentDataContainer().get(this.markerKey, PersistentDataType.STRING);
            if (name == null)
                continue;

            if (!(knownMarkers.get(name) instanceof KnownMarkers knownMarker))
                continue;
            markers.put(knownMarker, marker);
        }
    }

    public List<String> getMarkersInWorld(Player caller){
        var markers = getMarkersInWorld(caller.getWorld());
        return markers.keySet().stream().toList();
    }

    public void createMarker(Player caller, String at, String name){
        if (initialized.get()){
            caller.sendMessage("Markers cannot be altered when the game after game is initialized");
        }

        Location location = switch (at) {
            case "looking" -> caller.getLineOfSight(null, 6).getFirst().getLocation();
            case "player" -> caller.getLocation();
            default -> throw new IllegalStateException("Unexpected value: " + at);
        };

        var marker = caller.getWorld().spawnEntity(location, EntityType.MARKER);
        marker.getPersistentDataContainer().set(
                markerKey,
                PersistentDataType.STRING,
                name
        );
    }

    public boolean removeMarker(Player caller, String markerName){
        var markers = getMarkersInWorld(caller.getWorld());
        if (!markers.containsKey(markerName)) return false;
        markers.get(markerName).remove();
        return true;
    }

    public void init(InitializeGameEvent event){
        var input = event.getCommandInput();
        if (!(input.commandSender instanceof Player player))
            return;

        initialized.set(true);
        this.world = player.getWorld();
        ensureMarkers(this.world);

        player.sendMessage("Following known markers were found: ");
        this.markers.forEach(((knownMarker, marker) -> {
            player.sendMessage(knownMarker.getName());
        }));
    }

    public void start(StartGameEvent event){
        initWools();
    }

    public void initWools(){
        var markerNames = this.markers.keySet().stream().toList();

        var woolMarkers = markerNames.stream().filter(marker -> marker.getName().contains("wool")).toList();
        for(var woolMarkerName : woolMarkers){
            var woolMarker = markers.get(woolMarkerName);
            var wool = Wool.Create(this.managers, this.world, woolMarker.getLocation(), woolMarkerName);
            if (wool == null)
                continue;

            this.wools.add(wool);

            wool.placeEntityInWorld();
        }
    }

    @Nullable
    public Marker getMarker(KnownMarkers markerName){
        if (!initialized.get()) return null;
        return this.markers.get(markerName);
    }

    public List<KnownMarkers> getMarkersInWorld(){
        if (!initialized.get()) return List.of();
        return markers.keySet().stream().toList();
    }

    public void ensureEntityWools(){
        if (!initialized.get()) return;

        for(var wool : this.wools){
            wool.ensureEntity();
        }
    }

    @Override
    public void close() throws Exception {
        for(var wool : this.wools){
            wool.close();
        }
        initialized.set(false);
    }
}
