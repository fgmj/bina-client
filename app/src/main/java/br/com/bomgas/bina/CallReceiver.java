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
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class CallReceiver extends BroadcastReceiver {
    private static final String TAG = "CallReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())
                || !CallMonitorService.hasPhonePermissions(context)) return;
        String transition = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (!TelephonyManager.EXTRA_STATE_RINGING.equals(transition)
                && !TelephonyManager.EXTRA_STATE_OFFHOOK.equals(transition)
                && !TelephonyManager.EXTRA_STATE_IDLE.equals(transition)) return;
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(transition)
                && !intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) return;
        String raw = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        String number = raw == null ? "" : raw.replaceAll("[^0-9+]", "");
        if (!number.isEmpty() && !number.matches("\\+?[0-9]{1,15}")) return;
        long occurredAt = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        int subscription = intent.getIntExtra("subscription", -1);
        AppSettings settings = new AppSettings(context);
        EnvironmentConfig destination = settings.active();
        String sourceId = settings.sourceId();
        Context app = context.getApplicationContext();
        PendingResult result = goAsync();
        CallOutbox.RECORDING.execute(() -> {
            try {
                String boot = Integer.toString(android.provider.Settings.Global.getInt(
                        app.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT, -1));
                JSONObject extra = new JSONObject();
                extra.put("batteryLevel", getBatteryLevel(app));
                extra.put("location", getLocation(app));
                String description = CallOutbox.get(app).record(transition, subscription, number,
                        getReceivingNumber(app, intent), occurredAt, elapsed, boot, destination, sourceId, extra.toString());
                if (description != null) saveLog(app, description + " - salvo para entrega");
                CallOutbox.schedule(app, 0);
            } catch (Exception e) {
                Log.e(TAG, "Falha ao preservar chamada", e);
                app.getSharedPreferences("outbox-health", Context.MODE_PRIVATE).edit()
                        .putString("error", "Falha ao preservar chamada: " + e.getMessage()).commit();
                saveLog(app, "Falha ao preservar chamada: " + e.getMessage());
            } finally { result.finish(); }
        });
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
