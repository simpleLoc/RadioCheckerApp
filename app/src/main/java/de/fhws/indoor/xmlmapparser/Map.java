package de.fhws.indoor.xmlmapparser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;

import de.fhws.indoor.radiochecker.MainActivity;

public class Map {
    public interface ChangedListener {
        void onChange();
    }

    private final ArrayList<ChangedListener> changedListeners = new ArrayList<>();
    private final HashMap<String, Floor> floors = new HashMap<>();
    private MapSeenSerializer serializer = null;

    public void addChangedListener(ChangedListener listener) {
        changedListeners.add(listener);
    }

    public void removeChangedListener(ChangedListener listener) {
        changedListeners.remove(listener);
    }

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
        serializer.clearSeenStates();
        raiseChangedListeners();
    }

    public void setSerializer(MapSeenSerializer serializer) {
        this.serializer = serializer;
        this.serializer.loadSeenStates(this);
    }

    public void setSeenBeacon(String macStr) {
        setSeenBeacon(macStr, true);
    }

    public void setSeenBeacon(String macStr, boolean save) {
        MacAddress mac = new MacAddress(macStr);
        Optional<Beacon> beacon = floors.values().stream()
                .flatMap(floor -> floor.getBeacons().values().stream())
                .filter(b -> b.mac.equals(mac)).findFirst();
        beacon.ifPresent(b -> {
            if (!b.seen) {
                b.seen = true;
                if (serializer != null && save) {
                    serializer.saveSeenStateBeacon(macStr);
                }
                raiseChangedListeners();
            }
        });
    }


    public void setSeenUWB(String shortDeviceId) {
        setSeenUWB(shortDeviceId, true);
    }

    public void setSeenUWB(String shortDeviceId, boolean save) {
        Optional<UWBAnchor> anchor = floors.values().stream()
                .flatMap(floor -> floor.getUwbAnchors().values().stream())
                .filter(a -> a.shortDeviceId.equalsIgnoreCase(shortDeviceId)).findFirst();
        anchor.ifPresent(a -> {
            if (!a.seen) {
                a.seen = true;
                if (serializer != null && save) {
                    serializer.saveSeenStateUWB(shortDeviceId);
                }
                raiseChangedListeners();
            }
        });
    }

    public void setSeenWiFi(String macStr) {
        setSeenWiFi(macStr, true);
    }

    public void setSeenWiFi(String macStr, boolean save) {
        MacAddress mac = new MacAddress(macStr);
        Optional<AccessPoint> ap = floors.values().stream()
                .flatMap(floor -> floor.getAccessPoints().values().stream())
                .filter(a -> a.mac.equals(mac)).findFirst();
        ap.ifPresent(a -> {
            if (!a.seen) {
                a.seen = true;
                if (serializer != null && save) {
                    serializer.saveSeenStateWiFi(macStr);
                }
                raiseChangedListeners();
            }
        });
    }

    private void raiseChangedListeners() {
        changedListeners.forEach(ChangedListener::onChange);
    }
}
