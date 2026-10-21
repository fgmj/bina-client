package br.com.bomgas.bina;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1500); }
    };

    SettingsController(AppCompatActivity activity, Consumer<String> log) {
        this.activity = activity;
        this.log = log;
        settings = new AppSettings(activity);
        activity.findViewById(R.id.buttonConfigure).setOnClickListener(v -> showSettings());
        activity.findViewById(R.id.buttonSyncName).setOnClickListener(v -> syncName());
        activity.findViewById(R.id.buttonTest).setOnClickListener(v -> test(settings.active(), null));
        activity.findViewById(R.id.textConnection).setFocusable(true);
        activity.findViewById(R.id.textConnection).setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle("Fila de chamadas")
                .setMessage(CallOutbox.status(activity, settings.active(), true))
                .setPositiveButton("Fechar", null).show());
        refreshStatus();
    }

    void start() { handler.removeCallbacks(refresh); handler.post(refresh); syncName(); }
    void stop() { handler.removeCallbacks(refresh); }

    private void refreshStatus() {
        EnvironmentConfig active = settings.active();
        text(R.id.textEnvironment, "Ambiente ativo: " + active.label());
        String name = settings.name(active);
        text(R.id.textDeviceName, name.isEmpty() ? "Aparelho sem nome — configure o cadastro"
                : "Aparelho: " + name + (settings.nameSynced(active) ? "" : " (sincronização pendente)"));
        text(R.id.textConnection, settings.connection(active) + "\n" + CallOutbox.status(activity, active, false));
        text(R.id.textMonitoring, !CallMonitorService.hasPhonePermissions(activity)
                ? "Monitoramento indisponível: conceda as permissões de telefone"
                : CallMonitorService.isRunning(activity) ? "Monitoramento ativo"
                : "Monitoramento inativo — reabra o aplicativo para tentar iniciar");
    }

    private void text(int id, String value) { ((TextView) activity.findViewById(id)).setText(value); }

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

    private EnvironmentConfig selected(RadioGroup choice, EditText address, EditText port) {
        return choice.getCheckedRadioButtonId() == R.id.chooseLocal
                ? EnvironmentConfig.local(address.getText().toString(), port.getText().toString())
                : EnvironmentConfig.production();
    }

    private void showSettings() {
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
        address.setEnabled(active.local);
        port.setEnabled(active.local);
        choice.setOnCheckedChangeListener((group, checked) -> {
            boolean local = checked == R.id.chooseLocal;
            address.setEnabled(local);
            port.setEnabled(local);
            try {
                EnvironmentConfig editing = selected(choice, address, port);
                name.setText(settings.name(editing));
                editingBase[0] = editing.baseUrl();
                editingRevision[0] = settings.revision(editing);
            }
            catch (IllegalArgumentException ignored) { name.setText(""); }
        });
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Ambiente e aparelho")
                .setView(form).setPositiveButton("Salvar", null)
                .setNeutralButton("Testar", null).setNegativeButton("Cancelar", null).create();
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
