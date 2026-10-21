package br.com.bomgas.bina;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import androidx.core.content.FileProvider;
import java.io.File;

/** Only an explicit notification tap can launch the package installer. */
public final class UpdateInstallActivity extends Activity {
    private boolean busy;
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); }
    @Override protected void onResume() {
        super.onResume();
        if (busy) return;
        busy = true;
        SharedPreferences state = getSharedPreferences(UpdateWorker.PREFS, MODE_PRIVATE);
        String key = getIntent().getStringExtra("key");
        if (key == null || !key.equals(state.getString("key", ""))
                || !key.equals(UpdatePolicy.endpoint(new AppSettings(this).active()))) {
            showError("A atualização pertence a outro ambiente. Aguarde uma nova consulta."); return;
        }
        String error = state.getString("error", null);
        if (error != null) { showError(error); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this).setMessage("Permita que o Bina instale esta atualização. Depois, retorne ao aplicativo.")
                    .setPositiveButton("Abrir configuração", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                            busy = false;
                        } catch (RuntimeException e) { showError("Não foi possível abrir a permissão de instalação: " + e.getMessage()); }
                    }).setNegativeButton("Cancelar", (dialog, which) -> finish()).setOnCancelListener(dialog -> finish()).show();
            return;
        }
        File apk = new File(getFilesDir(), "updates/update.apk");
        long code = state.getLong("code", 0);
        String hash = state.getString("hash", "");
        new Thread(() -> {
            try {
                UpdateApk.validate(this, apk, code, hash);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    try {
                        if (!key.equals(UpdatePolicy.endpoint(new AppSettings(this).active()))) throw new IllegalStateException("Ambiente ativo alterado");
                        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", apk);
                        startActivity(new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                        finish();
                    } catch (RuntimeException e) { showError("Não foi possível abrir o instalador: " + e.getMessage()); }
                });
            } catch (Exception e) { runOnUiThread(() -> showError(e.getMessage())); }
        }, "updater-install").start();
    }
    private void showError(String message) {
        Log.e("Updater", message);
        getSharedPreferences(UpdateWorker.PREFS, MODE_PRIVATE).edit().putString("lastInstallError", message).apply();
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle("Não foi possível atualizar").setMessage(message)
                .setPositiveButton("Fechar", (dialog, which) -> finish()).setOnCancelListener(dialog -> finish()).show();
    }
}
