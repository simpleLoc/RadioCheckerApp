package de.fhws.indoor.radiochecker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import de.fhws.indoor.libsmartphoneindoormap.renderer.ColorScheme;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.helpers.CsvHelper;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;
import de.fhws.indoor.libsmartphoneindoormap.renderer.MapView;
import de.fhws.indoor.libsmartphoneindoormap.model.Map;
import de.fhws.indoor.libsmartphoneindoormap.parser.MapSeenSerializer;
import de.fhws.indoor.libsmartphoneindoormap.parser.XMLMapParser;
import de.fhws.indoor.libsmartphonesensors.SensorManager;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFi;
import de.fhws.indoor.libsmartphonesensors.ui.EventCounterView;
import de.fhws.indoor.libsmartphonesensors.util.permissions.AppCompatMultiPermissionRequester;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends AppCompatActivity {
    public static final String MAP_URI = "map.xml";
    public static final String MAP_PREFERENCES = "MAP_PREFERENCES";
    public static final String MAP_PREFERENCES_FLOOR = "FloorName";
    private static final long DEFAULT_WIFI_SCAN_INTERVAL = (Build.VERSION.SDK_INT == 28 ? 30 : 1);

    private MapView mapView = null;
    private MapView.ViewConfig mapViewConfig = new MapView.ViewConfig();
    public static Map currentMap = null;
    private SensorManager sensorManager;
    private AppCompatMultiPermissionRequester permissionRequester = null;
    // sensorManager status
    private Timer sensorManagerStatisticsTimer;
    private AtomicLong loadCounterWifi = new AtomicLong(0);
    private AtomicLong loadCounterWifiRTT = new AtomicLong(0);
    private AtomicLong loadCounterBeacon = new AtomicLong(0);
    private AtomicLong loadCounterGPS = new AtomicLong(0);
    private AtomicLong loadCounterUWB = new AtomicLong(0);

    ArrayAdapter<String> mFloorNameAdapter;
    private SharedPreferences mPrefs;

    private void resetSensorStatistics() {
        loadCounterWifi.set(0);
        loadCounterWifiRTT.set(0);
        loadCounterBeacon.set(0);
        loadCounterGPS.set(0);
        loadCounterUWB.set(0);
    }
    private String makeStatusString(long evtCnt) {
        return (evtCnt == 0) ? "-" : Long.toString(evtCnt);
    }
    private void updateSensorStatistics() {
        runOnUiThread(() -> {
            EventCounterView evtCounterView = findViewById(R.id.event_counter_view);
            evtCounterView.updateCounterData(counterData -> {
                DecawaveUWB sensorUWB = sensorManager.getSensor(DecawaveUWB.class);
                WiFi sensorWifi = sensorManager.getSensor(WiFi.class);
                counterData.wifiEvtCnt = loadCounterWifi.get();
                counterData.wifiScanCnt = (sensorWifi != null) ? sensorWifi.getScanResultCount() : 0;
                counterData.bleEvtCnt = loadCounterBeacon.get();
                counterData.ftmEvtCnt = loadCounterWifiRTT.get();
                counterData.gpsEvtCnt = loadCounterGPS.get();
                counterData.uwbEvtCnt = loadCounterUWB.get();
                counterData.uwbState = EventCounterView.UWBState.from(sensorUWB);
            });
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionRequester = new AppCompatMultiPermissionRequester(this);

        mapView = findViewById(R.id.MapView);
        mapView.setColorScheme(new ColorScheme(R.color.wallColor, R.color.unseenColor, R.color.seenColor, R.color.selectedColor));
        mPrefs = getSharedPreferences(MAP_PREFERENCES, MODE_PRIVATE);

        // configure event counter view
        EventCounterView eventCounterView = findViewById(R.id.event_counter_view);
        eventCounterView.setClickable(true);
        eventCounterView.setActiveDataChangedCallback(activeData -> {
            mapViewConfig.showBluetooth = activeData.ble;
            mapViewConfig.showUWB = activeData.uwb;
            if(mapViewConfig.showWiFi != activeData.wifi) { activeData.ftm = activeData.wifi; }
            else if(mapViewConfig.showWiFi != activeData.ftm) { activeData.wifi = activeData.ftm; }
            mapViewConfig.showWiFi = activeData.wifi;
            activeData.gps = true;
            mapView.setViewConfig(mapViewConfig);
        });
        eventCounterView.updateActiveData(true, activeData -> {
            activeData.uwb = true; activeData.ble = true; activeData.wifi = true; activeData.ftm = true; activeData.gps = true;
        });


        Button btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> startActivity(new Intent(getApplicationContext(), SettingsActivity.class)));

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

                mapView.selectFloor(floorName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        //configure sensorManager
        sensorManager = new SensorManager(new SensorDataInterface() {
            @Override
            public long getStartTimestamp() { return 0; }

            @Override
            public void onData(long timestamp, SensorType sensorId, String csv) {
                if(currentMap == null) { return; }

                if(sensorId == SensorType.IBEACON) {
                    currentMap.setSeenBeacon(csv.substring(0, 12));
                    loadCounterBeacon.incrementAndGet();
                } else if(sensorId == SensorType.WIFI) {
                    currentMap.setSeenWiFi(csv.substring(0, 12));
                    loadCounterWifi.incrementAndGet();
                } else if(sensorId == SensorType.WIFIRTT) {
                    String macStr = CsvHelper.getParameter(csv, ';', 1);
                    loadCounterWifiRTT.incrementAndGet();
                    currentMap.setSeenFtm(macStr);
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
                    loadCounterUWB.incrementAndGet();
                } else if(sensorId == SensorType.GPS) {
                    loadCounterGPS.incrementAndGet();
                }
            }

            @Override
            public OutputStream requestAuxiliaryChannel(String id) throws IOException {
                return null;
            }
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
        XMLMapParser parser = new XMLMapParser(this);
        try {
            currentMap = parser.parse(getContentResolver().openInputStream(
                    Uri.fromFile(new File(getExternalFilesDir(null), MAP_URI))));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (currentMap != null) {
            currentMap.setSerializer(new MapSeenSerializer(getApplicationContext()));
        }

        mapViewConfig.showFingerprint = false;
        mapView.setViewConfig(mapViewConfig);
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
            currentMap.getFloors().keySet().stream().sorted().forEach(s -> mFloorNameAdapter.add(s));
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
        config.hasWifiRTT = true;
        config.hasBluetooth = true;
        config.hasDecawaveUWB = true;
        config.decawaveUWBTagMacAddress = preferences.getString("prefDecawaveUWBTagMacAddress", "AA:BB:CC:DD:EE:FF");
        config.wifiScanIntervalMSec = Long.parseLong(preferences.getString("prefWifiScanIntervalMSec", Long.toString(DEFAULT_WIFI_SCAN_INTERVAL)));
        config.ftmBurstSize = 0;

        try {
            sensorManager.configure(this, config, permissionRequester);
            permissionRequester.launch(() -> {
                try {
                    Log.i("RadioChecker", "Starting SensorManager");
                    sensorManager.start(this);
                } catch (Throwable e) {
                    Toast.makeText(this, "Failed to start SensorManager", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Failed to configure SensorManager", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            //TODO: ui feedback?
        }
    }
}