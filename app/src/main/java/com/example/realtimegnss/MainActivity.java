package com.example.realtimegnss;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import android.location.LocationManager;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

        public class MainActivity extends AppCompatActivity {

            private static final int REQUEST_CODE_PERMISSIONS = 1;
            private static final String SERVER_IP = "10.0.0.2"; // Replace with your computer's IP address
            private static final int SERVER_PORT = 5001; // Replace with your server's port number

            private FusedLocationProviderClient fusedLocationClient;
            private LocationCallback locationCallback;
            private Socket socket;
            private PrintWriter writer;
            private LocationManager locationManager;

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

                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
                locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

                // Request permissions
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                            REQUEST_CODE_PERMISSIONS);
                } else {
                    startLocationUpdates();
                    registerGnssMeasurementsCallback();
                }

                // Start socket connection in a separate thread
                new Thread(() -> {
                    try {
                        socket = new Socket(SERVER_IP, SERVER_PORT);
                        OutputStream outputStream = socket.getOutputStream();
                        writer = new PrintWriter(outputStream, true);
                        Log.d("MainActivity", "Socket connection established");
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error connecting to server", e);
                    }
                }).start();
            }

            private void startLocationUpdates() {
                LocationRequest locationRequest = LocationRequest.create()
                        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                        .setInterval(1000) // 1 second interval
                        .setFastestInterval(500); // 0.5 second fastest interval

                locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult locationResult) {
                        super.onLocationResult(locationResult);
                        if (locationResult == null) {
                            Log.d("MainActivity", "Location result is null");
                            return;
                        }

                        for (Location location : locationResult.getLocations()) {
                            Log.d("MainActivity", "Location result: " + location);
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                            altitude = location.getAltitude();

                            Log.d("MainActivity", "Latitude: " + latitude);
                            Log.d("MainActivity", "Longitude: " + longitude);
                            Log.d("MainActivity", "Altitude: " + altitude);

                            calculateSatellitePosition();

                            runOnUiThread(() -> {
                                latitudeText.setText("Latitude: " + latitude);
                                longitudeText.setText("Longitude: " + longitude);
                                altitudeText.setText("Altitude: " + altitude);
                                xText.setText("X: " + x);
                                yText.setText("Y: " + y);
                                zText.setText("Z: " + z);
                            });
                        }
                    }
                };

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            }

            private void registerGnssMeasurementsCallback() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    locationManager.registerGnssMeasurementsCallback(new GnssMeasurementsEvent.Callback() {
                        @Override
                        public void onGnssMeasurementsReceived(@NonNull GnssMeasurementsEvent event) {
                            super.onGnssMeasurementsReceived(event);
                            Log.d("MainActivity", "GNSS measurements received");
                            List<Map<String, Object>> satellitesData = new ArrayList<>();
                            for (GnssMeasurement measurement : event.getMeasurements()) {
                                Log.d("MainActivity", "Processing GNSS measurement: " + measurement);
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

            private void sendCombinedData(List<Map<String, Object>> satellitesData) {
                Log.d("MainActivity", "Preparing to send combined data");
                // Create a map to store all the data
                Map<String, Object> combinedData = new HashMap<>();
                combinedData.put("latitude", latitude);
                combinedData.put("longitude", longitude);
                combinedData.put("altitude", altitude);
                combinedData.put("x", x);
                combinedData.put("y", y);
                combinedData.put("z", z);
                combinedData.put("satellites", satellitesData);

                // Convert the map to JSON
                Gson gson = new Gson();
                String json = gson.toJson(combinedData);
                Log.d("MainActivity", "Sending JSON: " + json);

                // Send the JSON data to the server
                if (writer != null) {
                    new Thread(() -> {
                        try {
                            Log.d("MainActivity", "Sending data to server");
                            writer.println(json);
                            writer.flush();
                            Log.d("MainActivity", "Data sent to server");
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error sending data to server", e);
                        }
                    }).start(); // Ensure this runs in a background thread
                } else {
                    Log.e("MainActivity", "Writer is null, cannot send data");
                }

                runOnUiThread(() -> {
                    if (!satellitesData.isEmpty()) {
                        Map<String, Object> firstSatellite = satellitesData.get(0);
                        pseudorangeText.setText("Pseudo-Range Rate: " + firstSatellite.get("pseudorangeRate"));
                        cn0Text.setText("CN0: " + firstSatellite.get("cn0"));
                        dopplerText.setText("Doppler: " + firstSatellite.get("doppler"));
                    }
                });
            }

            @Override
            protected void onDestroy() {
                super.onDestroy();
                if (socket != null) {
                    try {
                        socket.close();
                        Log.d("MainActivity", "Socket closed");
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error closing socket", e);
                    }
                }
                if (fusedLocationClient != null && locationCallback != null) {
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                    Log.d("MainActivity", "Location updates removed");
                }
            }
        }
