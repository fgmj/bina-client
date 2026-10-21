package br.com.bomgas.bina;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 123;
    private static final String PREFS_NAME = "LogPrefs";
    private static final String LOG_KEY = "LogList";
    private static final String TAG = "MainActivity";

    private SettingsController settingsController;
    private final List<String> logList = new ArrayList<>();
    private RecyclerView recyclerView;
    private LogAdapter logAdapter;
    private TextView textVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        try {
            initializeViews();
            setupRecyclerView();
            loadLogs();
            settingsController = new SettingsController(this, this::addLogEntry);
            setupBroadcastReceiver();
            checkPermissions();

            addLogEntry("Aplicação iniciada com sucesso");
            scrollToLastLog();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar a aplicação: " + e.getMessage(), e);
            addLogEntry("Erro ao iniciar: " + e.getMessage());
        }
    }

    private void initializeViews() {
        try {
            textVersion = findViewById(R.id.textVersion);
            recyclerView = findViewById(R.id.recycler_view);

            setupVersionInfo();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar views: " + e.getMessage(), e);
            addLogEntry("Erro ao inicializar interface: " + e.getMessage());
        }
    }

    private void setupRecyclerView() {
        try {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            logAdapter = new LogAdapter(logList);
            recyclerView.setAdapter(logAdapter);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao configurar RecyclerView: " + e.getMessage(), e);
            addLogEntry("Erro ao configurar lista de logs: " + e.getMessage());
        }
    }

    private void setupVersionInfo() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            long lastUpdateTime = pInfo.lastUpdateTime;

            String buildDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    .format(new Date(lastUpdateTime));

            if (textVersion != null) {
                textVersion.setText(String.format("Versão %s - %s", versionName, buildDate));
            }

        } catch (Exception e) {
            Log.e(TAG, "Falha ao recuperar versão: " + e.getMessage(), e);
            addLogEntry("Falha ao recuperar versão: " + e.getMessage());
        }
    }

    private void setupBroadcastReceiver() {
        try {
            LocalBroadcastManager.getInstance(this)
                    .registerReceiver(logReceiver, new IntentFilter("br.com.bomgas.bina.NEW_LOG"));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao registrar BroadcastReceiver: " + e.getMessage(), e);
            addLogEntry("Erro ao configurar recebimento de logs: " + e.getMessage());
        }
    }

    private void checkPermissions() {
        try {
            String[] requiredPermissions = {
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };

            List<String> missingPermissions = new ArrayList<>();
            for (String permission : requiredPermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission);
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= 33
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (!missingPermissions.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        missingPermissions.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
                addLogEntry("Solicitando permissões necessárias...");
            } else {
                addLogEntry("Todas as permissões já concedidas");
                startMonitoring();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar permissões: " + e.getMessage(), e);
            addLogEntry("Erro ao verificar permissões: " + e.getMessage());
        }
    }

    private void startMonitoring() {
        if (CallMonitorService.start(this)) {
            addLogEntry("Inicialização do monitoramento solicitada");
        } else {
            addLogEntry("Monitoramento indisponível: verifique as permissões de telefone e reabra o aplicativo");
        }
    }
    public void addLogEntry(String message) {
        try {
            if (message == null || message.trim().isEmpty()) {
                return;
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            String logMessage = String.format("[%s] %s", timestamp, message.trim());

            logList.add(logMessage);

            // Limitar a 50 itens
            if (logList.size() > 50) {
                logList.remove(0);
            }

            runOnUiThread(() -> {
                try {
                    if (logAdapter != null) {
                        logAdapter.notifyDataSetChanged();
                    }
                    scrollToLastLog();
                    saveLogs();
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao atualizar UI do log: " + e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Erro ao adicionar entrada de log: " + e.getMessage(), e);
        }
    }

    private void scrollToLastLog() {
        try {
            if (recyclerView != null && !logList.isEmpty()) {
                recyclerView.scrollToPosition(logList.size() - 1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao rolar para o último log: " + e.getMessage(), e);
        }
    }

    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent != null) {
                    String message = intent.getStringExtra("message");
                    if (message != null) {
                        addLogEntry(message);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao receber broadcast: " + e.getMessage(), e);
                addLogEntry("Erro ao receber broadcast: " + e.getMessage());
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        CallOutbox.schedule(this, 0);
        if (settingsController != null) settingsController.start();
        addLogEntry("Activity iniciada");
    }

    @Override
    protected void onStop() {
        if (settingsController != null) settingsController.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver);
            addLogEntry("Encerrando aplicação");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao destruir activity: " + e.getMessage(), e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        try {
            if (requestCode == REQUEST_CODE_PERMISSIONS) {
                boolean allGranted = true;
                List<String> deniedPermissions = new ArrayList<>();

                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults.length > i && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        addLogEntry("Permissão concedida: " + permissions[i]);
                    } else {
                        allGranted = false;
                        deniedPermissions.add(permissions[i]);
                    }
                }

                startMonitoring();
                if (allGranted) {
                    addLogEntry("Todas as permissões concedidas");
                    Toast.makeText(this, "Permissões concedidas com sucesso!", Toast.LENGTH_SHORT).show();
                } else {
                    addLogEntry("Permissões negadas: " + deniedPermissions);
                    Toast.makeText(this, "Algumas permissões são necessárias para o funcionamento", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao tratar resultado da permissão: " + e.getMessage(), e);
            addLogEntry("Erro ao tratar resultado da permissão: " + e.getMessage());
        }
    }

    private void loadLogs() {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String logsJson = sharedPreferences.getString(LOG_KEY, null);
            if (logsJson != null && !logsJson.isEmpty()) {
                Type listType = new TypeToken<List<String>>() {}.getType();
                List<String> savedLogs = new Gson().fromJson(logsJson, listType);
                if (savedLogs != null) {
                    logList.clear();
                    logList.addAll(savedLogs);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao carregar logs: " + e.getMessage(), e);
            addLogEntry("Erro ao carregar logs salvos: " + e.getMessage());
        }
    }

    private void saveLogs() {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            String logsJson = new Gson().toJson(logList);
            editor.putString(LOG_KEY, logsJson);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar logs: " + e.getMessage(), e);
        }
    }

}
