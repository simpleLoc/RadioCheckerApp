package de.fhws.indoor.radiochecker;

import android.content.Context;
import android.view.View;

import java.util.ArrayList;

public class SignalStrengthHistogram extends View {

    private static int MAX_MEASUREMENTS = 5000;
    private ArrayList<Float> measurements = new ArrayList<>();

    public SignalStrengthHistogram(Context context) {
        super(context);
    }

    public void push(float signalStrength) {
        measurements.add(signalStrength);
        if(measurements.size() > MAX_MEASUREMENTS) {
            measurements.remove(0);
        }
    }

    public void render() {

    }

}
