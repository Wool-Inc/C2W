package net.klaaswhite.c2w.classes;

import org.bukkit.Location;
import org.bukkit.entity.Marker;

public class ManagedMarker {

    private final Marker marker;
    private final String name;

    public ManagedMarker(Marker marker, String name){
        this.marker = marker;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Location getLocation(){
        return marker.getLocation();
    }

    public void remove() {
        this.marker.remove();
    }
}
