package de.fhws.indoor.radiochecker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;
import de.fhws.indoor.maprenderer.MapView;
import de.fhws.indoor.xmlmapparser.Map;
import de.fhws.indoor.xmlmapparser.XMLMapParser;
import de.fhws.indoor.libsmartphonesensors.SensorManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    public static final String MAP_URI = "map.xml";
    public static final String MAP_PREFERENCES = "MAP_PREFERENCES";
    public static final String MAP_PREFERENCES_FLOOR = "FloorName";
    private static final long DEFAULT_WIFI_SCAN_INTERVAL = (Build.VERSION.SDK_INT == 28 ? 30 : 1);

    public static Map currentMap = null;
    private SensorManager sensorManager = new SensorManager();
    // sensorManager status
    private Timer sensorManagerStatisticsTimer;
    private volatile int loadCounterWifi = 0;
    private volatile int loadCounterWifiRTT = 0;
    private volatile int loadCounterBeacon = 0;
    private volatile int loadCounterGPS = 0;
    private volatile int loadCounterUWB = 0;

    ArrayAdapter<String> mFloorNameAdapter;
    private SharedPreferences mPrefs;

    private void resetSensorStatistics() {
        loadCounterWifi = 0;
        loadCounterWifiRTT = 0;
        loadCounterBeacon = 0;
        loadCounterGPS = 0;
        loadCounterUWB = 0;
    }
    private String makeStatusString(long evtCnt) {
        return (evtCnt == 0) ? "-" : Long.toString(evtCnt);
    }
    private void updateSensorStatistics() {
        runOnUiThread(() -> {
            final TextView txtWifi = (TextView) findViewById(R.id.txtEvtCntWifi);
            txtWifi.setText(makeStatusString(loadCounterWifi));
            final TextView txtWifiRTT = (TextView) findViewById(R.id.txtEvtCntWifiRTT);
            txtWifiRTT.setText(makeStatusString(loadCounterWifiRTT));
            final TextView txtBeacon = (TextView) findViewById(R.id.txtEvtCntBeacon);
            txtBeacon.setText(makeStatusString(loadCounterBeacon));
            final TextView txtGPS = (TextView) findViewById(R.id.txtEvtCntGPS);

            txtGPS.setText(makeStatusString(loadCounterGPS));
            final TextView txtUWB = (TextView) findViewById(R.id.txtEvtCntUWB);
            DecawaveUWB sensorUWB = sensorManager.getSensor(DecawaveUWB.class);
            if(sensorUWB != null) {
                if(sensorUWB.isConnectedToTag()) {
                    txtUWB.setText(makeStatusString(loadCounterUWB));
                } else {
                    txtUWB.setText(sensorUWB.isCurrentlyConnecting() ? "⌛" : "✖");
                }
            }
        });
    }

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
                    int shortDeviceId = Integer.parseInt(segments[i]);
                    // shortDeviceId is a uint16
                    if(shortDeviceId >= 0 && shortDeviceId <= 65535) {
                        String shortDeviceIdStr = String.format("%04X", shortDeviceId);
                        currentMap.setSeenUWB(shortDeviceIdStr);
                    }
                }
            }
        });

        //register sensorManager listener for statistics UI
        sensorManager.addSensorListener((timestamp, id, csv) -> {
            // update UI for WIFI/BEACON/GPS
            if(id == SensorType.WIFI) { runOnUiThread(() -> loadCounterWifi++); }
            if(id == SensorType.WIFIRTT) { runOnUiThread(() -> loadCounterWifiRTT++); }
            if(id == SensorType.IBEACON) { runOnUiThread(() -> loadCounterBeacon++); }
            if(id == SensorType.GPS) { runOnUiThread(() -> loadCounterGPS++); }
            if(id == SensorType.DECAWAVE_UWB) { runOnUiThread(() -> loadCounterUWB++); }
        });

        sensorManagerStatisticsTimer = new Timer();
        sensorManagerStatisticsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateSensorStatistics();
            }
        }, 250, 250);
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
        resetSensorStatistics();
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