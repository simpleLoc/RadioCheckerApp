package de.fhws.indoor.xmlmapparser;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class MapSeenSerializer {
    private static final String TAG = "MapSeenSerializer";
    private static final String TYPE_BEACON = "Beacon";
    private static final String TYPE_UWB = "UWB";
    private static final String TYPE_WIFI = "WiFi";

    private static final String SEPARATOR = " ";

    private static final String SAFE_FILE = "SeenStates.txt";


    private final Uri safeFileUri;
    private final Context ctx;

    public MapSeenSerializer(Context applicationContext) {
        ctx = applicationContext;
        safeFileUri = Uri.fromFile(new File(applicationContext.getExternalFilesDir(null), SAFE_FILE));
    }

    public void saveSeenStateBeacon(String identifier) {
        saveSeenState(TYPE_BEACON, identifier);
    }

    public void saveSeenStateUWB(String identifier) {
        saveSeenState(TYPE_UWB, identifier);
    }

    public void saveSeenStateWiFi(String identifier) {
        saveSeenState(TYPE_WIFI, identifier);
    }

    public void clearSeenStates() {
        File toDelete = new File(safeFileUri.getPath());
        toDelete.delete();
    }

    public void loadSeenStates(Map map) {
        try {
            InputStream inputStream = new FileInputStream(safeFileUri.getPath());

            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String receiveString = "";

            while ( (receiveString = bufferedReader.readLine()) != null ) {
                String[] list = receiveString.split(SEPARATOR);

                if (list.length != 2) {
                    throw new IOException("Parse error at: " + receiveString);
                }

                loadSeenState(map, list[0], list[1]);
            }

            inputStream.close();
        }
        catch (FileNotFoundException e) {
            Log.w(TAG, "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e(TAG, "Can not read file: " + e.toString());
        }
    }

    private void loadSeenState(Map map, String type, String identifier) {
        switch (type) {
            case TYPE_BEACON:
                map.setSeenBeacon(identifier, false);
                break;

            case TYPE_UWB:
                map.setSeenUWB(identifier, false);
                break;

            case TYPE_WIFI:
                map.setSeenWiFi(identifier, false);
                break;

            default:
                break;
        }
    }

    private void saveSeenState(String type, String identifier) {
        OutputStreamWriter writer;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(safeFileUri.getPath(), true));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Could not write to file: " + e);
            return;
        }

        try {
            writer.write(type);
            writer.write(SEPARATOR);
            writer.write(identifier);
            writer.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
