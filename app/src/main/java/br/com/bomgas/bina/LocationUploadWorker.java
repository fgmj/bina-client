package br.com.bomgas.bina;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends stored points in batches to the active environment. Degrades gracefully while the server
 * lacks POST /api/localizacoes: points stay stored and sending pauses (LocationQueue.canSend).
 */
public final class LocationUploadWorker extends Worker {
    private static final String TAG = "LocationUpload";
    private static final int MAX_BATCHES_PER_RUN = 20;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();

    public LocationUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void enqueue(Context context) {
        try {
            WorkManager.getInstance(context).enqueueUniqueWork("location-upload", ExistingWorkPolicy.KEEP,
                    new OneTimeWorkRequest.Builder(LocationUploadWorker.class)
                            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
        } catch (RuntimeException e) {
            Log.w(TAG, "Não foi possível agendar envio de localização", e);
        }
    }

    @NonNull @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try {
            LocationQueue queue = LocationTracking.queue(context);
            if (!queue.canSend(System.currentTimeMillis())) return Result.success();
            AppSettings settings = new AppSettings(context);
            EnvironmentConfig destination = settings.active();
            String sourceId = settings.sourceId();
            for (int round = 0; round < MAX_BATCHES_PER_RUN && queue.size() > 0; round++) {
                List<LocationQueue.Point> batch = queue.batch(LocationUpload.BATCH);
                List<String> ids = new ArrayList<>();
                for (LocationQueue.Point point : batch) ids.add(point.pointId);
                int code = post(LocationUpload.url(destination), LocationUpload.payload(sourceId,
                        BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, batch));
                switch (LocationUpload.classify(code)) {
                    case SENT:
                        queue.acknowledge(ids);
                        queue.markSupported();
                        note(context, "Último envio aceito (HTTP " + code + ")");
                        break;
                    case UNSUPPORTED:
                        queue.markUnsupported(System.currentTimeMillis());
                        note(context, "Servidor ainda não aceita localização (HTTP " + code + ")");
                        return Result.success();
                    case DROP:
                        queue.acknowledge(ids);
                        note(context, "Lote recusado pelo servidor e descartado (HTTP " + code + ")");
                        break;
                    default:
                        note(context, "Servidor indisponível (HTTP " + code + "); nova tentativa automática");
                        return Result.retry();
                }
            }
            return Result.success();
        } catch (IOException e) {
            note(context, "Sem conexão para enviar localização: " + e.getMessage());
            return Result.retry();
        } catch (RuntimeException e) {
            Log.w(TAG, "Falha inesperada no envio de localização", e);
            return Result.retry();
        }
    }

    private static int post(String url, String json) throws IOException {
        Request request = new Request.Builder().url(url)
                .post(RequestBody.create(json, MediaType.get("application/json; charset=utf-8"))).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            return response.code();
        }
    }

    private static void note(Context context, String message) {
        LocationTracking.prefs(context).edit().putString("lastUpload", message).apply();
    }
}
