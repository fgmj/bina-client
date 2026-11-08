package br.com.bomgas.bina;

import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import com.google.gson.JsonObject;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Small UI adapter; telephony remains owned by the existing receiver and service. */
final class SettingsController {
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private final AppCompatActivity activity;
    private final AppSettings settings;
    private final ServerApi api = new ServerApi();
    private final Consumer<String> log;
    private final Runnable render;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1500); }
    };

    SettingsController(AppCompatActivity activity, Consumer<String> log, Runnable render) {
        this.activity = activity;
        this.log = log;
        this.render = render;
        settings = new AppSettings(activity);
        refreshStatus();
    }

    void start() { handler.removeCallbacks(refresh); handler.post(refresh); syncName(); }
    void stop() { handler.removeCallbacks(refresh); }

    /** Presentation lives in MonitorScreen; this keeps the original refresh points. */
    private void refreshStatus() { render.run(); }

    void syncNameNow() { syncName(); }

    void testActive() { test(settings.active(), null); }

    void showQueue() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Envios de chamadas")
                .setMessage(CallOutbox.status(activity, settings.active(), true))
                .setPositiveButton("Enviar agora", (dialog, which) -> {
                    CallOutbox.schedule(activity, 0);
                    log.accept("Envio da fila solicitado");
                })
                .setNegativeButton("Fechar", null).show();
    }

    void showServer() {
        EnvironmentConfig active = settings.active();
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Servidor")
                .setMessage("Ambiente ativo: " + active.label() + "\n\n" + settings.connection(active))
                .setPositiveButton("Testar conexão", (dialog, which) -> testActive())
                .setNeutralButton("Configurar", (dialog, which) -> showSettings())
                .setNegativeButton("Fechar", null).show();
    }

    private void syncName() {
        EnvironmentConfig active = settings.active();
        String name = settings.name(active);
        if (!name.isEmpty()) register(active, name, null);
    }

    private void register(EnvironmentConfig destination, String name, Long revision) {
        String token = settings.beginRegistration(destination, name);
        String sourceId = settings.sourceId();
        refreshStatus();
        NETWORK.execute(() -> {
            String message;
            try {
                ServerApi.Registration result = api.register(destination, sourceId, name, revision);
                settings.acceptRegistration(destination, token, result);
                settings.connection(destination, "Último cadastro confirmado pelo servidor");
                message = "Nome sincronizado em " + destination.label() + ": " + result.name
                        + (!result.name.equals(name) ? ". Adotado o nome atual do servidor; edite novamente se desejar." : "");
            } catch (Exception e) {
                settings.connection(destination, "Falha no cadastro: " + e.getMessage());
                message = "Nome salvo no aparelho; cadastro não confirmado em " + destination.label() + ": " + e.getMessage();
            }
            showResult(message, null);
        });
    }

    private void test(EnvironmentConfig destination, TextView feedback) {
        if (feedback != null) feedback.setText("Testando " + destination.label() + "…");
        settings.connection(destination, "Teste de conexão em andamento…");
        refreshStatus();
        NETWORK.execute(() -> {
            String message;
            try {
                api.testConnection(destination);
                message = "Conexão confirmada com " + destination.label();
            } catch (Exception e) {
                message = "Falha na conexão com " + destination.label() + ": " + e.getMessage();
            }
            settings.connection(destination, "Último teste: " + message);
            showResult(message, feedback);
        });
    }

    private void showResult(String message, TextView feedback) {
        activity.runOnUiThread(() -> {
            if (activity.isDestroyed()) return;
            log.accept(message);
            if (feedback != null) feedback.setText(message);
            refreshStatus();
        });
    }

    private void checkUpdates(AlertDialog dialog, Button button, TextView feedback) {
        EnvironmentConfig environment = settings.active();
        button.setEnabled(false);
        button.setText("Verificando…");
        feedback.setText("Verificando atualizações em " + environment.label() + "…");
        // A dedicated task avoids queuing this explicit check behind registration requests.
        new Thread(() -> {
            JsonObject[] metadata = {null};
            UpdatePolicy.Decision decision = UpdatePolicy.check(BuildConfig.VERSION_CODE, 0, true, () -> {
                metadata[0] = UpdateClient.fetch(environment);
                return metadata[0] == null ? null : UpdateClient.code(metadata[0]);
            });
            activity.runOnUiThread(() -> {
                if (!updatesVisible(dialog)) return;
                button.setEnabled(true);
                button.setText("Verificar atualizações");
                if (!sameUpdateEnvironment(environment)) {
                    feedback.setText("Ambiente ativo alterado. Verifique novamente.");
                    return;
                }
                switch (decision) {
                    case CURRENT:
                        feedback.setText("Você está na versão mais recente (" + BuildConfig.VERSION_NAME + ")");
                        break;
                    case UNAVAILABLE:
                        feedback.setText("Nenhuma atualização publicada para este ambiente/canal.");
                        break;
                    case UPDATE:
                        String message = "Nova versão disponível: " + metadata[0].get("versionName").getAsString() + " — instalar agora?";
                        feedback.setText(message);
                        new MaterialAlertDialogBuilder(activity).setMessage(message)
                                .setPositiveButton("Instalar agora", (d, which) -> {
                                    if (updatesVisible(dialog)) downloadUpdate(dialog, button, feedback, environment, metadata[0]);
                                }).setNegativeButton("Agora não", null).show();
                        break;
                    default:
                        feedback.setText("Não foi possível verificar atualizações. Verifique a conexão e o endpoint do ambiente ativo e tente novamente.");
                }
            });
        }, "updater-manual-check").start();
    }

    private boolean updatesVisible(AlertDialog dialog) {
        return dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed();
    }

    private boolean sameUpdateEnvironment(EnvironmentConfig environment) {
        return UpdatePolicy.endpoint(environment).equals(UpdatePolicy.endpoint(settings.active()));
    }

    private void downloadUpdate(AlertDialog dialog, Button button, TextView feedback,
            EnvironmentConfig environment, JsonObject metadata) {
        button.setEnabled(false);
        button.setText("Baixando…");
        feedback.setText("Baixando e validando atualização…");
        Context context = activity.getApplicationContext();
        new Thread(() -> {
            String error = null;
            String key = UpdatePolicy.endpoint(environment);
            try {
                synchronized (UpdateWorker.LOCK) {
                    if (!sameUpdateEnvironment(environment)) throw new IllegalStateException("Ambiente ativo alterado");
                    UpdateClient.download(context, environment, metadata, () -> false);
                    if (!sameUpdateEnvironment(environment)) throw new IllegalStateException("Ambiente ativo alterado");
                    SharedPreferences state = context.getSharedPreferences(UpdateWorker.PREFS, Context.MODE_PRIVATE);
                    long code = UpdateClient.code(metadata);
                    if (!state.edit().putString("key", key).putLong("code", code)
                            .putString("hash", metadata.get("sha256").getAsString()).remove("error")
                            .putString("file", "update.apk")
                            .putLong(key + "/offered", Math.max(code, state.getLong(key + "/offered", 0)))
                            .commit()) throw new java.io.IOException("Não foi possível salvar a atualização");
                }
            } catch (Exception e) { error = "Não foi possível baixar/instalar a atualização: " + e.getMessage(); }
            final String result = error;
            activity.runOnUiThread(() -> {
                if (!updatesVisible(dialog)) return;
                button.setEnabled(true);
                button.setText("Verificar atualizações");
                if (result != null) { feedback.setText(result); return; }
                if (!sameUpdateEnvironment(environment)) { feedback.setText("Ambiente ativo alterado. Verifique novamente."); return; }
                feedback.setText("Atualização validada. Abrindo instalador…");
                try {
                    activity.startActivity(new Intent(activity, UpdateInstallActivity.class).putExtra("key", key));
                } catch (RuntimeException e) {
                    feedback.setText("Não foi possível abrir o instalador. Tente novamente.");
                }
            });
        }, "updater-manual-download").start();
    }

    private EnvironmentConfig selected(RadioGroup choice, EditText address, EditText port) {
        return choice.getCheckedRadioButtonId() == R.id.chooseLocal
                ? EnvironmentConfig.local(address.getText().toString(), port.getText().toString())
                : EnvironmentConfig.production();
    }

    void showSettings() {
        View form = activity.getLayoutInflater().inflate(R.layout.dialog_settings, null);
        RadioGroup choice = form.findViewById(R.id.environmentChoice);
        EditText address = form.findViewById(R.id.localAddress);
        EditText port = form.findViewById(R.id.localPort);
        EditText name = form.findViewById(R.id.deviceName);
        TextView feedback = form.findViewById(R.id.settingsFeedback);
        EnvironmentConfig active = settings.active();
        String[] editingBase = {active.baseUrl()};
        Long[] editingRevision = {settings.revision(active)};
        address.setText(settings.localAddress());
        port.setText(settings.localPort());
        name.setText(settings.name(active));
        choice.check(active.local ? R.id.chooseLocal : R.id.chooseProduction);
        View localFields = form.findViewById(R.id.localFields);
        address.setEnabled(active.local);
        port.setEnabled(active.local);
        localFields.setVisibility(active.local ? View.VISIBLE : View.GONE);
        LocationSettings.bind(activity, form);
        choice.setOnCheckedChangeListener((group, checked) -> {
            boolean local = checked == R.id.chooseLocal;
            address.setEnabled(local);
            port.setEnabled(local);
            localFields.setVisibility(local ? View.VISIBLE : View.GONE);
            try {
                EnvironmentConfig editing = selected(choice, address, port);
                name.setText(settings.name(editing));
                editingBase[0] = editing.baseUrl();
                editingRevision[0] = settings.revision(editing);
            }
            catch (IllegalArgumentException ignored) { name.setText(""); }
        });
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle("Configurações")
                .setView(form).setPositiveButton("Salvar", null)
                .setNeutralButton("Testar", null).setNegativeButton("Cancelar", null).create();
        Button updates = form.findViewById(R.id.checkUpdates);
        TextView updateFeedback = form.findViewById(R.id.updateFeedback);
        updates.setOnClickListener(v -> checkUpdates(dialog, updates, updateFeedback));
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try { test(selected(choice, address, port), feedback); }
                catch (IllegalArgumentException e) { feedback.setText(e.getMessage()); }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    EnvironmentConfig destination = selected(choice, address, port);
                    String validName = ServerApi.validateName(name.getText().toString());
                    settings.select(destination);
                    // Use the revision shown when editing began, never a newer background refresh.
                    register(destination, validName, destination.baseUrl().equals(editingBase[0]) ? editingRevision[0] : null);
                    log.accept("Ambiente ativo: " + destination.label() + ". Nome salvo; aguardando cadastro no servidor.");
                    dialog.dismiss();
                } catch (IllegalArgumentException e) { feedback.setText(e.getMessage()); }
            });
        });
        dialog.show();
    }
}
