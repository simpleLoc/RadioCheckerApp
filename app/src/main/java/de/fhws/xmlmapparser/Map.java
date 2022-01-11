package de.fhws.xmlmapparser;

import java.util.HashMap;

public class Map {
    private final HashMap<String, Floor> floors = new HashMap<>();


    public HashMap<String, Floor> getFloors() {
        return floors;
    }

    public void addFloor(Floor floor) {
        floors.put(floor.getName(), floor);
    }

    public void resetSeen(boolean value) {
        for (Floor floor : floors.values()) {
            floor.resetSeen(value);
        }
    }
}
