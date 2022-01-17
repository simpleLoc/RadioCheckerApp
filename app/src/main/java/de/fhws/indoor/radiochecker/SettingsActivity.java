package de.fhws.indoor.radiochecker;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.fhws.indoor.xmlmapparser.XMLMapParser;

public class SettingsActivity extends AppCompatActivity {

    ContentResolver mContentResolver = null;

    ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    Uri dst = Uri.fromFile(new File(getExternalFilesDir(null), MainActivity.MAP_URI));
                    try {
                        copy(uri, dst);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        XMLMapParser parser = new XMLMapParser();
                        MainActivity.currentMap = parser.parse(mContentResolver.openInputStream(dst));
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

    public void copy(Uri src, Uri dst) throws IOException {
        try (InputStream in = mContentResolver.openInputStream(src)) {
            try (OutputStream out = mContentResolver.openOutputStream(dst)) {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }
}