package com.example.realtimegnss;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private LocationManager locationManager;
    private Socket socket;
    private PrintWriter writer;
    private static final String SERVER_IP = "192.168.33.10"; // Replace with your computer's IP address
    private static final int SERVER_PORT = 5001; // Replace with your server's port number

    private TextView latitudeText;
    private TextView longitudeText;
    private TextView altitudeText;
    private TextView pseudorangeText;
    private TextView cn0Text;
    private TextView dopplerText;
    private TextView xText;
    private TextView yText;
    private TextView zText;

    private double latitude;
    private double longitude;
    private double altitude;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        latitudeText = findViewById(R.id.latitude_text);
        longitudeText = findViewById(R.id.longitude_text);
        altitudeText = findViewById(R.id.altitude_text);
        pseudorangeText = findViewById(R.id.pseudorange_text);
        cn0Text = findViewById(R.id.cn0_text);
        dopplerText = findViewById(R.id.doppler_text);
        xText = findViewById(R.id.x_text);
        yText = findViewById(R.id.y_text);
        zText = findViewById(R.id.z_text);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Request location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
        }

        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                OutputStream outputStream = socket.getOutputStream();
                writer = new PrintWriter(outputStream, true);
            } catch (Exception e) {
                Log.e("MainActivity", "Error connecting to server", e);
            }
        }).start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.registerGnssMeasurementsCallback(new GnssMeasurementsEvent.Callback() {
                @Override
                public void onGnssMeasurementsReceived(@NonNull GnssMeasurementsEvent event) {
                    super.onGnssMeasurementsReceived(event);
                    List<Map<String, Object>> satellitesData = new ArrayList<>();
                    for (GnssMeasurement measurement : event.getMeasurements()) {
                        Map<String, Object> satelliteData = new HashMap<>();
                        satelliteData.put("cn0", measurement.getCn0DbHz());
                        satelliteData.put("pseudorangeRate", measurement.getPseudorangeRateMetersPerSecond());
                        satelliteData.put("doppler", measurement.getPseudorangeRateMetersPerSecond());

                        // Collecting additional GNSS data
                        satelliteData.put("svid", measurement.getSvid());
                        satelliteData.put("constellationType", measurement.getConstellationType());
                        satelliteData.put("timeOffsetNanos", measurement.getTimeOffsetNanos());
                        satelliteData.put("state", measurement.getState());
                        satelliteData.put("receivedSvTimeNanos", measurement.getReceivedSvTimeNanos());
                        satelliteData.put("receivedSvTimeUncertaintyNanos", measurement.getReceivedSvTimeUncertaintyNanos());
                        satelliteData.put("cn0DbHz", measurement.getCn0DbHz());
                        satelliteData.put("pseudorangeRateMetersPerSecond", measurement.getPseudorangeRateMetersPerSecond());
                        satelliteData.put("pseudorangeRateUncertaintyMetersPerSecond", measurement.getPseudorangeRateUncertaintyMetersPerSecond());
                        satelliteData.put("accumulatedDeltaRangeState", measurement.getAccumulatedDeltaRangeState());
                        satelliteData.put("accumulatedDeltaRangeMeters", measurement.getAccumulatedDeltaRangeMeters());
                        satelliteData.put("accumulatedDeltaRangeUncertaintyMeters", measurement.getAccumulatedDeltaRangeUncertaintyMeters());
                        satelliteData.put("carrierFrequencyHz", measurement.getCarrierFrequencyHz());
                        satelliteData.put("carrierCycles", measurement.getCarrierCycles());
                        satelliteData.put("carrierPhase", measurement.getCarrierPhase());
                        satelliteData.put("carrierPhaseUncertainty", measurement.getCarrierPhaseUncertainty());
                        satelliteData.put("multipathIndicator", measurement.getMultipathIndicator());
                        satelliteData.put("snrInDb", measurement.getSnrInDb());
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            satelliteData.put("automaticGainControlLevelDb", measurement.getAutomaticGainControlLevelDb());
                        }

                        satellitesData.add(satelliteData);
                    }

                    // Send the data
                    sendCombinedData(satellitesData);
                }
            }, null);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        altitude = location.getAltitude();

        runOnUiThread(() -> {
            latitudeText.setText("Latitude: " + latitude);
            longitudeText.setText("Longitude: " + longitude);
            altitudeText.setText("Altitude: " + altitude);

        });
    }

    private void sendCombinedData(List<Map<String, Object>> satellitesData) {
        // Create a map to store all the data
        Map<String, Object> combinedData = new HashMap<>();
        combinedData.put("latitude", latitude);
        combinedData.put("longitude", longitude);
        combinedData.put("altitude", altitude);
        combinedData.put("satellites", satellitesData);

        // Convert the map to JSON
        Gson gson = new Gson();
        String json = gson.toJson(combinedData);
        Log.d("MainActivity", "Sending JSON: " + json);

        // Send the JSON data to the server
        if (writer != null) {
            new Thread(() -> {
                Log.d("MainActivity", "Sending data to server");
                writer.println(json);
            }).start(); // Ensure this runs in a background thread
        }

        runOnUiThread(() -> {
            if (!satellitesData.isEmpty()) {
                Map<String, Object> firstSatellite = satellitesData.get(0);
                pseudorangeText.setText("Pseudo-Range Rate: " + firstSatellite.get("pseudorangeRate"));
                cn0Text.setText("CN0: " + firstSatellite.get("cn0"));
                dopplerText.setText("Doppler: " + firstSatellite.get("doppler"));
//                xText.setText("X: " + x);
//                yText.setText("Y: " + y);
//                zText.setText("Z: " + z);
            }
        });
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(@NonNull String provider) {}

    @Override
    public void onProviderDisabled(@NonNull String provider) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                Log.e("MainActivity", "Error closing socket", e);
            }
        }
    }
}
