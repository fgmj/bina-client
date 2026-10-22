package br.com.bomgas.bina;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Android observations and explicit user recovery actions; no capture is owned by this UI. */
final class MonitoringRecovery {
    static MonitoringState state(Context context) {
        boolean notifications = NotificationManagerCompat.from(context).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = context.getSystemService(NotificationManager.class)
                    .getNotificationChannel(CallMonitorService.CHANNEL_ID);
            notifications &= channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        boolean restricted = Build.VERSION.SDK_INT >= 28 && manager != null && manager.isBackgroundRestricted();
        return MonitoringState.assess(granted(context, Manifest.permission.READ_PHONE_STATE),
                granted(context, Manifest.permission.READ_CALL_LOG), CallMonitorService.isRunning(context),
                notifications, restricted);
    }

    private static boolean granted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    static void show(AppCompatActivity activity) {
        MonitoringState state = state(activity);
        String error = activity.getSharedPreferences("monitor-health", Context.MODE_PRIVATE).getString("error", "");
        new AlertDialog.Builder(activity).setTitle(state.title)
                .setMessage(state.details + (error.isEmpty() ? "" : "\n\nÚltima falha: " + error))
                .setPositiveButton(state.canStart ? "Retomar serviço" : "Permissões", (dialog, which) -> {
                    if (!state.canStart) {
                        settings(activity);
                        return;
                    }
                    boolean requested = CallMonitorService.start(activity);
                    CallOutbox.schedule(activity, 0);
                    Toast.makeText(activity, requested
                            ? "Retomada solicitada. Confira o estado e a fila."
                            : "Confira as permissões e restrições nas configurações.", Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("Configurações", (dialog, which) -> settings(activity))
                .setNegativeButton("Fechar", null).show();
    }

    private static void settings(AppCompatActivity activity) {
        new AlertDialog.Builder(activity).setTitle("Revisar configurações")
                .setItems(new String[]{"Permissões do aplicativo", "Notificações do monitoramento", "Bateria e segundo plano"},
                        (dialog, which) -> {
                            Intent intent;
                            if (which == 1 && Build.VERSION.SDK_INT >= 26) {
                                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                            } else if (which == 2) {
                                intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            } else {
                                intent = appSettings(activity);
                            }
                            try { activity.startActivity(intent); }
                            catch (ActivityNotFoundException e) {
                                try { activity.startActivity(appSettings(activity)); }
                                catch (ActivityNotFoundException unavailable) {
                                    Toast.makeText(activity, "Abra as configurações do Bina no Android.", Toast.LENGTH_LONG).show();
                                }
                            }
                        }).setNegativeButton("Voltar", (dialog, which) -> show(activity)).show();
    }

    private static Intent appSettings(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.getPackageName()));
    }
}
