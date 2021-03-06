package de.fhws.indoor.radiochecker;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.fhws.indoor.libsmartphoneindoormap.parser.XMLMapParser;

/**
 * @author Markus Ebner
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    ContentResolver mContentResolver = null;
    ActivityResultLauncher<String> mGetContent = registerForActivityResult(
        new ActivityResultContracts.GetContent(),
            uri -> {
                Uri dst = Uri.fromFile(new File(getActivity().getExternalFilesDir(null), MainActivity.MAP_URI));
                try {
                    copyMapToAppStorage(uri, dst);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    XMLMapParser parser = new XMLMapParser(getContext());
                    MainActivity.currentMap = parser.parse(mContentResolver.openInputStream(dst));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            });


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout v = (LinearLayout)super.onCreateView(inflater, container, savedInstanceState);

        Button btnSelectMap = new Button(getActivity().getApplicationContext());
        btnSelectMap.setText(R.string.select_map_button_text);
        v.addView(btnSelectMap);
        btnSelectMap.setOnClickListener(btn -> handleSelectMap());

        Button btnResetSeen = new Button(getActivity().getApplicationContext());
        btnResetSeen.setText(R.string.reset_seen_button_text);
        v.addView(btnResetSeen);
        btnResetSeen.setOnClickListener(btn -> {
            if (MainActivity.currentMap != null) {
                MainActivity.currentMap.resetSeen(true);
            }
        });

        return v;
    }


    private void handleSelectMap() {
        mContentResolver = getActivity().getContentResolver();
        mGetContent.launch("*/*");
    }

    // To guarantee that the app still has access to the chosen map, we copy
    // it into the app's local storage path - where the app always has
    // read access.
    private void copyMapToAppStorage(Uri src, Uri dst) throws IOException {
        try (InputStream in = mContentResolver.openInputStream(src)) {
            try (OutputStream out = mContentResolver.openOutputStream(dst, "wt")) {
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
