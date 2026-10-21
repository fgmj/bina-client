package br.com.bomgas.bina;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class CallMonitorService extends Service {
    private static final String TAG = "CallMonitorService";
    private static final String CHANNEL_ID = "call_monitor";
    private static volatile boolean running;

    static boolean isRunning(Context context) { return running && hasPhonePermissions(context); }

    static boolean hasPhonePermissions(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean start(Context context) {
        if (!hasPhonePermissions(context)) {
            return false;
        }
        try {
            ContextCompat.startForegroundService(context, new Intent(context, CallMonitorService.class));
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Não foi possível iniciar o monitoramento; abra o aplicativo para tentar novamente", e);
            return false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, createNotification());
        running = true;
        CallOutbox.schedule(this, 0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!hasPhonePermissions(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // CallReceiver is declared in the manifest. Registering it here would duplicate events.
        return START_STICKY;
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Monitoramento de chamadas", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        PendingIntent openApp = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitoramento de chamadas ativo")
                .setContentText("Toque para abrir o Bina")
                .setSmallIcon(R.drawable.baseline_wifi_calling_3_24)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }
}
