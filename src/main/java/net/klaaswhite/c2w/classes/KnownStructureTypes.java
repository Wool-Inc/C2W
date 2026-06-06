package net.klaaswhite.c2w.classes;

public enum KnownStructureTypes {
    DUNGEON("dungeon"),
    TRANSITION("transition"),
    CENTER("center"),
    SPAWN("spawn"),
    CORNER("corner"),
    SIDE("side");

    private final String structureKey;

    KnownStructureTypes(String structureKey){
        this.structureKey = structureKey;
    }

    public String getKey(){
        return structureKey;
    }
}
