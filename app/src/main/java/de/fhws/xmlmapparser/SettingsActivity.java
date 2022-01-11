package de.fhws.xmlmapparser;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;

public class SettingsActivity extends AppCompatActivity {

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
                        ed.putString(MainActivity.MAP_PREFERENCES_URI, uri.toString());
                        ed.apply();

                        XMLMapParser parser = new XMLMapParser();
                        MainActivity.currentMap = parser.parse(mContentResolver.openInputStream(uri));
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

        String mapUri = mPrefs.getString(MainActivity.MAP_PREFERENCES_URI, null);
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

        Button buttonResetSeen = findViewById(R.id.button_reset_seen);
        buttonResetSeen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (MainActivity.currentMap != null) {
                    MainActivity.currentMap.resetSeen(false);
                }
            }
        });
    }
}