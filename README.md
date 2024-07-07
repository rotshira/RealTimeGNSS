# Real-Time GNSS Android App

## Overview

The Real-Time GNSS Android App collects GNSS measurements from the device and transmits the data to a specified server for further processing and visualization. It provides real-time GNSS data such as latitude, longitude, altitude, pseudorange rate, CN0, Doppler shift, and computed ECEF (Earth-Centered, Earth-Fixed) coordinates.


<img width="300" alt="image" src="https://imgur.com/HandBa4.png">
## Features

- **Real-Time GNSS Data Collection**: Captures GNSS measurements from the device.
- **Data Transmission**: Sends collected GNSS data to a specified server.
- **Data Display**: Displays latitude, longitude, altitude, pseudorange rate, CN0, Doppler shift, and ECEF coordinates on the app UI.
- **Socket Communication**: Establishes a socket connection to transmit GNSS data to the server.
- **Location Updates**: Uses Android's LocationManager to receive location updates.

## Requirements

- Android device with API level 24 or higher.
- Permissions for accessing fine location (`ACCESS_FINE_LOCATION`).
- A server to receive and process the GNSS data.

### Permissions

Ensure the following permissions are added to your `AndroidManifest.xml`:

```
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" /> 
```

### Dependencies
Add the following dependencies to your build.gradle file:
```
dependencies {
    implementation 'com.google.code.gson:gson:2.8.8'
    implementation 'androidx.appcompat:appcompat:1.2.0'
}
```

### Usage
1. Configure Server IP and Port:
Replace `SERVER_IP` and `SERVER_PORT` with the IP address and port number of your server in `MainActivity.java`:
```
    private static final String SERVER_IP = "192.168.43.53"; // Replace with your server's IP address
    private static final int SERVER_PORT = 5001; // Replace with your server's port number
```
2. Start the App: Build and run the app on an Android device.
3. Permissions Request:
   The app will request permission to access the device's location. Ensure you grant this permission.
4. View Data:
   The app will display real-time GNSS data on the UI. It will also transmit this data to the specified server.

## Code Overview

### MainActivity.java

The `MainActivity` class handles the GNSS data collection, processing, and transmission. It implements the `LocationListener` interface to receive location updates.

- **GNSS Data Collection:**
  Uses `LocationManager` to request location updates and register `GnssMeasurementsCallback` to receive GNSS measurements.

- **Socket Communication:**
  Establishes a socket connection to the server and sends GNSS data as JSON strings.

- **UI Update:**
  Displays the received GNSS data on the app's UI elements.

- **Data Processing:**
  Converts latitude, longitude, and altitude to ECEF coordinates.

### Example Code Snippets

#### Registering GNSS Measurements Callback
```
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

```
### Sending Data to the Server
```Java
private void sendCombinedData() {
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

    Gson gson = new Gson();
    String json = gson.toJson(combinedData);
    Log.d("MainActivity", "Sending JSON: " + json);

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

```




