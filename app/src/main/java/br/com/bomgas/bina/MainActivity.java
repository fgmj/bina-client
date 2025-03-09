package br.com.bomgas.bina;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 123;
    private static final String PREFS_NAME = "LogPrefs";
    private static final String LOG_KEY = "LogList";
    private static final String IP_KEY = "ServerIp";
    private static final String TAG = "MainActivity";
    private EditText editTextIp;
    private Button buttonDiscover, buttonTest;
    private final List<String> logList = new ArrayList<>();
    private RecyclerView recyclerView;
    private LogAdapter logAdapter;

    private static final String URL_KEY = "ServerUrl"; // Chave para salvar a URL
    private EditText editTextUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            editTextIp = findViewById(R.id.editTextIp);
            buttonDiscover = findViewById(R.id.buttonDiscover);
            buttonTest = findViewById(R.id.buttonTest);
            editTextUrl = findViewById(R.id.editTextUrl);

            recyclerView = findViewById(R.id.recycler_view);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));


            loadLogs(); // Carregar logs ao iniciar a aplicação
            loadConfigs();

            editTextUrl.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    saveUrl(editTextUrl.getText().toString());
                }
            });

            editTextIp.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    saveIp(editTextIp.getText().toString());
                }
            });

            logAdapter = new LogAdapter(logList);
            recyclerView.setAdapter(logAdapter);
            addLogEntry("Iniciando aplicação...");

            buttonDiscover.setOnClickListener(v -> discoverServer());
            buttonTest.setOnClickListener(v -> testConnection());
            checkPermissions();
            recyclerView.scrollToPosition(logList.size() - 1);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar a aplicação: " + e.getMessage(), e);
            addLogEntry("Erro ao iniciar: " + e.getMessage());
        }
    }

    private void checkPermissions() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG}, REQUEST_CODE_PERMISSIONS);
            } else {
                addLogEntry("Permissão já concedida.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar permissões: " + e.getMessage(), e);
            addLogEntry("Erro ao verificar permissões: " + e.getMessage());
        }
    }

    public void addLogEntry(String message) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String logMessage = String.format("[%s] %s", timestamp, message);

            logList.add(logMessage);

            // Limitar a 50 itens
            if (logList.size() > 50) {
                logList.remove(0);
            }

            saveLogs();
            // Rolar para o último log
            recyclerView.scrollToPosition(logList.size() - 1);
            logAdapter.notifyDataSetChanged(); // Atualiza o RecyclerView
        } catch (Exception e) {
            Log.e(TAG, "Erro ao adicionar entrada de log: " + e.getMessage(), e);
        }
    }

    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String message = intent.getStringExtra("message");
                addLogEntry(message);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao receber broadcast: " + e.getMessage(), e);
                addLogEntry("Erro ao receber broadcast: " + e.getMessage());
            }
        }
    };

    private void discoverServer() {
        addLogEntry("Procurando servidor...");
        new Thread(() -> {
            try {

                InetAddress addr = InetAddress.getLocalHost();
                String hostname = InetAddress.getByName(addr.getHostName()).toString();


                JmDNS jmdns = JmDNS.create(addr, hostname);
                ServiceInfo[] services = jmdns.list("_http._tcp.local.");
                addLogEntry("Serviços encontrados: "+ Arrays.asList(services));

                if (services.length > 0) {
                    String serverIp = services[0].getInetAddresses()[0].getHostAddress();
                    int serverPort = services[0].getPort();
                    runOnUiThread(() -> {
                        editTextIp.setText(serverIp + ":" + serverPort);
                        saveIp(serverIp + ":" + serverPort);
                        addLogEntry("Servidor encontrado: " + serverIp + ":" + serverPort);
                    });
                } else {
                    runOnUiThread(() -> addLogEntry("Nenhum servidor encontrado."));
                }

                jmdns.close();
            } catch (IOException e) {
                Log.e(TAG, "Erro ao descobrir servidor: " + e.getMessage(), e);
                runOnUiThread(() -> addLogEntry("Erro ao descobrir servidor."));
            }
        }).start();
    }

    private void testConnection() {
        String ip = editTextIp.getText().toString();
        if (ip.isEmpty()) {
            Toast.makeText(this, "Nenhum IP configurado!", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                InetAddress address = InetAddress.getByName(ip.split(":")[0]);
                boolean reachable = address.isReachable(3000);
                runOnUiThread(() -> {
                    if (reachable) {
                        addLogEntry("Servidor acessível em " + ip);
                    } else {
                        addLogEntry("Falha ao conectar no servidor " + ip);
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "Erro ao testar conexão: " + e.getMessage(), e);
                runOnUiThread(() -> addLogEntry("Erro ao testar conexão: " + e.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, new IntentFilter("br.com.bomgas.bina.NEW_LOG"));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao registrar logReceiver: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        addLogEntry("Encerrando aplicação");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        try {
            if (requestCode == REQUEST_CODE_PERMISSIONS) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    addLogEntry("Permissão concedida.");
                } else {
                    addLogEntry("Permissão necessária para o funcionamento do app.");
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
            if (logsJson != null) {
                Type listType = new TypeToken<List<String>>() {}.getType();
                List<String> savedLogs = new Gson().fromJson(logsJson, listType);
                logList.addAll(savedLogs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao carregar logs: " + e.getMessage(), e);
            addLogEntry("Erro ao carregar logs: " + e.getMessage());
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
            addLogEntry("Erro ao salvar logs: " + e.getMessage());
        }
    }





    private void loadConfigs() {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            String savedIp = sharedPreferences.getString(IP_KEY, null);
            if (savedIp != null) {
                editTextIp.setText(savedIp);
                ConfigSingleton.getInstance().setIP(savedIp);
            }

            String savedUrl = sharedPreferences.getString(URL_KEY, "http://%SAVED_IP%/number/%PHONE_NUMBER%");
            if (savedUrl != null){
                editTextUrl.setText(savedUrl);
                ConfigSingleton.getInstance().setUrl(savedUrl);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro ao carregar configurações: " + e.getMessage(), e);
            addLogEntry("Erro ao carregar configurações: " + e.getMessage());
        }
    }

    private void saveIp(String ip) {
        try {
            addLogEntry("IP salvo: " + ip);
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(IP_KEY, ip);
            editor.apply();

            // Atualizar o IP no CallReceiver
            Intent intent = new Intent("br.com.bomgas.bina.NEW_IP");
            intent.putExtra("message", ip);
            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);

            ConfigSingleton.getInstance().setIP(ip);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar IP: " + e.getMessage(), e);
            addLogEntry("Erro ao salvar IP: " + e.getMessage());
        }
    }

    private void saveUrl(String url) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(URL_KEY, url);
        editor.apply();

        Intent intent = new Intent("br.com.bomgas.bina.NEW_URL");
        intent.putExtra("message", url);
        LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);

        ConfigSingleton.getInstance().setUrl(url);

        addLogEntry("URL salva: " + url);
    }
}
