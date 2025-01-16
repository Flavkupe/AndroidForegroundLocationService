package com.flavkupe.foregroundlocationservice;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import java.util.ArrayList;

public class LocationService extends Service {

    private static LocationService instance;

    private static class CoordinateList {
        public ArrayList<Coordinates> Coordinates = new ArrayList<>();
    }

    private static class Coordinates {

        public Coordinates(double lat, double lon) {
            Latitude = lat;
            Longitude = lon;
            Timestamp = System.currentTimeMillis();
        }

        public double Longitude;
        public double Latitude;

        // Unix timestamp in milliseconds
        public long Timestamp;
    }

    private static final String CHANNEL_ID = "LocationServiceChannel";

    private static final String TAG = "LocationService";

    // How many ms pass before polling location, by default.
    // You can modify this value if you wish.
    private static int LOCATION_INTERVAL = 5000;


    // Minimum meters required to travel before polling for position.
    // You can modify this value if you wish.
    private static int MIN_LOCATION_POLL_METERS = 50;

    // How many items should be accumulated before doubling the poll duration
    // You can modify this value if you wish.
    private static int ITEMS_TO_INTERVAL_CHANGE = 200;

    // Maximum coordinates kept in memory; after this threshold is crossed, no more updates will
    // be done until the stored coordinates are read and the list of locations is cleared.
    // You can modify this value if you wish.
    private static int UPDATE_LIMIT = 1000;

    private int currentInterval = LOCATION_INTERVAL;

    private static Activity mainActivity;

    private boolean isPolling = false;

    private final ArrayList<Coordinates> locations = new ArrayList<>();

    // Call this externally to start this service.
    public static void startLocationService(Activity activity) {
        if (instance != null) {
            return;
        }

        mainActivity = activity;
        activity.startService(new Intent(activity, LocationService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (instance != null) {
            return START_STICKY;
        }

        instance = this;
        createNotificationChannel();
        startForeground(1, getNotification(intent));


        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopForeground(true);
        stopSelf();
    }

    // Call this externally from your app once the screen is unlocked in order
    // to get a json string containing the list of polled locations.
    public static String getLocations() {
        if (instance == null) {
            Log.v(TAG, "Cannot get locations: instance is null");
            return null;
        }

        Gson gson = new Gson();

        CoordinateList list = new CoordinateList();
        list.Coordinates = instance.locations;

        String serialized = gson.toJson(list);
        return serialized;
    }

    private void createNotificationChannel() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_ID,
                        "Location Service Channel",
                        NotificationManager.IMPORTANCE_HIGH);
                NotificationManager manager = getSystemService(NotificationManager.class);
                manager.createNotificationChannel(serviceChannel);
            } else {
            }
        } catch (Exception error) {
            Log.v(TAG, "Exception in createNotificationChannel:" + error.getMessage());
        }
    }

    private Notification getNotification(Intent intent) {
        try {
            // Intent notificationIntent = new Intent(this, mainActivity.getClass());
            Intent notificationIntent = intent;
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Location Service")
                    .setContentText("Getting location updates")
                    .setSmallIcon(R.drawable.city)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
            }
            return builder.build();
        } catch (Exception error) {
            return null;
        }
    }

    private LocationManager locationManager;

    // Call this externally from your app right when the screen locks
    // (or whenever you want to start polling for locations). This will
    // clear any previously polled locations.
    public static boolean startPollingLocation() {
        if (instance == null) {
            return false;
        }

        return instance.startPollingLocationInternal();
    }

    // Call this externally from your app once the screen is unlocked in order
    // to stop polling for locations.
    public static boolean stopPollingLocation() {
        if (instance == null) {
            return false;
        }

        return instance.stopPollingLocationInternal();
    }

    private boolean startPollingLocationInternal() {
        if (instance == null) {
            return false;
        }

        if (isPolling) {
            return true;
        }

        try {
            instance.locations.clear();
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }

            currentInterval = LOCATION_INTERVAL;
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    currentInterval, MIN_LOCATION_POLL_METERS, locationListener, Looper.getMainLooper());

            isPolling = true;
            return true;
        } catch (Exception ex) {
            Log.v(TAG, "Failed to start polling: " + ex.getMessage());
            return false;
        }
    }

    private boolean stopPollingLocationInternal() {
        if (!isPolling) {
            return true;
        }

        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }

            locationManager.removeUpdates(locationListener);
            isPolling = false;
            return true;
        } catch (Exception ex) {
            Log.v(TAG, "Failed to stop polling: " + ex.getMessage());
            return false;
        }
    }

    private void addCoordinate(Coordinates coordinates) {
        locations.add(coordinates);
        updateIntervals();
    }

    private void updateIntervals() {
        if (!isPolling) {
            return;
        }
        int size = locations.size();
        if (size >= UPDATE_LIMIT) {
            isPolling = false;
            Log.v(TAG, "Reached limit; stopped polling!");
            locationManager.removeUpdates(locationListener);
            return;
        }

        if (size % ITEMS_TO_INTERVAL_CHANGE == 0) {
            currentInterval *= 2;
            changeInterval(currentInterval);
        }
    }

    private void changeInterval(int intervalMS) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // remove the updates and add them back to reset interval
        locationManager.removeUpdates(locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                intervalMS, MIN_LOCATION_POLL_METERS, locationListener, Looper.getMainLooper());
        return;
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (instance == null) {
                return;
            }

            instance.addCoordinate(new Coordinates(location.getLatitude(), location.getLongitude()));
        }
        @Override
        public void onProviderEnabled(String provider) {}
        @Override
        public void onProviderDisabled(String provider) {}
    };
}

