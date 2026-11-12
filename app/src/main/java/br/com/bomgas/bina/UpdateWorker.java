package br.com.bomgas.bina;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.*;
import com.google.gson.*;
import java.io.*;

public final class UpdateWorker extends Worker {
    static final String PREFS = "UpdaterState";
    static final String CHANNEL = "app_updates";
    static final int NOTIFICATION = 6006;
    static final Object LOCK = new Object();
    public UpdateWorker(Context context, WorkerParameters parameters) { super(context, parameters); }
    @Override public Result doWork() {
        synchronized (LOCK) {
            try { return check(); }
            catch (Exception e) {
                Log.w("Updater", "Consulta/download de atualização falhou; nova tentativa com backoff", e);
                return Result.retry();
            }
        }
    }
    private Result check() throws Exception {
        Context context = getApplicationContext();
        EnvironmentConfig environment = new AppSettings(context).active();
        String key = UpdatePolicy.endpoint(environment);
        SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JsonObject metadata = UpdateClient.fetch(environment);
        if (metadata == null) return Result.success();
        long code = UpdateClient.code(metadata);
        long installed = UpdateApk.version(context.getPackageManager().getPackageInfo(context.getPackageName(), 0));
        if (UpdatePolicy.decide(installed, code, state.getLong(key + "/offered", 0), false) != UpdatePolicy.Decision.UPDATE) return Result.success();
        if (!notificationsAllowed(context)) return Result.success();
        String name = metadata.get("versionName").getAsString();
        String hash = metadata.get("sha256").getAsString();
        File apk;
        try { apk = UpdateClient.download(context, environment, metadata, this::isStopped); }
        catch (UpdateApk.SignatureMismatch e) {
            Log.e("Updater", e.getMessage(), e);
            return offer(context, state, key, code, name, null, hash, e.getMessage());
        }
        return offer(context, state, key, code, name, apk, hash, null);
    }
    private Result offer(Context context, SharedPreferences state, String key, long code, String name, File apk, String hash, String error) throws IOException {
        if (!key.equals(UpdatePolicy.endpoint(new AppSettings(context).active())) || !notificationsAllowed(context)) return Result.success();
        Intent intent = new Intent(context, UpdateInstallActivity.class).putExtra("key", key);
        PendingIntent action = PendingIntent.getActivity(context, NOTIFICATION, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!state.edit().putString("key", key).putLong("code", code).putString("hash", hash)
                .putString("error", error).putString("file", apk == null ? "" : apk.getName())
                .putLong(key + "/offered", code).commit()) throw new IOException("Não foi possível persistir oferta");
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Atualizações do aplicativo", NotificationManager.IMPORTANCE_LOW));
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_bina).setContentTitle("Bina " + name)
                .setContentText(error == null ? "Atualização pronta para instalar" : error)
                .setContentIntent(action).setAutoCancel(true).setOnlyAlertOnce(true).setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, error == null ? "Instalar" : "Ver problema", action);
        manager.notify(NOTIFICATION, notification.build());
        return Result.success();
    }
    private static boolean notificationsAllowed(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = context.getSystemService(NotificationManager.class).getNotificationChannel(CHANNEL);
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        }
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }
}
