package com.example.xmlmapparser;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;

import com.example.maprenderer.MapView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        XMLMapParser parser = new XMLMapParser();
        File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        Map m = parser.parse("file://" + downloadDir.getPath() + "/SHL56_full.xml");

        MapView mapView = findViewById(R.id.MapView);
        mapView.setMap(m);

        Button settingsButton = findViewById(R.id.button_settings);
        settingsButton.setOnClickListener(view -> setContentView(R.layout.settings_activity));
    }
}