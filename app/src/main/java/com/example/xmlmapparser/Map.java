package com.example.xmlmapparser;

import java.util.ArrayList;
import java.util.HashMap;

public class Map {
    private final HashMap<String, Floor> floors = new HashMap<>();


    public HashMap<String, Floor> getFloors() {
        return floors;
    }

    public void addFloor(Floor floor) {
        floors.put(floor.getName(), floor);
    }
}
