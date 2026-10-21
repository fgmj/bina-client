package br.com.bomgas.bina;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-wide owner; durable state is excluded from backup/transfer to another device. */
final class CallOutbox {
    static final ExecutorService RECORDING = Executors.newSingleThreadExecutor();
    private static DurableCalls instance;
    private static final int JOB_ID = 4001;

    static synchronized DurableCalls get(Context context) throws IOException {
        if (instance == null) instance = new DurableCalls(new File(context.getNoBackupFilesDir(), "calls-v1.json"));
        return instance;
    }

    static synchronized void schedule(Context context, long delay) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        // Safety net for a process dying between committing an event and scheduling its immediate job.
        // Also closes the race where a new event arrives while an empty immediate job is finishing.
        if (scheduler.getPendingJob(JOB_ID + 1) == null) {
            scheduler.schedule(new JobInfo.Builder(JOB_ID + 1, new ComponentName(context, CallDeliveryJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                    .setPeriodic(15 * 60 * 1000L).build());
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return;
        int result = scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, CallDeliveryJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
                .setMinimumLatency(Math.max(0, delay))
                .setBackoffCriteria(15000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
        if (result != JobScheduler.RESULT_SUCCESS)
            android.util.Log.e("CallOutbox", "Android recusou o agendamento de entrega");
    }

    static String status(Context context, EnvironmentConfig active, boolean detailed) {
        String health = context.getSharedPreferences("outbox-health", Context.MODE_PRIVATE).getString("error", "");
        try {
            List<DurableCalls.Pending> pending = get(context).pending();
            if (pending.isEmpty()) return health.isEmpty() ? "Fila de chamadas: sem pendências" : health;
            int here = 0;
            Map<String, Integer> counts = new LinkedHashMap<>();
            Map<String, DurableCalls.Pending> heads = new LinkedHashMap<>();
            for (DurableCalls.Pending item : pending) {
                if (item.url.equals(active.eventsUrl())) here++;
                counts.put(item.url, counts.containsKey(item.url) ? counts.get(item.url) + 1 : 1);
                if (!heads.containsKey(item.url)) heads.put(item.url, item);
            }
            String summary = "Fila: " + pending.size() + " pendência(s), " + here + " neste ambiente.";
            if (!detailed) return (health.isEmpty() ? "" : "Falha ao preservar chamada. ") + summary
                    + (heads.values().stream().anyMatch(item -> !item.error.isEmpty()) ? " Há envios sem confirmação." : "")
                    + " Toque para detalhes.";
            StringBuilder details = new StringBuilder();
            for (DurableCalls.Pending item : heads.values()) {
                    details.append("\n\n").append(item.url).append(" · ").append(counts.get(item.url))
                            .append(" pendência(s) · ").append(item.attempts).append(" tentativa(s) da primeira pendência");
                    if (!item.error.isEmpty()) details.append(" · ").append(item.error)
                            .append(" · próxima tentativa após ")
                            .append(java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(item.nextAttempt)));
            }
            return (health.isEmpty() ? "" : health + "\n") + summary
                    + " Destinos originais preservados." + details;
        } catch (IOException e) { return "Falha no armazenamento da fila: " + e.getMessage(); }
    }
}
