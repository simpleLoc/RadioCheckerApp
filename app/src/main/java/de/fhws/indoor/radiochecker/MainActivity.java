package de.fhws.indoor.radiochecker;

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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import de.fhws.indoor.libsmartphoneindoormap.model.MacAddress;
import de.fhws.indoor.libsmartphoneindoormap.model.Map;
import de.fhws.indoor.libsmartphoneindoormap.model.UWBAnchor;
import de.fhws.indoor.libsmartphoneindoormap.model.Vec3;
import de.fhws.indoor.libsmartphoneindoormap.parser.MapSeenSerializer;
import de.fhws.indoor.libsmartphoneindoormap.parser.XMLMapParser;
import de.fhws.indoor.libsmartphoneindoormap.renderer.ColorScheme;
import de.fhws.indoor.libsmartphoneindoormap.renderer.MapView;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorManager;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.helpers.CsvHelper;
import de.fhws.indoor.libsmartphonesensors.helpers.UwbConfigurator;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFi;
import de.fhws.indoor.libsmartphonesensors.ui.EventCounterView;
import de.fhws.indoor.libsmartphonesensors.util.permissions.AppCompatMultiPermissionRequester;

public class MainActivity extends AppCompatActivity {
    public static final String MAP_URI = "map.xml";
    public static final String MAP_PREFERENCES = "MAP_PREFERENCES";
    public static final String MAP_PREFERENCES_FLOOR = "FloorName";

    public static final String MAP_PREFERENCES_FLOOR_IDX = "FloorIdx";
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

    private HashMap<MacAddress, UWBAnchor> ble2UwbAnchor = new HashMap<>();
    private HashMap<MacAddress, Float> ble2FloorAtHeight = new HashMap<>();
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
        mapView.setColorScheme(new ColorScheme(
                R.color.outlineColor,
                R.color.outlineRemoveColor,
                R.color.wallColor,
                R.color.wallColorConcrete,
                R.color.wallColorWood,
                R.color.doorColor,
                R.color.doorColorLocked,
                R.color.stairColor,
                R.color.unseenColor,
                R.color.seenColor,
                R.color.selectedColor,
                R.color.uwbTagColor
        ));
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
                ed.putInt(MAP_PREFERENCES_FLOOR_IDX, i);
                ed.apply();

                mapView.selectFloor(i);
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

                    String bleMacStr = csv.substring(0, 12);
                    MacAddress bleMac = new MacAddress(bleMacStr);
                    if (ble2UwbAnchor.containsKey(bleMac)) {
                        DecawaveUWB sensorUWB = sensorManager.getSensor(DecawaveUWB.class);
                        UWBAnchor anchor = ble2UwbAnchor.get(bleMac);
                        Float floorAtHeight = ble2FloorAtHeight.get(bleMac);
                        if (anchor != null && floorAtHeight != null) {
                            sensorUWB.getConfigurator().configureDevicePosition(
                                    anchor.bleMac.toColonDelimitedString(),
                                    new UwbConfigurator.UwbAnchorConfiguration(
                                            new UwbConfigurator.UwbAnchorPosition(anchor.position.x, anchor.position.y, anchor.position.z + floorAtHeight)
                                    ),
                                    new UwbConfigurator.UWBDeviceConfiguredCallback() {

                                        @Override
                                        public void done() {
                                            currentMap.setSeenUWB(anchor.shortDeviceId);
                                            Log.d("UWBConf", "done");
                                        }

                                        @Override
                                        public void fail() {
                                            Log.e("UWBConf", "fail");
                                        }
                                    });
                        }
//                            uwbConfigurator.configure(sensorUWB, anchor.bleMac, new DecawaveUWB.UWBAnchorPosition(anchor.position.x, anchor.position.y, anchor.position.z), () -> {
//                                runOnUiThread(() -> {
//                                    currentMap.setSeenUWB(anchor.shortDeviceId);
//                                });
//                            });
                    }
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
//                    Log.d("UWBPos", segments[0] + " " + segments[1] + " " + segments[2] + " " + segments[3] + " len " + segments.length);
//                    Log.d("UWBPos", Arrays.toString(segments));
                    mapView.updateUWBTagPosition(new Vec3(Float.parseFloat(segments[0]) * 0.001f, Float.parseFloat(segments[1]) * 0.001f, Float.parseFloat(segments[2]) * 0.001f));


//                    for(int i = 4; i < segments.length; i += 3) {
//                        int shortDeviceId = Integer.parseInt(segments[i]);
//                        // shortDeviceId is a uint16
//                        if(shortDeviceId >= 0 && shortDeviceId <= 65535) {
//                            String shortDeviceIdStr = String.format("%04X", shortDeviceId);
//                            currentMap.setSeenUWB(shortDeviceIdStr);
//                        }
//                    }
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

        ble2UwbAnchor.clear();
        ble2FloorAtHeight.clear();
        if (currentMap != null) {
            currentMap.getFloors().forEach(floor -> floor.getUwbAnchors().forEach((anchorShort, anchor) -> {
                ble2UwbAnchor.put(anchor.bleMac, anchor);
                ble2FloorAtHeight.put(anchor.bleMac, floor.getAtHeight());
            }));
        }
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
        int floorIdx = mPrefs.getInt(MAP_PREFERENCES_FLOOR_IDX, 0);
        if (floorName != null) {
            mapView.selectFloor(floorIdx);
            Spinner spinnerFloor = findViewById(R.id.spinner_selectFloor);
            int position = mFloorNameAdapter.getPosition(floorName);
            spinnerFloor.setSelection(position);
        }
    }

    private void updateFloorNames() {
        if (currentMap != null) {
            mFloorNameAdapter.clear();
            currentMap.getFloors().forEach(s -> mFloorNameAdapter.add(s.getName()));
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