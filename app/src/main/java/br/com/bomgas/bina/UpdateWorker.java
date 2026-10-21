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
import okhttp3.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

public final class UpdateWorker extends Worker {
    static final String PREFS = "UpdaterState";
    static final String CHANNEL = "app_updates";
    static final int NOTIFICATION = 6006;
    private static final Object LOCK = new Object();
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(10, TimeUnit.MINUTES)
            .followRedirects(false).followSslRedirects(false).build();

    public UpdateWorker(Context context, WorkerParameters parameters) { super(context, parameters); }
    @Override public Result doWork() {
        synchronized (LOCK) {
            File partial = new File(getApplicationContext().getFilesDir(), "updates/download.part");
            try { return check(partial); }
            catch (Exception e) {
                Log.w("Updater", "Consulta/download de atualização falhou; nova tentativa com backoff", e);
                return Result.retry();
            } finally { if (partial.exists()) partial.delete(); }
        }
    }
    private Result check(File partial) throws Exception {
        Context context = getApplicationContext();
        EnvironmentConfig environment = new AppSettings(context).active();
        String key = UpdatePolicy.endpoint(environment);
        SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JsonObject metadata;
        try (Response response = http.newCall(new Request.Builder().url(key).build()).execute()) {
            // A channel without a published updater is normal, including production.
            if (response.code() == 404 || response.code() == 204) return Result.success();
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            if (response.body().contentLength() > 65536) throw new IOException("Metadados grandes demais");
            try (InputStream stream = response.body().byteStream()) {
                byte[] bytes = readLimited(stream, 65536);
                metadata = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        long code = metadata.get("versionCode").getAsBigDecimal().longValueExact();
        long installed = UpdateApk.version(context.getPackageManager().getPackageInfo(context.getPackageName(), 0));
        if (!UpdatePolicy.shouldOffer(installed, code, state.getLong(key + "/offered", 0))) return Result.success();
        if (!notificationsAllowed(context)) return Result.success();
        String name = metadata.get("versionName").getAsString();
        String hash = metadata.get("sha256").getAsString();
        if (!hash.matches("[a-fA-F0-9]{64}")) throw new IOException("SHA-256 inválido");
        HttpUrl origin = HttpUrl.get(environment.baseUrl());
        HttpUrl url = origin.resolve(metadata.get("url").getAsString());
        if (url == null || !url.scheme().equals(origin.scheme()) || !url.host().equals(origin.host()) || url.port() != origin.port()
                || !url.username().isEmpty() || !url.password().isEmpty()) throw new IOException("URL de APK fora do servidor ativo");
        if (!partial.getParentFile().isDirectory() && !partial.getParentFile().mkdirs()) throw new IOException("Storage indisponível");
        try (Response download = http.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!download.isSuccessful() || download.body() == null) throw new IOException("APK HTTP " + download.code());
            try (InputStream input = download.body().byteStream(); FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[32768]; int count; long total = 0;
                while ((count = input.read(buffer)) != -1) {
                    if (isStopped()) return Result.retry();
                    if ((total += count) > 256L * 1024 * 1024) throw new IOException("APK grande demais");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
        }
        try { UpdateApk.validate(context, partial, code, hash); }
        catch (UpdateApk.SignatureMismatch e) {
            Log.e("Updater", e.getMessage(), e);
            // Persistent diagnostic remains accessible by tapping the discreet notification.
            return offer(context, state, key, code, name, null, hash, e.getMessage());
        }
        if (!key.equals(UpdatePolicy.endpoint(new AppSettings(context).active()))) return Result.success();
        File apk = new File(partial.getParentFile(), "update.apk");
        if (apk.exists() && !apk.delete()) throw new IOException("Não foi possível substituir APK");
        if (!partial.renameTo(apk)) throw new IOException("Não foi possível concluir APK");
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
                .setSmallIcon(R.drawable.baseline_wifi_calling_3_24).setContentTitle("Bina " + name)
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
    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > limit) throw new IOException("Metadados grandes demais");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
}
