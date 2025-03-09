package br.com.bomgas.bina;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.net.URL;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class CallReceiver extends BroadcastReceiver {

    private static final String TAG = "CallReceiver";
    private static final int MAX_PHONE_NUMBER_LENGTH = 15;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                if (incomingNumber != null && !incomingNumber.isEmpty()
                        && incomingNumber.matches("\\d+")
                        && incomingNumber.length() <= MAX_PHONE_NUMBER_LENGTH) {
                    Log.i(TAG, "Chamada recebida de: " + incomingNumber);
                    sendWebhook(context, incomingNumber);
                } else {
                    Log.w(TAG, "Número de chamada recebido é nulo.");
                }
            }
        }
    }

    private void sendWebhook(Context context, String phoneNumber) {
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
                public void onFailure(Call call, IOException e) {
                    String errorMessage = "Falha ao enviar webhook para: " + phoneNumber + ". Erro: " + e.getMessage();
                    saveLog(context, errorMessage);
                    Log.e(TAG, errorMessage, e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseMessage;
                    if (response.isSuccessful()) {
                        responseMessage = "Webhook enviado com sucesso para: " + phoneNumber;
                        Log.i(TAG, responseMessage);
                    } else {
                        responseMessage = "Falha na resposta do servidor para o número: " + phoneNumber;
                        Log.w(TAG, responseMessage);
                    }
                    saveLog(context, responseMessage);
                }

            });
        } catch (Exception e) {
            String exceptionMessage = "Erro ao preparar envio de webhook: " + e.getMessage();
            saveLog(context, exceptionMessage);
            Log.e(TAG, exceptionMessage, e);
        }
    }

    @NotNull
    protected static String montaURL(String phoneNumber, String savedIP, String url) {
        return url.replace("%SAVED_IP%", savedIP).replace("%PHONE_NUMBER%", phoneNumber);
    }

    private void saveLog(Context context, String message) {
        Intent intent = new Intent("br.com.bomgas.bina.NEW_LOG");
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
