package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.classes.KnownStructureTypes;
import net.klaaswhite.c2w.classes.Lazy;
import net.klaaswhite.c2w.classes.ManagedMarker;
import net.klaaswhite.c2w.events.InitializeGameEvent;
import net.klaaswhite.c2w.interfaces.IManager;
import net.klaaswhite.c2w.worlds.ManagedWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Marker;

import java.util.ArrayList;
import java.util.HashMap;

public class StructureManager implements IManager {



    private final org.bukkit.structure.StructureManager bukkitStructureManager;

    private final Lazy<MarkerManager> markerManager;
    private final Lazy<WorldManager> worldManager;


    public StructureManager(Managers managers){
        this.markerManager = managers.get(MarkerManager.class);
        this.worldManager = managers.get(WorldManager.class);

        this.bukkitStructureManager = Bukkit.getStructureManager();
        var eventManager = managers.get(EventManager.class).getValue();
        eventManager.registerInternalEvent(InitializeGameEvent.class, this::init);
    }

    private void init(InitializeGameEvent event){
        var templateMarkers = markerManager.getValue().getMarkersInWorld(worldManager.getValue().getTemplateWorld());

        var knownStructureMarkers = new HashMap<KnownStructureTypes, ArrayList<ManagedMarker>>();

        for(var knownStructureType : KnownStructureTypes.values()){
            knownStructureMarkers.put(knownStructureType, )
        }
    }

    @Override
    public void close() throws Exception {

    }
}
