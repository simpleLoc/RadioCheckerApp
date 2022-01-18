package de.fhws.indoor.radiochecker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.maprenderer.MapView;
import de.fhws.indoor.xmlmapparser.Map;
import de.fhws.indoor.xmlmapparser.XMLMapParser;
import de.fhws.indoor.libsmartphonesensors.SensorManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public static final String MAP_URI = "map.xml";
    public static final String MAP_PREFERENCES = "MAP_PREFERENCES";
    public static final String MAP_PREFERENCES_FLOOR = "FloorName";
    private static final long DEFAULT_WIFI_SCAN_INTERVAL = (Build.VERSION.SDK_INT == 28 ? 30 : 1);

    public static Map currentMap = null;
    private SensorManager sensorManager = new SensorManager();

    ArrayAdapter<String> mFloorNameAdapter;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(MAP_PREFERENCES, MODE_PRIVATE);

        Button btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
            }
        });

        mFloorNameAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());

        Spinner spinnerFloor = findViewById(R.id.spinner_selectFloor);
        spinnerFloor.setAdapter(mFloorNameAdapter);
        spinnerFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String floorName = (String) adapterView.getItemAtPosition(i);

                SharedPreferences.Editor ed = mPrefs.edit();
                ed.putString(MAP_PREFERENCES_FLOOR, floorName);
                ed.apply();

                MapView mapView = findViewById(R.id.MapView);
                mapView.selectFloor(floorName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        // setup sensorManager callbacks
        sensorManager.addSensorListener((timestamp, sensorId, csv) -> {
            if(currentMap == null) { return; }

            if(sensorId == SensorType.IBEACON) {
                currentMap.setSeenBeacon(csv.substring(0, 12));
            } else if(sensorId == SensorType.WIFI) {
                currentMap.setSeenWiFi(csv.substring(0, 12));
            } else if(sensorId == SensorType.DECAWAVE_UWB) {
                String[] segments = csv.split(";");
                // skip initial 4 (x, y, z, quality) - then take every 3rd
                for(int i = 4; i < segments.length; i += 3) {
                    if(segments[i].length() == 4) { // short device id
                        currentMap.setSeenUWB(segments[i]);
                    }
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        showMap();
        setupSensors();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            sensorManager.stop(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showMap() {
        XMLMapParser parser = new XMLMapParser();
        try {
            currentMap = parser.parse(getContentResolver().openInputStream(
                    Uri.fromFile(new File(getExternalFilesDir(null), MAP_URI))));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        MapView mapView = findViewById(R.id.MapView);
        mapView.setMap(currentMap);
        updateFloorNames();

        String floorName = mPrefs.getString(MAP_PREFERENCES_FLOOR, null);
        if (floorName != null) {
            mapView.selectFloor(floorName);
            Spinner spinnerFloor = findViewById(R.id.spinner_selectFloor);
            int position = mFloorNameAdapter.getPosition(floorName);
            spinnerFloor.setSelection(position);
        }
    }

    private void updateFloorNames() {
        if (currentMap != null) {
            mFloorNameAdapter.clear();
            mFloorNameAdapter.addAll(currentMap.getFloors().keySet());
        }
    }

    protected void setupSensors() {
        try {
            sensorManager.stop(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        SensorManager.Config config = new SensorManager.Config();
        config.hasWifi = true;
        config.hasBluetooth = true;
        config.hasDecawaveUWB = true;
        config.decawaveUWBTagMacAddress = preferences.getString("prefDecawaveUWBTagMacAddress", "");
        config.wifiScanIntervalSec = Long.parseLong(preferences.getString("prefWifiScanIntervalMSec", Long.toString(DEFAULT_WIFI_SCAN_INTERVAL)));

        try {
            sensorManager.configure(this, config);
            sensorManager.start(this);
        } catch (Exception e) {
            e.printStackTrace();
            //TODO: ui feedback?
        }
    }
}