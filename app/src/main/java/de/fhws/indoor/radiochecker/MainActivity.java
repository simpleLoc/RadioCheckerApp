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
import android.widget.TextView;

import de.fhws.indoor.libsmartphoneindoormap.renderer.ColorScheme;
import de.fhws.indoor.libsmartphonesensors.SensorDataInterface;
import de.fhws.indoor.libsmartphonesensors.SensorType;
import de.fhws.indoor.libsmartphonesensors.sensors.DecawaveUWB;
import de.fhws.indoor.libsmartphoneindoormap.renderer.MapView;
import de.fhws.indoor.libsmartphoneindoormap.model.Map;
import de.fhws.indoor.libsmartphoneindoormap.parser.MapSeenSerializer;
import de.fhws.indoor.libsmartphoneindoormap.parser.XMLMapParser;
import de.fhws.indoor.libsmartphonesensors.SensorManager;
import de.fhws.indoor.libsmartphonesensors.sensors.WiFi;
import de.fhws.indoor.libsmartphonesensors.util.MultiPermissionRequester;

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
            WiFi wifiSensor = sensorManager.getSensor(WiFi.class);
            long wifiScanResultCnt = (wifiSensor == null) ? 0 : wifiSensor.getScanResultCount();

            final TextView txtWifi = (TextView) findViewById(R.id.txtEvtCntWifi);
            txtWifi.setText(makeStatusString(loadCounterWifi.get()) + " | " + wifiScanResultCnt);
            final TextView txtWifiRTT = (TextView) findViewById(R.id.txtEvtCntWifiRTT);
            txtWifiRTT.setText(makeStatusString(loadCounterWifiRTT.get()));
            final TextView txtBeacon = (TextView) findViewById(R.id.txtEvtCntBeacon);
            txtBeacon.setText(makeStatusString(loadCounterBeacon.get()));
            final TextView txtGPS = (TextView) findViewById(R.id.txtEvtCntGPS);

            txtGPS.setText(makeStatusString(loadCounterGPS.get()));
            final TextView txtUWB = (TextView) findViewById(R.id.txtEvtCntUWB);
            DecawaveUWB sensorUWB = sensorManager.getSensor(DecawaveUWB.class);
            if(sensorUWB != null) {
                if(sensorUWB.isConnectedToTag()) {
                    txtUWB.setText(makeStatusString(loadCounterUWB.get()));
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

        mapView = findViewById(R.id.MapView);
        mapView.setColorScheme(new ColorScheme(R.color.wallColor, R.color.unseenColor, R.color.seenColor, R.color.selectedColor));
        mPrefs = getSharedPreferences(MAP_PREFERENCES, MODE_PRIVATE);

        TextView lblCntBeacon = findViewById(R.id.lblCntBeacon);
        lblCntBeacon.setOnClickListener((view) -> {
            mapViewConfig.showBluetooth = !mapViewConfig.showBluetooth;
            mapView.setViewConfig(mapViewConfig);
            lblCntBeacon.setTextColor(getResources().getColor(((mapViewConfig.showBluetooth) ? R.color.white : R.color.unseenColor), getTheme()));
        });
        TextView lblCntUWB = findViewById(R.id.lblCntUWB);
        lblCntUWB.setOnClickListener((view) -> {
            mapViewConfig.showUWB = !mapViewConfig.showUWB;
            mapView.setViewConfig(mapViewConfig);
            lblCntUWB.setTextColor(getResources().getColor(((mapViewConfig.showUWB) ? R.color.white : R.color.unseenColor), getTheme()));
        });
        TextView lblCntWifi = findViewById(R.id.lblCntWifi);
        TextView lblCntWifiRTT = findViewById(R.id.lblCntWifiRTT);
        View.OnClickListener wifiClickListener = (view) -> {
            mapViewConfig.showWiFi = !mapViewConfig.showWiFi;
            mapView.setViewConfig(mapViewConfig);
            lblCntWifi.setTextColor(getResources().getColor(((mapViewConfig.showWiFi) ? R.color.white : R.color.unseenColor), getTheme()));
            lblCntWifiRTT.setTextColor(getResources().getColor(((mapViewConfig.showWiFi) ? R.color.white : R.color.unseenColor), getTheme()));
        };
        lblCntWifi.setOnClickListener(wifiClickListener);
        lblCntWifiRTT.setOnClickListener(wifiClickListener);

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
        config.decawaveUWBTagMacAddress = preferences.getString("prefDecawaveUWBTagMacAddress", "");
        config.wifiScanIntervalMSec = Long.parseLong(preferences.getString("prefWifiScanIntervalMSec", Long.toString(DEFAULT_WIFI_SCAN_INTERVAL)));
        config.ftmBurstSize = 0;

        try {
            MultiPermissionRequester permissionRequester = new MultiPermissionRequester(this);
            sensorManager.configure(this, config, permissionRequester);
            permissionRequester.setSuccessListener(() -> {
                try {
                    sensorManager.start(this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            permissionRequester.launch();
        } catch (Exception e) {
            e.printStackTrace();
            //TODO: ui feedback?
        }
    }
}