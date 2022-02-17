package de.fhws.indoor.radiochecker;

import java.util.ArrayList;
import java.util.HashMap;

public class MeasurementCounter<DeviceIdentifier> {

    private static final int MAX_EVT_COUNTS = 5000;
    private HashMap<DeviceIdentifier, Long> measurements = new HashMap<>();

    public synchronized void addMeasurement(DeviceIdentifier identifier, float measurement) {
        if(!measurements.containsKey(identifier)) {
            measurements.put(identifier, new Long(1));
        } else {
            measurements.put(identifier, new Long(measurements.get(identifier) + 1));
        }
    }

    public synchronized HashMap<DeviceIdentifier, ArrayList<Float>> get() {
        return (HashMap<DeviceIdentifier, ArrayList<Float>>) measurements.clone();
    }

}
