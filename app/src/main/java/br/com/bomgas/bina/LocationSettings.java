package br.com.bomgas.bina;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.lang.ref.WeakReference;

/** "Localização" section of the settings dialog and its two-step permission flow (Android 10+). */
final class LocationSettings {
    static final int REQUEST_FOREGROUND = 7101;
    static final int REQUEST_BACKGROUND = 7102;
    private static WeakReference<View> boundForm = new WeakReference<>(null);

    private LocationSettings() {}

    static void bind(AppCompatActivity activity, View form) {
        boundForm = new WeakReference<>(form);
        MaterialSwitch toggle = form.findViewById(R.id.locationSwitch);
        Button background = form.findViewById(R.id.locationBackground);
        refresh(activity, form);
        toggle.setOnCheckedChangeListener((button, on) -> {
            if (on == LocationTracking.enabled(activity)) return;
            LocationTracking.setEnabled(activity, on);
            if (on) continueFlow(activity);
            refresh(activity, form);
        });
        background.setOnClickListener(v -> askBackground(activity));
    }

    private static void continueFlow(AppCompatActivity activity) {
        if (!LocationTracking.foregroundPermission(activity)) {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_FOREGROUND);
        } else if (LocationTracking.needsBackgroundStep(activity)) {
            askBackground(activity);
        }
    }

    private static void askBackground(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT < 29) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Enviar localização com a tela desligada")
                .setMessage(Build.VERSION.SDK_INT >= 30
                        ? "Na próxima tela, escolha \"Permitir o tempo todo\". Sem isso, a posição só é enviada com o Bina aberto."
                        : "No próximo aviso, escolha \"Permitir o tempo todo\". Sem isso, a posição só é enviada com o Bina aberto.")
                .setPositiveButton("Continuar", (dialog, which) -> ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND))
                .setNegativeButton("Agora não", null)
                .show();
    }

    /** Returns true when the request belonged to the location flow. */
    static boolean onPermissionResult(AppCompatActivity activity, int requestCode) {
        if (requestCode == REQUEST_FOREGROUND) {
            if (LocationTracking.foregroundPermission(activity)) {
                LocationTracking.ensure(activity);
                if (LocationTracking.needsBackgroundStep(activity)) askBackground(activity);
            } else {
                LocationTracking.setEnabled(activity, false);
                Toast.makeText(activity, "Sem permissão de localização, o envio ficou desligado.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_BACKGROUND) {
            LocationTracking.ensure(activity);
            if (!LocationTracking.backgroundPermission(activity) && Build.VERSION.SDK_INT >= 30
                    && !ActivityCompat.shouldShowRequestPermissionRationale(activity,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                // The system stopped asking (denied repeatedly): lead the operator to the app settings.
                // A single "not now" is respected and the section keeps the "Permitir" button.
                Toast.makeText(activity, "Em Permissões › Localização, escolha \"Permitir o tempo todo\".",
                        Toast.LENGTH_LONG).show();
                try {
                    activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + activity.getPackageName())));
                } catch (ActivityNotFoundException ignored) { }
            }
        } else {
            return false;
        }
        View form = boundForm.get();
        if (form != null && form.isAttachedToWindow()) refresh(activity, form);
        return true;
    }

    private static void refresh(AppCompatActivity activity, View form) {
        boolean enabled = LocationTracking.enabled(activity);
        MaterialSwitch toggle = form.findViewById(R.id.locationSwitch);
        if (toggle.isChecked() != enabled) toggle.setChecked(enabled);
        TextView info = form.findViewById(R.id.locationInfo);
        StatusSummary summary = LocationTracking.summary(activity);
        String help = activity.getString(R.string.settings_location_help);
        info.setText(summary == null ? help : help + "\n\nSituação: " + summary.label);
        form.findViewById(R.id.locationBackground).setVisibility(
                enabled && LocationTracking.needsBackgroundStep(activity) ? View.VISIBLE : View.GONE);
    }
}
