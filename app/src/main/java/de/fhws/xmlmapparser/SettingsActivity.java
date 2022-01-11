package de.fhws.xmlmapparser;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;
import java.util.ArrayList;

public class SettingsActivity extends AppCompatActivity {

    ArrayList<String> mFloorNames = new ArrayList<>();
    ContentResolver mContentResolver = null;

    private SharedPreferences mPrefs;

    ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    TextView textViewMapPath = findViewById(R.id.textView_mapPath);
                    textViewMapPath.setText(uri.getPath());
                    try {
                        SharedPreferences.Editor ed = mPrefs.edit();
                        ed.putString("Uri", uri.toString());
                        ed.apply();

                        XMLMapParser parser = new XMLMapParser();
                        MainActivity.currentMap = parser.parse(mContentResolver.openInputStream(uri));

                        updateFloorNames();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mPrefs = getSharedPreferences(MainActivity.MAP_PREFERENCES, MODE_PRIVATE);
        mContentResolver = getContentResolver();

        String mapUri = mPrefs.getString("Uri", null);
        if (mapUri != null) {
            TextView textViewMapPath = findViewById(R.id.textView_mapPath);
            textViewMapPath.setText(Uri.parse(mapUri).getPath());
        }

        Button buttonSelectMap = findViewById(R.id.button_selectMap);
        buttonSelectMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mGetContent.launch("*/*");
            }
        });

        if (MainActivity.currentMap != null) {
            updateFloorNames();
        }

        Spinner spinnerFloor = findViewById(R.id.spinner_floor);
        spinnerFloor.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mFloorNames));
        spinnerFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String floorName = (String) adapterView.getItemAtPosition(i);

                SharedPreferences.Editor ed = mPrefs.edit();
                ed.putString("FloorName", floorName);
                ed.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private void updateFloorNames() {
        mFloorNames.clear();
        mFloorNames.addAll(MainActivity.currentMap.getFloors().keySet());
    }
}