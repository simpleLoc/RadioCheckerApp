package de.fhws.xmlmapparser;

import java.util.HashMap;
import java.util.Optional;

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

    public void setSeenBeacon(String mac) {
        Optional<Beacon> beacon = floors.values().stream()
                .flatMap(floor -> floor.getBeacons().values().stream())
                .filter(b -> b.mac.equals(mac)).findFirst();
        beacon.ifPresent(b -> b.seen = true);
    }

    public void setSeenUWB(String shortDeviceId) {
        Optional<UWBAnchor> anchor = floors.values().stream()
                .flatMap(floor -> floor.getUwbAnchors().values().stream())
                .filter(a -> a.shortDeviceId.equals(shortDeviceId)).findFirst();
        anchor.ifPresent(a -> a.seen = true);
    }

    public void setSeenWiFi(String mac) {
        Optional<AccessPoint> ap = floors.values().stream()
                .flatMap(floor -> floor.getAccessPoints().values().stream())
                .filter(a -> a.mac.equals(mac)).findFirst();
        ap.ifPresent(a -> a.seen = true);
    }
}
