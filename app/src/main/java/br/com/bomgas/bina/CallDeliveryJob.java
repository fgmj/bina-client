package br.com.bomgas.bina;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallDeliveryJob extends JobService {
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private final Set<JobParameters> stopped = ConcurrentHashMap.newKeySet();

    @Override public boolean onStartJob(JobParameters parameters) {
        stopped.remove(parameters);
        NETWORK.execute(() -> {
            boolean retry = false;
            try {
                DurableCalls calls = CallOutbox.get(this);
                ServerApi api = new ServerApi();
                calls.deliver(System.currentTimeMillis(), (url, json) -> {
                    if (stopped.contains(parameters)) throw new java.io.IOException("Entrega interrompida pelo Android");
                    api.sendQueuedEvent(url, json);
                });
                retry = !calls.pending().isEmpty();
            } catch (Exception e) {
                retry = true;
                Log.e("CallDeliveryJob", "Entrega pendente; será tentada novamente", e);
            } finally {
                if (!stopped.remove(parameters)) jobFinished(parameters, retry);
            }
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        stopped.add(parameters);
        return true;
    }
}
