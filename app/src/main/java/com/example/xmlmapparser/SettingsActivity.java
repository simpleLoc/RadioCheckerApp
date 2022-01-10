package com.example.xmlmapparser;

import android.content.ContentResolver;
import android.content.Intent;
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
import androidx.preference.PreferenceFragmentCompat;

import com.example.maprenderer.MapView;

import java.io.FileNotFoundException;
import java.util.ArrayList;

public class SettingsActivity extends AppCompatActivity {

    ArrayList<String> mFloorNames = new ArrayList<>();
    ArrayAdapter<String> mSpinnerAdapter = null;
    ContentResolver mContentResolver = null;

    ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    XMLMapParser parser = new XMLMapParser();
                    TextView textViewMapPath = findViewById(R.id.textView_mapPath);
                    textViewMapPath.setText(uri.getPath());
                    try {
                        Map m = parser.parse(mContentResolver.openInputStream(uri));

                        //MapView mapView = findViewById(R.id.MapView);
                        //mapView.setMap(m);

                        mFloorNames.clear();
                        mFloorNames.addAll(m.getFloors().keySet());
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

        mContentResolver = getContentResolver();

        Button buttonSelectMap = findViewById(R.id.button_selectMap);
        buttonSelectMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mGetContent.launch("*/*");
            }
        });

        Spinner spinnerFloor = findViewById(R.id.spinner_floor);
        spinnerFloor.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mFloorNames));
        spinnerFloor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }
}