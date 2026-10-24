package ivica.weatherapplication;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Uses current Play services directly, without the obsolete location utility. */
final class AndroidCityLocation implements StartupLocationController.Gateway {
    private final Activity activity;
    private final Runnable requestPermissions;
    private final FusedLocationProviderClient locations;
    private final ExecutorService geocoding = Executors.newSingleThreadExecutor();
    private volatile boolean closed;

    AndroidCityLocation(Activity activity, Runnable requestPermissions) {
        this.activity = activity;
        this.requestPermissions = requestPermissions;
        locations = LocationServices.getFusedLocationProviderClient(activity);
    }

    @Override public boolean fineGranted() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override public boolean coarseGranted() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void requestPermissions() { requestPermissions.run(); }

    @Override public void findCity(StartupLocationController.Result result) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            result.failed();
            return;
        }
        try {
            locations.getLastLocation()
                    .addOnSuccessListener(activity, location -> geocode(location, result))
                    .addOnFailureListener(activity, error -> deliver(result, null));
        } catch (SecurityException revoked) {
            deliver(result, null);
        }
    }

    private void geocode(Location location, StartupLocationController.Result result) {
        if (closed) return;
        if (location == null || !Geocoder.isPresent()) {
            deliver(result, null);
            return;
        }
        Geocoder geocoder = new Geocoder(activity.getApplicationContext(), Locale.getDefault());
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1,
                        new Geocoder.GeocodeListener() {
                    @Override public void onGeocode(List<Address> addresses) {
                        deliver(result, city(addresses));
                    }
                    @Override public void onError(String errorMessage) { deliver(result, null); }
                });
            } catch (IllegalArgumentException | SecurityException unavailable) {
                deliver(result, null);
            }
        } else {
            // The older API is blocking, so never run it on the UI thread.
            geocoding.execute(() -> {
                try {
                    deliver(result, city(geocoder.getFromLocation(
                            location.getLatitude(), location.getLongitude(), 1)));
                } catch (IOException | IllegalArgumentException | SecurityException unavailable) {
                    deliver(result, null);
                }
            });
        }
    }

    private static String city(List<Address> addresses) {
        return addresses == null || addresses.isEmpty() || addresses.get(0) == null
                ? null : addresses.get(0).getLocality();
    }

    private void deliver(StartupLocationController.Result result, String city) {
        activity.runOnUiThread(() -> {
            if (!closed) result.city(city);
        });
    }

    void close() {
        closed = true;
        geocoding.shutdownNow();
    }
}
