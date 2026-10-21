package br.com.bomgas.bina;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.UUID;

/** All receiver/service/activity instances share the same persisted selection. */
public final class AppSettings {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    private final Context context;

    public AppSettings(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("DeviceSettings", Context.MODE_PRIVATE);
    }

    public EnvironmentConfig active() {
        synchronized (LOCK) {
            return prefs.getBoolean("local", false)
                    ? EnvironmentConfig.local(localAddress(), localPort()) : EnvironmentConfig.production();
        }
    }
    public String localAddress() { return prefs.getString("localAddress", "10.0.2.2"); }
    public String localPort() { return prefs.getString("localPort", "8080"); }
    public void select(EnvironmentConfig config) {
        synchronized (LOCK) {
            SharedPreferences.Editor editor = prefs.edit().putBoolean("local", config.local);
            if (config.local) editor.putString("localAddress", config.address).putString("localPort", String.valueOf(config.port));
            editor.apply();
        }
    }
    public String sourceId() {
        synchronized (LOCK) {
            String id = prefs.getString("sourceId", null);
            if (id == null) {
                id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                if (id == null || id.trim().isEmpty()) id = UUID.randomUUID().toString();
                prefs.edit().putString("sourceId", id).apply();
            }
            return id;
        }
    }
    private String key(EnvironmentConfig config, String field) { return config.baseUrl() + "/" + field; }
    public String name(EnvironmentConfig config) { return prefs.getString(key(config, "name"), ""); }
    public Long revision(EnvironmentConfig config) {
        long revision = prefs.getLong(key(config, "revision"), 0);
        return revision == 0 ? null : revision;
    }
    public boolean nameSynced(EnvironmentConfig config) { return prefs.getBoolean(key(config, "synced"), false); }
    public String beginRegistration(EnvironmentConfig config, String name) {
        synchronized (LOCK) {
            String token = UUID.randomUUID().toString();
            prefs.edit().putString(key(config, "name"), ServerApi.validateName(name))
                    .putBoolean(key(config, "synced"), false).putString(key(config, "request"), token).apply();
            return token;
        }
    }
    public void acceptRegistration(EnvironmentConfig config, String token, ServerApi.Registration result) {
        synchronized (LOCK) {
            if (!token.equals(prefs.getString(key(config, "request"), ""))) return;
            Long current = revision(config);
            if (current != null && current > result.revision) return;
            prefs.edit().putString(key(config, "name"), result.name)
                    .putLong(key(config, "revision"), result.revision).putBoolean(key(config, "synced"), true).apply();
        }
    }
    public String connection(EnvironmentConfig config) {
        return prefs.getString(key(config, "connection"), "Conexão ainda não testada");
    }
    public void connection(EnvironmentConfig config, String status) {
        prefs.edit().putString(key(config, "connection"), status).apply();
    }
}
