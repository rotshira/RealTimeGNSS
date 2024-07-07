package com.example.realtimegnss;
//

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
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private LocationManager locationManager;
    private Socket socket;
    private PrintWriter writer;
    private static final String SERVER_IP = "192.168.43.53"; // Replace with your computer's IP address
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
    private double pseudorangeRateMetersPerSecond;
    private double cn0DbHz;
    private double dopplerShift;
    private double x;
    private double y;
    private double z;

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
                    for (GnssMeasurement measurement : event.getMeasurements()) {
                        cn0DbHz = measurement.getCn0DbHz();
                        pseudorangeRateMetersPerSecond = measurement.getPseudorangeRateMetersPerSecond();
                        dopplerShift = pseudorangeRateMetersPerSecond; // Use pseudorange rate as Doppler shift

                        // Calculate x, y, z here using your Python logic converted to Java
                        calculateSatellitePosition();

                        Log.d("MainActivity", "Sending GNSS data");
                        sendCombinedData();
                    }
                }
            }, null);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        altitude = location.getAltitude();

        calculateSatellitePosition();
        sendCombinedData();

        runOnUiThread(() -> {
            latitudeText.setText("Latitude: " + latitude);
            longitudeText.setText("Longitude: " + longitude);
            altitudeText.setText("Altitude: " + altitude);
        });
    }

    private void calculateSatellitePosition() {
        // WGS84 ellipsoid constants
        double a = 6378137.0; // semi-major axis in meters
        double f = 1 / 298.257223563; // flattening
        double b = a * (1 - f); // semi-minor axis in meters
        double e2 = (a * a - b * b) / (a * a); // square of eccentricity

        // Convert latitude, longitude, altitude to radians
        double latRad = Math.toRadians(latitude);
        double lonRad = Math.toRadians(longitude);

        // Calculate prime vertical radius of curvature
        double N = a / Math.sqrt(1 - e2 * Math.sin(latRad) * Math.sin(latRad));

        // Calculate X, Y, Z coordinates
        x = (N + altitude) * Math.cos(latRad) * Math.cos(lonRad);
        y = (N + altitude) * Math.cos(latRad) * Math.sin(lonRad);
        z = ((1 - e2) * N + altitude) * Math.sin(latRad);
    }

    private void sendCombinedData() {
        // Create a map to store all the data
        Map<String, Object> combinedData = new HashMap<>();
        combinedData.put("latitude", latitude);
        combinedData.put("longitude", longitude);
        combinedData.put("altitude", altitude);
        combinedData.put("pseudorangeRate", pseudorangeRateMetersPerSecond);
        combinedData.put("cn0", cn0DbHz);
        combinedData.put("doppler", dopplerShift);
        combinedData.put("x", x);
        combinedData.put("y", y);
        combinedData.put("z", z);

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
            pseudorangeText.setText("Pseudo-Range Rate: " + pseudorangeRateMetersPerSecond);
            cn0Text.setText("CN0: " + cn0DbHz);
            dopplerText.setText("Doppler: " + dopplerShift);
            xText.setText("X: " + x);
            yText.setText("Y: " + y);
            zText.setText("Z: " + z);
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
