package br.com.bomgas.bina;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Optional tracking (opt-in): LocationManager delivers a fix about every five minutes to
 * LocationReceiver through a PendingIntent. Independent of call capture and its outbox.
 */
final class LocationTracking {
    static final long INTERVAL_MS = 5 * 60_000L;
    private static final String PREFS = "location-tracking";
    private static final String PERIODIC = "location-upload-periodic";
    private static final String TAG = "LocationTracking";
    private static LocationQueue queue;

    private LocationTracking() {}

    static synchronized LocationQueue queue(Context context) throws IOException {
        if (queue == null) queue = new LocationQueue(new File(context.getNoBackupFilesDir(), "locations-v1.json"));
        return queue;
    }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context) { return prefs(context).getBoolean("enabled", false); }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("enabled", enabled).apply();
        ensure(context);
    }

    static boolean foregroundPermission(Context context) {
        return granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
                || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /** Before Android 10 foreground permission already covers background access. */
    static boolean backgroundPermission(Context context) {
        return Build.VERSION.SDK_INT < 29 ? foregroundPermission(context)
                : granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    static boolean needsBackgroundStep(Context context) {
        return Build.VERSION.SDK_INT >= 29 && foregroundPermission(context) && !backgroundPermission(context);
    }

    private static boolean granted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Balanced accuracy first: fused (Android 12+), then network, GPS only as a last resort. */
    static String provider(LocationManager manager) {
        if (manager == null) return null;
        List<String> enabled = manager.getProviders(true);
        if (Build.VERSION.SDK_INT >= 31 && enabled.contains(LocationManager.FUSED_PROVIDER))
            return LocationManager.FUSED_PROVIDER;
        if (enabled.contains(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER;
        if (enabled.contains(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER;
        return null;
    }

    private static PendingIntent updates(Context context) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(context, 0, new Intent(context, LocationReceiver.class), flags);
    }

    /** Idempotent: safe on every app start, resume, boot and settings change. */
    @SuppressLint("MissingPermission") // checked by foregroundPermission(); SecurityException is caught below
    static void ensure(Context context) {
        Context app = context.getApplicationContext();
        try {
            LocationManager manager = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
            WorkManager work = WorkManager.getInstance(app);
            if (!enabled(app) || !foregroundPermission(app)) {
                if (manager != null) manager.removeUpdates(updates(app));
                if (!enabled(app)) {
                    work.cancelUniqueWork(PERIODIC);
                    LocationUploadWorker.enqueue(app); // flush what is already stored
                }
                return;
            }
            String provider = provider(manager);
            if (provider != null) manager.requestLocationUpdates(provider, INTERVAL_MS, 0f, updates(app));
            work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                    new PeriodicWorkRequest.Builder(LocationUploadWorker.class, 15, TimeUnit.MINUTES)
                            .setConstraints(new Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
        } catch (RuntimeException e) {
            // SecurityException, IllegalArgumentException or WorkManager failures must never reach call capture.
            Log.w(TAG, "Não foi possível ajustar o rastreamento", e);
        }
    }

    static StatusSummary summary(Context context) {
        TrackingStatus.Inputs in = new TrackingStatus.Inputs();
        in.enabled = enabled(context);
        if (!in.enabled) return null;
        in.foregroundPermission = foregroundPermission(context);
        in.backgroundPermission = backgroundPermission(context);
        in.providerAvailable = provider((LocationManager) context.getSystemService(Context.LOCATION_SERVICE)) != null;
        try {
            LocationQueue points = queue(context);
            in.pending = points.size();
            in.serverUnsupported = points.unsupported();
            long last = points.lastAcceptedAt();
            in.lastPointTime = last == 0 ? null : new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(last));
        } catch (IOException e) {
            in.lastPointTime = null;
        }
        return TrackingStatus.summarize(in);
    }
}
