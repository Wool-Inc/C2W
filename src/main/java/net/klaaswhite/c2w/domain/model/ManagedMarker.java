package net.klaaswhite.c2w.domain.model;

public class ManagedMarker {

    private final MarkerEntity markerEntity;
    private final String name;

    public ManagedMarker(MarkerEntity markerEntity, String name) {
        this.markerEntity = markerEntity;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public BlockPos getPosition() {
        return markerEntity.getPosition();
    }

    public void remove() {
        markerEntity.remove();
    }
}
