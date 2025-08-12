package br.com.bomgas.bina;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.provider.Settings;
import android.os.BatteryManager;
import android.location.Location;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class CallReceiver extends BroadcastReceiver {

    private static final String TAG = "CallReceiver";
    private static final int MAX_PHONE_NUMBER_LENGTH = 15;
    public static final String API_URL = "https://bina.fernandojunior.com.br/api/eventos";
    public static final String API_IP = "204.216.163.47";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

            // Obtendo o número do telefone que está recebendo a chamada
            String receivingNumber = "";
            int slotIndex = -1;

            try {
                // Verifica permissão antes de acessar informações do telefone
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Permissão READ_PHONE_STATE não concedida");
                    saveLog(context, "Permissão READ_PHONE_STATE não concedida");
                    return;
                }

                SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (sm == null) {
                    Log.w(TAG, "SubscriptionManager não disponível");
                    saveLog(context, "SubscriptionManager não disponível");
                    return;
                }

                // Primeiro tenta obter o slot da chamada
                int subId = intent.getIntExtra("subscription", -1);
                if (subId != -1) {
                    SubscriptionInfo info = sm.getActiveSubscriptionInfo(subId);
                    if (info != null) {
                        slotIndex = info.getSimSlotIndex();
                        receivingNumber = info.getNumber();
                        Log.d(TAG, "Chamada recebida no SIM: " + (slotIndex + 1));
                    }
                } else {
                    // Fallback: tenta obter informações do primeiro SIM ativo
                    List<SubscriptionInfo> subscriptions = sm.getActiveSubscriptionInfoList();
                    if (subscriptions != null && !subscriptions.isEmpty()) {
                        SubscriptionInfo info = subscriptions.get(0);
                        slotIndex = info.getSimSlotIndex();
                        receivingNumber = info.getNumber();
                    }
                }

            } catch (Exception e) {
                String exceptionMessage = "Erro ao buscar número destinatário: " + e.getMessage();
                saveLog(context, exceptionMessage);
                Log.e(TAG, exceptionMessage, e);
            }

            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                if (incomingNumber != null && !incomingNumber.isEmpty()
                        && incomingNumber.matches("\\d+")
                        && incomingNumber.length() <= MAX_PHONE_NUMBER_LENGTH) {

                    Log.i(TAG, "Chamada recebida [SIM " + (slotIndex + 1) + "] de: " + incomingNumber + " para: " + (receivingNumber != null ? receivingNumber : "Número desconhecido"));

                    sendEventToAPI(context, "CALL_RECEIVED", "Chamada recebida", incomingNumber, receivingNumber);
                    sendWebhookLocal(context, incomingNumber);
                } else {
                    Log.w(TAG, "Número de chamada recebido é nulo ou inválido.");
                }
            }
        }
    }

    private void sendWebhookLocal(Context context, String phoneNumber) {
        try {
            String savedIP = ConfigSingleton.getInstance().getIP();
            if (savedIP == null || savedIP.isEmpty()) {
                Log.i(TAG, "Servidor não configurado");
                saveLog(context, "Servidor não configurado");
                return;
            }

            String url = ConfigSingleton.getInstance().getUrl();
            if (url == null || url.isEmpty()) {
                Log.i(TAG, "URL não configurada");
                saveLog(context, "URL não configurada");
                return;
            }

            // Constrói a URL utilizando o IP salvo
            url = montaURL(phoneNumber, savedIP, url);
            Log.i(TAG, "Enviando webhook para: " + url);
            saveLog(context, "Enviando webhook para: " + url);

            // Criação do request
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            // Execução do request de forma assíncrona
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    String errorMessage = "Falha ao enviar mensagem local para: " + phoneNumber + ". Erro: " + e.getMessage();
                    saveLog(context, errorMessage);
                    Log.e(TAG, errorMessage, e);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response)  {
                    String responseMessage;
                    try (response) {
                        if (response.isSuccessful()) {
                            responseMessage = "Mensagem local enviada com sucesso para: " + phoneNumber;
                            Log.i(TAG, responseMessage);
                        } else {
                            responseMessage = "Falha na resposta do servidor local para o número: " + phoneNumber + " - Código: " + response.code();
                            Log.w(TAG, responseMessage);
                        }
                        saveLog(context, responseMessage);
                    }
                }
            });
        } catch (Exception e) {
            String exceptionMessage = "Erro ao preparar envio da mensagem local: " + e.getMessage();
            saveLog(context, exceptionMessage);
            Log.e(TAG, exceptionMessage, e);
        }
    }

    public static void sendEventToAPI(Context context, String eventType, String description, String phoneNumber, String receivingNumber) {
        try {
            String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

            JSONObject additionalData = new JSONObject();
            additionalData.put("numero", phoneNumber);
            additionalData.put("data", timestamp);
            additionalData.put("receivingNumber", receivingNumber);

            // Adicionar nível da bateria
            additionalData.put("batteryLevel", getBatteryLevel(context));

            // Adicionar localização
            JSONObject location = getLocation(context);
            additionalData.put("location", location);

            JSONObject json = new JSONObject();
            json.put("description", description);
            json.put("deviceId", deviceId);
            json.put("eventType", eventType);
            json.put("additionalData", additionalData.toString());

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    String errorMessage = "Falha ao enviar evento para API. Erro: " + e.getMessage();
                    saveLog(context, errorMessage);
                    Log.e(TAG, errorMessage, e);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response)  {
                    String responseMessage;
                    try (response) {
                        if (response.isSuccessful()) {
                            responseMessage = "Evento enviado com sucesso: " + eventType;
                            Log.i(TAG, responseMessage);
                        } else {
                            responseMessage = "Falha na resposta da API: " + response.code();
                            Log.w(TAG, responseMessage);
                        }
                        saveLog(context, responseMessage);
                    }
                }
            });
        } catch (Exception e) {
            String exceptionMessage = "Erro ao preparar envio para API: " + e.getMessage();
            saveLog(context, exceptionMessage);
            Log.e(TAG, exceptionMessage, e);
        }
    }

    // Método para obter nível da bateria
    private static int getBatteryLevel(Context context) {
        try {
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter nível da bateria: " + e.getMessage(), e);
        }
        return -1; // Valor indicando erro
    }

    // Método para obter localização
    private static JSONObject getLocation(Context context) {
        JSONObject locationJson = new JSONObject();
        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            if (locationManager == null) {
                locationJson.put("error", "LocationManager não disponível");
                return locationJson;
            }

            // Verificar permissões
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                locationJson.put("error", "Permissão de localização não concedida");
                return locationJson;
            }

            // Tentar obter última localização conhecida
            Location lastKnownLocation = null;

            // Verificar GPS
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }

            // Se GPS não disponível, tentar rede
            if (lastKnownLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (lastKnownLocation != null) {
                locationJson.put("latitude", lastKnownLocation.getLatitude());
                locationJson.put("longitude", lastKnownLocation.getLongitude());
                locationJson.put("accuracy", lastKnownLocation.getAccuracy());
                locationJson.put("provider", lastKnownLocation.getProvider());

                // Timestamp da localização
                String locationTimestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        .format(new Date(lastKnownLocation.getTime()));
                locationJson.put("timestamp", locationTimestamp);
            } else {
                locationJson.put("error", "Localização não disponível");
            }

        } catch (Exception e) {
            try {
                locationJson.put("error", "Erro ao obter localização: " + e.getMessage());
            } catch (Exception jsonException) {
                Log.e(TAG, "Erro ao criar JSON de localização: " + jsonException.getMessage(), jsonException);
            }
            Log.e(TAG, "Erro ao obter localização: " + e.getMessage(), e);
        }

        return locationJson;
    }




    @NotNull
    protected static String montaURL(String phoneNumber, String savedIP, String url) {
        return url.replace("%SAVED_IP%", savedIP).replace("%PHONE_NUMBER%", phoneNumber);
    }

    private static void saveLog(Context context, String message) {
        Intent intent = new Intent("br.com.bomgas.bina.NEW_LOG");
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
