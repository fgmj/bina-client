package br.com.bomgas.bina;

import android.app.Application;
import android.util.Log;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Scheduling only: no HTTP or APK processing on the application thread. */
public final class UpdaterApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            Constraints network = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
            WorkManager manager = WorkManager.getInstance(this);
            manager.enqueueUniquePeriodicWork("updater-periodic", ExistingPeriodicWorkPolicy.KEEP,
                    new PeriodicWorkRequest.Builder(UpdateWorker.class, 12, TimeUnit.HOURS)
                            .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
        } catch (RuntimeException e) { Log.e("Updater", "Não foi possível agendar atualização", e); }
    }
    static void checkOnStart(android.content.Context context) {
        try {
            WorkManager.getInstance(context).enqueueUniqueWork("updater-start", ExistingWorkPolicy.KEEP,
                    new OneTimeWorkRequest.Builder(UpdateWorker.class)
                            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
        } catch (RuntimeException e) { Log.e("Updater", "Não foi possível consultar atualização no início", e); }
    }
}
