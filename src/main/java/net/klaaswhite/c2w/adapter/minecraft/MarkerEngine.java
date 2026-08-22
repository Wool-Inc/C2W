package net.klaaswhite.c2w.adapter.minecraft;

import net.klaaswhite.c2w.domain.game.WoolTimer;
import net.klaaswhite.c2w.domain.model.BlockPos;
import net.klaaswhite.c2w.domain.model.ManagedMarker;
import net.klaaswhite.c2w.domain.model.WoolColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Marker engine that handles marker discovery via {@link MinecraftManager},
 * wool creation from wool markers, and marker lookups.
 * <p>
 * Lives in the adapter layer — uses {@link MinecraftManager} for all
 * Minecraft interactions.
 */
public class MarkerEngine {

    private final MinecraftManager mc;
    private final WoolTimer woolTimer;
    private final ArrayList<Wool> wools;
    // ponytail: cache (entity -> marker name) discovered once, so lookups don't re-read PDC repeatedly.
    private List<MarkerEntity> markerEntityList;
    private java.util.Map<MarkerEntity, String> markerNames;

    public MarkerEngine(MinecraftManager mc, WoolTimer woolTimer) {
        this.mc = mc;
        this.woolTimer = woolTimer;
        this.wools = new ArrayList<>();
        this.markerEntityList = new ArrayList<>();
    }

    public String getMarkerKey() {
        return mc.markers().getMarkerKey();
    }

    public Hashtable<String, ManagedMarker> getMarkersInWorld(String worldName) {
        var markers = new Hashtable<String, ManagedMarker>();
        var entities = mc.markers().getMarkersInWorld(worldName);
        for (var entity : entities) {
            var name = entity.getPersistentData(getMarkerKey());
            if (name == null) continue;
            markers.put(name, new ManagedMarker(entity, name));
        }
        return markers;
    }

    public List<String> getMarkerNames() {
        if (markerNames == null) return List.of();
        var names = new ArrayList<String>();
        for (var name : markerNames.values()) {
            if (name != null) names.add(name);
        }
        return names;
    }

    public void discoverMarkers(String worldName) {
        this.markerEntityList = new ArrayList<>();
        this.markerNames = new java.util.HashMap<>();
        var entities = mc.markers().getMarkersInWorld(worldName);
        for (var entity : entities) {
            var name = entity.getPersistentData(getMarkerKey());
            if (name == null) continue;
            this.markerEntityList.add(entity);
            this.markerNames.put(entity, name);
        }
    }

    public @Nullable MarkerEntity getMarker(String markerName) {
        if (markerNames == null) return null;
        for (var entry : markerNames.entrySet()) {
            if (markerName.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public void initWools(String worldName) {
        if (markerEntityList == null || markerNames == null) return;

        // Find all generic "wool" markers
        var woolMarkers = new java.util.ArrayList<MarkerEntity>();
        for (var entity : markerEntityList) {
            var name = markerNames.get(entity);
            if ("wool".equals(name)) {
                woolMarkers.add(entity);
            }
        }

        // Assign colors to each wool marker (up to 4 wools)
        WoolColor[] colors = WoolColor.values();
        for (int i = 0; i < Math.min(woolMarkers.size(), colors.length); i++) {
            var markerEntity = woolMarkers.get(i);
            BlockPos spawnPos = markerEntity.getPosition();
            if (spawnPos == null) continue;

            WoolColor color = colors[i];
            // ponytail: simple assignment, rotate if more than 4 markers
            var wool = new Wool(mc, woolTimer, color, spawnPos, worldName);
            wools.add(wool);
            wool.placeEntityInWorld();
        }
    }

    public List<Wool> getWools() {
        return new ArrayList<>(wools);
    }

    public void ensureEntityWools() {
        for (var wool : wools) {
            wool.ensureEntity();
        }
    }

    public void close() {
        for (var wool : wools) {
            woolTimer.unregisterWool(wool);
            wool.close();
        }
        wools.clear();
        if (markerEntityList != null) markerEntityList.clear();
        if (markerNames != null) markerNames.clear();
    }

    /**
     * Tear down per-game state (the wools) without disposing the engine itself.
     * Called on a mid-game reset so a subsequent {@link #initWools(String)} starts
     * from a clean slate.
     */
    public void reset() {
        for (var wool : wools) {
            woolTimer.unregisterWool(wool);
            wool.close();
        }
        wools.clear();
    }
}