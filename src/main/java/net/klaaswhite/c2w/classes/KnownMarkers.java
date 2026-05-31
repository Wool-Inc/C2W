package net.klaaswhite.c2w.classes;

public enum KnownMarkers {
    WOOL_RED("wool-red"),
    WOOL_GREEN("wool-green"),
    WOOL_BLUE("wool-blue"),
    WOOL_YELLOW("wool-yellow"),
    CAP_RED("cap-red"),
    CAP_GREEN("cap-green"),
    CAP_BLUE("cap-blue"),
    CAP_YELLOW("cap-yellow"),
    BOUNDARY_WOOLCAP_PIT_1("boundary-woolcap-pit-1"),
    BOUNDARY_WOOLCAP_PIT_2("boundary-woolcap-pit-2"),
    BOUNDARY_WOOLCAP_ELEVATOR_1("boundary-woolcap-elevator-1"),
    BOUNDARY_WOOLCAP_ELEVATOR_2("boundary-woolcap-elevator-2");

    private final String markerName;

    KnownMarkers(String markerName){
        this.markerName = markerName;
    }

    public String getName(){
        return markerName;
    }
}
