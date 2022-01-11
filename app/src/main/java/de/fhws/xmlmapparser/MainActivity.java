package de.fhws.xmlmapparser;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import de.fhws.maprenderer.MapView;

import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
    public static final String MAP_PREFERENCES = "MAP_PREFERENCES";
    public static Map currentMap = null;

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(MAP_PREFERENCES, MODE_PRIVATE);

        showMap();

        Button settingsButton = findViewById(R.id.button_settings);
        settingsButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showMap();
    }

    private void showMap() {
        String mapUri = mPrefs.getString("Uri", null);
        if (currentMap == null && mapUri != null) {
            XMLMapParser parser = new XMLMapParser();
            try {
                currentMap = parser.parse(getContentResolver().openInputStream(Uri.parse(mapUri)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        MapView mapView = findViewById(R.id.MapView);
        mapView.setMap(currentMap);

        String floorName = mPrefs.getString("FloorName", null);
        if (floorName != null) {
            mapView.selectFloor(floorName);
        }
    }
}