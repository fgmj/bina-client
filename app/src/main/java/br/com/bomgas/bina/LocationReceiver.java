package br.com.bomgas.bina;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Stores each periodic fix in the durable LocationQueue and asks for an upload. */
public class LocationReceiver extends BroadcastReceiver {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !LocationTracking.enabled(context)) return;
        Location location = intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED);
        if (location == null) return;
        Context app = context.getApplicationContext();
        PendingResult result = goAsync();
        WRITER.execute(() -> {
            try {
                LocationQueue.Point point = LocationQueue.Point.of(location.getLatitude(), location.getLongitude(),
                        location.hasAccuracy() ? location.getAccuracy() : -1f, location.getProvider(),
                        location.getTime(), battery(app));
                if (LocationTracking.queue(app).offer(point)) LocationUploadWorker.enqueue(app);
            } catch (Exception e) {
                Log.w("LocationReceiver", "Falha ao guardar localização", e);
            } finally {
                result.finish();
            }
        });
    }

    private static int battery(Context context) {
        try {
            BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            return manager == null ? -1 : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
