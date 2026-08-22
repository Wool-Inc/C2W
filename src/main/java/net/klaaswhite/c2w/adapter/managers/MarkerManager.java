package net.klaaswhite.c2w.adapter.managers;

import net.klaaswhite.c2w.adapter.minecraft.MarkerEngine;
import net.klaaswhite.c2w.adapter.minecraft.MarkerEntity;
import net.klaaswhite.c2w.adapter.minecraft.Markers;
import net.klaaswhite.c2w.adapter.minecraft.MinecraftManager;
import net.klaaswhite.c2w.adapter.minecraft.Players;
import net.klaaswhite.c2w.adapter.minecraft.Wool;
import net.klaaswhite.c2w.bootstrap.Managers;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import net.klaaswhite.c2w.domain.events.StartGameEvent;
import net.klaaswhite.c2w.bootstrap.world.ManagedWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;

import org.jspecify.annotations.Nullable;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks named marker entities (keyed by their {@code map_marker} persistent
 * data tag) in the active game world. Marker names are plain strings, not an
 * enum - the generic {@code wool} markers are resolved by string pattern in
 * {@link #initWools()}.
 */
public class MarkerManager implements AutoCloseable {

    /**
     * The full set of marker names a structure may declare. Used by
     * {@code /marker create}'s tab-completion. Names are not a closed set:
     * structures may add their own internal markers (chests, spawners,
     * capture points) that the plugin does not interpret.
     */
    public static final List<String> MARKER_NAMES = List.of(
            "wool",
            "boundary-woolcap-pit-1", "boundary-woolcap-pit-2",
            "boundary-woolcap-elevator-1", "boundary-woolcap-elevator-2"
    );

    private final Managers managers;
    private final EventManager eventManager;

    private final MinecraftManager mc;
    private final MarkerEngine engine;

    private final AtomicBoolean initialized;

    private String worldName;

    public MarkerManager(Managers managers, EventManager eventManager, MinecraftManager mc) {
        this.managers = managers;
        this.eventManager = eventManager;
        this.mc = mc;
        this.engine = new MarkerEngine(mc, managers.woolTimer);

        this.eventManager.registerInternalEvent(StartGameEvent.class, this::start);
        this.eventManager.registerInternalEvent(net.klaaswhite.c2w.domain.events.ResetEvent.class, this::onReset);

        this.initialized = new AtomicBoolean(false);
    }

    public String getMarkerKey() {
        return engine.getMarkerKey();
    }

    public Hashtable<String, ManagedMarker> getMarkersInWorld(ManagedWorld world) {
        return getMarkersInWorld(world.getName());
    }

    public Hashtable<String, ManagedMarker> getMarkersInWorld(String worldName) {
        return engine.getMarkersInWorld(worldName);
    }

    public List<String> getMarkersInWorld(Player caller) {
        String worldName = mc.players().getWorldName(caller.getName());
        if (worldName == null) return List.of();
        var markers = getMarkersInWorld(worldName);
        return markers.keySet().stream().toList();
    }

    public void createMarker(Player caller, String at, String name) {
        if (initialized.get()) {
            caller.sendMessage("Markers cannot be altered when the game after game is initialized");
            return;
        }

        BlockPos pos;
        switch (at) {
            case "looking" -> {
                var target = mc.players().getTargetBlock(caller.getName(), 6);
                if (target == null) {
                    caller.sendMessage("No block in sight.");
                    return;
                }
                pos = target;
            }
            case "player" -> pos = mc.players().getPosition(caller.getName());
            default -> throw new IllegalStateException("Unexpected value: " + at);
        }

        if (pos == null) {
            caller.sendMessage("Could not determine position.");
            return;
        }

        String worldName = mc.players().getWorldName(caller.getName());
        if (worldName == null) {
            caller.sendMessage("Could not determine your world.");
            return;
        }
        var markerEntity = mc.markers().spawnMarker(worldName, pos);
        if (markerEntity != null) {
            markerEntity.setPersistentData(getMarkerKey(), name);
        }
    }

    public boolean removeMarker(Player caller, String markerName) {
        String worldName = mc.players().getWorldName(caller.getName());
        if (worldName == null) return false;
        var markers = getMarkersInWorld(worldName);
        if (!markers.containsKey(markerName)) return false;
        markers.get(markerName).remove();
        return true;
    }

    public void start(StartGameEvent event) {
        this.worldName = event.getGameWorldName();
        engine.discoverMarkers(this.worldName);
        initialized.set(true);
        engine.initWools(this.worldName);
        registerWoolPickupListeners();
    }

    /**
     * Register EntityPickupItemEvent listeners for each wool's dropped item entity,
     * so that picking up the wool item calls {@link Wool#pickup(ManagedPlayer)}.
     */
    private void registerWoolPickupListeners() {
        for (var wool : engine.getWools()) {
            var droppedId = wool.getDroppedItemId();
            if (droppedId == null) continue;

            // Register the listener by UUID directly — the chunk may not be loaded
            // yet so Bukkit.getEntity() could return null.  The EntityManager's
            // UUID-based lookup fires when the item is picked up regardless.
            managers.entityManager.addItemPickedUpEventListener(droppedId, event -> {
                if (!(event.getEntity() instanceof org.bukkit.entity.Player player)) {
                    event.setCancelled(true);
                    return;
                }
                var managedPlayer = managers.playerManager.getPlayer(player);
                if (managedPlayer == null) {
                    event.setCancelled(true);
                    return;
                }
                // delegate to the wool's pickup logic
                if (!wool.pickup(managedPlayer)) {
                    event.setCancelled(true);
                    return;
                }
                // pickup succeeded — cancel the default Bukkit pickup so the item
                // doesn't go into the player's inventory; the wool handles it
                event.setCancelled(true);
            });
        }
    }

    public @Nullable MarkerEntity getMarker(String markerName) {
        if (!initialized.get()) return null;
        return engine.getMarker(markerName);
    }

    public List<String> getMarkersInWorld() {
        if (!initialized.get()) return List.of();
        return engine.getMarkerNames();
    }

    public void ensureEntityWools() {
        if (!initialized.get()) return;
        engine.ensureEntityWools();
    }

    public List<net.klaaswhite.c2w.adapter.minecraft.Wool> getWools() {
        return engine.getWools();
    }

    /**
     * Tear down per-game state (the wools) on a mid-game reset. The engine and
     * marker discovery are preserved so a subsequent game start can re-init.
     */
    public void reset() {
        engine.reset();
    }

    private void onReset(net.klaaswhite.c2w.domain.events.ResetEvent event) {
        reset();
    }

    @Override
    public void close() {
        engine.close();
        initialized.set(false);
        this.worldName = null;
    }
}
