package de.fhws.indoor.xmlmapparser;

import java.util.HashMap;
import java.util.Locale;
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

    public void setSeenBeacon(String macStr) {
        MacAddress mac = new MacAddress(macStr);
        Optional<Beacon> beacon = floors.values().stream()
                .flatMap(floor -> floor.getBeacons().values().stream())
                .filter(b -> b.mac.equals(mac)).findFirst();
        beacon.ifPresent(b -> b.seen = true);
    }

    public void setSeenUWB(String shortDeviceId) {
        Optional<UWBAnchor> anchor = floors.values().stream()
                .flatMap(floor -> floor.getUwbAnchors().values().stream())
                .filter(a -> a.shortDeviceId.equalsIgnoreCase(shortDeviceId)).findFirst();
        anchor.ifPresent(a -> a.seen = true);
    }

    public void setSeenWiFi(String macStr) {
        MacAddress mac = new MacAddress(macStr);
        Optional<AccessPoint> ap = floors.values().stream()
                .flatMap(floor -> floor.getAccessPoints().values().stream())
                .filter(a -> a.mac.equals(mac)).findFirst();
        ap.ifPresent(a -> a.seen = true);
    }
}
