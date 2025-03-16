package br.com.bomgas.bina;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
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

            // Obtendo o número do telefone que está recebendo a chamada (pode não estar disponível em todos os dispositivos)
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            String receivingNumber = "";

            try {
                if (telephonyManager != null && ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    receivingNumber = telephonyManager.getLine1Number(); // Pode retornar null ou estar indisponível
                }
            } catch (Exception e){
                String exceptionMessage = "Erro ao buscar numero destinatário : " + e.getMessage();
                saveLog(context, exceptionMessage);
                Log.e(TAG, exceptionMessage, e);
            }


            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                if (incomingNumber != null && !incomingNumber.isEmpty()
                        && incomingNumber.matches("\\d+")
                        && incomingNumber.length() <= MAX_PHONE_NUMBER_LENGTH) {

                    Log.i(TAG, "Chamada recebida de: " + incomingNumber + " para: " + (receivingNumber != null ? receivingNumber : "Número desconhecido"));

                    sendEventToAPI(context, "CALL_RECEIVED", "Chamada recebida", incomingNumber, receivingNumber);
                    sendWebhookLocal(context, incomingNumber);
                } else {
                    Log.w(TAG, "Número de chamada recebido é nulo ou inválido.");
                }
            }
        }
    }


    private void sendWebhookLocal(Context context, String phoneNumber) {
        OkHttpClient client = new OkHttpClient();

        try {
            String savedIP = ConfigSingleton.getInstance().getIP();
            if (savedIP==null || savedIP.isEmpty()) {
                Log.i(TAG, "Servidor não configurado");
                saveLog(context, "Servidor não configurado");
                return;
            }

            String url = ConfigSingleton.getInstance().getUrl();
            if (url==null || url.isEmpty()) {
                Log.i(TAG, "URL não configurada");
                saveLog(context, "URL não configurada");
                return;
            }

            // Constrói a URL utilizando o IP salvo ou um IP padrão
            url = montaURL(phoneNumber,  savedIP, url);
            Log.i(TAG, "Enviando webhook para: " + url);
            saveLog(context, "Enviando webhook para: " + url);

            // Criação do request
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            // Execução do request de forma assíncrona
            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    String errorMessage = "Falha ao enviar mensagem local para: " + phoneNumber + ". Erro: " + e.getMessage();
                    saveLog(context, errorMessage);
                    Log.e(TAG, errorMessage, e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseMessage;
                    if (response.isSuccessful()) {
                        responseMessage = "Mensagem local enviado com sucesso para: " + phoneNumber;
                        Log.i(TAG, responseMessage);
                    } else {
                        responseMessage = "Falha na resposta do servidor local para o número: " + phoneNumber;
                        Log.w(TAG, responseMessage);
                    }
                    saveLog(context, responseMessage);
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
            JSONObject json = new JSONObject();
            json.put("description", description);
            json.put("deviceId", deviceId);
            json.put("eventType", eventType);
            json.put("additionalData",  new JSONObject()
                      .put("numero", phoneNumber)
                      .put("data", timestamp)
                      .put("receivingNumber", receivingNumber)
                    .toString()
            );

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String errorMessage = "Falha ao enviar evento para API. Erro: " + e.getMessage();
                    saveLog(context, errorMessage);
                    Log.e(TAG, errorMessage, e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseMessage;
                    if (response.isSuccessful()) {
                        responseMessage = "Evento enviado com sucesso: " + eventType;
                        Log.i(TAG, responseMessage);
                    } else {
                        responseMessage = "Falha na resposta da API: " + response.code();
                        Log.w(TAG, responseMessage);
                    }
                    saveLog(context, responseMessage);
                }
            });
        } catch (Exception e) {
            String exceptionMessage = "Erro ao preparar envio para API: " + e.getMessage();
            saveLog(context, exceptionMessage);
            Log.e(TAG, exceptionMessage, e);
        }
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
