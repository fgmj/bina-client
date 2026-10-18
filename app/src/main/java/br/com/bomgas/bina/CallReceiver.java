package br.com.bomgas.bina;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jetbrains.annotations.NotNull;
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

public class CallReceiver extends BroadcastReceiver {
    private static final String TAG = "CallReceiver";
    public static final String API_URL = BuildConfig.BINA_API_URL;
    public static final String API_IP = "204.216.163.47";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();

    private static final IncomingCallTracker callTracker = new IncomingCallTracker();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())
                || !CallMonitorService.hasPhonePermissions(context)) {
            return;
        }
        try {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            int subscriptionId = intent.getIntExtra("subscription", -1);
            IncomingCallTracker.Event event;
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                // Android sends a second copy without this extra when both phone permissions exist.
                if (!intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) {
                    return;
                }
                String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                number = number == null ? "" : number.replaceAll("[^0-9+]", "");
                if (!number.isEmpty() && !number.matches("\\+?[0-9]{1,15}")) {
                    return;
                }
                event = callTracker.ringing(subscriptionId, number, getReceivingNumber(context, intent));
            } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                event = callTracker.answered(subscriptionId, SystemClock.elapsedRealtime());
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                event = callTracker.ended(subscriptionId, SystemClock.elapsedRealtime());
            } else {
                return;
            }
            if (event == null) {
                return;
            }
            String description;
            switch (event.type) {
                case "CALL_RECEIVED":
                    description = "Chamada recebida";
                    break;
                case "CALL_ANSWERED_INCOMING":
                    description = "Chamada recebida atendida";
                    break;
                case "CALL_MISSED":
                    description = "Chamada não atendida";
                    break;
                default:
                    description = "Chamada encerrada - Duração: " + formatDurationStatic(event.durationSeconds);
                    break;
            }
            saveLog(context, description + (event.phoneNumber.isEmpty() ? " - Número oculto" : ": " + event.phoneNumber));
            sendEventToAPI(context, event.type, description, event.phoneNumber,
                    event.receivingNumber, event.durationSeconds);
            if ("CALL_RECEIVED".equals(event.type) && !event.phoneNumber.isEmpty()) {
                sendWebhookLocal(context, event.phoneNumber);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro no CallReceiver", e);
            saveLog(context, "Erro no monitoramento: " + e.getMessage());
        }
    }
    private String getReceivingNumber(Context context, Intent intent) {
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                SubscriptionManager manager = (SubscriptionManager) context.getSystemService(
                        Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (manager != null) {
                    int subscriptionId = intent.getIntExtra("subscription", -1);
                    SubscriptionInfo info = subscriptionId != -1
                            ? manager.getActiveSubscriptionInfo(subscriptionId) : null;
                    if (info == null && subscriptionId == -1) {
                        List<SubscriptionInfo> subscriptions = manager.getActiveSubscriptionInfoList();
                        if (subscriptions != null && !subscriptions.isEmpty()) {
                            info = subscriptions.get(0);
                        }
                    }
                    if (info != null && info.getNumber() != null && !info.getNumber().isEmpty()) {
                        return info.getNumber();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar número destinatário", e);
        }
        return getMyPhoneNumber(context);
    }
    private String getMyPhoneNumber(Context context) {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null &&
                    ActivityCompat.checkSelfPermission(context,
                            Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                String number = telephonyManager.getLine1Number();
                return number != null ? number : "Número desconhecido";
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter número do telefone: " + e.getMessage(), e);
        }
        return "Número desconhecido";
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

            url = montaURL(phoneNumber, savedIP, url);
            Log.i(TAG, "Enviando webhook para: " + url);
            saveLog(context, "Enviando webhook para: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String errorMessage = "Falha ao enviar mensagem local para: " + phoneNumber + ". Erro: "
                            + e.getMessage();
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

    public static void sendEventToAPI(Context context, String eventType, String description, String phoneNumber,
            String receivingNumber, long duration) {
        try {
            String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

            JSONObject additionalData = new JSONObject();
            additionalData.put("numero", phoneNumber);
            additionalData.put("data", timestamp);
            additionalData.put("receivingNumber", receivingNumber);
            additionalData.put("batteryLevel", getBatteryLevel(context));
            additionalData.put("location", getLocation(context));
            additionalData.put("duration", duration);
            additionalData.put("durationFormatted", formatDurationStatic(duration));

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

    private static String formatDurationStatic(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
        }
    }

    protected static String montaURL(String phoneNumber, String savedIP, String url) {
        return url.replace("%SAVED_IP%", savedIP).replace("%PHONE_NUMBER%", phoneNumber);
    }

    private static void saveLog(Context context, String message) {
        Intent intent = new Intent("br.com.bomgas.bina.NEW_LOG");
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
